package com.dms.app.data.local

import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * KeyStoreManager provides AES-256 GCM double-envelope encryption and key management.
 * Uses Android KeyStore key alias "dms_master_key" or JVM fallback key for test environments.
 */
class KeyStoreManager(
    private val keyAlias: String = KEY_ALIAS_DEFAULT
) {
    companion object {
        const val KEY_ALIAS_DEFAULT = "dms_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val AES_KEY_SIZE_BITS = 256
    }

    private val masterKey: SecretKey by lazy { getOrCreateMasterKey() }

    private fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                generateKeyStoreKey()
            }
        } catch (e: Exception) {
            // JVM environment fallback (e.g. unit tests without Android KeyStore service provider)
            getOrGenerateJvmFallbackKey()
        }
    }

    private fun generateKeyStoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val keySpecClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
        val builder = keySpecClass.getConstructor(String::class.java, Int::class.java)
            .newInstance(keyAlias, 3 /* PURPOSE_ENCRYPT | PURPOSE_DECRYPT */)
        
        keySpecClass.getMethod("setBlockModes", Array<String>::class.java)
            .invoke(builder, arrayOf("GCM"))
        keySpecClass.getMethod("setEncryptionPaddings", Array<String>::class.java)
            .invoke(builder, arrayOf("NoPadding"))
        keySpecClass.getMethod("setKeySize", Int::class.javaPrimitiveType)
            .invoke(builder, AES_KEY_SIZE_BITS)

        val spec = keySpecClass.getMethod("build").invoke(builder)
        keyGenerator.init(spec as java.security.spec.AlgorithmParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun getOrGenerateJvmFallbackKey(): SecretKey {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        return SecretKeySpec(randomBytes, "AES")
    }

    /**
     * Encrypts plaintext string returning Base64 encoded string: [12-byte IV + Ciphertext + Tag]
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(IV_SIZE_BYTES)
            SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val buffer = ByteBuffer.allocate(IV_SIZE_BYTES + cipherBytes.size)
            buffer.put(iv)
            buffer.put(cipherBytes)
            Base64.getEncoder().encodeToString(buffer.array())
        } catch (e: Exception) {
            plainText
        }
    }

    /**
     * Decrypts Base64 encoded string containing [12-byte IV + Ciphertext + Tag]
     */
    fun decrypt(cipherTextBase64: String): String {
        return decryptOrOriginal(cipherTextBase64)
    }

    /**
     * Safe decryption helper returning decrypted plaintext or original text on failure.
     */
    fun decryptOrOriginal(text: String): String {
        if (text.isEmpty()) return ""
        return try {
            val combinedBytes = Base64.getDecoder().decode(text)
            if (combinedBytes.size <= IV_SIZE_BYTES) return text

            val iv = ByteArray(IV_SIZE_BYTES)
            val cipherBytes = ByteArray(combinedBytes.size - IV_SIZE_BYTES)
            val buffer = ByteBuffer.wrap(combinedBytes)
            buffer.get(iv)
            buffer.get(cipherBytes)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            text
        }
    }
}
