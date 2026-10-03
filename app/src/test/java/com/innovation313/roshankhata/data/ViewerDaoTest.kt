package com.innovation313.roshankhata.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * The helper's read-only phone (3 Oct 2026), held to its one promise: it can
 * read everything and change nothing.
 *
 * Two halves. The first runs the real thing on a real (in-memory) database:
 * reads pass through, writes — including whole transactions — leave every row
 * as it was. The second reads KhataDao.kt itself and checks that EVERY method
 * that writes is refused by ViewerDao, so adding a write to the DAO without
 * re-running scripts/gen_viewer_dao.py fails here instead of quietly handing
 * helpers a way to change the book.
 */
@RunWith(AndroidJUnit4::class)
class ViewerDaoTest {

    private lateinit var db: KhataDatabase
    private lateinit var real: KhataDao
    private lateinit var viewer: KhataDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java
        ).allowMainThreadQueries().build()
        real = db.ledgerDao()
        viewer = ViewerDao(real)
    }

    @After
    fun close() = db.close()

    private fun refused(block: suspend () -> Unit): Boolean = try {
        runBlocking { block() }
        false
    } catch (e: CancellationException) {
        true
    }

    @Test
    fun anOrdinaryDatabaseHandsOutTheRealDao() {
        // Every other phone, and every other test, must be untouched by this.
        assertFalse(db.viewerCopy)
        assertFalse(db.khataDao() is ViewerDao)
    }

    @Test
    fun readsPassThrough() = runBlocking {
        val id = real.insertParty(Party(name = "Test Kisan"))
        assertNotNull(viewer.getParty(id))
        assertEquals(1, viewer.partiesWithBalanceOnce().size)
    }

    @Test
    fun aWriteIsRefusedAndChangesNothing() {
        val id = runBlocking { real.insertParty(Party(name = "Test Kisan")) }
        val before = runBlocking { real.getParty(id) }!!

        assertTrue(refused { viewer.insertParty(Party(name = "Someone Else")) })
        assertTrue(refused { viewer.updateParty(before.copy(name = "Renamed")) })
        assertTrue(refused { viewer.softDeleteParty(id, System.currentTimeMillis()) })

        runBlocking {
            assertEquals(1, real.partiesWithBalanceOnce().size)
            assertEquals(before, real.getParty(id))
        }
    }

    @Test
    fun aWholeTransactionIsRefusedToo() {
        // restoreAll is a default method: delegated as-is it would run on the
        // REAL dao and wipe the book. It must be refused like any other write.
        runBlocking { real.insertParty(Party(name = "Test Kisan")) }
        assertTrue(refused {
            viewer.restoreAll(
                parties = emptyList(), entries = emptyList(), cheques = emptyList(),
                cash = emptyList(), plans = emptyList(), installments = emptyList(),
                bills = emptyList(), billItems = emptyList(), products = emptyList(),
                invoices = emptyList(), invoiceItems = emptyList(),
                dismissedDuplicates = emptyList(), entryItems = emptyList(),
                dayCloses = emptyList(), schemes = emptyList()
            )
        })
        runBlocking { assertEquals(1, real.partiesWithBalanceOnce().size) }
    }

    // ---------- Coverage: every write in KhataDao is refused ----------

    private fun dataDir(): File =
        listOf(
            File("src/main/java/com/innovation313/roshankhata/data"),
            File("app/src/main/java/com/innovation313/roshankhata/data")
        ).first { it.isDirectory }

    private fun stripComments(s: String) =
        s.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    /** The same rule as scripts/gen_viewer_dao.py, written independently. */
    private fun writesInKhataDao(): Set<String> {
        val src = stripComments(File(dataDir(), "KhataDao.kt").readText())
        val funs = Regex("""\bfun\s+(\w+)\s*\(""").findAll(src).toList()
        val direct = mutableSetOf<String>()
        val bodies = mutableMapOf<String, String>()
        var prevEnd = 0
        funs.forEachIndexed { k, m ->
            val name = m.groupValues[1]
            var i = m.range.last + 1
            var depth = 1
            while (depth > 0) {
                if (src[i] == '(') depth++ else if (src[i] == ')') depth--
                i++
            }
            val annotations = src.substring(prevEnd, m.range.first)
            val next = if (k + 1 < funs.size) funs[k + 1].range.first else src.length
            val tail = src.substring(i, next)
            val hasBody = Regex("""^\s*(:\s*[\w<>?,\s.\[\]]+?)?\s*[{=]""").containsMatchIn(tail)
            if (hasBody) {
                bodies[name] = tail
            } else {
                // The window runs from the end of the previous method's
                // parameters to this method, so the LAST Room annotation in
                // it is this method's own.
                val last = Regex("""@(Insert|Update|Delete|Upsert)\b|@Query\(\s*"{1,3}\s*(\w+)""")
                    .findAll(annotations).lastOrNull()
                if (last != null) {
                    val verb = last.groupValues[2].uppercase()
                    if (!last.value.startsWith("@Query") ||
                        verb in setOf("UPDATE", "DELETE", "INSERT", "REPLACE")
                    ) direct += name
                }
            }
            prevEnd = i
        }
        val writes = direct.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            for ((name, body) in bodies) {
                if (name in writes) continue
                if (writes.any { Regex("""\b${Regex.escape(it)}\s*\(""").containsMatchIn(body) }) {
                    writes += name
                    changed = true
                }
            }
        }
        return writes
    }

    private fun refusedInViewerDao(): Set<String> =
        Regex("""override suspend fun (\w+)\(""")
            .findAll(File(dataDir(), "ViewerDao.kt").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun everyWriteInTheDaoIsRefused() {
        val writes = writesInKhataDao()
        // A sanity floor: if the scan finds almost nothing, the scan is broken.
        assertTrue("found only ${writes.size} writes", writes.size >= 100)
        val missing = writes - refusedInViewerDao()
        assertTrue(
            "Writes a read-only phone could still make — run scripts/gen_viewer_dao.py: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun noReadIsRefused() {
        // Refusing a read would break a screen on the helper's phone.
        val extra = refusedInViewerDao() - writesInKhataDao()
        assertTrue("ViewerDao refuses methods that only read: $extra", extra.isEmpty())
    }
}
