package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.ui.FeatureIntro
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The feature sheets' help pages are kept twice since Hudhud's answers moved to :shared (16 Nov 2026, audit T22): the phone's
 * first-open sheets (FeatureIntro.Topic, with their pictures) and the shared list Hudhud matches questions against and opens
 * pages from (AnswerBook.FEATURES), which the computer reads too. This holds the two to one list — the same keys, the same
 * title and body words, in the same order — so a sheet added on the phone fails here until Hudhud knows its page.
 */
@RunWith(AndroidJUnit4::class)
class HudhudFeaturePagesTest {

    @Test
    fun theSharedFeaturePagesAreThePhonesFirstOpenSheets() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val phone = FeatureIntro.Topic.values().map { listOf(it.key, res.getResourceEntryName(it.title), res.getResourceEntryName(it.body)) }
        val shared = AnswerBook.FEATURES.map { listOf(it.key, it.title, it.body) }
        assertEquals(phone, shared)
    }

    @Test
    fun everyPageHasAPhoneScreenAndKeepsItsName() {
        // The phone's map is a `when` with no else (it does not build without every page); this holds its names to the rule
        // the catalog check reads it by (BUSINESS_SETTINGS -> BusinessSettingsActivity).
        for (id in ScreenId.values()) {
            val camel = id.name.lowercase().split('_').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            assertEquals(id.name, camel + "Activity", HudhudPhoneEnv.activity(id).simpleName)
        }
    }
}
