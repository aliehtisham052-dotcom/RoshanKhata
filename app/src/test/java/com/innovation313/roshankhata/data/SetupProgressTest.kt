package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.SetupProgress.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a first run picks up (5 Nov 2026, the owner's report): a setup left at the shop type or
 * the shop's details opened Home and the walkthrough next time, its name and country never
 * asked, because "language chosen" was the only thing the front door looked at.
 */
@RunWith(AndroidJUnit4::class)
class SetupProgressTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun freshInstall() {
        for (name in listOf("language", "setup")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun chooseLanguage() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putBoolean("chosen", true).commit()
    }

    @Test
    fun theRuleGoesStepByStep() {
        assertEquals(Step.LANGUAGE, SetupProgress.next(languageChosen = false, started = false, done = false, hasTrade = false))
        assertEquals(Step.LANGUAGE, SetupProgress.next(languageChosen = false, started = true, done = false, hasTrade = true))
        assertEquals(Step.SHOP_TYPE, SetupProgress.next(languageChosen = true, started = true, done = false, hasTrade = false))
        assertEquals(Step.SHOP_DETAILS, SetupProgress.next(languageChosen = true, started = true, done = false, hasTrade = true))
        assertEquals(Step.DONE, SetupProgress.next(languageChosen = true, started = true, done = true, hasTrade = true))
    }

    @Test
    fun anInstallFromBeforeTheRuleIsNeverSentThroughTheStepsAgain() {
        assertEquals(Step.DONE, SetupProgress.next(languageChosen = true, started = false, done = false, hasTrade = false))
        chooseLanguage()
        assertEquals("an older phone was sent back into the setup", Step.DONE, SetupProgress.next(context))
        assertTrue(
            "the older phone's setup was not remembered as done",
            context.getSharedPreferences("setup", Context.MODE_PRIVATE).getBoolean("done", false)
        )
        // A later "started" (it cannot happen, but) never undoes it.
        SetupProgress.markStarted(context)
        assertEquals(Step.DONE, SetupProgress.next(context))
    }

    @Test
    fun aNewSetupLeftAfterItsLanguageOpensOnTheShopTypeThenTheDetails() {
        assertEquals(Step.LANGUAGE, SetupProgress.next(context))
        SetupProgress.markStarted(context)
        chooseLanguage()
        assertEquals("the app was closed on the shop type", Step.SHOP_TYPE, SetupProgress.next(context))
        BusinessProfile.setTrade(context, Trade.KIRYANA)
        assertEquals("the app was closed on the shop's details", Step.SHOP_DETAILS, SetupProgress.next(context))
        SetupProgress.markDone(context)
        assertEquals(Step.DONE, SetupProgress.next(context))
        assertTrue(SetupProgress.isDone(context))
    }
}
