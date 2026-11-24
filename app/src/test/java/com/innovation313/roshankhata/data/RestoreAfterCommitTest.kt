package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BC-245 (24 Nov 2026): after the restored ledger has committed, a failing step never makes the restore throw, never
 * stops the steps after it, and a profile that cannot be written says so in the outcome.
 */
class RestoreAfterCommitTest {
    private val noted = ArrayList<String>()
    private fun run(live: (suspend () -> Unit)?, pics: (suspend () -> Unit)?, profile: (suspend () -> Unit)?) =
        runBlocking { RestoreAfterCommit.run(live, pics, profile) { where, _ -> noted += where } }

    @Test fun everyStepRunsInOrderAndTheOutcomeIsRestored() {
        val order = ArrayList<String>()
        val out = run({ order += "live" }, { order += "pics" }, { order += "profile" })
        assertEquals(listOf("live", "pics", "profile"), order)
        assertEquals(RestoreAfterCommit.Outcome.RESTORED, out)
        assertTrue(noted.isEmpty())
    }

    @Test fun aProfileFailureIsRestoredWithoutProfileNeverAThrow() {
        val out = run(null, null) { throw IllegalStateException("prefs full") }
        assertEquals(RestoreAfterCommit.Outcome.RESTORED_PROFILE_NOT_SET, out)
        assertEquals(listOf("restore profile"), noted)
    }

    @Test fun anEarlierFailureDoesNotSkipTheProfile() {
        var wrote = false
        val out = run({ throw java.io.IOException("relay") }, { throw RuntimeException("pictures") }, { wrote = true })
        assertTrue("the profile is still written", wrote)
        assertEquals(RestoreAfterCommit.Outcome.RESTORED, out)
        assertEquals(listOf("restore end live sync", "restore reattach pictures"), noted)
    }

    @Test fun noProfileInTheFileLeavesTheOldOneAndIsRestored() {
        assertEquals(RestoreAfterCommit.Outcome.RESTORED, run(null, null, null))
    }

    @Test fun aCancellationIsNotSwallowed() {
        try {
            run({ throw kotlinx.coroutines.CancellationException("gone") }, null, null)
            org.junit.Assert.fail("cancellation passes through")
        } catch (_: kotlinx.coroutines.CancellationException) {}
    }

    @Test fun theBackupRestoreRoutesEverythingAfterTheCommitThroughIt() {
        val f = java.io.File("src/main/java/com/innovation313/roshankhata/data/Backup.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/innovation313/roshankhata/data/Backup.kt")
        val text = f.readText().replace("\r\n", "\n")
        val body = text.substring(text.indexOf("suspend fun restore(context"), text.indexOf("suspend fun liveOn"))
        val afterCommit = body.substring(body.indexOf("BackupCodec.restoreLedger"))
        assertTrue(afterCommit.contains("RestoreAfterCommit.run("))
        // No bare profile write or live disable left outside the guarded steps' lambdas.
        assertEquals(1, Regex("restoreBusinessProfile\\(").findAll(afterCommit).count())
        assertEquals(1, Regex("LiveSync\\.disable\\(").findAll(afterCommit).count())
    }
}
