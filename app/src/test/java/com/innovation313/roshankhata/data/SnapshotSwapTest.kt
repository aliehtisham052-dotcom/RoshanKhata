package com.innovation313.roshankhata.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * BC-245 (24 Nov 2026): a daily-copy restore deletes nothing of the live book until the new file is in place, and a
 * rename that fails leaves the book and its write-ahead log exactly as they were. Plain files, made-up content.
 */
class SnapshotSwapTest {
    private lateinit var dir: File
    private lateinit var live: File
    private lateinit var tmp: File
    private lateinit var wal: File
    private lateinit var shm: File
    private lateinit var aside: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("rk-swap").toFile()
        live = File(dir, "khata.db").also { it.writeText("old book") }
        tmp = File(dir, "khata.db.restoring").also { it.writeText("copy") }
        wal = File(dir, "khata.db-wal").also { it.writeText("old writes") }
        shm = File(dir, "khata.db-shm").also { it.writeText("index") }
        aside = File(dir, "khata.db-wal.old")
    }

    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun nothingOfTheLiveBookIsDeletedBeforeTheNewFileIsInPlace() {
        var seenAtRename = false
        val ok = SnapshotSwap.swap(live, tmp) { from, to ->
            if (from == tmp) {
                // The moment of the rename: the old book is still the book, its log is kept (aside), the copy waits.
                assertEquals("old book", live.readText())
                assertTrue(aside.exists())
                assertEquals("old writes", aside.readText())
                assertTrue(tmp.exists())
                seenAtRename = true
            }
            from.renameTo(to)
        }
        assertTrue(seenAtRename)
        assertTrue(ok)
        assertEquals("copy", live.readText())
        assertFalse("no old log may meet the new book", wal.exists())
        assertFalse(aside.exists())
        assertFalse(shm.exists())
    }

    @Test fun aRenameThatFailsPutsTheLogBackAndChangesNothing() {
        val ok = SnapshotSwap.swap(live, tmp) { from, to -> if (from == tmp) false else from.renameTo(to) }
        assertFalse(ok)
        assertEquals("old book", live.readText())
        assertEquals("old writes", wal.readText())
        assertFalse(aside.exists())
        assertTrue("the copy stays for the owner", tmp.exists())
    }

    @Test fun aRenameThatThrowsAlsoPutsTheLogBack() {
        try {
            SnapshotSwap.swap(live, tmp) { from, to -> if (from == tmp) throw java.io.IOException("disk") else from.renameTo(to) }
            org.junit.Assert.fail("the failure is not hidden")
        } catch (_: java.io.IOException) {}
        assertEquals("old book", live.readText())
        assertEquals("old writes", wal.readText())
        assertFalse(aside.exists())
    }

    @Test fun aLogThatCannotBeMovedAsideLeavesTheBookAlone() {
        val ok = SnapshotSwap.swap(live, tmp) { from, to -> if (from == wal) false else from.renameTo(to) }
        assertFalse(ok)
        assertEquals("old book", live.readText())
        assertEquals("old writes", wal.readText())
        assertTrue(tmp.exists())
    }

    @Test fun aBookWithNoLogSwapsAsBefore() {
        wal.delete(); shm.delete()
        assertTrue(SnapshotSwap.swap(live, tmp))
        assertEquals("copy", live.readText())
        assertFalse(aside.exists())
    }

    @Test fun aRunKilledBeforeTheRenameGetsItsLogBack() {
        // The state a kill between "log aside" and "rename" leaves: old book, log aside, the copy still waiting.
        wal.renameTo(aside)
        shm.delete()
        SnapshotSwap.recover(live)
        assertEquals("old writes", wal.readText())
        assertFalse(aside.exists())
        assertEquals("old book", live.readText())
    }

    @Test fun aRunKilledAfterTheRenameDropsTheOldLog() {
        // Rename done (no ".restoring" any more), the aside log not yet deleted.
        wal.renameTo(aside)
        tmp.delete()
        live.writeText("copy")
        SnapshotSwap.recover(live)
        assertFalse(aside.exists())
        assertFalse(wal.exists())
        assertEquals("copy", live.readText())
    }

    @Test fun aKilledRunsLogIsNotLostByTheNextSwap() {
        wal.renameTo(aside)
        // A new attempt: it takes the earlier log back first, then keeps it through its own failed rename.
        val ok = SnapshotSwap.swap(live, tmp) { from, to -> if (from == tmp) false else from.renameTo(to) }
        assertFalse(ok)
        assertEquals("old writes", wal.readText())
    }

    @Test fun theRestoreCallsTheOrderedSwapAndNoDeleteBeforeIt() {
        // The restore's source: the log is never deleted by the swap step any more (CRLF-safe, BC-257).
        val src = File("src/main/java/com/innovation313/roshankhata/data/Snapshots.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/innovation313/roshankhata/data/Snapshots.kt")
        val text = src.readText().replace("\r\n", "\n")
        val swapIn = text.substring(text.indexOf("private fun swapIn"), text.indexOf("private fun readAccess"))
        assertTrue(swapIn.contains("SnapshotSwap.swap(live, tmp)"))
        assertFalse("swapIn must not delete the live log itself", swapIn.contains("-wal\").delete()"))
        assertFalse(swapIn.contains("-shm\").delete()"))
    }
}
