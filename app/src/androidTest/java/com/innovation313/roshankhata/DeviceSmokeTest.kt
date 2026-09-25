package com.innovation313.roshankhata

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What only a REAL Android can show.
 *
 * The data side — the balance SUM, the backup round trip, the launcher not
 * crashing — already runs on every push under Robolectric (src/test). That is
 * a simulation of Android inside the JVM, and there are things it cannot see:
 * the real window manager tearing an Activity down on rotation, the real
 * lifecycle when the owner switches to WhatsApp and comes back, a real touch
 * event through the real view system. Those run here, on an emulator in CI or
 * a phone on USB (`gradle connectedDebugAndroidTest`).
 *
 * The language screen carries most of this because it is the first thing
 * every new owner touches and the one screen that never moves on by itself —
 * it waits for a tap — so a test can rotate it or background it without
 * racing the app.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Every test starts as a brand-new install: no language chosen yet. The
     * name is LanguageActivity's own prefs file ("language", key "chosen") —
     * private there, so written out here; if it is ever renamed, the tap test
     * below fails loudly rather than silently testing the wrong path.
     */
    @Before
    fun freshInstall() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** The tap test picks English; do not leave the emulator in it for the next run. */
    @After
    fun resetLanguage() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theRealLauncherOpensWithoutCrashing() {
        // GateActivity shows the splash and then, on its own timer, routes on
        // and finishes. On a slow CI emulator that timer can fire before this
        // line reads the state, so DESTROYED-after-routing is a pass too. A
        // crash never gets here: it throws out of launch() and fails the test.
        ActivityScenario.launch(GateActivity::class.java).use { scenario ->
            assertTrue(
                "GateActivity is in ${scenario.state}",
                scenario.state == Lifecycle.State.RESUMED ||
                    scenario.state == Lifecycle.State.DESTROYED
            )
        }
    }

    @Test
    fun theLanguageScreenSurvivesRotation() {
        // recreate() is what the real system does on rotation, a font-size
        // change, or dark mode switching: destroy the Activity and build it
        // again from scratch. A view looked up before it exists, or state that
        // assumes onCreate runs once, crashes here and nowhere else.
        ActivityScenario.launch(LanguageActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theLanguageScreenSurvivesGoingToTheBackgroundAndBack() {
        // The owner opens the app, a customer calls, they come back. CREATED
        // is "stopped and off screen"; back to RESUMED is "on screen again".
        ActivityScenario.launch(LanguageActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun choosingALanguageOnFirstRunIsRememberedAndMovesOn() {
        // The first tap every new owner makes. performClick goes through the
        // real view system and the real click listener, then the real
        // per-app-locale call and the hand-off to the welcome screen.
        ActivityScenario.launch(LanguageActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.langEnglish).performClick()
            }
        }
        assertTrue(
            "the language choice was not remembered, so the picker would show again",
            LanguageActivity.isChosen(context)
        )
    }
}
