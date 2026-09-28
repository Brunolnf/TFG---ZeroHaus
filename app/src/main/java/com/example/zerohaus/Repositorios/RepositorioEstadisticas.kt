package com.example.zerohaus.Repositorios

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.TimeZone

/** Contadores de un día (visitas al perfil y contactos). */
data class ContadoresDia(val visitas: Int = 0, val chats: Int = 0, val llamadas: Int = 0) {
    val contactos: Int get() = chats + llamadas
    operator fun plus(o: ContadoresDia) =
        ContadoresDia(visitas + o.visitas, chats + o.chats, llamadas + o.llamadas)
}

/** Agregado de /estadisticas/{tecnicoId}. `dias` usa claves yyyyMMdd (UTC). */
data class EstadisticasProfesional(val dias: Map<Int, ContadoresDia> = emptyMap()) {

    /** Suma de los últimos [n] días, hoy incluido. */
    fun ultimosDias(n: Int): ContadoresDia =
        serieDiaria(n).fold(ContadoresDia()) { acc, (_, c) -> acc + c }

    /** Una entrada por día (del más antiguo a hoy), con ceros donde no hubo actividad. */
    fun serieDiaria(n: Int): List<Pair<Int, ContadoresDia>> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1))
        return (0 until n).map {
            val clave = RepositorioEstadisticas.claveDia(cal)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            clave to (dias[clave] ?: ContadoresDia())
        }
    }
}

class RepositorioEstadisticas {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Registra una visita o un contacto en el perfil de un profesional.
     * Fire-and-forget: si ya existe el evento de hoy, las rules rechazan la
     * escritura (es la deduplicación) y se ignora sin molestar al usuario.
     */
    fun registrarEvento(tecnicoId: String, tipo: String) {
        val uid = auth.currentUser?.uid ?: return
        if (tecnicoId.isBlank() || tecnicoId == uid || tipo !in TIPOS) return
        val dia = claveDia(Calendar.getInstance(TimeZone.getTimeZone("UTC")))
        db.collection("tecnicos").document(tecnicoId)
            .collection("eventos").document("${uid}_${dia}_$tipo")
            .set(mapOf("dia" to dia, "tipo" to tipo))
    }

    fun escucharEstadisticas(tecnicoId: String, callback: (EstadisticasProfesional) -> Unit): ListenerRegistration =
        db.collection("estadisticas").document(tecnicoId)
            .addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                val dias = (snap?.get("dias") as? Map<String, Map<String, Number>>).orEmpty()
                    .mapNotNull { (clave, c) ->
                        clave.toIntOrNull()?.let {
                            it to ContadoresDia(
                                visitas = c["visita"]?.toInt() ?: 0,
                                chats = c["chat"]?.toInt() ?: 0,
                                llamadas = c["llamada"]?.toInt() ?: 0
                            )
                        }
                    }.toMap()
                callback(EstadisticasProfesional(dias))
            }

    companion object {
        const val VISITA = "visita"
        const val CHAT = "chat"
        const val LLAMADA = "llamada"
        private val TIPOS = setOf(VISITA, CHAT, LLAMADA)

        /** yyyyMMdd como entero; mismo formato que valida firestore.rules. */
        fun claveDia(cal: Calendar): Int =
            cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }
}
