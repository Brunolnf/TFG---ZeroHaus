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

Triggers de Firestore
  on_message_created           Nuevo mensaje de chat → push, email y notificación.
  on_resena_changed            Reseña creada/editada/borrada → recalcula la nota.
  on_evento_perfil             Visita o contacto a un profesional → estadísticas.

Pub/Sub y programadas
  on_play_rtdn                 Avisos en tiempo real de Google Play (renovaciones…).
  revisar_suscripciones_diario Respaldo diario de suscripciones + limpieza de eventos.
  backup_firestore_diario      Exportación diaria de Firestore a Cloud Storage.

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
import re
import secrets
import smtplib
import ssl
import time
import os
from datetime import datetime
from email.message import EmailMessage
from email.utils import formataddr
from firebase_functions import https_fn, firestore_fn, scheduler_fn, pubsub_fn, options
from firebase_functions.params import SecretParam
from firebase_admin import initialize_app, auth, firestore, messaging, storage
from google.cloud.firestore_v1 import FieldFilter
from google.auth import default as default_creds
from google.auth.transport.requests import AuthorizedSession

initialize_app()

PROJECT_ID = "zerohaus-2a865"
BACKUP_BUCKET = "gs://zerohaus-2a865-backups/firestore"
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

COLECCIONES_POR_CAMPO_UID = [
    ("viviendas",       "uid"),
    ("informes",        "uid"),
    ("notificaciones",  "uid"),
    ("suscripciones",   "uid"),
]

COLECCIONES_DOBLE_CAMPO = [
    ("resenas", ["uid", "tecnicoId"]),
]

DOCS_POR_ID = ["usuarios", "tecnicos", "ajustes", "estadisticas", "limites_ia", "verificaciones_email", "avisos_email"]


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

    for col, campo in COLECCIONES_POR_CAMPO_UID:
        q = db.collection(col).where(filter=FieldFilter(campo, "==", uid))
        stats[col] = _borrar_query(db, q)

    for col, campos in COLECCIONES_DOBLE_CAMPO:
        total = 0
        for campo in campos:
            q = db.collection(col).where(filter=FieldFilter(campo, "==", uid))
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
    stats["archivos"] = archivos_borrados + _borrar_storage(f"perfiles/{uid}/")

    stats["eventos"] = _borrar_subcoleccion(db, db.collection("tecnicos").document(uid), "eventos")

    for col in DOCS_POR_ID:
        try:
            db.collection(col).document(uid).delete()
            stats[col] = 1
        except Exception:
            stats[col] = 0

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
    Reservado al administrador (custom claim `admin`). Args: uid (str)."""
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

    db = firestore.client()
    stats = _purgar_usuario(db, uid)
    return {"ok": True, "uid": uid, "stats": stats}


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
    try:
        auth.update_user(uid, email_verified=True)
    except auth.UserNotFoundError:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.NOT_FOUND, "No existe esa cuenta.")
    print(f"[ADMIN] {req.auth.uid} marcó como verificado el email de {uid}")
    return {"ok": True}


@https_fn.on_call(
    region=REGION,
    enforce_app_check=True,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def eliminar_mi_cuenta(req: https_fn.CallableRequest) -> dict:
    """Borrado self-service: el usuario autenticado elimina su PROPIA cuenta y
    todos sus datos. Obligatorio por la política de Google Play (borrado de
    cuenta desde la app) y por el RGPD (derecho de supresión). Irreversible."""
    if req.auth is None:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión."
        )

    uid = req.auth.uid
    db = firestore.client()
    stats = _purgar_usuario(db, uid)
    return {"ok": True, "uid": uid, "stats": stats}


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
        "token": d.get("tokenFCM") or None,
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

AVISO_CHAT_ESPERA_MS = 30 * 60 * 1000   # como mucho 1 email por conversación cada 30 min


def _enviar_aviso_email(db, uid: str, idioma: str, tipo: str, clave_limite: str = "", **campos) -> None:
    """Aviso por email (tipo 'mensaje' o 'valoracion') a un usuario con el email
    verificado. Nunca rompe el trigger: los fallos solo se registran."""
    try:
        if clave_limite:
            ref = db.collection("avisos_email").document(uid)
            ultimo = int(((ref.get().to_dict() or {}).get(clave_limite)) or 0)
            if _ahora_ms() - ultimo < AVISO_CHAT_ESPERA_MS:
                return
            ref.set({clave_limite: _ahora_ms()}, merge=True)
        usuario = auth.get_user(uid)
        if not usuario.email or not usuario.email_verified:
            return
        t = _AVISOS_EMAIL.get(idioma, _AVISOS_EMAIL["Español"])
        asunto, cuerpo = (t[0], t[1]) if tipo == "mensaje" else (t[2], t[3])
        _enviar_email(usuario.email, asunto.format(**campos), cuerpo.format(**campos), t[4], idioma)
    except Exception as e:
        print(f"[EMAIL] No se pudo enviar el aviso '{tipo}' a {uid}: {e}")


def _crear_notificacion_in_app(db, uid: str, titulo: str, detalle: str, tipo: str) -> None:
    """Añade una entrada al historial de notificaciones del usuario (/notificaciones)."""
    ref = db.collection("notificaciones").document()
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


def _enviar_push(token, titulo: str, cuerpo: str, data: dict = None, sonido: bool = True) -> None:
    """Envía un push por FCM. Si el token ya no es válido lo ignora sin fallar."""
    if not token:
        return
    tipo = (data or {}).get("tipo", "general")
    canal = _canal_para_tipo(str(tipo)) if sonido else CANAL_SILENCIO
    msg = messaging.Message(
        token=token,
        notification=messaging.Notification(title=titulo, body=cuerpo),
        data={k: str(v) for k, v in (data or {}).items()},
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id=canal,
                sound="default" if sonido else None,
            ),
        ),
    )
    try:
        messaging.send(msg)
    except (messaging.UnregisteredError, messaging.SenderIdMismatchError):
        pass
    except Exception as e:
        print(f"[FCM] Error enviando push a token {token[:20]}...: {e}")


# ════════════════════════════════════════════════════════════════════════
# Trigger: nuevo mensaje de chat → push al otro participante
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_created(
    document="chats/{chatId}/mensajes/{msgId}",
    region=REGION,
    secrets=[SMTP_USUARIO, SMTP_CLAVE],
)
def on_message_created(event) -> None:
    """Nuevo mensaje de chat: notificación en la app y, según los ajustes de cada
    destinatario, push y/o email."""
    if event.data is None:
        return

    msg = event.data.to_dict() or {}
    emisor_uid = msg.get("emisorUid", "")
    emisor_nombre = msg.get("emisorNombre", "Alguien")
    texto = msg.get("texto", "")
    tipo_msg = msg.get("tipo", "texto")
    chat_id = event.params["chatId"]

    cuerpo = texto if (tipo_msg == "texto" and texto) else "Adjunto"

    db = firestore.client()
    chat_snap = db.collection("chats").document(chat_id).get()
    if not chat_snap.exists:
        return
    participantes = (chat_snap.to_dict() or {}).get("participantes", [])
    destinatarios = [uid for uid in participantes if uid != emisor_uid]

    for uid in destinatarios:
        pref = _preferencias(db, uid)
        textos = _AVISOS_EMAIL.get(pref["idioma"], _AVISOS_EMAIL["Español"])
        _crear_notificacion_in_app(
            db, uid,
            titulo=textos[0].format(nombre=emisor_nombre),
            detalle=cuerpo[:200],
            tipo="chat",
        )
        if not pref["mensajes"]:
            continue
        if pref["push"]:
            _enviar_push(
                pref["token"],
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


# ════════════════════════════════════════════════════════════════════════
# Trigger: reseña creada/modificada/borrada → recalcular rating
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_written(
    document="resenas/{resenaId}",
    region=REGION,
    secrets=[SMTP_USUARIO, SMTP_CLAVE],
)
def on_resena_changed(event) -> None:
    """Reseña creada, editada o borrada: recalcula la nota del profesional y, si es
    nueva, le avisa (notificación, push y/o email según sus ajustes)."""
    before = event.data.before
    after = event.data.after

    tecnicos_afectados = set()
    if before is not None and before.exists:
        tid = (before.to_dict() or {}).get("tecnicoId")
        if tid:
            tecnicos_afectados.add(tid)
    if after is not None and after.exists:
        tid = (after.to_dict() or {}).get("tecnicoId")
        if tid:
            tecnicos_afectados.add(tid)

    db = firestore.client()
    for tecnico_id in tecnicos_afectados:
        _recalcular_rating(db, tecnico_id)

    es_creacion = (before is None or not before.exists) and after is not None and after.exists
    if es_creacion:
        data = after.to_dict() or {}
        tecnico_id = data.get("tecnicoId", "")
        nombre_usuario = data.get("nombreUsuario", "Un cliente")
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
            )
            if pref["valoraciones"]:
                if pref["push"]:
                    _enviar_push(
                        pref["token"],
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


def _recalcular_rating(db, tecnico_id: str) -> None:
    """Recalcula `rating` y `opiniones` del profesional a partir de todas sus reseñas."""
    q = db.collection("resenas").where(filter=FieldFilter("tecnicoId", "==", tecnico_id))
    docs = list(q.stream())
    n = len(docs)
    if n == 0:
        db.collection("tecnicos").document(tecnico_id).set(
            {"rating": 0.0, "opiniones": 0},
            merge=True,
        )
        return
    total = sum((d.to_dict() or {}).get("puntuacion", 0) for d in docs)
    promedio = round(total / n, 2)
    db.collection("tecnicos").document(tecnico_id).set(
        {"rating": promedio, "opiniones": n},
        merge=True,
    )


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


def _huella_codigo(uid: str, codigo: str) -> str:
    """HMAC del código ligado al usuario: en Firestore nunca está en claro."""
    return hmac.new(SMTP_CLAVE.value.encode(), f"{uid}:{codigo}".encode(), hashlib.sha256).hexdigest()


def _generar_codigo_nuevo(uid: str, historial: list) -> tuple:
    """Código aleatorio de 6 dígitos que este usuario no ha recibido nunca."""
    usados = set(historial)
    for _ in range(50):
        codigo = f"{secrets.randbelow(1_000_000):06d}"
        huella = _huella_codigo(uid, codigo)
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


# enforce_app_check=False a propósito: los builds de depuración no instalan
# App Check y, si no, nadie podría verificar su email para entrar. Están
# protegidas por autenticación y por los límites de envío e intentos.
@https_fn.on_call(region=REGION, secrets=[SMTP_USUARIO, SMTP_CLAVE])
def enviar_codigo_verificacion(req: https_fn.CallableRequest) -> dict:
    """Envía un código nuevo. Args: idioma (str). Devuelve {verificado} o
    {enviado, esperaSeg} si hay que esperar para reenviar."""
    if req.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión.")
    uid = req.auth.uid
    usuario = auth.get_user(uid)
    if usuario.email_verified:
        return {"verificado": True}
    if not usuario.email:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.FAILED_PRECONDITION, "La cuenta no tiene email.")

    idioma = str((req.data or {}).get("idioma") or "Español")
    db = firestore.client()
    ref = db.collection("verificaciones_email").document(uid)
    ahora = _ahora_ms()
    hoy = time.strftime("%Y%m%d", time.gmtime())

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
        codigo, huella = _generar_codigo_nuevo(uid, historial)
        txn.set(ref, {
            "huella": huella,
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


@https_fn.on_call(region=REGION, secrets=[SMTP_USUARIO, SMTP_CLAVE])
def verificar_codigo_email(req: https_fn.CallableRequest) -> dict:
    """Comprueba el código. Args: codigo (str de 6 dígitos)."""
    if req.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión.")
    uid = req.auth.uid
    codigo = str((req.data or {}).get("codigo") or "").strip()
    if not re.fullmatch(r"\d{6}", codigo):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "El código tiene 6 dígitos.")

    if auth.get_user(uid).email_verified:
        return {"verificado": True}

    db = firestore.client()
    ref = db.collection("verificaciones_email").document(uid)
    ahora = _ahora_ms()

    @firestore.transactional
    def _comprobar(txn):
        d = ref.get(transaction=txn).to_dict() or {}
        huella = d.get("huella")
        if not huella or int(d.get("expira") or 0) < ahora:
            return "caducado", 0
        intentos = int(d.get("intentos") or 0)
        if intentos >= CODIGO_MAX_INTENTOS:
            return "bloqueado", 0
        if hmac.compare_digest(huella, _huella_codigo(uid, codigo)):
            # Un solo uso: se borra la huella activa (queda en el historial)
            txn.update(ref, {"huella": firestore.DELETE_FIELD, "intentos": 0})
            return "ok", 0
        intentos += 1
        cambios = {"intentos": intentos}
        if intentos >= CODIGO_MAX_INTENTOS:
            cambios["huella"] = firestore.DELETE_FIELD
        txn.update(ref, cambios)
        return "incorrecto", CODIGO_MAX_INTENTOS - intentos

    resultado, restantes = _comprobar(db.transaction())
    if resultado == "caducado":
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.FAILED_PRECONDITION, "El código ha caducado. Pide uno nuevo.", {"motivo": "caducado"})
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

    auth.update_user(uid, email_verified=True)
    return {"verificado": True}


# ════════════════════════════════════════════════════════════════════════
# IA: consejos personalizados sobre el informe (Gemini en Vertex AI)
# ════════════════════════════════════════════════════════════════════════
#
# Se ejecuta en el servidor con la cuenta de servicio de la función: la app no
# lleva ninguna clave. A Gemini solo se le envían las características técnicas
# de la vivienda y los resultados del informe (nunca nombre, dirección ni
# email). El resultado se guarda en el propio informe (caché por idioma) y hay
# un límite diario por usuario para acotar el coste.

GEMINI_MODELOS = [m for m in os.environ.get("GEMINI_MODEL", "gemini-2.5-flash,gemini-2.0-flash-001").split(",") if m]
GEMINI_UBICACIONES = ["europe-west1", "global"]
IA_LIMITE_DIARIO = int(os.environ.get("IA_LIMITE_DIARIO", "10"))

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
]


def _prompt_ia(informe: dict, vivienda: dict, idioma: str) -> str:
    """Construye el prompt para Gemini SIN datos personales (ni nombre, ni
    dirección, ni email): solo características técnicas y resultados."""
    datos_vivienda = {k: vivienda.get(k) for k in _CAMPOS_VIVIENDA_IA if vivienda.get(k) not in (None, "", 0)}
    recomendaciones = [
        {"mejora": r.get("titulo"), "ahorro_eur_anio": r.get("ahorroEuros"), "ahorro_pct": r.get("ahorroEstimado")}
        for r in (informe.get("recomendaciones") or [])
    ]
    resultado = {
        "etiqueta": informe.get("etiqueta"),
        "consumo_kwh_anio": informe.get("consumoEstimado"),
        "consumo_kwh_m2_anio": informe.get("consumoPorM2"),
        "emisiones_kg_co2_anio": informe.get("emisiones"),
        "coste_eur_anio": informe.get("costeAnual"),
    }
    return (
        "Eres un asesor de eficiencia energética de viviendas en España. "
        "Con los datos de esta vivienda y su informe, da consejos concretos, "
        "realistas y ordenados por impacto. No repitas literalmente las mejoras "
        "ya calculadas: explica cuál priorizar y por qué según la zona y el tipo "
        "de vivienda, y añade consejos que el cálculo no contempla (ventilación, "
        "puentes térmicos, termostato, tarifa eléctrica, ayudas públicas españolas "
        "como el PREE o las deducciones del IRPF por eficiencia). No inventes "
        "cifras distintas de las del informe.\n\n"
        f"VIVIENDA: {json.dumps(datos_vivienda, ensure_ascii=False)}\n"
        f"INFORME: {json.dumps(resultado, ensure_ascii=False)}\n"
        f"MEJORAS YA CALCULADAS: {json.dumps(recomendaciones, ensure_ascii=False)}\n\n"
        f"Responde en el idioma «{idioma}». 'resumen': 2-3 frases sobre la situación. "
        "'consejos': entre 3 y 5, título corto y detalle de 1-3 frases. "
        "'habitos': 3 hábitos sin coste para ahorrar desde hoy."
    )


def _llamar_gemini(prompt: str) -> dict:
    """Llama a Gemini en Vertex AI con respuesta JSON estructurada. Prueba cada
    modelo configurado en cada región hasta que uno responda."""
    from google import genai
    from google.genai import types

    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=_ESQUEMA_IA,
        temperature=0.4,
        max_output_tokens=2048,
    )
    ultimo_error = None
    for ubicacion in GEMINI_UBICACIONES:
        client = genai.Client(vertexai=True, project=PROJECT_ID, location=ubicacion)
        for modelo in GEMINI_MODELOS:
            try:
                resp = client.models.generate_content(model=modelo, contents=prompt, config=config)
                return json.loads(resp.text)
            except Exception as e:  # modelo no disponible en la región, cuota, etc.
                ultimo_error = e
                print(f"[IA] {modelo}@{ubicacion} falló: {str(e)[:200]}")
    raise RuntimeError(f"Gemini no disponible: {ultimo_error}")


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
)
def generar_sugerencias_ia(req: https_fn.CallableRequest) -> dict:
    """Consejos personalizados para un informe del usuario, generados con Gemini.
    Devuelve los guardados si ya existen en ese idioma (salvo `regenerar`).

    Args: informeId (str), idioma (str, nombre del idioma de la app),
    regenerar (bool, ignora la caché)."""
    if req.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión.")
    uid = req.auth.uid
    data = req.data or {}
    informe_id = str(data.get("informeId") or "")
    idioma = str(data.get("idioma") or "Español")[:30]
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

    # Límite diario por usuario (transaccional para que no se cuele en paralelo)
    hoy = time.strftime("%Y%m%d", time.gmtime())
    limite_ref = db.collection("limites_ia").document(uid)

    @firestore.transactional
    def _consumir(txn):
        d = (limite_ref.get(transaction=txn).to_dict() or {})
        usados = int(d.get("usados") or 0) if d.get("dia") == hoy else 0
        if usados >= IA_LIMITE_DIARIO:
            return False
        txn.set(limite_ref, {"dia": hoy, "usados": usados + 1})
        return True

    if not _consumir(db.transaction()):
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED,
            "Has alcanzado el límite diario de consultas a la IA.",
        )

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
    (/estadisticas/{tecnicoId}), por tipo y por día."""
    datos = event.data.to_dict() if event.data else {}
    tipo = datos.get("tipo")
    dia = str(datos.get("dia") or "")
    if tipo not in TIPOS_EVENTO or len(dia) != 8:
        return
    tecnico_id = event.params["tecnicoId"]
    firestore.client().collection("estadisticas").document(tecnico_id).set({
        "total": {tipo: firestore.Increment(1)},
        "dias": {dia: {tipo: firestore.Increment(1)}},
        "actualizado": _ahora_ms(),
    }, merge=True)


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
            )
        expiry_ms = _expiry_de(data, product_id)
        if not expiry_ms:
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
                "La compra no corresponde a ese plan.",
            )
        if data.get("subscriptionState") not in ESTADOS_VIGENTES or expiry_ms <= _ahora_ms():
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
                "La suscripción no está activa en Google Play.",
            )
        if data.get("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_PENDING":
            _acknowledge_play(product_id, token)
        return "play", expiry_ms, data

    if data == {}:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.PERMISSION_DENIED,
            "Google Play no reconoce esta compra.",
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
    if req.auth is None:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Debes iniciar sesión."
        )
    uid = req.auth.uid
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
            _crear_notificacion_in_app(
                db, uid,
                titulo="¡Suscripción activada!",
                detalle=f"Tu plan {plan_nombre.replace('_', ' ').title()} está activo. Ya apareces destacado en el directorio.",
                tipo="suscripcion",
            )
    except Exception as e:
        print(f"[SUSCRIPCION] Error notificando: {e}")

    return {"ok": True, "resultado": resultado, "plan": plan_final, "hastaMs": hasta}


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


@scheduler_fn.on_schedule(
    schedule="every day 05:00",
    timezone=scheduler_fn.Timezone("Europe/Madrid"),
    region=REGION,
)
def revisar_suscripciones_diario(event: scheduler_fn.ScheduledEvent) -> None:
    """Red de seguridad por si falla o no está configurado RTDN."""
    db = firestore.client()
    ahora = _ahora_ms()
    limite = ahora + 3 * DIA_MS

    # 1) Suscripciones que caducan pronto o ya caducaron: re-sincronizar con Play
    for d in db.collection("suscripciones").where(filter=FieldFilter("activa", "==", True)).stream():
        if int((d.to_dict() or {}).get("fechaFin") or 0) < limite:
            _sincronizar_token(db, d.id)

    # 2) Profesionales con el plan visible vencido (p. ej. caducó el destacado
    #    pero sigue vigente el verificado): recalcular
    vencidos = (
        db.collection("tecnicos")
        .where(filter=FieldFilter("suscripcionHasta", ">", 0))
        .where(filter=FieldFilter("suscripcionHasta", "<", ahora))
        .stream()
    )
    for d in vencidos:
        _recalcular_plan(db, d.id)

    # 3) Privacidad: eventos individuales de estadísticas de más de 48 h
    try:
        print(f"[ESTADISTICAS] Eventos antiguos borrados: {_purgar_eventos_antiguos(db)}")
    except Exception as e:
        print(f"[ESTADISTICAS] No se pudieron purgar eventos: {e}")


# ════════════════════════════════════════════════════════════════════════
# Backup diario automático de Firestore (Cloud Scheduler)
# ════════════════════════════════════════════════════════════════════════

@scheduler_fn.on_schedule(
    schedule="every day 04:00",
    timezone=scheduler_fn.Timezone("Europe/Madrid"),
    region=REGION,
)
def backup_firestore_diario(event: scheduler_fn.ScheduledEvent) -> None:
    """Exporta toda la base de datos a Cloud Storage cada madrugada (04:00, Madrid)."""
    creds, _ = default_creds(scopes=["https://www.googleapis.com/auth/cloud-platform"])
    sess = AuthorizedSession(creds)

    ts = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    output_prefix = f"{BACKUP_BUCKET}/{ts}"

    url = (
        f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}"
        f"/databases/(default):exportDocuments"
    )
    body = {
        "outputUriPrefix": output_prefix,
        "collectionIds": [],
    }
    r = sess.post(url, json=body, timeout=60)
    if r.status_code >= 300:
        print(f"[BACKUP] ERROR HTTP {r.status_code}: {r.text}")
        raise RuntimeError(f"Backup falló: {r.text}")
    op = r.json().get("name", "(sin nombre)")
    print(f"[BACKUP] Iniciado export a {output_prefix} (op: {op})")
