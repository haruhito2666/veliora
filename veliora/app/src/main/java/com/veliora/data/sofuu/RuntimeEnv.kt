package com.veliora.data.sofuu

import android.content.Context
import android.system.Os
import android.util.Log

/**
 * Process-environment knobs for the embedded Sofuu runtime.
 *
 * Sofuu reads these per call, so web-search settings can be applied as soon
 * as the user saves them. API keys and provider settings still require the
 * runtime to be created with its config, so those apply on next launch.
 */
object RuntimeEnv {

    private const val TAG = "VelioraRuntime"

    fun applyWebSettings(context: Context, settings: RuntimeSettings) {
        set("SOFUU_WEB_ENGINE", if (settings.webEngine == RuntimeSettings.WEB_DUCKDUCKGO) null else settings.webEngine)

        val secretName = when (settings.webEngine) {
            RuntimeSettings.WEB_BRAVE -> "web:brave"
            RuntimeSettings.WEB_TAVILY -> "web:tavily"
            else -> null
        }
        val apiKey = secretName?.let { SecretStore.get(context, it) }.orEmpty()
        set("BRAVE_API_KEY", if (settings.webEngine == RuntimeSettings.WEB_BRAVE) apiKey else null)
        set("TAVILY_API_KEY", if (settings.webEngine == RuntimeSettings.WEB_TAVILY) apiKey else null)
    }

    fun applyMcpConfig(context: Context) {
        val file = McpServerStore.runtimeConfigFile(context)
        // Sofuu's loader reads this file at connect time. Create it once so a
        // fresh installation has an explicit empty server list instead of a
        // missing path.
        if (!file.isFile) {
            runCatching { file.writeText("[]") }
        }
        set("SOFUU_MCP_CONFIG", file.absolutePath)
    }

    private fun set(name: String, value: String?) {
        try {
            Os.setenv(name, value, true)
        } catch (error: Exception) {
            Log.w(TAG, "Could not set $name", error)
        }
    }
}
