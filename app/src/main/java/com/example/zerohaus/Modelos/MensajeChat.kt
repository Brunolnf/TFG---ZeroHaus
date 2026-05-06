package com.example.zerohaus.Modelos

data class MensajeChat(
    val id: String = "",
    val chatId: String = "",
    val emisorUid: String = "",
    val emisorNombre: String = "",
    val texto: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val leido: Boolean = false,
    val tipo: String = "texto",
    val mediaUrl: String = "",
    val mediaNombre: String = "",
    val mediaBytes: Long = 0L
)

data class Chat(
    val id: String = "",
    val participantes: List<String> = emptyList(),
    val nombresParticipantes: Map<String, String> = emptyMap(),
    val ultimoMensaje: String = "",
    val fechaUltimoMensaje: Long = 0L,
    val noLeidosPor: Map<String, Int> = emptyMap()
) {
    fun tieneNoLeidos(miUid: String): Boolean {
        return (noLeidosPor[miUid] ?: 0) > 0
    }
}
