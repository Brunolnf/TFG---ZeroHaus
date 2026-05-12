package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tarea
import com.example.zerohaus.Modelos.Tecnico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RepositorioTecnicos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    companion object {
        // Coordenadas aproximadas del centro de las ciudades españolas más comunes
        private val CIUDADES_COORDS = mapOf(
            "madrid"         to (40.4168 to -3.7038),
            "barcelona"      to (41.3851 to 2.1734),
            "valencia"       to (39.4699 to -0.3763),
            "sevilla"        to (37.3882 to -5.9823),
            "zaragoza"       to (41.6488 to -0.8891),
            "málaga"         to (36.7196 to -4.4200),
            "malaga"         to (36.7196 to -4.4200),
            "murcia"         to (37.9922 to -1.1307),
            "palma"          to (39.5696 to 2.6502),
            "las palmas"     to (28.1235 to -15.4363),
            "bilbao"         to (43.2630 to -2.9350),
            "alicante"       to (38.3452 to -0.4815),
            "córdoba"        to (37.8882 to -4.7794),
            "cordoba"        to (37.8882 to -4.7794),
            "valladolid"     to (41.6523 to -4.7245),
            "vigo"           to (42.2314 to -8.7124),
            "gijón"          to (43.5453 to -5.6615),
            "gijon"          to (43.5453 to -5.6615),
            "granada"        to (37.1773 to -3.5986),
            "pamplona"       to (42.8125 to -1.6458),
            "santander"      to (43.4623 to -3.8099),
            "san sebastián"  to (43.3128 to -1.9761),
            "donostia"       to (43.3128 to -1.9761),
            "badajoz"        to (38.8794 to -6.9706),
            "almería"        to (36.8340 to -2.4637),
            "almeria"        to (36.8340 to -2.4637),
            "logroño"        to (42.4627 to -2.4449),
            "burgos"         to (42.3439 to -3.6969),
            "salamanca"      to (40.9701 to -5.6635),
            "albacete"       to (38.9943 to -1.8585),
            "a coruña"       to (43.3713 to -8.3962),
            "huelva"         to (37.2614 to -6.9447),
            "jaén"           to (37.7796 to -3.7849),
            "jaen"           to (37.7796 to -3.7849),
            "toledo"         to (39.8628 to -4.0273),
            "cáceres"        to (39.4753 to -6.3723),
            "caceres"        to (39.4753 to -6.3723),
            "lleida"         to (41.6176 to 0.6200),
            "tarragona"      to (41.1189 to 1.2445),
            "girona"         to (41.9794 to 2.8214),
            "lugo"           to (43.0097 to -7.5567),
            "ourense"        to (42.3364 to -7.8641),
            "pontevedra"     to (42.4328 to -8.6459),
            "oviedo"         to (43.3619 to -5.8494),
            "león"           to (42.5987 to -5.5671),
            "leon"           to (42.5987 to -5.5671),
            "tenerife"       to (28.4636 to -16.2518),
            "santa cruz"     to (28.4636 to -16.2518),
            "vitoria"        to (42.8467 to -2.6716),
            "gasteiz"        to (42.8467 to -2.6716),
            "castellón"      to (39.9860 to -0.0513),
            "castellon"      to (39.9860 to -0.0513)
        )

        fun coordenadasDeCiudad(ciudad: String): Pair<Double, Double>? =
            CIUDADES_COORDS[ciudad.trim().lowercase()]
    }

    fun obtenerTecnicos(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos")
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { doc ->
                    doc.toObject(Tecnico::class.java)?.let { t ->
                        if (t.id.isBlank()) t.copy(id = doc.id) else t
                    }
                })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun obtenerTecnico(id: String, callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos").document(id).get()
            .addOnSuccessListener { doc ->
                val t = doc.toObject(Tecnico::class.java)
                callback(if (t != null && t.id.isBlank()) t.copy(id = doc.id) else t)
            }
            .addOnFailureListener { callback(null) }
    }

    fun obtenerRanking(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos")
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { doc ->
                    doc.toObject(Tecnico::class.java)?.let { t ->
                        if (t.id.isBlank()) t.copy(id = doc.id) else t
                    }
                })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun solicitarPresupuesto(solicitud: SolicitudPresupuesto, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document()
        val s = solicitud.copy(id = ref.id, uidCliente = uid())
        ref.set(s)
            .addOnSuccessListener {
                // Notificación de confirmación para el cliente
                crearNotif(
                    uid = uid(),
                    titulo = "Solicitud enviada a ${solicitud.tecnicoNombre}",
                    detalle = "Tu solicitud de presupuesto ha sido enviada. Recibirás una notificación cuando el técnico responda.",
                    tipo = "presupuesto"
                )
                // Notificación para el técnico (usamos tecnicoUid = Auth UID real)
                if (solicitud.tecnicoUid.isNotEmpty()) {
                    crearNotif(
                        uid = solicitud.tecnicoUid,
                        titulo = "Nueva solicitud de presupuesto",
                        detalle = "${solicitud.nombreCliente} te ha enviado una solicitud de presupuesto.",
                        tipo = "presupuesto"
                    )
                }
                callback(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error enviando solicitud")))
            }
    }

    /** Devuelve el perfil de técnico vinculado al uid de Auth actual. */
    fun obtenerMiPerfilTecnico(callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos")
            .whereEqualTo("uid", uid())
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                val tec = doc?.toObject(Tecnico::class.java)
                callback(if (tec != null && doc != null && tec.id.isBlank()) tec.copy(id = doc.id) else tec)
            }
            .addOnFailureListener { callback(null) }
    }

    /** Actualiza los campos editables del perfil del técnico (incluyendo métodos de pago). */
    fun actualizarPerfilTecnico(
        tecnicoId: String,
        nombre: String,
        ciudad: String,
        descripcion: String,
        telefono: String,
        emailContacto: String,
        especialidades: List<String>,
        paypalUsername: String = "",
        bizumTelefono: String = "",
        callback: (Result<Unit>) -> Unit
    ) {
        val coords = coordenadasDeCiudad(ciudad)
        val datos = mutableMapOf<String, Any>(
            "nombre" to nombre,
            "ciudad" to ciudad,
            "descripcion" to descripcion,
            "telefono" to telefono,
            "emailContacto" to emailContacto,
            "especialidades" to especialidades,
            "paypalUsername" to paypalUsername,
            "bizumTelefono" to bizumTelefono
        )
        if (coords != null) {
            datos["latitud"] = coords.first
            datos["longitud"] = coords.second
        }
        db.collection("tecnicos").document(tecnicoId).update(datos)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error actualizando perfil")))
            }
    }

    fun registrarTecnico(tecnico: Tecnico, callback: (Result<Unit>) -> Unit) {
        val ref = if (tecnico.id.isNotEmpty()) {
            db.collection("tecnicos").document(tecnico.id)
        } else {
            db.collection("tecnicos").document()
        }
        val t = tecnico.copy(id = ref.id, uid = uid())
        ref.set(t)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error registrando técnico")))
            }
    }

    fun obtenerSolicitudesRecibidas(callback: (List<SolicitudPresupuesto>) -> Unit) {
        db.collection("solicitudes")
            .whereEqualTo("tecnicoUid", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(
                    snap.documents.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                        .sortedByDescending { it.fechaCreacion }
                )
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun obtenerMisSolicitudes(callback: (List<SolicitudPresupuesto>) -> Unit) {
        // Sin orderBy para evitar requerir índice compuesto en Firestore
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(
                    snap.documents.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                        .sortedByDescending { it.fechaCreacion }
                )
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun responderPresupuesto(
        solicitudId: String, precio: Double, respuesta: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val uidCliente = doc.getString("uidCliente") ?: ""
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            ref.update(
                mapOf(
                    "estado" to "Presupuestado",
                    "precioPresupuesto" to precio,
                    "respuestaTecnico" to respuesta,
                    "fechaRespuesta" to System.currentTimeMillis()
                )
            ).addOnSuccessListener {
                if (uidCliente.isNotEmpty()) {
                    crearNotif(
                        uid = uidCliente,
                        titulo = "Presupuesto recibido de $tecnicoNombre",
                        detalle = "$tecnicoNombre ha respondido con un presupuesto de ${precio.toInt()} €. Ábrelo en Presupuestos para aceptarlo.",
                        tipo = "presupuesto"
                    )
                }
                callback(Result.success(Unit))
            }.addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error respondiendo")))
            }
        }.addOnFailureListener { e ->
            callback(Result.failure(Exception(e.message ?: "Error obteniendo solicitud")))
        }
    }

    fun aceptarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            val precio = doc.getDouble("precioPresupuesto") ?: 0.0
            ref.update("estado", "Aceptado")
                .addOnSuccessListener {
                    crearNotif(
                        uid = uid(),
                        titulo = "Presupuesto aceptado ✓",
                        detalle = "Has aceptado el presupuesto de $tecnicoNombre (${precio.toInt()} €). El técnico se pondrá en contacto pronto.",
                        tipo = "presupuesto"
                    )
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error aceptando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun rechazarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            ref.update("estado", "Rechazado")
                .addOnSuccessListener {
                    crearNotif(
                        uid = uid(),
                        titulo = "Presupuesto rechazado",
                        detalle = "Has rechazado el presupuesto de $tecnicoNombre.",
                        tipo = "presupuesto"
                    )
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error rechazando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /** TÉCNICO — envía la ficha de inicio cuando el cliente ya aceptó el presupuesto. */
    fun enviarFichaActividad(
        solicitudId: String,
        fechaInicio: Long,
        fechaFinEstimada: Long,
        descripcionFicha: String,
        precioFinal: Double,
        tareas: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val uidCliente = doc.getString("uidCliente") ?: ""
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            ref.update(
                mapOf(
                    "estado" to "FichaEnviada",
                    "fichaFechaInicio" to fechaInicio,
                    "fichaFechaFinEstimada" to fechaFinEstimada,
                    "fichaDescripcion" to descripcionFicha,
                    "fichaPrecioFinal" to precioFinal,
                    "fichaTareas" to tareas
                )
            ).addOnSuccessListener {
                if (uidCliente.isNotEmpty()) {
                    crearNotif(
                        uid = uidCliente,
                        titulo = "Ficha de inicio recibida",
                        detalle = "$tecnicoNombre te ha enviado los detalles del trabajo. Revísalos y confirma para empezar.",
                        tipo = "presupuesto"
                    )
                }
                callback(Result.success(Unit))
            }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error enviando ficha"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /** CLIENTE — acepta la ficha de inicio. Crea automáticamente el documento /proyectos. */
    fun aceptarFichaYCrearProyecto(solicitudId: String, callback: (Result<String>) -> Unit) {
        val refSol = db.collection("solicitudes").document(solicitudId)
        refSol.get().addOnSuccessListener { doc ->
            val sol = doc.toObject(SolicitudPresupuesto::class.java)
            if (sol == null) { callback(Result.failure(Exception("Solicitud no encontrada"))); return@addOnSuccessListener }

            val refProy = db.collection("proyectos").document()
            val proyecto = Proyecto(
                id = refProy.id,
                uid = sol.uidCliente,
                titulo = if (sol.fichaDescripcion.isNotBlank()) sol.fichaDescripcion.take(60) else "Reforma con ${sol.tecnicoNombre}",
                descripcion = sol.fichaDescripcion.ifBlank { sol.descripcion },
                viviendaNombre = "",
                tecnicoId = sol.tecnicoId,
                tecnicoUid = sol.tecnicoUid,
                tecnicoNombre = sol.tecnicoNombre,
                progreso = 0,
                estado = "En curso",
                tareas = sol.fichaTareas.map { Tarea(nombre = it, completada = false) },
                fechaInicio = sol.fichaFechaInicio,
                fechaFinEstimada = sol.fichaFechaFinEstimada,
                precio = sol.fichaPrecioFinal,
                solicitudId = sol.id,
                pagado = false
            )

            refProy.set(proyecto)
                .addOnSuccessListener {
                    refSol.update(
                        mapOf("estado" to "EnCurso", "proyectoId" to refProy.id)
                    ).addOnSuccessListener {
                        // Notificación al técnico
                        if (sol.tecnicoUid.isNotEmpty()) {
                            crearNotif(
                                uid = sol.tecnicoUid,
                                titulo = "Reforma iniciada ✓",
                                detalle = "${sol.nombreCliente} ha aceptado la ficha. El proyecto ya está en marcha.",
                                tipo = "proyecto"
                            )
                        }
                        callback(Result.success(refProy.id))
                    }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error actualizando solicitud"))) }
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error creando proyecto"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /** CLIENTE — rechaza la ficha de inicio (vuelve a "Aceptado" para que el técnico la ajuste). */
    fun rechazarFicha(solicitudId: String, motivo: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoUid = doc.getString("tecnicoUid") ?: ""
            val nombreCliente = doc.getString("nombreCliente") ?: "Cliente"
            ref.update("estado", "Aceptado")
                .addOnSuccessListener {
                    if (tecnicoUid.isNotEmpty()) {
                        crearNotif(
                            uid = tecnicoUid,
                            titulo = "Ficha rechazada",
                            detalle = "$nombreCliente ha rechazado la ficha de inicio${if (motivo.isNotBlank()) ": $motivo" else ""}. Puedes ajustarla y reenviarla.",
                            tipo = "presupuesto"
                        )
                    }
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /** TÉCNICO — marca el trabajo como terminado, pendiente de pago. */
    fun marcarTrabajoTerminado(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val uidCliente = doc.getString("uidCliente") ?: ""
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            val precioFinal = doc.getDouble("fichaPrecioFinal") ?: 0.0
            ref.update("estado", "PendientePago")
                .addOnSuccessListener {
                    if (uidCliente.isNotEmpty()) {
                        crearNotif(
                            uid = uidCliente,
                            titulo = "Trabajo terminado ✓",
                            detalle = "$tecnicoNombre ha finalizado el trabajo. Importe a pagar: ${precioFinal.toInt()} €.",
                            tipo = "presupuesto"
                        )
                    }
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /**
     * CLIENTE — declara que ya ha pagado al técnico (por PayPal, Bizum, etc.).
     * Pasa la solicitud a "PagoEnVerificacion". El técnico tendrá que confirmar la recepción.
     */
    fun clienteMarcaPagado(
        solicitudId: String,
        metodo: String,
        referencia: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoUid = doc.getString("tecnicoUid") ?: ""
            val nombreCliente = doc.getString("nombreCliente") ?: "Cliente"
            val precio = doc.getDouble("fichaPrecioFinal") ?: 0.0
            val ahora = System.currentTimeMillis()

            ref.update(
                mapOf(
                    "estado" to "PagoEnVerificacion",
                    "metodoPago" to metodo,
                    "referenciaPago" to referencia,
                    "fechaPagoCliente" to ahora
                )
            ).addOnSuccessListener {
                if (tecnicoUid.isNotEmpty()) {
                    val medio = when (metodo) {
                        "paypal" -> "PayPal"
                        "bizum"  -> "Bizum"
                        else     -> "transferencia"
                    }
                    crearNotif(
                        uid = tecnicoUid,
                        titulo = "Pago en verificación",
                        detalle = "$nombreCliente dice haber pagado ${precio.toInt()} € por $medio. Verifica la recepción y confirma el cobro.",
                        tipo = "presupuesto"
                    )
                }
                callback(Result.success(Unit))
            }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /**
     * TÉCNICO — confirma haber recibido el pago. Cierra solicitud y proyecto.
     * Crea entrada en /pagos para historial.
     */
    fun tecnicoConfirmaPago(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val sol = doc.toObject(SolicitudPresupuesto::class.java)
            if (sol == null) { callback(Result.failure(Exception("Solicitud no encontrada"))); return@addOnSuccessListener }
            val ahora = System.currentTimeMillis()

            ref.update(
                mapOf(
                    "estado" to "Completado",
                    "pagado" to true,
                    "fechaPago" to ahora
                )
            ).addOnSuccessListener {
                // Cerrar proyecto
                if (sol.proyectoId.isNotEmpty()) {
                    db.collection("proyectos").document(sol.proyectoId).update(
                        mapOf("estado" to "Finalizado", "pagado" to true, "progreso" to 100)
                    )
                }
                // Histórico de pagos (opcional)
                val refPago = db.collection("pagos").document()
                refPago.set(
                    hashMapOf(
                        "id" to refPago.id,
                        "solicitudId" to sol.id,
                        "uidCliente" to sol.uidCliente,
                        "tecnicoUid" to sol.tecnicoUid,
                        "tecnicoNombre" to sol.tecnicoNombre,
                        "nombreCliente" to sol.nombreCliente,
                        "importe" to sol.fichaPrecioFinal,
                        "metodo" to sol.metodoPago,
                        "referencia" to sol.referenciaPago,
                        "fechaConfirmacion" to ahora
                    )
                )
                // Notificación al cliente
                if (sol.uidCliente.isNotEmpty()) {
                    crearNotif(
                        uid = sol.uidCliente,
                        titulo = "Pago confirmado ✓",
                        detalle = "${sol.tecnicoNombre} ha confirmado la recepción del pago. Ya puedes valorarlo.",
                        tipo = "reforma"
                    )
                }
                // Notificación al técnico
                crearNotif(
                    uid = uid(),
                    titulo = "Cobro confirmado",
                    detalle = "Has confirmado el cobro de ${sol.fichaPrecioFinal.toInt()} € de ${sol.nombreCliente}.",
                    tipo = "reforma"
                )
                callback(Result.success(Unit))
            }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /**
     * CLIENTE — rechaza el pago previamente declarado (por error o no quiere ya).
     * Vuelve a PendientePago.
     */
    fun cancelarMarcaPago(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("solicitudes").document(solicitudId).update(
            mapOf(
                "estado" to "PendientePago",
                "metodoPago" to "",
                "referenciaPago" to "",
                "fechaPagoCliente" to 0L
            )
        )
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun completarSolicitud(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            // tecnicoUid = Auth UID real; tecnicoId = doc ID de Firestore (no sirve para notif.)
            val tecnicoUid = doc.getString("tecnicoUid") ?: ""
            val nombreCliente = doc.getString("nombreCliente") ?: "Cliente"
            ref.update("estado", "Completado")
                .addOnSuccessListener {
                    crearNotif(
                        uid = uid(),
                        titulo = "Reforma completada ✓",
                        detalle = "Has marcado la reforma con $tecnicoNombre como completada. Ya puedes valorarlo.",
                        tipo = "reforma"
                    )
                    if (tecnicoUid.isNotEmpty()) {
                        crearNotif(
                            uid = tecnicoUid,
                            titulo = "Reforma completada",
                            detalle = "$nombreCliente ha marcado la reforma como completada.",
                            tipo = "reforma"
                        )
                    }
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error completando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun puedeValorar(tecnicoId: String, callback: (Boolean) -> Unit) {
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.any { doc ->
                    doc.getString("tecnicoId") == tecnicoId && doc.getString("estado") == "Completado"
                })
            }
            .addOnFailureListener { callback(false) }
    }

    private fun crearNotif(uid: String, titulo: String, detalle: String, tipo: String) {
        val ref = db.collection("notificaciones").document()
        ref.set(
            hashMapOf(
                "id" to ref.id, "uid" to uid,
                "titulo" to titulo, "detalle" to detalle,
                "fecha" to System.currentTimeMillis(),
                "leida" to false, "tipo" to tipo
            )
        )
    }
}
