package com.veliora

import android.content.Context
import android.util.Log
import java.io.File
import java.time.OffsetDateTime

/**
 * Captures the last uncaught Java exception so a device-only crash can be
 * diagnosed without adb. The trace is written to app-private storage and
 * surfaced on the Instance screen; the previous handler still runs so the
 * normal Android crash behavior is unchanged.
 */
object CrashReporter {

    private const val TAG = "VelioraCrash"
    private const val DIR = "diagnostics"
    private const val FILE = "last-crash.txt"
    private const val MAX_LENGTH = 8_000

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun last(context: Context): String? {
        val file = file(context)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val trace = Log.getStackTraceString(throwable)
        File(dir, FILE).writeText(
            buildString {
                appendLine("time=${OffsetDateTime.now()}")
                appendLine("thread=${thread.name}")
                appendLine(trace.take(MAX_LENGTH))
            },
        )
    }

    private fun file(context: Context): File =
        File(File(context.filesDir, DIR), FILE)
}
