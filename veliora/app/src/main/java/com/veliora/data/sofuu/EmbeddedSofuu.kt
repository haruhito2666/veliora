package com.veliora.data.sofuu

import android.content.Context
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
        val bundle = withContext(Dispatchers.IO) {
            context.assets.open("veliora-embedded.js").bufferedReader().use { it.readText() }
        }

        withContext(engine) {
            val root = File(context.filesDir, "sofuu").apply { mkdirs() }
            val configRoot = File(root, "config").apply { mkdirs() }
            val providerKeys = readProviderKeys(root)

            val runtimeConfig = JSONObject()
                .put("embedded", true)
                .put("config_root", configRoot.absolutePath)
                // Agent memory must live in the app's private data directory.
                // The Sofuu default is <cwd>/.sofuu/brain/brain.qtsq, which is
                // not writable in an Android app process.
                .put("brain_path", File(root, "brain.qtsq").absolutePath)
            if (BuildConfig.EMBEDDED_PROVIDER.isNotBlank()) {
                runtimeConfig.put("provider", BuildConfig.EMBEDDED_PROVIDER)
            }
            if (BuildConfig.EMBEDDED_MODEL.isNotBlank()) {
                runtimeConfig.put("model", BuildConfig.EMBEDDED_MODEL)
            }
            if (BuildConfig.EMBEDDED_BASE_URL.isNotBlank()) {
                runtimeConfig.put("base_url", BuildConfig.EMBEDDED_BASE_URL)
            }
            if (providerKeys.length() > 0) {
                runtimeConfig.put("api_keys", providerKeys)
            }

            val runtime = SofuuBridge(runtimeConfig.toString())
            bridge = runtime

            val eval = runtime.eval(bundle)
                ?: throw IOException("Sofuu could not load the Veliora runtime bundle")
            val evalEnvelope = JSONObject(eval)
            if (!evalEnvelope.optBoolean("ok")) {
                val error = evalEnvelope.optJSONObject("error")
                throw IOException(
                    "Sofuu rejected the Veliora runtime bundle: " +
                        (error?.optString("message") ?: "unknown error"),
                )
            }

            val config = JSONObject()
                .put("storePath", File(root, "veliora-store").absolutePath)
                .put("mode", BuildConfig.EMBEDDED_MODE)
                .put("authMode", "clerk")
                .put("stepDelayMs", BuildConfig.EMBEDDED_STEP_DELAY_MS)
                .put("provider", BuildConfig.EMBEDDED_PROVIDER)
                .put("model", BuildConfig.EMBEDDED_MODEL)
                .put("baseUrl", BuildConfig.EMBEDDED_BASE_URL)
                // A real-inference mode must never silently degrade to the
                // scripted fixtures: surface provider/agent failures instead.
                .put("fallbackToScripted", BuildConfig.EMBEDDED_MODE == "scripted")
                .put("appOrigins", JSONArray().put("http://localhost"))
                .put(
                    "clerk",
                    JSONObject()
                        .put("issuer", BuildConfig.CLERK_ISSUER)
                        .put("jwksUrl", BuildConfig.CLERK_JWKS_URL)
                        .put("authorizedParties", authorizedParties()),
                )

            val raw = runtime.call("veliora.init", config.toString())
                ?: throw IOException("Sofuu returned no initialization result")
            val envelope = JSONObject(raw)
            if (!envelope.optBoolean("ok")) {
                val error = envelope.optJSONObject("error")
                throw IOException(
                    "Veliora runtime initialization failed: " +
                        (error?.optString("message") ?: "unknown error"),
                )
            }
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
     */
    private suspend fun authToken(): String {
        val result = Clerk.activeSession?.fetchToken()
        val token = result?.successOrNull()?.jwt
        if (token.isNullOrBlank()) {
            throw IOException("Not signed in. Sign in and try again.")
        }
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
