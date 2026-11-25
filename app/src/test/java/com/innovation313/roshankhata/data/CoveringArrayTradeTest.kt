package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The phone's trade logic walked over docs/covering-array.tsv (plan B4, 24 Nov 2026; the shared half is
 * CoveringArrayLogicTest). Each row sets its trade and country and checks TradeFeatures' switches, TradeVocab's lists
 * in the row's language and that the unit list leads with the trade's units. Not run where it was written (needs Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class CoveringArrayTradeTest {

    private class Row(val id: String, val language: String, val trade: String, val country: String)

    private val rows: List<Row> by lazy {
        val f = listOf(File("../docs/covering-array.tsv"), File("docs/covering-array.tsv")).first { it.isFile }
        val lines = f.readLines().filter { it.isNotBlank() }
        val head = lines.first().split('\t')
        lines.drop(1).map { l -> val m = head.zip(l.split('\t')).toMap(); Row(m.getValue("row"), m.getValue("language"), m.getValue("trade"), m.getValue("country")) }
    }

    private val expiryTrades = setOf("AGRI", "MEDICAL", "KIRYANA", "DAIRY", "BAKERY", "MEAT", "PRODUCE", "COSMETICS", "RESTAURANT", "WHOLESALE", "GENERAL", "OTHER")

    private fun localised(tag: String): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    @Test
    fun `the array's trades are exactly the enum's`() {
        assertEquals(Trade.entries.map { it.name }.sorted(), rows.map { it.trade }.distinct().sorted())
    }

    @Test
    fun `every row's trade switches and words hold`() {
        val app: Context = ApplicationProvider.getApplicationContext()
        val before = ShopCountry.current
        val locale = Locale.getDefault()
        try {
            for (r in rows) {
                val trade = Trade.valueOf(r.trade)
                val country = ShopCountry.valueOf(r.country)
                BusinessProfile.setTrade(app, trade)
                ShopCountry.current = country
                Locale.setDefault(Locale.forLanguageTag(r.language))
                val ctx = localised(r.language)
                assertEquals("${r.id} seasons", trade == Trade.AGRI && country.seasons, TradeFeatures.seasons(ctx))
                assertEquals("${r.id} spray", trade == Trade.AGRI, TradeFeatures.sprayAdvice(ctx))
                assertEquals("${r.id} inspector", trade == Trade.AGRI || trade == Trade.MEDICAL, TradeFeatures.inspectorReports(ctx))
                assertEquals("${r.id} expiry daily", r.trade in expiryTrades, TradeFeatures.expiryDaily(ctx))
                assertEquals("${r.id} items", 3, TradeVocab.items(ctx, trade).size)
                assertTrue("${r.id} starters", TradeVocab.starters(ctx, trade).size >= 7)
                val labels = TradeVocab.unitLabels(ctx, trade)
                assertTrue("${r.id} unit labels", labels.isNotEmpty() && labels.none { it.isBlank() })
                assertEquals("${r.id} the unit list starts with the trade's units", labels, TradeVocab.unitChoices(ctx).take(labels.size))
                assertTrue("${r.id} hint", TradeVocab.itemHint(ctx).contains(TradeVocab.items(ctx, trade).first()))
            }
        } finally {
            ShopCountry.current = before
            Locale.setDefault(locale)
        }
    }
}
