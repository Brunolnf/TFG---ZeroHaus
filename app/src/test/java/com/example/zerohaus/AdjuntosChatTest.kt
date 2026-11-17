package com.example.zerohaus

import com.example.zerohaus.Util.AdjuntosChat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjuntosChatTest {
    private val base = "https://firebasestorage.googleapis.com/v0/b/zerohaus-2a865.firebasestorage.app/o/"

    @Test fun aceptaArchivoDeChats() {
        assertTrue(AdjuntosChat.esUrlSegura(base + "chats%2Fabc%2Ffoto.jpg?alt=media&token=x"))
    }

    @Test fun rechazaOtraCarpeta() {
        assertFalse(AdjuntosChat.esUrlSegura(base + "informes%2Fa.pdf"))
    }

    @Test fun rechazaOtroBucketYEsquemas() {
        assertFalse(AdjuntosChat.esUrlSegura("https://firebasestorage.googleapis.com/v0/b/otro/o/chats%2Fa"))
        assertFalse(AdjuntosChat.esUrlSegura("http://firebasestorage.googleapis.com/v0/b/zerohaus-2a865.firebasestorage.app/o/chats%2Fa"))
        assertFalse(AdjuntosChat.esUrlSegura("file:///data/data/x"))
        assertFalse(AdjuntosChat.esUrlSegura("intent://x#Intent;end"))
        assertFalse(AdjuntosChat.esUrlSegura(""))
        assertFalse(AdjuntosChat.esUrlSegura(null))
    }

    @Test fun rechazaTrucosDeUrl() {
        assertFalse(AdjuntosChat.esUrlSegura("HTTPS://FIREBASESTORAGE.GOOGLEAPIS.COM/v0/b/zerohaus-2a865.firebasestorage.app/o/chats%2Fa"))
        assertFalse(AdjuntosChat.esUrlSegura("https://firebasestorage.googleapis.com@evil.com/v0/b/zerohaus-2a865.firebasestorage.app/o/chats%2Fa"))
        assertFalse(AdjuntosChat.esUrlSegura(" " + base + "chats%2Fa"))
        assertFalse(AdjuntosChat.esUrlSegura("file://" + base + "chats%2Fa"))
        assertFalse(AdjuntosChat.esUrlSegura("content://com.example/chats%2Fa"))
    }

    @Test fun rechazaDominioEngañoso() {
        assertFalse(AdjuntosChat.esUrlSegura("https://firebasestorage.googleapis.com.evil.com/v0/b/zerohaus-2a865.firebasestorage.app/o/chats%2Fa"))
    }
}
