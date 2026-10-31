package com.veliora.data.sofuu

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * A saved model connection. The API key is not stored here; it lives only in
 * [SecretStore] under [secretName].
 */
@Serializable
data class ConnectionProfile(
    val id: String,
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String = "",
)

/**
 * Persisted connection profiles for cloud and local OpenAI-compatible model
 * services. Profile metadata is plain app-private JSON; the API key is
 * Android-Keystore encrypted.
 */
object ConnectionStore {

    @Serializable
    private data class StoredConnections(val connections: List<ConnectionProfile> = emptyList())

    private const val PREFS = "veliora_connections"
    private const val KEY_LIST = "connections"
    private const val KEY_MIGRATED = "migrated_flat_settings"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<ConnectionProfile> {
        migrateIfNeeded(context)
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ConnectionProfile.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, connections: List<ConnectionProfile>) {
        prefs(context).edit()
            .putString(KEY_LIST, json.encodeToString(ListSerializer(ConnectionProfile.serializer()), connections))
            .apply()
    }

    fun get(context: Context, id: String): ConnectionProfile? =
        load(context).firstOrNull { it.id == id }

    fun upsert(context: Context, profile: ConnectionProfile) {
        val next = load(context).filterNot { it.id == profile.id } + profile
        save(context, next.sortedBy { it.name.lowercase() })
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
        SecretStore.remove(context, secretName(id))
    }

    fun apiKey(context: Context, id: String): String =
        SecretStore.get(context, secretName(id)).orEmpty()

    fun setApiKey(context: Context, id: String, value: String) {
        SecretStore.put(context, secretName(id), value)
    }

    fun secretName(id: String) = "connection:$id"

    fun newId() = "conn_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun migrateIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val runtimePrefs = context.getSharedPreferences("veliora_runtime_settings", Context.MODE_PRIVATE)
        if (runtimePrefs.getBoolean("configured", false)) {
            val provider = runtimePrefs.getString("provider", "").orEmpty()
            val model = runtimePrefs.getString("model", "").orEmpty()
            val baseUrl = runtimePrefs.getString("base_url", "").orEmpty()
            if (provider.isNotBlank() || model.isNotBlank() || baseUrl.isNotBlank()) {
                val profile = ConnectionProfile(
                    id = "default",
                    name = "Default",
                    provider = provider,
                    model = model,
                    baseUrl = baseUrl,
                )
                val existing = runCatching {
                    prefs.getString(KEY_LIST, null)?.let {
                        json.decodeFromString(ListSerializer(ConnectionProfile.serializer()), it)
                    }
                }.getOrNull().orEmpty()
                if (existing.none { it.id == profile.id }) {
                    save(context, existing + profile)
                }
                val legacyKey = runtimePrefs.getString("api_key", "").orEmpty()
                if (legacyKey.isNotBlank()) {
                    SecretStore.put(context, secretName(profile.id), legacyKey)
                }
            }
            runtimePrefs.edit().remove("api_key").apply()
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }
}
