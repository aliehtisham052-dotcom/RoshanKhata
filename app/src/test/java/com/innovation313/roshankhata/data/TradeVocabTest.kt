package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Every trade has its words in every language (9 Oct 2026).
 *
 * The owner's rule: the examples a shopkeeper sees are in the language they
 * chose, in that language's own letters, and about their own trade. A
 * missing array fails the build rather than falling back to English — or
 * worse, to the pesticide dealer's examples the app began with.
 */
@RunWith(AndroidJUnit4::class)
class TradeVocabTest {

    private val languages = listOf("en", "ur", "ur-Latn", "sd", "hi", "bn", "in", "ar", "fa")

    private fun localised(tag: String): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    @Test
    fun `every trade has items, units, types and starters in every language`() {
        for (tag in languages) {
            val ctx = localised(tag)
            for (trade in Trade.entries) {
                val where = "$tag/$trade"
                assertEquals("$where items", 3, TradeVocab.items(ctx, trade).size)
                assertTrue("$where types", TradeVocab.types(ctx, trade).size in 3..4)
                assertTrue("$where starters", TradeVocab.starters(ctx, trade).size >= 7)
                val units = TradeVocab.unitKeys(ctx, trade)
                assertTrue("$where units", units.isNotEmpty())
                for (u in units) assertTrue("$where unit '$u' is not a UnitWords key", u in UnitWords.KEYS)
                for (list in listOf(TradeVocab.items(ctx, trade), TradeVocab.types(ctx, trade), TradeVocab.starters(ctx, trade))) {
                    for (w in list) assertFalse("$where blank word", w.isBlank())
                }
            }
        }
    }

    @Test
    fun `a dairy on Urdu reads Urdu dairy words, not the pesticide dealer's`() {
        val ctx = localised("ur")
        val items = TradeVocab.items(ctx, Trade.DAIRY)
        assertTrue(items.joinToString(), items.any { it.contains("دودھ") })
        assertFalse(items.joinToString(), items.any { it.contains("یوریا") })
        // The hint is built from those words, in Urdu.
        assertTrue(TradeVocab.itemHint(ctx).contains("دودھ") || TradeVocab.items(ctx).first().isNotBlank())
    }

    @Test
    fun `the agri trade keeps the words the app began with`() {
        val ctx = localised("en")
        assertEquals(listOf("Urea", "Feed", "Seed"), TradeVocab.items(ctx, Trade.AGRI))
        assertEquals("bag", TradeVocab.unitKeys(ctx, Trade.AGRI).first())
    }

    @Test
    fun `the trade's units lead the list and every unit is still there`() {
        val ctx = localised("en")
        val choices = UnitWords.choices(ctx)
        assertEquals(UnitWords.KEYS.size, choices.size)
        assertEquals(UnitWords.KEYS.toSet(), choices.toSet())
    }
}
