package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.ui.CardTemplates
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The business card in Urdu (12 Oct): every design drawn right to left, in a
 * mirror, with its words and its QR turned back the right way round.
 *
 * Proven here, on a real canvas with the real fonts:
 *  - every design draws an Urdu card;
 *  - the QR on the Urdu card is the same code, the right way round, as on the
 *    left-to-right card (a mirrored code is not read by every phone);
 *  - drawing an Urdu card leaves nothing behind: the next English card is
 *    exactly the card it was before.
 * The pictures go to Download/rtl-proof/ for a person to look at. Dummy data only.
 */
@RunWith(AndroidJUnit4::class)
class CardLanguageOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private val urdu = CardTemplates.CardData(
        name = "علی ٹریڈرز", type = "زرعی ادویات · بیج · کھاد", owner = "علی احمد",
        phone = "0300 0000000", address = "مین بازار، پسرور", footer = "روشن کھاتہ",
        whatsapp = "0300 0000000", email = "ali.traders@example.com", tagline = "گھر تک ڈیلیوری",
        qrMessage = "السلام علیکم", rtl = true
    )

    private val english = CardTemplates.CardData(
        name = "Ali Traders", type = "Pesticides · Seeds · Fertiliser", owner = "Ali Ahmad",
        phone = "0300 0000000", address = "Main Bazaar, Pasrur", footer = "Roshan Khata",
        whatsapp = "0300 0000000", email = "ali.traders@example.com", tagline = "Home delivery",
        qrMessage = "Assalam o Alaikum"
    )

    @Before
    fun fonts() = CardTemplates.init(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun draw(id: Int, d: CardTemplates.CardData): Bitmap {
        val bmp = Bitmap.createBitmap(CardTemplates.W, CardTemplates.H, Bitmap.Config.ARGB_8888)
        CardTemplates.byId(id).draw(Canvas(bmp), d, CardTemplates.W, CardTemplates.H, -1f)
        return bmp
    }

    @Test
    fun every_design_draws_in_urdu_with_its_qr_the_right_way_round() {
        for (tpl in CardTemplates.all) {
            val rtl = draw(tpl.id, urdu)
            val ltr = draw(tpl.id, urdu.copy(rtl = false))
            val px = IntArray(rtl.width * rtl.height)
            rtl.getPixels(px, 0, rtl.width, 0, 0, rtl.width, rtl.height)
            assertTrue("${tpl.id}: the Urdu card is blank", px.distinct().size > 4)

            val qr = tpl.layout(urdu.copy(rtl = false), CardTemplates.W, CardTemplates.H).qr
            if (qr != null) {
                // Left to right the tile sits at qr; on the Urdu card, at its mirror image.
                val mirrored = RectF(CardTemplates.W - qr.right, qr.top, CardTemplates.W - qr.left, qr.bottom)
                val inset = 8
                val w = qr.width().toInt() - 2 * inset
                val h = qr.height().toInt() - 2 * inset
                // The code moves by this much, whole pixels; what is left is under half a
                // pixel, so a module edge can soften but never turn black to white.
                val shift = Math.round(mirrored.left - qr.left)
                var differ = 0
                for (y in 0 until h) for (x in 0 until w) {
                    val lx = qr.left.toInt() + inset + x
                    val ly = qr.top.toInt() + inset + y
                    val a = ltr.getPixel(lx, ly)
                    val b = rtl.getPixel(lx + shift, ly)
                    if (abs(Color.red(a) - Color.red(b)) > 200) differ++
                }
                assertTrue("${tpl.id}: the QR on the Urdu card is not the same code the right way round ($differ of ${w * h} differ)",
                    differ < w * h / 50)
            }
            save("card_${tpl.id}_ur.png", rtl)
            ltr.recycle()
            rtl.recycle()
        }
    }

    @Test
    fun an_urdu_card_leaves_nothing_behind() {
        for (tpl in CardTemplates.all) {
            val before = draw(tpl.id, english)
            draw(tpl.id, urdu).recycle()
            val after = draw(tpl.id, english)
            assertTrue("${tpl.id}: the English card changed after an Urdu one", before.sameAs(after))
            if (tpl.id == CardTemplates.all.first().id) save("card_${tpl.id}_en.png", after)
            before.recycle()
            after.recycle()
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
