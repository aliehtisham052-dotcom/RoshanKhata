package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.Color
import com.innovation313.roshankhata.desktop.gfx.Layout
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.Path
import com.innovation313.roshankhata.desktop.gfx.PathParser
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.StaticLayout
import com.innovation313.roshankhata.desktop.gfx.TextDirectionHeuristics
import com.innovation313.roshankhata.desktop.gfx.TextPaint
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.desktop.poster.PosterDraw
import com.innovation313.roshankhata.desktop.poster.PosterFields
import com.innovation313.roshankhata.desktop.poster.PosterInput
import com.innovation313.roshankhata.desktop.poster.PosterOccasion
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The phone's eleven poster designs drawn on the computer, flat, in English
 * and Urdu, with a made-up shop. Pictures kept in build/poster-proof and put
 * on the desktop-test pre-release by desktop.yml, to be looked at (lesson 16).
 */
class PosterDesignsTest {

    private fun context(ur: Boolean): Context {
        val tmp = Files.createTempDirectory("rk-poster").toFile()
        val settings = FileShopSettings(File(tmp, "s.properties"))
        settings.setBusinessName("Al-Noor Agri Store")
        settings.setBusinessNameUrdu("النور ایگری اسٹور")
        val locale = Locale.forLanguageTag(if (ur) "ur" else "en")
        return Context(desktopWords(locale), settings, ProfileImages(tmp), ur, locale)
    }

    private fun input(ctx: Context, o: PosterOccasion, ur: Boolean) = PosterInput(
        occasion = o,
        title = if (o.title.isEmpty()) ctx.getString(W.poster_custom_title_hint) else ctx.getString(o.title),
        message = if (o.message.isEmpty()) ctx.getString(W.poster_custom_msg_hint) else ctx.getString(o.message),
        shopName = if (ur) "النور ایگری اسٹور" else "Al-Noor Agri Store",
        phone = "0300 0000000",
        address = if (ur) "مین بازار" else "Main Bazaar, Sample Town",
        logo = null,
        mark = ctx.getString(W.made_with_app),
        fields = PosterFields(eidDays = 3, ramazanHours = true, reason = PosterFields.Reason.EID.key),
        email = "shop@example.com",
        whatsapp = true
    )

    /** Pixels unlike their neighbour four to the right: a plain fill has almost none, a drawn poster thousands. */
    private fun detail(img: BufferedImage): Int {
        var n = 0
        for (y in 0 until img.height step 6) for (x in 0 until img.width - 4 step 6) {
            val a = img.getRGB(x, y)
            val b = img.getRGB(x + 4, y)
            val d = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
                kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + kotlin.math.abs((a and 0xFF) - (b and 0xFF))
            if (d > 40) n++
        }
        return n
    }

    @Test
    fun `all eleven designs draw, in English and Urdu`() {
        val dir = File("build/poster-proof").apply { mkdirs() }
        assertEquals(11, PosterOccasion.entries.size)
        val thin = ArrayList<String>()
        for (ur in listOf(false, true)) {
            val ctx = context(ur)
            for (o in PosterOccasion.entries) {
                val img = PosterDraw.image(ctx, input(ctx, o, ur))
                assertEquals(PosterDraw.WIDTH, img.width)
                assertEquals(PosterDraw.HEIGHT, img.height)
                // The picture first, so a design that fails is still there to look at.
                ImageIO.write(img, "png", File(dir, "poster-${if (ur) "ur" else "en"}-${o.key}.png"))
                val n = detail(img)
                if (n < 400) thin += "${o.key}${if (ur) "ur" else "en"}=$n"
            }
        }
        assertTrue("posters that look empty: $thin", thin.isEmpty())
    }

    @Test
    fun `path data becomes the shape it describes`() {
        // The poster's map pin, 24 units: its point is at the bottom middle.
        val pin = PathParser.createPathFromPathData(
            "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7z"
        )
        val b = RectF()
        pin.computeBounds(b, true)
        assertEquals(5f, b.left, 0.01f)
        assertEquals(19f, b.right, 0.01f)
        assertEquals(2f, b.top, 0.01f)
        assertEquals(22f, b.bottom, 0.01f)
    }

    @Test
    fun `a cut-out path keeps only what is outside the cut`() {
        val moon = Path().apply { addCircle(50f, 50f, 40f, Path.Direction.CW) }
        moon.op(Path().apply { addCircle(70f, 50f, 40f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        val c = Canvas(100, 100)
        c.drawPath(moon, Paint().apply { color = Color.BLACK })
        val img = c.picture(100, 100)
        assertEquals(Color.BLACK, img.getRGB(15, 50))
        assertEquals(Color.WHITE, img.getRGB(60, 50))
    }

    @Test
    fun `a layout wraps into its width and stacks its lines`() {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f }
        val text = "one two three four five six seven eight nine ten"
        val one = StaticLayout.Builder.obtain(text, 0, text.length, p, 10_000).build()
        val narrow = StaticLayout.Builder.obtain(text, 0, text.length, p, 200)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.LTR).build()
        assertEquals(1, one.lineCount)
        assertTrue(narrow.lineCount > 2)
        assertTrue(narrow.height > one.height * 2)
        for (i in 0 until narrow.lineCount) assertTrue(narrow.getLineWidth(i) <= 200.5f)
    }

    @Test
    fun `a picture drawn into is what is drawn from it`() {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), bmp.image.getRGB(5, 5))
    }

    @Test
    fun `a blurred shape spreads past its edge, a shadow sits under it`() {
        val c = Canvas(200, 200)
        c.drawRect(80f, 80f, 120f, 120f, Paint().apply {
            color = Color.BLACK
            maskFilter = com.innovation313.roshankhata.desktop.gfx.BlurMaskFilter(20f, com.innovation313.roshankhata.desktop.gfx.BlurMaskFilter.Blur.NORMAL)
        })
        val img = c.picture(200, 200)
        // Just outside the square: grey, not white (spread) and not black (soft).
        val edge = img.getRGB(125, 100) and 0xFF
        assertTrue("edge=$edge", edge in 40..250)
    }
}
