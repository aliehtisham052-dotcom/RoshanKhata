package com.innovation313.roshankhata

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The language filter (12 Oct, app size) keeps only the app's own nine
 * languages from the libraries' eighty-odd. This proves the nine are all still
 * in the built app, each reading in its own words, on a real Android: a filter
 * that dropped one (Roman Urdu's "b+ur+Latn" folder is the unusual one) would
 * quietly show that language in English.
 *
 * Indonesian is asked for as "id", the way the phone asks, and must still
 * find the strings kept in values-in.
 */
@RunWith(AndroidJUnit4::class)
class LocalesKeptOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    @Test
    fun every_language_of_the_app_is_still_in_it() {
        val expected = linkedMapOf(
            "en" to "Back",
            "ur" to "واپس",
            "ur-Latn" to "Wapas",
            "sd" to "واپس",
            "ar" to "رجوع",
            "fa" to "بازگشت",
            "hi" to "वापस",
            "bn" to "ফিরে যান",
            "id" to "Kembali"
        )
        for ((tag, word) in expected) {
            assertEquals("$tag: the language's own strings are missing from the app", word,
                inLanguage(tag).getString(R.string.poster_back))
        }
    }
}
