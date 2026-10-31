package com.veliora.data.sofuu

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App-private secret storage backed by Android Keystore AES/GCM.
 *
 * Values are encrypted before they touch SharedPreferences. Nothing here is
 * uploaded; the ciphertext only ever feeds the embedded Sofuu runtime.
 */
object SecretStore {

    private const val TAG = "VelioraSecrets"
    private const val PREFS = "veliora_secrets"
    private const val KEY_ALIAS = "veliora_secret_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val VERSION = "v1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun put(context: Context, name: String, value: String) {
        if (value.isBlank()) {
            remove(context, name)
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = VERSION + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        prefs(context).edit().putString(name, encoded).apply()
    }

    fun get(context: Context, name: String): String? {
        val encoded = prefs(context).getString(name, null) ?: return null
        if (!encoded.startsWith(VERSION)) return null
        return runCatching {
            val bytes = Base64.decode(encoded.removePrefix(VERSION), Base64.NO_WRAP)
            if (bytes.size <= IV_BYTES) return null
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val payload = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.onFailure {
            Log.e(TAG, "Could not decrypt secret '$name'", it)
        }.getOrNull()
    }

    fun remove(context: Context, name: String) {
        prefs(context).edit().remove(name).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
