package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tarea
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RepositorioTecnicos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    companion object {
        /** Máximo de solicitudes activas (no terminadas) que un cliente puede tener con un mismo técnico. */
        const val MAX_SOLICITUDES_ACTIVAS = 5

        /**
         * Ventana de deduplicación: dos solicitudes idénticas (mismo cliente, técnico y
         * descripción) creadas dentro de este margen se consideran el MISMO envío disparado
         * dos veces, no dos solicitudes distintas. 60 s es de sobra: nadie manda dos
         * presupuestos legítimos al mismo técnico en menos de un minuto.
         */
        const val VENTANA_DEDUP_MS = 60_000L

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
        db.collection("tecnicos").getOrTimeout { snap ->
            if (snap == null) { callback(emptyList()); return@getOrTimeout }
            val tecnicos = snap.documents.mapNotNull { doc ->
                doc.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }
            }
            db.collection("resenas").getOrTimeout { resenasSnap ->
                if (resenasSnap == null) { callback(tecnicos); return@getOrTimeout }
                val porTecnico = resenasSnap.documents
                    .mapNotNull { it.toObject(Resena::class.java) }
                    .groupBy { it.tecnicoId }
                callback(tecnicos.map { t ->
                    val lista = porTecnico[t.id].orEmpty()
                    if (lista.isEmpty()) t.copy(rating = 0.0, opiniones = 0)
                    else t.copy(
                        rating = Math.round(lista.map { it.puntuacion }.average() * 10.0) / 10.0,
                        opiniones = lista.size
                    )
                })
            }
        }
    }

    /**
     * Tiempo real: directorio completo de técnicos. El callback se dispara con la
     * caché al instante y luego en cada alta/baja/modificación. Necesario para que
     * un técnico recién creado aparezca de inmediato en el listado del cliente
     * (sin reabrir la app). El recálculo de rating con las reseñas vive en una
     * segunda suscripción para que ambos lados se mantengan en vivo.
     */
    fun escucharTecnicos(callback: (List<Tecnico>) -> Unit): Pair<ListenerRegistration, ListenerRegistration> {
        var tecnicosBase: List<Tecnico> = emptyList()
        var resenasPorTecnico: Map<String, List<Resena>> = emptyMap()

        // El contador de proyectos completados vive YA en el doc del técnico:
        // arranca con el histórico sembrado (trabajos previos al tracking) y se
        // incrementa con FieldValue.increment(1) cuando un proyecto pasa a
        // "Finalizado" (ver tecnicoConfirmaPago). No hace falta cross-query a
        // /proyectos, que las reglas de Firestore bloquean entre clientes.
        fun emitir() {
            callback(tecnicosBase.map { t ->
                val lista = resenasPorTecnico[t.id].orEmpty()
                if (lista.isEmpty()) t.copy(rating = 0.0, opiniones = 0)
                else t.copy(
                    rating = Math.round(lista.map { it.puntuacion }.average() * 10.0) / 10.0,
                    opiniones = lista.size
                )
            })
        }

        val regTec = db.collection("tecnicos").addSnapshotListener { snap, err ->
            if (err != null) {
                // Loguea para verlo en logcat y emite vacío; el VM detectará uid stale
                // en la próxima invocación y reenganchará con el auth correcto.
                android.util.Log.w("RepoTecnicos", "Listener técnicos cancelado: ${err.message}")
                tecnicosBase = emptyList(); emitir(); return@addSnapshotListener
            }
            tecnicosBase = snap?.documents?.mapNotNull { doc ->
                doc.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }
            } ?: emptyList()
            emitir()
        }
        val regRes = db.collection("resenas").addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.w("RepoTecnicos", "Listener reseñas cancelado: ${err.message}")
                resenasPorTecnico = emptyMap(); emitir(); return@addSnapshotListener
            }
            resenasPorTecnico = snap?.documents
                ?.mapNotNull { it.toObject(Resena::class.java) }
                ?.groupBy { it.tecnicoId } ?: emptyMap()
            emitir()
        }
        return regTec to regRes
    }

    fun obtenerTecnico(id: String, callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos").document(id).get()
            .addOnSuccessListener { doc ->
                val t = doc.toObject(Tecnico::class.java)
                callback(if (t != null && t.id.isBlank()) t.copy(id = doc.id) else t)
            }
            .addOnFailureListener { callback(null) }
    }

    fun obtenerRanking(callback: (List<Tecnico>) -> Unit) = obtenerTecnicos(callback)

    fun solicitarPresupuesto(solicitud: SolicitudPresupuesto, callback: (Result<Unit>) -> Unit) {
        val clienteUid = uid()
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", clienteUid)
            .whereEqualTo("tecnicoId", solicitud.tecnicoId)
            .get()
            .addOnSuccessListener { snap ->
                val ahora = System.currentTimeMillis()

                // 1) ANTI-DUPLICADO: si ya existe una solicitud "Pendiente" con la misma
                //    descripción creada hace < VENTANA_DEDUP_MS, es el mismo envío disparado
                //    dos veces (doble tap, recomposición, reintento, doble callback…). No
                //    creamos otra: devolvemos éxito como si la primera fuera la buena.
                val duplicadoReciente = snap.documents.any { doc ->
                    val fc = doc.getLong("fechaCreacion") ?: 0L
                    doc.getString("estado") == "Pendiente" &&
                        doc.getString("descripcion") == solicitud.descripcion &&
                        (ahora - fc) in 0..VENTANA_DEDUP_MS
                }
                if (duplicadoReciente) {
                    callback(Result.success(Unit))
                    return@addOnSuccessListener
                }

                // 2) TOPE de 5 solicitudes ACTIVAS por técnico (las terminadas/rechazadas
                //    no cuentan, así el historial no llena el cupo).
                val activas = snap.documents.count { doc ->
                    doc.getString("estado") !in listOf("Completado", "Rechazado")
                }
                if (activas >= MAX_SOLICITUDES_ACTIVAS) {
                    callback(Result.failure(Exception("Has alcanzado el máximo de $MAX_SOLICITUDES_ACTIVAS solicitudes activas con este técnico")))
                    return@addOnSuccessListener
                }

                val ref = db.collection("solicitudes").document()
                val s = solicitud.copy(id = ref.id, uidCliente = clienteUid)
                ref.set(s)
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error enviando solicitud")))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error verificando solicitudes")))
            }
    }

    /** Devuelve el perfil de técnico vinculado al uid de Auth actual. */
    fun obtenerMiPerfilTecnico(callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos")
            .whereEqualTo("uid", uid())
            .limit(1)
            .getOrTimeout { snap ->
                val doc = snap?.documents?.firstOrNull()
                val tec = doc?.toObject(Tecnico::class.java)
                callback(if (tec != null && tec.id.isBlank()) tec.copy(id = doc.id) else tec)
            }
    }

    fun actualizarPerfilTecnico(
        tecnicoId: String,
        nombre: String,
        ciudad: String,
        descripcion: String,
        telefono: String,
        emailContacto: String,
        especialidades: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        val coords = coordenadasDeCiudad(ciudad)
        val datos = mutableMapOf<String, Any>(
            "nombre" to nombre,
            "ciudad" to ciudad,
            "descripcion" to descripcion,
            "telefono" to telefono,
            "emailContacto" to emailContacto,
            "especialidades" to especialidades
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
            .getOrTimeout { snap ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                    ?.sortedByDescending { it.fechaCreacion } ?: emptyList())
            }
    }

    fun obtenerMisSolicitudes(callback: (List<SolicitudPresupuesto>) -> Unit) {
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .getOrTimeout { snap ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                    ?.sortedByDescending { it.fechaCreacion } ?: emptyList())
            }
    }

    /** Tiempo real: solicitudes que YO (cliente) he enviado. El callback se dispara
     *  al instante con la caché y luego en cada cambio (nueva, estado actualizado…). */
    fun escucharMisSolicitudes(callback: (List<SolicitudPresupuesto>) -> Unit): ListenerRegistration {
        return db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .addSnapshotListener { snap, _ ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                    ?.sortedByDescending { it.fechaCreacion } ?: emptyList())
            }
    }

    /** Tiempo real: solicitudes recibidas por MÍ (técnico). */
    fun escucharSolicitudesRecibidas(callback: (List<SolicitudPresupuesto>) -> Unit): ListenerRegistration {
        return db.collection("solicitudes")
            .whereEqualTo("tecnicoUid", uid())
            .addSnapshotListener { snap, _ ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                    ?.sortedByDescending { it.fechaCreacion } ?: emptyList())
            }
    }

    fun responderPresupuesto(
        solicitudId: String, precio: Double, respuesta: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            if (doc.getString("estado") != "Pendiente") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update(
                mapOf(
                    "estado" to "Presupuestado",
                    "precioPresupuesto" to precio,
                    "respuestaTecnico" to respuesta,
                    "fechaRespuesta" to System.currentTimeMillis()
                )
            ).addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error respondiendo"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun aceptarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            if (doc.getString("estado") != "Presupuestado") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update("estado", "Aceptado")
                .addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error aceptando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun rechazarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            if (doc.getString("estado") != "Presupuestado") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update("estado", "Rechazado")
                .addOnSuccessListener { callback(Result.success(Unit)) }
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
            val estadoActual = doc.getString("estado")
            if (estadoActual != "Aceptado" && estadoActual != "FichaRechazada") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update(
                mapOf(
                    "estado" to "FichaEnviada",
                    "fichaFechaInicio" to fechaInicio,
                    "fichaFechaFinEstimada" to fechaFinEstimada,
                    "fichaDescripcion" to descripcionFicha,
                    "fichaPrecioFinal" to precioFinal,
                    "fichaTareas" to tareas
                )
            ).addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error enviando ficha"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /** CLIENTE — acepta la ficha de inicio. Crea automáticamente el documento /proyectos. */
    fun aceptarFichaYCrearProyecto(solicitudId: String, callback: (Result<String>) -> Unit) {
        val refSol = db.collection("solicitudes").document(solicitudId)
        refSol.get().addOnSuccessListener { doc ->
            val sol = doc.toObject(SolicitudPresupuesto::class.java)
            if (sol == null) { callback(Result.failure(Exception("Solicitud no encontrada"))); return@addOnSuccessListener }
            if (sol.estado != "FichaEnviada") { callback(Result.success(sol.proyectoId)); return@addOnSuccessListener }

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
                    // La notif al técnico la genera `on_solicitud_estado_cambiado` (plantilla "EnCurso").
                    refSol.update(
                        mapOf("estado" to "EnCurso", "proyectoId" to refProy.id)
                    ).addOnSuccessListener { callback(Result.success(refProy.id)) }
                        .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error actualizando solicitud"))) }
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error creando proyecto"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /**
     * CLIENTE — rechaza la ficha de inicio. Pasa a "FichaRechazada" (estado propio,
     * no "Aceptado") para que la Cloud Function `on_solicitud_estado_cambiado` no
     * dispare la plantilla "Aceptado" (que enviaría una notif errónea
     * "Presupuesto aceptado" al técnico, duplicando la notif manual de rechazo).
     */
    fun rechazarFicha(solicitudId: String, motivo: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            if (doc.getString("estado") != "FichaEnviada") { callback(Result.success(Unit)); return@addOnSuccessListener }
            val tecnicoUid = doc.getString("tecnicoUid") ?: ""
            val nombreCliente = doc.getString("nombreCliente") ?: "Cliente"
            ref.update("estado", "FichaRechazada")
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
            if (doc.getString("estado") != "EnCurso") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update("estado", "PendientePago")
                .addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /**
     * CLIENTE — declara que ya ha pagado al técnico (tarjeta simulada o efectivo).
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
            if (doc.getString("estado") != "PendientePago") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update(
                mapOf(
                    "estado" to "PagoEnVerificacion",
                    "metodoPago" to metodo,
                    "referenciaPago" to referencia,
                    "fechaPagoCliente" to System.currentTimeMillis()
                )
            ).addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
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
            if (sol.estado != "PagoEnVerificacion") { callback(Result.success(Unit)); return@addOnSuccessListener }
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
                // Contador denormalizado en /tecnicos. Las reglas no permiten
                // a clientes leer /proyectos de otros usuarios, así que un cross-
                // query para contar finalizados fallaría con PERMISSION_DENIED.
                // Mantenemos el campo aquí y lo subimos en el momento exacto en
                // que un proyecto se cierra (técnico confirma cobro).
                if (sol.tecnicoId.isNotEmpty()) {
                    db.collection("tecnicos").document(sol.tecnicoId)
                        .update("proyectosCompletados", FieldValue.increment(1))
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
                callback(Result.success(Unit))
            }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /**
     * CLIENTE — rechaza el pago previamente declarado (por error o no quiere ya).
     * Vuelve a PendientePago.
     */
    fun cancelarMarcaPago(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            if (doc.getString("estado") != "PagoEnVerificacion") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update(
                mapOf(
                    "estado" to "PendientePago",
                    "metodoPago" to "",
                    "referenciaPago" to "",
                    "fechaPagoCliente" to 0L
                )
            )
                .addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun completarSolicitud(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            if (doc.getString("estado") == "Completado") { callback(Result.success(Unit)); return@addOnSuccessListener }
            ref.update("estado", "Completado")
                .addOnSuccessListener { callback(Result.success(Unit)) }
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

    fun contarSolicitudesCompletadas(tecnicoId: String, callback: (Int) -> Unit) {
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.count { doc ->
                    doc.getString("tecnicoId") == tecnicoId && doc.getString("estado") == "Completado"
                })
            }
            .addOnFailureListener { callback(0) }
    }

    /**
     * CLIENTE — cancela y elimina una solicitud propia en estado "Pendiente" o "Presupuestado".
     * El documento se borra de Firestore; el técnico deja de verla.
     */
    fun cancelarSolicitud(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("solicitudes").document(solicitudId)
            .delete()
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error cancelando solicitud")))
            }
    }

    private fun crearNotif(uid: String, titulo: String, detalle: String, tipo: String) =
        RepositorioNotificaciones.crearRapida(uid, titulo, detalle, tipo)
}
