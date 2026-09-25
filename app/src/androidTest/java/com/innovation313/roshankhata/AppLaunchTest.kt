package com.innovation313.roshankhata

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app opens.
 *
 * This is the smallest possible instrumented test and the most valuable one
 * to have first: the worst failure this app can ship is an update that
 * crashes before the owner sees their ledger, which no unit test can catch
 * because no unit test ever starts an Activity, inflates a layout, reads a
 * theme, or applies the language. Those live on a device, and until now
 * nothing checked them except a person holding a phone.
 *
 * [GateActivity] is the real launcher — the one the home-screen icon starts —
 * so this walks the same path a user does: splash, then the routing decision.
 * It asserts only that the screen reaches RESUMED. That is deliberate: a test
 * that also drove taps through the language picker into Home would fail
 * whenever a button moved, and a test that cries wolf is worse than no test.
 * What this one reports is unambiguous — the app started, or it did not.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @Test
    fun theLauncherScreenOpensWithoutCrashing() {
        ActivityScenario.launch(GateActivity::class.java).use { scenario ->
            assertEquals(
                "GateActivity did not reach RESUMED — the app failed to start",
                Lifecycle.State.RESUMED,
                scenario.state
            )
        }
    }
}
