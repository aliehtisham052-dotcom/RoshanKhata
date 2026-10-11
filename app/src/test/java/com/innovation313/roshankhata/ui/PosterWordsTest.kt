package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.PosterFields
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Locale

/**
 * What a poster prints for its fields (12 Oct): the week grouped into runs,
 * times said the way each language says them, figures always 0–9, and the
 * saved fields surviving a round trip.
 */
@RunWith(AndroidJUnit4::class)
class PosterWordsTest {

    private fun localised(tag: String): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    @Test
    fun `open days group into runs from Monday`() {
        val c = Calendar.getInstance()
        assertEquals(listOf(listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY)),
            PosterWords.runs(PosterWords.WEEK.toSet() - Calendar.SUNDAY))
        assertEquals(2, PosterWords.runs(PosterWords.WEEK.toSet() - Calendar.WEDNESDAY).size)
        assertEquals(listOf(listOf(Calendar.FRIDAY)), PosterWords.runs(setOf(Calendar.FRIDAY)))
        assertTrue(PosterWords.runs(emptySet()).isEmpty())
        c.clear()
    }

    @Test
    fun `urdu says the time as people do`() {
        val ur = localised("ur")
        assertEquals("صبح 9 بجے", PosterWords.time(ur, 9 * 60))
        assertEquals("صبح 9 تا رات 8 بجے", PosterWords.range(ur, 9 * 60, 20 * 60))
        assertEquals("دوپہر 1 تا 2:30 بجے", PosterWords.range(ur, 13 * 60, 14 * 60 + 30))
        assertEquals("subah 9 se raat 8 baje", PosterWords.range(localised("ur-Latn"), 9 * 60, 20 * 60))
    }

    @Test
    fun `english prints a clock`() {
        val en = localised("en")
        assertEquals("9:00 AM – 8:00 PM", PosterWords.range(en, 9 * 60, 20 * 60))
        assertEquals("12:00 AM", PosterWords.time(en, 0))
    }

    @Test
    fun `the default timings read Mon to Sat, the prayer break and Sunday closed`() {
        val en = localised("en")
        val rows = PosterWords.hoursRows(en, PosterFields())
        assertEquals(3, rows.size)
        assertEquals("Mon – Sat", rows[0].label)
        assertEquals("Friday prayers", rows[1].label)
        assertEquals("Sunday", rows[2].label)
        assertTrue(rows[2].closed)
        val ur = PosterWords.hoursRows(localised("ur"), PosterFields())
        assertEquals("صبح 9 تا رات 8 بجے", ur[0].value)
    }

    @Test
    fun `closed says why and when, in the poster's language`() {
        val f = PosterFields(reason = PosterFields.Reason.WEDDING.key, reopen = PosterFields.Reopen.TOMORROW.key, reopenAt = 9 * 60)
        val ur = PosterWords.closedRows(localised("ur"), f)
        assertEquals("خاندان میں شادی", ur[0].value)
        assertEquals("کل صبح 9 بجے", ur[1].value)
        val en = PosterWords.closedRows(localised("en"), f.copy(reopen = ""))
        assertEquals(1, en.size)
        val own = PosterWords.closedRows(localised("en"), f.copy(reason = PosterFields.CUSTOM_REASON, reasonText = "  Stocktaking "))
        assertEquals("Stocktaking", own[0].value)
    }

    @Test
    fun `every language prints its figures in 0-9`() {
        val f = PosterFields(ramazanHours = true, eidDays = 3)
        for (tag in listOf("en", "ur", "ur-Latn", "ar", "sd", "fa", "hi", "bn", "in")) {
            val ctx = localised(tag)
            val text = (PosterWords.hoursRows(ctx, f) + PosterWords.ramazanRows(ctx, f) + PosterWords.closedRows(ctx, f))
                .joinToString(" ") { it.label + " " + it.value } + " " + PosterWords.eidNote(ctx, f)
            val bad = text.filter { it.isDigit() && it !in '0'..'9' }
            assertTrue("$tag printed non-Latin digits: $text", bad.isEmpty())
            assertTrue("$tag: no 3 in the Eid note", PosterWords.eidNote(ctx, f)!!.contains("3") || tag == "ar")
        }
    }

    @Test
    fun `fields survive a round trip and bad values fall back`() {
        val f = PosterFields(items = listOf("Urea", "DAP"), reason = "eid", holidays = setOf(Calendar.FRIDAY), eidDays = 3, showPhone = false)
        assertEquals(f, PosterFields.fromJson(JSONObject(f.toJson().toString())))
        val bad = PosterFields.fromJson(JSONObject("""{"opens": 99999, "holidays": [0, 9, 6], "eidDays": 50}"""))
        assertEquals(PosterFields().opens, bad.opens)
        assertEquals(setOf(6), bad.holidays)
        assertEquals(10, bad.eidDays)
    }
}
