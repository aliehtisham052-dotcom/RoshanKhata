package com.innovation313.roshankhata.ui

import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The computer prints figures and names with BidiPort, the phone with
 * androidx BidiFormatter. Both must give the same marks for the strings
 * the app wraps (phone numbers, names with brackets, mixed scripts), in a
 * left-to-right and a right-to-left language alike.
 */
@RunWith(AndroidJUnit4::class)
class BidiPortTest {

    private val saved = Locale.getDefault()

    @After
    fun back() = Locale.setDefault(saved)

    private val samples = listOf(
        "", " ", "0300 1234567", "+92 300 7654321", "Rs 4,598", "1205",
        "Ali", "(Shari)Touseef", "علی", "(شاری)توصیف", "علی Traders", "Bhatti علی",
        "Ali (علی)", "علی (Ali)", "12 علی", "علی 12", "-", "+ Rs 10", "— 5",
        "‫علی‬", "‪Ali‬", "a‫ب‬c", "ب‪a‬ج",
        "‪‫ب‬‬", "0300-1234567 / Ali", "مسجد road 7", "x‏y"
    )

    private val tags = listOf("en", "ur", "ur-Latn", "ar", "sd", "fa", "hi", "bn", "in")

    private fun show(s: String) = s.map { if (it.code in 0x200E..0x202E) "<%04X>".format(it.code) else it.toString() }.joinToString("")

    @Test
    fun `the computer's copy wraps exactly as the phone's BidiFormatter`() {
        for (tag in tags) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals("$tag direction", BidiFormatter.getInstance().isRtlContext, BidiPort.isRtlLocale(Locale.getDefault()))
            for (s in samples) {
                val phoneLtr = BidiFormatter.getInstance().unicodeWrap(s, TextDirectionHeuristicsCompat.LTR)
                val phoneAuto = BidiFormatter.getInstance().unicodeWrap(s)
                assertEquals("$tag ltr '${show(s)}'", show(phoneLtr), show(BidiPort.ltr(s)))
                assertEquals("$tag auto '${show(s)}'", show(phoneAuto), show(BidiPort.auto(s)))
            }
        }
    }
}
