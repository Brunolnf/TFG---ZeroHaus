package com.example.zerohaus.ViewModel

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioMonetizacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Estado del panel de inicio del profesional.
 */
data class PanelTecnicoEstado(
    val tecnico: Tecnico? = null,
    val cargando: Boolean = false
)

/**
 * Panel del profesional. Si su perfil `/tecnicos` falta o está desvinculado,
 * lo repara (lo crea o lo enlaza por uid/email) para que aparezca en el directorio.
 */
class PanelTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(PanelTecnicoEstado())
        private set

    private val repoMonetizacion = RepositorioMonetizacion()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val watchdog = Handler(Looper.getMainLooper())
    private var listenerPerfil: ListenerRegistration? = null

    init {
        if (auth.currentUser != null) cargar()
    }

    fun cargar(forzar: Boolean = false) {
        if (!forzar && estado.tecnico != null) return
        if (!forzar && estado.cargando) return
        estado = estado.copy(cargando = true)
        watchdog.removeCallbacksAndMessages(null)
        watchdog.postDelayed({
            if (estado.cargando) estado = estado.copy(cargando = false)
        }, 10000L)
        val miUid = auth.currentUser?.uid ?: run {
            watchdog.removeCallbacksAndMessages(null)
            estado = estado.copy(cargando = false)
            return
        }

        db.collection("tecnicos").whereEqualTo("uid", miUid).limit(1).get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                val tec = doc?.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }
                if (tec != null) {
                    iniciarListenerPerfil(tec)
                } else {
                    autoCrearPerfilTecnico(miUid)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error buscando técnico por uid: ${it.message}")
                estado = estado.copy(cargando = false)
            }
    }

    private fun autoCrearPerfilTecnico(miUid: String) {
        val miEmail = auth.currentUser?.email.orEmpty()

        db.collection("tecnicos").document(miUid).get()
            .addOnSuccessListener { docDirecto ->
                if (docDirecto.exists()) {
                    repararYUsar(docDirecto, miUid)
                } else {
                    buscarPorEmail(miUid, miEmail)
                }
            }
            .addOnFailureListener {
                buscarPorEmail(miUid, miEmail)
            }
    }

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
                    clonarANuevoDoc(doc, miUid)
                } else {
                    crearDesdeUsuarios(miUid)
                }
            }
            .addOnFailureListener {
                crearDesdeUsuarios(miUid)
            }
    }

    private fun repararYUsar(doc: DocumentSnapshot, miUid: String) {
        val datos = doc.data?.toMutableMap() ?: mutableMapOf()
        datos["id"] = miUid
        datos["uid"] = miUid

        db.collection("tecnicos").document(miUid)
            .set(datos)
            .addOnSuccessListener {
                val tec = doc.toObject(Tecnico::class.java)?.copy(id = miUid, uid = miUid)
                iniciarListenerPerfil(tec)
            }
            .addOnFailureListener {
                val tec = doc.toObject(Tecnico::class.java)?.copy(id = miUid, uid = miUid)
                if (tec != null) iniciarListenerPerfil(tec)
                else crearDesdeUsuarios(miUid)
            }
    }

    private fun clonarANuevoDoc(docOriginal: DocumentSnapshot, miUid: String) {
        val datos = docOriginal.data?.toMutableMap() ?: mutableMapOf()
        datos["id"] = miUid
        datos["uid"] = miUid

        db.collection("tecnicos").document(miUid)
            .set(datos)
            .addOnSuccessListener {
                val tec = docOriginal.toObject(Tecnico::class.java)
                    ?.copy(id = miUid, uid = miUid)
                iniciarListenerPerfil(tec)
            }
            .addOnFailureListener {
                val tec = docOriginal.toObject(Tecnico::class.java)
                    ?.copy(id = docOriginal.id, uid = miUid)
                if (tec != null) iniciarListenerPerfil(tec)
                else crearDesdeUsuarios(miUid)
            }
    }

    private fun crearDesdeUsuarios(miUid: String) {
        db.collection("usuarios").document(miUid).get()
            .addOnSuccessListener { uSnap ->
                if (!uSnap.exists()) {
                    crearPerfilMinimo(miUid)
                    return@addOnSuccessListener
                }
                val tipo = uSnap.getString("tipoUsuario") ?: ""
                val esTecnico = tipo.equals("Técnico", ignoreCase = true) ||
                                tipo.equals("Tecnico", ignoreCase = true)
                val esEmpresa = tipo.equals("Empresa", ignoreCase = true)
                if (!esTecnico && !esEmpresa) {
                    iniciarListenerPerfil(null)
                    return@addOnSuccessListener
                }
                val nombre = uSnap.getString("nombre") ?: auth.currentUser?.displayName ?: ""
                val email = uSnap.getString("email") ?: auth.currentUser?.email ?: ""
                val datos = mapOf(
                    "id" to miUid,
                    "uid" to miUid,
                    "nombre" to nombre,
                    "emailContacto" to email,
                    "tipoProfesional" to if (esEmpresa) Tecnico.TIPO_EMPRESA else Tecnico.TIPO_TECNICO,
                    "rating" to 0.0,
                    "opiniones" to 0,
                    "proyectosCompletados" to 0,
                )
                db.collection("tecnicos").document(miUid)
                    .set(datos)
                    .addOnSuccessListener {
                        iniciarListenerPerfil(
                            Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                        )
                    }
                    .addOnFailureListener {
                        iniciarListenerPerfil(
                            Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                        )
                    }
            }
            .addOnFailureListener {
                crearPerfilMinimo(miUid)
            }
    }

    private fun crearPerfilMinimo(miUid: String) {
        val nombre = auth.currentUser?.displayName ?: ""
        val email = auth.currentUser?.email ?: ""
        val datos = mapOf(
            "id" to miUid,
            "uid" to miUid,
            "nombre" to nombre,
            "emailContacto" to email,
            "tipoProfesional" to Tecnico.TIPO_TECNICO,
            "rating" to 0.0,
            "opiniones" to 0,
            "proyectosCompletados" to 0,
        )
        db.collection("tecnicos").document(miUid)
            .set(datos)
            .addOnSuccessListener {
                iniciarListenerPerfil(
                    Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                )
            }
            .addOnFailureListener {
                iniciarListenerPerfil(
                    Tecnico(id = miUid, uid = miUid, nombre = nombre, emailContacto = email)
                )
            }
    }

    companion object { private const val TAG = "PanelTecnicoVM" }

    private fun iniciarListenerPerfil(tec: Tecnico?) {
        watchdog.removeCallbacksAndMessages(null)
        estado = estado.copy(tecnico = tec, cargando = false)

        listenerPerfil?.remove()
        listenerPerfil = repoMonetizacion.escucharMiPerfilProfesional { t ->
            if (t != null) estado = estado.copy(tecnico = t, cargando = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        watchdog.removeCallbacksAndMessages(null)
        listenerPerfil?.remove()
        listenerPerfil = null
    }
}
