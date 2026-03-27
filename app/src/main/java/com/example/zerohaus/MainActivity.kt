package com.example.zerohaus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.zerohaus.Navegacion.AppNavegacion
import com.example.zerohaus.ui.theme.ZeroHausTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroHausTheme {
                AppNavegacion()
            }
        }
    }
}