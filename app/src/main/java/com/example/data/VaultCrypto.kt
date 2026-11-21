package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "currency_sentinel_vault_v3_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "ENC_V3:"

    private val softwareFallbackKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("CurrencySentinel-V3-Offline-Forensic-Vault".toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    private fun getOrCreateKey(): SecretKey {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            gen.init(spec)
            gen.generateKey()
        }.getOrElse {
            softwareFallbackKey
        }
    }

    fun encryptBytes(plain: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        // [1 byte ivLen][iv][cipherText]
        return ByteArray(1 + iv.size + cipherText.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(cipherText, 0, this, 1 + iv.size, cipherText.size)
        }
    }

    fun decryptBytes(payload: ByteArray): ByteArray {
        if (payload.size < 14) return payload
        val ivLen = payload[0].toInt() and 0xFF
        if (ivLen !in 12..16 || payload.size <= 1 + ivLen) return payload
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val cipherText = payload.copyOfRange(1 + ivLen, payload.size)

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(cipherText)
        }.getOrElse {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, softwareFallbackKey, GCMParameterSpec(128, iv))
            cipher.doFinal(cipherText)
        }
    }

    fun encryptString(plain: String): String {
        val enc = encryptBytes(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    fun decryptString(encoded: String): String {
        if (!encoded.startsWith(PREFIX)) return encoded
        val raw = Base64.decode(encoded.removePrefix(PREFIX), Base64.NO_WRAP)
        return String(decryptBytes(raw), Charsets.UTF_8)
    }
}
