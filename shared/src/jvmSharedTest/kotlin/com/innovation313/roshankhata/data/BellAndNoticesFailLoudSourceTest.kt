package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BC-256 (24 Nov 2026), the part the Answer files' scan does not reach: LedgerAlerts (the bell and the daily count) and Automations (the
 * notices) may not turn a failed read into an empty one. Both are Room-bound and cannot be compiled by the local harness, so this reads the
 * sources, as [AnswerFailsLoudSourceTest] does: no `runCatching`, no `getOrDefault(` / `.getOrNull()`, and every `catch` that takes an
 * Exception also lets cancellation go on up. The bell's one allowed catch is `readOrNull`, which says its null with [LedgerAlerts.unreadRow].
 */
class BellAndNoticesFailLoudSourceTest {

    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data") }
        .first { it.isDirectory }

    private fun text(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    private fun code(name: String): List<Pair<Int, String>> = text(name).lines().mapIndexedNotNull { i, l ->
        val t = l.trim()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) null else (i + 1) to l.substringBefore(" // ")
    }

    @Test fun `the bell and the notices catch no read into an empty list`() {
        val hits = listOf("LedgerAlerts.kt", "Automations.kt").flatMap { f ->
            code(f).filter { (_, l) -> l.contains("runCatching") || l.contains("getOrDefault(") || l.contains(".getOrNull()") }
                .map { (n, l) -> "$f:$n ${l.trim()}" }
        }
        assertTrue("a failed read is told as a failure, never as none:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `every catch of an exception lets a cancellation go on`() {
        for (f in listOf("LedgerAlerts.kt", "Automations.kt")) {
            val t = text(f)
            val catches = Regex("catch \\(e: Exception\\)").findAll(t).map { it.range.first }.toList()
            for (at in catches) {
                val before = t.substring(maxOf(0, at - 200), at)
                assertTrue("$f: a catch of Exception at $at has no CancellationException above it:\n$before", before.contains("CancellationException"))
            }
        }
    }

    @Test fun `the bell says a shelf it could not read`() {
        val bell = text("LedgerAlerts.kt")
        assertTrue("compute reads the shelf through readOrNull", bell.contains("val low = readOrNull { Stock.of(dao) }"))
        assertTrue("and says it when it failed", bell.contains("out += unreadRow(words)"))
        // The daily count has no such row: it propagates, so a sweep that cannot count leaves yesterday's notice standing.
        val counts = bell.substringAfter("suspend fun dueCounts").substringBefore("suspend fun compute")
        assertTrue(counts, counts.contains("val low = Stock.of(dao)") && !counts.contains("readOrNull"))
    }

    @Test fun `a notice whose read failed is not marked said`() {
        val a = text("Automations.kt")
        val loop = a.substringAfter("for (kind in Kind.values())").substringBefore("return out")
        assertTrue(loop, loop.contains("onFailed(kind, e)") && loop.contains("continue"))
        // setSaid comes after the catch that continues, never before the notice was made.
        assertTrue(loop, loop.indexOf("continue") < loop.indexOf("settings.setSaid"))
    }
}
