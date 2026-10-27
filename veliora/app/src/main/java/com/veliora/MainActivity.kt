package com.veliora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.veliora.ui.auth.ClerkAuthGate
import com.veliora.ui.navigation.VelioraNavHost
import com.veliora.ui.theme.VelioraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VelioraTheme {
                ClerkAuthGate {
                    VelioraNavHost()
                }
            }
        }
    }
}
