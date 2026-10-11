package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.PosterNewStock
import com.innovation313.roshankhata.ui.poster3d.Hero
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import com.innovation313.roshankhata.ui.poster3d.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

/**
 * The posters' 3D hero, rendered by Filament on a real Android (11 Oct).
 *
 * Checked by numbers: the still frame is the size asked for, mostly clear
 * (the poster shows through around the crate), yet carries a solid object
 * with gold in it. The full New stock poster is then drawn in English and
 * Urdu with the hero in place, and both pictures are saved to
 * Download/rtl-proof/ for a person to look at in the CI report.
 *
 * A phone that cannot run Filament (no OpenGL ES 3.0) skips the 3D part;
 * the poster still draws, with the flat crate.
 */
class Poster3dOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    @Test
    fun the_crate_renders_in_3d_and_lands_on_the_poster() {
        var ok = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { ok = Studio.available(app) }
        if (!ok) Log.w("Poster3D", "This device cannot run Filament; the flat crate is used")
        assumeTrue("Filament not available on this device", ok)

        var stage: HeroStage? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { stage = HeroStage(app, Hero.CRATE) }
        val r = PosterNewStock.HERO
        val w = r.width().toInt()
        val h = r.height().toInt()
        val hero = runBlocking { withContext(Dispatchers.Main) { stage!!.still(w, h) } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { stage!!.destroy() }

        assertEquals(w, hero.width)
        assertEquals(h, hero.height)
        var solid = 0
        var gold = 0
        val px = IntArray(w * h)
        hero.getPixels(px, 0, w, 0, 0, w, h)
        for (p in px) {
            if (Color.alpha(p) > 200) {
                solid++
                if (Color.red(p) > 150 && Color.green(p) > 110 && Color.blue(p) < Color.red(p) - 50) gold++
            }
        }
        val clear = px.count { Color.alpha(it) < 10 }
        assertTrue("the crate drew nothing ($solid solid pixels)", solid > px.size / 20)
        assertTrue("no gold on the crate ($gold)", gold > 200)
        assertTrue("background not clear ($clear clear pixels)", clear > px.size / 4)
        save("hero_crate.png", hero)

        for (tag in listOf("en", "ur")) {
            val ctx = inLanguage(tag)
            val (bmp, fit) = PosterImage.draw(ctx, PosterImage.Input(
                occasion = PosterOccasion.NEW_STOCK, title = ctx.getString(R.string.poster_new_stock),
                message = ctx.getString(R.string.poster_new_stock_msg), shopName = "Ali Traders",
                phone = "0300 0000000", address = "Main Bazaar", logo = null,
                mark = ctx.getString(R.string.made_with_app), hero = hero
            ))
            assertEquals(PosterImage.HEIGHT, bmp.height)
            assertTrue("$tag: headline at the floor (${fit.titleSize})", fit.titleSize > 72f)
            save("poster_new_stock_$tag.png", bmp)
            bmp.recycle()
        }
        hero.recycle()
    }

    private fun save(name: String, bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/rtl-proof")
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        app.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
