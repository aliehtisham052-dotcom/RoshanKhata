package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Each row of Hudhud's check list says how its question came in — voice, typed or a chip (25 Nov 2026, Marhala 2: a
 * recogniser's mistake is not an understanding mistake, so the log must tell them apart). Older lists of seven and eight
 * fields still read, mark and count. Made-up questions.
 */
class CheckLogInputTest {

    private fun add(lines: List<String>, q: String, input: CheckLog.Input?) =
        CheckLog.add(lines, "2026-11-25 09:00", "ur", q, "figure: SALES", "Sale", 1L, input).first

    @Test
    fun aRowSaysHowItWasAsked() {
        var lines = add(emptyList(), "aaj ki sale", CheckLog.Input.VOICE)
        lines = add(lines, "aaj ki sale", CheckLog.Input.TYPED)
        lines = add(lines, "Aaj ka hisaab", CheckLog.Input.CHIP)
        lines = add(lines, "q", null)
        assertEquals(CheckLog.HEADER, lines.first())
        assertEquals("input", CheckLog.HEADER.split('\t')[8])
        assertEquals(CheckLog.FIELDS, CheckLog.HEADER.split('\t').size)
        assertEquals(listOf(CheckLog.Input.VOICE, CheckLog.Input.TYPED, CheckLog.Input.CHIP, null),
            lines.drop(1).map { CheckLog.inputOf(it) })
        assertEquals(listOf("voice", "typed", "chip", ""), lines.drop(1).map { it.split('\t')[8] })
        lines.drop(1).forEach { assertEquals(CheckLog.FIELDS, it.split('\t').size) }
        // The business is still the eighth field.
        assertEquals(1L, CheckLog.businessOf(lines[1]))
    }

    @Test
    fun aMarkKeepsTheInput() {
        var lines = add(emptyList(), "aaj ki sale", CheckLog.Input.VOICE)
        lines = CheckLog.mark(lines, 1, right = false)
        assertEquals(CheckLog.Input.VOICE, CheckLog.inputOf(lines[1]))
        assertEquals("wrong", lines[1].split('\t')[6])
        assertEquals(CheckLog.Counts(asked = 1, right = 0, wrong = 1), CheckLog.counts(lines))
    }

    @Test
    fun olderListsOfSevenAndEightFieldsStillRead() {
        val old = listOf(
            "no\ttime\tlanguage\tquestion\ttaken_as\tanswer\tmark\tbusiness",
            "1\t2026-11-14 09:00\tur\taaj ki sale\tfigure: SALES_TODAY\tSale\tright",
            "2\t2026-11-16 09:01\tur\tkal ki sale\tfigure: SALES\tSale\t\t2"
        )
        assertEquals(CheckLog.Counts(asked = 2, right = 1, wrong = 0), CheckLog.counts(old))
        assertNull(CheckLog.inputOf(old[1]))
        assertNull(CheckLog.inputOf(old[2]))
        assertEquals(2L, CheckLog.businessOf(old[2]))
        val marked = CheckLog.mark(old, 2, right = false)
        assertEquals(CheckLog.Counts(asked = 2, right = 1, wrong = 1), CheckLog.counts(marked))
        assertNull(CheckLog.inputOf(marked[2]))
        assertEquals(2L, CheckLog.businessOf(marked[2]))
        // The next row goes on under the new header, with its input; the old rows stay as they were.
        val (more, next) = CheckLog.add(marked, "t", "en", "q", "x", "y", 1L, CheckLog.Input.VOICE)
        assertEquals(3, next)
        assertEquals(CheckLog.HEADER, more.first())
        assertEquals(CheckLog.Input.VOICE, CheckLog.inputOf(more.last()))
        assertEquals(old[1], more[1])
        assertEquals(3, CheckLog.counts(more).asked)
    }

    @Test
    fun onlyTheThreeWordsAreAnInput() {
        assertEquals(CheckLog.Input.CHIP, CheckLog.Input.of(" chip "))
        assertNull(CheckLog.Input.of("Voice?"))
        assertNull(CheckLog.Input.of(""))
        assertNull(CheckLog.Input.of(null))
    }
}
