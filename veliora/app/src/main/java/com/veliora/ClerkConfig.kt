package com.veliora

/**
 * Clerk is the reference auth adapter, not a dependency on the maintainer's
 * Clerk application. Every build supplies its own publishable key and
 * Frontend API/JWKS URLs:
 *
 *   ./gradlew :app:assembleDebug \
 *     -PVELIORA_CLERK_PUBLISHABLE_KEY=pk_test_... \
 *     -PVELIORA_CLERK_ISSUER=https://clerk.your-domain.example \
 *     -PVELIORA_CLERK_JWKS_URL=https://clerk.your-domain.example/.well-known/jwks.json
 *
 * or export the same variables. Publishable keys are safe to ship; Clerk
 * secret keys (sk_...) must never be embedded in the app or committed.
 */
object ClerkConfig {
    val publishableKey: String
        get() = BuildConfig.CLERK_PUBLISHABLE_KEY.trim()

    val issuer: String
        get() = BuildConfig.CLERK_ISSUER.trim()

    val jwksUrl: String
        get() = BuildConfig.CLERK_JWKS_URL.trim()

    fun isConfigured(): Boolean =
        publishableKey.startsWith("pk_") &&
            !publishableKey.contains("placeholder") &&
            issuer.isNotBlank() &&
            jwksUrl.isNotBlank()
}
