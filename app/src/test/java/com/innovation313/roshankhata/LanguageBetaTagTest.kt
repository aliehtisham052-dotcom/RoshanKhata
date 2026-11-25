package com.innovation313.roshankhata

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D17 on the phone's language page (the setup's first step and More → Language): the four languages no native speaker
 * has read yet wear the Beta tag, the other five do not, and the line under the list says what the tag means.
 * The tag follows `LanguageStatus` (shared), so a native read later is one line there.
 */
@RunWith(AndroidJUnit4::class)
class LanguageBetaTagTest {
    private val rows = mapOf(
        R.id.langEnglish to false, R.id.langRomanUrdu to false, R.id.langUrdu to false,
        R.id.langSindhi to true, R.id.langPersian to true, R.id.langArabic to true,
        R.id.langHindi to false, R.id.langBengali to true, R.id.langIndonesian to false
    )

    @Test
    fun theFourUnreadLanguagesWearTheTagAndTheOthersDoNot() {
        ActivityScenario.launch(LanguageActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((id, beta) in rows) {
                    val tag = activity.findViewById<View>(id).findViewById<View>(R.id.tvLangBeta)
                    assertEquals(
                        "the Beta tag on ${activity.resources.getResourceEntryName(id)}",
                        if (beta) View.VISIBLE else View.GONE, tag.visibility
                    )
                }
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tvLangBetaHelp).visibility)
            }
        }
    }

    @Test
    fun theTagIsSaidToTalkBackWithTheLanguage() {
        ActivityScenario.launch(LanguageActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val word = activity.getString(R.string.lang_beta)
                val sindhi = activity.findViewById<View>(R.id.langSindhi).contentDescription.toString()
                val urdu = activity.findViewById<View>(R.id.langUrdu).contentDescription.toString()
                assertTrue("Sindhi's row does not say Beta: $sindhi", sindhi.contains(word))
                assertTrue("Urdu's row says Beta: $urdu", !urdu.split(", ").contains(word))
            }
        }
    }
}
