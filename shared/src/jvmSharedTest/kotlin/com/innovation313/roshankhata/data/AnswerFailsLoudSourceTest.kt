package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The answer layer catches nothing on its own (24 Nov 2026, stage 7 F2): AnswerBook, AnswerFacts, AnswerCounterFacts,
 * AnswerRecordFacts, AnswerWhy and AnswerQ may not turn a failed read into an empty one. A read that throws goes up to the
 * platform's one catch, which shows `AnswerBook.failed`. This test reads the sources (the local harness cannot compile the
 * Room-bound files, and a source scan needs no compile): no `runCatching`, no `getOrNull`/`getOrDefault` on a
 * result (List.getOrElse is an index, not a failure), no `catch (`, and `HudhudReadFailure.bestEffort` only at the places listed here — each says nothing when empty.
 * Broken on purpose once: a `runCatching` put back in AnswerBook, and a fourth `bestEffort`, each failed.
 */
class AnswerFailsLoudSourceTest {

    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data") }
        .first { it.isDirectory }

    private val files get() = dir.listFiles { f -> f.name.startsWith("Answer") && f.name.endsWith(".kt") }!!.sortedBy { it.name }

    /** Code lines only: no comment lines, no text after a `//`. */
    private fun codeLines(f: File): List<Pair<Int, String>> = f.readLines().mapIndexedNotNull { i, l ->
        val t = l.trim()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) null else (i + 1) to l.substringBefore(" // ")
    }

    @Test fun `the answer files are found`() {
        assertTrue(files.map { it.name }.toString(), files.size >= 5)
        assertTrue(files.any { it.name == "AnswerBook.kt" })
    }

    @Test fun `no read is caught and made empty`() {
        val hits = files.flatMap { f ->
            codeLines(f).filter { (_, l) ->
                l.contains("runCatching") || l.contains("getOrDefault(") || l.contains("catch (") ||
                    l.contains(".getOrNull()")
            }.map { (n, l) -> "${f.name}:$n ${l.trim()}" }
        }
        assertTrue("a failed read is told as a failure, never as none:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `best effort is used in three named places only`() {
        val hits = files.flatMap { f ->
            codeLines(f).filter { (_, l) -> l.contains("HudhudReadFailure.bestEffort") }.map { (_, l) -> f.name to l.trim() }
        }
        assertEquals(hits.toString(), 3, hits.size)
        val bookHits = hits.filter { it.first == "AnswerBook.kt" }.map { it.second }
        assertTrue("the day's chips", bookHits.any { it.contains("c.alerts(dao)") })
        assertTrue("the voice's own words", bookHits.any { it.contains("own()") })
        assertTrue("the version name", hits.any { it.first == "AnswerFacts.kt" && it.second.contains("appVersion()") })
    }

    @Test fun `the failure card uses the failure words`() {
        val book = File(dir, "AnswerBook.kt").readText().replace("\r\n", "\n")
        val failed = book.substringAfter("fun failed(").substringBefore("\n\n")
        assertTrue(failed, failed.contains("HudhudReadFailure.words"))
        assertTrue("the old 'I could not find this' words are not the failure's", !failed.contains("W.hudhud_unknown"))
    }
}
