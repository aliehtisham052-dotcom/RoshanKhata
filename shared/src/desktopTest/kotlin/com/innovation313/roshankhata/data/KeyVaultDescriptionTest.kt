package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * DPAPI keeps one description beside every blob the computer's lock seals — the sync key and the backup password
 * alike (17 Nov 2026, the review: the password's blob said "sync key"). It names neither.
 */
class KeyVaultDescriptionTest {
    @Test fun `the description names neither the sync key nor the password`() {
        val d = KeyVault.DESCRIPTION.lowercase()
        for (w in listOf("sync", "password", "backup")) assertFalse("$w in \"${KeyVault.DESCRIPTION}\"", d.contains(w))
    }
}
