package com.veliora.data.sofuu

import android.content.Context
import android.os.SystemClock
import android.system.Os
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.sofuu.runtime.SofuuBridge
import com.veliora.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Veliora gateway running inside libsofuu on the device.
 *
 * There is no Veliora HTTP server and no `sofuu` executable in the APK:
 * [SofuuBridge] evaluates the bundled gateway JS and every request goes
 * through the SDK's generic funnel. The bridge is thread-affine, so all
 * native calls are serialized on one worker thread.
 */
class EmbeddedSofuu(private val context: Context) {

    data class Response(val status: Int, val body: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "veliora-sofuu")
    }.asCoroutineDispatcher()

    @Volatile
    private var bridge: SofuuBridge? = null

    private val startLock = Any()
    private val startInFlight = AtomicBoolean(false)

    @Volatile
    private var ready = CompletableDeferred<Result<Unit>>()

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedTokenAt = 0L

    private companion object {
        const val TOKEN_CACHE_MS = 30_000L
    }

    /** Starts the runtime once; safe to call from Application.onCreate(). */
    fun start() = launchStart(force = false)

    /** Retries initialization after a failure. */
    fun retry() = launchStart(force = true)

    private fun launchStart(force: Boolean) {
        synchronized(startLock) {
            if (initialized) return
            if (startInFlight.get()) return
            if (!force && ready.isCompleted) return
            if (!startInFlight.compareAndSet(false, true)) return
            if (force) ready = CompletableDeferred()

            val attempt = ready
            scope.launch {
                val result = runCatching { initialize() }
                attempt.complete(result)
                if (result.isSuccess) {
                    initialized = true
                } else {
                    withContext(engine) {
                        runCatching { bridge?.close() }
                    }
                    bridge = null
                }
                startInFlight.set(false)
            }
        }
    }

    private suspend fun initialize() {
        bridge = createRuntime(loadBundle())
    }

    private suspend fun loadBundle(): String = withContext(Dispatchers.IO) {
        context.assets.open("veliora-embedded.js").bufferedReader().use { it.readText() }
    }

    private suspend fun createRuntime(bundle: String): SofuuBridge = withContext(engine) {
        val root = File(context.filesDir, "sofuu").apply { mkdirs() }
        val configRoot = File(root, "config").apply { mkdirs() }
        val settings = RuntimeSettings.load(context)
        val providerKeys = readProviderKeys(root)
        val keyName = when (settings.provider.lowercase()) {
            "anthropic" -> "anthropic"
            "local", "ollama", "llamacpp", "llama.cpp", "llama-server" -> null
            else -> "openai"
        }
        if (settings.apiKey.isNotBlank() && keyName != null) {
            // Sofuu resolves custom OpenAI-compatible providers through
            // the canonical "openai" key slot, not the provider label.
            providerKeys.put(keyName, settings.apiKey)
        }
        val runtimeConfig = JSONObject()
            .put("embedded", true)
            .put("config_root", configRoot.absolutePath)
            // Agent memory must live in the app's private data directory.
            // The Sofuu default is <cwd>/.sofuu/brain/brain.qtsq, which is
            // not writable in an Android app process.
            .put("brain_path", File(root, "brain.qtsq").absolutePath)
        if (settings.provider.isNotBlank()) {
            runtimeConfig.put("provider", settings.provider)
        }
        if (settings.model.isNotBlank()) {
            runtimeConfig.put("model", settings.model)
        }
        if (settings.baseUrl.isNotBlank()) {
            runtimeConfig.put("base_url", settings.baseUrl)
        }
        if (providerKeys.length() > 0) {
            runtimeConfig.put("api_keys", providerKeys)
        }

        // Sofuu's QTSQ vault derives its key from $HOME/.sofuu/brain.key.
        // Android does not provide HOME, and the no-HOME fallback is a
        // process-local random key: data written in one launch cannot be
        // decrypted by the next. Pin HOME to app-private storage so the key
        // file is stable across restarts.
        try {
            Os.setenv("HOME", context.filesDir.absolutePath, true)
        } catch (error: Exception) {
            throw IOException("Could not set HOME for the embedded runtime", error)
        }

        // The Android libcurl has no built-in CA store. Export the
        // platform anchors before the runtime can create a curl handle;
        // otherwise Clerk JWKS and every other HTTPS fetch fail TLS
        // verification.
        AndroidCaTrust.install(context)
        RuntimeEnv.applyMcpConfig(context)
        RuntimeEnv.applyWebSettings(context, settings)

        val runtime = SofuuBridge(runtimeConfig.toString())
        try {
            val eval = runtime.eval(bundle)
            val evalEnvelope = envelope(eval, "bundle evaluation")
            if (!evalEnvelope.optBoolean("ok")) {
                val error = evalEnvelope.optJSONObject("error")
                throw IOException(
                    "Sofuu rejected the Veliora runtime bundle: " +
                        (error?.optString("message") ?: "unknown error"),
                )
            }

            // Agent mode is the only path that can call web/MCP tools during
            // investigations. It is deliberately opt-in and only valid with a
            // real model connection.
            val runtimeMode = when {
                settings.mode == InferenceMode.SCRIPTED -> settings.mode.runtimeMode
                settings.useAgents -> "agent"
                else -> settings.mode.runtimeMode
            }

            val config = JSONObject()
                .put("storePath", File(root, "veliora-store").absolutePath)
                .put("mode", runtimeMode)
                .put("authMode", "clerk")
                .put("stepDelayMs", settings.stepDelayMs)
                .put("provider", settings.provider)
                .put("model", settings.model)
                .put("baseUrl", settings.baseUrl)
                // A real-inference mode must never silently degrade to the
                // scripted fixtures unless the user explicitly opts in.
                .put(
                    "fallbackToScripted",
                    settings.fallbackToScripted || settings.mode == InferenceMode.SCRIPTED,
                )
                .put("appOrigins", JSONArray().put("http://localhost"))
                .put(
                    "clerk",
                    JSONObject()
                        .put("issuer", BuildConfig.CLERK_ISSUER)
                        .put("jwksUrl", BuildConfig.CLERK_JWKS_URL)
                        .put(
                            "jwksCachePath",
                            File(root, "jwks-cache.json").absolutePath,
                        )
                        .put("authorizedParties", authorizedParties()),
                )

            val raw = runtime.call("veliora.init", config.toString())
            val initEnvelope = envelope(raw, "initialization")
            if (!initEnvelope.optBoolean("ok")) {
                val error = initEnvelope.optJSONObject("error")
                throw IOException(
                    "Veliora runtime initialization failed: " +
                        (error?.optString("message") ?: "unknown error"),
                )
            }
            runtime
        } catch (error: Exception) {
            runCatching { runtime.close() }
            throw error
        }
    }

    private fun envelope(raw: String?, what: String): JSONObject {
        if (raw.isNullOrBlank() || raw == "null") {
            throw IOException("Sofuu $what returned no JSON envelope")
        }
        return try {
            JSONObject(raw)
        } catch (error: Exception) {
            throw IOException("Sofuu $what returned invalid JSON: ${raw.take(200)}", error)
        }
    }

    private fun authorizedParties(): JSONArray {
        val parties = JSONArray()
        // Native tokens may carry the Android package as their authorized
        // party instead of a browser origin. Derive it from the actual app id
        // so forks with another applicationId are not tied to com.veliora.
        val applicationId = context.packageName
        parties.put(applicationId)
        parties.put("$applicationId://")
        BuildConfig.CLERK_AUTHORIZED_PARTIES
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { parties.put(it) }
        return parties
    }

    /**
     * Optional provider keys, provisioned into the app's private data
     * directory (not shipped in the APK):
     *
     *   files/sofuu/provider-keys.json
     *   {"openai":"sk-...","anthropic":"sk-ant-..."}
     *
     * Keeping this out of BuildConfig prevents a release APK from embedding
     * a provider secret.
     */
    private fun readProviderKeys(root: File): JSONObject {
        val file = File(root, "provider-keys.json")
        if (!file.isFile) return JSONObject()
        return runCatching {
            JSONObject(file.readText())
        }.getOrDefault(JSONObject())
    }

    suspend fun request(method: String, path: String, body: String? = null): Response {
        awaitReady()
        val token = authToken()
        val args = JSONObject()
            .put("method", method)
            .put("url", path)
            .put(
                "headers",
                JSONObject()
                    .put("authorization", "Bearer $token")
                    .put("x-veliora-platform", "android")
                    .put("x-veliora-app-version", BuildConfig.VERSION_NAME)
                    .put("content-type", "application/json")
                    .put("accept", "application/json"),
            )
            .put("body", body ?: "")

        val raw = withContext(engine) {
            bridge?.call("veliora.request", args.toString())
        } ?: throw IOException("Sofuu runtime is not ready")

        return parseResponse(raw)
    }

    /**
     * Advances the embedded event loop while a pipeline is active.
     *
     * Android has no background libuv pump; the C ABI drains its loop until
     * this bounded timer settles. The UI calls it from the polling loop.
     */
    suspend fun tick(milliseconds: Int = 50) {
        awaitReady()
        val args = JSONObject()
            .put("ms", milliseconds.coerceIn(0, 2_000))
            .toString()
        withContext(engine) {
            bridge?.call("veliora.tick", args)
        }
    }

    suspend fun capabilities(): JSONObject {
        awaitReady()
        val raw = withContext(engine) { bridge?.checkCaps() } ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    suspend fun abiVersion(): Int {
        awaitReady()
        return withContext(engine) { bridge?.abiVersion ?: 0 }
    }

    /** A small end-to-end probe of the native bridge, not just capability bits. */
    suspend fun selfTest(): JSONObject {
        awaitReady()
        return withContext(engine) {
            val runtime = bridge ?: throw IOException("Sofuu runtime is not ready")
            val vector = runtime.embedLocal("on-device")
            JSONObject()
                .put("abi", runtime.abiVersion)
                .put("sha256", runtime.sha256("abc") ?: "")
                .put("embeddingDims", vector?.size ?: 0)
        }
    }

    private suspend fun awaitReady() {
        ready.await().getOrThrow()
    }

    /**
     * Reference auth seam: return the current bearer token from your auth
     * provider. This implementation reads the Clerk active session; a fork
     * using another provider replaces this function (and the auth UI) while
     * keeping the gateway contract unchanged.
     *
     * Clerk tokens are short-lived, so the cache is intentionally small: it
     * only removes the per-request fetchToken round trip during bursts of
     * polling without ever outliving a meaningful refresh window.
     */
    private suspend fun authToken(): String {
        SystemClock.elapsedRealtime().let { now ->
            cachedToken?.takeIf { now - cachedTokenAt < TOKEN_CACHE_MS }?.let { return it }
        }
        val result = Clerk.activeSession?.fetchToken()
        val token = result?.successOrNull()?.jwt
        if (token.isNullOrBlank()) {
            throw IOException("Not signed in. Sign in and try again.")
        }
        cachedToken = token
        cachedTokenAt = SystemClock.elapsedRealtime()
        return token
    }

    private fun parseResponse(raw: String): Response {
        val envelope = JSONObject(raw)
        if (!envelope.optBoolean("ok")) {
            val error = envelope.optJSONObject("error")
            throw IOException(error?.optString("message") ?: "Sofuu request failed")
        }
        val result = envelope.optJSONObject("result")
            ?: throw IOException("Sofuu returned an empty response")
        val status = result.optInt("status", 200)
        val body = result.optString("body", "")
        if (status !in 200..299) {
            throw IOException("HTTP $status: ${errorMessage(body)}")
        }
        return Response(status, body)
    }

    private fun errorMessage(body: String): String {
        if (body.isBlank()) return "request failed"
        return runCatching {
            JSONObject(body)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf(String::isNotBlank)
        }.getOrNull() ?: body.take(300)
    }
}
