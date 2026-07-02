
package com.example.zerohaus.Modelos

data class Usuario(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val tipoUsuario: String = "Propietario",
    val fotoPerfil: String = "",
    val fechaRegistro: Long = System.currentTimeMillis(),
    // bloqueado: impide iniciar sesión (login lo comprueba y cierra sesión).
    // eliminado: el usuario fue borrado por el admin; el doc se mantiene
    //            como tombstone para que el email no pueda re-registrarse
    //            si la cuenta Auth sigue viva (huérfana).
    val bloqueado: Boolean = false,
    val eliminado: Boolean = false
)
