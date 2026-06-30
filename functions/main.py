"""
Cloud Functions de ZeroHaus.

Operaciones server-side reservadas al administrador, que el cliente Android
no puede ejecutar por seguridad (borrado de Auth, cascadas masivas, etc.).

Despliegue:
  GOOGLE_APPLICATION_CREDENTIALS=$KEY firebase deploy --only functions
"""
import json
import time
import os
from firebase_functions import https_fn, firestore_fn, scheduler_fn, options
from firebase_admin import initialize_app, auth, firestore, messaging
from google.cloud.firestore_v1 import FieldFilter
from google.auth import default as default_creds
from google.auth.transport.requests import AuthorizedSession

initialize_app()

PROJECT_ID = "zerohaus-2a865"
BACKUP_BUCKET = "gs://zerohaus-2a865-backups/firestore"


def _ahora_ms() -> int:
    """Timestamp en milisegundos: el formato que usa la app Android para
    el campo `fecha` (un Long). NO usar SERVER_TIMESTAMP porque al leer
    el doc con toObject(Notificacion::class.java) rompería la conversión
    (Timestamp != Long)."""
    return int(time.time() * 1000)

REGION = "europe-west1"  # Bélgica, más cerca de España = menor latencia

# Colecciones donde el `uid` del usuario aparece como propietario del documento.
# Se borran con query `where(campo, ==, uid)`.
COLECCIONES_POR_CAMPO_UID = [
    ("viviendas",      "uid"),
    ("informes",       "uid"),
    ("certificados",   "uid"),
    ("notificaciones", "uid"),
]

# Colecciones donde el usuario puede aparecer en uno de varios campos
# (cliente o técnico). Se borran con queries OR (dos pasadas).
COLECCIONES_DOBLE_CAMPO = [
    ("proyectos",   ["uid", "tecnicoUid"]),
    ("solicitudes", ["uidCliente", "tecnicoUid"]),
    ("pagos",       ["uidCliente", "tecnicoUid"]),
    ("resenas",     ["uid", "tecnicoId"]),
]

# Docs únicos cuyo id == uid del usuario.
DOCS_POR_ID = ["usuarios", "tecnicos", "ajustes"]


def _verificar_admin(req: https_fn.CallableRequest) -> None:
    """Comprueba el custom claim `admin: true` del JWT del caller.

    El claim se asigna server-side con `set_admin_claim.py` (Admin SDK) y NO
    es modificable desde el cliente. No comparamos por email para evitar
    filtrar la identidad del admin en el código distribuido.
    """
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
    """Borra todos los docs que cumplen la query en batches. Devuelve total borrados."""
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
    """Borra todos los docs de una subcolección."""
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


@https_fn.on_call(
    region=REGION,
    cors=options.CorsOptions(cors_origins="*", cors_methods=["post"]),
)
def eliminar_usuario_completo(req: https_fn.CallableRequest) -> dict:
    """
    Borra **definitivamente** un usuario:
      1. Cuenta de Firebase Auth.
      2. Documento en /usuarios, /tecnicos, /ajustes.
      3. Cascada en colecciones donde el uid aparece como dueño o como
         contraparte (cliente/técnico): viviendas, informes, proyectos,
         certificados, notificaciones, resenas, solicitudes, pagos.
      4. Chats donde el usuario participa, incluyendo sus subcolecciones
         de mensajes.

    Args (vía request.data):
        uid (str): UID del usuario a eliminar.

    Returns:
        dict con conteo por colección.

    Raises:
        UNAUTHENTICATED si no hay sesión.
        PERMISSION_DENIED si el caller no es el admin.
        INVALID_ARGUMENT si falta uid.
        FAILED_PRECONDITION si se intenta borrar al propio admin.
    """
    _verificar_admin(req)

    uid = (req.data or {}).get("uid")
    if not uid or not isinstance(uid, str):
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
            "Falta el parámetro 'uid'."
        )

    # Protección anti-auto-eliminación.
    caller_uid = req.auth.uid if req.auth else None
    if uid == caller_uid:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "El administrador no puede eliminarse a sí mismo."
        )

    db = firestore.client()
    stats: dict = {}

    # 1) Cascada en colecciones por campo simple.
    for col, campo in COLECCIONES_POR_CAMPO_UID:
        q = db.collection(col).where(campo, "==", uid)
        stats[col] = _borrar_query(db, q)

    # 2) Cascada en colecciones con dos posibles campos (cliente / técnico).
    for col, campos in COLECCIONES_DOBLE_CAMPO:
        total = 0
        for campo in campos:
            q = db.collection(col).where(campo, "==", uid)
            total += _borrar_query(db, q)
        stats[col] = total

    # 3) Chats: el usuario aparece en el array `participantes`. Para cada chat
    # eliminamos también la subcolección 'mensajes' (Firestore no la borra en
    # cascada automáticamente).
    chats_borrados = 0
    mensajes_borrados = 0
    chats = db.collection("chats").where("participantes", "array_contains", uid).stream()
    for chat in chats:
        mensajes_borrados += _borrar_subcoleccion(db, chat.reference, "mensajes")
        chat.reference.delete()
        chats_borrados += 1
    stats["chats"] = chats_borrados
    stats["mensajes"] = mensajes_borrados

    # 4) Docs cuyo id == uid (incluye el propio /usuarios/{uid}).
    for col in DOCS_POR_ID:
        try:
            db.collection(col).document(uid).delete()
            stats[col] = 1
        except Exception:
            stats[col] = 0

    # 5) Cuenta de Firebase Auth (lo último, para que si falla algo arriba
    # el usuario todavía pueda volver a entrar y avisar).
    try:
        auth.delete_user(uid)
        stats["auth"] = 1
    except auth.UserNotFoundError:
        stats["auth"] = 0  # ya no existía, no es error
    except Exception as e:
        # Si el Auth falla pero ya borramos todo lo demás, propagamos el error
        # con info para que el admin lo vea.
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INTERNAL,
            f"Datos borrados pero falló el borrado de Auth: {e}"
        )

    return {"ok": True, "uid": uid, "stats": stats}


# ════════════════════════════════════════════════════════════════════════
# Helpers para FCM y notificaciones in-app
# ════════════════════════════════════════════════════════════════════════

def _obtener_token_fcm(db, uid: str):
    """Lee el tokenFCM de /ajustes/{uid}. None si no hay token o doc.

    Vive en /ajustes (cerrado a esMio||esAdmin) en vez de /usuarios (lectura
    abierta), para evitar harvesting cross-user de tokens FCM. El Admin SDK
    se salta las rules, asi que esta funcion lo lee sin problemas.
    """
    snap = db.collection("ajustes").document(uid).get()
    if not snap.exists:
        return None
    token = (snap.to_dict() or {}).get("tokenFCM", "")
    return token if token else None


def _crear_notificacion_in_app(db, uid: str, titulo: str, detalle: str, tipo: str) -> None:
    """Crea un doc en /notificaciones para que se vea dentro de la app.
    Replica la firma exacta del modelo Notificacion de Kotlin (id como
    campo del doc, fecha como Long en millis)."""
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
    """Mapea el tipo de notificación al channel_id que la app Android crea
    en NotificacionesLocales.crearCanales. Si el canal no existe en el
    dispositivo (versión vieja de la app), Android cae al canal default."""
    return {
        "chat":        "zerohaus_chat_v2",
        "mensaje":     "zerohaus_chat_v2",
        "presupuesto": "zerohaus_presupuesto_v2",
        "proyecto":    "zerohaus_proyecto_v2",
        "reforma":     "zerohaus_proyecto_v2",
        "valoracion":  "zerohaus_general_v2",
    }.get(tipo, "zerohaus_general_v2")


def _enviar_push(token, titulo: str, cuerpo: str, data: dict = None) -> None:
    """Envía una push notification vía FCM. Silencia errores de token inválido
    (común si el usuario reinstaló o desinstaló la app). El channel_id se
    deriva del `tipo` del data, así Android usa el canal correcto cuando la
    app está en background."""
    if not token:
        return
    tipo = (data or {}).get("tipo", "general")
    canal = _canal_para_tipo(str(tipo))
    msg = messaging.Message(
        token=token,
        notification=messaging.Notification(title=titulo, body=cuerpo),
        data={k: str(v) for k, v in (data or {}).items()},
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id=canal,
                sound="default",
            ),
        ),
    )
    try:
        messaging.send(msg)
    except (messaging.UnregisteredError, messaging.SenderIdMismatchError):
        # Token caducado/inválido: no es error grave. La próxima vez que el
        # usuario abra la app, el registro refrescará el token.
        pass
    except Exception as e:
        print(f"[FCM] Error enviando push a token {token[:20]}...: {e}")


# ════════════════════════════════════════════════════════════════════════
# Trigger: nuevo mensaje de chat → push al otro participante
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_created(
    document="chats/{chatId}/mensajes/{msgId}",
    region=REGION,
)
def on_message_created(event) -> None:
    """Cuando se crea un mensaje en un chat, notifica a los OTROS participantes
    con una push notification + entrada en /notificaciones."""
    if event.data is None:
        return

    msg = event.data.to_dict() or {}
    emisor_uid = msg.get("emisorUid", "")
    emisor_nombre = msg.get("emisorNombre", "Alguien")
    texto = msg.get("texto", "")
    tipo_msg = msg.get("tipo", "texto")
    chat_id = event.params["chatId"]

    # Si es media, dar un cuerpo legible.
    cuerpo = texto if (tipo_msg == "texto" and texto) else "Adjunto"

    db = firestore.client()
    chat_snap = db.collection("chats").document(chat_id).get()
    if not chat_snap.exists:
        return
    participantes = (chat_snap.to_dict() or {}).get("participantes", [])
    destinatarios = [uid for uid in participantes if uid != emisor_uid]

    for uid in destinatarios:
        _crear_notificacion_in_app(
            db, uid,
            titulo=f"Nuevo mensaje de {emisor_nombre}",
            detalle=cuerpo[:200],
            tipo="chat",
        )
        token = _obtener_token_fcm(db, uid)
        _enviar_push(
            token,
            titulo=emisor_nombre,
            cuerpo=cuerpo[:200],
            data={"tipo": "chat", "chatId": chat_id},
        )


# ════════════════════════════════════════════════════════════════════════
# Trigger: reseña creada/modificada/borrada → recalcular rating del técnico
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_written(
    document="resenas/{resenaId}",
    region=REGION,
)
def on_resena_changed(event) -> None:
    """Recalcula el rating agregado del técnico tras cualquier cambio en /resenas
    y notifica al técnico cuando se crea una reseña nueva."""
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
            _crear_notificacion_in_app(
                db, tecnico_id,
                titulo="Has recibido una valoración",
                detalle=f"{nombre_usuario} te ha valorado con {puntuacion}/5 ({estrellas})",
                tipo="valoracion",
            )
            token = _obtener_token_fcm(db, tecnico_id)
            _enviar_push(
                token,
                titulo="Nueva valoración recibida",
                cuerpo=f"{nombre_usuario}: {puntuacion}/5",
                data={"tipo": "valoracion", "tecnicoId": tecnico_id},
            )


def _recalcular_rating(db, tecnico_id: str) -> None:
    """Cuenta y promedia todas las reseñas del técnico y actualiza /tecnicos/{id}.
    El rating en el doc del técnico siempre refleja la realidad sin depender
    de que el cliente lo actualice correctamente."""
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
# Trigger: nueva solicitud de presupuesto → notificar al técnico
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_created(
    document="solicitudes/{solId}",
    region=REGION,
)
def on_solicitud_created(event) -> None:
    """Cuando un cliente crea una solicitud de presupuesto, avisa al técnico."""
    if event.data is None:
        return
    sol = event.data.to_dict() or {}
    tecnico_uid = sol.get("tecnicoUid", "")
    nombre_cliente = sol.get("nombreCliente", "Un cliente")
    descripcion = sol.get("descripcion", "")
    sol_id = event.params["solId"]
    if not tecnico_uid:
        return

    db = firestore.client()
    _crear_notificacion_in_app(
        db, tecnico_uid,
        titulo="Nueva solicitud de presupuesto",
        detalle=f"{nombre_cliente}: {descripcion[:150]}",
        tipo="presupuesto",
    )
    token = _obtener_token_fcm(db, tecnico_uid)
    _enviar_push(
        token,
        titulo="Nueva solicitud de presupuesto",
        cuerpo=f"{nombre_cliente} quiere un presupuesto",
        data={"tipo": "presupuesto", "solicitudId": sol_id},
    )


# ════════════════════════════════════════════════════════════════════════
# Trigger: cambio de estado en solicitud → notificar a la otra parte
# ════════════════════════════════════════════════════════════════════════

@firestore_fn.on_document_updated(
    document="solicitudes/{solId}",
    region=REGION,
)
def on_solicitud_estado_cambiado(event) -> None:
    """Cuando cambia el estado de una solicitud, notifica a quien deba enterarse.
    Cubre las transiciones clave del flujo: Presupuestado, Aceptado, EnCurso,
    Completado, etc."""
    antes = (event.data.before.to_dict() or {})
    despues = (event.data.after.to_dict() or {})
    estado_antes = antes.get("estado", "")
    estado_despues = despues.get("estado", "")
    if estado_antes == estado_despues:
        return

    sol_id = event.params["solId"]
    uid_cliente = despues.get("uidCliente", "")
    tecnico_uid = despues.get("tecnicoUid", "")
    tecnico_nombre = despues.get("tecnicoNombre", "El técnico")
    nombre_cliente = despues.get("nombreCliente", "El cliente")

    motivo_rechazo_ficha = (despues.get("motivoRechazoFicha") or "").strip()
    detalle_ficha_rechazada = (
        f"{nombre_cliente} ha rechazado la ficha de inicio"
        + (f": {motivo_rechazo_ficha}" if motivo_rechazo_ficha else "")
        + ". Puedes ajustarla y reenviarla."
    )

    # Mapa: estado nuevo → (destinatario_uid, titulo, cuerpo, tipo).
    plantillas = {
        "Presupuestado": (uid_cliente, "Has recibido un presupuesto",
                          f"{tecnico_nombre} te ha enviado un presupuesto.", "presupuesto"),
        "Aceptado":      (tecnico_uid, "Presupuesto aceptado",
                          f"{nombre_cliente} ha aceptado tu presupuesto.", "proyecto"),
        "FichaEnviada":  (uid_cliente, "Ficha de inicio recibida",
                          f"{tecnico_nombre} te ha enviado la ficha de inicio.", "proyecto"),
        "FichaRechazada": (tecnico_uid, "Ficha rechazada",
                           detalle_ficha_rechazada, "presupuesto"),
        "EnCurso":       (tecnico_uid, "Proyecto en marcha",
                          f"{nombre_cliente} ha aceptado la ficha. ¡A trabajar!", "proyecto"),
        "PendientePago": (uid_cliente, "Trabajo finalizado",
                          f"{tecnico_nombre} ha marcado el trabajo como finalizado. Procede al pago.", "proyecto"),
        "PagoEnVerificacion": (tecnico_uid, "Cliente dice haber pagado",
                               f"{nombre_cliente} indica haber pagado. Verifica el cobro.", "proyecto"),
        "Completado":    (uid_cliente, "Proyecto completado",
                          f"{tecnico_nombre} ha confirmado el cobro. ¡Ya puedes valorar!", "proyecto"),
        "Rechazado":     (tecnico_uid, "Presupuesto rechazado",
                          f"{nombre_cliente} ha rechazado tu presupuesto.", "presupuesto"),
    }

    plantilla = plantillas.get(estado_despues)
    if plantilla is None:
        return
    destinatario, titulo, cuerpo, tipo = plantilla
    if not destinatario:
        return

    db = firestore.client()
    _crear_notificacion_in_app(db, destinatario, titulo, cuerpo, tipo)
    token = _obtener_token_fcm(db, destinatario)
    _enviar_push(token, titulo, cuerpo, {"tipo": tipo, "solicitudId": sol_id})

    # Contador denormalizado: al cerrar el proyecto, sumamos uno al técnico.
    # Lo hace el trigger (Admin SDK, se salta las rules) en lugar del cliente,
    # porque las rules de /tecnicos congelan `proyectosCompletados` para evitar
    # infladas de métricas desde un cliente custom.
    if estado_despues == "Completado":
        tecnico_id = despues.get("tecnicoId", "") or tecnico_uid
        if tecnico_id:
            db.collection("tecnicos").document(tecnico_id).update({
                "proyectosCompletados": firestore.Increment(1)
            })


# ════════════════════════════════════════════════════════════════════════
# Backup diario automático de Firestore (Cloud Scheduler)
# ════════════════════════════════════════════════════════════════════════

@scheduler_fn.on_schedule(
    schedule="every day 04:00",
    timezone=scheduler_fn.Timezone("Europe/Madrid"),
    region=REGION,
)
def backup_firestore_diario(event: scheduler_fn.ScheduledEvent) -> None:
    """Ejecuta `firestore.googleapis.com.../databases/(default):exportDocuments`
    cada noche a las 4:00 (hora España) y deja el snapshot en
    `gs://zerohaus-2a865-backups/firestore/<timestamp>`. El bucket tiene
    una lifecycle policy que borra exports de más de 90 días para evitar
    coste descontrolado."""
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
        # collectionIds vacío = exporta todas las colecciones.
        "collectionIds": [],
    }
    r = sess.post(url, json=body, timeout=60)
    if r.status_code >= 300:
        print(f"[BACKUP] ERROR HTTP {r.status_code}: {r.text}")
        raise RuntimeError(f"Backup falló: {r.text}")
    op = r.json().get("name", "(sin nombre)")
    print(f"[BACKUP] Iniciado export a {output_prefix} (op: {op})")

