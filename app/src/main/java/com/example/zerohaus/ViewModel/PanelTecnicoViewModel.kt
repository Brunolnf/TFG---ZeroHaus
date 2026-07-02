package com.example.zerohaus.ViewModel

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class PanelTecnicoEstado(
    val tecnico: Tecnico? = null,
    val solicitudesPendientes: Int = 0,
    val solicitudesPresupuestadas: Int = 0,
    val solicitudesAceptadas: Int = 0,
    val cargando: Boolean = false
)

class PanelTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(PanelTecnicoEstado())
        private set

    private val repo = RepositorioTecnicos()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val watchdog = Handler(Looper.getMainLooper())
    private var listenerSolicitudes: ListenerRegistration? = null

    init {
        if (auth.currentUser != null) cargar()
    }

    fun cargar(forzar: Boolean = false) {
        if (!forzar && estado.tecnico != null) return   // ya tiene datos
        if (!forzar && estado.cargando) return           // carga en curso
        estado = estado.copy(cargando = true)
        // Watchdog: la cadena de autocura encadena varias queries; si alguna se
        // cuelga (App Check / red), soltamos el spinner a los 10s igualmente.
        watchdog.removeCallbacksAndMessages(null)
        watchdog.postDelayed({
            if (estado.cargando) estado = estado.copy(cargando = false)
        }, 10000L)
        val miUid = auth.currentUser?.uid ?: run {
            watchdog.removeCallbacksAndMessages(null)
            estado = estado.copy(cargando = false)
            return
        }

        // 1. Buscar perfil de técnico vinculado a este Auth uid.
        db.collection("tecnicos").whereEqualTo("uid", miUid).limit(1).get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                val tec = doc?.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }
                if (tec != null) {
                    cargarSolicitudesYActualizar(tec)
                } else {
                    autoCrearPerfilTecnico(miUid)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error buscando técnico por uid: ${it.message}")
                estado = estado.copy(cargando = false)
            }
    }

    /**
     * Autocura multi-estrategia. Intenta varios caminos para encontrar
     * o crear el perfil de técnico correcto.
     */
    private fun autoCrearPerfilTecnico(miUid: String) {
        val miEmail = auth.currentUser?.email.orEmpty()
        Log.i(TAG, "Autocura: uid=$miUid email=$miEmail")

        // Puede existir con uid vacío (datos antiguos) o de un intento previo.
        db.collection("tecnicos").document(miUid).get()
            .addOnSuccessListener { docDirecto ->
                if (docDirecto.exists()) {
                    Log.i(TAG, "Encontrado /tecnicos/$miUid directo. Reparando uid…")
                    repararYUsar(docDirecto, miUid)
                } else {
                    buscarPorEmail(miUid, miEmail)
                }
            }
            .addOnFailureListener {
                buscarPorEmail(miUid, miEmail)
            }
    }

    /** Paso 1: buscar por emailContacto */
    private fun buscarPorEmail(miUid: String, miEmail: String) {
        if (miEmail.isBlank()) {
            crearDesdeUsuarios(miUid)
            return
        }
        db.collection("tecnicos")
            .whereEqualTo("emailContacto", miEmail)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                if (doc != null) {
                    Log.i(TAG, "Encontrado /tecnicos/${doc.id} por email=$miEmail")
                    clonarANuevoDoc(doc, miUid)
                } else {
                    crearDesdeUsuarios(miUid)
                }
            }
            .addOnFailureListener {
                crearDesdeUsuarios(miUid)
            }
    }

    /**
     * El doc existe en /tecnicos/{miUid} pero su campo uid no coincide.
     * Intentamos update (la regla docId==uid() lo permite).
     * Si falla, lo leemos como data y lo re-creamos.
     */
    private fun repararYUsar(doc: DocumentSnapshot, miUid: String) {
        val datos = doc.data?.toMutableMap() ?: mutableMapOf()
        datos["id"] = miUid
        datos["uid"] = miUid

        db.collection("tecnicos").document(miUid)
            .set(datos)
            .addOnSuccessListener {
                Log.i(TAG, "Reparado /tecnicos/$miUid")
                val tec = doc.toObject(Tecnico::class.java)?.copy(id = miUid, uid = miUid)
                cargarSolicitudesYActualizar(tec)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error reparando: ${e.message}. Usando data local.")
                // Aunque no pudimos escribir, intentamos mostrar los datos igualmente
                val tec = doc.toObject(Tecnico::class.java)?.copy(id = miUid, uid = miUid)
                if (tec != null) {
                    cargarSolicitudesYActualizar(tec)
                } else {
                    crearDesdeUsuarios(miUid)
                }
            }
    }

    /**
     * Copia todos los campos del doc encontrado a /tecnicos/{miUid}.
     * La regla create ya permite: request.resource.data.uid == uid().
     */
    private fun clonarANuevoDoc(docOriginal: DocumentSnapshot, miUid: String) {
        val datos = docOriginal.data?.toMutableMap() ?: mutableMapOf()
        datos["id"] = miUid
        datos["uid"] = miUid
        Log.i(TAG, "Clonando /tecnicos/${docOriginal.id} → /tecnicos/$miUid")

        db.collection("tecnicos").document(miUid)
            .set(datos)
            .addOnSuccessListener {
                Log.i(TAG, "Clonado OK")
                val tec = docOriginal.toObject(Tecnico::class.java)
                    ?.copy(id = miUid, uid = miUid)
                cargarSolicitudesYActualizar(tec)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error clonando: ${e.message}")
                // Aunque no pudimos clonar, mostramos los datos que tenemos
                val tec = docOriginal.toObject(Tecnico::class.java)
                    ?.copy(id = docOriginal.id, uid = miUid)
                if (tec != null) {
                    cargarSolicitudesYActualizar(tec)
                } else {
                    crearDesdeUsuarios(miUid)
                }
            }
    }

    /** Último recurso: lee /usuarios/{uid} y crea un perfil técnico mínimo. */
    private fun crearDesdeUsuarios(miUid: String) {
        Log.i(TAG, "Intentando crear desde /usuarios/$miUid")
        db.collection("usuarios").document(miUid).get()
            .addOnSuccessListener { uSnap ->
                if (!uSnap.exists()) {
                    Log.w(TAG, "No existe /usuarios/$miUid — creando perfil mínimo")
                    crearPerfilMinimo(miUid)
                    return@addOnSuccessListener
                }
                val tipo = uSnap.getString("tipoUsuario") ?: ""
                val esTecnico = tipo.equals("Técnico", ignoreCase = true) ||
                                tipo.equals("Tecnico", ignoreCase = true)
                if (!esTecnico) {
                    Log.w(TAG, "tipoUsuario='$tipo' — no es técnico")
                    cargarSolicitudesYActualizar(null)
                    return@addOnSuccessListener
                }
                val nombre = uSnap.getString("nombre") ?: auth.currentUser?.displayName ?: ""
                val email = uSnap.getString("email") ?: auth.currentUser?.email ?: ""
                val datos = mapOf(
                    "id" to miUid,
                    "uid" to miUid,
                    "nombre" to nombre,
                    "emailContacto" to email,
                )
                db.collection("tecnicos").document(miUid)
                    .set(datos)
                    .addOnSuccessListener {
                        Log.i(TAG, "Perfil técnico creado desde /usuarios")
                        cargarSolicitudesYActualizar(
                            Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error creando desde /usuarios: ${e.message}")
                        // Último recurso: mostrar lo que tenemos sin persistir
                        cargarSolicitudesYActualizar(
                            Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                        )
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error leyendo /usuarios: ${e.message}")
                crearPerfilMinimo(miUid)
            }
    }

    /** Último-último recurso: crea un perfil con los datos de Auth. */
    private fun crearPerfilMinimo(miUid: String) {
        val nombre = auth.currentUser?.displayName ?: ""
        val email = auth.currentUser?.email ?: ""
        val datos = mapOf(
            "id" to miUid,
            "uid" to miUid,
            "nombre" to nombre,
            "emailContacto" to email,
        )
        Log.i(TAG, "Creando perfil mínimo para uid=$miUid nombre=$nombre email=$email")
        db.collection("tecnicos").document(miUid)
            .set(datos)
            .addOnSuccessListener {
                cargarSolicitudesYActualizar(
                    Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Ni siquiera pude crear perfil mínimo: ${e.message}")
                // Mostrar algo en vez del error rojo
                cargarSolicitudesYActualizar(
                    Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                )
            }
    }

    companion object { private const val TAG = "PanelTecnicoVM" }

    /**
     * Suscribe los contadores de solicitudes en tiempo real. Antes era un one-shot get
     * que solo se disparaba en `cargar()`: si llegaba una solicitud nueva mientras el
     * panel estaba abierto (o el cliente rechazaba un presupuesto y reenviaba), los
     * contadores quedaban desfasados hasta cerrar y volver a abrir la app.
     */
    private fun cargarSolicitudesYActualizar(tec: Tecnico?) {
        listenerSolicitudes?.remove()
        listenerSolicitudes = repo.escucharSolicitudesRecibidas { solicitudes ->
            watchdog.removeCallbacksAndMessages(null)
            estado = PanelTecnicoEstado(
                tecnico = tec,
                solicitudesPendientes = solicitudes.count { it.estado == "Pendiente" },
                solicitudesPresupuestadas = solicitudes.count { it.estado == "Presupuestado" },
                solicitudesAceptadas = solicitudes.count { it.estado == "Aceptado" },
                cargando = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        watchdog.removeCallbacksAndMessages(null)
        listenerSolicitudes?.remove()
        listenerSolicitudes = null
    }
}
