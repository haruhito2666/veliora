package com.veliora.data.sofuu

import android.content.Context
import com.veliora.BuildConfig

/**
 * Inference mode exposed by the Configuration screen.
 *
 * Local and cloud both use the OpenAI-compatible `sofuu` transport; the
 * difference is the base URL and whether an API key is required. A true
 * on-device GGUF path is not wired into the investigation LLM yet.
 */
enum class InferenceMode(val label: String, val runtimeMode: String) {
    SCRIPTED("Scripted", "scripted"),
    CLOUD("Cloud", "sofuu"),
    LOCAL("Local", "sofuu"),
}

/**
 * User-editable runtime settings.
 *
 * Provider/model/base URL come from the active [ConnectionProfile]; the API
 * key comes from [SecretStore]. These values override the build-time defaults
 * and are never uploaded. Saving applies on the next app launch, except for
 * web-search env values which Sofuu reads per call.
 */
data class RuntimeSettings(
    val mode: InferenceMode,
    val connectionId: String?,
    val provider: String,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val stepDelayMs: Int,
    val fallbackToScripted: Boolean,
    val useAgents: Boolean,
    val webEngine: String,
) {
    companion object {
        const val WEB_DUCKDUCKGO = "duckduckgo"
        const val WEB_BRAVE = "brave"
        const val WEB_TAVILY = "tavily"

        private const val PREFS = "veliora_runtime_settings"
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_MODE = "mode"
        private const val KEY_CONNECTION = "connection_id"
        private const val KEY_STEP_DELAY = "step_delay_ms"
        private const val KEY_FALLBACK = "fallback_to_scripted"
        private const val KEY_USE_AGENTS = "use_agents"
        private const val KEY_WEB_ENGINE = "web_engine"

        fun defaults(): RuntimeSettings {
            val mode = when (BuildConfig.EMBEDDED_MODE.lowercase()) {
                "scripted" -> InferenceMode.SCRIPTED
                else -> InferenceMode.CLOUD
            }
            return RuntimeSettings(
                mode = mode,
                connectionId = null,
                provider = BuildConfig.EMBEDDED_PROVIDER,
                model = BuildConfig.EMBEDDED_MODEL,
                baseUrl = BuildConfig.EMBEDDED_BASE_URL,
                apiKey = "",
                stepDelayMs = BuildConfig.EMBEDDED_STEP_DELAY_MS.coerceIn(0, 2_000),
                fallbackToScripted = mode == InferenceMode.SCRIPTED,
                useAgents = false,
                webEngine = WEB_DUCKDUCKGO,
            )
        }

        fun load(context: Context): RuntimeSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val fallback = defaults()
            if (!prefs.getBoolean(KEY_CONFIGURED, false)) return fallback

            val mode = runCatching {
                InferenceMode.valueOf(
                    prefs.getString(KEY_MODE, fallback.mode.name) ?: fallback.mode.name,
                )
            }.getOrDefault(fallback.mode)

            var connectionId = prefs.getString(KEY_CONNECTION, null)
            if (mode != InferenceMode.SCRIPTED && connectionId == null) {
                connectionId = ConnectionStore.load(context).firstOrNull()?.id
            }

            var provider = fallback.provider
            var model = fallback.model
            var baseUrl = fallback.baseUrl
            var apiKey = ""
            if (mode != InferenceMode.SCRIPTED) {
                connectionId?.let { id ->
                    ConnectionStore.get(context, id)?.let { connection ->
                        provider = connection.provider
                        model = connection.model
                        baseUrl = connection.baseUrl
                        apiKey = ConnectionStore.apiKey(context, id)
                    }
                }
            }

            return RuntimeSettings(
                mode = mode,
                connectionId = connectionId,
                provider = provider,
                model = model,
                baseUrl = baseUrl,
                apiKey = apiKey,
                stepDelayMs = prefs.getInt(KEY_STEP_DELAY, fallback.stepDelayMs).coerceIn(0, 2_000),
                fallbackToScripted = prefs.getBoolean(KEY_FALLBACK, fallback.fallbackToScripted),
                useAgents = prefs.getBoolean(KEY_USE_AGENTS, false),
                webEngine = webEngineOrDefault(prefs.getString(KEY_WEB_ENGINE, null)),
            )
        }

        fun save(context: Context, settings: RuntimeSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_CONFIGURED, true)
                .putString(KEY_MODE, settings.mode.name)
                .putString(KEY_CONNECTION, settings.connectionId)
                .putInt(KEY_STEP_DELAY, settings.stepDelayMs.coerceIn(0, 2_000))
                .putBoolean(KEY_FALLBACK, settings.fallbackToScripted)
                .putBoolean(KEY_USE_AGENTS, settings.useAgents)
                .putString(KEY_WEB_ENGINE, webEngineOrDefault(settings.webEngine))
                .apply()
        }

        fun reset(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }

        fun webEngineOrDefault(value: String?): String = when (value?.lowercase()) {
            WEB_BRAVE -> WEB_BRAVE
            WEB_TAVILY -> WEB_TAVILY
            else -> WEB_DUCKDUCKGO
        }
    }
}
