package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The poster screen itself (12 Oct), photographed for a person to look at:
 * the top (designs, language, the live preview) and, for Closed, Timings and
 * Ramazan, that design's own fields. Pictures go to Download/rtl-proof/.
 */
class PosterScreenProofTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    /**
     * The three steps (12 Oct): Design with its big picture and language
     * switch; Words with the pinned preview and the design's own fields; Send.
     * Each design's Words step is photographed; Send once. The shop has a name
     * (dummy), as the Send step asks for one.
     */
    @Test
    fun the_screen_opens_with_each_designs_fields() {
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessName(app, "Ali Traders")
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessPhone(app, "0300 0000000")
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessAddress(app, "Main Bazaar, Pasrur")
        // No email yet: the Send step offers "+ Email".
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessEmail(app, null)
        for (key in listOf("new_stock", "closed", "hours", "ramazan", "offer", "azadi")) {
            app.getSharedPreferences("poster", Context.MODE_PRIVATE).edit().putString("occasion", key).commit()
            ActivityScenario.launch(PosterActivity::class.java).use { scenario ->
                SystemClock.sleep(2500)
                if (key == "new_stock") save("screen_top.png")
                scenario.onActivity { a -> a.findViewById<android.view.View>(R.id.btnStepNext).performClick() }
                SystemClock.sleep(1500)
                save("screen_fields_$key.png")
                if (key == "new_stock") {
                    scenario.onActivity { a ->
                        val scroll = a.findViewById<ScrollView>(R.id.posterScroll)
                        scroll.scrollTo(0, scroll.getChildAt(0).height)
                    }
                    SystemClock.sleep(800)
                    save("screen_fields_new_stock_end.png")
                    scenario.onActivity { a -> a.findViewById<android.view.View>(R.id.btnStepNext).performClick() }
                    SystemClock.sleep(1500)
                    save("screen_send.png")
                }
            }
        }
        assertTrue(true)
    }

    /**
     * The Design step on a small phone (360 x 720 dp, 12 Oct): the poster, the
     * strip of designs and the language switch should all be in view, and the
     * arrows inside the screen. The phone's own size is put back afterwards.
     */
    @Test
    fun the_design_step_fits_a_small_phone() {
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessName(app, "Ali Traders")
        app.getSharedPreferences("poster", Context.MODE_PRIVATE).edit().putString("occasion", "new_stock").commit()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(cmd: String) {
            // Read to the end, so the command has finished before the test goes on.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).use { it.readBytes() }
        }
        try {
            shell("wm size 720x1440")
            shell("wm density 320")
            SystemClock.sleep(1500)
            ActivityScenario.launch(PosterActivity::class.java).use { scenario ->
                SystemClock.sleep(2500)
                save("screen_small_design.png")
                var inView = false
                scenario.onActivity { a ->
                    val lang = a.findViewById<android.view.View>(R.id.toggleLang)
                    val bar = a.findViewById<android.view.View>(R.id.btnStepNext)
                    val l = IntArray(2); lang.getLocationOnScreen(l)
                    val b = IntArray(2); bar.getLocationOnScreen(b)
                    val prev = a.findViewById<android.view.View>(R.id.btnPrevDesign)
                    val p = IntArray(2); prev.getLocationOnScreen(p)
                    inView = l[1] + lang.height <= b[1] && p[0] >= 0
                }
                assertTrue("the language switch is under the bar, or an arrow is off the screen, on a 360 x 720 phone", inView)
                scenario.onActivity { a -> a.findViewById<android.view.View>(R.id.btnStepNext).performClick() }
                SystemClock.sleep(1200)
                scenario.onActivity { a -> a.findViewById<android.view.View>(R.id.btnStepNext).performClick() }
                SystemClock.sleep(1500)
                save("screen_small_send.png")
            }
        } finally {
            shell("wm size reset")
            shell("wm density reset")
            SystemClock.sleep(1500)
        }
    }

    /**
     * Changing design never shows one design through another (12 Oct): the
     * live poster is off the stage from the moment the change starts, and back
     * once the new design is drawn, on the page now in view.
     */
    @Test
    fun a_new_design_never_shows_through_the_last() {
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessName(app, "Ali Traders")
        app.getSharedPreferences("poster", Context.MODE_PRIVATE).edit().putString("occasion", "new_stock").commit()
        ActivityScenario.launch(PosterActivity::class.java).use { scenario ->
            SystemClock.sleep(3000)
            for (step in 1..3) {
                var hiddenAtOnce = false
                scenario.onActivity { a ->
                    a.findViewById<android.view.View>(R.id.btnNextDesign).performClick()
                    hiddenAtOnce = a.findViewById<android.view.View>(R.id.posterFrame).visibility != android.view.View.VISIBLE
                }
                assertTrue("design change $step: the last design's live poster was still on the stage", hiddenAtOnce)
                SystemClock.sleep(3000)
                var back = false
                var onPage = false
                scenario.onActivity { a ->
                    back = a.findViewById<android.view.View>(R.id.posterFrame).visibility == android.view.View.VISIBLE
                    val key = a.getSharedPreferences("poster", Context.MODE_PRIVATE).getString("occasion", null)
                    val i = com.innovation313.roshankhata.data.PosterOccasion.values().indexOfFirst { it.key == key }
                    onPage = a.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.designPager).currentItem == i
                }
                assertTrue("design change $step: the new live poster never came back", back)
                assertTrue("design change $step: the pages did not come to rest on the chosen design", onPage)
            }
            // A slide by hand: the live poster leaves as the drag starts.
            var hiddenOnDrag = false
            scenario.onActivity { a ->
                val pager = a.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.designPager)
                pager.beginFakeDrag()
                pager.fakeDragBy(-pager.width * 0.3f)
                hiddenOnDrag = a.findViewById<android.view.View>(R.id.posterFrame).visibility != android.view.View.VISIBLE
                pager.fakeDragBy(-pager.width * 0.5f)
                pager.endFakeDrag()
            }
            assertTrue("a slide by hand left the live poster on the stage", hiddenOnDrag)
            SystemClock.sleep(3000)
            save("screen_after_slide.png")
        }
    }

    private fun save(name: String) {
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/rtl-proof")
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        app.contentResolver.openOutputStream(uri)?.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shot.recycle()
    }
}
