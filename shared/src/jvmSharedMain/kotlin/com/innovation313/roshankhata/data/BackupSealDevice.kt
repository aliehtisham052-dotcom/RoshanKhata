package com.innovation313.roshankhata.data

/**
 * The device's own lock for the backup password ([BackupPassword]): the phone's Android Keystore, Windows' DPAPI —
 * the same [KeyVault] that seals the sync key. Where the device has none (a Linux test) [wrap] is null and no password
 * is kept; a lock that does not answer throws [KeyLockUnavailable], which [BackupPassword.state] reads as Unreadable.
 */
object DeviceBackupLock : BackupPassword.Lock {
    override fun wrap(plain: ByteArray): ByteArray? = KeyVault.wrap(plain)
    override fun unwrap(wrapped: ByteArray): ByteArray? = KeyVault.unwrap(wrapped)
}
