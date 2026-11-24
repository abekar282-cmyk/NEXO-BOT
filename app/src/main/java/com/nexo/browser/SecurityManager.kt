package com.nexo.browser

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
}

class AndroidKeyStoreKeyProvider(
    private val alias: String = KEY_ALIAS
) : SecretKeyProvider {

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexo_proxy_master_key_v1"
    }
}

class InMemoryTestKeyProvider(
    rawKey: ByteArray = ByteArray(32) { idx -> (idx * 7 + 13).toByte() }
) : SecretKeyProvider {
    private val secretKey = SecretKeySpec(rawKey.copyOf(32), "AES")
    override fun getOrCreateKey(): SecretKey = secretKey
}

/**
 * Encrypts and decrypts sensitive proxy credentials using AES-256-GCM backed by AndroidKeyStore.
 */
class SecurityManager(
    private val keyProvider: SecretKeyProvider = defaultKeyProvider()
) {

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = keyProvider.getOrCreateKey()

        // AndroidKeyStore with randomizedEncryptionRequired generates its own IV on init(ENCRYPT_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        if (!cipherText.startsWith(PREFIX)) {
            return cipherText
        }
        val payload = Base64.decode(cipherText.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH) { "Corrupted encrypted credential payload" }

        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val encryptedBytes = payload.copyOfRange(GCM_IV_LENGTH, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateKey(), spec)
        val decrypted = cipher.doFinal(encryptedBytes)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun maskCredential(secret: String): String {
        return if (secret.isBlank()) "" else "••••••••"
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "ENCv1:"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        fun defaultKeyProvider(): SecretKeyProvider {
            return try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                AndroidKeyStoreKeyProvider()
            } catch (_: Throwable) {
                // Only reached on host JVM unit test runners where AndroidKeyStore is absent
                val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
                InMemoryTestKeyProvider(random)
            }
        }
    }
}
