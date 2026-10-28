package com.veliora.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.auth.types.MfaType
import com.clerk.api.auth.types.Strategy
import com.clerk.api.auth.types.VerificationType
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.network.serialization.errorMessage
import com.clerk.api.session.Session
import com.clerk.api.signin.SignIn
import com.clerk.api.signin.sendCode
import com.clerk.api.signin.sendMfaEmailCode
import com.clerk.api.signin.verifyCode
import com.clerk.api.signin.verifyMfaCode
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.isEmailLinkVerificationSupported
import com.clerk.api.signup.sendEmailCode
import com.clerk.api.signup.update
import com.clerk.api.signup.verifyCode
import com.clerk.api.sso.OAuthProvider
import com.clerk.api.sso.OAuthResult
import com.clerk.api.sso.SSOCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The screens owned by the custom Clerk authentication flow. */
enum class AuthScreen {
    WELCOME,
    SIGN_UP_CHOICE,
    SIGN_UP_EMAIL,
    SIGN_IN_CHOICE,
    SIGN_IN_EMAIL,
    COMPLETE_PROFILE,
    VERIFY,
}

/** Which Clerk verification the code screen is currently completing. */
enum class CodeKind {
    SIGN_UP_EMAIL,
    SIGN_IN_FIRST_FACTOR_EMAIL,
    SIGN_IN_MFA_EMAIL,
    SIGN_IN_MFA_TOTP,
    SIGN_IN_MFA_BACKUP,
}

data class AuthUiState(
    val screen: AuthScreen = AuthScreen.WELCOME,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val codeKind: CodeKind? = null,
    val codeDestination: String = "",
    val canResend: Boolean = false,
    val missingProfileFields: List<String> = emptyList(),
    val returnScreen: AuthScreen = AuthScreen.WELCOME,
)

/**
 * Drives the custom Clerk screens (the reference auth adapter).
 *
 * Every method calls the real Clerk SDK. A completed sign-in or sign-up activates the created
 * session, which makes `Clerk.activeSession` available to the embedded Sofuu gateway. A fork using
 * another provider replaces this view model and [ClerkAuthFlow]; the gateway contract is unchanged.
 */
class AuthViewModel : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var pendingSignUp: SignUp? = null
    private var pendingSignIn: SignIn? = null

    fun reset() {
        pendingSignUp = null
        pendingSignIn = null
        _state.value = AuthUiState()
    }

    fun dismissMessage() {
        _state.update { it.copy(error = null, info = null) }
    }

    fun openSignUp() {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                screen = AuthScreen.SIGN_UP_CHOICE,
                error = null,
                info = null,
                codeKind = null,
                missingProfileFields = emptyList(),
            )
        }
    }

    fun openSignIn() {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                screen = AuthScreen.SIGN_IN_CHOICE,
                error = null,
                info = null,
                codeKind = null,
                missingProfileFields = emptyList(),
            )
        }
    }

    fun openSignUpEmail() {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                screen = AuthScreen.SIGN_UP_EMAIL,
                error = null,
                info = null,
                missingProfileFields = emptyList(),
            )
        }
    }

    fun openSignInEmail() {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                screen = AuthScreen.SIGN_IN_EMAIL,
                error = null,
                info = null,
                missingProfileFields = emptyList(),
            )
        }
    }

    fun goBack() {
        val current = _state.value
        if (current.busy) return
        val next = when (current.screen) {
            AuthScreen.WELCOME -> AuthScreen.WELCOME
            AuthScreen.SIGN_UP_CHOICE,
            AuthScreen.SIGN_IN_CHOICE,
            -> AuthScreen.WELCOME
            AuthScreen.SIGN_UP_EMAIL -> AuthScreen.SIGN_UP_CHOICE
            AuthScreen.SIGN_IN_EMAIL -> AuthScreen.SIGN_IN_CHOICE
            AuthScreen.COMPLETE_PROFILE -> AuthScreen.SIGN_UP_CHOICE
            AuthScreen.VERIFY -> current.returnScreen
        }
        _state.update {
            it.copy(
                screen = next,
                error = null,
                info = null,
                codeKind = if (next == AuthScreen.VERIFY) it.codeKind else null,
                missingProfileFields = if (next == AuthScreen.COMPLETE_PROFILE) {
                    it.missingProfileFields
                } else {
                    emptyList()
                },
            )
        }
    }

    // region Google

    fun continueWithGoogle(signUp: Boolean) {
        if (_state.value.busy) return
        launchAuth {
            clearPendingSession()
            val result =
                if (signUp) {
                    Clerk.auth.signUpWithOAuth(OAuthProvider.GOOGLE)
                } else {
                    Clerk.auth.signInWithOAuth(
                        provider = OAuthProvider.GOOGLE,
                        transferable = true,
                    )
                }
            when (result) {
                is ClerkResult.Success -> handleOAuthResult(result.value)
                is ClerkResult.Failure -> handleFailure(result)
            }
        }
    }

    private suspend fun handleOAuthResult(result: OAuthResult) {
        when (val outcome = result.outcome) {
            is OAuthResult.Outcome.SignIn ->
                handleSignIn(outcome.signIn, outcome.signIn.identifier.orEmpty())
            is OAuthResult.Outcome.SignUp ->
                handleSignUp(outcome.signUp)
            OAuthResult.Outcome.Empty ->
                fail("Google did not return an account. Please try again.")
        }
    }

    // endregion

    // region Email sign-up

    fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        password: String,
    ) {
        if (_state.value.busy) return
        val cleanEmail = email.trim()
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        val cleanUsername = username.trim()

        validateEmail(cleanEmail)?.let {
            fail(it)
            return
        }
        if (Clerk.isFirstNameEnabled && cleanFirst.isBlank()) {
            fail("Enter your first name.")
            return
        }
        if (Clerk.isLastNameEnabled && cleanLast.isBlank()) {
            fail("Enter your last name.")
            return
        }
        if (Clerk.isUserNameEnabled && cleanUsername.isBlank()) {
            fail("Choose a username.")
            return
        }
        if (Clerk.passwordIsEnabled) {
            if (password.length < MIN_PASSWORD_LENGTH) {
                fail("Use at least $MIN_PASSWORD_LENGTH characters for your password.")
                return
            }
        }

        launchAuth {
            clearPendingSession()
            val result = Clerk.auth.signUp {
                this.email = cleanEmail
                if (Clerk.passwordIsEnabled) this.password = password
                if (Clerk.isFirstNameEnabled && cleanFirst.isNotBlank()) {
                    this.firstName = cleanFirst
                }
                if (Clerk.isLastNameEnabled && cleanLast.isNotBlank()) {
                    this.lastName = cleanLast
                }
                if (Clerk.isUserNameEnabled && cleanUsername.isNotBlank()) {
                    this.username = cleanUsername
                }
                // The form states that continuing accepts the terms. Sending the flag also
                // satisfies instances that require legal consent at sign-up.
                this.legalAccepted = true
            }
            when (result) {
                is ClerkResult.Success -> handleSignUp(result.value)
                is ClerkResult.Failure -> handleFailure(result)
            }
        }
    }

    fun completeProfile(
        password: String,
        firstName: String,
        lastName: String,
        username: String,
    ) {
        val current = _state.value
        if (current.busy) return
        val fields = current.missingProfileFields
        val signUp = pendingSignUp
        if (signUp == null || fields.isEmpty()) {
            fail("This sign-up expired. Start again.")
            return
        }
        if ("password" in fields && password.length < MIN_PASSWORD_LENGTH) {
            fail("Use at least $MIN_PASSWORD_LENGTH characters for your password.")
            return
        }
        if ("first_name" in fields && firstName.isBlank()) {
            fail("Enter your first name.")
            return
        }
        if ("last_name" in fields && lastName.isBlank()) {
            fail("Enter your last name.")
            return
        }
        if ("username" in fields && username.isBlank()) {
            fail("Choose a username.")
            return
        }

        launchAuth {
            val updated = signUp.update {
                if ("password" in fields) this.password = password
                if ("first_name" in fields) this.firstName = firstName.trim()
                if ("last_name" in fields) this.lastName = lastName.trim()
                if ("username" in fields) this.username = username.trim()
            }
            when (updated) {
                is ClerkResult.Success -> handleSignUp(updated.value)
                is ClerkResult.Failure -> handleFailure(updated)
            }
        }
    }

    private suspend fun handleSignUp(signUp: SignUp) {
        pendingSignUp = signUp
        pendingSignIn = null

        when (signUp.status) {
            SignUp.Status.COMPLETE -> {
                completeSession(signUp.createdSessionId)
            }

            SignUp.Status.MISSING_REQUIREMENTS -> {
                val legalMissing = signUp.missingFields.contains(LEGAL_ACCEPTED)
                val emailUnverified = signUp.unverifiedFields.contains(EMAIL_ADDRESS)
                val knownMissing = signUp.missingFields.filterNot { it == LEGAL_ACCEPTED }
                val collectible = knownMissing.filter { it in PROFILE_FIELDS }

                when {
                    legalMissing -> {
                        val updated = signUp.update { legalAccepted = true }
                        when (updated) {
                            is ClerkResult.Success -> handleSignUp(updated.value)
                            is ClerkResult.Failure -> handleFailure(updated)
                        }
                    }

                    collectible.isNotEmpty() -> {
                        _state.value = _state.value.copy(
                            screen = AuthScreen.COMPLETE_PROFILE,
                            busy = false,
                            error = null,
                            info = null,
                            codeKind = null,
                            missingProfileFields = collectible,
                            returnScreen = AuthScreen.SIGN_UP_CHOICE,
                        )
                    }

                    emailUnverified -> requestSignUpCode(signUp)

                    knownMissing.isNotEmpty() -> {
                        fail(
                            "Clerk still needs ${knownMissing.toFieldList()}. " +
                                "Add ${if (knownMissing.size == 1) "that field" else "those fields"} " +
                                "in the Clerk Dashboard or a supported form.",
                        )
                    }

                    signUp.unverifiedFields.isNotEmpty() -> {
                        fail(
                            "This account needs ${signUp.unverifiedFields.toFieldList()} " +
                                "to be verified. That verification is not supported on this screen.",
                        )
                    }

                    else -> fail("Sign-up is not complete yet. Please try again.")
                }
            }

            SignUp.Status.ABANDONED ->
                fail("This sign-up expired. Start again.")

            SignUp.Status.UNKNOWN ->
                fail("Clerk returned an unknown sign-up state. Please try again.")
        }
    }

    private suspend fun requestSignUpCode(signUp: SignUp) {
        val result = signUp.sendEmailCode()
        when (result) {
            is ClerkResult.Success -> {
                if (result.value.status == SignUp.Status.COMPLETE) {
                    completeSession(result.value.createdSessionId)
                } else {
                    pendingSignUp = result.value
                    val destination = result.value.emailAddress.orEmpty()
                    val returnScreen = when (_state.value.screen) {
                        AuthScreen.SIGN_UP_EMAIL -> AuthScreen.SIGN_UP_EMAIL
                        AuthScreen.SIGN_IN_CHOICE -> AuthScreen.SIGN_IN_CHOICE
                        else -> AuthScreen.SIGN_UP_CHOICE
                    }
                    _state.value = _state.value.copy(
                        screen = AuthScreen.VERIFY,
                        busy = false,
                        error = null,
                        info = null,
                        codeKind = CodeKind.SIGN_UP_EMAIL,
                        codeDestination = destination,
                        canResend = true,
                        missingProfileFields = emptyList(),
                        returnScreen = returnScreen,
                    )
                }
            }

            is ClerkResult.Failure -> {
                if (signUp.isEmailLinkVerificationSupported) {
                    fail(
                        "This Clerk instance sends email links instead of verification codes. " +
                            "Enable Email verification code in the Clerk Dashboard.",
                    )
                } else {
                    handleFailure(result)
                }
            }
        }
    }

    // endregion

    // region Email sign-in

    fun signIn(email: String, password: String) {
        if (_state.value.busy) return
        val cleanEmail = email.trim()
        validateEmail(cleanEmail)?.let {
            fail(it)
            return
        }
        if (Clerk.passwordIsEnabled && password.isBlank()) {
            fail("Enter your password.")
            return
        }

        launchAuth {
            clearPendingSession()
            val result = Clerk.auth.signInWithPassword {
                identifier = cleanEmail
                this.password = password
            }
            when (result) {
                is ClerkResult.Success -> handleSignIn(result.value, cleanEmail)
                is ClerkResult.Failure -> handleFailure(result)
            }
        }
    }

    fun signInWithEmailCode(email: String) {
        if (_state.value.busy) return
        val cleanEmail = email.trim()
        validateEmail(cleanEmail)?.let {
            fail(it)
            return
        }

        launchAuth {
            clearPendingSession()
            val result = Clerk.auth.signInWithOtp { this.email = cleanEmail }
            when (result) {
                is ClerkResult.Success -> {
                    if (result.value.status == SignIn.Status.COMPLETE) {
                        completeSession(result.value.createdSessionId)
                    } else {
                        pendingSignIn = result.value
                        showSignInCode(
                            signIn = result.value,
                            kind = CodeKind.SIGN_IN_FIRST_FACTOR_EMAIL,
                            destination = cleanEmail,
                            canResend = true,
                            returnScreen = AuthScreen.SIGN_IN_EMAIL,
                        )
                    }
                }

                is ClerkResult.Failure -> handleFailure(result)
            }
        }
    }

    private suspend fun handleSignIn(signIn: SignIn, identifier: String) {
        pendingSignIn = signIn
        pendingSignUp = null

        when (signIn.status) {
            SignIn.Status.COMPLETE -> completeSession(signIn.createdSessionId)

            SignIn.Status.NEEDS_FIRST_FACTOR -> {
                val emailFactor =
                    signIn.supportedFirstFactors.orEmpty().any {
                        it.strategyType == Strategy.EmailCode
                    }
                if (emailFactor) {
                    val result = signIn.sendCode {
                        email = signIn.identifier?.takeIf { it.isNotBlank() } ?: identifier
                    }
                    when (result) {
                        is ClerkResult.Success -> {
                            if (result.value.status == SignIn.Status.COMPLETE) {
                                completeSession(result.value.createdSessionId)
                            } else {
                                showSignInCode(
                                    signIn = result.value,
                                    kind = CodeKind.SIGN_IN_FIRST_FACTOR_EMAIL,
                                    destination = identifier,
                                    canResend = true,
                                    returnScreen = AuthScreen.SIGN_IN_EMAIL,
                                )
                            }
                        }

                        is ClerkResult.Failure -> handleFailure(result)
                    }
                } else {
                    fail(
                        "This account needs a verification method that is not available on this " +
                            "screen. Try Google or contact support.",
                    )
                }
            }

            SignIn.Status.NEEDS_SECOND_FACTOR -> requestSecondFactor(signIn, identifier)

            SignIn.Status.NEEDS_NEW_PASSWORD ->
                fail("Your account needs a new password before you can sign in.")

            SignIn.Status.NEEDS_CLIENT_TRUST ->
                fail("Clerk needs to verify this device before you can sign in.")

            SignIn.Status.NEEDS_IDENTIFIER ->
                fail("Enter your email and password again.")

            SignIn.Status.UNKNOWN ->
                fail("Clerk returned an unknown sign-in state. Please try again.")
        }
    }

    private suspend fun requestSecondFactor(signIn: SignIn, identifier: String) {
        val factors = signIn.supportedSecondFactors.orEmpty()
        val emailFactor = factors.firstOrNull { it.strategyType == Strategy.EmailCode }
        val hasTotp = factors.any { it.strategyType == Strategy.Totp }
        val hasBackup = factors.any { it.strategyType == Strategy.BackupCode }

        when {
            emailFactor != null -> {
                val result = signIn.sendMfaEmailCode(emailFactor.emailAddressId)
                when (result) {
                    is ClerkResult.Success -> {
                        if (result.value.status == SignIn.Status.COMPLETE) {
                            completeSession(result.value.createdSessionId)
                        } else {
                            showSignInCode(
                                signIn = result.value,
                                kind = CodeKind.SIGN_IN_MFA_EMAIL,
                                destination = emailFactor.safeIdentifier ?: identifier,
                                canResend = true,
                                returnScreen = AuthScreen.SIGN_IN_EMAIL,
                            )
                        }
                    }

                    is ClerkResult.Failure -> handleFailure(result)
                }
            }

            hasTotp -> showSignInCode(
                signIn = signIn,
                kind = CodeKind.SIGN_IN_MFA_TOTP,
                destination = "",
                canResend = false,
                returnScreen = AuthScreen.SIGN_IN_EMAIL,
            )

            hasBackup -> showSignInCode(
                signIn = signIn,
                kind = CodeKind.SIGN_IN_MFA_BACKUP,
                destination = "",
                canResend = false,
                returnScreen = AuthScreen.SIGN_IN_EMAIL,
            )

            else -> fail(
                "This account needs a second verification method that is not available on this " +
                    "screen.",
            )
        }
    }

    private fun showSignInCode(
        signIn: SignIn,
        kind: CodeKind,
        destination: String,
        canResend: Boolean,
        returnScreen: AuthScreen,
    ) {
        pendingSignIn = signIn
        _state.value = _state.value.copy(
            screen = AuthScreen.VERIFY,
            busy = false,
            error = null,
            info = null,
            codeKind = kind,
            codeDestination = destination,
            canResend = canResend,
            missingProfileFields = emptyList(),
            returnScreen = returnScreen,
        )
    }

    // endregion

    // region Verification

    fun verifyCode(code: String) {
        val current = _state.value
        if (current.busy) return
        val kind = current.codeKind ?: return
        val clean = code.trim()

        if (kind != CodeKind.SIGN_IN_MFA_BACKUP && clean.length != CODE_LENGTH) {
            fail("Enter the $CODE_LENGTH-digit code.")
            return
        }
        if (kind == CodeKind.SIGN_IN_MFA_BACKUP && clean.isBlank()) {
            fail("Enter one of your backup codes.")
            return
        }

        launchAuth {
            when (kind) {
                CodeKind.SIGN_UP_EMAIL -> {
                    val signUp = pendingSignUp
                    if (signUp == null) {
                        fail("This sign-up expired. Start again.")
                    } else {
                        when (val result = signUp.verifyCode(clean, VerificationType.EMAIL)) {
                            is ClerkResult.Success -> handleSignUp(result.value)
                            is ClerkResult.Failure -> handleFailure(result)
                        }
                    }
                }

                CodeKind.SIGN_IN_FIRST_FACTOR_EMAIL -> {
                    val signIn = pendingSignIn
                    if (signIn == null) {
                        fail("This sign-in expired. Start again.")
                    } else {
                        when (val result = signIn.verifyCode(clean)) {
                            is ClerkResult.Success -> {
                                handleSignIn(result.value, current.codeDestination)
                            }

                            is ClerkResult.Failure -> handleFailure(result)
                        }
                    }
                }

                CodeKind.SIGN_IN_MFA_EMAIL -> verifyMfa(MfaType.EMAIL_CODE, clean)
                CodeKind.SIGN_IN_MFA_TOTP -> verifyMfa(MfaType.TOTP, clean)
                CodeKind.SIGN_IN_MFA_BACKUP -> verifyMfa(MfaType.BACKUP_CODE, clean)
            }
        }
    }

    private suspend fun verifyMfa(type: MfaType, code: String) {
        val signIn = pendingSignIn
        if (signIn == null) {
            fail("This sign-in expired. Start again.")
            return
        }
        when (val result = signIn.verifyMfaCode(code, type)) {
            is ClerkResult.Success ->
                handleSignIn(result.value, _state.value.codeDestination)
            is ClerkResult.Failure -> handleFailure(result)
        }
    }

    fun resendCode() {
        val current = _state.value
        if (current.busy || !current.canResend) return
        val kind = current.codeKind ?: return
        launchAuth {
            when (kind) {
                CodeKind.SIGN_UP_EMAIL -> {
                    val signUp = pendingSignUp
                    if (signUp == null) {
                        fail("This sign-up expired. Start again.")
                        return@launchAuth
                    }
                    when (val result = signUp.sendEmailCode()) {
                        is ClerkResult.Success -> {
                            pendingSignUp = result.value
                            _state.update {
                                it.copy(
                                    busy = false,
                                    error = null,
                                    info = "A new code was sent to " +
                                        (result.value.emailAddress ?: current.codeDestination) +
                                        ".",
                                )
                            }
                        }

                        is ClerkResult.Failure -> handleFailure(result)
                    }
                }

                CodeKind.SIGN_IN_FIRST_FACTOR_EMAIL -> {
                    val signIn = pendingSignIn
                    if (signIn == null) {
                        fail("This sign-in expired. Start again.")
                        return@launchAuth
                    }
                    when (
                        val result = signIn.sendCode {
                            email = current.codeDestination.takeIf { it.isNotBlank() }
                        }
                    ) {
                        is ClerkResult.Success -> {
                            pendingSignIn = result.value
                            _state.update {
                                it.copy(
                                    busy = false,
                                    error = null,
                                    info = "A new code was sent to ${current.codeDestination}.",
                                )
                            }
                        }

                        is ClerkResult.Failure -> handleFailure(result)
                    }
                }

                CodeKind.SIGN_IN_MFA_EMAIL -> {
                    val signIn = pendingSignIn
                    if (signIn == null) {
                        fail("This sign-in expired. Start again.")
                        return@launchAuth
                    }
                    val emailId = signIn.supportedSecondFactors
                        .orEmpty()
                        .firstOrNull { it.strategyType == Strategy.EmailCode }
                        ?.emailAddressId
                    when (val result = signIn.sendMfaEmailCode(emailId)) {
                        is ClerkResult.Success -> {
                            pendingSignIn = result.value
                            _state.update {
                                it.copy(
                                    busy = false,
                                    error = null,
                                    info = "A new code was sent to ${current.codeDestination}.",
                                )
                            }
                        }

                        is ClerkResult.Failure -> handleFailure(result)
                    }
                }

                CodeKind.SIGN_IN_MFA_TOTP,
                CodeKind.SIGN_IN_MFA_BACKUP,
                -> {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    // endregion

    // region Session

    private suspend fun completeSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            fail("Clerk did not return a session. Please try again.")
            return
        }
        if (Clerk.activeSession?.id == sessionId) {
            _state.update { it.copy(busy = false, error = null) }
            return
        }

        var activationError: String? = null
        when (val result = Clerk.auth.setActive(sessionId)) {
            is ClerkResult.Success -> Unit
            is ClerkResult.Failure -> activationError = result.errorMessage
        }

        if (Clerk.activeSession?.id != sessionId) {
            // A client refresh resolves the read-after-write gap when the session exists but the
            // local client has not observed `last_active_session_id` yet.
            Clerk.refreshClient()
        }

        when {
            Clerk.activeSession?.id == sessionId ->
                _state.update { it.copy(busy = false, error = null) }

            Clerk.session?.id == sessionId -> {
                // The session exists but is PENDING: it cannot mint the token the embedded gateway
                // needs, and these custom screens cannot complete Clerk session tasks.
                Clerk.auth.signOut(sessionId)
                fail(
                    "Your account needs an additional Clerk verification step that this app " +
                        "does not support yet. Ask the workspace owner to finish the setup.",
                )
            }

            else -> fail(
                activationError ?: "Clerk could not activate the session. Please try again.",
            )
        }
    }

    private suspend fun clearPendingSession() {
        val session = Clerk.session ?: return
        if (session.status != Session.SessionStatus.ACTIVE) {
            // Custom screens cannot complete Clerk session tasks, and a pending session cannot
            // mint the token the embedded gateway needs. Clear it before starting a fresh flow.
            Clerk.auth.signOut(session.id)
        }
    }

    // endregion

    // region Helpers

    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, info = null) }
            try {
                block()
            } catch (e: SSOCancellationException) {
                _state.update { it.copy(busy = false, error = null) }
            } catch (e: Exception) {
                fail(e.message ?: "Authentication failed. Please try again.")
            }
        }
    }

    private fun handleFailure(failure: ClerkResult.Failure<ClerkErrorResponse>) {
        if (failure.throwable is SSOCancellationException) {
            _state.update { it.copy(busy = false, error = null) }
        } else {
            fail(failure.errorMessage)
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(busy = false, error = message, info = null) }
    }

    private fun validateEmail(email: String): String? =
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            "Enter a valid email address."
        } else {
            null
        }

    private fun List<String>.toFieldList(): String =
        map {
            when (it) {
                "email_address" -> "email address"
                "phone_number" -> "phone number"
                "first_name" -> "first name"
                "last_name" -> "last name"
                "password" -> "password"
                "username" -> "username"
                LEGAL_ACCEPTED -> "terms acceptance"
                else -> it.replace('_', ' ')
            }
        }.distinct().joinToString(", ")

    private companion object {
        const val CODE_LENGTH = 6
        const val MIN_PASSWORD_LENGTH = 8
        const val EMAIL_ADDRESS = "email_address"
        const val LEGAL_ACCEPTED = "legal_accepted"

        val PROFILE_FIELDS = setOf("password", "first_name", "last_name", "username")
    }

    // endregion
}
