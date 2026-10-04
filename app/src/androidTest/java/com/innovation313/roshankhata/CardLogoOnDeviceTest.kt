package com.innovation313.roshankhata

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.CardPdf
import com.innovation313.roshankhata.ui.CardLogo
import com.innovation313.roshankhata.ui.CardTemplates
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shop's logo on its business card (4 Oct 2026), measured with the real
 * fonts on a real Android canvas — the unit tests' canvas does not measure
 * text, so only here is "never on a word" actually proven.
 *
 * For every design: the logo's tile stays inside the print safe zone and never
 * touches a line of text or the QR, with ordinary details, with every field
 * filled to the brim, and in Urdu. With ordinary details every design must
 * find room — a design that cannot is named in the failure.
 */
@RunWith(AndroidJUnit4::class)
class CardLogoOnDeviceTest {

    private val logo: Bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.parseColor("#C0392B")) }

    private val ordinary = CardTemplates.CardData(
        name = "Al-Noor Zarai Store", type = "Pesticides · Seeds", owner = "Ehtisham Ali",
        phone = "0300 1234567", address = "Main Bazar, Pasrur", footer = "Roshan Khata",
        whatsapp = "0321 7654321", qrMessage = "Salam", logo = logo
    )

    private val brim = CardTemplates.CardData(
        name = "Al-Madina Pesticides and General Order Suppliers Pasrur",
        type = "Pesticides, Seeds, Fertiliser, Livestock Feed and Sprayers",
        owner = "Chaudhry Muhammad Ehtisham Ali Bajwa",
        phone = "0300 1234567",
        address = "Lappay wali tehsil Pasrur district Sialkot Punjab Pakistan",
        footer = "Roshan Khata",
        whatsapp = "0321 7654321",
        email = "al.madina.pesticides.pasrur@gmail.com",
        web = "facebook.com/almadinapesticidespasrur",
        tagline = "Ghar tak delivery, har roz subah 8 se raat 10 baje tak",
        logo = logo
    )

    private val urdu = CardTemplates.CardData(
        name = "النور زرعی سٹور", type = "زرعی ادویات · بیج", owner = "احتشام علی",
        phone = "0300 1234567", address = "مین بازار پسرور", footer = "",
        whatsapp = "0300 1234567", logo = logo
    )

    @Before
    fun fonts() = CardTemplates.init(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun touches(a: RectF, b: RectF) = RectF.intersects(a, b)

    @Test
    fun logoNeverCoversTextOrQrAndStaysInsideTheCut() {
        val problems = mutableListOf<String>()
        for (w in intArrayOf(CardTemplates.W, CardPdf.PRINT_W)) {
            for (tpl in CardTemplates.all) {
                for ((label, d) in listOf("ordinary" to ordinary, "brim" to brim, "urdu" to urdu)) {
                    val layout = tpl.layout(d, w, CardTemplates.H)
                    val slot = CardLogo.slot(layout, w, CardTemplates.H) ?: continue
                    val where = "design ${tpl.id} ($label, width $w)"
                    if (slot.left < CardLogo.EDGE || slot.top < CardLogo.EDGE ||
                        slot.right > w - CardLogo.EDGE || slot.bottom > CardTemplates.H - CardLogo.EDGE
                    ) problems += "$where: logo outside the safe zone $slot"
                    layout.qr?.let { if (touches(slot, it)) problems += "$where: logo on the QR" }
                    if (layout.text.any { touches(slot, it) }) problems += "$where: logo on text"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun everyDesignFindsRoomWithOrdinaryDetails() {
        val missing = CardTemplates.all.filter { tpl ->
            CardLogo.slot(tpl.layout(ordinary, CardTemplates.W, CardTemplates.H), CardTemplates.W, CardTemplates.H) == null
        }.map { it.id }
        assertTrue("no room for the logo on designs $missing", missing.isEmpty())
    }

    @Test
    fun everyDesignDrawsWithALogoStillAndMoving() {
        val bmp = Bitmap.createBitmap(CardPdf.PRINT_W, CardTemplates.H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (tpl in CardTemplates.all) for (d in listOf(ordinary, brim, urdu)) {
            for (phase in floatArrayOf(-1f, 0f, 0.5f)) {
                tpl.draw(canvas, d, CardTemplates.W, CardTemplates.H, phase)
            }
            tpl.draw(canvas, d, CardPdf.PRINT_W, CardTemplates.H, -1f)
        }
        bmp.recycle()
    }
}
