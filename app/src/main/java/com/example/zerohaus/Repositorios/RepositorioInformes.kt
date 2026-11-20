package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Util.EstadoGuardado
import com.example.zerohaus.Util.Lectura
import com.example.zerohaus.Util.getConOrigen
import com.example.zerohaus.Util.getOrTimeout
import com.example.zerohaus.Util.guardarConTope
import com.example.zerohaus.Util.mapear
import com.example.zerohaus.Util.programadorPrincipal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Informe guardado; [pendienteSync] si el servidor aún no ha confirmado el guardado (sin conexión). */
data class InformeGuardado(val informe: InformeEnergetico, val pendienteSync: Boolean)

/** Espera máxima a la confirmación del servidor al guardar vivienda + informe. */
const val TOPE_GUARDADO_MS = 8000L

/**
 * Informes energéticos: los genera con el algoritmo, los guarda en
 * `/informes` y lee el historial y los consejos de IA ya generados.
 */
class RepositorioInformes {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    /** Reserva el id de un informe nuevo sin escribir nada (para que un reintento lo reutilice). */
    fun reservarId(): String = db.collection("informes").document().id

    private fun construirInforme(id: String, vivienda: Vivienda): InformeEnergetico {
        val resultado = AlgoritmoEnergetico.calcular(vivienda)
        return InformeEnergetico(
            id = id,
            viviendaId = vivienda.id,
            uid = uid(),
            nombreVivienda = vivienda.nombre,
            etiqueta = resultado.etiqueta,
            estadoEficiencia = resultado.estadoEficiencia,
            consumoEstimado = resultado.consumoEstimado,
            consumoPorM2 = resultado.consumoPorM2,
            energiaPrimariaM2 = resultado.energiaPrimariaM2,
            emisiones = resultado.emisiones,
            costeAnual = resultado.costeAnual,
            recomendaciones = resultado.recomendaciones,
            precioLuz = resultado.precioLuz,
            consumoLuzEstimado = resultado.consumoLuzEstimado,
            consumoLuzFactura = vivienda.consumoLuzFacturaKwh
        )
    }

    /**
     * Genera un informe energético basado en los datos de la vivienda.
     * Usa un algoritmo simplificado de cálculo energético.
     */
    fun generarInforme(vivienda: Vivienda, callback: (Result<InformeEnergetico>) -> Unit) {
        val ref = db.collection("informes").document()
        val informe = construirInforme(ref.id, vivienda)

        ref.set(informe)
            .addOnSuccessListener { callback(Result.success(informe)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error generando informe")))
            }
    }

    /**
     * Guarda la vivienda y su informe en un WriteBatch: o se escriben los dos o
     * ninguno, así que un fallo del servidor no deja un informe sin su vivienda.
     * [vivienda] y [informeId] vienen de ids reservados antes con `document()`:
     * un reintento escribe los mismos documentos con `set`, así que no duplica nada.
     * Con [guardarVivienda] = false (vivienda existente sin cambios) solo se escribe el informe.
     *
     * Sin conexión el batch no se confirma hasta reconectar. Decisión: se espera
     * [TOPE_GUARDADO_MS]; si no hay respuesta, Firestore ya lo tiene en su cola
     * local (se aplica en la caché al momento y se envía entero, en orden, al
     * reconectar, incluso si la app se reinicia), así que se devuelve el informe
     * con `pendienteSync = true` y la UI avisa «guardado pendiente de confirmar».
     * Si el servidor lo rechazase luego, ambos cambios se revierten juntos y
     * [alFalloTardio] avisa para que la UI lo diga.
     */
    fun guardarViviendaEInforme(
        vivienda: Vivienda,
        guardarVivienda: Boolean,
        informeId: String,
        topeMs: Long = TOPE_GUARDADO_MS,
        alFalloTardio: (Throwable) -> Unit = {},
        callback: (Result<InformeGuardado>) -> Unit
    ) {
        val v = vivienda.copy(uid = uid())
        val informe = construirInforme(informeId, v)
        val batch = db.batch()
        if (guardarVivienda) batch.set(db.collection("viviendas").document(v.id), v)
        batch.set(db.collection("informes").document(informeId), informe)

        guardarConTope(
            topeMs, programadorPrincipal(),
            commit = { fin ->
                batch.commit()
                    .addOnSuccessListener { fin(Result.success(Unit)) }
                    .addOnFailureListener { e -> fin(Result.failure(Exception(e.message ?: "Error guardando"))) }
            },
            alResultado = { estado ->
                callback(when (estado) {
                    EstadoGuardado.Confirmado -> Result.success(InformeGuardado(informe, pendienteSync = false))
                    EstadoGuardado.PendienteDeConfirmar -> Result.success(InformeGuardado(informe, pendienteSync = true))
                    is EstadoGuardado.Fallo -> Result.failure(estado.causa)
                })
            },
            alFalloTardio = alFalloTardio
        )
    }

    /**
     * Último informe del usuario. Usa el índice `uid` + `fechaGeneracion` DESC con
     * `limit(1)`; si viene vacío, la consulta antigua (sin orden) como respaldo por
     * si hay documentos viejos sin `fechaGeneracion`, que `orderBy` deja fuera.
     */
    fun obtenerUltimoInforme(callback: (InformeEnergetico?) -> Unit) {
        db.collection("informes")
            .whereEqualTo("uid", uid())
            .orderBy("fechaGeneracion", Query.Direction.DESCENDING)
            .limit(1)
            .getOrTimeout { snap ->
                val ultimo = snap?.documents?.firstNotNullOfOrNull { it.toObject(InformeEnergetico::class.java) }
                if (snap == null || ultimo != null) {
                    callback(ultimo)
                } else {
                    db.collection("informes")
                        .whereEqualTo("uid", uid())
                        .getOrTimeout { todos ->
                            callback(todos?.documents
                                ?.mapNotNull { it.toObject(InformeEnergetico::class.java) }
                                ?.maxByOrNull { it.fechaGeneracion })
                        }
                }
            }
    }

    /**
     * Informes del usuario, del más reciente al más antiguo, indicando si son del
     * servidor, de la caché o si la lectura falló (ver [Lectura]).
     */
    fun obtenerInformesConOrigen(callback: (Lectura<List<InformeEnergetico>>) -> Unit) {
        db.collection("informes")
            .whereEqualTo("uid", uid())
            .getConOrigen { lectura ->
                callback(lectura.mapear { snap ->
                    snap.documents
                        .mapNotNull { it.toObject(InformeEnergetico::class.java) }
                        .sortedByDescending { it.fechaGeneracion }
                })
            }
    }

    /** Consejos de IA ya guardados en el informe, solo si están en [idioma]. */
    fun obtenerSugerenciasGuardadas(informeId: String, idioma: String, callback: (SugerenciasIA?) -> Unit) {
        db.collection("informes").document(informeId).getOrTimeout { snap ->
            @Suppress("UNCHECKED_CAST")
            val m = snap?.get("sugerenciasIA") as? Map<String, Any?>
            callback(m?.takeIf { it["idioma"] == idioma }?.let { RepositorioIA.parsear(it) })
        }
    }

    fun eliminarInforme(informeId: String, callback: (Boolean) -> Unit) {
        db.collection("informes").document(informeId)
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }
}
