package com.veliora.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clerk.api.Clerk
import com.clerk.api.session.Session
import com.veliora.ClerkConfig
import com.veliora.app
import com.veliora.ui.theme.Slate600
import com.veliora.ui.theme.Slate900
import com.veliora.ui.theme.White

/**
 * Holds the app behind Clerk's authentication flow.
 *
 * Clerk is mandatory: there is no anonymous route. The custom screens cover Google OAuth and email
 * sign-up/sign-in, and only an ACTIVE Clerk session unlocks the Veliora graph. A pending session
 * cannot mint tokens, so it is treated as signed out.
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
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()

    val activeSession = session?.takeIf { it.status == Session.SessionStatus.ACTIVE }
    var wasAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(activeSession) {
        if (activeSession != null) {
            wasAuthenticated = true
        } else {
            // A missing/expired session must not leave the previous account's organization data
            // in the singleton store.
            context.app.orgStore.clear()
            if (wasAuthenticated) {
                // Signing out returns to a clean welcome screen instead of the last form.
                authViewModel.reset()
            }
        }
    }

    when {
        !initialized && initializationError != null ->
            ClerkErrorScreen(initializationError?.message ?: "Clerk could not initialize")

        !initialized -> ClerkLoadingScreen()

        activeSession == null -> ClerkAuthFlow(authViewModel)

        else -> content()
    }
}

@Composable
private fun ClerkLoadingScreen() {
    Surface(color = White) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = Slate900)
                Text(
                    text = "Connecting to Clerk…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                )
            }
        }
    }
}

@Composable
private fun ClerkErrorScreen(message: String) {
    Surface(color = White) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Sign-in unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    color = Slate900,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { Clerk.reinitialize() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Slate900,
                        contentColor = White,
                    ),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ClerkConfigurationScreen() {
    Surface(color = White) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Clerk is not configured",
                    style = MaterialTheme.typography.titleLarge,
                    color = Slate900,
                )
                Text(
                    text = "Build the app with your Clerk publishable key:\n\n" +
                        "./gradlew :app:assembleDebug \\\n" +
                        "  -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                )
                Text(
                    text = "The publishable key is safe to include in the app. " +
                        "Never put a Clerk secret key (sk_...) in client code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600,
                )
            }
        }
    }
}
