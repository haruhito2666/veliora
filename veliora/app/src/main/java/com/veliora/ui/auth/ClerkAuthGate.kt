package com.veliora.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import com.veliora.ClerkConfig

/**
 * Holds the app behind Clerk's prebuilt sign-in/sign-up flow.
 *
 * Clerk is mandatory: there is no anonymous route. AuthView covers every
 * sign-in method enabled in the Clerk Dashboard. Once Clerk emits an active
 * session, the Veliora navigation graph is composed; signing out clears the
 * session and this gate returns to AuthView.
 */
@Composable
fun ClerkAuthGate(content: @Composable () -> Unit) {
    if (!ClerkConfig.isConfigured()) {
        ClerkConfigurationScreen()
        return
    }

    val initialized by Clerk.isInitialized.collectAsStateWithLifecycle()
    val initializationError by Clerk.initializationError.collectAsStateWithLifecycle()
    val session by Clerk.sessionFlow.collectAsStateWithLifecycle()

    when {
        !initialized && initializationError != null ->
            ClerkErrorScreen(initializationError?.message ?: "Clerk could not initialize")
        !initialized -> ClerkLoadingScreen()
        session == null -> AuthView(isDismissible = false)
        else -> content()
    }
}

@Composable
private fun ClerkLoadingScreen() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text("Connecting to Clerk…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ClerkErrorScreen(message: String) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Sign-in unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { Clerk.reinitialize() }) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ClerkConfigurationScreen() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Clerk is not configured",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Build the app with your Clerk publishable key:\n\n" +
                        "./gradlew :app:assembleDebug \\\n" +
                        "  -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_...",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "The publishable key is safe to include in the app. " +
                        "Never put a Clerk secret key (sk_...) in client code.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
