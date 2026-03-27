
package com.example.zerohaus.Modelos

data class MensajeChat(
    val id: String = "",
    val chatId: String = "",
    val emisorUid: String = "",
    val emisorNombre: String = "",
    val texto: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val leido: Boolean = false
)

data class Chat(
    val id: String = "",
    val participantes: List<String> = emptyList(),
    val nombresParticipantes: Map<String, String> = emptyMap(),
    val ultimoMensaje: String = "",
    val fechaUltimoMensaje: Long = 0,
    val noLeidosPor: Map<String, Int> = emptyMap()
)