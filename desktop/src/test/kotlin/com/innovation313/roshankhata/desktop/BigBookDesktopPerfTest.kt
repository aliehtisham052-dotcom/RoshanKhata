package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.BigBookProbe
import com.innovation313.roshankhata.data.BigBookSeed
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.syncSql
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * The computer against a wholesaler's book (3 Nov 2026, the speed work): the phone's big book
 * ([BigBookSeed]: 10,000 customers, 300,000 lines, goods, bills, a cashbook, counter tickets) written into
 * a book in a temporary folder — never the owner's — and every heavy read the pages make timed by
 * [BigBookProbe], the same list the phone's on-device test runs; then Home's own figures, and the whole
 * book backed up, read back and restored into a second book, which must be the same book row by row
 * ([LiveSync.fingerprint]): a backup that cannot bring a big book back whole is data lost.
 *
 * The numbers go to build/perf/bigbook-desktop.txt, which desktop.yml shows as one annotation whether this
 * passes or not: a slow run then says by how much. CI's Windows runner is not the owner's laptop; the
 * figures are for comparing one build with the next.
 */
class BigBookDesktopPerfTest {

    private val rt = Runtime.getRuntime()
    private fun heapMb() = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
    private fun since(t: Long) = (System.nanoTime() - t) / 1_000_000

    private suspend fun count(db: KhataDatabase, table: String): Long =
        db.syncSql().read { rows("SELECT COUNT(*) FROM `$table`", types = "I").first()[0] as Long }

    @Test
    fun `a wholesaler's book is read quickly and its backup brings it back whole`() = runBlocking {
        val dir = Files.createTempDirectory("rk-bigbook").toFile()
        val report = ArrayList<String>()
        val out = File("build/perf/bigbook-desktop.txt").also { it.parentFile.mkdirs(); it.delete() }
        var same = false
        var probeRan = false
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        val copy = DesktopDatabase.open(dir.resolve("restored.db"))
        try {
            var t = System.nanoTime()
            val customers = BigBookSeed.seed(db)
            report += "Seed: $customers customers in ${since(t)} ms; " + listOf(
                "transactions", "entry_items", "products", "supplier_bills", "bill_items", "sales", "sale_items", "cashbook"
            ).joinToString(", ") { "$it ${count(db, it)}" }

            report += BigBookProbe.run(db, desktopWords(Locale.ENGLISH)).map { it.toString() }
            probeRan = true

            // The Khata page's search runs on the window's own thread, once per key (shownBy): what a key waits for.
            val parties = db.khataDao().partiesWithBalanceOnce()
            for (q in listOf("b", "bil", "thalewala")) {
                var keyBest = Long.MAX_VALUE
                repeat(BigBookProbe.RUNS) {
                    val k = System.nanoTime()
                    parties.shownBy(q, PartySide.ALL, PartyType.ALL, PartySort.RECENT)
                    keyBest = minOf(keyBest, System.nanoTime() - k)
                }
                report += "Khata search on the window's thread, \"$q\": " + "%.1f".format(Locale.ROOT, keyBest / 1e6) + " ms"
            }

            // Windows' own Home read (homeFigures): the cashbook's sums, the drawer, today's and the last entries.
            var best = Long.MAX_VALUE
            var first = -1L
            repeat(BigBookProbe.RUNS) {
                val h = System.nanoTime()
                homeFigures(db.khataDao())
                val ms = since(h)
                if (first < 0) first = ms
                best = minOf(best, ms)
            }
            report += "Home (Windows): every figure: $best ms" + if (first > best) " (first $first)" else ""

            // The whole book out, read back, and into a second book.
            val file = dir.resolve("backup.txt")
            t = System.nanoTime()
            file.bufferedWriter(Charsets.UTF_8).use { BackupCodec.exportTo(db.khataDao(), "Test shop", JSONObject(), it) }
            report += "Backup of the whole book: ${since(t)} ms, ${file.length() / 1_048_576} MB"
            System.gc()
            val before = heapMb()
            t = System.nanoTime()
            val (result, data) = BackupCodec.parseFile(file)
            val parsed = since(t)
            val held = heapMb()
            assertTrue("the backup did not read back: $result", result is BackupCodec.ImportResult.Ok && data != null)
            t = System.nanoTime()
            BackupCodec.restoreLedger(copy.khataDao(), data!!)
            report += "Restore: read in $parsed ms (heap $before -> $held MB of ${rt.maxMemory() / 1_048_576}), written in ${since(t)} ms"
            t = System.nanoTime()
            val a = LiveSync.fingerprint(db.syncSql())
            val b = LiveSync.fingerprint(copy.syncSql())
            same = a == b
            report += "Restored book the same, row by row: ${if (same) "yes" else "NO"} (compared in ${since(t)} ms)"
            report += "Book file: ${dir.resolve("khata.db").length() / 1_048_576} MB"
        } catch (e: Throwable) {
            report += "STOPPED: ${e.javaClass.simpleName}: ${e.message}"
            throw e
        } finally {
            out.writeText(report.joinToString("\n") + "\n", Charsets.UTF_8)
            println(report.joinToString("\n"))
            copy.close()
            db.close()
            dir.deleteRecursively()
        }
        assertTrue("the probe did not run", probeRan)
        assertEquals("the restored big book differs from the one backed up", true, same)
    }
}
