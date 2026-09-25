package com.example.myapplication.shared.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the session JSON (token + user + menu_permissions, one blob) encrypted with an
 * Android Keystore AES-GCM key — the key material never leaves hardware-backed storage. Plain
 * SharedPreferences would work but stores the token in clear text on disk; this mirrors the
 * intent of mobile/lib/core/storage/session_store.dart's FlutterSecureStorage without adding a
 * new Gradle dependency (androidx.security:security-crypto's stable history is still thin).
 */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun save(sessionJson: String) {
        val (ivB64, cipherB64) = encrypt(sessionJson)
        prefs.edit().putString(KEY_IV, ivB64).putString(KEY_DATA, cipherB64).apply()
    }

    fun read(): String? {
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        val cipherB64 = prefs.getString(KEY_DATA, null) ?: return null
        return runCatching { decrypt(ivB64, cipherB64) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_IV).remove(KEY_DATA).apply()
    }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(ivB64: String, cipherB64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val bytes = cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP))
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val PREFS_NAME = "logi_session"
        private const val KEY_ALIAS = "logi_session_key"
        private const val KEY_IV = "iv"
        private const val KEY_DATA = "data"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
