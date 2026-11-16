package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The answer layer counts from one clock (17 Nov 2026, audit L035): [AnswerEnv.now]. A card that read the system clock
 * itself — "today", "this month", "a few minutes ago", a span of days, the drawer, this month's profit — could not be held
 * still by a test and would cross midnight in the middle of one (the golden answers skip the half hour around midnight for
 * it). This reads the answer files' source and fails for a clock read anywhere but [AnswerEnv]'s own default, and for a call
 * that takes the system clock by default without being given the env's: [Hudhud.span], [HudhudWhyAsk.spans],
 * [DayCloseMath.drawerByTheBooks], [Profit.thisMonth] and [Profit.lastMonth] (both read the calendar themselves).
 *
 * Drift protection, not proof (lesson 125): a new clock-reading helper outside these names is not seen.
 */
class HudhudAnswerClockTest {

    private val dir: File by lazy { File(HudhudRealPages.root, "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data") }

    private val files: List<File> by lazy {
        dir.listFiles { f -> f.name.startsWith("Answer") && f.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }
    }

    /** Each source line that is not a comment, with its file and number. */
    private fun code(): List<Triple<String, Int, String>> = files.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, line ->
            val t = line.trim()
            if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")) null else Triple(f.name, i + 1, line)
        }
    }

    @Test
    fun theAnswerFilesAreFound() {
        val names = files.map { it.name }
        for (n in listOf("AnswerBook.kt", "AnswerFacts.kt", "AnswerCounterFacts.kt", "AnswerRecordFacts.kt", "AnswerWhy.kt", "AnswerEnv.kt")) {
            assertTrue("$n not found in $dir", n in names)
        }
    }

    @Test
    fun noAnswerReadsTheSystemClockButTheEnvsDefault() {
        val bad = code().filter { (file, _, line) ->
            ("System.currentTimeMillis()" in line || "Instant.now()" in line || "LocalDate.now()" in line || "System.nanoTime()" in line) &&
                !(file == "AnswerEnv.kt" && "fun now(): Long = System.currentTimeMillis()" in line)
        }
        assertEquals("clock reads outside AnswerEnv.now()", emptyList<String>(), bad.map { "${it.first}:${it.second}: ${it.third.trim()}" })
    }

    @Test
    fun aCalendarIsSetBeforeItIsRead() {
        // Calendar.getInstance() is the present moment until something sets it: every one in the answers is set from the
        // env's clock or a stored moment on the same line (timeInMillis = …, or clear() before a date is set).
        val bad = code().filter { (_, _, line) ->
            "Calendar.getInstance(" in line && !("timeInMillis" in line || "clear()" in line)
        }.filterNot { (file, n, _) ->
            // A Calendar whose next line sets its moment (`.apply {` then `timeInMillis = c.now()`).
            val next = File(dir, file).readLines().getOrNull(n)?.trim().orEmpty()
            next.startsWith("timeInMillis =")
        }
        assertEquals("Calendar.getInstance() left on the system clock", emptyList<String>(), bad.map { "${it.first}:${it.second}: ${it.third.trim()}" })
    }

    @Test
    fun everySpanAndDrawerIsGivenTheEnvsClock() {
        val defaulting = listOf(
            Regex("""Hudhud\.span\((?![^()]*(?:\([^()]*\))?[^()]*,\s*\w+\.now\(\))"""),
            Regex("""HudhudWhyAsk\.spans\((?![^)]*now\(\))"""),
            Regex("""drawerByTheBooks\(\s*dao\s*\)"""),
            Regex("""Profit\.(?:thisMonth|lastMonth)\("""),
        )
        val bad = code().filter { (_, _, line) -> defaulting.any { it.containsMatchIn(line) } }
        assertEquals("calls left on the system clock", emptyList<String>(), bad.map { "${it.first}:${it.second}: ${it.third.trim()}" })
    }
}
