"""
Cloud Functions de ZeroHaus (Python 3.11, 2.ª generación, región europe-west1).

Todo lo que la app no puede o no debe hacer desde el móvil: operaciones con
Admin SDK, verificación de compras, envío de correo y tareas programadas.

Callables (se invocan desde la app con Firebase Functions)
  activar_suscripcion          Verifica una compra con Google Play y activa el plan.
  enviar_codigo_verificacion   Envía el código de 6 dígitos para verificar el email.
  verificar_codigo_email       Comprueba el código y marca el email como verificado.
  generar_sugerencias_ia       Consejos personalizados del informe con Gemini.
  eliminar_mi_cuenta           Borrado completo de la propia cuenta (RGPD / Play).
  eliminar_usuario_completo    Borrado completo de un usuario (solo admin).
  marcar_email_verificado      Marca un email como verificado (solo admin).
  bloquear_usuario             Bloquea o desbloquea una cuenta en Auth y en /usuarios (solo admin).

Triggers de Firestore
  on_message_created           Nuevo mensaje de chat → push, email y notificación.
  on_resena_changed            Reseña creada/editada/borrada → recalcula la nota y pone el nombre real.
  on_evento_perfil             Visita o contacto a un profesional → estadísticas.
  on_tecnico_creado            Perfil profesional nuevo → emailVerificado según Auth.
  on_denuncia_creada           Denuncia nueva → evidencia acotada y aviso al administrador.

Pub/Sub y programadas
  on_play_rtdn                 Avisos en tiempo real de Google Play (renovaciones…).
  revisar_suscripciones_diario Bloqueos pendientes en Auth, respaldo diario de suscripciones,
                               perfiles profesionales ocultos de cuentas ya verificadas y
                               limpieza de eventos y notificaciones.
  actualizar_precio_luz_diario Precio de la luz de Remote Config con el PVPC oficial de Red Eléctrica.

Pago con retención (Stripe Connect, modo test): las funciones stripe_* y
pagos_*, el webhook stripe_webhook y la tarea pagos_liberar_vencidos están en
pagos.py, con sus secretos (STRIPE_SECRET_KEY y los dos STRIPE_WEBHOOK_SECRET*).

Secretos (Secret Manager): SMTP_USUARIO y SMTP_CLAVE, para el correo saliente.
  firebase functions:secrets:set SMTP_USUARIO
  firebase functions:secrets:set SMTP_CLAVE

Despliegue:  firebase deploy --only functions
"""
import base64
import hashlib
import hmac
import html
import json
import math
import re
import secrets
import smtplib
import ssl
import time
import os
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo
from email.message import EmailMessage
from email.utils import formataddr
from firebase_functions import https_fn, firestore_fn, scheduler_fn, pubsub_fn, options
from firebase_functions.params import SecretParam
from firebase_admin import initialize_app, auth, firestore, messaging, storage
from google.api_core.exceptions import NotFound
from google.cloud.firestore_v1 import FieldFilter
from google.auth import default as default_creds
from google.auth.transport.requests import AuthorizedSession

initialize_app()

PROJECT_ID = "zerohaus-2a865"
PACKAGE_NAME = "es.zerohaus.app"

# ══ Suscripciones (profesionales pagan para ser Verificado/Destacado) ══
PLANES = {
    "verificado_trimestral":      {"plan": "verificado",          "dias": 91},
    "verificado_anual":           {"plan": "verificado",          "dias": 365},
    "destacado_mensual":          {"plan": "destacado",           "dias": 30},
    "destacado_anuncios_mensual": {"plan": "destacado_anuncios",  "dias": 30},
}

def _ahora_ms() -> int:
    """Instante actual en milisegundos (mismo formato que usa la app)."""
    return int(time.time() * 1000)


# Correo saliente (códigos de verificación y avisos por email). Credenciales
# en Secret Manager: firebase functions:secrets:set SMTP_USUARIO / SMTP_CLAVE.
# Servidor por defecto: Gmail; otro proveedor con SMTP_HOST / SMTP_PUERTO.
SMTP_USUARIO = SecretParam("SMTP_USUARIO")
SMTP_CLAVE = SecretParam("SMTP_CLAVE")
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PUERTO = int(os.environ.get("SMTP_PUERTO", "465"))

REGION = "europe-west1"

# Techo de instancias: una ráfaga de mensajes, eventos o llamadas no escala sin
# límite (ni el coste). Va antes de cualquier función: el decorador lee las
# opciones globales al definirla.
#
# CPU y concurrencia: con cpu=None, firebase-tools asigna 1 CPU a las funciones
# de hasta 2 GB y, con 1 CPU, una concurrencia de 80 peticiones por instancia
# (deploy/functions/prepare.js, resolveCpuAndConcurrency; docs de Firebase,
# «Manage functions»). Sin cpu=None, set_global_options pondría "gcf_gen1":
# 0,167 CPU con 256 MB y una sola petición a la vez por instancia.
MAX_INSTANCIAS = 10
# on_message_created recibe un evento por mensaje: con margen para que una
# ráfaga de chat no se quede sin instancias y se pierdan avisos
MAX_INSTANCIAS_CHAT = 50
MAX_INSTANCIAS_IA = 3      # Gemini: cada llamada cuesta dinero
options.set_global_options(region=REGION, max_instances=MAX_INSTANCIAS, cpu=None)

COLECCIONES_POR_CAMPO_UID = [
    ("viviendas",       "uid"),
    ("informes",        "uid"),
    ("notificaciones",  "uid"),
    ("suscripciones",   "uid"),
    # Contador diario de denuncias por autor: su id lleva el uid
    ("limites_denuncias_autor", "uid"),
]

# Colecciones donde el usuario aparece en uno de dos campos: las suyas y las
# que le tocan (reseñas sobre él, bloqueos que le pusieron, denuncias contra él)
COLECCIONES_DOBLE_CAMPO = [
    ("resenas",   ["uid", "tecnicoId"]),
    ("bloqueos",  ["autor", "bloqueado"]),
    ("denuncias", ["autor", "objetivo"]),
]

# cuentas_stripe: se borra el documento, no la cuenta en Stripe (la cierra el
# profesional desde su panel de Stripe; ZeroHaus no puede borrar su historial)
DOCS_POR_ID = ["usuarios", "tecnicos", "ajustes", "estadisticas", "limites_ia", "verificaciones_email", "avisos_email",
               "cuentas_stripe", "limites_pagos"]


def _verificar_admin(req: https_fn.CallableRequest) -> None:
    """Exige sesión y el custom claim `admin`; si no, lanza PERMISSION_DENIED."""
    if req.auth is None:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAUTHENTICATED,
            "Debes iniciar sesión."
        )
    token = req.auth.token or {}
    if token.get("admin") is not True:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.PERMISSION_DENIED,
            "Solo el administrador puede ejecutar esta operación."
        )


def _exigir_email_verificado(req: https_fn.CallableRequest) -> str:
    """uid de quien llama, si ha iniciado sesión con el email verificado (lo
    mismo que exigen las reglas para cualquier dato). Sin esto, una cuenta
    recién creada y sin verificar podía gastar el cupo de Gemini."""
    if req.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión.")
    if (req.auth.token or {}).get("email_verified") is not True:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.PERMISSION_DENIED,
            "Verifica tu email para continuar.",
            {"motivo": "email_sin_verificar"},
        )
    return req.auth.uid


def _borrar_query(db, query, batch_size=400) -> int:
    """Borra por lotes todos los documentos de una consulta. Devuelve cuántos."""
    total = 0
    while True:
        docs = list(query.limit(batch_size).stream())
        if not docs:
            return total
        batch = db.batch()
        for d in docs:
            batch.delete(d.reference)
        batch.commit()
        total += len(docs)
        if len(docs) < batch_size:
            return total


def _borrar_subcoleccion(db, parent_ref, sub_name: str, batch_size=400) -> int:
    """Borra por lotes una subcolección de `parent_ref`. Devuelve cuántos."""
    total = 0
    while True:
        docs = list(parent_ref.collection(sub_name).limit(batch_size).stream())
        if not docs:
            return total
        batch = db.batch()
        for d in docs:
            batch.delete(d.reference)
        batch.commit()
        total += len(docs)
        if len(docs) < batch_size:
            return total


def _borrar_storage(prefijo: str) -> int:
    """Borra todos los archivos de Cloud Storage bajo `prefijo` (bucket por
    defecto del proyecto). Nunca rompe el borrado de la cuenta: si falla, lo
    registra y sigue."""
    try:
        blobs = list(storage.bucket().list_blobs(prefix=prefijo))
        for b in blobs:
            b.delete()
        return len(blobs)
    except Exception as e:
        print(f"[PURGA] No se pudo borrar Storage '{prefijo}': {e}")
        return 0


def _purgar_usuario(db, uid: str) -> dict:
    """Borra en cascada TODOS los datos de un usuario: Firestore (colecciones,
    chats y subcolecciones), archivos de Storage (foto de perfil y adjuntos de
    sus chats) y su cuenta de Auth. Compartido por el borrado de admin y por
    el borrado self-service (RGPD / política de Google Play). Irreversible."""
    stats: dict = {}

    # Perfiles profesionales: el de id = uid y los antiguos reclamados con otro
    # id, que llevan el uid en el campo `uid`. De cada uno se borran también
    # sus reseñas, estadísticas, eventos y fotos.
    perfiles = sorted({uid} | {
        d.id for d in db.collection("tecnicos").where(filter=FieldFilter("uid", "==", uid)).stream()
    })
    otros_perfiles = [p for p in perfiles if p != uid]

    for col, campo in COLECCIONES_POR_CAMPO_UID:
        q = db.collection(col).where(filter=FieldFilter(campo, "==", uid))
        stats[col] = _borrar_query(db, q)

    for col, campos in COLECCIONES_DOBLE_CAMPO:
        total = 0
        for campo in campos:
            # tecnicoId: las reseñas sobre cualquiera de sus perfiles; el resto, por uid
            for valor in (perfiles if campo == "tecnicoId" else [uid]):
                q = db.collection(col).where(filter=FieldFilter(campo, "==", valor))
                total += _borrar_query(db, q)
        stats[col] = total

    chats_borrados = 0
    mensajes_borrados = 0
    archivos_borrados = 0
    chats = db.collection("chats").where(
        filter=FieldFilter("participantes", "array_contains", uid)
    ).stream()
    for chat in chats:
        mensajes_borrados += _borrar_subcoleccion(db, chat.reference, "mensajes")
        archivos_borrados += _borrar_storage(f"chats/{chat.id}/")
        chat.reference.delete()
        chats_borrados += 1
    stats["chats"] = chats_borrados
    stats["mensajes"] = mensajes_borrados
    stats["archivos"] = archivos_borrados + sum(_borrar_storage(f"perfiles/{p}/") for p in perfiles)

    eventos = 0
    for perfil_id in perfiles:
        eventos += _borrar_subcoleccion(db, db.collection("tecnicos").document(perfil_id), "eventos")
    # El de id = uid (con su /estadisticas) se borra con DOCS_POR_ID
    for perfil_id in otros_perfiles:
        db.collection("estadisticas").document(perfil_id).delete()
        db.collection("tecnicos").document(perfil_id).delete()
    stats["eventos"] = eventos
    stats["perfilesOtroId"] = len(otros_perfiles)

    for col in DOCS_POR_ID:
        try:
            db.collection(col).document(uid).delete()
            stats[col] = 1
        except Exception:
            stats[col] = 0

    # Los pagos no se borran (registros contables): se seudonimizan (pagos.py)
    stats["pagos"] = pagos.seudonimizar_pagos(db, uid)

    try:
        auth.delete_user(uid)
        stats["auth"] = 1
    except auth.UserNotFoundError:
        stats["auth"] = 0
    except Exception as e:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INTERNAL,
            f"Datos borrados pero falló el borrado de Auth: {e}"
        )

    return stats


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def eliminar_usuario_completo(req: https_fn.CallableRequest) -> dict:
    """Borra definitivamente un usuario: Auth, Firestore (cascada) y Storage.
    Reservado al administrador (custom claim `admin`). Args: uid (str) y
    forzar (bool, opcional): borra sin cancelar antes en Google Play, por si
    Play lleva tiempo sin responder; queda en el registro."""
    _verificar_admin(req)

    uid = (req.data or {}).get("uid")
    if not uid or not isinstance(uid, str):
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
            "Falta el parámetro 'uid'."
        )

    caller_uid = req.auth.uid if req.auth else None
    if uid == caller_uid:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "El administrador no puede eliminarse a sí mismo."
        )

    forzar = (req.data or {}).get("forzar") is True
    if forzar:
        print(f"[ADMIN] {caller_uid} borra a {uid} con forzar: sin cancelar antes sus suscripciones en Play")
    return _borrar_cuenta(firestore.client(), uid, cancelar_en_play=not forzar)


def _borrar_cuenta(db, uid: str, cancelar_en_play: bool = True) -> dict:
    """Cancela en Google Play las suscripciones que aún pueden cobrarse y
    después purga la cuenta. Si Play falla de forma pasajera, la cancelación
    lanza UNAVAILABLE antes de borrar nada (se reintenta). `cancelarEnPlay`:
    alguna quedó sin cancelar (Play no nos dejó, o el administrador forzó el
    borrado) y hay que cancelarla en Google Play.

    Con un pago en curso (cobrándose, retenido, terminado o en disputa) no se
    borra nada, ni se toca Play: sin la cuenta, el dinero se quedaría sin
    nadie que lo confirme, lo dispute o lo reciba."""
    if pagos.pagos_activos(db, uid):
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "Tienes pagos en curso. Termínalos o cancélalos antes de borrar la cuenta.",
            {"motivo": "pagos"},
        )
    if cancelar_en_play:
        canceladas, sin_cancelar = _cancelar_suscripciones_play(db, uid)
    else:
        canceladas, sin_cancelar = 0, len(_suscripciones_por_cancelar(db, uid))
    stats = _purgar_usuario(db, uid)
    return {
        "ok": True, "uid": uid, "stats": stats,
        "suscripcionCancelada": canceladas > 0,
        "cancelarEnPlay": sin_cancelar > 0,
    }


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def marcar_email_verificado(req: https_fn.CallableRequest) -> dict:
    """Marca como verificado el email de un usuario. Reservado al administrador.
    Sirve para cuentas que no pueden recibir el código por correo, como la
    cuenta de prueba de los revisores de Google Play. Args: uid (str)."""
    _verificar_admin(req)
    uid = (req.data or {}).get("uid")
    if not uid or not isinstance(uid, str):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Falta el parámetro 'uid'.")
    db = firestore.client()
    # Antes de marcar en Auth: una marca de deshacer pendiente desharía esta
    # verificación (y si no se puede quitar, no se verifica)
    _quitar_marca_deshacer(db, uid)
    try:
        auth.update_user(uid, email_verified=True)
    except auth.UserNotFoundError:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.NOT_FOUND, "No existe esa cuenta.")
    _marcar_perfil_verificado(db, uid)
    print(f"[ADMIN] {req.auth.uid} marcó como verificado el email de {uid}")
    return {"ok": True}


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def bloquear_usuario(req: https_fn.CallableRequest) -> dict:
    """Bloquea o desbloquea una cuenta. Reservado al administrador.
    Args: uid (str), bloqueado (bool).

    Antes era solo el campo `bloqueado` de /usuarios, que comprueba el login
    de la app: con otra app o la API REST se seguía usando la cuenta. Ahora
    también se desactiva en Auth (no puede iniciar sesión ni renovar el
    token) y se cierran sus sesiones abiertas."""
    _verificar_admin(req)
    data = req.data or {}
    uid = data.get("uid")
    bloqueado = data.get("bloqueado")
    if not uid or not isinstance(uid, str) or not isinstance(bloqueado, bool):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Faltan 'uid' o 'bloqueado'.")
    if uid == req.auth.uid:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "El administrador no puede bloquearse a sí mismo."
        )
    try:
        auth.update_user(uid, disabled=bloqueado)
    except auth.UserNotFoundError:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.NOT_FOUND, "No existe esa cuenta.")
    if bloqueado:
        # El token que ya tiene vale como mucho una hora más; no se renueva
        auth.revoke_refresh_tokens(uid)
    firestore.client().collection("usuarios").document(uid).set({"bloqueado": bloqueado}, merge=True)
    print(f"[ADMIN] {req.auth.uid} {'bloqueó' if bloqueado else 'desbloqueó'} a {uid}")
    return {"ok": True}


def _sincronizar_bloqueados(db) -> int:
    """Desactiva en Auth a quien tiene `bloqueado` en /usuarios pero sigue con
    la cuenta activa (bloqueados antes de bloquear_usuario o a mano en la
    consola). Devuelve cuántos."""
    desactivados = 0
    for u in db.collection("usuarios").where(filter=FieldFilter("bloqueado", "==", True)).stream():
        try:
            if not auth.get_user(u.id).disabled:
                auth.update_user(u.id, disabled=True)
                auth.revoke_refresh_tokens(u.id)
                desactivados += 1
        except auth.UserNotFoundError:
            continue
    return desactivados


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def eliminar_mi_cuenta(req: https_fn.CallableRequest) -> dict:
    """Borrado self-service: el usuario autenticado elimina su PROPIA cuenta y
    todos sus datos. Obligatorio por la política de Google Play (borrado de
    cuenta desde la app) y por el RGPD (derecho de supresión). Irreversible.
    Antes cancela en Google Play la renovación de sus suscripciones: sin
    cuenta no podría usarlas y Play le seguiría cobrando."""
    if req.auth is None:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión."
        )

    return _borrar_cuenta(firestore.client(), req.auth.uid)


# ════════════════════════════════════════════════════════════════════════
# Helpers para FCM y notificaciones in-app
# ════════════════════════════════════════════════════════════════════════

def _preferencias(db, uid: str) -> dict:
    """Ajustes de notificación del usuario (/ajustes/{uid}). Los interruptores
    de la pantalla de Ajustes se aplican AQUÍ, en el servidor: si no, Android
    mostraría el push igualmente con la app en segundo plano."""
    snap = db.collection("ajustes").document(uid).get()
    d = (snap.to_dict() or {}) if snap.exists else {}
    return {
        # Destino del push: el FID (app 2.3+) y el token antiguo de respaldo
        "destino": {"fid": d.get("fidFCM") or None, "token": d.get("tokenFCM") or None},
        "push": d.get("notificacionesPush", True) is not False,
        "sonido": d.get("notificacionesSonido", True) is not False,
        "email": d.get("notificacionesEmail", False) is True,
        "mensajes": d.get("notificacionesMensajes", True) is not False,
        "valoraciones": d.get("notificacionesValoraciones", True) is not False,
        "idioma": d.get("idioma") or "Español",
    }


# (asunto, cuerpo) por tipo de aviso + pie común, en los 14 idiomas
_AVISOS_EMAIL = {
    "Español": ("Nuevo mensaje de {nombre}", "{nombre} te ha escrito en ZeroHaus:\n«{texto}»\n\nAbre la app para responder.", "Has recibido una valoración", "{nombre} te ha valorado con {n}/5 en ZeroHaus.", "Recibes este email porque tienes activados los avisos por email. Puedes desactivarlos en Ajustes › Notificaciones."),
    "English": ("New message from {nombre}", "{nombre} has written to you on ZeroHaus:\n“{texto}”\n\nOpen the app to reply.", "You've received a review", "{nombre} rated you {n}/5 on ZeroHaus.", "You're receiving this because email alerts are on. You can turn them off in Settings › Notifications."),
    "Català": ("Nou missatge de {nombre}", "{nombre} t'ha escrit a ZeroHaus:\n«{texto}»\n\nObre l'app per respondre.", "Has rebut una valoració", "{nombre} t'ha valorat amb un {n}/5 a ZeroHaus.", "Reps aquest correu perquè tens activats els avisos per correu. Pots desactivar-los a Configuració › Notificacions."),
    "Euskara": ("Mezu berria: {nombre}", "{nombre}(e)k idatzi dizu ZeroHaus-en:\n«{texto}»\n\nIreki aplikazioa erantzuteko.", "Balorazio bat jaso duzu", "{nombre}(e)k {n}/5 eman dizu ZeroHaus-en.", "Posta bidezko abisuak aktibatuta dituzulako jasotzen duzu hau. Ezarpenak › Jakinarazpenak atalean desaktibatu ditzakezu."),
    "Galego": ("Nova mensaxe de {nombre}", "{nombre} escribiuche en ZeroHaus:\n«{texto}»\n\nAbre a app para responder.", "Recibiches unha valoración", "{nombre} valorouche cun {n}/5 en ZeroHaus.", "Recibes este correo porque tes activados os avisos por correo. Podes desactivalos en Axustes › Notificacións."),
    "Português": ("Nova mensagem de {nombre}", "{nombre} escreveu-lhe no ZeroHaus:\n«{texto}»\n\nAbra a app para responder.", "Recebeu uma avaliação", "{nombre} avaliou-o com {n}/5 no ZeroHaus.", "Recebe este email porque tem os avisos por email ativos. Pode desativá-los em Definições › Notificações."),
    "Français": ("Nouveau message de {nombre}", "{nombre} vous a écrit sur ZeroHaus :\n« {texto} »\n\nOuvrez l'app pour répondre.", "Vous avez reçu un avis", "{nombre} vous a attribué {n}/5 sur ZeroHaus.", "Vous recevez cet e-mail car les alertes par e-mail sont activées. Vous pouvez les désactiver dans Réglages › Notifications."),
    "Deutsch": ("Neue Nachricht von {nombre}", "{nombre} hat dir auf ZeroHaus geschrieben:\n„{texto}“\n\nÖffne die App, um zu antworten.", "Du hast eine Bewertung erhalten", "{nombre} hat dich auf ZeroHaus mit {n}/5 bewertet.", "Du erhältst diese E-Mail, weil E-Mail-Benachrichtigungen aktiviert sind. Du kannst sie unter Einstellungen › Benachrichtigungen ausschalten."),
    "Italiano": ("Nuovo messaggio da {nombre}", "{nombre} ti ha scritto su ZeroHaus:\n«{texto}»\n\nApri l'app per rispondere.", "Hai ricevuto una valutazione", "{nombre} ti ha valutato {n}/5 su ZeroHaus.", "Ricevi questa email perché gli avvisi via email sono attivi. Puoi disattivarli in Impostazioni › Notifiche."),
    "العربية": ("رسالة جديدة من {nombre}", "راسلك {nombre} على ZeroHaus:\n«{texto}»\n\nافتح التطبيق للرد.", "تلقيت تقييماً", "قيّمك {nombre} بـ {n}/5 على ZeroHaus.", "تتلقى هذه الرسالة لأن تنبيهات البريد مفعّلة. يمكنك إيقافها من الإعدادات › الإشعارات."),
    "中文": ("{nombre} 发来新消息", "{nombre} 在 ZeroHaus 上给您发了消息：\n“{texto}”\n\n请打开应用回复。", "您收到了一条评价", "{nombre} 在 ZeroHaus 上给您打了 {n}/5 分。", "您收到此邮件是因为开启了邮件提醒，可在“设置 › 通知”中关闭。"),
    "Română": ("Mesaj nou de la {nombre}", "{nombre} ți-a scris pe ZeroHaus:\n„{texto}”\n\nDeschide aplicația pentru a răspunde.", "Ai primit o evaluare", "{nombre} te-a evaluat cu {n}/5 pe ZeroHaus.", "Primești acest email pentru că ai activat alertele prin email. Le poți dezactiva din Setări › Notificări."),
    "Nederlands": ("Nieuw bericht van {nombre}", "{nombre} heeft je geschreven op ZeroHaus:\n“{texto}”\n\nOpen de app om te antwoorden.", "Je hebt een beoordeling ontvangen", "{nombre} gaf je een {n}/5 op ZeroHaus.", "Je ontvangt deze e-mail omdat e-mailmeldingen aan staan. Je kunt ze uitzetten via Instellingen › Meldingen."),
    "Polski": ("Nowa wiadomość od {nombre}", "{nombre} napisał(a) do Ciebie w ZeroHaus:\n„{texto}”\n\nOtwórz aplikację, aby odpowiedzieć.", "Otrzymałeś ocenę", "{nombre} ocenił(a) Cię na {n}/5 w ZeroHaus.", "Otrzymujesz tę wiadomość, bo masz włączone powiadomienia e-mail. Możesz je wyłączyć w Ustawienia › Powiadomienia."),
}

def _idioma_conocido(idioma) -> str:
    """El idioma que manda la app si es uno de los 14 conocidos; si no,
    «Español». Va escrito dentro del prompt de la IA: nunca texto libre."""
    return idioma if isinstance(idioma, str) and idioma in _AVISOS_EMAIL else "Español"


AVISO_CHAT_ESPERA_MS = 30 * 60 * 1000   # como mucho 1 email por conversación cada 30 min
# Tope de correos al día entre todos (códigos y avisos): la cuenta de Gmail
# admite unos 500 y, si se pasa, Google la bloquea y nadie recibe su código.
# Así, aunque alguien cree cuentas en masa para mandar códigos, no la tumba.
CORREOS_MAX_DIA = int(os.environ.get("CORREOS_MAX_DIA", "300"))
# Parte del tope que pueden gastar los avisos (mensajes y valoraciones): el
# resto queda siempre para los códigos de verificación.
CORREOS_AVISOS_MAX_DIA = int(os.environ.get("CORREOS_AVISOS_MAX_DIA", "150"))
# Avisos por email que recibe como mucho una misma persona al día
AVISOS_MAX_DIA_USUARIO = int(os.environ.get("AVISOS_MAX_DIA_USUARIO", "20"))


def _hoy_utc() -> str:
    """Día UTC (AAAAMMDD) del reloj de _ahora_ms: con él cambian los cupos diarios."""
    return time.strftime("%Y%m%d", time.gmtime(_ahora_ms() / 1000))


def _reservar_cupo_correo(db, aviso: bool = False) -> bool:
    """Gasta un correo del tope diario. Los códigos solo miran el total
    (CORREOS_MAX_DIA); los avisos, además, su propio cupo
    (CORREOS_AVISOS_MAX_DIA), para no dejar sin código a nadie. False si no quedan."""
    ref = db.collection("limites_correo").document(_hoy_utc())

    @firestore.transactional
    def _reservar(txn):
        d = ref.get(transaction=txn).to_dict() or {}
        enviados = int(d.get("enviados") or 0)
        avisos = int(d.get("avisos") or 0)
        if enviados >= CORREOS_MAX_DIA or (aviso and avisos >= CORREOS_AVISOS_MAX_DIA):
            return False
        txn.set(ref, {"enviados": enviados + 1, "avisos": avisos + (1 if aviso else 0)})
        return True

    return _reservar(db.transaction())


def _reservar_aviso_usuario(db, uid: str, clave_limite: str = "") -> bool:
    """Reserva un aviso para este destinatario en /avisos_email/{uid}, en una
    transacción: con `clave_limite` (chat_{id}) como mucho uno por chat cada
    AVISO_CHAT_ESPERA_MS, y AVISOS_MAX_DIA_USUARIO al día (dia, enviadosHoy).
    Si no toca enviar, no escribe nada. Al escribir borra las esperas de chat
    ya vencidas, para que el documento no crezca sin límite. False si no toca."""
    ref = db.collection("avisos_email").document(uid)
    ahora = _ahora_ms()
    hoy = _hoy_utc()

    @firestore.transactional
    def _reservar(txn):
        d = ref.get(transaction=txn).to_dict() or {}
        if clave_limite and ahora - int(d.get(clave_limite) or 0) < AVISO_CHAT_ESPERA_MS:
            return False
        enviados_hoy = int(d.get("enviadosHoy") or 0) if d.get("dia") == hoy else 0
        if enviados_hoy >= AVISOS_MAX_DIA_USUARIO:
            return False
        cambios = {
            k: firestore.DELETE_FIELD for k, v in d.items()
            if k.startswith("chat_") and (not isinstance(v, (int, float)) or ahora - v >= AVISO_CHAT_ESPERA_MS)
        }
        cambios.update({"dia": hoy, "enviadosHoy": enviados_hoy + 1})
        if clave_limite:
            cambios[clave_limite] = ahora
        txn.set(ref, cambios, merge=True)
        return True

    return _reservar(db.transaction())


def _enviar_aviso_email(db, uid: str, idioma: str, tipo: str, clave_limite: str = "", **campos) -> None:
    """Aviso por email (tipo 'mensaje' o 'valoracion') a un usuario con el email
    verificado. Nunca rompe el trigger: los fallos solo se registran.

    Primero se reserva el aviso de la persona (espera del chat y tope diario) y
    después el cupo común. Si el común está agotado, el aviso no sale pero ya
    cuenta en la espera del chat y en el tope de la persona; si falla el SMTP,
    cuenta además en el cupo común. Así un fallo nunca provoca reintentos."""
    try:
        usuario = auth.get_user(uid)
        if not usuario.email or not usuario.email_verified:
            return
        if not _reservar_aviso_usuario(db, uid, clave_limite):
            return
        if not _reservar_cupo_correo(db, aviso=True):
            print(f"[EMAIL] Tope diario de correos alcanzado: sin aviso '{tipo}' a {uid}")
            return
        t = _AVISOS_EMAIL.get(idioma, _AVISOS_EMAIL["Español"])
        asunto, cuerpo = (t[0], t[1]) if tipo == "mensaje" else (t[2], t[3])
        _enviar_email(usuario.email, asunto.format(**campos), cuerpo.format(**campos), t[4], idioma)
    except Exception as e:
        print(f"[EMAIL] No se pudo enviar el aviso '{tipo}' a {uid}: {e}")


def _crear_notificacion_in_app(db, uid: str, titulo: str, detalle: str, tipo: str, doc_id: str = "") -> None:
    """Añade una entrada al historial de notificaciones del usuario (/notificaciones).

    Con `doc_id` (id del evento + destinatario) es idempotente: si Firestore
    entrega el mismo evento dos veces, se sobrescribe en vez de duplicarse."""
    coleccion = db.collection("notificaciones")
    ref = coleccion.document(doc_id) if doc_id else coleccion.document()
    ref.set({
        "id": ref.id,
        "uid": uid,
        "titulo": titulo,
        "detalle": detalle,
        "fecha": _ahora_ms(),
        "leida": False,
        "tipo": tipo,
    })


def _canal_para_tipo(tipo: str) -> str:
    """Canal de Android (definido en NotificacionesLocales.kt) para cada tipo de aviso."""
    return {
        "chat":         "zerohaus_chat_v2",
        "mensaje":      "zerohaus_chat_v2",
        "suscripcion":  "zerohaus_general_v2",
        "valoracion":   "zerohaus_general_v2",
    }.get(tipo, "zerohaus_general_v2")


CANAL_SILENCIO = "zerohaus_silencio_v1"   # canal sin sonido (Ajustes › Sonido desactivado)


def _enviar_push(destino: dict, titulo: str, cuerpo: str, data: dict = None, sonido: bool = True) -> None:
    """Envía un push por FCM a un móvil.

    FCM pasa de los tokens de registro al ID de instalación (FID): firebase-admin
    marca `Message.token` como obsoleto. Se envía al FID si la app lo ha
    guardado (`fidFCM`) y, si no hay o falla, al token antiguo (`tokenFCM`),
    para que las versiones anteriores de la app sigan recibiendo avisos.
    Un destino que ya no existe se ignora sin fallar."""
    tipo = (data or {}).get("tipo", "general")
    canal = _canal_para_tipo(str(tipo)) if sonido else CANAL_SILENCIO
    comun = dict(
        notification=messaging.Notification(title=titulo, body=cuerpo),
        data={k: str(v) for k, v in (data or {}).items()},
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id=canal,
                sound="default" if sonido else None,
                # Un aviso por chat: el siguiente mensaje sustituye al anterior
                # en vez de apilarse (y tocarlo abre ese chat: el chatId va en data)
                tag=(data or {}).get("chatId"),
            ),
        ),
    )
    intentos = []
    if (destino or {}).get("fid"):
        intentos.append(("fid", lambda: messaging.Message(fid=destino["fid"], **comun)))
    if (destino or {}).get("token"):
        intentos.append(("token", lambda: messaging.Message(token=destino["token"], **comun)))
    for nombre, crear in intentos:
        try:
            messaging.send(crear())
            return
        except (messaging.UnregisteredError, messaging.SenderIdMismatchError):
            continue  # ese destino ya no vale: se prueba el siguiente
        except Exception as e:
            print(f"[FCM] Error enviando push por {nombre}: {e}")


# ════════════════════════════════════════════════════════════════════════
# Trigger: nuevo mensaje de chat → push al otro participante
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_created(
    document="chats/{chatId}/mensajes/{msgId}",
    region=REGION,
    secrets=[SMTP_USUARIO, SMTP_CLAVE],
    max_instances=MAX_INSTANCIAS_CHAT,
)
def on_message_created(event) -> None:
    """Nuevo mensaje de chat: notificación en la app y, según los ajustes de cada
    destinatario, push y/o email."""
    if event.data is None:
        return

    msg = event.data.to_dict() or {}
    emisor_uid = msg.get("emisorUid", "")
    chat_id = event.params["chatId"]

    db = firestore.client()
    emisor_nombre = _nombre_real(db, emisor_uid, msg.get("emisorNombre", ""))
    chat_snap = db.collection("chats").document(chat_id).get()
    if not chat_snap.exists:
        return
    participantes = (chat_snap.to_dict() or {}).get("participantes", [])
    destinatarios = [uid for uid in participantes if uid != emisor_uid]
    # Quién ha escrito en el chat: solo lo pone el servidor, y las reseñas
    # exigen que hayan escrito los dos (que el profesional haya contestado)
    if emisor_uid in participantes:
        try:
            if "escribieron" in (chat_snap.to_dict() or {}):
                chat_snap.reference.update({"escribieron": firestore.ArrayUnion([emisor_uid])})
            else:
                # Chat de antes de este campo: se rellena con todos sus mensajes
                _rellenar_escribieron_chat(chat_snap)
        except Exception as e:
            print(f"[CHAT] No se pudo anotar quién escribió en {chat_id}: {e}")

    for uid in destinatarios:
        # Las reglas ya impiden escribir con un bloqueo, salvo al administrador
        # (Admin SDK o claim admin): tampoco se avisa a quien bloqueó o fue bloqueado
        if _hay_bloqueo(db, emisor_uid, uid):
            continue
        pref = _preferencias(db, uid)
        textos = _AVISOS_EMAIL.get(pref["idioma"], _AVISOS_EMAIL["Español"])
        cuerpo = _resumen_mensaje(msg, pref["idioma"])
        _crear_notificacion_in_app(
            db, uid,
            titulo=textos[0].format(nombre=emisor_nombre),
            detalle=cuerpo[:200],
            tipo="chat",
            doc_id=f"{event.id}_{uid}",
        )
        if not pref["mensajes"]:
            continue
        if pref["push"]:
            _enviar_push(
                pref["destino"],
                titulo=emisor_nombre,
                cuerpo=cuerpo[:200],
                data={"tipo": "chat", "chatId": chat_id},
                sonido=pref["sonido"],
            )
        if pref["email"]:
            _enviar_aviso_email(
                db, uid, pref["idioma"], "mensaje", clave_limite=f"chat_{chat_id}",
                nombre=emisor_nombre, texto=cuerpo[:300],
            )


def _hay_bloqueo(db, a: str, b: str) -> bool:
    """Bloqueo en cualquier sentido entre a y b (/bloqueos/{autor}_{bloqueado}).
    Si Firestore falla, se avisa igual: las reglas ya cortan el chat y esto
    solo es una segunda barrera."""
    if not a or not b:
        return False
    bloqueos = db.collection("bloqueos")
    try:
        return bloqueos.document(f"{a}_{b}").get().exists or bloqueos.document(f"{b}_{a}").get().exists
    except Exception as e:
        print(f"[BLOQUEOS] No se pudo comprobar el bloqueo entre {a} y {b}: {e}")
        return False


def _autores_del_chat(mensajes: list, participantes: list) -> list:
    """Participantes que han escrito algún mensaje, en el orden de `participantes`."""
    escritos = {m.get("emisorUid") for m in mensajes}
    return [p for p in participantes if p in escritos]


def _rellenar_escribieron_chat(chat_snap) -> None:
    """Pone `escribieron` de un chat a partir de los mensajes que tiene."""
    mensajes = [m.to_dict() or {} for m in chat_snap.reference.collection("mensajes").stream()]
    participantes = (chat_snap.to_dict() or {}).get("participantes") or []
    chat_snap.reference.update({"escribieron": _autores_del_chat(mensajes, participantes)})


def _nombre_real(db, uid: str, respaldo: str) -> str:
    """Nombre de una persona tal como está en su perfil (/usuarios).

    No se usa el nombre que viene en el mensaje o en la reseña: lo escribe el
    móvil y podía ser cualquiera («ZeroHaus Soporte»), y el servidor lo ponía
    en el push y en el email que se envían desde la cuenta de ZeroHaus."""
    return (_nombre_perfil(db, uid) or str(respaldo).strip() or "ZeroHaus")[:60]


def _nombre_perfil(db, uid: str) -> str:
    """Nombre de /usuarios/{uid} (60 caracteres como mucho), o "" si no lo hay."""
    if not uid:
        return ""
    try:
        nombre = str((db.collection("usuarios").document(uid).get().to_dict() or {}).get("nombre") or "")
    except Exception as e:
        print(f"[NOMBRE] No se pudo leer el perfil de {uid}: {e}")
        return ""
    return nombre.strip()[:60]


# Nombre de los adjuntos sin texto (foto, archivo) en los 14 idiomas
_ADJUNTOS = {
    "Español": ("Foto", "Archivo"), "English": ("Photo", "File"), "Català": ("Foto", "Fitxer"),
    "Euskara": ("Argazkia", "Fitxategia"), "Galego": ("Foto", "Ficheiro"), "Português": ("Foto", "Ficheiro"),
    "Français": ("Photo", "Fichier"), "Deutsch": ("Foto", "Datei"), "Italiano": ("Foto", "File"),
    "العربية": ("صورة", "ملف"), "中文": ("照片", "文件"), "Română": ("Fotografie", "Fișier"),
    "Nederlands": ("Foto", "Bestand"), "Polski": ("Zdjęcie", "Plik"),
}


def _resumen_mensaje(msg: dict, idioma: str) -> str:
    """Texto del aviso de un mensaje: el texto, o '📷 Foto' / '📎 nombre.pdf'
    para los adjuntos, en el idioma de quien lo recibe (antes ponía 'Adjunto'
    en español para todos)."""
    texto = str(msg.get("texto") or "").strip()
    tipo = msg.get("tipo", "texto")
    foto, archivo = _ADJUNTOS.get(idioma, _ADJUNTOS["Español"])
    if tipo == "imagen":
        return f"📷 {texto or foto}"
    if tipo == "archivo":
        return f"📎 {str(msg.get('mediaNombre') or '').strip() or archivo}"
    return texto or "…"


# ════════════════════════════════════════════════════════════════════════
# Trigger: reseña creada/modificada/borrada → recalcular rating
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_written(
    document="resenas/{resenaId}",
    region=REGION,
    secrets=[SMTP_USUARIO, SMTP_CLAVE],
)
def on_resena_changed(event) -> None:
    """Reseña creada, editada o borrada: recalcula la nota del profesional si
    cambian la puntuación o el profesional, pone en la reseña el nombre del
    perfil de su autor y, si es nueva, avisa al profesional (notificación,
    push y/o email según sus ajustes)."""
    before = event.data.before
    after = event.data.after
    antes = (before.to_dict() or {}) if before is not None and before.exists else None
    despues = (after.to_dict() or {}) if after is not None and after.exists else None

    db = firestore.client()
    # La nota solo depende del profesional y la puntuación: otras ediciones
    # (como la corrección del nombre de abajo) no la recalculan
    def nota(d):
        return d.get("tecnicoId"), d.get("puntuacion")
    if antes is None or despues is None or nota(antes) != nota(despues):
        for tecnico_id in {d.get("tecnicoId") for d in (antes, despues) if d and d.get("tecnicoId")}:
            _recalcular_rating(db, tecnico_id)

    if despues is not None:
        _corregir_nombre_resena(db, event.params["resenaId"], despues)

    es_creacion = antes is None and despues is not None
    if es_creacion:
        data = despues
        tecnico_id = data.get("tecnicoId", "")
        nombre_usuario = _nombre_real(db, data.get("uid", ""), data.get("nombreUsuario", ""))
        puntuacion = int(data.get("puntuacion", 5))
        estrellas = "*" * puntuacion
        if tecnico_id:
            pref = _preferencias(db, tecnico_id)
            textos = _AVISOS_EMAIL.get(pref["idioma"], _AVISOS_EMAIL["Español"])
            _crear_notificacion_in_app(
                db, tecnico_id,
                titulo=textos[2],
                detalle=f"{textos[3].format(nombre=nombre_usuario, n=puntuacion)} ({estrellas})",
                tipo="valoracion",
                doc_id=f"{event.id}_{tecnico_id}",
            )
            if pref["valoraciones"]:
                if pref["push"]:
                    _enviar_push(
                        pref["destino"],
                        titulo=textos[2],
                        cuerpo=f"{nombre_usuario}: {puntuacion}/5",
                        data={"tipo": "valoracion", "tecnicoId": tecnico_id},
                        sonido=pref["sonido"],
                    )
                if pref["email"]:
                    _enviar_aviso_email(
                        db, tecnico_id, pref["idioma"], "valoracion",
                        nombre=nombre_usuario, n=puntuacion,
                    )


def _corregir_nombre_resena(db, resena_id: str, resena: dict) -> None:
    """La lista pública de reseñas muestra `nombreUsuario`, que escribe el
    móvil: se sustituye por el nombre del perfil del autor. Solo se escribe si
    es distinto; esa escritura vuelve a disparar on_resena_changed, que ya lo
    encuentra bien y no recalcula ni avisa. Sin nombre en el perfil no se toca."""
    nombre = _nombre_perfil(db, resena.get("uid") or "")
    if not nombre or resena.get("nombreUsuario") == nombre:
        return
    try:
        db.collection("resenas").document(resena_id).update({"nombreUsuario": nombre})
    except NotFound:
        pass   # la reseña se borró mientras tanto
    except Exception as e:
        print(f"[RESENAS] No se pudo corregir el nombre de la reseña {resena_id}: {e}")


def _recalcular_rating(db, tecnico_id: str) -> None:
    """Recalcula `rating` y `opiniones` del profesional a partir de todas sus reseñas.
    Solo actualiza un perfil que existe: si la cuenta se borró antes de que
    llegara el trigger de la reseña, no se recrea un perfil vacío."""
    q = db.collection("resenas").where(filter=FieldFilter("tecnicoId", "==", tecnico_id))
    docs = list(q.stream())
    n = len(docs)
    total = sum((d.to_dict() or {}).get("puntuacion", 0) for d in docs)
    promedio = round(total / n, 2) if n else 0.0
    try:
        db.collection("tecnicos").document(tecnico_id).update({"rating": promedio, "opiniones": n})
    except NotFound:
        pass


# ════════════════════════════════════════════════════════════════════════
# Trigger: denuncia nueva → evidencia y aviso al administrador
# ════════════════════════════════════════════════════════════════════════
#
# La app crea /denuncias/{autor}_{objetivo} (una por autor y objetivo; las
# reglas validan cada campo). Aquí se copia al propio documento una evidencia
# acotada, para que no se pierda si el denunciado borra lo que escribió, y se
# avisa por correo al buzón de ZeroHaus. Ni la evidencia ni el estado los
# puede escribir la app.

# Denuncias: raras, y cada una puede gastar un correo
MAX_INSTANCIAS_DENUNCIAS = 3
DENUNCIA_MENSAJES_MAX = 20     # últimos mensajes del denunciado que se copian
DENUNCIA_TEXTO_MAX = 500       # caracteres por mensaje copiado
DENUNCIA_URL_MAX = 2048        # el documento de la denuncia no crece sin límite
# Avisos de denuncias al día, además del cupo común de avisos: una ráfaga de
# denuncias no se come el correo de los demás avisos ni el de los códigos
AVISOS_DENUNCIA_MAX_DIA = int(os.environ.get("AVISOS_DENUNCIA_MAX_DIA", "20"))
# Denuncias de un mismo autor al día (UTC) que avisan por correo: a partir de
# la siguiente se guardan con `exceso` y sin correo, para que una sola cuenta
# no gaste el tope de todos denunciando a muchos usuarios
DENUNCIAS_MAX_DIA_AUTOR = 5
CONTEXTOS_DENUNCIA = {"chat", "perfil", "resena"}
MOTIVOS_DENUNCIA = {"spam", "acoso", "fraude", "inapropiado", "suplantacion", "otro"}


def _recortar(valor, maximo: int) -> str:
    """Texto de un campo copiado, con tope ("" si no hay)."""
    return "" if valor is None else str(valor)[:maximo]


def _entero(valor) -> int:
    """Número entero de un campo copiado (fecha, puntuación), o 0 si no lo es."""
    return int(valor) if isinstance(valor, (int, float)) and not isinstance(valor, bool) else 0


def _evidencia_chat(db, chat_id: str, autor: str, objetivo: str) -> dict:
    """Últimos mensajes del denunciado en el chat, solo si autor y denunciado
    participan en él: si no, cualquiera podría copiarse mensajes ajenos en su
    denuncia (que el autor puede leer). Usa el índice (emisorUid, fecha) de
    firestore.indexes.json."""
    chat = db.collection("chats").document(chat_id).get()
    participantes = (chat.to_dict() or {}).get("participantes") if chat.exists else None
    if not isinstance(participantes, list) or autor not in participantes or objetivo not in participantes:
        return {"nota": "El autor y el denunciado no comparten este chat: no se copian mensajes."}
    consulta = (chat.reference.collection("mensajes")
                .where(filter=FieldFilter("emisorUid", "==", objetivo))
                .order_by("fecha", direction=firestore.Query.DESCENDING)
                .limit(DENUNCIA_MENSAJES_MAX))
    mensajes = []
    for d in consulta.stream():
        m = d.to_dict() or {}
        mensajes.append({
            "texto": _recortar(m.get("texto"), DENUNCIA_TEXTO_MAX),
            "tipo": _recortar(m.get("tipo") or "texto", 20),
            "fecha": _entero(m.get("fecha")),
            "mediaUrl": _recortar(m.get("mediaUrl"), DENUNCIA_URL_MAX),
        })
    return {"mensajes": mensajes}


def _evidencia_resena(db, resena_id: str, objetivo: str) -> dict:
    """Puntuación, comentario y fecha de la reseña, si la escribió el denunciado."""
    snap = db.collection("resenas").document(resena_id).get()
    r = (snap.to_dict() or {}) if snap.exists else {}
    if not r or r.get("uid") != objetivo:
        return {"nota": "La reseña no existe o no es del denunciado."}
    return {
        "puntuacion": _entero(r.get("puntuacion")),
        "comentario": _recortar(r.get("comentario"), 1000),
        "fecha": _entero(r.get("fecha")),
    }


def _evidencia_perfil(db, perfil_id: str, objetivo: str) -> dict:
    """Nombre y descripción del perfil profesional, si es del denunciado (en
    los perfiles antiguos el id del documento no es el uid)."""
    snap = db.collection("tecnicos").document(perfil_id).get()
    p = (snap.to_dict() or {}) if snap.exists else {}
    if not p or p.get("uid") != objetivo:
        return {"nota": "El perfil no existe o no es del denunciado."}
    return {"nombre": _recortar(p.get("nombre"), 100), "descripcion": _recortar(p.get("descripcion"), 2000)}


def _evidencia_denuncia(db, d: dict) -> dict:
    """Evidencia según el contexto: {mensajes} del chat, la reseña o el perfil
    (campos sueltos), o {nota} si no hay nada que copiar."""
    autor, objetivo, referencia = d.get("autor"), d.get("objetivo"), d.get("referencia")
    # La referencia es un id de documento, nunca una ruta: con «/» se leería otro
    if not all(isinstance(v, str) and v and "/" not in v for v in (autor, objetivo, referencia)) \
            or len(referencia) > 300:
        return {"nota": "Referencia no válida: no se copia evidencia."}
    contexto = d.get("contexto")
    if contexto == "chat":
        return _evidencia_chat(db, referencia, autor, objetivo)
    if contexto == "resena":
        return _evidencia_resena(db, referencia, objetivo)
    if contexto == "perfil":
        return _evidencia_perfil(db, referencia, objetivo)
    return {"nota": "Contexto desconocido: no se copia evidencia."}


def _reservar_aviso_denuncia(db) -> str:
    """Reserva en una sola transacción un correo del cupo común de avisos
    (/limites_correo, el de _reservar_cupo_correo) y uno de los
    AVISOS_DENUNCIA_MAX_DIA de denuncias (/limites_denuncias/{día UTC}, solo
    servidor). Mira primero el común y después el de denuncias, y solo gasta
    si quedan los dos: así no se pierde uno cuando el otro está agotado.
    Devuelve "" si hay correo, o el cupo agotado ("correo" o "denuncias")."""
    hoy = _hoy_utc()
    correo_ref = db.collection("limites_correo").document(hoy)
    denuncias_ref = db.collection("limites_denuncias").document(hoy)

    @firestore.transactional
    def _reservar(txn):
        correo = correo_ref.get(transaction=txn).to_dict() or {}
        denuncias = denuncias_ref.get(transaction=txn).to_dict() or {}
        enviados, avisos = int(correo.get("enviados") or 0), int(correo.get("avisos") or 0)
        if enviados >= CORREOS_MAX_DIA or avisos >= CORREOS_AVISOS_MAX_DIA:
            return "correo"
        avisos_denuncias = int(denuncias.get("avisos") or 0)
        if avisos_denuncias >= AVISOS_DENUNCIA_MAX_DIA:
            return "denuncias"
        txn.set(correo_ref, {"enviados": enviados + 1, "avisos": avisos + 1})
        txn.set(denuncias_ref, {"avisos": avisos_denuncias + 1})
        return ""

    return _reservar(db.transaction())


def _avisar_denuncia(db, denuncia_id: str, d: dict) -> None:
    """Correo al buzón de ZeroHaus (el propio remitente) con lo justo para
    encontrarla en el panel: motivo, contexto e ids; ni el comentario ni la
    evidencia. Nunca rompe el trigger: sin cupo o si falla el SMTP, solo
    queda en el registro."""
    try:
        agotado = _reservar_aviso_denuncia(db)
        if agotado:
            print(f"[DENUNCIAS] Tope diario de {agotado} alcanzado: sin correo por {denuncia_id}")
            return
        motivo = d.get("motivo") if d.get("motivo") in MOTIVOS_DENUNCIA else "otro"
        contexto = d.get("contexto") if d.get("contexto") in CONTEXTOS_DENUNCIA else "?"
        texto = (
            "Hay una denuncia nueva en ZeroHaus.\n\n"
            f"Motivo: {motivo}\nContexto: {contexto}\n"
            f"Denuncia: {_recortar(denuncia_id, 300)}\n"
            f"Autor: {_recortar(d.get('autor'), 128)}\n"
            f"Denunciado: {_recortar(d.get('objetivo'), 128)}\n"
            f"Referencia: {_recortar(d.get('referencia'), 300)}\n\n"
            "Revísala en el panel de administración de la app."
        )
        _enviar_email(SMTP_USUARIO.value, f"Denuncia: {motivo} ({contexto})", texto,
                      "Aviso automático de ZeroHaus.", "Español")
    except Exception as e:
        print(f"[DENUNCIAS] No se pudo avisar de la denuncia {denuncia_id}: {e}")


@firestore_fn.on_document_created(
    document="denuncias/{denunciaId}",
    region=REGION,
    secrets=[SMTP_USUARIO, SMTP_CLAVE],
    max_instances=MAX_INSTANCIAS_DENUNCIAS,
)
def on_denuncia_creada(event) -> None:
    """Denuncia nueva: copia la evidencia, la deja `pendiente` y avisa al
    administrador, salvo que su autor pase de DENUNCIAS_MAX_DIA_AUTOR hoy:
    entonces se guarda con `exceso: true` y sin correo.

    Idempotente (Firestore puede entregar el evento más de una vez): si ya
    tiene `evidencia` no hace nada, y la evidencia y el contador del autor se
    guardan en una transacción que la comprueba otra vez; solo quien la
    guarda cuenta y avisa."""
    if event.data is None:
        return
    denuncia_id = event.params["denunciaId"]
    db = firestore.client()
    ref = db.collection("denuncias").document(denuncia_id)
    snap = ref.get()
    datos = (snap.to_dict() or {}) if snap.exists else None
    if datos is None or "evidencia" in datos:
        return   # borrada por el administrador o ya procesada
    try:
        evidencia = _evidencia_denuncia(db, datos)
    except Exception as e:
        # Sin evidencia se avisa igual: el administrador puede mirar el contenido
        print(f"[DENUNCIAS] No se pudo copiar la evidencia de {denuncia_id}: {e}")
        evidencia = {"nota": "No se pudo copiar la evidencia: revisa el contenido directamente."}

    autor = datos.get("autor") if isinstance(datos.get("autor"), str) else ""
    hoy = _hoy_utc()
    # Un autor raro (solo posible con el Admin SDK) cuenta aparte, sin «/» en el id
    contador_ref = db.collection("limites_denuncias_autor").document(
        f"{autor}_{hoy}" if autor and "/" not in autor else f"_{hoy}")

    @firestore.transactional
    def _guardar(txn):
        """None si ya estaba guardada o no existe; si no, si es un exceso."""
        actual = ref.get(transaction=txn)
        d = (actual.to_dict() or {}) if actual.exists else None
        if d is None or "evidencia" in d:
            return None
        contador = contador_ref.get(transaction=txn).to_dict() or {}
        hechas = int(contador.get("denuncias") or 0) + 1
        cambios = {"evidencia": evidencia}
        if "estado" not in d:   # no se pisa si el administrador ya la gestionó
            cambios["estado"] = "pendiente"
        if hechas > DENUNCIAS_MAX_DIA_AUTOR:
            cambios["exceso"] = True
        txn.update(ref, cambios)
        txn.set(contador_ref, {"uid": autor, "dia": hoy, "denuncias": hechas})
        return hechas > DENUNCIAS_MAX_DIA_AUTOR

    exceso = _guardar(db.transaction())
    if exceso is False:
        _avisar_denuncia(db, denuncia_id, datos)
    elif exceso:
        print(f"[DENUNCIAS] {denuncia_id}: su autor pasa de {DENUNCIAS_MAX_DIA_AUTOR} hoy, sin correo")


# ════════════════════════════════════════════════════════════════════════
# Verificación del email con código de 6 dígitos
# ════════════════════════════════════════════════════════════════════════
#
# Sin email verificado no se entra en la app (y las rules lo exigen para
# cualquier dato). Flujo: la app pide `enviar_codigo_verificacion`, el
# usuario recibe un código de 6 dígitos por email y lo manda a
# `verificar_codigo_email`, que marca el email como verificado en Auth.
#
# - El código se genera con `secrets` (aleatorio criptográfico) y NUNCA se
#   repite para el mismo usuario: se guarda la huella de todos los emitidos.
# - Solo se guarda su HMAC, nunca el código en claro. Un solo uso, caduca a
#   los 10 minutos y admite 5 intentos; después hay que pedir otro.
# - Envío: 1 por minuto y 10 al día por usuario.
# - El correo sale por SMTP con credenciales en Secret Manager:
#     firebase functions:secrets:set SMTP_USUARIO   (p. ej. cuenta@gmail.com)
#     firebase functions:secrets:set SMTP_CLAVE     (contraseña de aplicación)
#   Servidor por defecto: Gmail (smtp.gmail.com:465). Otro proveedor:
#   variables de entorno SMTP_HOST / SMTP_PUERTO.

CODIGO_VALIDEZ_MS = 10 * 60 * 1000
CODIGO_MAX_INTENTOS = 5
ENVIO_ESPERA_MS = 60 * 1000
ENVIOS_MAX_DIA = 10
HISTORIAL_MAX = 200

# asunto, cuerpo (con {codigo}), aviso final — en los 14 idiomas de la app
_TEXTOS_EMAIL = {
    "Español": ("Tu código de verificación de ZeroHaus", "Tu código es {codigo}. Introdúcelo en la app para verificar tu email. Caduca en 10 minutos.", "Si no has creado una cuenta en ZeroHaus, ignora este mensaje."),
    "English": ("Your ZeroHaus verification code", "Your code is {codigo}. Enter it in the app to verify your email. It expires in 10 minutes.", "If you didn't create a ZeroHaus account, ignore this message."),
    "Català": ("El teu codi de verificació de ZeroHaus", "El teu codi és {codigo}. Introdueix-lo a l'app per verificar el teu correu. Caduca en 10 minuts.", "Si no has creat cap compte a ZeroHaus, ignora aquest missatge."),
    "Euskara": ("Zure ZeroHaus egiaztapen-kodea", "Zure kodea {codigo} da. Sartu aplikazioan zure posta egiaztatzeko. 10 minututan iraungitzen da.", "Ez baduzu ZeroHaus kontu bat sortu, ez egin kasurik mezu honi."),
    "Galego": ("O teu código de verificación de ZeroHaus", "O teu código é {codigo}. Introdúceo na app para verificar o teu correo. Caduca en 10 minutos.", "Se non creaches unha conta en ZeroHaus, ignora esta mensaxe."),
    "Português": ("O seu código de verificação ZeroHaus", "O seu código é {codigo}. Introduza-o na app para verificar o seu email. Expira em 10 minutos.", "Se não criou uma conta ZeroHaus, ignore esta mensagem."),
    "Français": ("Votre code de vérification ZeroHaus", "Votre code est {codigo}. Saisissez-le dans l'app pour vérifier votre e-mail. Il expire dans 10 minutes.", "Si vous n'avez pas créé de compte ZeroHaus, ignorez ce message."),
    "Deutsch": ("Dein ZeroHaus-Bestätigungscode", "Dein Code lautet {codigo}. Gib ihn in der App ein, um deine E-Mail zu bestätigen. Er läuft in 10 Minuten ab.", "Wenn du kein ZeroHaus-Konto erstellt hast, ignoriere diese Nachricht."),
    "Italiano": ("Il tuo codice di verifica ZeroHaus", "Il tuo codice è {codigo}. Inseriscilo nell'app per verificare la tua email. Scade tra 10 minuti.", "Se non hai creato un account ZeroHaus, ignora questo messaggio."),
    "العربية": ("رمز التحقق الخاص بك في ZeroHaus", "رمزك هو {codigo}. أدخله في التطبيق للتحقق من بريدك الإلكتروني. تنتهي صلاحيته خلال 10 دقائق.", "إذا لم تنشئ حساباً في ZeroHaus، فتجاهل هذه الرسالة."),
    "中文": ("您的 ZeroHaus 验证码", "您的验证码是 {codigo}。请在应用中输入以验证您的邮箱，10 分钟内有效。", "如果您没有注册 ZeroHaus 账户，请忽略此邮件。"),
    "Română": ("Codul tău de verificare ZeroHaus", "Codul tău este {codigo}. Introdu-l în aplicație pentru a-ți verifica emailul. Expiră în 10 minute.", "Dacă nu ți-ai creat un cont ZeroHaus, ignoră acest mesaj."),
    "Nederlands": ("Je ZeroHaus-verificatiecode", "Je code is {codigo}. Voer hem in de app in om je e-mail te verifiëren. Hij verloopt over 10 minuten.", "Heb je geen ZeroHaus-account aangemaakt? Negeer dan dit bericht."),
    "Polski": ("Twój kod weryfikacyjny ZeroHaus", "Twój kod to {codigo}. Wpisz go w aplikacji, aby zweryfikować e-mail. Wygasa za 10 minut.", "Jeśli nie zakładałeś konta ZeroHaus, zignoruj tę wiadomość."),
}


def _normalizar_email(email) -> str:
    return (email or "").strip().lower()


def _huella_codigo(uid: str, email: str, codigo: str) -> str:
    """HMAC del código ligado al usuario y al email al que se envió: en
    Firestore nunca está en claro."""
    return hmac.new(SMTP_CLAVE.value.encode(), f"{uid}:{email}:{codigo}".encode(), hashlib.sha256).hexdigest()


def _generar_codigo_nuevo(uid: str, email: str, historial: list) -> tuple:
    """Código aleatorio de 6 dígitos que este usuario no ha recibido nunca."""
    usados = set(historial)
    for _ in range(50):
        codigo = f"{secrets.randbelow(1_000_000):06d}"
        huella = _huella_codigo(uid, email, codigo)
        if huella not in usados:
            return codigo, huella
    raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INTERNAL, "No se pudo generar el código.")


def _enviar_email(destino: str, asunto: str, texto: str, pie: str, idioma: str, destacado: str = "") -> None:
    """Email de ZeroHaus (texto plano + HTML) por SMTP. `destacado` se muestra
    grande (el código de verificación). Todo el texto se escapa en el HTML."""
    msg = EmailMessage()
    msg["Subject"] = asunto
    msg["From"] = formataddr(("ZeroHaus", SMTP_USUARIO.value))
    msg["To"] = destino
    msg.set_content(f"{texto}\n\n{destacado}\n\n{pie}".replace("\n\n\n\n", "\n\n"))
    direccion = "rtl" if idioma == "العربية" else "ltr"
    bloque = "" if not destacado else f"""
  <p style="font-size:34px;font-weight:bold;letter-spacing:10px;text-align:center;
            background:#f0fdf4;border-radius:12px;padding:16px;margin:20px 0;color:#166534">{html.escape(destacado)}</p>"""
    msg.add_alternative(f"""\
<div dir="{direccion}" style="font-family:Arial,sans-serif;max-width:440px;margin:auto;padding:24px;color:#1f2937">
  <h2 style="color:#166534;margin:0 0 16px">ZeroHaus</h2>
  <p style="font-size:15px;white-space:pre-line">{html.escape(texto)}</p>{bloque}
  <p style="font-size:12px;color:#6b7280">{html.escape(pie)}</p>
</div>""", subtype="html")

    contexto = ssl.create_default_context()
    if SMTP_PUERTO == 465:
        with smtplib.SMTP_SSL(SMTP_HOST, SMTP_PUERTO, context=contexto, timeout=20) as s:
            s.login(SMTP_USUARIO.value, SMTP_CLAVE.value)
            s.send_message(msg)
    else:
        with smtplib.SMTP(SMTP_HOST, SMTP_PUERTO, timeout=20) as s:
            s.starttls(context=contexto)
            s.login(SMTP_USUARIO.value, SMTP_CLAVE.value)
            s.send_message(msg)


def _enviar_email_codigo(destino: str, codigo: str, idioma: str) -> None:
    """Email con el código de verificación, en el idioma del usuario."""
    asunto, cuerpo, aviso = _TEXTOS_EMAIL.get(idioma, _TEXTOS_EMAIL["Español"])
    _enviar_email(
        destino, f"{codigo} · {asunto}",
        cuerpo.format(codigo=codigo),
        aviso, idioma, destacado=codigo,
    )


# Con App Check, como el resto de funciones: sin él, cualquiera podía crear
# cuentas con correos ajenos y usar esta función para mandarles códigos desde
# la cuenta de ZeroHaus. Los builds de depuración usan el token de depuración
# (Util/ProveedorAppCheck.kt) y la app de Google Play, Play Integrity.
@https_fn.on_call(region=REGION, enforce_app_check=True, secrets=[SMTP_USUARIO, SMTP_CLAVE])
def enviar_codigo_verificacion(req: https_fn.CallableRequest) -> dict:
    """Envía un código nuevo. Args: idioma (str). Devuelve {verificado} o
    {enviado, esperaSeg} si hay que esperar para reenviar."""
    if req.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión.")
    uid = req.auth.uid
    usuario = auth.get_user(uid)
    db = firestore.client()
    if usuario.email_verified and _ya_verificada(db, uid):
        return {"verificado": True}
    if not usuario.email:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.FAILED_PRECONDITION, "La cuenta no tiene email.")

    idioma = _idioma_conocido((req.data or {}).get("idioma"))
    email = _normalizar_email(usuario.email)
    ref = db.collection("verificaciones_email").document(uid)
    ahora = _ahora_ms()
    hoy = _hoy_utc()

    @firestore.transactional
    def _reservar(txn):
        d = ref.get(transaction=txn).to_dict() or {}
        espera = int(d.get("ultimoEnvio") or 0) + ENVIO_ESPERA_MS - ahora
        if espera > 0:
            return None, espera
        envios_hoy = int(d.get("enviosHoy") or 0) if d.get("dia") == hoy else 0
        if envios_hoy >= ENVIOS_MAX_DIA:
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED,
                "Has pedido demasiados códigos hoy. Inténtalo mañana.",
                {"motivo": "envios"},
            )
        historial = list(d.get("historial") or [])
        codigo, huella = _generar_codigo_nuevo(uid, email, historial)
        txn.set(ref, {
            "huella": huella,
            # El código solo vale para este email: si el de la cuenta cambia, caduca
            "email": email,
            "expira": ahora + CODIGO_VALIDEZ_MS,
            "intentos": 0,
            "ultimoEnvio": ahora,
            "dia": hoy,
            "enviosHoy": envios_hoy + 1,
            "historial": (historial + [huella])[-HISTORIAL_MAX:],
        })
        return codigo, 0

    codigo, espera = _reservar(db.transaction())
    if codigo is None:
        return {"enviado": False, "esperaSeg": (espera + 999) // 1000}

    if not _reservar_cupo_correo(db):
        print("[VERIFICACION] Tope diario de correos alcanzado")
        ref.update({"ultimoEnvio": 0, "huella": firestore.DELETE_FIELD})
        # Mismo motivo que el límite por usuario: la app ya dice «inténtalo mañana»
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED,
            "Se han enviado demasiados códigos hoy. Inténtalo mañana.",
            {"motivo": "envios"},
        )

    try:
        _enviar_email_codigo(usuario.email, codigo, idioma)
    except Exception as e:
        print(f"[VERIFICACION] Error SMTP: {e}")
        # Deja reintentar enseguida si el envío falló
        ref.update({"ultimoEnvio": 0, "huella": firestore.DELETE_FIELD})
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAVAILABLE,
            "No se pudo enviar el email. Inténtalo de nuevo en unos minutos.",
        )
    return {"enviado": True, "esperaSeg": ENVIO_ESPERA_MS // 1000}


@https_fn.on_call(region=REGION, enforce_app_check=True, secrets=[SMTP_USUARIO, SMTP_CLAVE])
def verificar_codigo_email(req: https_fn.CallableRequest) -> dict:
    """Comprueba el código. Args: codigo (str de 6 dígitos)."""
    if req.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión.")
    uid = req.auth.uid
    codigo = str((req.data or {}).get("codigo") or "").strip()
    if not re.fullmatch(r"\d{6}", codigo):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "El código tiene 6 dígitos.")

    usuario = auth.get_user(uid)
    db = firestore.client()
    if usuario.email_verified and _ya_verificada(db, uid):
        return {"verificado": True}
    email = _normalizar_email(usuario.email)

    ref = db.collection("verificaciones_email").document(uid)
    ahora = _ahora_ms()

    @firestore.transactional
    def _comprobar(txn):
        d = ref.get(transaction=txn).to_dict() or {}
        huella = d.get("huella")
        # Si el email de la cuenta ya no es al que se envió el código, no vale
        if not huella or int(d.get("expira") or 0) < ahora or not email or d.get("email") != email:
            return "caducado", 0
        intentos = int(d.get("intentos") or 0)
        if intentos >= CODIGO_MAX_INTENTOS:
            return "bloqueado", 0
        if hmac.compare_digest(huella, _huella_codigo(uid, email, codigo)):
            # Un solo uso: se borra la huella activa (queda en el historial)
            txn.update(ref, {"huella": firestore.DELETE_FIELD, "intentos": 0})
            return "ok", 0
        intentos += 1
        cambios = {"intentos": intentos}
        if intentos >= CODIGO_MAX_INTENTOS:
            cambios["huella"] = firestore.DELETE_FIELD
        txn.update(ref, cambios)
        return "incorrecto", CODIGO_MAX_INTENTOS - intentos

    caducado = https_fn.HttpsError(
        https_fn.FunctionsErrorCode.FAILED_PRECONDITION, "El código ha caducado. Pide uno nuevo.", {"motivo": "caducado"})
    resultado, restantes = _comprobar(db.transaction())
    if resultado == "caducado":
        raise caducado
    if resultado == "bloqueado":
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED, "Demasiados intentos. Pide un código nuevo.", {"motivo": "intentos"})
    if resultado == "incorrecto":
        if restantes == 0:
            raise https_fn.HttpsError(https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED, "Demasiados intentos. Pide un código nuevo.", {"motivo": "intentos"})
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
            "Código incorrecto.",
            {"restantes": restantes},
        )

    no_confirmada = https_fn.HttpsError(
        https_fn.FunctionsErrorCode.UNAVAILABLE, "No se pudo verificar el email. Pide un código nuevo en unos minutos.")
    # Si el email de la cuenta cambió entre la comprobación y este momento, la
    # verificación sería de otro correo: se deshace y el código no vale. Si no
    # se puede confirmar (Auth no responde), también: falla en cerrado.
    try:
        auth.update_user(uid, email_verified=True)
        email_actual = _normalizar_email(auth.get_user(uid).email)
    except Exception as e:
        print(f"[SEGURIDAD] No se pudo confirmar la verificación de {uid}: {e}")
        _deshacer_o_anotar(db, uid, email)
        raise no_confirmada
    if email_actual != email:
        print(f"[SEGURIDAD] El email de {uid} cambió mientras se verificaba: se deshace")
        if not _deshacer_o_anotar(db, uid, email):
            raise no_confirmada
        raise caducado
    # Verificación confirmada: una marca de un fallo anterior ya no aplica. La
    # marca guarda el email de aquel código: si la cuenta verifica ahora otro,
    # la tarea diaria la desharía, así que si no se puede quitar se reintenta.
    for intento in (1, 2):
        try:
            _quitar_marca_deshacer(db, uid)
            break
        except Exception as e:
            print(f"[SEGURIDAD] No se pudo quitar la marca de deshacer de {uid} (intento {intento}): {e}")
    _marcar_perfil_verificado(db, uid)
    return {"verificado": True}


# Si no se puede deshacer una verificación, queda anotado en
# /verificaciones_email/{uid} (deshacerPendiente y deshacerEmail, el email al
# que se envió el código: el único que el código verificó). Mientras esté la
# marca se reintenta al pedir o comprobar un código y en la tarea diaria, y
# solo se deshace si la cuenta sigue verificada con otro email; si ya tiene
# ese email o no está verificada, la marca se quita sin tocar la cuenta.

def _quitar_marca_deshacer(db, uid: str) -> None:
    try:
        db.collection("verificaciones_email").document(uid).update(
            {"deshacerPendiente": firestore.DELETE_FIELD, "deshacerEmail": firestore.DELETE_FIELD})
    except NotFound:
        pass

def _deshacer_o_anotar(db, uid: str, email: str) -> bool:
    """Deshace la verificación; si no se puede, deja la marca para
    reintentarlo. True si se deshizo."""
    if _deshacer_verificacion(uid):
        return True
    try:
        db.collection("verificaciones_email").document(uid).set(
            {"deshacerPendiente": True, "deshacerEmail": email}, merge=True)
    except Exception as e:
        print(f"[SEGURIDAD] No se pudo anotar el deshacer pendiente de {uid}: {e}")
    return False


def _reintentar_deshacer(db, uid: str) -> str:
    """Resuelve la marca de deshacer de `uid`:
    ""            no hay marca;
    "pendiente"   la cuenta sigue verificada con otro email y aún no se pudo deshacer;
    "sin_verificar" la cuenta no está verificada (se ha deshecho ahora o ya no
                  lo estaba): se quita la marca;
    "legitima"    la cuenta tiene el email al que se envió el código: la
                  verificación vale y se quita la marca sin tocar nada."""
    ref = db.collection("verificaciones_email").document(uid)
    marca = ref.get().to_dict() or {}
    if marca.get("deshacerPendiente") is not True:
        return ""
    try:
        usuario = auth.get_user(uid)
    except auth.UserNotFoundError:
        usuario = None
    except Exception as e:
        print(f"[SEGURIDAD] No se pudo consultar Auth para el deshacer pendiente de {uid}: {e}")
        return "pendiente"
    email_del_codigo = _normalizar_email(marca.get("deshacerEmail"))
    if usuario is None or not usuario.email_verified:
        estado = "sin_verificar"
    elif email_del_codigo and _normalizar_email(usuario.email) == email_del_codigo:
        estado = "legitima"
    elif _deshacer_verificacion(uid):
        estado = "sin_verificar"
        print(f"[SEGURIDAD] Deshecha la verificación pendiente de {uid}")
    else:
        return "pendiente"
    _quitar_marca_deshacer(db, uid)
    if estado == "legitima":
        print(f"[SEGURIDAD] Marca de deshacer de {uid} sin efecto: la cuenta tiene el email verificado por el código")
    return estado


def _ya_verificada(db, uid: str) -> bool:
    """Para una cuenta que Auth da por verificada: True si lo está de verdad
    (y entonces muestra su perfil, por si falló al marcarlo); False si tenía
    la verificación por deshacer y ahora se ha deshecho (sigue como una cuenta
    sin verificar). Si aún no se puede deshacer, UNAVAILABLE."""
    estado = _reintentar_deshacer(db, uid)
    if estado == "pendiente":
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAVAILABLE, "No se pudo verificar el email. Inténtalo de nuevo en unos minutos.")
    if estado == "sin_verificar":
        return False
    _marcar_perfil_verificado(db, uid)
    return True


def _uids_deshacer_pendiente(db) -> set:
    return {d.id for d in db.collection("verificaciones_email")
            .where(filter=FieldFilter("deshacerPendiente", "==", True)).stream()}


def _reintentar_deshacer_pendientes(db) -> int:
    """Tarea diaria: resuelve cada marca de deshacer pendiente. Devuelve
    cuántas se han resuelto (deshechas o sin efecto)."""
    resueltas = 0
    for uid in _uids_deshacer_pendiente(db):
        try:
            if _reintentar_deshacer(db, uid) in ("sin_verificar", "legitima"):
                resueltas += 1
        except Exception as e:
            print(f"[SEGURIDAD] No se pudo reintentar el deshacer de {uid}: {e}")
    return resueltas


def _deshacer_verificacion(uid: str) -> bool:
    """Quita la marca de email verificado y cierra las sesiones de la cuenta
    (un token emitido mientras estaba marcada ya no se renueva). True si la
    marca se quitó; los fallos quedan en el registro con [SEGURIDAD]."""
    deshecha = True
    try:
        auth.update_user(uid, email_verified=False)
    except Exception as e:
        deshecha = False
        print(f"[SEGURIDAD] No se pudo deshacer la verificación de {uid}: {e}")
    try:
        auth.revoke_refresh_tokens(uid)
    except Exception as e:
        print(f"[SEGURIDAD] No se pudieron cerrar las sesiones de {uid}: {e}")
    return deshecha


# ════════════════════════════════════════════════════════════════════════
# Perfil profesional: en el directorio solo con el email verificado
# ════════════════════════════════════════════════════════════════════════
#
# /tecnicos/{id}.emailVerificado lo escribe solo el servidor (las reglas no
# dejan al cliente ponerlo a true ni quitarlo). La app oculta del directorio y
# del mapa los perfiles con false; los anteriores a este campo no lo tienen y
# se siguen mostrando.

def _marcar_perfil_verificado(db, uid: str) -> None:
    """Hace visible el perfil profesional de `uid` tras verificar su email.
    Siempre después de actualizar Auth (on_tecnico_creado depende de ese orden).
    Sin perfil no hace nada; otros fallos se registran sin romper la verificación
    (los corrige la revisión diaria, _mostrar_perfiles_verificados)."""
    try:
        db.collection("tecnicos").document(uid).update({"emailVerificado": True})
    except NotFound:
        pass
    except Exception as e:
        print(f"[VERIFICACION] No se pudo marcar el perfil de {uid}: {e}")


def _email_verificado_en_auth(uid: str) -> bool:
    """True solo si Auth confirma que la cuenta tiene el email verificado. Si
    no existe o Auth falla, False: el perfil queda oculto y la revisión diaria
    lo corrige cuando la cuenta esté verificada."""
    try:
        return auth.get_user(uid).email_verified is True
    except Exception as e:
        if not isinstance(e, auth.UserNotFoundError):
            print(f"[TECNICOS] No se pudo consultar Auth para {uid}: {e}")
        return False


def _anotar_email_verificado(db, ref, uid: str, respetar_true: bool, oculto: bool = False) -> None:
    """Pone emailVerificado del perfil según Auth, en una transacción. Con
    `respetar_true` no pisa un true que haya puesto ya la verificación del
    código. Con `oculto` queda en false sin consultar Auth."""

    @firestore.transactional
    def _anotar(txn):
        snap = ref.get(transaction=txn)
        if not snap.exists:
            return
        actual = (snap.to_dict() or {}).get("emailVerificado")
        if actual is True and respetar_true and not oculto:
            return
        verificado = False if oculto else _email_verificado_en_auth(uid)
        if actual is not verificado:
            txn.update(ref, {"emailVerificado": verificado})

    _anotar(db.transaction())


def _tiene_marca_deshacer(db, uid: str) -> bool:
    """True si la verificación de `uid` está pendiente de deshacer (o no se
    puede comprobar: en la duda, el perfil queda oculto)."""
    try:
        marca = db.collection("verificaciones_email").document(uid).get().to_dict() or {}
    except Exception as e:
        print(f"[TECNICOS] No se pudo leer la verificación de {uid}: {e}")
        return True
    return marca.get("deshacerPendiente") is True


@firestore_fn.on_document_created(document="tecnicos/{tecnicoId}", region=REGION)
def on_tecnico_creado(event) -> None:
    """Perfil profesional nuevo (la app lo crea al registrarse, antes de
    verificar el email): queda oculto del directorio hasta que la cuenta lo verifica."""
    if event.data is None:
        return
    creado = event.data.to_dict() or {}
    uid = creado.get("uid") or ""
    if not uid:
        return  # sin cuenta asociada: solo puede crearlo el administrador
    db = firestore.client()
    ref = db.collection("tecnicos").document(event.params["tecnicoId"])
    # Un true que ya venía en el alta no lo puso el servidor: se decide por Auth.
    # Solo se respeta el que apareció después (la verificación del código).
    # Con una verificación pendiente de deshacer, oculto aunque Auth diga que sí.
    _anotar_email_verificado(db, ref, uid, respetar_true=creado.get("emailVerificado") is not True,
                             oculto=_tiene_marca_deshacer(db, uid))


AUTH_LOTE = 100   # máximo de identificadores por llamada a auth.get_users


def _mostrar_perfiles_verificados(db) -> int:
    """Pone emailVerificado a true en los perfiles ocultos cuya cuenta ya tiene
    el email verificado: cubre un fallo al marcarlo, perfiles cuyo id no es el
    uid y verificaciones por otra vía. Consulta Auth por lotes y un fallo (de
    un lote o de un perfil) no detiene el resto. No muestra los de cuentas con
    la verificación pendiente de deshacer. Devuelve cuántos."""
    pendientes = _uids_deshacer_pendiente(db)
    ocultos = []
    for d in db.collection("tecnicos").where(filter=FieldFilter("emailVerificado", "==", False)).stream():
        uid = (d.to_dict() or {}).get("uid") or ""
        if uid and uid not in pendientes:
            ocultos.append((d, uid))
    corregidos = 0
    for i in range(0, len(ocultos), AUTH_LOTE):
        lote = ocultos[i:i + AUTH_LOTE]
        try:
            resultado = auth.get_users([auth.UidIdentifier(u) for u in sorted({uid for _, uid in lote})])
            verificados = {u.uid for u in resultado.users if u.email_verified is True}
        except Exception as e:
            print(f"[TECNICOS] No se pudo consultar Auth para un lote de perfiles: {e}")
            continue
        for d, uid in lote:
            if uid not in verificados:
                continue
            try:
                d.reference.update({"emailVerificado": True})
                corregidos += 1
            except Exception as e:
                print(f"[TECNICOS] No se pudo mostrar el perfil {d.id}: {e}")
    return corregidos


# ════════════════════════════════════════════════════════════════════════
# IA: consejos personalizados sobre el informe (Gemini en Vertex AI)
# ════════════════════════════════════════════════════════════════════════
#
# Se ejecuta en el servidor con la cuenta de servicio de la función: la app no
# lleva ninguna clave. A Gemini solo se le envían las características técnicas
# de la vivienda y los resultados del informe (nunca nombre, dirección ni
# email). El resultado se guarda en el propio informe (caché por idioma) y hay
# un límite diario por usuario para acotar el coste.

# gemini-2.0-flash se apagó en junio de 2026 y gemini-2.5-flash el 20/10/2026
# (solo sigue para proyectos que ya lo usaban). Se prueba primero la región de
# la UE y, si el modelo no está disponible allí, el endpoint global. OJO: en
# los logs de octubre de 2026 europe-west1 devuelve 404 para los dos modelos,
# así que en la práctica responde siempre `global` (lo dice la política de
# privacidad). Si Google los publica en europe-west1, se usarán sin cambios.
GEMINI_MODELOS = [m for m in os.environ.get("GEMINI_MODEL", "gemini-3.5-flash,gemini-3.8-flash").split(",") if m]
GEMINI_UBICACIONES = ["europe-west1", "global"]
IA_LIMITE_DIARIO = int(os.environ.get("IA_LIMITE_DIARIO", "10"))
# Tope de consultas a la IA al día entre todos los usuarios: acota el coste
# aunque alguien use muchas cuentas
IA_LIMITE_GLOBAL_DIARIO = int(os.environ.get("IA_LIMITE_GLOBAL_DIARIO", "500"))
# Lo que escribe el usuario (informe y vivienda) entra recortado en el prompt
IA_TEXTO_MAX = 200
IA_RECOMENDACIONES_MAX = 12
IA_PROMPT_MAX = 8000
_CONTROL = re.compile(r"[\x00-\x1f\x7f]")

_ESQUEMA_IA = {
    "type": "OBJECT",
    "properties": {
        "resumen": {"type": "STRING"},
        "consejos": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "titulo": {"type": "STRING"},
                    "detalle": {"type": "STRING"},
                    "prioridad": {"type": "STRING", "enum": ["alta", "media", "baja"]},
                    "coste": {"type": "STRING", "enum": ["bajo", "medio", "alto"]},
                },
                "required": ["titulo", "detalle", "prioridad", "coste"],
            },
        },
        "habitos": {"type": "ARRAY", "items": {"type": "STRING"}},
    },
    "required": ["resumen", "consejos", "habitos"],
}

_CAMPOS_VIVIENDA_IA = [
    "superficie", "anioConstruccion", "tipoVivienda", "provincia", "orientacion",
    "tipoVentanas", "aislamiento", "calefaccion", "acs", "refrigeracion",
    "iluminacion", "fotovoltaica", "electrodomesticos", "ocupantes",
    "potenciaContratadaKw",
]


def _dato_ia(valor):
    """Un dato del usuario tal como entra en el prompt: los números tal cual y
    el resto como texto de IA_TEXTO_MAX caracteres, sin caracteres de control."""
    if valor is None or isinstance(valor, (bool, int, float)):
        return valor
    return _CONTROL.sub(" ", str(valor)[:IA_TEXTO_MAX])


def _prompt_ia(informe: dict, vivienda: dict, idioma: str) -> str:
    """Construye el prompt para Gemini SIN datos personales (ni nombre, ni
    dirección, ni email): solo características técnicas y resultados.

    Los campos los escribe el usuario en Firestore: cada texto se recorta a
    IA_TEXTO_MAX caracteres, van como mucho IA_RECOMENDACIONES_MAX mejoras y
    el prompt no pasa de IA_PROMPT_MAX caracteres (si hace falta, sobran antes
    las mejoras, que ya están en el informe, que los datos de la vivienda)."""
    idioma = _idioma_conocido(idioma)
    datos_vivienda = {
        k: _dato_ia(vivienda.get(k)) for k in _CAMPOS_VIVIENDA_IA if vivienda.get(k) not in (None, "", 0)
    }
    mejoras = informe.get("recomendaciones")
    recomendaciones = [
        {"mejora": _dato_ia(r.get("titulo")), "ahorro_eur_anio": _dato_ia(r.get("ahorroEuros")),
         "ahorro_pct": _dato_ia(r.get("ahorroEstimado"))}
        for r in (mejoras if isinstance(mejoras, list) else []) if isinstance(r, dict)
    ][:IA_RECOMENDACIONES_MAX]
    resultado = {
        "etiqueta": _dato_ia(informe.get("etiqueta")),
        "consumo_kwh_anio": _dato_ia(informe.get("consumoEstimado")),
        "consumo_kwh_m2_anio": _dato_ia(informe.get("consumoPorM2")),
        "emisiones_kg_co2_anio": _dato_ia(informe.get("emisiones")),
        "coste_eur_anio": _dato_ia(informe.get("costeAnual")),
    }
    # Si el usuario añadió su factura de la luz: consumo real y precio que paga
    factura = informe.get("consumoLuzFactura")
    if isinstance(factura, (int, float)) and factura > 0:
        resultado["consumo_luz_estimado_kwh_anio"] = _dato_ia(informe.get("consumoLuzEstimado"))
        resultado["consumo_luz_real_factura_kwh_anio"] = _dato_ia(factura)
        resultado["precio_luz_factura_eur_kwh"] = _dato_ia(informe.get("precioLuz"))

    prompt = _texto_prompt_ia(datos_vivienda, resultado, recomendaciones, idioma)
    while len(prompt) > IA_PROMPT_MAX and recomendaciones:
        recomendaciones.pop()
        prompt = _texto_prompt_ia(datos_vivienda, resultado, recomendaciones, idioma)
    while len(prompt) > IA_PROMPT_MAX and datos_vivienda:
        datos_vivienda.popitem()
        prompt = _texto_prompt_ia(datos_vivienda, resultado, recomendaciones, idioma)
    return prompt


def _texto_prompt_ia(datos_vivienda: dict, resultado: dict, recomendaciones: list, idioma: str) -> str:
    return (
        "Eres un asesor de eficiencia energética de viviendas en España. "
        "Con los datos de esta vivienda y su informe, da consejos concretos, "
        "realistas y ordenados por impacto. No repitas literalmente las mejoras "
        "ya calculadas: explica cuál priorizar y por qué según la zona y el tipo "
        "de vivienda, y añade consejos que el cálculo no contempla (ventilación, "
        "puentes térmicos, termostato, tarifa eléctrica, ayudas públicas españolas "
        "como el PREE o las deducciones del IRPF por eficiencia). No inventes "
        "cifras distintas de las del informe. Si hay datos de la factura de la luz, "
        "compara el consumo real con el estimado y, si la potencia contratada "
        "parece alta para la vivienda, sugiere revisarla.\n\n"
        f"VIVIENDA: {json.dumps(datos_vivienda, ensure_ascii=False)}\n"
        f"INFORME: {json.dumps(resultado, ensure_ascii=False)}\n"
        f"MEJORAS YA CALCULADAS: {json.dumps(recomendaciones, ensure_ascii=False)}\n\n"
        f"Responde en el idioma «{idioma}». 'resumen': 2-3 frases sobre la situación. "
        "'consejos': entre 3 y 5, título corto y detalle de 1-3 frases. "
        "'habitos': 3 hábitos sin coste para ahorrar desde hoy."
    )


def _llamar_gemini(contenido, esquema: dict = _ESQUEMA_IA, temperatura: float = 0.4) -> dict:
    """Llama a Gemini en Vertex AI con respuesta JSON estructurada según
    `esquema`. `contenido` es el prompt o una lista de partes (texto, imagen,
    PDF). Prueba cada modelo configurado en cada región hasta que uno responda."""
    from google import genai
    from google.genai import types

    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=esquema,
        temperature=temperatura,
        # Los Gemini 3 razonan antes de responder y ese razonamiento cuenta
        # dentro de max_output_tokens: nivel bajo para que el JSON no se corte
        # y la respuesta tarde menos.
        thinking_config=types.ThinkingConfig(thinking_level=types.ThinkingLevel.LOW),
        max_output_tokens=4096,
    )
    ultimo_error = None
    for ubicacion in GEMINI_UBICACIONES:
        client = genai.Client(vertexai=True, project=PROJECT_ID, location=ubicacion)
        for modelo in GEMINI_MODELOS:
            try:
                resp = client.models.generate_content(model=modelo, contents=contenido, config=config)
                return json.loads(resp.text)
            except Exception as e:  # modelo no disponible en la región, cuota, etc.
                ultimo_error = e
                print(f"[IA] {modelo}@{ubicacion} falló: {str(e)[:200]}")
    raise RuntimeError(f"Gemini no disponible: {ultimo_error}")


def _consumir_cupo_ia(db, uid: str) -> None:
    """Gasta una consulta del límite diario de IA del usuario (compartido por
    los consejos y la lectura de facturas) y del tope del día entre todos
    (/limites_ia_global/{AAAAMMDD}), en la misma transacción para que no se
    cuele en paralelo. Si no quedan, lanza RESOURCE_EXHAUSTED sin gastar nada."""
    hoy = _hoy_utc()
    limite_ref = db.collection("limites_ia").document(uid)
    global_ref = db.collection("limites_ia_global").document(hoy)

    @firestore.transactional
    def _consumir(txn):
        d = (limite_ref.get(transaction=txn).to_dict() or {})
        g = (global_ref.get(transaction=txn).to_dict() or {})
        usados = int(d.get("usados") or 0) if d.get("dia") == hoy else 0
        usados_global = int(g.get("usados") or 0)
        if usados >= IA_LIMITE_DIARIO:
            return "usuario"
        if usados_global >= IA_LIMITE_GLOBAL_DIARIO:
            return "global"
        txn.set(limite_ref, {"dia": hoy, "usados": usados + 1})
        txn.set(global_ref, {"usados": usados_global + 1})
        return ""

    agotado = _consumir(db.transaction())
    if agotado == "usuario":
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED,
            "Has alcanzado el límite diario de consultas a la IA.",
        )
    if agotado == "global":
        print("[IA] Tope diario global de consultas alcanzado")
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED,
            "Hoy se ha alcanzado el límite de consultas a la IA. Inténtalo mañana.",
            {"motivo": "global"},
        )


def _limpiar_respuesta_ia(r: dict) -> dict:
    """Recorta y valida lo que devuelve el modelo antes de guardarlo."""
    consejos = []
    for c in (r.get("consejos") or [])[:5]:
        if not isinstance(c, dict):
            continue
        consejos.append({
            "titulo": str(c.get("titulo", ""))[:120],
            "detalle": str(c.get("detalle", ""))[:600],
            "prioridad": c.get("prioridad") if c.get("prioridad") in ("alta", "media", "baja") else "media",
            "coste": c.get("coste") if c.get("coste") in ("bajo", "medio", "alto") else "medio",
        })
    return {
        "resumen": str(r.get("resumen", ""))[:800],
        "consejos": consejos,
        "habitos": [str(h)[:200] for h in (r.get("habitos") or [])[:3]],
    }


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    timeout_sec=120,
    memory=options.MemoryOption.MB_512,
    max_instances=MAX_INSTANCIAS_IA,
)
def generar_sugerencias_ia(req: https_fn.CallableRequest) -> dict:
    """Consejos personalizados para un informe del usuario, generados con Gemini.
    Devuelve los guardados si ya existen en ese idioma (salvo `regenerar`).

    Args: informeId (str), idioma (str, nombre del idioma de la app),
    regenerar (bool, ignora la caché)."""
    uid = _exigir_email_verificado(req)
    data = req.data or {}
    informe_id = str(data.get("informeId") or "")
    idioma = _idioma_conocido(data.get("idioma"))
    regenerar = bool(data.get("regenerar"))
    if not informe_id or "/" in informe_id:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "informeId inválido.")

    db = firestore.client()
    informe_ref = db.collection("informes").document(informe_id)
    snap = informe_ref.get()
    informe = snap.to_dict() if snap.exists else None
    if not informe or informe.get("uid") != uid:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.NOT_FOUND, "Informe no encontrado.")

    cache = informe.get("sugerenciasIA") or {}
    if cache.get("idioma") == idioma and cache.get("consejos") and not regenerar:
        return {"ok": True, "cache": True, **cache}

    _consumir_cupo_ia(db, uid)

    vivienda = {}
    vivienda_id = informe.get("viviendaId")
    if vivienda_id:
        vs = db.collection("viviendas").document(vivienda_id).get()
        if vs.exists and (vs.to_dict() or {}).get("uid") == uid:
            vivienda = vs.to_dict() or {}

    try:
        resultado = _limpiar_respuesta_ia(_llamar_gemini(_prompt_ia(informe, vivienda, idioma)))
    except Exception as e:
        print(f"[IA] Error generando sugerencias: {e}")
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAVAILABLE,
            "No se pudieron generar los consejos. Inténtalo más tarde.",
        )

    guardado = {**resultado, "idioma": idioma, "generado": _ahora_ms()}
    informe_ref.update({"sugerenciasIA": guardado})
    return {"ok": True, "cache": False, **guardado}


# ── Lectura de la factura de la luz ─────────────────────────────────────
#
# El usuario manda una foto o el PDF de su factura y Gemini extrae solo los
# números que usa el informe: consumo, días facturados, importe y potencia.
# La imagen se procesa en memoria y NO se guarda en ningún sitio, ni se
# registra en los logs. Tampoco se piden nombre, dirección, CUPS ni cuenta.

FACTURA_TIPOS = {"image/jpeg", "image/png", "image/webp", "application/pdf"}
FACTURA_MAX_BYTES = 7 * 1024 * 1024  # las callables admiten 10 MB con base64

_ESQUEMA_FACTURA = {
    "type": "OBJECT",
    "properties": {
        "esFacturaLuz": {"type": "BOOLEAN"},
        "consumoKwh": {"type": "NUMBER"},
        "dias": {"type": "INTEGER"},
        "importeTotal": {"type": "NUMBER"},
        "potenciaKw": {"type": "NUMBER"},
    },
    "required": ["esFacturaLuz", "consumoKwh", "dias", "importeTotal", "potenciaKw"],
}

_PROMPT_FACTURA = (
    "Eres un lector de facturas de electricidad de hogares en España. Del "
    "documento adjunto extrae únicamente estos datos del periodo facturado:\n"
    "- consumoKwh: energía activa consumida en kWh (suma de todos los periodos "
    "P1, P2, P3 si vienen separados).\n"
    "- dias: número de días del periodo de facturación.\n"
    "- importeTotal: importe total de la factura en euros, con impuestos.\n"
    "- potenciaKw: potencia contratada en kW (la mayor si hay varias).\n"
    "Si el documento no es una factura de electricidad o no se lee, pon "
    "esFacturaLuz a false y el resto a 0. Si un dato no aparece, pon 0. "
    "No extraigas nombres, direcciones, CUPS ni números de cuenta."
)


class _FacturaNoValida(Exception):
    pass


def _validar_factura(r: dict) -> dict:
    """Comprueba que lo leído tiene sentido y lo pasa a datos anuales."""
    try:
        consumo = float(r.get("consumoKwh") or 0)
        dias = int(r.get("dias") or 0)
        importe = float(r.get("importeTotal") or 0)
        potencia = float(r.get("potenciaKw") or 0)
    except (TypeError, ValueError):
        raise _FacturaNoValida()
    if not r.get("esFacturaLuz") or consumo <= 0 or importe <= 0 or not 1 <= dias <= 400:
        raise _FacturaNoValida()
    precio = importe / consumo
    # Precio medio real de un hogar (impuestos y término de potencia incluidos):
    # fuera de este rango es que se ha leído mal algún número.
    if not 0.05 <= precio <= 1.5:
        raise _FacturaNoValida()
    return {
        "consumoKwh": round(consumo, 1),
        "dias": dias,
        "importeTotal": round(importe, 2),
        "potenciaKw": round(potencia, 2) if 0 < potencia <= 50 else 0.0,
        "consumoAnualKwh": round(consumo * 365 / dias, 1),
        "precioMedio": round(precio, 4),
    }


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    timeout_sec=120,
    memory=options.MemoryOption.MB_512,
    max_instances=MAX_INSTANCIAS_IA,
)
def leer_factura(req: https_fn.CallableRequest) -> dict:
    """Lee una factura de la luz con Gemini. Args: mime (image/jpeg, image/png,
    image/webp o application/pdf) y datos (el archivo en base64). Devuelve
    consumo del periodo y anualizado, días, importe, potencia y precio medio."""
    from google.genai import types

    uid = _exigir_email_verificado(req)
    data = req.data or {}
    mime = str(data.get("mime") or "")
    if mime not in FACTURA_TIPOS:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Formato no admitido.", {"motivo": "formato"})
    try:
        archivo = base64.b64decode(str(data.get("datos") or ""), validate=True)
    except (ValueError, TypeError):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Archivo no válido.", {"motivo": "formato"})
    if not archivo or len(archivo) > FACTURA_MAX_BYTES:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Archivo demasiado grande.", {"motivo": "tamano"})

    _consumir_cupo_ia(firestore.client(), uid)

    try:
        leido = _llamar_gemini(
            [types.Part.from_bytes(data=archivo, mime_type=mime), _PROMPT_FACTURA],
            esquema=_ESQUEMA_FACTURA,
            temperatura=0.0,
        )
    except Exception as e:
        print(f"[FACTURA] Gemini no disponible: {str(e)[:200]}")
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAVAILABLE, "No se pudo leer la factura. Inténtalo más tarde.")
    try:
        return {"ok": True, **_validar_factura(leido)}
    except _FacturaNoValida:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "No parece una factura de la luz o no se lee bien.",
            {"motivo": "no_legible"},
        )


# ════════════════════════════════════════════════════════════════════════
# Estadísticas del profesional (visitas al perfil y contactos)
# ════════════════════════════════════════════════════════════════════════
#
# La app crea tecnicos/{id}/eventos/{uid}_{dia}_{tipo} (las rules garantizan
# uno por cliente, día y tipo). Aquí se suman en /estadisticas/{id}, que solo
# lee el profesional. Los eventos individuales (que llevan el uid del cliente
# en el id) se borran a las 48 h: del cliente no queda nada, solo los totales.

TIPOS_EVENTO = {"visita", "chat", "llamada"}


@firestore_fn.on_document_created(
    document="tecnicos/{tecnicoId}/eventos/{eventoId}",
    region=REGION,
)
def on_evento_perfil(event) -> None:
    """Suma una visita o un contacto a las estadísticas del profesional
    (/estadisticas/{tecnicoId}), por tipo y por día.

    Idempotente: Firestore puede entregar el mismo evento más de una vez. En
    una transacción se suma solo si el evento no está marcado `contado`, y se
    marca a la vez."""
    datos = event.data.to_dict() if event.data else {}
    tipo = datos.get("tipo")
    dia = str(datos.get("dia") or "")
    if tipo not in TIPOS_EVENTO or len(dia) != 8:
        return
    tecnico_id = event.params["tecnicoId"]
    db = firestore.client()
    evento_ref = (
        db.collection("tecnicos").document(tecnico_id).collection("eventos").document(event.params["eventoId"])
    )
    estadisticas_ref = db.collection("estadisticas").document(tecnico_id)

    @firestore.transactional
    def _contar(txn):
        snap = evento_ref.get(transaction=txn)
        if not snap.exists or (snap.to_dict() or {}).get("contado") is True:
            return
        txn.set(estadisticas_ref, {
            "total": {tipo: firestore.Increment(1)},
            "dias": {dia: {tipo: firestore.Increment(1)}},
            "actualizado": _ahora_ms(),
        }, merge=True)
        txn.update(evento_ref, {"contado": True})

    _contar(db.transaction())


NOTIFICACIONES_DIAS = 90


def _purgar_notificaciones_antiguas(db) -> int:
    """Borra las notificaciones de más de NOTIFICACIONES_DIAS días: cada mensaje
    de chat crea una y, si no, crecerían sin fin."""
    limite = _ahora_ms() - NOTIFICACIONES_DIAS * 24 * 60 * 60 * 1000
    q = db.collection("notificaciones").where(filter=FieldFilter("fecha", "<", limite))
    return _borrar_query(db, q)


def _purgar_eventos_antiguos(db) -> int:
    """Borra los eventos individuales anteriores a ayer (UTC)."""
    ayer = time.gmtime(time.time() - 24 * 60 * 60)
    limite = ayer.tm_year * 10000 + ayer.tm_mon * 100 + ayer.tm_mday
    q = db.collection_group("eventos").where(filter=FieldFilter("dia", "<", limite))
    return _borrar_query(db, q)


# ════════════════════════════════════════════════════════════════════════
# Suscripciones: verificación con Google Play (API purchases.subscriptionsv2)
# ════════════════════════════════════════════════════════════════════════
#
# Fuente de la verdad: Google Play. Cada purchaseToken se registra en
# /suscripciones/{token} ligado a UN uid. El plan visible del profesional
# (tecnicos.planActivo / suscripcionHasta) se RECALCULA siempre a partir de
# sus suscripciones vigentes, tanto al activar como cuando Play avisa de un
# cambio (RTDN: renovación, cancelación, reembolso, retención...) o en la
# revisión diaria de respaldo.

ANDROID_PUBLISHER = (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/"
    f"applications/{PACKAGE_NAME}"
)
# CANCELED = el usuario canceló la renovación pero ya pagó hasta expiryTime.
ESTADOS_VIGENTES = {
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED",
}
NIVELES = {"verificado": 1, "destacado": 2, "destacado_anuncios": 3}
DIA_MS = 24 * 60 * 60 * 1000
RTDN_TOPIC = os.environ.get("PLAY_RTDN_TOPIC", "play-rtdn")

_RFC3339 = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(\.\d+)?(Z|[+-]\d\d:\d\d)$")


def _cuenta_ofuscada(uid: str) -> str:
    """Mismo valor que BillingManager.cuentaOfuscada() en la app (SHA-256 hex del uid)."""
    return hashlib.sha256(uid.encode("utf-8")).hexdigest()


def _rfc3339_a_ms(valor):
    """Convierte una fecha RFC 3339 de Google Play (con nanosegundos) a milisegundos."""
    m = _RFC3339.match(valor or "")
    if not m:
        return None
    base, frac, zona = m.groups()
    frac = (frac or ".0")[:7]            # Python admite hasta microsegundos
    zona = "+00:00" if zona == "Z" else zona
    return int(datetime.fromisoformat(f"{base}{frac}{zona}").timestamp() * 1000)


def _sesion_play() -> AuthorizedSession:
    """Sesión HTTP autenticada contra la Google Play Developer API."""
    creds, _ = default_creds(scopes=["https://www.googleapis.com/auth/androidpublisher"])
    return AuthorizedSession(creds)


def _consultar_play(token: str):
    """Estado de la suscripción en Play.

    dict con datos → respuesta válida; {} → Play dice que el token no existe;
    None → Play no responde / API sin configurar (se puede reintentar).
    """
    try:
        r = _sesion_play().get(
            f"{ANDROID_PUBLISHER}/purchases/subscriptionsv2/tokens/{token}", timeout=15
        )
    except Exception as e:
        print(f"[SUSCRIPCION] Play no disponible: {e}")
        return None
    if r.status_code == 200:
        return r.json()
    print(f"[SUSCRIPCION] Play HTTP {r.status_code}: {r.text[:300]}")
    if r.status_code in (400, 404, 410):
        return {}
    return None


def _expiry_de(data: dict, product_id=None):
    """Caducidad (ms) de la línea de la compra para ese producto, o None."""
    fechas = [
        _rfc3339_a_ms(li.get("expiryTime")) or 0
        for li in (data.get("lineItems") or [])
        if product_id is None or li.get("productId") == product_id
    ]
    return max(fechas) if fechas else None


def _acknowledge_play(product_id: str, token: str) -> None:
    """Confirma la compra desde el servidor. Si nadie la confirma en 3 días,
    Google la reembolsa automáticamente; hacerlo aquí evita depender de que
    la app siga viva tras la compra."""
    try:
        r = _sesion_play().post(
            f"{ANDROID_PUBLISHER}/purchases/subscriptions/{product_id}/tokens/{token}:acknowledge",
            json={}, timeout=15,
        )
        if r.status_code >= 300:
            print(f"[SUSCRIPCION] acknowledge HTTP {r.status_code}: {r.text[:300]}")
    except Exception as e:
        print(f"[SUSCRIPCION] acknowledge falló: {e}")


# Estados en los que Play ya no va a cobrar: no hace falta cancelar nada
ESTADOS_SIN_RENOVACION = {
    "SUBSCRIPTION_STATE_CANCELED",
    "SUBSCRIPTION_STATE_EXPIRED",
    "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED",
}
# Sin acceso ahora (activa=False), pero Play aún puede volver a cobrar
ESTADOS_PENDIENTES_DE_COBRO = {"SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED"}


def _cancelar_en_play(token: str) -> str:
    """Cancela la renovación de una suscripción en Google Play con
    purchases.subscriptionsv2.cancel (la de purchases.subscriptions está
    obsoleta). DEVELOPER_REQUESTED_STOP_PAYMENTS: no se puede restaurar y no
    hay más cobros; no reembolsa el periodo ya pagado.

    Devuelve "cancelada" si Play la ha cancelado ahora, "no_renueva" si ya no
    se iba a cobrar (Play no conoce el token, o está cancelada o caducada) y
    "sin_permiso" si Play niega el permiso (401, o 403 de permisos): es un
    fallo de configuración nuestro y no puede impedir que alguien borre su
    cuenta. Ante un fallo pasajero (5xx, red, 403 por cuota) con la
    suscripción aún activa, lanza UNAVAILABLE (reintentable)."""
    no_disponible = https_fn.HttpsError(
        https_fn.FunctionsErrorCode.UNAVAILABLE,
        "No se pudo cancelar la suscripción en Google Play. Inténtalo de nuevo en unos minutos.",
        {"motivo": "play"},
    )
    try:
        sesion = _sesion_play()
    except Exception as e:
        print(f"[SUSCRIPCION] ERROR: sin credenciales para la API de Google Play: {e}")
        raise no_disponible
    try:
        r = sesion.post(
            f"{ANDROID_PUBLISHER}/purchases/subscriptionsv2/tokens/{token}:cancel",
            json={"cancellationContext": {"cancellationType": "DEVELOPER_REQUESTED_STOP_PAYMENTS"}},
            timeout=15,
        )
        if r.status_code < 300:
            return "cancelada"
        if _play_niega_permiso(r):
            print(f"[SUSCRIPCION] ERROR: sin permiso para cancelar en Play (HTTP {r.status_code}). "
                  f"Revisa los permisos de la cuenta de servicio en Play Console: {r.text[:300]}")
            return "sin_permiso"
        print(f"[SUSCRIPCION] cancel HTTP {r.status_code}: {r.text[:300]}")
    except Exception as e:
        print(f"[SUSCRIPCION] cancel falló: {e}")
    # No se canceló: se sigue solo si Play confirma que ya no se va a cobrar
    data = _consultar_play(token)
    if data == {} or (data and data.get("subscriptionState") in ESTADOS_SIN_RENOVACION):
        return "no_renueva"
    raise no_disponible


# Razones de los errores de Google que indican falta de permiso
_RAZONES_SIN_PERMISO = {
    "permissiondenied", "forbidden", "insufficientpermissions",
    "iam_permission_denied", "access_token_scope_insufficient",
}


def _play_niega_permiso(r) -> bool:
    """True si la respuesta de Play es una falta de permiso: 401, o 403 con
    status PERMISSION_DENIED o una razón de permisos. Google también responde
    403 por cuota o límites (rateLimitExceeded, quotaExceeded,
    dailyLimitExceeded…): eso es pasajero y manda aunque el status sea
    PERMISSION_DENIED."""
    if r.status_code == 401:
        return True
    if r.status_code != 403:
        return False
    try:
        error = (r.json() or {}).get("error")
    except Exception:
        error = None
    if not isinstance(error, dict):
        return False
    razones = {
        str(e.get("reason") or "").lower()
        for e in (error.get("errors") or []) + (error.get("details") or [])
        if isinstance(e, dict)
    }
    if any(marca in razon for razon in razones for marca in ("quota", "limit", "exceeded")):
        return False
    return error.get("status") == "PERMISSION_DENIED" or bool(razones & _RAZONES_SIN_PERMISO)


def _suscripciones_por_cancelar(db, uid: str) -> list:
    """Tokens de las suscripciones de Play de `uid` que aún pueden cobrarse:
    vigentes, o en retención o pausa (según lo último que sabemos de Play)."""
    tokens = []
    for d in db.collection("suscripciones").where(filter=FieldFilter("uid", "==", uid)).stream():
        s = d.to_dict() or {}
        estado = s.get("estadoPlay")
        if s.get("origen") != "play" or estado in ESTADOS_SIN_RENOVACION:
            continue
        if not s.get("activa") and estado not in ESTADOS_PENDIENTES_DE_COBRO:
            continue   # caducada o sustituida por un cambio de plan
        tokens.append(d.id)
    return tokens


def _cancelar_suscripciones_play(db, uid: str) -> tuple:
    """Antes de borrar una cuenta: cancela en Google Play la renovación de
    sus suscripciones que aún pueden cobrarse (vigentes, o en retención o
    pausa). Ante un fallo pasajero de Play lanza UNAVAILABLE y la cuenta no se
    borra: se puede reintentar. Devuelve (canceladas, sin_permiso): cuántas se
    han cancelado y cuántas tiene que cancelar el usuario en Google Play."""
    canceladas = sin_permiso = 0
    for token in _suscripciones_por_cancelar(db, uid):
        resultado = _cancelar_en_play(token)
        if resultado == "cancelada":
            print(f"[SUSCRIPCION] Renovación cancelada en Play al borrar la cuenta {uid}")
            canceladas += 1
        elif resultado == "sin_permiso":
            sin_permiso += 1
    return canceladas, sin_permiso


def _validar_token_suscripcion(product_id: str, token: str, uid: str):
    """Valida el purchaseToken para ESE usuario.

    Devuelve (origen, expiry_ms, data_play) con la caducidad real de Google
    Play. Solo se aceptan compras verificadas: no hay modo de prueba.
    """
    data = _consultar_play(token)
    if data:
        cuenta = (data.get("externalAccountIdentifiers") or {}).get("obfuscatedExternalAccountId")
        if cuenta and cuenta != _cuenta_ofuscada(uid):
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.PERMISSION_DENIED,
                "Esta suscripción pertenece a otra cuenta de ZeroHaus.",
                {"motivo": "otra_cuenta"},
            )
        expiry_ms = _expiry_de(data, product_id)
        if not expiry_ms:
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
                "La compra no corresponde a ese plan.",
                {"motivo": "plan_incorrecto"},
            )
        if data.get("subscriptionState") not in ESTADOS_VIGENTES or expiry_ms <= _ahora_ms():
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
                "La suscripción no está activa en Google Play.",
                {"motivo": "no_activa"},
            )
        if data.get("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_PENDING":
            _acknowledge_play(product_id, token)
        return "play", expiry_ms, data

    if data == {}:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.PERMISSION_DENIED,
            "Google Play no reconoce esta compra.",
            {"motivo": "no_reconocida"},
        )
    # Play no respondió: la app lo reintenta (procesarPendientes al volver)
    raise https_fn.HttpsError(
        https_fn.FunctionsErrorCode.UNAVAILABLE,
        "No se pudo verificar la suscripción con Google Play. Inténtalo de nuevo.",
    )


def _recalcular_plan(db, uid: str):
    """planActivo/suscripcionHasta = la suscripción vigente de mayor nivel.

    Con planes solapados (p. ej. verificado anual + destacado mensual), cuando
    caduca el destacado la revisión diaria vuelve a dejarlo en verificado.
    """
    ahora = _ahora_ms()
    vigentes = [
        s for s in (
            d.to_dict() or {}
            for d in db.collection("suscripciones").where(filter=FieldFilter("uid", "==", uid)).stream()
        )
        if s.get("activa") and int(s.get("fechaFin") or 0) > ahora
    ]
    if vigentes:
        mejor = max(vigentes, key=lambda s: (NIVELES.get(s.get("plan"), 0), int(s.get("fechaFin") or 0)))
        plan, hasta = mejor.get("plan", ""), int(mejor.get("fechaFin") or 0)
    else:
        plan, hasta = "", 0

    ref = db.collection("tecnicos").document(uid)
    if ref.get().exists:
        ref.update({"planActivo": plan, "suscripcionHasta": hasta})
    return plan, hasta


def _invalidar_token_enlazado(db, data: dict, uid: str) -> None:
    """Al cambiar de plan (upgrade/downgrade) Play emite un token nuevo y el
    anterior (linkedPurchaseToken) deja de valer."""
    anterior = (data or {}).get("linkedPurchaseToken")
    if not anterior:
        return
    ref = db.collection("suscripciones").document(anterior)
    snap = ref.get()
    if snap.exists and (snap.to_dict() or {}).get("uid") == uid:
        ref.update({"activa": False, "actualizado": _ahora_ms()})


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def activar_suscripcion(req: https_fn.CallableRequest) -> dict:
    """Activa o renueva la suscripción de un profesional.

    Args (vía request.data):
        planId (str): ID del producto de Google Play.
        purchaseToken (str): Token de la compra.

    Idempotencia: /suscripciones/{purchaseToken} como libro contable, ligado
    al uid que lo registró por primera vez.
    """
    uid = _exigir_email_verificado(req)
    data = req.data or {}
    plan_id = data.get("planId") or ""
    token = data.get("purchaseToken") or ""

    if plan_id not in PLANES:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Plan desconocido."
        )
    if not isinstance(token, str) or len(token) < 10 or "/" in token:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "purchaseToken inválido."
        )

    db = firestore.client()
    if not db.collection("tecnicos").document(uid).get().exists:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "No tienes perfil profesional.",
            {"motivo": "sin_perfil"},
        )

    origen, expiry_play_ms, datos_play = _validar_token_suscripcion(plan_id, token, uid)

    plan_nombre = PLANES[plan_id]["plan"]
    ahora = _ahora_ms()
    # Google Play es la única fuente de la fecha de fin (renovaciones, pruebas)
    fecha_fin = expiry_play_ms
    suscripcion_ref = db.collection("suscripciones").document(token)

    @firestore.transactional
    def _registrar(txn):
        snap = suscripcion_ref.get(transaction=txn)
        previo = (snap.to_dict() or {}) if snap.exists else {}
        # Un token solo puede pertenecer a quien lo registró primero:
        # impide que otro usuario reutilice la compra (y sus renovaciones).
        if snap.exists and previo.get("uid") != uid:
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.PERMISSION_DENIED,
                "Esta suscripción pertenece a otra cuenta de ZeroHaus.",
                {"motivo": "otra_cuenta"},
            )
        if snap.exists and previo.get("activa") and fecha_fin <= int(previo.get("fechaFin") or 0):
            return "ya_activada"
        txn.set(suscripcion_ref, {
            "id": token, "uid": uid, "planId": plan_id,
            "plan": plan_nombre, "origen": origen,
            "fechaInicio": previo.get("fechaInicio", ahora),
            "fechaFin": fecha_fin,
            "activa": True,
            "actualizado": ahora,
        })
        return "renovada" if snap.exists else "activada"

    resultado = _registrar(db.transaction())
    _invalidar_token_enlazado(db, datos_play, uid)
    plan_final, hasta = _recalcular_plan(db, uid)

    try:
        if resultado == "activada":
            titulo, detalle = _TEXTOS_SUSCRIPCION.get(
                _preferencias(db, uid)["idioma"], _TEXTOS_SUSCRIPCION["Español"])
            _crear_notificacion_in_app(db, uid, titulo=titulo, detalle=detalle, tipo="suscripcion")
    except Exception as e:
        print(f"[SUSCRIPCION] Error notificando: {e}")

    return {"ok": True, "resultado": resultado, "plan": plan_final, "hastaMs": hasta}


# Aviso "suscripción activada" (título, detalle) en los 14 idiomas
_TEXTOS_SUSCRIPCION = {
    "Español": ("¡Suscripción activada!", "Tu suscripción de ZeroHaus ya está activa."),
    "English": ("Subscription activated!", "Your ZeroHaus subscription is now active."),
    "Català": ("Subscripció activada!", "La teva subscripció de ZeroHaus ja és activa."),
    "Euskara": ("Harpidetza aktibatuta!", "Zure ZeroHaus harpidetza aktibo dago jada."),
    "Galego": ("Subscrición activada!", "A túa subscrición de ZeroHaus xa está activa."),
    "Português": ("Subscrição ativada!", "A sua subscrição ZeroHaus já está ativa."),
    "Français": ("Abonnement activé !", "Votre abonnement ZeroHaus est maintenant actif."),
    "Deutsch": ("Abo aktiviert!", "Dein ZeroHaus-Abo ist jetzt aktiv."),
    "Italiano": ("Abbonamento attivato!", "Il tuo abbonamento ZeroHaus è ora attivo."),
    "العربية": ("تم تفعيل الاشتراك!", "اشتراكك في ZeroHaus نشط الآن."),
    "中文": ("订阅已激活！", "您的 ZeroHaus 订阅现已生效。"),
    "Română": ("Abonament activat!", "Abonamentul tău ZeroHaus este acum activ."),
    "Nederlands": ("Abonnement geactiveerd!", "Je ZeroHaus-abonnement is nu actief."),
    "Polski": ("Subskrypcja aktywowana!", "Twoja subskrypcja ZeroHaus jest już aktywna."),
}


def _sincronizar_token(db, token: str) -> None:
    """Refresca /suscripciones/{token} con el estado real de Play y recalcula
    el plan de su dueño."""
    ref = db.collection("suscripciones").document(token)
    snap = ref.get()
    if not snap.exists:
        # Compra que la app aún no ha registrado: se registrará (y se
        # confirmará) en cuanto el profesional vuelva a abrirla.
        print(f"[SUSCRIPCION] Token aún no registrado por la app: {token[:12]}…")
        return
    s = snap.to_dict() or {}
    uid = s.get("uid") or ""
    ahora = _ahora_ms()

    if s.get("origen") == "play":
        data = _consultar_play(token)
        if data is None:
            return  # Play no responde: se reintenta en la revisión diaria
        expiry = _expiry_de(data, s.get("planId")) if data else None
        estado = data.get("subscriptionState", "") if data else "SUBSCRIPTION_STATE_EXPIRED"
        activa = bool(expiry) and estado in ESTADOS_VIGENTES and expiry > ahora
        ref.update({
            "fechaFin": expiry or ahora,
            "activa": activa,
            "estadoPlay": estado,
            "actualizado": ahora,
        })
        if activa and data.get("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_PENDING":
            _acknowledge_play(s.get("planId", ""), token)
        _invalidar_token_enlazado(db, data, uid)
    elif s.get("activa") and int(s.get("fechaFin") or 0) <= ahora:
        # Registros antiguos del modo demo (ya eliminado): solo caducan
        ref.update({"activa": False, "actualizado": ahora})

    if uid:
        _recalcular_plan(db, uid)


@pubsub_fn.on_message_published(topic=RTDN_TOPIC, region=REGION)
def on_play_rtdn(event: pubsub_fn.CloudEvent[pubsub_fn.MessagePublishedData]) -> None:
    """Real-time developer notifications de Google Play.

    Play publica aquí cada renovación, cancelación, reembolso, retención de
    cuenta o expiración. Configuración: Play Console → Monetización →
    Notificaciones en tiempo real → tema projects/<proyecto>/topics/play-rtdn.
    """
    try:
        payload = json.loads(base64.b64decode(event.data.message.data or b"").decode("utf-8"))
    except Exception as e:
        print(f"[RTDN] Mensaje ilegible: {e}")
        return
    if payload.get("packageName") != PACKAGE_NAME:
        return
    noti = payload.get("subscriptionNotification")
    if not noti:
        print(f"[RTDN] Notificación sin suscripción (test/otra): {payload}")
        return
    token = noti.get("purchaseToken") or ""
    if not token or "/" in token:
        return
    print(f"[RTDN] tipo={noti.get('notificationType')} plan={noti.get('subscriptionId')}")
    _sincronizar_token(firestore.client(), token)


def _revisar_suscripciones(db) -> int:
    """Re-sincroniza con Play las suscripciones que caducan pronto o ya
    caducaron y recalcula el plan visible de los profesionales con el plan
    vencido (p. ej. caducó el destacado pero sigue vigente el verificado).
    Un fallo con una suscripción no detiene el resto. Devuelve cuántas se revisaron."""
    ahora = _ahora_ms()
    limite = ahora + 3 * DIA_MS
    revisadas = 0
    for d in db.collection("suscripciones").where(filter=FieldFilter("activa", "==", True)).stream():
        if int((d.to_dict() or {}).get("fechaFin") or 0) < limite:
            try:
                _sincronizar_token(db, d.id)
                revisadas += 1
            except Exception as e:
                print(f"[SUSCRIPCION] No se pudo revisar {d.id[:12]}…: {e}")
    vencidos = (
        db.collection("tecnicos")
        .where(filter=FieldFilter("suscripcionHasta", ">", 0))
        .where(filter=FieldFilter("suscripcionHasta", "<", ahora))
        .stream()
    )
    for d in vencidos:
        try:
            _recalcular_plan(db, d.id)
        except Exception as e:
            print(f"[SUSCRIPCION] No se pudo recalcular el plan de {d.id}: {e}")
    return revisadas


@scheduler_fn.on_schedule(
    schedule="every day 05:00",
    timezone=scheduler_fn.Timezone("Europe/Madrid"),
    region=REGION,
    # Con los 60 s por defecto, al crecer los datos se cortaba antes de llegar
    # a los últimos pasos. 540 s bastan (en 2.ª generación se admiten hasta 1800)
    timeout_sec=540,
)
def revisar_suscripciones_diario(event: scheduler_fn.ScheduledEvent) -> None:
    """Revisión diaria. Primero lo que afecta a la seguridad y a los pagos:
    cuentas bloqueadas desactivadas en Auth, suscripciones (red de seguridad
    por si falla o no está configurado RTDN) y perfiles profesionales ocultos
    de cuentas ya verificadas; después, la limpieza (eventos de estadísticas y
    notificaciones antiguas). Cada paso va aparte: un fallo no detiene los demás."""
    db = firestore.client()
    pasos = (
        ("[ADMIN] Cuentas bloqueadas desactivadas en Auth", _sincronizar_bloqueados),
        ("[SEGURIDAD] Marcas de deshacer verificación resueltas", _reintentar_deshacer_pendientes),
        ("[SUSCRIPCION] Suscripciones revisadas con Play", _revisar_suscripciones),
        ("[TECNICOS] Perfiles ocultos ya verificados", _mostrar_perfiles_verificados),
        # Privacidad: eventos individuales de estadísticas de más de 48 h
        ("[ESTADISTICAS] Eventos antiguos borrados", _purgar_eventos_antiguos),
        # El historial de la app muestra las últimas
        ("[NOTIFICACIONES] Notificaciones antiguas borradas", _purgar_notificaciones_antiguas),
    )
    for texto, paso in pasos:
        try:
            print(f"{texto}: {paso(db)}")
        except Exception as e:
            print(f"{texto}: no se pudo completar: {e}")


# ══ Precio de la luz automático (Red Eléctrica → Remote Config) ══
# Los informes sin factura usan `precio_electricidad` de Remote Config. Antes
# se cambiaba a mano en la consola; ahora cada mañana se calcula con el PVPC
# (tarifa regulada 2.0TD) de las últimas semanas que publica Red Eléctrica en
# su API pública REData, y se publica solo si ha cambiado lo suficiente.
REE_PRECIOS_URL = "https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real"
REE_ID_PVPC = "1001"                  # indicador PVPC en la respuesta, en €/MWh
REMOTE_CONFIG_URL = f"https://firebaseremoteconfig.googleapis.com/v1/projects/{PROJECT_ID}/remoteConfig"
PARAM_PRECIO_LUZ = "precio_electricidad"

PRECIO_LUZ_DIAS = 28                  # media de 4 semanas: suaviza los picos de un día
PRECIO_LUZ_TRAMO_DIAS = 7             # la API responde mejor a consultas de una semana
PRECIO_LUZ_MIN_HORAS = 20 * 24        # con menos datos no se toca el precio
# Impuestos sobre el término de energía (2026): impuesto especial sobre la
# electricidad (5,11269632 %) e IVA (21 %). Si cambian, se actualizan aquí.
IMPUESTO_ELECTRICIDAD = 0.0511269632
IVA_LUZ = 0.21
PRECIO_LUZ_RANGO = (0.01, 1.5)        # el mismo que acepta la app (PreciosEnergia)
PRECIO_LUZ_CAMBIO_MIN = 0.005         # medio céntimo: por debajo no se publica


class _PrecioLuzNoValido(Exception):
    """Los datos de Red Eléctrica no bastan para dar un precio fiable."""


def _tramos_precio_luz(hoy: date) -> list:
    """Semanas (inicio, fin) que cubren los PRECIO_LUZ_DIAS días hasta ayer."""
    tramos = []
    fin = hoy - timedelta(days=1)
    restantes = PRECIO_LUZ_DIAS
    while restantes > 0:
        dias = min(PRECIO_LUZ_TRAMO_DIAS, restantes)
        inicio = fin - timedelta(days=dias - 1)
        tramos.append((inicio, fin))
        fin = inicio - timedelta(days=1)
        restantes -= dias
    return list(reversed(tramos))


def _valores_pvpc(respuesta: dict) -> list:
    """Precios del PVPC (€/MWh) de una respuesta de REData; ignora el resto.

    Los negativos se quedan: el PVPC baja de cero en las horas solares de
    algunos fines de semana, y quitarlos subiría la media.
    """
    for indicador in (respuesta or {}).get("included") or []:
        if str(indicador.get("id")) != REE_ID_PVPC:
            continue
        valores = []
        for v in (indicador.get("attributes") or {}).get("values") or []:
            x = v.get("value") if isinstance(v, dict) else None
            if isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x):
                valores.append(float(x))
        return valores
    return []


def _precio_luz_desde_pvpc(valores_eur_mwh: list) -> float:
    """Media del PVPC en €/kWh con impuestos, redondeada a 4 decimales.

    No incluye el término de potencia ni el alquiler del contador, que no
    dependen de lo que se consume; el precio de una factura real sí los lleva.
    """
    if len(valores_eur_mwh) < PRECIO_LUZ_MIN_HORAS:
        raise _PrecioLuzNoValido(f"solo {len(valores_eur_mwh)} horas con precio")
    media_kwh = sum(valores_eur_mwh) / len(valores_eur_mwh) / 1000
    precio = round(media_kwh * (1 + IMPUESTO_ELECTRICIDAD) * (1 + IVA_LUZ), 4)
    minimo, maximo = PRECIO_LUZ_RANGO
    if not minimo <= precio <= maximo:
        raise _PrecioLuzNoValido(f"{precio} €/kWh está fuera del rango válido")
    return precio


def _aplicar_precio_luz(plantilla: dict, precio: float) -> bool:
    """Pone el precio en la plantilla de Remote Config. False si no hace falta
    (la diferencia con el actual es menor que PRECIO_LUZ_CAMBIO_MIN)."""
    parametros = plantilla.setdefault("parameters", {})
    parametro = parametros.setdefault(PARAM_PRECIO_LUZ, {"valueType": "NUMBER"})
    try:
        actual = float((parametro.get("defaultValue") or {}).get("value"))
    except (TypeError, ValueError):
        actual = None
    if actual is not None and abs(actual - precio) < PRECIO_LUZ_CAMBIO_MIN:
        return False
    parametro["defaultValue"] = {"value": f"{precio:.4f}"}
    return True


def _descargar_pvpc(hoy: date) -> list:
    """Precios horarios del PVPC (€/MWh) de los últimos PRECIO_LUZ_DIAS días."""
    valores = []
    for inicio, fin in _tramos_precio_luz(hoy):
        consulta = urllib.parse.urlencode({
            "start_date": f"{inicio.isoformat()}T00:00",
            "end_date": f"{fin.isoformat()}T23:59",
            "time_trunc": "hour",
        })
        peticion = urllib.request.Request(
            f"{REE_PRECIOS_URL}?{consulta}", headers={"Accept": "application/json"}
        )
        with urllib.request.urlopen(peticion, timeout=30) as r:
            valores += _valores_pvpc(json.load(r))
    return valores


def _publicar_precio_luz(precio: float, horas: int) -> bool:
    """Lee la plantilla de Remote Config, cambia solo el precio de la luz y la
    publica. If-Match con el ETag evita pisar un cambio hecho a la vez en la consola."""
    creds, _ = default_creds(scopes=["https://www.googleapis.com/auth/firebase.remoteconfig"])
    sesion = AuthorizedSession(creds)
    r = sesion.get(REMOTE_CONFIG_URL, timeout=30)
    r.raise_for_status()
    etag = r.headers.get("ETag") or "*"
    plantilla = r.json()
    if not _aplicar_precio_luz(plantilla, precio):
        return False
    plantilla["version"] = {
        "description": f"{PARAM_PRECIO_LUZ} = {precio:.4f} (PVPC de Red Eléctrica, media de {horas} h)"
    }
    r = sesion.put(
        REMOTE_CONFIG_URL,
        data=json.dumps(plantilla),
        headers={"Content-Type": "application/json; UTF-8", "If-Match": etag},
        timeout=30,
    )
    r.raise_for_status()
    return True


@scheduler_fn.on_schedule(
    schedule="every day 06:30",
    timezone=scheduler_fn.Timezone("Europe/Madrid"),
    region=REGION,
    # 4 consultas a Red Eléctrica y 2 a Remote Config de hasta 30 s cada una:
    # con los 60 s por defecto se cortaría antes de dejar su línea en el registro
    timeout_sec=300,
)
def actualizar_precio_luz_diario(event: scheduler_fn.ScheduledEvent) -> None:
    """Actualiza `precio_electricidad` con el PVPC de Red Eléctrica. Si la API
    falla o los datos no son fiables se deja el precio que había."""
    hoy = datetime.now(ZoneInfo("Europe/Madrid")).date()
    try:
        valores = _descargar_pvpc(hoy)
        precio = _precio_luz_desde_pvpc(valores)
    except (_PrecioLuzNoValido, OSError, ValueError) as e:
        print(f"[PRECIO_LUZ] Sin cambios, datos de Red Eléctrica no válidos: {e}")
        return
    try:
        publicado = _publicar_precio_luz(precio, len(valores))
    except Exception as e:
        print(f"[PRECIO_LUZ] No se pudo publicar {precio} €/kWh en Remote Config: {e}")
        return
    estado = "publicado" if publicado else "sin cambios (diferencia menor de medio céntimo)"
    print(f"[PRECIO_LUZ] {precio} €/kWh con {len(valores)} h de PVPC: {estado}")


# Copias de seguridad: las hace Firestore (copias gestionadas, diaria 7 días y
# semanal 4 semanas). La antigua función backup_firestore_diario fallaba a
# diario con 403 por falta de permisos y nunca llegó a guardar nada.


# ══ Pago con retención (Stripe Connect): todo en pagos.py ══
# pagos.py no importa main (Firebase carga main.py sin meterlo en sys.modules y
# se ejecutaría dos veces): recibe aquí lo que comparte con el resto. Va al
# final, después de set_global_options y de las ayudas que usa. Sus funciones se
# importan para que Firebase las encuentre en main.
import pagos  # noqa: E402

pagos.configurar(
    exigir_email_verificado=_exigir_email_verificado,
    verificar_admin=_verificar_admin,
    preferencias=_preferencias,
    crear_notificacion=_crear_notificacion_in_app,
    enviar_push=_enviar_push,
)
from pagos import (  # noqa: E402,F401
    pagos_aceptar, pagos_cancelar, pagos_confirmar, pagos_disputar, pagos_liberar_vencidos,
    pagos_reintentar, pagos_resolver, pagos_solicitar, pagos_terminar,
    stripe_crear_enlace_cuenta, stripe_estado_cuenta, stripe_webhook,
)
