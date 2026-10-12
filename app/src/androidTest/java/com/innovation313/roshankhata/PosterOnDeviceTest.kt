package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.PosterFields
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
 * Every design in English and Urdu, and each language's Eid poster, is saved
 * to Download/rtl-proof/ for a person to look at. These are the flat heroes
 * (no 3D picture is passed); Poster3dOnDeviceTest draws them with 3D.
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
                    shopName = "Ali Traders Pasrur", phone = "0300 0000000", address = "Main Bazaar, Pasrur",
                    logo = null, mark = ctx.getString(R.string.made_with_app)
                )
                val (bmp, fit) = PosterImage.draw(ctx, input)
                assertEquals("$tag/${o.key} width", PosterImage.WIDTH, bmp.width)
                assertEquals("$tag/${o.key} height", PosterImage.HEIGHT, bmp.height)
                assertTrue("$tag/${o.key}: big line squeezed to the floor (${fit.titleSize})", fit.titleSize > 56f)
                assertTrue("$tag/${o.key}: message squeezed to the floor (${fit.messageSize})", fit.messageSize > 30f)
                if (o == PosterOccasion.EID_FITR || tag == "en" || tag == "ur") save("poster_${o.key}_$tag.png", bmp)
                bmp.recycle()
            }
        }
    }

    @Test
    fun the_owners_own_words_draw_in_both_languages() {
        for ((tag, title, message) in listOf(
            Triple("en", "Wheat seed booking is open", "Book at the shop or call us. First come, first served."),
            Triple("ur", "گندم کے بیج کی بکنگ شروع ہو گئی ہے", "اپنی بکنگ کے لیے دکان پر تشریف لائیں یا کال کریں۔ پہلے آئیں، پہلے پائیں۔")
        )) {
            val ctx = inLanguage(tag)
            val (bmp, fit) = PosterImage.draw(ctx, PosterImage.Input(
                PosterOccasion.CUSTOM, title, message, "Ali Traders", "0300 0000000", "Main Bazaar", null,
                ctx.getString(R.string.made_with_app)
            ))
            assertTrue("$tag: big line squeezed (${fit.titleSize})", fit.titleSize > 56f)
            save("poster_custom_$tag.png", bmp)
            bmp.recycle()
        }
    }

    /**
     * The fields chosen on the poster screen (12 Oct), drawn in English and
     * Urdu: four things arrived, closed for a wedding until tomorrow 9, the
     * timings with the Friday break and Sunday off, the Ramazan hours, three
     * days of Eid holidays. Each picture is saved for a person to look at.
     */
    @Test
    fun the_fields_draw_in_both_languages() {
        val cases = listOf(
            PosterOccasion.NEW_STOCK to PosterFields(items = listOf("Urea", "DAP", "Wheat seed", "Sprays")),
            PosterOccasion.CLOSED to PosterFields(reason = PosterFields.Reason.WEDDING.key, reopen = PosterFields.Reopen.TOMORROW.key),
            PosterOccasion.HOURS to PosterFields(),
            PosterOccasion.RAMAZAN to PosterFields(ramazanHours = true),
            PosterOccasion.EID_FITR to PosterFields(eidDays = 3)
        )
        for (tag in listOf("en", "ur")) {
            val ctx = inLanguage(tag)
            for ((o, f) in cases) {
                val fields = if (o == PosterOccasion.NEW_STOCK && tag == "ur")
                    f.copy(items = listOf("یوریا", "ڈی اے پی", "گندم کا بیج", "سپرے")) else f
                val (bmp, fit) = PosterImage.draw(ctx, PosterImage.Input(
                    occasion = o, title = ctx.getString(o.title), message = ctx.getString(o.message),
                    shopName = if (tag == "ur") "علی ٹریڈرز" else "Ali Traders", phone = "0300 0000000",
                    address = if (tag == "ur") "مین بازار، پسرور" else "Main Bazaar, Pasrur", logo = null,
                    mark = ctx.getString(R.string.made_with_app), fields = fields,
                    // The contact line at its fullest (12 Oct): address and email, on WhatsApp.
                    email = "ali.traders@example.com", whatsapp = true
                ))
                assertTrue("$tag/${o.key}: big line squeezed (${fit.titleSize})", fit.titleSize > 56f)
                assertTrue("$tag/${o.key}: words squeezed (${fit.messageSize})", fit.messageSize > 24f)
                save("poster_fields_${o.key}_$tag.png", bmp)
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
