package com.innovation313.roshankhata

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The first shared Compose screen (About, KMP Phase 5a) speaks the app's
 * language: its words come from the shared resources, copied from the same
 * strings.xml, and must be exactly what the phone's own resources say in
 * that language — English, Urdu, Roman Urdu (a script variant), Sindhi.
 */
@RunWith(Parameterized::class)
class SharedAboutOnDeviceTest(private val language: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages() = listOf("en", "ur", "ur-Latn", "sd", "ar")
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun reset() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun aboutSaysWhatThePhoneSays() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putBoolean("chosen", true).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var what = ""
            var version = ""
            scenario.onActivity {
                what = it.getString(R.string.about_what)
                version = it.getString(R.string.about_version, BuildConfig.VERSION_NAME)
            }
            compose.onNodeWithTag("aboutWhat").assertTextEquals(what)
            compose.onNodeWithTag("aboutVersion").assertTextEquals(version)
        }
    }
}
