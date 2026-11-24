package com.innovation313.roshankhata.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The pure halves of stage 7's F2 and F3 (24 Nov 2026): the failure words ([HudhudReadFailure]) and the note that the learnt sense
 * is missing ([HudhudSenseState]). The answer layer itself (AnswerBook over Room) cannot be compiled by the local harness, so what
 * it is held to is in [AnswerFailsLoudSourceTest]; the platforms' catch is read in the report, not run.
 */
class AnswerFailsLoudPolicyTest {

    // --- F2: the words of a read that failed ---

    @Test fun `a failure says the book could not be read, never that nothing was found`() {
        val known = mapOf("hudhud_read_failed" to "could not read", "hudhud_unknown" to "not found")
        assertEquals("could not read", HudhudReadFailure.words { known.getValue(it) })
    }

    @Test fun `until the key is in the strings the old words are used rather than a crash`() {
        val old = mapOf("hudhud_unknown" to "not found")
        // A word lookup that throws for an unknown name, as the phone's WordIds and the computer's XmlWords do.
        assertEquals("not found", HudhudReadFailure.words { old[it] ?: throw IllegalArgumentException(it) })
        // A blank answer is no answer.
        assertEquals("not found", HudhudReadFailure.words { if (it == "hudhud_read_failed") "  " else "not found" })
    }

    @Test fun `best effort gives its fallback for a failed read and the value for a good one`() {
        assertEquals(listOf(1), HudhudReadFailure.bestEffort(emptyList<Int>()) { listOf(1) })
        assertEquals(emptyList<Int>(), HudhudReadFailure.bestEffort(emptyList<Int>()) { throw IllegalStateException("no") })
        assertNull(HudhudReadFailure.bestEffort<String?>(null) { throw RuntimeException() })
    }

    @Test fun `best effort never swallows a cancellation`() {
        try {
            HudhudReadFailure.bestEffort(0) { throw CancellationException("cancelled") }
            fail("a cancelled read went on as an empty one")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    // --- F3: the sense is missing ---

    private fun withNoSense(read: () -> ByteArray?, then: (told: MutableList<String>) -> Unit) {
        val told = ArrayList<String>()
        HudhudSenseState.forgetForTest()
        HudhudSenseState.sink = { told.add(it) }
        try {
            HudhudSense.useModel(HudhudSenseState.reading("hudhud/sense.bin", read))
            then(told)
        } finally {
            HudhudSenseState.forgetForTest()
            HudhudSense.use(HudhudModelTest.shippedModel())
        }
    }

    @Test fun `a missing asset is told once and the mode is rules only`() = withNoSense({ null }) { told ->
        assertEquals(HudhudSenseState.Mode.RULES_ONLY, HudhudSenseState.check())
        assertTrue(HudhudSenseState.rulesOnly)
        assertTrue("the reason is the loader's own: ${HudhudSenseState.reason}", HudhudSenseState.reason!!.contains("not in this build"))
        HudhudSenseState.check(); HudhudSenseState.check()
        assertEquals("said once for the process, not once per sentence: $told", 1, told.size)
        assertTrue(told[0], told[0].contains("rules alone"))
    }

    @Test fun `a loader that throws is told with the exception's name`() = withNoSense({ throw java.io.IOException("x") }) { told ->
        assertEquals(HudhudSenseState.Mode.RULES_ONLY, HudhudSenseState.check())
        assertTrue(told.single(), told.single().contains("IOException"))
    }

    @Test fun `understand notes the missing sense on its own`() = withNoSense({ null }) { told ->
        Hudhud.understand("kitne customers hain", listOf(Hudhud.Name(1, "Ali Raza")), emptyList(), "ur-Latn", emptyList(), null)
        assertTrue("a sentence read with no sense was never noted", HudhudSenseState.rulesOnly)
        assertEquals(1, told.size)
    }

    @Test fun `with the sense there is nothing to tell`() {
        HudhudSenseState.forgetForTest()
        val told = ArrayList<String>()
        HudhudSenseState.sink = { told.add(it) }
        try {
            HudhudSense.use(HudhudModelTest.shippedModel())
            assertEquals(HudhudSenseState.Mode.WITH_SENSE, HudhudSenseState.check())
            assertFalse(HudhudSenseState.rulesOnly)
            assertNull(HudhudSenseState.reason)
            assertTrue(told.isEmpty())
        } finally {
            HudhudSenseState.forgetForTest()
        }
    }
}
