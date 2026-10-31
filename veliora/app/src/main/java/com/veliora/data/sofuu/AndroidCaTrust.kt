package com.veliora.data.sofuu

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Exports Android's system CA certificates for the embedded Sofuu runtime.
 *
 * The Android libcurl is intentionally built without a default trust store
 * (`CURL_CA_BUNDLE=none`), so without this every HTTPS request from the
 * gateway fails with "SSL peer certificate or SSH remote key was not OK".
 * Sofuu's curl wrapper reads `SOFUU_CA_BUNDLE` when it creates a handle, so
 * the bundle must be installed in the process environment before the
 * [com.sofuu.runtime.SofuuBridge] is constructed.
 */
internal object AndroidCaTrust {

    private const val TAG = "VelioraCaTrust"
    private const val ENV_NAME = "SOFUU_CA_BUNDLE"

    // System store first; the APEX copy is the same set on Android 14+, and
    // the per-user directory holds CAs the owner installed. Duplicates are
    // dropped by filename.
    private val SOURCES = listOf(
        "/system/etc/security/cacerts",
        "/apex/com.android.conscrypt/cacerts",
        "/data/misc/user/0/cacerts-added",
    )

    fun install(context: Context) {
        val bundle = ensureBundle(context)
        if (bundle == null) {
            Log.e(TAG, "No readable system CA certificates; HTTPS in the embedded runtime will fail")
            return
        }
        try {
            Os.setenv(ENV_NAME, bundle.absolutePath, true)
        } catch (error: Exception) {
            Log.e(TAG, "Could not export $ENV_NAME", error)
        }
    }

    private fun ensureBundle(context: Context): File? {
        val dir = File(context.filesDir, "ca").apply { mkdirs() }
        val out = File(dir, "cacert.pem")
        val stamp = File(dir, "cacert.stamp")
        val sources = SOURCES.map(::File).filter { it.isDirectory }
        if (sources.isEmpty()) return null

        // Rebuild only when the visible anchor set changed. Listing the
        // directories is cheap; reading ~150 PEM files on every launch is not.
        val key = sources.joinToString("|") { source ->
            val names = runCatching {
                source.list()?.sorted()?.joinToString(",")
            }.getOrNull().orEmpty()
            "${source.path}:${source.lastModified()}:$names"
        }
        if (out.isFile && out.length() > 0 &&
            runCatching { stamp.readText() }.getOrNull() == key
        ) {
            return out
        }

        return runCatching {
            val byName = LinkedHashMap<String, File>()
            for (source in sources) {
                for (file in source.listFiles().orEmpty()) {
                    if (file.isFile) byName.putIfAbsent(file.name, file)
                }
            }

            val temp = File(dir, "cacert.pem.tmp")
            temp.outputStream().buffered().use { output ->
                for (file in byName.values) {
                    val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
                    if (bytes.isEmpty()) continue
                    if (!String(bytes, Charsets.ISO_8859_1).contains("BEGIN CERTIFICATE")) continue
                    output.write(bytes)
                    if (bytes.last() != '\n'.code.toByte()) output.write('\n'.code)
                }
            }

            if (temp.length() == 0L) {
                temp.delete()
                out.delete()
                stamp.delete()
                null
            } else {
                if (out.exists()) out.delete()
                if (!temp.renameTo(out)) {
                    temp.copyTo(out, overwrite = true)
                    temp.delete()
                }
                stamp.writeText(key)
                out
            }
        }.onFailure {
            Log.e(TAG, "Failed to build the Android CA bundle", it)
        }.getOrNull()
    }
}
