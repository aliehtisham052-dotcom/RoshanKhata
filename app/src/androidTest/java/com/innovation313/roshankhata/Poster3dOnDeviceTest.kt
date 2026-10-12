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
import com.innovation313.roshankhata.ui.PosterDesigns
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.poster3d.CoinFace
import com.innovation313.roshankhata.ui.poster3d.Hero
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import com.innovation313.roshankhata.ui.poster3d.SealFace
import com.innovation313.roshankhata.ui.poster3d.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

/**
 * The posters' 3D heroes, rendered by Filament on a real Android (11 Oct).
 *
 * Every hero is drawn still, off-screen, at its place's size and checked by
 * numbers: the frame is the size asked for, mostly clear (the poster shows
 * through around it), yet carries a solid object with gold in it. Its poster
 * is then drawn in English and Urdu with the hero in place, and every picture
 * is saved to Download/rtl-proof/ for a person to look at.
 *
 * A phone that cannot run Filament (no OpenGL ES 3.0) skips this; the
 * posters still draw, with flat heroes. So does the emulator's legacy
 * SwiftShader translator (see Studio.isLegacySwiftShader); CI uses ANGLE.
 */
class Poster3dOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    @Test
    fun every_hero_renders_in_3d_and_lands_on_its_poster() {
        var ok = false
        Log.i("Poster3D", "starting the engine")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { ok = Studio.engineReady(app) }
        Log.i("Poster3D", "engine available: $ok")
        if (!ok) Log.w("Poster3D", "This device cannot run Filament; the flat heroes are used")
        assumeTrue("Filament not available on this device", ok)
        // CI's emulator runs on ANGLE (swangle_indirect), which takes the full look.
        // The legacy SwiftShader translator draws every material white, and the
        // app shows no 3D there, so there is nothing to check.
        Log.i("Poster3D", "GL driver: ${Studio.glDriver}")
        val lookName = InstrumentationRegistry.getArguments().getString("look")
        assumeTrue("legacy SwiftShader: the app draws flat heroes here", lookName != null || !Studio.isLegacySwiftShader)
        val look = HeroStage.Look.named(lookName)
        Log.i("Poster3D", "look: ${lookName ?: "full"} = $look")

        for (hero in Hero.values()) {
            val r = hero.rect
            val w = r.width().toInt()
            val h = r.height().toInt()
            var stage: HeroStage? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val face = when {
                    hero.face == null -> null
                    hero == Hero.OFFER -> CoinFace.draw(app, 20)
                    else -> SealFace.draw(app, "Ali Traders", null)
                }
                stage = HeroStage(app, hero, look, face)
                face?.recycle()
            }
            Log.i("Poster3D", "${hero.name}: stage built, rendering the still ${w}x$h")
            val still = runBlocking { withTimeout(30_000L) { withContext(Dispatchers.Main) { stage!!.still(w, h) } } }
            InstrumentationRegistry.getInstrumentation().runOnMainSync { stage!!.destroy() }
            describe(hero.name, still)
            save("hero_${hero.name.lowercase(Locale.ROOT)}.png", still)

            if (lookName == null || lookName == "full") {
                assertEquals(w, still.width)
                assertEquals(h, still.height)
                val px = IntArray(w * h)
                still.getPixels(px, 0, w, 0, 0, w, h)
                var solid = 0
                var gold = 0
                for (p in px) {
                    if (Color.alpha(p) > 200) {
                        solid++
                        // Gold by its hue (red over green over blue, well clear of grey) and
                        // some brightness: lit gold leaf on the seal sits near (155, 125, 65).
                        val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                        if (r > 120 && g > 90 && r > g && g > b && b < r - 50) gold++
                    }
                }
                val clear = px.count { Color.alpha(it) < 10 }
                Log.i("Poster3D", "${hero.name}: $solid solid, $gold gold, $clear clear of ${px.size}")
                assertTrue("${hero.name}: drew too little ($solid solid pixels)", solid > px.size / 60)
                assertTrue("${hero.name}: no gold ($gold)", gold > 200)
                assertTrue("${hero.name}: background not clear ($clear clear pixels)", clear > px.size / 4)
            }

            val occasion = PosterOccasion.values().first { PosterDesigns.hero(it) == hero }
            for (tag in listOf("en", "ur")) {
                val ctx = inLanguage(tag)
                val (bmp, fit) = PosterImage.draw(ctx, PosterImage.Input(
                    occasion = occasion, title = ctx.getString(occasion.title),
                    message = ctx.getString(occasion.message),
                    shopName = if (tag == "ur") "علی ٹریڈرز" else "Ali Traders",
                    phone = "0300 0000000", address = if (tag == "ur") "مین بازار" else "Main Bazaar", logo = null,
                    mark = ctx.getString(R.string.made_with_app), hero = still
                ))
                assertEquals(PosterImage.HEIGHT, bmp.height)
                assertTrue("$tag/${occasion.key}: headline at the floor (${fit.titleSize})", fit.titleSize > 56f)
                save("poster3d_${occasion.key}_$tag.png", bmp)
                bmp.recycle()
            }
            still.recycle()
        }
    }

    /**
     * What a frame holds, in the log: a 32×16 grid of brightness (0-9, '.'
     * for clear) and the average colour of the solid pixels. CI pictures
     * cannot always be downloaded; the log always comes back.
     */
    private fun describe(name: String, bmp: Bitmap) {
        val cols = 32
        val rows = 16
        val sb = StringBuilder()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val p = bmp.getPixel(x * bmp.width / cols + bmp.width / cols / 2, y * bmp.height / rows + bmp.height / rows / 2)
                if (Color.alpha(p) < 10) sb.append('.')
                else sb.append(((Color.red(p) + Color.green(p) + Color.blue(p)) * 10 / 766).toString())
            }
            Log.i("Poster3D", "$name grid ${"%02d".format(y)} $sb")
            sb.setLength(0)
        }
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
