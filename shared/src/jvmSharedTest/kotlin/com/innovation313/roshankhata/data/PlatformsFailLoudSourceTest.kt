package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BC-256 (24 Nov 2026), the platforms' side: the phone's sheet and the computer's panel may not answer against an empty book after a
 * failed read, and the computer reads his sense through [HudhudSenseState] as the phone does. The app and desktop files cannot be compiled
 * by the local harness (nor are they in this module), so this reads their sources, as [AnswerFailsLoudSourceTest] does.
 */
class PlatformsFailLoudSourceTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory && File(it, "desktop/src/main/kotlin").isDirectory }

    private fun text(rel: String) = File(root, rel).readText().replace("\r\n", "\n")

    private val sheet = "app/src/main/java/com/innovation313/roshankhata/ui/HudhudSheet.kt"
    private val pane = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/HudhudPane.kt"
    private val main = "app/src/main/java/com/innovation313/roshankhata/MainActivity.kt"

    @Test fun `the sheet reads the book without falling back to empty`() {
        val t = text(sheet)
        for (read in listOf("partiesWithBalanceOnce()", "productsOnce().flatMap", "staffCardsOnce()")) {
            val at = t.indexOf(read)
            assertTrue("$read is read in the sheet", at > 0)
            val line = t.substring(t.lastIndexOf('\n', at) + 1, t.indexOf('\n', at))
            assertFalse("a failed read must not become an empty list: $line", line.contains("emptyList()") && line.contains("catch"))
        }
        // A failed answer reads the book again, so one failure does not spoil every later question.
        assertTrue(t.contains("rereadBook()"))
        assertTrue("the failure card is the sheet's answer to a failed read", t.contains("HudhudBook.failed(ctx)"))
    }

    @Test fun `the panel reads the sense through the state and its book without runCatching`() {
        val t = text(pane)
        assertTrue(t.contains("HudhudSenseState.reading("))
        assertFalse("sense.bin is not read through a bare runCatching", t.contains("runCatching { HudhudDeskEnv::class.java.getResourceAsStream"))
        assertFalse(t.contains("runCatching { dao.productsOnce()"))
        assertFalse(t.contains("runCatching { if (AnswerGate.staff())"))
        assertTrue("a book that failed to read says so", t.contains("AnswerBook.failed(env)"))
    }

    @Test fun `home's warm-up notes a missing model at start`() {
        assertTrue(text(main).contains("HudhudSenseState.check()"))
    }
}
