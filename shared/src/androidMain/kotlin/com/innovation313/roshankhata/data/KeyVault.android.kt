package com.innovation313.roshankhata.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The phone's lock: an AES-256 key that never leaves the Android Keystore
 * (hardware-backed on phones that have it), made once for this install and
 * gone with it. Every wrap takes a fresh nonce from the Keystore itself
 * (randomized encryption is required of a Keystore AES-GCM key).
 */
actual object KeyVault {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "rk-sync-key-vault"
    private const val NONCE = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** The phone always has the Keystore: a failure to seal is the lock not answering, never a reason to keep the key plain. */
    actual fun wrap(plain: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val nonce = c.iv
        require(nonce.size == NONCE)
        nonce + c.doFinal(plain)
    } catch (e: Exception) {
        throw KeyLockUnavailable("keystore: " + (e.message ?: e.javaClass.simpleName), e)
    }

    /** Null only when the Keystore's key is not the one that sealed it (another install's file); any other trouble is [KeyLockUnavailable]. */
    actual fun unwrap(wrapped: ByteArray): ByteArray? {
        if (wrapped.size < NONCE + TAG_BITS / 8) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE))
            c.doFinal(wrapped, NONCE, wrapped.size - NONCE)
        } catch (e: AEADBadTagException) {
            null
        } catch (e: Exception) {
            throw KeyLockUnavailable("keystore: " + (e.message ?: e.javaClass.simpleName), e)
        }
    }
}
