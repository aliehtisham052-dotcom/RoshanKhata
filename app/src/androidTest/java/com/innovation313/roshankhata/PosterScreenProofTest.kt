package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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

    /**
     * A design is one picture, in its place, whichever way the app reads (BC-237, BC-238; the owner's Urdu phone,
     * 19 Nov 2026). Two faults stood on the screen together:
     *
     *  - the live 3D hero was hung from the frame's start, the right edge in Urdu, so the clock, the mosque and the
     *    crate stood beside their place;
     *  - the design's page and its small picture were drawn with the flat stand-in while the live layer showed the
     *    3D hero: two pictures of one design on one screen, and the flat one first whenever a design was chosen.
     *
     * For each design, in English and in Urdu: the hero's view lies on the hero's own rectangle; the page and the
     * small picture are NOT the flat drawing there (drawn again here with the flat hero, to compare) and are each
     * other's picture. Where the device draws the heroes flat there is one picture already and only the screen is
     * photographed. Pictures go to Download/rtl-proof/ to be looked at.
     */
    @Test
    fun a_design_is_one_picture_in_its_place_in_urdu_too() {
        com.innovation313.roshankhata.data.BusinessProfile.setBusinessName(app, "Ali Traders")
        val inst = InstrumentationRegistry.getInstrumentation()
        try {
            for (tag in listOf("en", "ur")) {
                inst.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                SystemClock.sleep(800)
                for (key in listOf("new_stock", "hours", "eid_adha", "offer")) {
                    app.getSharedPreferences("poster", Context.MODE_PRIVATE).edit().putString("occasion", key).commit()
                    ActivityScenario.launch(PosterActivity::class.java).use { scenario ->
                        var proof: PosterActivity.Proof? = null
                        var last = "the screen never answered"
                        var threeD = false
                        var rtl = false
                        var frameW = 0
                        var left = 0; var top = 0; var width = 0; var height = 0
                        // The first 3D frame on CI's software GPU takes seconds (its shaders compile).
                        val until = SystemClock.uptimeMillis() + 120_000
                        while (proof == null && SystemClock.uptimeMillis() < until) {
                            SystemClock.sleep(1000)
                            scenario.onActivity { a ->
                                val p = a.proof()
                                last = p.state
                                if (p.liveUp && p.page != null && p.thumb != null) {
                                    val frame = a.findViewById<View>(R.id.posterFrame)
                                    val hero = a.findViewById<View>(R.id.heroView)
                                    threeD = hero.visibility == View.VISIBLE
                                    rtl = frame.layoutDirection == View.LAYOUT_DIRECTION_RTL
                                    frameW = frame.width
                                    left = hero.left; top = hero.top; width = hero.width; height = hero.height
                                    proof = p
                                }
                            }
                        }
                        SystemClock.sleep(1500)
                        save("screen_one_picture_${key}_$tag.png")
                        if (proof == null) android.util.Log.i("Poster3D", "one picture: NEVER $key/$tag $last")
                        val p = proof
                            ?: throw AssertionError("$key/$tag: the live poster, its page and its small picture never all came: $last")
                        android.util.Log.i("Poster3D", "one picture: $key/$tag came: $last")
                        if (tag == "ur") assertTrue("$key/$tag: the screen does not read right to left, so this proves nothing", rtl)
                        if (!threeD) return@use
                        val r = p.hero ?: throw AssertionError("$key/$tag: no place for the hero")
                        val page = p.page ?: throw AssertionError("$key/$tag: no page")
                        val thumb = p.thumb ?: throw AssertionError("$key/$tag: no small picture")
                        val flat = p.flat ?: throw AssertionError("$key/$tag: no flat picture to compare with")
                        assertTrue("$key/$tag: the poster's frame has no width", frameW > 0)
                        val s = frameW / 1080f
                        val where = "$key/$tag: the hero's view is at $left,$top ${width}x$height, its place is " +
                            "${r.left * s},${r.top * s} ${r.width() * s}x${r.height() * s}"
                        assertTrue(where, kotlin.math.abs(left - r.left * s) <= 2f && kotlin.math.abs(top - r.top * s) <= 2f)
                        assertTrue(where, kotlin.math.abs(width - r.width() * s) <= 2f && kotlin.math.abs(height - r.height() * s) <= 2f)
                        val pageVsFlat = apart(page, flat, r)
                        val thumbVsFlat = apart(thumb, flat, r)
                        val thumbVsPage = apart(thumb, page, r)
                        val said = "$key/$tag: page-flat $pageVsFlat, small-flat $thumbVsFlat, small-page $thumbVsPage"
                        android.util.Log.i("Poster3D", "one picture: $said")
                        assertTrue("the page is drawn with the flat hero while the live poster shows the 3D one. $said", pageVsFlat >= 3f)
                        assertTrue("the small picture is not the page's picture. $said", thumbVsPage < thumbVsFlat)
                    }
                }
            }
        } finally {
            inst.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        }
    }

    /**
     * How far apart two pictures of the poster are inside [r] (the poster's own 1080 x 1920 numbers): the mean
     * difference of a colour channel, 0 to 255, over a 48 x 48 grid. Each picture is read at its own size.
     */
    private fun apart(a: Bitmap, b: Bitmap, r: android.graphics.RectF): Float {
        fun at(bmp: Bitmap, fx: Float, fy: Float): Int =
            bmp.getPixel((fx * bmp.width).toInt().coerceIn(0, bmp.width - 1), (fy * bmp.height).toInt().coerceIn(0, bmp.height - 1))
        var sum = 0L
        val n = 48
        for (i in 0 until n) for (j in 0 until n) {
            val fx = (r.left + (i + 0.5f) / n * r.width()) / 1080f
            val fy = (r.top + (j + 0.5f) / n * r.height()) / 1920f
            val p = at(a, fx, fy)
            val q = at(b, fx, fy)
            sum += kotlin.math.abs((p shr 16 and 0xff) - (q shr 16 and 0xff)) +
                kotlin.math.abs((p shr 8 and 0xff) - (q shr 8 and 0xff)) +
                kotlin.math.abs((p and 0xff) - (q and 0xff))
        }
        return sum / (n * n * 3f)
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
