package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Siembra datos de prueba realistas en Firestore usando exclusivamente
 * los usuarios que ya existen en la coleccion /usuarios.
 *
 * Solo debe ejecutarse desde el panel de administracion.
 */
class RepositorioSeed {

    private val db = FirebaseFirestore.getInstance()

    // ── Plantillas de viviendas ──
    private data class ViviendaPlantilla(
        val nombre: String, val superficie: Int, val anio: Int,
        val ventanas: String, val aislamiento: String, val calefaccion: String,
        val acs: String, val direccion: String, val orientacion: String
    )

    private val plantillasViviendas = listOf(
        ViviendaPlantilla("Piso en el centro", 78, 1990, "Doble acristalamiento", "Aislamiento parcial", "Caldera de gas", "Gas", "Calle Gran Via 45, 3B", "Sur"),
        ViviendaPlantilla("Casa unifamiliar", 165, 1978, "Vidrio simple", "Sin aislamiento", "Caldera de gas", "Gas", "Av. de la Constitucion 12", "Norte"),
        ViviendaPlantilla("Atico reformado", 62, 2018, "Triple", "Aislamiento completo", "Aerotermia", "Aerotermia", "Calle Serrano 88, Atico", "Suroeste"),
        ViviendaPlantilla("Duplex en urbanizacion", 120, 2005, "Doble acristalamiento", "Aislamiento completo", "Aerotermia", "Solar termica", "Urb. Los Pinos 7", "Este"),
        ViviendaPlantilla("Piso barrio historico", 95, 1965, "Vidrio simple", "Sin aislamiento", "Electrica", "Electrico", "Plaza Mayor 3, 2A", "Oeste"),
        ViviendaPlantilla("Chalet adosado", 140, 2000, "Doble acristalamiento", "Aislamiento parcial", "Biomasa", "Solar termica", "Calle del Roble 15", "Sureste")
    )

    // ── Conversaciones de chat ──
    private data class MsgPlantilla(val deCliente: Boolean, val texto: String, val tipo: String = "texto", val mediaUrl: String = "")

    private val conversaciones = listOf(
        listOf(
            MsgPlantilla(true, "Hola, he visto su perfil y me gustaria consultar sobre la instalacion de paneles solares en mi vivienda."),
            MsgPlantilla(false, "Hola! Encantado de ayudarle. Podria indicarme la superficie disponible en su tejado y la orientacion?"),
            MsgPlantilla(true, "Tengo unos 40m2 de tejado orientado al sur, sin sombras. La casa es de 1990."),
            MsgPlantilla(true, "Le envio una foto del tejado", "imagen", "https://picsum.photos/seed/tejado1/800/600"),
            MsgPlantilla(false, "Perfecto, con esa orientacion y superficie podriamos instalar unos 10-12 paneles. Le preparo un presupuesto detallado."),
            MsgPlantilla(true, "Genial, cuanto tiempo llevaria la instalacion?"),
            MsgPlantilla(false, "Normalmente entre 2 y 3 dias laborables. Le envio el presupuesto esta semana."),
            MsgPlantilla(true, "Estupendo, quedo a la espera. Muchas gracias!")
        ),
        listOf(
            MsgPlantilla(true, "Buenos dias, necesito mejorar el aislamiento de mi piso. Las facturas de calefaccion son muy altas."),
            MsgPlantilla(false, "Buenos dias! Que tipo de aislamiento tiene actualmente? Sabe si las paredes tienen camara de aire?"),
            MsgPlantilla(true, "No tiene ningun aislamiento, es un piso de los anos 60. Las paredes son de ladrillo macizo."),
            MsgPlantilla(false, "Entendido. En ese caso la mejor opcion seria un SATE (aislamiento por el exterior) o insuflado si hay camara."),
            MsgPlantilla(false, "Le adjunto un ejemplo de un trabajo similar que hicimos el mes pasado", "imagen", "https://picsum.photos/seed/sate1/800/600"),
            MsgPlantilla(true, "Se ve muy bien! Podria pasarse a ver el piso y darme un presupuesto?"),
            MsgPlantilla(false, "Por supuesto. Que tal el jueves por la manana? Necesitaria unos 30 minutos para medir y valorar."),
            MsgPlantilla(true, "El jueves me viene perfecto. Le paso la direccion por aqui."),
            MsgPlantilla(true, "Calle Gran Via 45, 3B. El portero le abrira."),
            MsgPlantilla(false, "Apuntado! Nos vemos el jueves a las 10h. Un saludo.")
        ),
        listOf(
            MsgPlantilla(true, "Hola, estoy interesado en cambiar mi caldera de gas antigua por un sistema de aerotermia."),
            MsgPlantilla(false, "Buenas tardes! La aerotermia es una excelente eleccion. Que superficie tiene la vivienda?"),
            MsgPlantilla(true, "Unos 120m2, duplex con suelo radiante en la planta baja."),
            MsgPlantilla(false, "Genial, el suelo radiante es ideal para aerotermia porque trabaja a baja temperatura. La eficiencia sera muy alta."),
            MsgPlantilla(true, "Cuanto costaria aproximadamente?"),
            MsgPlantilla(false, "Para esa superficie, con una unidad de 8-10kW, estariamos entre 8.000 y 12.000 euros incluyendo instalacion. Hay subvenciones del plan MOVES que cubren hasta el 40%."),
            MsgPlantilla(true, "Interesante lo de las subvenciones. Me ayudarian con el papeleo?"),
            MsgPlantilla(false, "Por supuesto, nos encargamos de toda la gestion de subvenciones sin coste adicional.")
        )
    )

    // ── Plantillas de solicitudes ──
    private data class SolicitudPlantilla(
        val descripcion: String, val estado: String, val precio: Double,
        val respuesta: String, val pagado: Boolean
    )

    private val plantillasSolicitudes = listOf(
        SolicitudPlantilla("Quiero instalar paneles solares fotovoltaicos en el tejado. Orientacion sur, aproximadamente 40m2 disponibles sin sombras.", "Pendiente", 0.0, "", false),
        SolicitudPlantilla("Me gustaria mejorar el aislamiento de la fachada y cambiar las ventanas por doble acristalamiento con rotura de puente termico.", "Presupuestado", 4500.0, "Incluye aislamiento SATE de 8cm + 6 ventanas de aluminio RPT con doble vidrio bajo emisivo.", false),
        SolicitudPlantilla("Quiero sustituir la caldera de gas por un sistema de aerotermia. Actualmente tengo radiadores de agua caliente.", "Aceptado", 8950.0, "Incluye unidad exterior e interior Mitsubishi Ecodan 8 kW, adaptacion del circuito hidraulico existente y puesta en marcha.", false),
        SolicitudPlantilla("Necesito instalar suelo radiante en toda la planta baja de mi chalet (80m2).", "Completado", 6200.0, "Instalacion completa de suelo radiante con colectores, tubo PEX y regulacion por zonas.", true),
        SolicitudPlantilla("Instalacion de sistema de ventilacion mecanica con recuperador de calor para piso de 95m2.", "Rechazado", 3800.0, "Sistema VMC doble flujo Zehnder ComfoAir Q350 con conductos y rejillas.", false),
        SolicitudPlantilla("Quiero poner un sistema solar termico para ACS en mi vivienda unifamiliar.", "EnCurso", 3200.0, "2 captadores solares planos + deposito de 200L + grupo hidraulico y centralita.", false)
    )

    // ── Plantillas de resenas ──
    private data class ResenaPlantilla(val puntuacion: Int, val comentario: String)

    private val plantillasResenas = listOf(
        ResenaPlantilla(5, "Excelente trabajo, muy profesional y puntual. La instalacion quedo perfecta y el ahorro en la factura se noto desde el primer mes."),
        ResenaPlantilla(4, "Buen servicio en general. El trabajo se completo a tiempo y la calidad es buena. Solo un pequeno retraso en la entrega del material."),
        ResenaPlantilla(5, "Increible la diferencia tras el aislamiento. La casa mantiene la temperatura mucho mejor y el ruido exterior practicamente ha desaparecido."),
        ResenaPlantilla(3, "El resultado final es correcto pero la comunicacion durante la obra podria mejorar. Hubo un par de imprevistos que no se avisaron a tiempo."),
        ResenaPlantilla(5, "De los mejores tecnicos con los que he trabajado. Explico todo el proceso con detalle, cumplio plazos y presupuesto. Muy recomendable."),
        ResenaPlantilla(4, "Muy contento con la aerotermia. El consumo electrico ha bajado bastante comparado con la caldera de gas. Buena asesoria tecnica."),
        ResenaPlantilla(5, "Gestion impecable de las subvenciones. Nos tramitaron todo el plan MOVES y nos ahorramos un 40%. Gran profesional."),
        ResenaPlantilla(4, "Trabajo limpio y ordenado. Dejaron la zona de obra perfecta al terminar. El unico pero es que tardaron un dia mas de lo previsto.")
    )

    // ── Plantillas de proyectos ──
    private data class ProyectoPlantilla(
        val titulo: String, val descripcion: String, val estado: String,
        val progreso: Int, val tareas: List<Pair<String, Boolean>>
    )

    private val plantillasProyectos = listOf(
        ProyectoPlantilla("Instalacion fotovoltaica 5kW", "Instalacion de paneles solares en cubierta con inversor hibrido y bateria de litio para autoconsumo.", "En curso", 60, listOf(
            "Estudio de viabilidad y diseno" to true,
            "Tramitacion de licencias" to true,
            "Montaje de estructura en cubierta" to true,
            "Instalacion de paneles y cableado" to false,
            "Conexion inversor y bateria" to false,
            "Legalizacion y alta en IDAE" to false
        )),
        ProyectoPlantilla("Cambio de caldera a aerotermia", "Sustitucion de caldera de gas por sistema aerotermia aire-agua para calefaccion y ACS.", "Finalizado", 100, listOf(
            "Retirada de caldera antigua" to true,
            "Instalacion unidad exterior" to true,
            "Conexion circuito hidraulico" to true,
            "Programacion y puesta en marcha" to true,
            "Tramitacion subvencion MOVES" to true
        )),
        ProyectoPlantilla("Aislamiento SATE fachada norte", "Aislamiento termico por el exterior con sistema SATE de 10cm de EPS grafitado.", "Pendiente", 0, listOf(
            "Andamiaje y protecciones" to false,
            "Preparacion de superficie" to false,
            "Colocacion de paneles EPS" to false,
            "Malla de refuerzo y mortero" to false,
            "Acabado final y pintura" to false,
            "Retirada de andamios" to false
        )),
        ProyectoPlantilla("Suelo radiante planta baja", "Instalacion de suelo radiante hidraulico en salon, cocina y pasillo (80m2).", "En curso", 40, listOf(
            "Levantamiento del suelo existente" to true,
            "Instalacion de aislante y lamina" to true,
            "Colocacion de tubo PEX" to false,
            "Conexion a colectores" to false,
            "Vertido de mortero autonivelante" to false,
            "Colocacion de pavimento" to false,
            "Prueba de presion y puesta en marcha" to false
        ))
    )

    // ══════════════════════════════════════════════════════════════
    //  Ejecucion del seed
    // ══════════════════════════════════════════════════════════════

    fun ejecutarSeed(onProgreso: (String) -> Unit, onFin: (Result<String>) -> Unit) {
        onProgreso("Cargando usuarios existentes...")

        db.collection("usuarios").get().addOnSuccessListener { snap ->
            val usuarios = snap.documents.mapNotNull { it.toObject(Usuario::class.java) }
            val propietarios = usuarios.filter { it.tipoUsuario == "Propietario" && !it.eliminado && !it.bloqueado }
            val tecnicos = usuarios.filter { it.tipoUsuario == "Tecnico" && !it.eliminado && !it.bloqueado }

            if (propietarios.isEmpty() || tecnicos.isEmpty()) {
                // Intentar tambien con tilde por si el tipo se guardo con acento
                val tecnicosTilde = usuarios.filter { it.tipoUsuario == "Técnico" && !it.eliminado && !it.bloqueado }
                if (propietarios.isEmpty() || tecnicosTilde.isEmpty()) {
                    onFin(Result.failure(Exception("Se necesita al menos 1 propietario y 1 tecnico activos (hay ${propietarios.size} prop. y ${tecnicos.size + tecnicosTilde.size} tec.)")))
                    return@addOnSuccessListener
                }
                ejecutarPasos(propietarios, tecnicosTilde, onProgreso, onFin)
                return@addOnSuccessListener
            }
            ejecutarPasos(propietarios, tecnicos, onProgreso, onFin)
        }.addOnFailureListener {
            onFin(Result.failure(Exception("Error cargando usuarios: ${it.message}")))
        }
    }

    private fun ejecutarPasos(
        propietarios: List<Usuario>,
        tecnicos: List<Usuario>,
        onProgreso: (String) -> Unit,
        onFin: (Result<String>) -> Unit
    ) {
        var stats = ""
        val totalProp = propietarios.size

        // 1. Viviendas
        onProgreso("Creando viviendas para $totalProp propietarios...")
        sembrarViviendas(propietarios) { viviendas, nViv ->
            stats += "$nViv viviendas, "

            // 2. Informes
            onProgreso("Generando informes energeticos...")
            sembrarInformes(viviendas) { nInf ->
                stats += "$nInf informes, "

                // 3. Chats y mensajes
                onProgreso("Creando conversaciones...")
                sembrarChats(propietarios, tecnicos) { nChats, nMsgs ->
                    stats += "$nChats chats ($nMsgs mensajes), "

                    // 4. Proyectos
                    onProgreso("Creando proyectos...")
                    sembrarProyectos(propietarios, tecnicos, viviendas) { nProy ->
                        stats += "$nProy proyectos, "

                        // 5. Solicitudes
                        onProgreso("Creando solicitudes de presupuesto...")
                        sembrarSolicitudes(propietarios, tecnicos) { nSol ->
                            stats += "$nSol solicitudes, "

                            // 6. Resenas
                            onProgreso("Creando valoraciones...")
                            sembrarResenas(propietarios, tecnicos) { nRes ->
                                stats += "$nRes valoraciones"
                                onFin(Result.success("Seed completado: $stats"))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 1. Viviendas ──
    private fun sembrarViviendas(propietarios: List<Usuario>, onDone: (List<Vivienda>, Int) -> Unit) {
        val todasViviendas = mutableListOf<Vivienda>()
        val totalEsperadas = propietarios.size * 2
        val pendientes = AtomicInteger(totalEsperadas)
        if (propietarios.isEmpty()) { onDone(emptyList(), 0); return }

        propietarios.forEachIndexed { iProp, u ->
            repeat(2) { j ->
                val p = plantillasViviendas[(iProp * 2 + j) % plantillasViviendas.size]
                val ref = db.collection("viviendas").document()
                val vivienda = Vivienda(
                    id = ref.id,
                    uid = u.uid,
                    nombre = p.nombre,
                    superficie = p.superficie,
                    anioConstruccion = p.anio,
                    tipoVentanas = p.ventanas,
                    aislamiento = p.aislamiento,
                    calefaccion = p.calefaccion,
                    acs = p.acs,
                    direccion = p.direccion,
                    orientacion = p.orientacion,
                    fechaCreacion = System.currentTimeMillis() - (86400000L * (30 + j * 15))
                )
                synchronized(todasViviendas) { todasViviendas.add(vivienda) }
                ref.set(vivienda).addOnCompleteListener {
                    if (pendientes.decrementAndGet() == 0) onDone(todasViviendas, todasViviendas.size)
                }
            }
        }
    }

    // ── 4. Informes ──
    private fun sembrarInformes(viviendas: List<Vivienda>, onDone: (Int) -> Unit) {
        val pendientes = AtomicInteger(viviendas.size)
        if (viviendas.isEmpty()) { onDone(0); return }
        viviendas.forEachIndexed { i, v ->
            val resultado = AlgoritmoEnergetico.calcular(v)
            val ref = db.collection("informes").document()
            val informe = InformeEnergetico(
                id = ref.id,
                viviendaId = v.id,
                uid = v.uid,
                nombreVivienda = v.nombre,
                etiqueta = resultado.etiqueta,
                estadoEficiencia = resultado.estadoEficiencia,
                consumoEstimado = resultado.consumoEstimado,
                emisiones = resultado.emisiones,
                costeAnual = resultado.costeAnual,
                recomendaciones = resultado.recomendaciones,
                fechaGeneracion = System.currentTimeMillis() - (86400000L * (25 - i * 3))
            )
            ref.set(informe).addOnCompleteListener {
                if (pendientes.decrementAndGet() == 0) onDone(viviendas.size)
            }
        }
    }

    // ── 5. Chats y mensajes ──
    private fun sembrarChats(
        propietarios: List<Usuario>,
        tecnicos: List<Usuario>,
        onDone: (Int, Int) -> Unit
    ) {
        // Crear un chat entre cada propietario y cada tecnico (maximo 6 chats)
        val pares = mutableListOf<Pair<Usuario, Usuario>>()
        for (prop in propietarios) {
            for (tec in tecnicos) {
                pares.add(prop to tec)
                if (pares.size >= 6) break
            }
            if (pares.size >= 6) break
        }
        if (pares.isEmpty()) { onDone(0, 0); return }

        val totalMensajes = AtomicInteger(0)
        val pendientesChats = AtomicInteger(pares.size)

        pares.forEachIndexed { idx, (prop, tec) ->
            val conv = conversaciones[idx % conversaciones.size]
            val chatRef = db.collection("chats").document()
            val chat = Chat(
                id = chatRef.id,
                participantes = listOf(prop.uid, tec.uid),
                nombresParticipantes = mapOf(prop.uid to prop.nombre, tec.uid to tec.nombre),
                ultimoMensaje = conv.last().texto.take(80),
                fechaUltimoMensaje = System.currentTimeMillis() - (3600000L * idx),
                noLeidosPor = mapOf(prop.uid to 0, tec.uid to 1)
            )
            chatRef.set(chat).addOnSuccessListener {
                val pendientesMsgs = AtomicInteger(conv.size)
                conv.forEachIndexed { mIdx, msg ->
                    val emisorUid = if (msg.deCliente) prop.uid else tec.uid
                    val emisorNombre = if (msg.deCliente) prop.nombre else tec.nombre
                    val msgRef = chatRef.collection("mensajes").document()
                    val mensaje = MensajeChat(
                        id = msgRef.id,
                        chatId = chatRef.id,
                        emisorUid = emisorUid,
                        emisorNombre = emisorNombre,
                        texto = msg.texto,
                        fecha = System.currentTimeMillis() - (3600000L * (conv.size - mIdx) + 86400000L * idx),
                        leido = mIdx < conv.size - 1,
                        tipo = msg.tipo,
                        mediaUrl = msg.mediaUrl,
                        mediaNombre = if (msg.tipo == "imagen") "foto_${mIdx}.jpg" else "",
                        mediaBytes = if (msg.tipo == "imagen") 245000L else 0L
                    )
                    totalMensajes.incrementAndGet()
                    msgRef.set(mensaje).addOnCompleteListener {
                        if (pendientesMsgs.decrementAndGet() == 0) {
                            if (pendientesChats.decrementAndGet() == 0) {
                                onDone(pares.size, totalMensajes.get())
                            }
                        }
                    }
                }
            }.addOnFailureListener {
                if (pendientesChats.decrementAndGet() == 0) {
                    onDone(pares.size, totalMensajes.get())
                }
            }
        }
    }

    // ── 6. Proyectos ──
    private fun sembrarProyectos(
        propietarios: List<Usuario>,
        tecnicos: List<Usuario>,
        viviendas: List<Vivienda>,
        onDone: (Int) -> Unit
    ) {
        val maxProyectos = minOf(plantillasProyectos.size, propietarios.size * 2)
        val pendientes = AtomicInteger(maxProyectos)
        if (maxProyectos == 0) { onDone(0); return }

        repeat(maxProyectos) { i ->
            val prop = propietarios[i % propietarios.size]
            val tec = tecnicos[i % tecnicos.size]
            val viv = viviendas.firstOrNull { it.uid == prop.uid } ?: viviendas.first()
            val p = plantillasProyectos[i]
            val ref = db.collection("proyectos").document()
            val proyecto = Proyecto(
                id = ref.id,
                uid = prop.uid,
                titulo = p.titulo,
                descripcion = p.descripcion,
                viviendaNombre = viv.nombre,
                tecnicoId = tec.uid,
                tecnicoUid = tec.uid,
                tecnicoNombre = tec.nombre,
                progreso = p.progreso,
                estado = p.estado,
                tareas = p.tareas.map { Tarea(nombre = it.first, completada = it.second) },
                fechaCreacion = System.currentTimeMillis() - (86400000L * (60 - i * 10)),
                fechaFinEstimada = System.currentTimeMillis() + (86400000L * 30 * (i + 1))
            )
            ref.set(proyecto).addOnCompleteListener {
                if (pendientes.decrementAndGet() == 0) onDone(maxProyectos)
            }
        }
    }

    // ── 7. Solicitudes ──
    private fun sembrarSolicitudes(
        propietarios: List<Usuario>,
        tecnicos: List<Usuario>,
        onDone: (Int) -> Unit
    ) {
        val maxSolicitudes = minOf(plantillasSolicitudes.size, propietarios.size * tecnicos.size)
        val pendientes = AtomicInteger(maxSolicitudes)
        if (maxSolicitudes == 0) { onDone(0); return }

        repeat(maxSolicitudes) { i ->
            val prop = propietarios[i % propietarios.size]
            val tec = tecnicos[i % tecnicos.size]
            val p = plantillasSolicitudes[i]
            val ref = db.collection("solicitudes").document()
            val ahora = System.currentTimeMillis()
            val solicitud = SolicitudPresupuesto(
                id = ref.id,
                uidCliente = prop.uid,
                nombreCliente = prop.nombre,
                tecnicoId = tec.uid,
                tecnicoUid = tec.uid,
                tecnicoNombre = tec.nombre,
                descripcion = p.descripcion,
                estado = p.estado,
                precioPresupuesto = p.precio,
                respuestaTecnico = p.respuesta,
                fechaCreacion = ahora - (86400000L * (45 - i * 7)),
                fechaRespuesta = if (p.precio > 0) ahora - (86400000L * (40 - i * 7)) else 0L,
                pagado = p.pagado,
                fechaPago = if (p.pagado) ahora - (86400000L * 5) else 0L,
                metodoPago = if (p.pagado) "bizum" else "",
                fichaPrecioFinal = if (p.estado in listOf("EnCurso", "Completado")) p.precio * 1.05 else 0.0,
                fichaDescripcion = if (p.estado in listOf("EnCurso", "Completado")) p.respuesta else "",
                fichaFechaInicio = if (p.estado in listOf("EnCurso", "Completado")) ahora - (86400000L * 20) else 0L,
                fichaFechaFinEstimada = if (p.estado in listOf("EnCurso", "Completado")) ahora + (86400000L * 30) else 0L,
                fichaTareas = if (p.estado in listOf("EnCurso", "Completado")) listOf("Preparacion", "Instalacion", "Puesta en marcha", "Legalizacion") else emptyList()
            )
            ref.set(solicitud).addOnCompleteListener {
                if (pendientes.decrementAndGet() == 0) onDone(maxSolicitudes)
            }
        }
    }

    // ── 8. Resenas ──
    private fun sembrarResenas(
        propietarios: List<Usuario>,
        tecnicos: List<Usuario>,
        onDone: (Int) -> Unit
    ) {
        // Cada propietario valora a cada tecnico (maximo 8 resenas)
        val pares = mutableListOf<Pair<Usuario, Usuario>>()
        for (prop in propietarios) {
            for (tec in tecnicos) {
                pares.add(prop to tec)
                if (pares.size >= plantillasResenas.size) break
            }
            if (pares.size >= plantillasResenas.size) break
        }
        if (pares.isEmpty()) { onDone(0); return }

        val pendientes = AtomicInteger(pares.size)
        pares.forEachIndexed { i, (prop, tec) ->
            val p = plantillasResenas[i % plantillasResenas.size]
            val ref = db.collection("resenas").document()
            val resena = Resena(
                id = ref.id,
                tecnicoId = tec.uid,
                uid = prop.uid,
                nombreUsuario = prop.nombre,
                puntuacion = p.puntuacion,
                comentario = p.comentario,
                fecha = System.currentTimeMillis() - (86400000L * (30 - i * 3))
            )
            ref.set(resena).addOnCompleteListener {
                if (pendientes.decrementAndGet() == 0) onDone(pares.size)
            }
        }
    }
}
