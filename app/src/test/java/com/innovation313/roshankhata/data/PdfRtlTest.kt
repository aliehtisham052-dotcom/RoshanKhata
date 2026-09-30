package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which app languages print their PDFs right to left. */
class PdfRtlTest {

    @Test
    fun urdu_sindhi_farsi_arabic_are_right_to_left() {
        listOf("ur", "ur-PK", "sd", "fa", "ar", "ar-SA").forEach {
            assertTrue("$it should be RTL", PdfRtl.isRtlTag(it))
        }
    }

    @Test
    fun english_and_roman_urdu_stay_left_to_right() {
        // "b+ur+Latn" in res/ is the tag ur-Latn at runtime.
        listOf("en", "en-US", "ur-Latn", "ur_Latn", "").forEach {
            assertFalse("'$it' should be LTR", PdfRtl.isRtlTag(it))
        }
    }

    @Test
    fun an_explicit_script_wins_over_the_language() {
        assertTrue(PdfRtl.isRtlTag("pa-Arab"))
        assertFalse(PdfRtl.isRtlTag("ar-Latn"))
    }
}
