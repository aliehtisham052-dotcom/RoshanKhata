package com.innovation313.roshankhata.data

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString

/**
 * The computer's lock: Windows' own DPAPI (CryptProtectData), which seals
 * for the signed-in Windows user — another user on the same computer, or a
 * copy of the file elsewhere, cannot open it, and no key of ours is kept
 * anywhere. Reached through JNA; nothing but Crypt32 and Kernel32, which
 * every Windows has. On a computer that is not Windows
 * (the tests on Linux) there is no lock: null, and the key stays plain.
 */
actual object KeyVault {
    private val windows = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

    /** Ties the sealed key to this app: another program of the same user cannot open it by DPAPI alone. */
    private val ENTROPY = "roshan-khata-sync-key-v1".toByteArray(Charsets.US_ASCII)
    private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    @Structure.FieldOrder("cbData", "pbData")
    class DataBlob : Structure() {
        @JvmField var cbData: Int = 0
        @JvmField var pbData: Pointer? = null

        companion object {
            fun of(bytes: ByteArray): DataBlob = DataBlob().apply {
                val m = Memory(maxOf(1, bytes.size).toLong())
                m.write(0, bytes, 0, bytes.size)
                pbData = m
                cbData = bytes.size
                write()
            }
        }

        fun bytes(): ByteArray = pbData?.getByteArray(0, cbData) ?: ByteArray(0)
    }

    private interface Crypt32 : com.sun.jna.Library {
        fun CryptProtectData(
            pDataIn: DataBlob, szDataDescr: WString?, pOptionalEntropy: DataBlob?, pvReserved: Pointer?,
            pPromptStruct: Pointer?, dwFlags: Int, pDataOut: DataBlob
        ): Boolean
        fun CryptUnprotectData(
            pDataIn: DataBlob, ppszDataDescr: Pointer?, pOptionalEntropy: DataBlob?, pvReserved: Pointer?,
            pPromptStruct: Pointer?, dwFlags: Int, pDataOut: DataBlob
        ): Boolean
    }

    private interface Kernel32 : com.sun.jna.Library {
        fun LocalFree(hMem: Pointer?): Pointer?
    }

    private val crypt32: Crypt32 by lazy { Native.load("Crypt32", Crypt32::class.java) }
    private val kernel32: Kernel32 by lazy { Native.load("Kernel32", Kernel32::class.java) }

    /** JNA's library or Windows' call not reachable (quarantined, a locked TEMP): the lock did not answer. */
    private fun <T> withLock(block: () -> T): T = try {
        block()
    } catch (e: KeyLockUnavailable) {
        throw e
    } catch (e: Throwable) {
        // UnsatisfiedLinkError, NoClassDefFoundError, a JNA Error: never a reason to keep the key plain.
        throw KeyLockUnavailable("dpapi: " + (e.message ?: e.javaClass.simpleName), e)
    }

    /** On Windows the lock is always there: a refusal to seal is [KeyLockUnavailable], never a plain key. */
    actual fun wrap(plain: ByteArray): ByteArray? {
        if (!windows) return null
        return withLock {
            val out = DataBlob()
            if (!crypt32.CryptProtectData(DataBlob.of(plain), WString("Roshan Khata sync key"), DataBlob.of(ENTROPY), null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
                throw KeyLockUnavailable("dpapi: CryptProtectData refused")
            }
            out.read()
            try { out.bytes() } finally { kernel32.LocalFree(out.pbData) }
        }
    }

    /**
     * DPAPI's answers that mean "this blob is not for this Windows user", for good: not ours or damaged
     * (ERROR_INVALID_DATA, NTE_BAD_DATA, NTE_BAD_KEY), and NTE_BAD_KEY_STATE — sealed by another user or
     * another computer, or a master key gone for good after an administrator reset the password (Microsoft's
     * KB 904800: "Key not valid for use in specified state" for another user's data). Waiting cannot open those.
     */
    private val NOT_THIS_DEVICE = setOf(13, 0x80090005.toInt(), 0x80090003.toInt(), 0x8009000B.toInt())

    /**
     * Null when Windows will not open it for this user (another user's, another computer's — not this device's).
     * Any other refusal — the profile's key files not reachable yet, the protection service not answering — is the
     * lock not answering ([KeyLockUnavailable]): a null there turned live sync off and un-paired the shop
     * (23 Oct 2026, the review's R97; lesson 48).
     */
    actual fun unwrap(wrapped: ByteArray): ByteArray? {
        if (!windows) return null
        return withLock {
            val out = DataBlob()
            if (!crypt32.CryptUnprotectData(DataBlob.of(wrapped), null, DataBlob.of(ENTROPY), null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
                // Read at once: JNA keeps the call's GetLastError for this thread until the next native call.
                val err = Native.getLastError()
                if (err in NOT_THIS_DEVICE) return@withLock null
                throw KeyLockUnavailable("dpapi: CryptUnprotectData error " + Integer.toHexString(err))
            }
            out.read()
            try { out.bytes() } finally { kernel32.LocalFree(out.pbData) }
        }
    }
}
