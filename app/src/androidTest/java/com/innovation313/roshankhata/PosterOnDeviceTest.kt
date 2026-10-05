package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.ui.PosterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The Status poster, drawn on a real Android for every occasion in all nine
 * languages, with the app's own wording for each.
 *
 * Checked by numbers: the picture is 1080 × 1920, and the big line and the
 * message fitted their space without dropping below the smallest size the
 * renderer allows — the sign that a translation is too long for the poster.
 * Each language's Eid poster is saved to Download/rtl-proof/ for a person to
 * look at in the CI report.
 */
class PosterOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    private val languages = listOf("en", "ur-Latn", "ur", "sd", "fa", "ar", "in", "hi", "bn")

    @Test
    fun every_occasion_fits_in_every_language() {
        for (tag in languages) {
            val ctx = inLanguage(tag)
            for (o in PosterOccasion.values().filter { it != PosterOccasion.CUSTOM }) {
                val input = PosterImage.Input(
                    occasion = o, title = ctx.getString(o.title), message = ctx.getString(o.message),
                    shopName = "Ali Traders Pasrur", phone = "0340 7026467", address = "Main Bazaar, Pasrur",
                    logo = null, mark = ctx.getString(R.string.made_with_app)
                )
                val (bmp, fit) = PosterImage.draw(ctx, input)
                assertEquals("$tag/${o.key} width", PosterImage.WIDTH, bmp.width)
                assertEquals("$tag/${o.key} height", PosterImage.HEIGHT, bmp.height)
                assertTrue("$tag/${o.key}: big line squeezed to the floor (${fit.titleSize})", fit.titleSize > 56f)
                assertTrue("$tag/${o.key}: message squeezed to the floor (${fit.messageSize})", fit.messageSize > 30f)
                if (o == PosterOccasion.EID_FITR) save("poster_eid_$tag.png", bmp)
                bmp.recycle()
            }
        }
    }

    @Test
    fun the_owners_own_long_words_still_draw() {
        val ctx = inLanguage("ur")
        val long = "بہت لمبا جملہ ".repeat(14)
        val (bmp, fit) = PosterImage.draw(ctx, PosterImage.Input(
            PosterOccasion.CUSTOM, long, long, "", null, null, null, null
        ))
        assertEquals(PosterImage.HEIGHT, bmp.height)
        assertTrue(fit.titleLines >= 1)
        bmp.recycle()
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
