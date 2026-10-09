package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The trade picker's cards (9 Oct): every trade has its own icon and its
 * own name and example in every language, so no card ever shows a blank,
 * an English fallback in a translated list, or another trade's picture.
 */
@RunWith(AndroidJUnit4::class)
class TradeCardTest {

    private val locales = listOf("en", "ur", "ur-Latn", "ar", "sd", "fa", "hi", "bn", "in")

    private fun localised(tag: String): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    @Test
    fun `every card has a name and an example in every language`() {
        for (tag in locales) {
            val ctx = localised(tag)
            val names = Trade.entries.map { ctx.getString(it.nameRes) }
            for ((i, t) in Trade.entries.withIndex()) {
                assertTrue("$tag ${t.name} name blank", names[i].isNotBlank())
                assertTrue("$tag ${t.name} example blank", ctx.getString(t.exampleRes).isNotBlank())
            }
            assertEquals("$tag: two cards share a name", names.size, names.toSet().size)
        }
    }

    @Test
    fun `every card has its own icon and its colours resolve`() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        assertEquals(Trade.entries.size, Trade.entries.map { it.iconRes }.toSet().size)
        for (t in Trade.entries) {
            assertNotNull(t.name, ContextCompat.getDrawable(ctx, t.iconRes))
            ContextCompat.getColor(ctx, t.chipRes)
            ContextCompat.getColor(ctx, t.inkRes)
        }
    }

    @Test
    fun `the agriculture label no longer calls pesticides poison`() {
        assertTrue(!localised("ur").getString(Trade.AGRI.labelRes).contains("زہریلی"))
        assertTrue(!localised("ur-Latn").getString(Trade.AGRI.labelRes).contains("zehreli"))
    }
}
