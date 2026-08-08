package com.shellbox.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts sensitive strings (server passwords, private key content,
 * key passphrases) using an AES-256-GCM key that lives in the Android Keystore
 * and never leaves the device's secure hardware (when available).
 *
 * Ciphertext format persisted to the DB: base64(IV) + ":" + base64(ciphertext+tag).
 * Storing the IV alongside the ciphertext is required for GCM decryption and is
 * not itself sensitive.
 *
 * Plaintext-looking values (e.g. empty strings, or values already in the
 * "iv:ciphertext" shape from a previous run) are handled defensively so that
 * decrypting non-encrypted legacy data doesn't crash — see [decryptOrPassthrough].
 */
@Singleton
class CryptoManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "shellbox_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val SEPARATOR = ":"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Encrypts [plainText]. Returns an empty string for empty input (no need to encrypt "nothing"). */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value produced by [encrypt]. If [storedValue] doesn't look like
     * ciphertext (e.g. empty, or legacy plaintext from before encryption was added),
     * it's returned as-is rather than throwing — this makes the migration from
     * plaintext-stored fields to encrypted fields safe for existing installs.
     */
    fun decryptOrPassthrough(storedValue: String): String {
        if (storedValue.isEmpty()) return ""
        val parts = storedValue.split(SEPARATOR, limit = 2)
        if (parts.size != 2) return storedValue // not our format — legacy plaintext
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            // Corrupt entry, key invalidated (e.g. device credentials changed), or
            // genuinely legacy plaintext that happened to contain a ':' — fail safe
            // by returning the raw stored value instead of crashing the app.
            storedValue
        }
    }
}
