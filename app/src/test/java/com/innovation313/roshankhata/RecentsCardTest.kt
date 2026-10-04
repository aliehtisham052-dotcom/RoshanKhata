package com.innovation313.roshankhata

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.ScreenSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.util.ReflectionHelpers

/**
 * The card the app leaves in the recent-apps switcher (ScreenSecurity.plainCard).
 *
 * The thumbnail is switched off, so the system draws a stand-in: the page
 * colour, with a strip across the top in whatever the task says its status
 * bar colour is. Left to the theme that strip is the header's dark green, and
 * the owner saw a green band on an otherwise white card. These check what
 * the system is actually told, on the real Activity code Robolectric runs.
 *
 * What a phone maker's own switcher then paints is not something a test on a
 * simulated Android can see; the owner's phone covers that.
 */
@RunWith(AndroidJUnit4::class)
class RecentsCardTest {

    /** What this screen has told the system about itself. */
    private fun described(activity: Activity): ActivityManager.TaskDescription =
        ReflectionHelpers.getField(activity, "mTaskDescription")

    /** The colour the stand-in card is filled with: the theme's background. */
    private fun page(activity: Activity): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return value.data
    }

    @Test
    fun theCardHasNoGreenStripAlongItsTop() {
        ActivityScenario.launch(GateActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val green = ContextCompat.getColor(activity, R.color.section_khata)
                val told = described(activity)
                assertNotEquals(
                    "the switcher card would still carry the header's green along its top",
                    green, told.statusBarColor
                )
                assertEquals(
                    "the card's top strip is not the colour of the card's own page",
                    page(activity), told.statusBarColor
                )
                assertEquals(
                    "the card's bottom strip is not the colour of the card's own page",
                    page(activity), told.navigationBarColor
                )
            }
        }
    }

    @Test
    fun aScreenThatSetsItsOwnBarColoursStillLeavesAPlainCardOnceResumed() {
        ActivityScenario.launch(GateActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // What the language and welcome screens do in onCreate; the
                // window passes it on, and the description loses its colour.
                activity.window.statusBarColor = Color.TRANSPARENT
                assertEquals(Color.TRANSPARENT, described(activity).statusBarColor)

                // What every resume does (RoshanKhataApp's lifecycle callbacks).
                ScreenSecurity.plainCard(activity)
                assertEquals(page(activity), described(activity).statusBarColor)
            }
        }
    }

    @Test
    fun thePrimaryColourTheThemeGaveIsKept() {
        ActivityScenario.launch(GateActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val value = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.colorPrimary, value, true)
                assertEquals(value.data, described(activity).primaryColor)
            }
        }
    }
}
