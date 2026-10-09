package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The Khata header's counts, in every language the app speaks.
 *
 * The owner's Bengali screenshot read "১২০৫ জন গ্রাহক" over amounts that read
 * "Rs 4,598", and his Sindhi one "۱۲۰۵ گراهڪ" — getQuantityString formats
 * with the language's own digits, Format.money with 0-9. [Digits.quantity]
 * and [Digits.string] keep the plural choice and the translated words but
 * print the figure in 0-9, so one screen uses one numbering system.
 */
@RunWith(AndroidJUnit4::class)
class CountDigitsTest {

    private val locales = listOf("en", "ur", "ur-Latn", "ar", "sd", "fa", "hi", "bn", "in")

    private fun localised(tag: String): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    private fun assertLatinDigits(text: String, where: String) {
        val bad = text.filter { it.isDigit() && it !in '0'..'9' }
        assertTrue("$where printed non-Latin digits: '$text'", bad.isEmpty())
    }

    @Test
    fun `every language counts customers and settled accounts in 0-9`() {
        for (tag in locales) {
            val res = localised(tag).resources
            val customers = Digits.quantity(res, R.plurals.customer_count, 1205, 1205)
            val settled = Digits.string(res, R.string.settled_count, 1203)
            val overdue = Digits.quantity(res, R.plurals.summary_with_overdue, 7, customers, 7)
            assertLatinDigits(customers, "$tag customer_count")
            assertLatinDigits(settled, "$tag settled_count")
            assertLatinDigits(overdue, "$tag summary_with_overdue")
            assertTrue("$tag customer_count lost its figure: '$customers'", "1205" in customers)
            assertTrue("$tag settled_count lost its figure: '$settled'", "1203" in settled)
            assertTrue("$tag overdue lost its figure: '$overdue'", "7" in overdue && "1205" in overdue)
        }
    }

    @Test
    fun `the translated words are still the language's own`() {
        assertEquals("1205 customers", Digits.quantity(localised("en").resources, R.plurals.customer_count, 1205, 1205))
        assertTrue(Digits.quantity(localised("bn").resources, R.plurals.customer_count, 1205, 1205).contains("গ্রাহক"))
        assertTrue(Digits.quantity(localised("sd").resources, R.plurals.customer_count, 1205, 1205).contains("گراهڪ"))
    }
}
