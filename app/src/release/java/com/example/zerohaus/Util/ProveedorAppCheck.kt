package com.example.zerohaus.Util

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** App Check en release: Play Integrity (la app instalada desde Google Play). */
object ProveedorAppCheck {
    val fabrica: AppCheckProviderFactory get() = PlayIntegrityAppCheckProviderFactory.getInstance()
}
