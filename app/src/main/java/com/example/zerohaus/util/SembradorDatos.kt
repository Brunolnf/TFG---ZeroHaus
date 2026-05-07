package com.example.zerohaus.util

import android.content.Context
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.Tarea
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object SembradorDatos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ─────────────────────────────────────────
    // CREAR CUENTAS DE TÉCNICOS DEMO
    // ─────────────────────────────────────────
    data class TecnicoInfo(val docId: String, val nombre: String, val email: String, val chatId: String?)

    // Los 8 técnicos finales — emails directamente relacionados con su nombre.
    val TECNICOS_INFO = listOf(
        TecnicoInfo("demo-tecnico-ana-002",    "Ana Martínez",    "ana.martinez@zerohaus.app",    "demo-chat-ana-002"),
        TecnicoInfo("demo-tecnico-sofia-010",  "Sofía Hernández", "sofia.hernandez@zerohaus.app", null),
        TecnicoInfo("demo-tecnico-carlos-001", "Carlos García",   "carlos.garcia@zerohaus.app",   "demo-chat-carlos-001"),
        TecnicoInfo("demo-tecnico-pablo-007",  "Pablo Jiménez",   "pablo.jimenez@zerohaus.app",   null),
        TecnicoInfo("demo-tecnico-victor-013", "Víctor Aguirre",  "victor.aguirre@zerohaus.app",  null),
        TecnicoInfo("demo-tecnico-laura-004",  "Laura Sánchez",   "laura.sanchez@zerohaus.app",   null),
        TecnicoInfo("demo-tecnico-lucia-012",  "Lucía Peña",      "lucia.pena@zerohaus.app",      null),
        TecnicoInfo("demo-tecnico-miguel-003", "Miguel Torres",   "miguel.torres@zerohaus.app",   null)
    )

    // IDs de los 7 que tienen que sobrevivir tras el reset
    private val TECNICOS_IDS_VIVOS = TECNICOS_INFO.map { it.docId }.toSet()

    // IDs de TODOS los técnicos demo que el sembrador haya podido crear alguna vez
    // (incluye los que ya no queremos y los actuales). Se usa para borrar antes de sembrar de nuevo.
    private val TECNICO_IDS_DEMO_HISTORICO = listOf(
        "demo-tecnico-carlos-001",   "demo-tecnico-ana-002",
        "demo-tecnico-miguel-003",   "demo-tecnico-laura-004",
        "demo-tecnico-david-005",    "demo-tecnico-elena-006",
        "demo-tecnico-pablo-007",    "demo-tecnico-marta-008",
        "demo-tecnico-javier-009",   "demo-tecnico-sofia-010",
        "demo-tecnico-andres-011",   "demo-tecnico-lucia-012",
        "demo-tecnico-victor-013",   "demo-tecnico-cristina-014"
    )

    // Emails ANTIGUOS (de versiones previas del sembrador) que pueden tener cuentas Auth
    // huérfanas. Se intentan eliminar al hacer el reset para no dejar basura.
    private val EMAILS_LEGACY = listOf(
        "carlos@energiazero.es", "ana@solartech.es",
        "miguel@climaeficiente.es", "laura@auditoriasenergeticas.es",
        "david@rehab360.es", "elena@biomasaragon.es",
        "pablo@solarcostadelsol.es", "marta@smartenergy.es",
        "javier@geomur.es", "sofia@solalicante.es",
        "andres@vencoru.es", "lucia@aerogalicia.es",
        "victor@passivnavarra.es", "cristina@solarbalear.es"
    )
    const val PASSWORD_TECNICOS = "Tecnico2024!"

    data class CredencialTecnico(
        val nombre: String,
        val email: String,
        val password: String,
        val ok: Boolean,
        val motivo: String = ""
    )

    fun sembrarUsuariosTecnicos(context: Context, onDone: (ok: Boolean, mensaje: String) -> Unit) {
        // Mantengo este nombre para compatibilidad con el botón gris antiguo.
        // Internamente delega en el flujo de reset completo.
        resetearTecnicosCompleto(context) { creds ->
            val ok = creds.all { it.ok }
            onDone(ok, if (ok) "OK" else creds.filter { !it.ok }.joinToString("; ") { "${it.nombre}: ${it.motivo}" })
        }
    }

    // RESET COMPLETO de los 7 técnicos demo. Pasos en orden:
    //   1. Limpiar cuentas Auth + docs de versiones legacy (energiazero.es, solartech.es, etc.)
    //   2. Borrar /tecnicos/{docId} de los 14 IDs demo (incluye los 7 actuales — se sobrescribirán)
    //   3. Sembrar los 7 docs /tecnicos nuevos con uid = miUid (placeholder)
    //   4. Crear cuentas Auth con los nuevos emails (nombre.apellido@zerohaus.app)
    //   5. Vincular tecnico.uid = uid real de Auth, crear /usuarios/{uid}
    // Devuelve la lista de credenciales finales (con email + password + estado ok/error).
    fun resetearTecnicosCompleto(
        context: Context,
        onDone: (creds: List<CredencialTecnico>) -> Unit
    ) {
        val miUid = auth.currentUser?.uid ?: run { onDone(emptyList()); return }

        limpiarCuentasLegacy(context) {
            // Tras limpiar legacy, borrar también los demo docs (incluso los actuales)
            // para evitar problemas de permisos al sobrescribir.
            borrarTecnicosDemoTodos {
                sembrarTecnicos(miUid) { ok ->
                    if (!ok) { onDone(emptyList()); return@sembrarTecnicos }
                    crearCuentasAuthConCredenciales(context, miUid, onDone)
                }
            }
        }
    }

    /**
     * Para cada email legacy: si existe cuenta Auth con la pwd común, sign-in, borra su
     * tecnico-doc y su usuario-doc, y borra la cuenta Auth (user.delete()).
     * Si no hay cuenta o la pwd no coincide, simplemente se ignora.
     */
    private fun limpiarCuentasLegacy(context: Context, onDone: () -> Unit) {
        if (EMAILS_LEGACY.isEmpty()) { onDone(); return }
        val opciones = FirebaseApp.getInstance().options
        var pendientes = EMAILS_LEGACY.size

        EMAILS_LEGACY.forEach { email ->
            val appName = "legacy_${email.hashCode()}"
            val appSec = try {
                FirebaseApp.initializeApp(context, opciones, appName)
            } catch (_: Exception) {
                try { FirebaseApp.getInstance(appName) } catch (_: Exception) { null }
            } ?: run { if (--pendientes == 0) onDone(); return@forEach }

            val authTemp = FirebaseAuth.getInstance(appSec)
            val dbTemp = FirebaseFirestore.getInstance(appSec)

            authTemp.signInWithEmailAndPassword(email, PASSWORD_TECNICOS)
                .addOnSuccessListener { res ->
                    val tecUid = res.user?.uid
                    if (tecUid == null) {
                        authTemp.signOut(); if (--pendientes == 0) onDone(); return@addOnSuccessListener
                    }
                    // Borrar /usuarios/{uid} primero (el tech se borra a sí mismo)
                    dbTemp.collection("usuarios").document(tecUid).delete()
                        .addOnCompleteListener {
                            // Buscar y borrar los tecnico docs vinculados a este uid
                            dbTemp.collection("tecnicos").whereEqualTo("uid", tecUid).get()
                                .addOnCompleteListener { snap ->
                                    val docs = snap.result?.documents ?: emptyList()
                                    val borrarDoc = if (docs.isEmpty()) {
                                        com.google.android.gms.tasks.Tasks.forResult(null)
                                    } else {
                                        com.google.android.gms.tasks.Tasks.whenAll(
                                            docs.map { it.reference.delete() }
                                        )
                                    }
                                    borrarDoc.addOnCompleteListener {
                                        // Finalmente borrar la cuenta Auth
                                        res.user?.delete()?.addOnCompleteListener {
                                            authTemp.signOut()
                                            if (--pendientes == 0) onDone()
                                        } ?: run {
                                            authTemp.signOut()
                                            if (--pendientes == 0) onDone()
                                        }
                                    }
                                }
                        }
                }
                .addOnFailureListener {
                    // No existe la cuenta legacy con esa pwd — nada que limpiar
                    if (--pendientes == 0) onDone()
                }
        }
    }

    /**
     * Borra los 14 docs demo de /tecnicos. Algunos pueden tener uid distinto al cliente
     * (porque la versión anterior los enlazó a un Auth uid). En ese caso el delete fallará
     * con permission-denied, pero seguimos adelante — sembrarTecnicos los sobrescribirá si
     * el doc ya no existe; si existe con uid=otro, la sobrescritura fallará y ese técnico
     * concreto quedará con datos viejos hasta el siguiente reset.
     */
    data class ResultadoBorrado(val total: Int, val borrados: Int, val fallidos: List<String>)

    /**
     * Borra de /tecnicos todos los documentos cuyo `rating` sea 0.0
     * (también incluye los que no tengan campo rating o sea null).
     * Si el cliente no tiene permiso (uid ajeno), se reporta como fallido.
     */
    fun borrarTecnicosSinValoracion(onDone: (ResultadoBorrado) -> Unit) {
        db.collection("tecnicos").get()
            .addOnSuccessListener { snap ->
                val sinValoracion = snap.documents.filter {
                    (it.getDouble("rating") ?: 0.0) == 0.0
                }
                if (sinValoracion.isEmpty()) {
                    onDone(ResultadoBorrado(0, 0, emptyList())); return@addOnSuccessListener
                }

                var pendientes = sinValoracion.size
                var borrados = 0
                val fallidos = mutableListOf<String>()

                sinValoracion.forEach { doc ->
                    val nombre = doc.getString("nombre") ?: doc.id
                    doc.reference.delete()
                        .addOnSuccessListener { borrados++ }
                        .addOnFailureListener {
                            synchronized(fallidos) { fallidos.add(nombre) }
                        }
                        .addOnCompleteListener {
                            if (--pendientes == 0) {
                                onDone(ResultadoBorrado(sinValoracion.size, borrados, fallidos))
                            }
                        }
                }
            }
            .addOnFailureListener { onDone(ResultadoBorrado(0, 0, emptyList())) }
    }

    private fun borrarTecnicosDemoTodos(onDone: () -> Unit) {
        var pendientes = TECNICO_IDS_DEMO_HISTORICO.size
        TECNICO_IDS_DEMO_HISTORICO.forEach { id ->
            db.collection("tecnicos").document(id).delete()
                .addOnCompleteListener { if (--pendientes == 0) onDone() }
        }
    }

    private fun crearCuentasAuthConCredenciales(
        context: Context,
        miUid: String,
        onDone: (creds: List<CredencialTecnico>) -> Unit
    ) {
        val opciones = FirebaseApp.getInstance().options
        val resultados = mutableListOf<CredencialTecnico>()
        var pendientes = TECNICOS_INFO.size

        fun marcar(c: CredencialTecnico) {
            synchronized(resultados) { resultados.add(c) }
            if (--pendientes == 0) onDone(resultados.sortedBy { it.nombre })
        }

        TECNICOS_INFO.forEach { tec ->
            val appName = "creator_${tec.docId}"
            val appSec = try {
                FirebaseApp.initializeApp(context, opciones, appName)
            } catch (_: Exception) {
                try { FirebaseApp.getInstance(appName) } catch (_: Exception) { null }
            } ?: run {
                marcar(CredencialTecnico(tec.nombre, tec.email, PASSWORD_TECNICOS, false, "App secundaria"))
                return@forEach
            }

            val authTemp = FirebaseAuth.getInstance(appSec)
            val dbTemp = FirebaseFirestore.getInstance(appSec)

            fun procesarUid(tecUid: String) {
                // Crear /usuarios/{tecUid} desde la sesión del técnico
                dbTemp.collection("usuarios").document(tecUid).set(
                    hashMapOf(
                        "uid" to tecUid,
                        "nombre" to tec.nombre,
                        "email" to tec.email,
                        "tipoUsuario" to "Técnico"
                    )
                ).addOnCompleteListener { authTemp.signOut() }

                // Vincular uid en /tecnicos/{docId} desde la sesión del cliente
                db.collection("tecnicos").document(tec.docId).update("uid", tecUid)
                    .addOnSuccessListener {
                        marcar(CredencialTecnico(tec.nombre, tec.email, PASSWORD_TECNICOS, true))
                    }
                    .addOnFailureListener { e ->
                        marcar(CredencialTecnico(
                            tec.nombre, tec.email, PASSWORD_TECNICOS,
                            false, "Vincular uid: ${e.message?.take(40)}"
                        ))
                    }
            }

            authTemp.createUserWithEmailAndPassword(tec.email, PASSWORD_TECNICOS)
                .addOnSuccessListener { res ->
                    val uid = res.user?.uid
                    if (uid != null) procesarUid(uid)
                    else marcar(CredencialTecnico(tec.nombre, tec.email, PASSWORD_TECNICOS, false, "Sin uid"))
                }
                .addOnFailureListener { e ->
                    val msg = e.message ?: ""
                    if (msg.contains("already in use") || msg.contains("EMAIL_EXISTS")) {
                        authTemp.signInWithEmailAndPassword(tec.email, PASSWORD_TECNICOS)
                            .addOnSuccessListener { res ->
                                val uid = res.user?.uid
                                if (uid != null) procesarUid(uid)
                                else marcar(CredencialTecnico(tec.nombre, tec.email, PASSWORD_TECNICOS, false, "Sin uid login"))
                            }
                            .addOnFailureListener {
                                marcar(CredencialTecnico(tec.nombre, tec.email, PASSWORD_TECNICOS, false, "Pwd no coincide"))
                            }
                    } else {
                        marcar(CredencialTecnico(tec.nombre, tec.email, PASSWORD_TECNICOS, false, msg.take(50)))
                    }
                }
        }
    }

    // Busca todos los chats donde el técnico aparece como participante (por docId o docId+espacio)
    // y los actualiza con el uid real de Auth
    private fun buscarYActualizarChats(docId: String, uidReal: String, nombre: String, onDone: () -> Unit) {
        val posiblesIds = listOf(docId, "$docId ").distinct()
        var pendientes = posiblesIds.size
        val chatIds = mutableSetOf<String>()

        posiblesIds.forEach { idBuscar ->
            db.collection("chats")
                .whereArrayContains("participantes", idBuscar)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { chatIds.add(it.id) }
                    if (--pendientes == 0) {
                        if (chatIds.isEmpty()) { onDone(); return@addOnSuccessListener }
                        var chatsPendientes = chatIds.size
                        chatIds.forEach { chatId ->
                            actualizarChatTecnico("", docId, uidReal, nombre, chatId) {
                                if (--chatsPendientes == 0) onDone()
                            }
                        }
                    }
                }
                .addOnFailureListener { if (--pendientes == 0) onDone() }
        }
    }

    private fun actualizarChatTecnico(
        miUid: String, tecDocId: String, tecUidReal: String, tecNombre: String,
        chatId: String, onDone: () -> Unit
    ) {
        val ref = db.collection("chats").document(chatId)
        ref.get().addOnSuccessListener { doc ->
            if (!doc.exists()) { onDone(); return@addOnSuccessListener }

            @Suppress("UNCHECKED_CAST")
            val participantes = (doc.get("participantes") as? List<String>)?.toMutableList() ?: mutableListOf()
            @Suppress("UNCHECKED_CAST")
            val nombres = (doc.get("nombresParticipantes") as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
            @Suppress("UNCHECKED_CAST")
            val noLeidos = (doc.get("noLeidosPor") as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()

            // Busca el docId con o sin espacio al final
            val idVariants = listOf(tecDocId, "$tecDocId ").distinct()
            val idx = idVariants.map { participantes.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
            if (idx >= 0) participantes[idx] = tecUidReal

            idVariants.forEach { v ->
                val n = nombres.remove(v); if (n != null) nombres[tecUidReal] = n
                val nl = noLeidos.remove(v); if (nl != null) noLeidos[tecUidReal] = nl
            }
            if (!nombres.containsKey(tecUidReal)) nombres[tecUidReal] = tecNombre
            if (!noLeidos.containsKey(tecUidReal)) noLeidos[tecUidReal] = 0

            ref.update(
                mapOf(
                    "participantes" to participantes,
                    "nombresParticipantes" to nombres,
                    "noLeidosPor" to noLeidos
                )
            ).addOnCompleteListener { onDone() }
        }.addOnFailureListener { onDone() }
    }

    // IDs fijos para limpieza
    private val TECNICO_IDS = TECNICO_IDS_DEMO_HISTORICO
    private val VIVIENDA_IDS = listOf("demo-vivienda-001", "demo-vivienda-002", "demo-vivienda-003")
    private val INFORME_IDS = listOf(
        "demo-informe-001", "demo-informe-002", "demo-informe-003",
        "demo-informe-004", "demo-informe-005", "demo-informe-006"
    )
    private val SOLICITUD_IDS = listOf("demo-solicitud-001", "demo-solicitud-002", "demo-solicitud-003")
    private val NOTIF_IDS = listOf(
        "demo-notif-001", "demo-notif-002", "demo-notif-003",
        "demo-notif-004", "demo-notif-005", "demo-notif-006",
        "demo-notif-007", "demo-notif-008", "demo-notif-009", "demo-notif-010"
    )
    private val CHAT_IDS = listOf("demo-chat-carlos-001", "demo-chat-ana-002")
    private val PROYECTO_TITULOS = setOf(
        "Rehabilitación energética completa",
        "Instalación placas solares",
        "Sustitución sistema ACS"
    )

    // ─────────────────────────────────────────
    // LIMPIEZA
    // ─────────────────────────────────────────
    private var limpiezaRealizada = false

    fun limpiarDuplicadosSiNecesario(onDone: (Int) -> Unit) {
        if (limpiezaRealizada) { onDone(0); return }
        limpiezaRealizada = true
        limpiarTodo(onDone)
    }

    private fun limpiarTodo(onDone: (Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onDone(0); return }
        var count = 0

        // Colecciones con IDs fijos
        val colsConIds = listOf(
            "tecnicos" to TECNICO_IDS,
            "viviendas" to VIVIENDA_IDS,
            "informes" to INFORME_IDS,
            "solicitudes" to SOLICITUD_IDS,
            "notificaciones" to NOTIF_IDS
        )
        var pendientes = colsConIds.size + 2 // +proyectos +chats

        fun done() { if (--pendientes == 0) onDone(count) }

        colsConIds.forEach { (col, ids) ->
            var r = ids.size
            ids.forEach { id ->
                db.collection(col).document(id).delete()
                    .addOnCompleteListener { count++; if (--r == 0) done() }
            }
        }

        // Proyectos demo (IDs aleatorios, se identifican por título)
        db.collection("proyectos").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { snap ->
                val demos = snap.documents.filter { it.getString("titulo") in PROYECTO_TITULOS }
                if (demos.isEmpty()) { done(); return@addOnSuccessListener }
                var r = demos.size
                demos.forEach { d ->
                    d.reference.delete().addOnCompleteListener { count++; if (--r == 0) done() }
                }
            }
            .addOnFailureListener { done() }

        // Chats demo (IDs fijos + subcol mensajes)
        var cr = CHAT_IDS.size
        CHAT_IDS.forEach { chatId ->
            db.collection("chats").document(chatId).collection("mensajes").get()
                .addOnSuccessListener { msgs ->
                    if (msgs.isEmpty) {
                        db.collection("chats").document(chatId).delete()
                            .addOnCompleteListener { count++; if (--cr == 0) done() }
                    } else {
                        var mr = msgs.size()
                        msgs.forEach { msg ->
                            msg.reference.delete().addOnCompleteListener {
                                if (--mr == 0) {
                                    db.collection("chats").document(chatId).delete()
                                        .addOnCompleteListener { count++; if (--cr == 0) done() }
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    db.collection("chats").document(chatId).delete()
                        .addOnCompleteListener { count++; if (--cr == 0) done() }
                }
        }
    }

    // ─────────────────────────────────────────
    // SEMBRADO PRINCIPAL
    // ─────────────────────────────────────────
    fun sembrar(onDone: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onDone(false); return }
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                val nombre = doc.getString("nombre") ?: "Tú"
                sembrarTodo(uid, nombre, onDone)
            }
            .addOnFailureListener { sembrarTodo(uid, "Tú", onDone) }
    }

    private fun sembrarTodo(uid: String, miNombre: String, onDone: (Boolean) -> Unit) {
        var pendientes = 7
        var ok = true
        fun check(exito: Boolean) { if (!exito) ok = false; if (--pendientes == 0) onDone(ok) }

        sembrarTecnicos(uid)          { check(it) }
        sembrarViviendas(uid)         { check(it) }
        sembrarInformes(uid)          { check(it) }
        sembrarProyectos(uid)         { check(it) }
        sembrarChats(uid, miNombre)   { check(it) }
        sembrarSolicitudes(uid, miNombre) { check(it) }
        sembrarNotificaciones(uid)    { check(it) }
    }

    // ─────────────────────────────────────────
    // TÉCNICOS
    // ─────────────────────────────────────────
    private fun sembrarTecnicos(uid: String, onDone: (Boolean) -> Unit) {
        // Solo los 7 mejor valorados (rating + nº opiniones)
        val tecnicos = listOf(
            hashMapOf(
                "id" to "demo-tecnico-ana-002", "uid" to uid,
                "nombre" to "Ana Martínez", "ciudad" to "Sevilla",
                "rating" to 4.9, "opiniones" to 62, "proyectosCompletados" to 103, "distanciaKm" to 0.0,
                "especialidades" to listOf("Solar", "Fotovoltaica", "Autoconsumo"),
                "descripcion" to "Ingeniera en energías renovables. Instalaciones fotovoltaicas residenciales y comerciales desde 2015. Certificada por SMA y Fronius.",
                "telefono" to "623 456 789", "emailContacto" to "ana.martinez@zerohaus.app",
                "latitud" to 37.3624, "longitud" to -5.9847   // Sevilla - Heliópolis
            ),
            hashMapOf(
                "id" to "demo-tecnico-sofia-010", "uid" to uid,
                "nombre" to "Sofía Hernández", "ciudad" to "Alicante",
                "rating" to 4.9, "opiniones" to 51, "proyectosCompletados" to 84, "distanciaKm" to 0.0,
                "especialidades" to listOf("Fotovoltaica", "Baterías", "Autoconsumo"),
                "descripcion" to "Instaladora fotovoltaica certificada. Especializada en autoconsumo con baterías Tesla y Huawei. 8 años de experiencia.",
                "telefono" to "601 234 567", "emailContacto" to "sofia.hernandez@zerohaus.app",
                "latitud" to 38.3658, "longitud" to -0.4863  // Alicante - Garbinet
            ),
            hashMapOf(
                "id" to "demo-tecnico-carlos-001", "uid" to uid,
                "nombre" to "Carlos García", "ciudad" to "Madrid",
                "rating" to 4.8, "opiniones" to 47, "proyectosCompletados" to 89, "distanciaKm" to 0.0,
                "especialidades" to listOf("Aislamiento", "Ventanas", "Fachadas"),
                "descripcion" to "Especialista en rehabilitación energética con más de 10 años de experiencia en mejoras de envolvente térmica. Certificado PassivHaus.",
                "telefono" to "612 345 678", "emailContacto" to "carlos.garcia@zerohaus.app",
                "latitud" to 40.4291, "longitud" to -3.6182   // Madrid - San Blas
            ),
            hashMapOf(
                "id" to "demo-tecnico-pablo-007", "uid" to uid,
                "nombre" to "Pablo Jiménez", "ciudad" to "Málaga",
                "rating" to 4.8, "opiniones" to 38, "proyectosCompletados" to 67, "distanciaKm" to 0.0,
                "especialidades" to listOf("Solar térmica", "ACS", "Piscinas"),
                "descripcion" to "Técnico solar térmico con experiencia en costa: ACS, piscinas y climatización. Trabajos en toda la Costa del Sol.",
                "telefono" to "678 901 234", "emailContacto" to "pablo.jimenez@zerohaus.app",
                "latitud" to 36.7195, "longitud" to -4.4128  // Málaga - La Malagueta
            ),
            hashMapOf(
                "id" to "demo-tecnico-victor-013", "uid" to uid,
                "nombre" to "Víctor Aguirre", "ciudad" to "Pamplona",
                "rating" to 4.8, "opiniones" to 33, "proyectosCompletados" to 56, "distanciaKm" to 0.0,
                "especialidades" to listOf("PassivHaus", "Construcción pasiva", "Aislamiento"),
                "descripcion" to "Diseñador certificado PassivHaus. Construcción y rehabilitación con estándar pasivo. Edificios de consumo casi nulo.",
                "telefono" to "634 567 802", "emailContacto" to "victor.aguirre@zerohaus.app",
                "latitud" to 42.8071, "longitud" to -1.6514  // Pamplona - Iturrama
            ),
            hashMapOf(
                "id" to "demo-tecnico-laura-004", "uid" to uid,
                "nombre" to "Laura Sánchez", "ciudad" to "Valencia",
                "rating" to 4.7, "opiniones" to 29, "proyectosCompletados" to 45, "distanciaKm" to 0.0,
                "especialidades" to listOf("Auditorías", "Certificación CEE", "Consultoría"),
                "descripcion" to "Auditora energética certificada IDAE. Especialista en certificados de eficiencia energética y auditorías RITE para edificios residenciales.",
                "telefono" to "645 678 901", "emailContacto" to "laura.sanchez@zerohaus.app",
                "latitud" to 39.4901, "longitud" to -0.3894   // Valencia - Marxalenes
            ),
            hashMapOf(
                "id" to "demo-tecnico-lucia-012", "uid" to uid,
                "nombre" to "Lucía Peña", "ciudad" to "Vigo",
                "rating" to 4.7, "opiniones" to 27, "proyectosCompletados" to 49, "distanciaKm" to 0.0,
                "especialidades" to listOf("Aerotermia", "Suelo radiante", "Climatización"),
                "descripcion" to "Instaladora autorizada Daikin y Bosch. Soluciones de aerotermia con suelo radiante para clima atlántico.",
                "telefono" to "623 456 781", "emailContacto" to "lucia.pena@zerohaus.app",
                "latitud" to 42.2218, "longitud" to -8.7162  // Vigo - O Calvario
            ),
            hashMapOf(
                "id" to "demo-tecnico-miguel-003", "uid" to uid,
                "nombre" to "Miguel Torres", "ciudad" to "Barcelona",
                "rating" to 4.6, "opiniones" to 31, "proyectosCompletados" to 58, "distanciaKm" to 0.0,
                "especialidades" to listOf("Aerotermia", "HVAC", "Climatización"),
                "descripcion" to "Técnico especializado en sistemas de aerotermia y bombas de calor. Certificado por Mitsubishi, Daikin y Vaillant.",
                "telefono" to "634 567 890", "emailContacto" to "miguel.torres@zerohaus.app",
                "latitud" to 41.3912, "longitud" to 2.1118    // Barcelona - Les Corts
            )
        )

        var r = tecnicos.size
        var exito = true
        tecnicos.forEach { t ->
            db.collection("tecnicos").document(t["id"] as String).set(t)
                .addOnSuccessListener { if (--r == 0) onDone(exito) }
                .addOnFailureListener { exito = false; if (--r == 0) onDone(false) }
        }
    }

    // ─────────────────────────────────────────
    // VIVIENDAS
    // ─────────────────────────────────────────
    private fun sembrarViviendas(uid: String, onDone: (Boolean) -> Unit) {
        val ahora = System.currentTimeMillis()
        val dia = 86_400_000L

        val viviendas = listOf(
            hashMapOf(
                "id" to "demo-vivienda-001", "uid" to uid,
                "nombre" to "Piso principal - Madrid",
                "superficie" to 85, "anioConstruccion" to 1975,
                "tipoVentanas" to "Vidrio simple", "aislamiento" to "Sin aislamiento",
                "calefaccion" to "Gas natural", "acs" to "Calentador eléctrico",
                "direccion" to "Calle Mayor 12, Madrid", "orientacion" to "Norte",
                "fechaCreacion" to ahora - 180 * dia
            ),
            hashMapOf(
                "id" to "demo-vivienda-002", "uid" to uid,
                "nombre" to "Casa de verano - Alicante",
                "superficie" to 120, "anioConstruccion" to 1990,
                "tipoVentanas" to "Doble acristalamiento", "aislamiento" to "Lana mineral",
                "calefaccion" to "Bomba de calor", "acs" to "Solar térmico",
                "direccion" to "Urbanización Las Palmeras 5, Alicante", "orientacion" to "Sur",
                "fechaCreacion" to ahora - 90 * dia
            ),
            hashMapOf(
                "id" to "demo-vivienda-003", "uid" to uid,
                "nombre" to "Apartamento - Barcelona",
                "superficie" to 60, "anioConstruccion" to 2005,
                "tipoVentanas" to "Doble acristalamiento", "aislamiento" to "EPS",
                "calefaccion" to "Radiadores eléctricos", "acs" to "Calentador de gas",
                "direccion" to "Carrer de Gràcia 88, Barcelona", "orientacion" to "Este",
                "fechaCreacion" to ahora - 45 * dia
            )
        )

        var r = viviendas.size
        var exito = true
        viviendas.forEach { v ->
            db.collection("viviendas").document(v["id"] as String).set(v)
                .addOnSuccessListener { if (--r == 0) onDone(exito) }
                .addOnFailureListener { exito = false; if (--r == 0) onDone(false) }
        }
    }

    // ─────────────────────────────────────────
    // INFORMES ENERGÉTICOS (evolución para gráficas)
    // ─────────────────────────────────────────
    private fun sembrarInformes(uid: String, onDone: (Boolean) -> Unit) {
        val ahora = System.currentTimeMillis()
        val dia = 86_400_000L

        val recsE = listOf(
            mapOf("titulo" to "Sustituir ventanas por doble acristalamiento con RPT", "ahorroEstimado" to 20),
            mapOf("titulo" to "Añadir aislamiento SATE en fachada (6 cm)", "ahorroEstimado" to 25),
            mapOf("titulo" to "Cambiar caldera de gas por bomba de calor aerotérmica", "ahorroEstimado" to 30)
        )
        val recsD = listOf(
            mapOf("titulo" to "Instalar termostato inteligente programable", "ahorroEstimado" to 10),
            mapOf("titulo" to "Añadir aislamiento en cubierta (10 cm XPS)", "ahorroEstimado" to 15),
            mapOf("titulo" to "Instalar 6 paneles solares fotovoltaicos (3 kWp)", "ahorroEstimado" to 25)
        )
        val recsC = listOf(
            mapOf("titulo" to "Instalar paneles fotovoltaicos para autoconsumo total", "ahorroEstimado" to 20),
            mapOf("titulo" to "Mejorar ventilación con sistema de recuperación de calor", "ahorroEstimado" to 8)
        )
        val recsB = listOf(
            mapOf("titulo" to "Añadir baterías de almacenamiento solar (10 kWh)", "ahorroEstimado" to 12),
            mapOf("titulo" to "Optimizar horarios de consumo con tarifa supervalle", "ahorroEstimado" to 5)
        )

        val informes = listOf(
            // Piso Madrid — evolución mejora a lo largo de 6 meses
            hashMapOf(
                "id" to "demo-informe-001", "uid" to uid,
                "viviendaId" to "demo-vivienda-001", "nombreVivienda" to "Piso principal - Madrid",
                "etiqueta" to "E", "estadoEficiencia" to "Muy mejorable",
                "consumoEstimado" to 18500.0, "emisiones" to 4200.0, "costeAnual" to 2800.0,
                "recomendaciones" to recsE, "fechaGeneracion" to ahora - 180 * dia
            ),
            hashMapOf(
                "id" to "demo-informe-002", "uid" to uid,
                "viviendaId" to "demo-vivienda-001", "nombreVivienda" to "Piso principal - Madrid",
                "etiqueta" to "D", "estadoEficiencia" to "Mejorable",
                "consumoEstimado" to 15000.0, "emisiones" to 3400.0, "costeAnual" to 2300.0,
                "recomendaciones" to recsD, "fechaGeneracion" to ahora - 120 * dia
            ),
            hashMapOf(
                "id" to "demo-informe-003", "uid" to uid,
                "viviendaId" to "demo-vivienda-001", "nombreVivienda" to "Piso principal - Madrid",
                "etiqueta" to "D", "estadoEficiencia" to "Mejorable",
                "consumoEstimado" to 13800.0, "emisiones" to 3100.0, "costeAnual" to 2100.0,
                "recomendaciones" to recsD, "fechaGeneracion" to ahora - 60 * dia
            ),
            hashMapOf(
                "id" to "demo-informe-004", "uid" to uid,
                "viviendaId" to "demo-vivienda-001", "nombreVivienda" to "Piso principal - Madrid",
                "etiqueta" to "C", "estadoEficiencia" to "Eficiente",
                "consumoEstimado" to 11200.0, "emisiones" to 2500.0, "costeAnual" to 1750.0,
                "recomendaciones" to recsC, "fechaGeneracion" to ahora - 15 * dia
            ),
            // Casa Alicante
            hashMapOf(
                "id" to "demo-informe-005", "uid" to uid,
                "viviendaId" to "demo-vivienda-002", "nombreVivienda" to "Casa de verano - Alicante",
                "etiqueta" to "B", "estadoEficiencia" to "Muy eficiente",
                "consumoEstimado" to 7500.0, "emisiones" to 1200.0, "costeAnual" to 950.0,
                "recomendaciones" to recsB, "fechaGeneracion" to ahora - 30 * dia
            ),
            // Apartamento Barcelona
            hashMapOf(
                "id" to "demo-informe-006", "uid" to uid,
                "viviendaId" to "demo-vivienda-003", "nombreVivienda" to "Apartamento - Barcelona",
                "etiqueta" to "D", "estadoEficiencia" to "Mejorable",
                "consumoEstimado" to 9800.0, "emisiones" to 2200.0, "costeAnual" to 1450.0,
                "recomendaciones" to recsD, "fechaGeneracion" to ahora - 10 * dia
            )
        )

        var r = informes.size
        var exito = true
        informes.forEach { inf ->
            db.collection("informes").document(inf["id"] as String).set(inf)
                .addOnSuccessListener { if (--r == 0) onDone(exito) }
                .addOnFailureListener { exito = false; if (--r == 0) onDone(false) }
        }
    }

    // ─────────────────────────────────────────
    // PROYECTOS
    // ─────────────────────────────────────────
    private fun sembrarProyectos(uid: String, onDone: (Boolean) -> Unit) {
        val ahora = System.currentTimeMillis()
        val dia = 86_400_000L

        val proyectos = listOf(
            Proyecto(
                uid = uid,
                titulo = "Rehabilitación energética completa",
                descripcion = "Mejora integral de la eficiencia energética del piso principal. Incluye aislamiento SATE, ventanas PVC con RPT y sistema de aerotermia.",
                viviendaNombre = "Piso principal - Madrid",
                tecnicoNombre = "Carlos García",
                progreso = 65, estado = "En curso",
                fechaCreacion = ahora - 30 * dia,
                fechaFinEstimada = ahora + 60 * dia,
                tareas = listOf(
                    Tarea("Auditoría energética inicial", true),
                    Tarea("Sustitución de ventanas por PVC con RPT", true),
                    Tarea("Instalación de aislamiento SATE en fachada", true),
                    Tarea("Instalación de aerotermia Mitsubishi Ecodan", false),
                    Tarea("Revisión final y certificación CEE", false)
                )
            ),
            Proyecto(
                uid = uid,
                titulo = "Instalación placas solares",
                descripcion = "Instalación de 8 paneles solares fotovoltaicos para autoconsumo. Potencia estimada 4 kWp. Inversor híbrido con posibilidad de batería.",
                viviendaNombre = "Piso principal - Madrid",
                tecnicoNombre = "Ana Martínez",
                progreso = 0, estado = "Pendiente",
                fechaCreacion = ahora - 5 * dia,
                fechaFinEstimada = ahora + 45 * dia,
                tareas = listOf(
                    Tarea("Estudio de viabilidad y orientación cubierta", false),
                    Tarea("Trámites y permisos municipales", false),
                    Tarea("Instalación de estructura soporte", false),
                    Tarea("Montaje de paneles e inversor Fronius", false),
                    Tarea("Conexión a red y legalización ante distribuidora", false)
                )
            ),
            Proyecto(
                uid = uid,
                titulo = "Sustitución sistema ACS",
                descripcion = "Cambio de calentador eléctrico por bomba de calor aerotérmica para ACS. Ahorro estimado del 70% en agua caliente.",
                viviendaNombre = "Piso principal - Madrid",
                tecnicoNombre = "Miguel Torres",
                progreso = 100, estado = "Finalizado",
                fechaCreacion = ahora - 90 * dia,
                fechaFinEstimada = ahora - 30 * dia,
                tareas = listOf(
                    Tarea("Selección y compra del equipo Daikin Altherma", true),
                    Tarea("Retirada del calentador eléctrico antiguo", true),
                    Tarea("Instalación bomba de calor ACS", true),
                    Tarea("Pruebas de funcionamiento y puesta en marcha", true)
                )
            )
        )

        var exito = true
        var r = proyectos.size
        proyectos.forEach { p ->
            val ref = db.collection("proyectos").document()
            ref.set(p.copy(id = ref.id))
                .addOnSuccessListener { if (--r == 0) onDone(exito) }
                .addOnFailureListener { exito = false; if (--r == 0) onDone(false) }
        }
    }

    // ─────────────────────────────────────────
    // CHATS
    // ─────────────────────────────────────────
    private fun sembrarChats(uid: String, miNombre: String, onDone: (Boolean) -> Unit) {
        val ahora = System.currentTimeMillis()
        val min = 60_000L

        val conversaciones = listOf(
            Triple(
                "demo-chat-carlos-001",
                "demo-tecnico-carlos-001" to "Carlos García (Técnico)",
                listOf(
                    uid to "Hola Carlos, vi tu perfil y me gustaría hablar sobre la rehabilitación energética de mi piso.",
                    "demo-tecnico-carlos-001" to "¡Hola! Claro, con mucho gusto. ¿Cuál es la situación actual de tu vivienda?",
                    uid to "Tenemos ventanas de vidrio simple y caldera de gas antigua. El consumo es bastante alto, etiqueta E.",
                    "demo-tecnico-carlos-001" to "Entiendo. Lo primero sería hacer una auditoría energética para ver exactamente el punto de partida. ¿Cuándo te vendría bien?",
                    uid to "La semana que viene podría ser. ¿El martes o miércoles?",
                    "demo-tecnico-carlos-001" to "Perfecto, el miércoles a las 10:00 me viene bien. Te confirmo dirección y te mando el presupuesto previo esta tarde."
                )
            ),
            Triple(
                "demo-chat-ana-002",
                "demo-tecnico-ana-002" to "Ana Martínez (Técnico)",
                listOf(
                    uid to "Buenas Ana, me interesa instalar placas solares. Vi que tienes muy buena puntuación en la plataforma.",
                    "demo-tecnico-ana-002" to "¡Hola, gracias! Sí, tengo mucha experiencia en instalaciones fotovoltaicas residenciales. ¿Tienes tejado disponible orientado al sur?",
                    uid to "Sí, el tejado está orientado al sur con unos 40m² libres sin sombras.",
                    "demo-tecnico-ana-002" to "Ideal. Con esa orientación podríamos conseguir un autoconsumo del 60-70% de tu consumo anual. ¿Quieres que pase a hacer una valoración in situ?",
                    uid to "Sí, me interesa. ¿Podrías enviarme también un presupuesto aproximado?",
                    "demo-tecnico-ana-002" to "Claro, para una vivienda de tu perfil necesitarías entre 6 y 8 paneles (3-4 kWp). Te envío presupuesto detallado mañana por email."
                )
            )
        )

        var chatsPendientes = conversaciones.size
        var exito = true

        conversaciones.forEach { (chatId, tecnico, mensajes) ->
            val (tecId, tecNombre) = tecnico
            val ultimoTexto = mensajes.last().second
            val chatData = hashMapOf(
                "id" to chatId,
                "participantes" to listOf(uid, tecId),
                "nombresParticipantes" to mapOf(uid to miNombre, tecId to tecNombre),
                "ultimoMensaje" to ultimoTexto,
                "fechaUltimoMensaje" to ahora,
                "noLeidosPor" to mapOf(uid to 0, tecId to 0)
            )

            db.collection("chats").document(chatId).set(chatData)
                .addOnSuccessListener {
                    var msgPendientes = mensajes.size
                    mensajes.forEachIndexed { i, (emisorUid, texto) ->
                        val msgRef = db.collection("chats").document(chatId)
                            .collection("mensajes").document()
                        val msgData = hashMapOf(
                            "id" to msgRef.id,
                            "chatId" to chatId,
                            "emisorUid" to emisorUid,
                            "emisorNombre" to if (emisorUid == uid) miNombre else tecNombre,
                            "texto" to texto,
                            "fecha" to (ahora - (mensajes.size - i) * 5 * min),
                            "leido" to true
                        )
                        msgRef.set(msgData)
                            .addOnSuccessListener {
                                if (--msgPendientes == 0 && --chatsPendientes == 0) onDone(exito)
                            }
                            .addOnFailureListener {
                                exito = false
                                if (--msgPendientes == 0 && --chatsPendientes == 0) onDone(false)
                            }
                    }
                }
                .addOnFailureListener { exito = false; if (--chatsPendientes == 0) onDone(false) }
        }
    }

    // ─────────────────────────────────────────
    // SOLICITUDES DE PRESUPUESTO
    // ─────────────────────────────────────────
    private fun sembrarSolicitudes(uid: String, miNombre: String, onDone: (Boolean) -> Unit) {
        val ahora = System.currentTimeMillis()
        val dia = 86_400_000L

        val solicitudes = listOf(
            hashMapOf(
                "id" to "demo-solicitud-001", "uidCliente" to uid, "nombreCliente" to miNombre,
                "tecnicoId" to "demo-tecnico-carlos-001", "tecnicoNombre" to "Carlos García",
                "viviendaId" to "demo-vivienda-001",
                "descripcion" to "Me gustaría mejorar el aislamiento de la fachada y cambiar las ventanas por doble acristalamiento con rotura de puente térmico.",
                "estado" to "Presupuestado",
                "precioPresupuesto" to 4500.0,
                "respuestaTecnico" to "He revisado las características de tu vivienda. El presupuesto incluye aislamiento exterior SATE 6 cm + ventanas PVC con RPT. Material y mano de obra incluidos. Plazo de ejecución: 3 semanas.",
                "fechaCreacion" to ahora - 20 * dia,
                "fechaRespuesta" to ahora - 15 * dia
            ),
            hashMapOf(
                "id" to "demo-solicitud-002", "uidCliente" to uid, "nombreCliente" to miNombre,
                "tecnicoId" to "demo-tecnico-ana-002", "tecnicoNombre" to "Ana Martínez",
                "viviendaId" to "demo-vivienda-001",
                "descripcion" to "Quiero instalar paneles solares fotovoltaicos en el tejado. Orientación sur, aproximadamente 40m² disponibles sin sombras.",
                "estado" to "Pendiente",
                "precioPresupuesto" to 0.0,
                "respuestaTecnico" to "",
                "fechaCreacion" to ahora - 5 * dia,
                "fechaRespuesta" to 0L
            ),
            hashMapOf(
                "id" to "demo-solicitud-003", "uidCliente" to uid, "nombreCliente" to miNombre,
                "tecnicoId" to "demo-tecnico-miguel-003", "tecnicoNombre" to "Miguel Torres",
                "viviendaId" to "demo-vivienda-001",
                "descripcion" to "Quiero sustituir la caldera de gas por un sistema de aerotermia. Actualmente tengo radiadores de agua caliente.",
                "estado" to "Aceptado",
                "precioPresupuesto" to 8200.0,
                "respuestaTecnico" to "Incluye unidad exterior e interior Mitsubishi Ecodan 8 kW, adaptación del circuito hidráulico existente y puesta en marcha. Financiación 0% disponible a 24 meses.",
                "fechaCreacion" to ahora - 45 * dia,
                "fechaRespuesta" to ahora - 40 * dia
            )
        )

        var r = solicitudes.size
        var exito = true
        solicitudes.forEach { s ->
            db.collection("solicitudes").document(s["id"] as String).set(s)
                .addOnSuccessListener { if (--r == 0) onDone(exito) }
                .addOnFailureListener { exito = false; if (--r == 0) onDone(false) }
        }
    }

    // ─────────────────────────────────────────
    // NOTIFICACIONES
    // ─────────────────────────────────────────
    private fun sembrarNotificaciones(uid: String, onDone: (Boolean) -> Unit) {
        val ahora = System.currentTimeMillis()
        val dia = 86_400_000L

        val notifs = listOf(
            hashMapOf(
                "id" to "demo-notif-001", "uid" to uid,
                "titulo" to "Presupuesto recibido de Carlos García",
                "detalle" to "Carlos García ha respondido a tu solicitud de aislamiento y ventanas con un presupuesto de 4.500 €. Ábrelo en Presupuestos para aceptarlo.",
                "fecha" to ahora - 15 * dia, "leida" to false, "tipo" to "presupuesto"
            ),
            hashMapOf(
                "id" to "demo-notif-002", "uid" to uid,
                "titulo" to "Proyecto actualizado al 65%",
                "detalle" to "La rehabilitación energética completa ha avanzado. Se han instalado las ventanas PVC y el aislamiento SATE en fachada.",
                "fecha" to ahora - 10 * dia, "leida" to false, "tipo" to "proyecto"
            ),
            hashMapOf(
                "id" to "demo-notif-003", "uid" to uid,
                "titulo" to "Tu piso ha mejorado a etiqueta C",
                "detalle" to "Después de las mejoras realizadas, tu piso principal ha pasado de etiqueta E a C. Ahorro estimado: 1.050 €/año.",
                "fecha" to ahora - 15 * dia, "leida" to true, "tipo" to "general"
            ),
            hashMapOf(
                "id" to "demo-notif-004", "uid" to uid,
                "titulo" to "Nuevo mensaje de Ana Martínez",
                "detalle" to "Ana Martínez: \"Para una vivienda de tu perfil necesitarías entre 6 y 8 paneles (3-4 kWp). Te envío presupuesto detallado mañana.\"",
                "fecha" to ahora - 3 * dia, "leida" to false, "tipo" to "chat"
            ),
            hashMapOf(
                "id" to "demo-notif-005", "uid" to uid,
                "titulo" to "Presupuesto de aerotermia aceptado ✓",
                "detalle" to "Has aceptado el presupuesto de Miguel Torres (8.200 €). El técnico se pondrá en contacto para coordinar la instalación.",
                "fecha" to ahora - 40 * dia, "leida" to true, "tipo" to "presupuesto"
            ),
            hashMapOf(
                "id" to "demo-notif-006", "uid" to uid,
                "titulo" to "Nuevo mensaje de Carlos García",
                "detalle" to "Carlos García: \"El miércoles a las 10:00 me viene bien. Te confirmo dirección y te mando el presupuesto previo esta tarde.\"",
                "fecha" to ahora - 8 * dia, "leida" to false, "tipo" to "chat"
            ),
            hashMapOf(
                "id" to "demo-notif-007", "uid" to uid,
                "titulo" to "Tarea completada en tu proyecto",
                "detalle" to "Se ha marcado como completada la tarea \"Instalación de aislamiento SATE en fachada\" en el proyecto de rehabilitación energética.",
                "fecha" to ahora - 12 * dia, "leida" to true, "tipo" to "proyecto"
            ),
            hashMapOf(
                "id" to "demo-notif-008", "uid" to uid,
                "titulo" to "Solicitud enviada a Ana Martínez",
                "detalle" to "Tu solicitud de presupuesto para la instalación de paneles solares fotovoltaicos ha sido enviada. Recibirás una notificación cuando Ana Martínez responda.",
                "fecha" to ahora - 5 * dia, "leida" to true, "tipo" to "presupuesto"
            ),
            hashMapOf(
                "id" to "demo-notif-009", "uid" to uid,
                "titulo" to "Nuevo informe energético disponible",
                "detalle" to "Se ha generado un nuevo informe para tu piso de Madrid. Etiqueta actual: C. Consumo: 11.200 kWh/año. Ver en Historial de informes.",
                "fecha" to ahora - 16 * dia, "leida" to false, "tipo" to "general"
            ),
            hashMapOf(
                "id" to "demo-notif-010", "uid" to uid,
                "titulo" to "¡Proyecto finalizado!",
                "detalle" to "El proyecto \"Sustitución sistema ACS\" ha sido completado al 100%. La bomba de calor Daikin Altherma está operativa. Ahorro estimado: 70% en agua caliente.",
                "fecha" to ahora - 30 * dia, "leida" to true, "tipo" to "proyecto"
            )
        )

        var r = notifs.size
        var exito = true
        notifs.forEach { n ->
            db.collection("notificaciones").document(n["id"] as String).set(n)
                .addOnSuccessListener { if (--r == 0) onDone(exito) }
                .addOnFailureListener { exito = false; if (--r == 0) onDone(false) }
        }
    }
}
