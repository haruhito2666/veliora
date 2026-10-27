package com.veliora

/**
 * The Clerk publishable key is supplied at build time:
 *
 *   ./gradlew :app:assembleDebug -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_...
 *
 * or by exporting VELIORA_CLERK_PUBLISHABLE_KEY. Publishable keys are safe to
 * ship; Clerk secret keys (sk_...) must never be embedded in the app.
 */
object ClerkConfig {
    val publishableKey: String
        get() = BuildConfig.CLERK_PUBLISHABLE_KEY.trim()

    fun isConfigured(): Boolean =
        publishableKey.startsWith("pk_") && !publishableKey.contains("placeholder")
}
