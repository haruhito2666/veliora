package com.veliora.ui.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptic vocabulary per 23-ui-ux-flow.md §10.5.
 *
 * Capture a [HapticContext] once per composition via [rememberHapticContext],
 * then call the plain functions from any (non-composable) lambda.
 */
class HapticContext(
    private val context: Context,
    private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    /** A peer voted. */
    fun tick() {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** The org decided. */
    fun decision() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** The org restructured itself. */
    fun evolution() {
        vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** The org changed its law — double heavy. */
    fun amendment() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 200, 120), -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(effect)
    }
}

@Composable
fun rememberHapticContext(): HapticContext {
    val context = LocalContext.current
    val feedback = LocalHapticFeedback.current
    return remember(context, feedback) { HapticContext(context, feedback) }
}
