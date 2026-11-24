package com.innovation313.roshankhata.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * What runs after a restored ledger committed, on the computer (BC-258, the phone's BC-245 rule): every step is tried
 * whatever the one before did, every failure is noted, and a failing step never turns a replaced ledger into "not restored".
 */
class RestoreAfterCommitDeskTest {

    private val noted = ArrayList<String>()
    private val note: (String, Throwable) -> Unit = { where, _ -> noted += where }

    @Test
    fun `a failing first step does not stop the ones after it`() = runBlocking {
        val ran = ArrayList<String>()
        val result = RestoreAfterCommit.run(listOf(
            RestoreAfterCommit.Step("restore end live sync") { ran += "live"; error("the book is busy") },
            RestoreAfterCommit.Step("restore clear number mark") { ran += "mark" },
            RestoreAfterCommit.Step(RestoreAfterCommit.PROFILE) { ran += "profile" }
        ), note)
        assertEquals(listOf("live", "mark", "profile"), ran)
        assertEquals(listOf("restore end live sync"), result.failed)
        assertEquals(listOf("restore end live sync"), noted)
        assertFalse(result.allDone)
        assertFalse("the profile was written", result.profileNotSet)
    }

    @Test
    fun `a profile that cannot be written is said, and everything else still ran`() = runBlocking {
        var removed = false
        val result = RestoreAfterCommit.run(listOf(
            RestoreAfterCommit.Step(RestoreAfterCommit.PROFILE) { throw java.io.IOException("disk full") },
            RestoreAfterCommit.Step("restore remove paged copy") { removed = true }
        ), note)
        assertTrue(result.profileNotSet)
        assertTrue(removed)
        assertEquals(listOf(RestoreAfterCommit.PROFILE), noted)
    }

    @Test
    fun `every step done is all done`() = runBlocking {
        val result = RestoreAfterCommit.run(listOf(RestoreAfterCommit.Step(RestoreAfterCommit.PROFILE) { }), note)
        assertTrue(result.allDone)
        assertFalse(result.profileNotSet)
        assertTrue(noted.isEmpty())
    }

    @Test
    fun `a note that itself fails stops nothing`() = runBlocking {
        var second = false
        val result = RestoreAfterCommit.run(listOf(
            RestoreAfterCommit.Step("a") { error("x") },
            RestoreAfterCommit.Step("b") { second = true }
        ), { _, _ -> throw IllegalStateException("log unwritable") })
        assertTrue(second)
        assertEquals(listOf("a"), result.failed)
    }

    @Test
    fun `a cancellation is not swallowed`() {
        try {
            runBlocking {
                RestoreAfterCommit.run(listOf(RestoreAfterCommit.Step("a") { throw CancellationException("closed") }), note)
            }
            fail("the cancellation was swallowed")
        } catch (e: CancellationException) {
            assertTrue(noted.isEmpty())
        }
    }

    @Test
    fun `a callback that fails is noted and answers false`() {
        assertFalse(RestoreAfterCommit.guarded("restore refresh page", note) { error("the page is gone") })
        assertEquals(listOf("restore refresh page"), noted)
        assertTrue(RestoreAfterCommit.guarded("restore refresh page", note) { })
    }

    @Test
    fun `the restore's callers use it, and no step after the commit stands alone`() {
        // The two places that write after a committed ledger go through it (BC-258): the daily-copy restore and the rebuild.
        // Gradle runs a module's tests in the module's folder; a run from the repository's root finds it under desktop/.
        val rel = "src/main/kotlin/com/innovation313/roshankhata/desktop"
        val dir = listOf(java.io.File(rel), java.io.File("desktop/$rel")).first { it.isDirectory }
        val copies = dir.resolve("DailyCopies.kt").readText().replace("\r\n", "\n")
        val health = dir.resolve("BookHealth.kt").readText().replace("\r\n", "\n")
        assertTrue(copies.contains("RestoreAfterCommit.run("))
        assertTrue(health.contains("RestoreAfterCommit.run("))
        // No bare profile write is left beside the ledger's: the only restoreProfile call sits inside a step.
        for (src in listOf(copies, health)) {
            for (m in Regex("""BackupCodec\.restoreProfile""").findAll(src)) {
                val before = src.substring(maxOf(0, m.range.first - 200), m.range.first)
                assertTrue("restoreProfile outside a RestoreAfterCommit step", before.contains("RestoreAfterCommit.Step"))
            }
        }
    }
}
