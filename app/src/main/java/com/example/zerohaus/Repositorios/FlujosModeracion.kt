package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.ResultadoDenuncia

/** Resultado de una escritura que solo puede crear (las reglas no dejan sobrescribir). */
enum class ResultadoEscritura { OK, DENEGADO, FALLO }

/** Documento remoto mínimo que necesitan los flujos de moderación (aislado de Firestore para poder probarlos). */
interface DocumentoRemoto {
    /** true/false según exista en el servidor; null si la lectura falla (sin conexión...). */
    fun existeEnServidor(callback: (Boolean?) -> Unit)
    fun crear(datos: Map<String, Any>, callback: (ResultadoEscritura) -> Unit)
}

/** Origen de nombres de usuario (aislado de Firestore para poder probarlo). */
interface FuenteNombres {
    /** Nombres de los participantes de mis chats; null si la lectura falla. */
    fun deMisChats(callback: (Map<String, String>?) -> Unit)
    /** Nombre del perfil público de [uid]: éxito con null si no existe; fallo si la lectura falla. */
    fun dePerfil(uid: String, callback: (Result<String?>) -> Unit)
}

/** Flujos de denunciar, bloquear y resolver nombres. Cada callback se llama exactamente una vez. */
object FlujosModeracion {

    /**
     * Una denuncia por autor y objetivo. Lee del servidor (sin conexión no se
     * denuncia); si la escritura es denegada porque ya existe, es YA_DENUNCIADA.
     */
    fun denunciar(doc: DocumentoRemoto, datos: Map<String, Any>, callback: (ResultadoDenuncia) -> Unit) {
        doc.existeEnServidor { existe ->
            when (existe) {
                null -> callback(ResultadoDenuncia.ERROR)
                true -> callback(ResultadoDenuncia.YA_DENUNCIADA)
                false -> doc.crear(datos) { r ->
                    when (r) {
                        ResultadoEscritura.OK -> callback(ResultadoDenuncia.ENVIADA)
                        ResultadoEscritura.FALLO -> callback(ResultadoDenuncia.ERROR)
                        ResultadoEscritura.DENEGADO -> doc.existeEnServidor { ahora ->
                            callback(if (ahora == true) ResultadoDenuncia.YA_DENUNCIADA else ResultadoDenuncia.ERROR)
                        }
                    }
                }
            }
        }
    }

    /**
     * Lee antes del servidor (sin conexión falla enseguida en vez de esperar
     * para siempre). Si ya existe es éxito sin escribir (la regla solo deja crear).
     */
    fun bloquear(doc: DocumentoRemoto, datos: Map<String, Any>, callback: (Boolean) -> Unit) {
        doc.existeEnServidor { existe ->
            when (existe) {
                null -> callback(false)
                true -> callback(true)
                false -> doc.crear(datos) { r ->
                    when (r) {
                        ResultadoEscritura.OK -> callback(true)
                        ResultadoEscritura.FALLO -> callback(false)
                        ResultadoEscritura.DENEGADO -> doc.existeEnServidor { callback(it == true) }
                    }
                }
            }
        }
    }

    /**
     * Nombres de [uids]: primero los de mis chats, luego el perfil público. Una
     * lectura fallida no rompe nada: ese nombre no sale en el mapa. El callback
     * recibe además los uids cuya lectura de perfil falló por error (conviene reintentarlos).
     * Si [cancelado] pasa a true se dejan de lanzar lecturas y no se llama a [callback].
     */
    fun resolverNombres(
        uids: Set<String>,
        fuente: FuenteNombres,
        cancelado: () -> Boolean = { false },
        callback: (Map<String, String>, Set<String>) -> Unit
    ) {
        if (uids.isEmpty()) { callback(emptyMap(), emptySet()); return }
        val nombres = mutableMapOf<String, String>()
        val fallidos = mutableSetOf<String>()
        fuente.deMisChats { chats ->
            if (cancelado()) return@deMisChats
            chats?.forEach { (u, n) -> if (u in uids && n.isNotBlank()) nombres[u] = n }
            val faltan = uids - nombres.keys
            if (faltan.isEmpty()) { callback(nombres, fallidos); return@deMisChats }
            var pendientes = faltan.size
            faltan.forEach { u ->
                fuente.dePerfil(u) { r ->
                    val n = r.getOrNull()
                    if (r.isFailure) fallidos += u
                    else if (!n.isNullOrBlank()) nombres[u] = n
                    // También baja cuando la lectura falla
                    if (--pendientes == 0 && !cancelado()) callback(nombres, fallidos)
                }
            }
        }
    }
}
