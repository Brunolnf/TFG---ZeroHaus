package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.DecisionDisputa
import com.example.zerohaus.Modelos.Denuncia
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PresentacionPagos
import com.example.zerohaus.Modelos.ResultadoPago
import com.example.zerohaus.Repositorios.RepositorioPagos
import com.example.zerohaus.Util.getCadenas
import com.example.zerohaus.Util.textoError
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAdmin
import com.example.zerohaus.Repositorios.RepositorioModeracion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * Lógica del panel de administración (ver [com.example.zerohaus.Repositorios.RepositorioAdmin]).
 */
class AdminViewModel : ViewModel() {

    private val repo = RepositorioAdmin()
    private val repoModeracion = RepositorioModeracion()
    private var listenerDenuncias: ListenerRegistration? = null

    val denuncias = mutableStateListOf<Denuncia>()
    val cargandoDenuncias = mutableStateOf(true)

    // Disputas de pagos (T-E8): el panel está en español fijo, también los errores
    private val repoPagos by lazy { RepositorioPagos() }
    private var jobDisputas: Job? = null
    val disputas = mutableStateListOf<Pago>()
    val cargandoDisputas = mutableStateOf(false)
    /** Pago cuya resolución está en curso (desactiva sus botones). */
    val resolviendo = mutableStateOf<String?>(null)

    val usuarios = mutableStateListOf<Usuario>()
    val cargando = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val mensaje = mutableStateOf<String?>(null)
    val filtro = mutableStateOf("")

    // Abrir el panel NUNCA borra nada automáticamente: con usuarios reales,
    // borrar por patrones de nombre eliminaría cuentas legítimas. Los borrados
    // son siempre una acción explícita y confirmada del admin.
    fun cargar() {
        cargando.value = true
        // Reparación idempotente: recrea el perfil /tecnicos de profesionales
        // cuya cuenta existe pero cuyo perfil falta (no borra nada).
        repo.restaurarTecnicosDesdeUsuarios { restaurados ->
            if (restaurados > 0) mensaje.value = "Restaurados $restaurados perfil(es) profesional(es) que faltaban"
            repo.listarUsuarios { lista ->
                usuarios.clear()
                usuarios.addAll(lista)
                cargando.value = false
            }
        }
    }

    /** Escucha las denuncias en tiempo real (más recientes primero). */
    fun escucharDenuncias() {
        if (listenerDenuncias != null) return
        cargandoDenuncias.value = true
        listenerDenuncias = repoModeracion.escucharDenuncias { lista ->
            cargandoDenuncias.value = false
            if (lista == null) {
                // El listener ha muerto: se suelta para poder volver a suscribirse
                listenerDenuncias?.remove(); listenerDenuncias = null
                // Al cerrar sesión también falla (permisos): no es un error que mostrar
                if (FirebaseAuth.getInstance().currentUser != null) error.value = "No se pudieron cargar las denuncias"
            } else { denuncias.clear(); denuncias.addAll(lista) }
        }
    }

    /** Escucha los pagos en disputa. Se ven siempre: resolver no depende del interruptor de pagos. */
    fun escucharDisputas() {
        if (jobDisputas?.isActive == true) return
        cargandoDisputas.value = true
        jobDisputas = viewModelScope.launch {
            repoPagos.pagosEnDisputa().collect { r ->
                cargandoDisputas.value = false
                when (r) {
                    is ResultadoPago.Ok -> { disputas.clear(); disputas.addAll(PresentacionPagos.disputas(r.valor)) }
                    is ResultadoPago.Error ->
                        if (FirebaseAuth.getInstance().currentUser != null) error.value = getCadenas("Español").textoError(r.error)
                }
            }
        }
    }

    /** Libera el dinero al profesional o lo reembolsa al cliente. El servidor comprueba que soy administrador. */
    fun resolverDisputa(pago: Pago, decision: DecisionDisputa) {
        if (resolviendo.value != null) return
        resolviendo.value = pago.id
        viewModelScope.launch {
            when (val r = repoPagos.resolver(pago.id, decision)) {
                is ResultadoPago.Ok -> mensaje.value = when (decision) {
                    DecisionDisputa.LIBERAR -> "Pago liberado al profesional"
                    DecisionDisputa.REEMBOLSAR -> "Pago reembolsado al cliente"
                }
                is ResultadoPago.Error -> error.value = getCadenas("Español").textoError(r.error)
            }
            resolviendo.value = null
        }
    }

    fun marcarDenuncia(denuncia: Denuncia, estado: String) {
        repoModeracion.marcarEstado(denuncia.id, estado) { ok ->
            if (ok) mensaje.value = "Denuncia $estado" else error.value = "No se pudo actualizar la denuncia"
        }
    }

    /** Bloquea la cuenta del denunciado con la function `bloquear_usuario`. */
    fun bloquearCuenta(uid: String) {
        repo.setBloqueado(uid, true) { result ->
            result
                .onSuccess { mensaje.value = "Cuenta bloqueada"; cargar() }
                .onFailure { error.value = it.message }
        }
    }

    /** Nombre del usuario si está en la lista cargada; si no, el uid abreviado. */
    fun nombreDe(uid: String): String =
        usuarios.firstOrNull { it.uid == uid }?.nombre?.takeIf { it.isNotBlank() }
            ?: uid.take(8).ifEmpty { "?" }

    override fun onCleared() {
        listenerDenuncias?.remove()
        jobDisputas?.cancel()
        super.onCleared()
    }

    fun usuariosFiltrados(): List<Usuario> {
        val q = filtro.value.trim().lowercase()
        if (q.isEmpty()) return usuarios
        return usuarios.filter {
            it.nombre.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }

    fun crear(nombre: String, email: String, password: String, tipo: String, onDone: () -> Unit) {
        cargando.value = true
        repo.crearUsuario(nombre, email, password, tipo) { result ->
            cargando.value = false
            result
                .onSuccess { mensaje.value = "Usuario creado correctamente"; cargar(); onDone() }
                .onFailure { error.value = it.message }
        }
    }

    fun actualizar(uid: String, nombre: String, tipo: String, onDone: () -> Unit) {
        cargando.value = true
        repo.actualizarUsuario(uid, nombre, tipo) { result ->
            cargando.value = false
            result
                .onSuccess { mensaje.value = "Cambios guardados"; cargar(); onDone() }
                .onFailure { error.value = it.message }
        }
    }

    fun toggleBloqueo(usuario: Usuario) {
        val nuevo = !usuario.bloqueado
        repo.setBloqueado(usuario.uid, nuevo) { result ->
            result
                .onSuccess { mensaje.value = if (nuevo) "Usuario bloqueado" else "Usuario desbloqueado"; cargar() }
                .onFailure { error.value = it.message }
        }
    }

    fun marcarEmailVerificado(usuario: Usuario) {
        repo.marcarEmailVerificado(usuario.uid) { result ->
            result
                .onSuccess { mensaje.value = "Email de ${usuario.email} marcado como verificado" }
                .onFailure { error.value = it.message }
        }
    }

    fun eliminar(usuario: Usuario) {
        cargando.value = true
        repo.eliminarUsuario(usuario.uid) { result ->
            cargando.value = false
            result
                .onSuccess { mensaje.value = "Usuario y todos sus datos eliminados"; cargar() }
                .onFailure { error.value = it.message }
        }
    }

    fun limpiarError() { error.value = null }
    fun limpiarMensaje() { mensaje.value = null }
}
