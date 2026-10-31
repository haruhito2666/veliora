package com.veliora.data.sofuu

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * An HTTP MCP tool server. Android cannot spawn stdio MCP processes, so this
 * pass only supports Streamable HTTP endpoints.
 */
@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

/**
 * App-side MCP server registry plus the runtime config file consumed by
 * Sofuu's `mcp.loadConfig()`.
 */
object McpServerStore {

    @Serializable
    private data class StoredServers(val servers: List<McpServer> = emptyList())

    @Serializable
    private data class RuntimeMcpServer(
        val name: String,
        val url: String,
        val transport: String = "http",
        val enabled: Boolean = true,
    )

    private const val PREFS = "veliora_mcp_servers"
    private const val KEY_SERVERS = "servers"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<McpServer> {
        val raw = prefs(context).getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(McpServer.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, servers: List<McpServer>) {
        prefs(context).edit()
            .putString(KEY_SERVERS, json.encodeToString(ListSerializer(McpServer.serializer()), servers))
            .apply()
        writeRuntimeConfig(context, servers)
    }

    fun upsert(context: Context, server: McpServer) {
        save(context, load(context).filterNot { it.id == server.id } + server)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun runtimeConfigFile(context: Context): File {
        val dir = File(context.filesDir, ".sofuu").apply { mkdirs() }
        return File(dir, "mcp.json")
    }

    fun newId() = "mcp_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun writeRuntimeConfig(context: Context, servers: List<McpServer>) {
        // Sofuu's loader expects a plain JSON array; only HTTP servers are
        // useful in an Android sandbox.
        val payload = json.encodeToString(
            ListSerializer(RuntimeMcpServer.serializer()),
            servers.sortedBy { it.name.lowercase() }.map {
                RuntimeMcpServer(
                    name = it.name,
                    url = it.url,
                    transport = "http",
                    enabled = it.enabled,
                )
            },
        )
        runtimeConfigFile(context).writeText(payload)
    }
}
