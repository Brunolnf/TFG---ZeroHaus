package com.example.zerohaus.Util

/** Un adjunto del chat solo se abre si está en la carpeta de chats de nuestro Storage. */
object AdjuntosChat {
    const val PREFIJO_SEGURO =
        "https://firebasestorage.googleapis.com/v0/b/zerohaus-2a865.firebasestorage.app/o/chats%2F"

    fun esUrlSegura(url: String?): Boolean = url != null && url.startsWith(PREFIJO_SEGURO)
}
