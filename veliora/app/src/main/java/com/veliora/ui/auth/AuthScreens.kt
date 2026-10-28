package com.veliora.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.api.Clerk

// The auth flow is deliberately monochrome: a white page, near-black ink, and
// one hairline gray. Buttons carry the tactility instead of color.

private val AuthWhite = Color(0xFFFFFFFF)
private val AuthInk = Color(0xFF0B0B0C)
private val AuthMuted = Color(0xFF6B7280)
private val AuthLine = Color(0xFFE4E4E7)
private val AuthSoft = Color(0xFFF6F6F7)
private val AuthErrorInk = Color(0xFFB42318)
private val AuthErrorSoft = Color(0xFFFDF3F2)

private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

@Composable
fun ClerkAuthFlow(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = state.screen != AuthScreen.WELCOME) {
        viewModel.goBack()
    }

    LaunchedEffect(state.screen, state.codeKind) {
        if (state.screen == AuthScreen.VERIFY) code = ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AuthWhite) {
        when (state.screen) {
            AuthScreen.WELCOME -> WelcomeScreen(
                busy = state.busy,
                onGetStarted = {
                    password = ""
                    viewModel.openSignUp()
                },
                onSignIn = {
                    password = ""
                    viewModel.openSignIn()
                },
            )

            AuthScreen.SIGN_UP_CHOICE -> SignUpChoiceScreen(
                state = state,
                onBack = viewModel::goBack,
                onGoogle = { viewModel.continueWithGoogle(signUp = true) },
                onEmail = viewModel::openSignUpEmail,
                onSignIn = {
                    password = ""
                    viewModel.openSignIn()
                },
                onDismissMessage = viewModel::dismissMessage,
            )

            AuthScreen.SIGN_IN_CHOICE -> SignInChoiceScreen(
                state = state,
                onBack = viewModel::goBack,
                onGoogle = { viewModel.continueWithGoogle(signUp = false) },
                onEmail = viewModel::openSignInEmail,
                onSignUp = {
                    password = ""
                    viewModel.openSignUp()
                },
                onDismissMessage = viewModel::dismissMessage,
            )

            AuthScreen.SIGN_UP_EMAIL -> SignUpEmailScreen(
                state = state,
                firstName = firstName,
                lastName = lastName,
                username = username,
                email = email,
                password = password,
                onFirstNameChange = { firstName = it },
                onLastNameChange = { lastName = it },
                onUsernameChange = { username = it },
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onBack = viewModel::goBack,
                onSubmit = {
                    viewModel.signUp(
                        firstName = firstName,
                        lastName = lastName,
                        username = username,
                        email = email,
                        password = password,
                    )
                },
                onSignIn = {
                    password = ""
                    viewModel.openSignIn()
                },
                onDismissMessage = viewModel::dismissMessage,
            )

            AuthScreen.SIGN_IN_EMAIL -> SignInEmailScreen(
                state = state,
                email = email,
                password = password,
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onBack = viewModel::goBack,
                onSubmit = {
                    if (Clerk.passwordIsEnabled) {
                        viewModel.signIn(email = email, password = password)
                    } else {
                        viewModel.signInWithEmailCode(email = email)
                    }
                },
                onEmailCode = { viewModel.signInWithEmailCode(email = email) },
                onSignUp = {
                    password = ""
                    viewModel.openSignUp()
                },
                onDismissMessage = viewModel::dismissMessage,
            )

            AuthScreen.COMPLETE_PROFILE -> CompleteProfileScreen(
                state = state,
                firstName = firstName,
                lastName = lastName,
                username = username,
                password = password,
                onFirstNameChange = { firstName = it },
                onLastNameChange = { lastName = it },
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                onBack = viewModel::goBack,
                onSubmit = {
                    viewModel.completeProfile(
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        username = username,
                    )
                },
                onDismissMessage = viewModel::dismissMessage,
            )

            AuthScreen.VERIFY -> VerifyCodeScreen(
                state = state,
                code = code,
                onCodeChange = { code = it },
                onBack = viewModel::goBack,
                onVerify = { viewModel.verifyCode(code) },
                onResend = viewModel::resendCode,
                onDismissMessage = viewModel::dismissMessage,
            )
        }
    }
}

// ── Welcome ──────────────────────────────────────────────────────────
// The reference composition: headline near the top, the two primary
// actions anchored above the home indicator.

@Composable
private fun WelcomeScreen(
    busy: Boolean,
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.24f))
        Text(
            text = "Think different.\nFeel more.",
            modifier = Modifier.fillMaxWidth(),
            color = AuthInk,
            fontSize = 36.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "An AI that writes, reasons and creates — built around your workflow, " +
                "not someone else's.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            color = AuthMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        TactileButton(
            label = "Get Started",
            onClick = onGetStarted,
            variant = AuthButtonVariant.Paper,
            enabled = !busy,
        )
        Spacer(Modifier.height(12.dp))
        TactileButton(
            label = "I have an account",
            onClick = onSignIn,
            variant = AuthButtonVariant.Ink,
            enabled = !busy,
        )
        Spacer(Modifier.height(20.dp))
        LegalLine(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ── Choice screens ───────────────────────────────────────────────────

@Composable
private fun SignUpChoiceScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onEmail: () -> Unit,
    onSignIn: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    AuthPage(
        onBack = onBack,
        title = "Create your account",
        subtitle = "Choose how you'd like to get started.",
        footer = { AlternateModeLine("Already have an account?", "Sign in", onSignIn) },
    ) {
        MessageBanner(state, onDismissMessage)
        SectionLabel("CONTINUE WITH")
        TactileButton(
            label = "Continue with Google",
            onClick = onGoogle,
            variant = AuthButtonVariant.Ink,
            enabled = !state.busy,
            loading = state.busy,
            leading = { GoogleGlyph(Modifier.size(18.dp)) },
        )
        Spacer(Modifier.height(12.dp))
        TactileButton(
            label = "Continue with email",
            onClick = onEmail,
            variant = AuthButtonVariant.Paper,
            enabled = !state.busy,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = null,
                    tint = AuthInk,
                    modifier = Modifier.size(19.dp),
                )
            },
        )
        Spacer(Modifier.height(20.dp))
        LegalLine(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SignInChoiceScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onEmail: () -> Unit,
    onSignUp: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    AuthPage(
        onBack = onBack,
        title = "Welcome back",
        subtitle = "Sign in to pick up where you left off.",
        footer = { AlternateModeLine("New to Veliora?", "Create an account", onSignUp) },
    ) {
        MessageBanner(state, onDismissMessage)
        SectionLabel("CONTINUE WITH")
        TactileButton(
            label = "Continue with Google",
            onClick = onGoogle,
            variant = AuthButtonVariant.Ink,
            enabled = !state.busy,
            loading = state.busy,
            leading = { GoogleGlyph(Modifier.size(18.dp)) },
        )
        Spacer(Modifier.height(12.dp))
        TactileButton(
            label = "Continue with email",
            onClick = onEmail,
            variant = AuthButtonVariant.Paper,
            enabled = !state.busy,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = null,
                    tint = AuthInk,
                    modifier = Modifier.size(19.dp),
                )
            },
        )
        Spacer(Modifier.height(20.dp))
        LegalLine(Modifier.fillMaxWidth())
    }
}

// ── Email screens ────────────────────────────────────────────────────

@Composable
private fun SignUpEmailScreen(
    state: AuthUiState,
    firstName: String,
    lastName: String,
    username: String,
    email: String,
    password: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    AuthPage(
        onBack = onBack,
        title = "Your details",
        subtitle = if (Clerk.passwordIsEnabled) {
            "A few details and you're in."
        } else {
            "Enter your details. We'll email you a code to confirm your address."
        },
        bottom = {
            MessageBanner(state, onDismissMessage)
            TactileButton(
                label = if (Clerk.passwordIsEnabled) "Create account" else "Continue",
                onClick = onSubmit,
                variant = AuthButtonVariant.Ink,
                enabled = !state.busy,
                loading = state.busy,
            )
            Spacer(Modifier.height(12.dp))
            LegalLine(Modifier.fillMaxWidth())
        },
        footer = { AlternateModeLine("Already have an account?", "Sign in", onSignIn) },
    ) {
        if (Clerk.isFirstNameEnabled) {
            AuthTextField(
                value = firstName,
                onValueChange = onFirstNameChange,
                label = "First name",
                capitalization = KeyboardCapitalization.Words,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (Clerk.isLastNameEnabled) {
            AuthTextField(
                value = lastName,
                onValueChange = onLastNameChange,
                label = "Last name",
                capitalization = KeyboardCapitalization.Words,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (Clerk.isUserNameEnabled) {
            AuthTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Username",
                capitalization = KeyboardCapitalization.None,
            )
            Spacer(Modifier.height(12.dp))
        }

        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
        )
        if (Clerk.passwordIsEnabled) {
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = password,
                onValueChange = onPasswordChange,
                isNewPassword = true,
            )
        }
    }
}

@Composable
private fun SignInEmailScreen(
    state: AuthUiState,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onEmailCode: () -> Unit,
    onSignUp: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    AuthPage(
        onBack = onBack,
        title = "Sign in with email",
        subtitle = if (Clerk.passwordIsEnabled) {
            "Enter the email and password for your account."
        } else {
            "Enter your email and we'll send you a sign-in code."
        },
        bottom = {
            MessageBanner(state, onDismissMessage)
            TactileButton(
                label = if (Clerk.passwordIsEnabled) "Sign in" else "Email me a code",
                onClick = onSubmit,
                variant = AuthButtonVariant.Ink,
                enabled = !state.busy,
                loading = state.busy,
            )
        },
        footer = { AlternateModeLine("New to Veliora?", "Create an account", onSignUp) },
    ) {
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
        )
        if (Clerk.passwordIsEnabled) {
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = password,
                onValueChange = onPasswordChange,
                isNewPassword = false,
            )
            if (!state.busy) {
                Spacer(Modifier.height(4.dp))
                CenterTextButton(
                    text = "Email me a code instead",
                    onClick = onEmailCode,
                )
            }
        }
    }
}

// ── Profile completion ───────────────────────────────────────────────
// A social sign-up can come back missing a required attribute such as a
// password or a name. Collect exactly what Clerk asked for.

@Composable
private fun CompleteProfileScreen(
    state: AuthUiState,
    firstName: String,
    lastName: String,
    username: String,
    password: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val fields = state.missingProfileFields

    AuthPage(
        onBack = onBack,
        title = "Finish your account",
        subtitle = "Add the last details Clerk needs to finish creating your account.",
        bottom = {
            MessageBanner(state, onDismissMessage)
            TactileButton(
                label = "Finish sign-up",
                onClick = onSubmit,
                variant = AuthButtonVariant.Ink,
                enabled = !state.busy,
                loading = state.busy,
            )
        },
        footer = { AlternateModeLine("Wrong account?", "Start over", onBack) },
    ) {
        if ("first_name" in fields) {
            AuthTextField(
                value = firstName,
                onValueChange = onFirstNameChange,
                label = "First name",
                capitalization = KeyboardCapitalization.Words,
            )
            Spacer(Modifier.height(12.dp))
        }
        if ("last_name" in fields) {
            AuthTextField(
                value = lastName,
                onValueChange = onLastNameChange,
                label = "Last name",
                capitalization = KeyboardCapitalization.Words,
            )
            Spacer(Modifier.height(12.dp))
        }
        if ("username" in fields) {
            AuthTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Username",
                capitalization = KeyboardCapitalization.None,
            )
            Spacer(Modifier.height(12.dp))
        }
        if ("password" in fields) {
            PasswordField(
                value = password,
                onValueChange = onPasswordChange,
                isNewPassword = true,
            )
        }
    }
}

// ── Verification ─────────────────────────────────────────────────────

@Composable
private fun VerifyCodeScreen(
    state: AuthUiState,
    code: String,
    onCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val kind = state.codeKind
    val isBackupCode = kind == CodeKind.SIGN_IN_MFA_BACKUP
    val isTotp = kind == CodeKind.SIGN_IN_MFA_TOTP
    val emailDestination = state.codeDestination.ifBlank { "your email" }

    val title = when (kind) {
        CodeKind.SIGN_IN_MFA_TOTP, CodeKind.SIGN_IN_MFA_BACKUP -> "Verify it's you"
        else -> "Check your email"
    }
    val subtitle = when (kind) {
        CodeKind.SIGN_UP_EMAIL ->
            "We sent a 6-digit code to $emailDestination to confirm your account."
        CodeKind.SIGN_IN_FIRST_FACTOR_EMAIL ->
            "We sent a 6-digit code to $emailDestination."
        CodeKind.SIGN_IN_MFA_EMAIL ->
            "Enter the 6-digit code we sent to $emailDestination."
        CodeKind.SIGN_IN_MFA_TOTP ->
            "Enter the 6-digit code from your authenticator app."
        CodeKind.SIGN_IN_MFA_BACKUP ->
            "Enter one of your backup codes."
        null -> "Enter the verification code."
    }

    AuthPage(
        onBack = onBack,
        title = title,
        subtitle = subtitle,
        bottom = {
            MessageBanner(state, onDismissMessage)
            TactileButton(
                label = "Verify",
                onClick = onVerify,
                variant = AuthButtonVariant.Ink,
                enabled = !state.busy,
                loading = state.busy,
            )
            if (state.canResend && !state.busy) {
                Spacer(Modifier.height(8.dp))
                CenterTextButton(text = "Resend code", onClick = onResend)
            }
            Spacer(Modifier.height(8.dp))
        },
        footer = {
            AlternateModeLine("Wrong account?", "Go back", onBack)
        },
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { typed ->
                val limit = if (isBackupCode) 32 else 6
                val filtered = if (isBackupCode) {
                    typed.take(limit)
                } else {
                    typed.filter(Char::isDigit).take(limit)
                }
                onCodeChange(filtered)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Verification code" },
            textStyle = LocalTextStyle.current.copy(
                color = AuthInk,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isBackupCode) 18.sp else 24.sp,
                lineHeight = 30.sp,
                letterSpacing = if (isBackupCode) 1.sp else 8.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            ),
            placeholder = {
                Text(
                    text = if (isBackupCode) "Backup code" else "000000",
                    modifier = Modifier.fillMaxWidth(),
                    color = AuthMuted.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isBackupCode) 18.sp else 24.sp,
                    letterSpacing = if (isBackupCode) 1.sp else 8.sp,
                    textAlign = TextAlign.Center,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isBackupCode) KeyboardType.Text else KeyboardType.Number,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.None,
            ),
            keyboardActions = KeyboardActions(onDone = { onVerify() }),
            colors = authFieldColors(),
            enabled = !state.busy,
        )
        if (isTotp) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Codes refresh every 30 seconds.",
                modifier = Modifier.fillMaxWidth(),
                color = AuthMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Shared page + controls ───────────────────────────────────────────

@Composable
private fun AuthPage(
    onBack: (() -> Unit)?,
    title: String,
    subtitle: String,
    footer: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                TactileIconButton(
                    contentDescription = "Back",
                    onClick = onBack,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = AuthInk,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(if (onBack == null) 32.dp else 12.dp))
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = AuthInk,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                color = AuthMuted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }

        if (bottom != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                content = bottom,
            )
        }

        if (footer != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                footer()
            }
        }
    }
}

private enum class AuthButtonVariant { Ink, Paper }

@Composable
private fun TactileButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AuthButtonVariant,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "authButtonScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 0.dp else if (variant == AuthButtonVariant.Ink) 5.dp else 3.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "authButtonElevation",
    )

    val background = when {
        !enabled -> AuthSoft
        pressed && variant == AuthButtonVariant.Ink -> Color(0xFF232326)
        pressed -> Color(0xFFF2F2F3)
        variant == AuthButtonVariant.Ink -> AuthInk
        else -> AuthWhite
    }
    val foreground = if (variant == AuthButtonVariant.Ink) AuthWhite else AuthInk
    val border = if (variant == AuthButtonVariant.Paper) AuthInk else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (enabled) elevation else 0.dp,
                shape = CircleShape,
                clip = false,
            )
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, if (enabled) border else AuthLine, CircleShape)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics {
                if (loading) stateDescription = "Loading"
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = if (enabled) foreground else AuthMuted,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            } else if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = label,
                color = if (enabled) foreground else AuthMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
            )
        }
    }
}

@Composable
private fun TactileIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "authIconScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 0.dp else 2.dp,
        label = "authIconElevation",
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = elevation, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(AuthWhite)
            .border(1.dp, AuthLine, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun GoogleGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val diameter = size.minDimension
        val stroke = diameter * 0.19f
        val inset = stroke / 2f
        val arcSize = Size(diameter - stroke, diameter - stroke)
        val topLeft = Offset(inset, inset)
        val style = Stroke(width = stroke, cap = StrokeCap.Butt)

        drawCircle(color = AuthWhite, radius = diameter / 2f)

        drawArc(
            color = GoogleBlue,
            startAngle = 270f,
            sweepAngle = 82f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = style,
        )
        drawArc(
            color = GoogleGreen,
            startAngle = 352f,
            sweepAngle = 78f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = style,
        )
        drawArc(
            color = GoogleYellow,
            startAngle = 70f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = style,
        )
        drawArc(
            color = GoogleRed,
            startAngle = 160f,
            sweepAngle = 92f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = style,
        )
        // The horizontal blue crossbar that opens the G.
        drawRect(
            color = GoogleBlue,
            topLeft = Offset(diameter * 0.47f, diameter * 0.40f),
            size = Size(diameter * 0.53f, stroke * 0.92f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        color = AuthMuted,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MessageBanner(
    state: AuthUiState,
    onDismiss: () -> Unit,
) {
    val message = state.error ?: state.info ?: return
    val isError = state.error != null
    val background = if (isError) AuthErrorSoft else AuthSoft
    val ink = if (isError) AuthErrorInk else AuthMuted

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        color = background,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = ink,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Dismiss message",
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = LocalTextStyle.current.copy(
            color = AuthInk,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    color = AuthMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() },
        ),
        colors = authFieldColors(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    isNewPassword: Boolean,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    AuthTextField(
        value = value,
        onValueChange = onValueChange,
        label = "Password",
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Done,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (revealed) "Hide password" else "Show password",
                    ) {
                        revealed = !revealed
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = AuthMuted,
                    modifier = Modifier.size(19.dp),
                )
            }
        },
        supportingText = if (isNewPassword) "Use 8 or more characters." else null,
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthInk,
    unfocusedTextColor = AuthInk,
    disabledTextColor = AuthMuted,
    cursorColor = AuthInk,
    focusedContainerColor = AuthWhite,
    unfocusedContainerColor = AuthWhite,
    disabledContainerColor = AuthWhite,
    focusedBorderColor = AuthInk,
    unfocusedBorderColor = AuthLine,
    disabledBorderColor = AuthLine,
    focusedLabelColor = AuthInk,
    unfocusedLabelColor = AuthMuted,
    disabledLabelColor = AuthMuted,
    focusedPlaceholderColor = AuthMuted.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = AuthMuted.copy(alpha = 0.55f),
    focusedSupportingTextColor = AuthMuted,
    unfocusedSupportingTextColor = AuthMuted,
)

@Composable
private fun LegalLine(modifier: Modifier = Modifier) {
    Text(
        text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
        modifier = modifier,
        color = AuthMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AlternateModeLine(
    prompt: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = prompt,
            color = AuthMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = AuthInk),
        ) {
            Text(
                text = action,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun CenterTextButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = AuthInk),
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}
