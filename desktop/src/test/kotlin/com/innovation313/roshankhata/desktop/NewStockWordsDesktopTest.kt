package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.desktop.poster.NewStockWords
import com.innovation313.roshankhata.desktop.poster.NewStockWords.Family
import com.innovation313.roshankhata.desktop.poster.PosterDraw
import com.innovation313.roshankhata.desktop.poster.PosterFields
import com.innovation313.roshankhata.desktop.poster.PosterInput
import com.innovation313.roshankhata.desktop.poster.PosterOccasion
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The New stock poster's words by the kind of shop (13 Nov 2026), on the computer, in all nine languages: a
 * restaurant has a menu, a salon a service, a baker's counter is fresh today; none of them has "stock". The phone's
 * own test (NewStockWordsTest) places every trade in its family; this one holds the words each family says.
 * Pictures of the five families in the nine languages go to build/poster-proof, to be looked at (lesson 16).
 */
class NewStockWordsDesktopTest {

    private val tags = listOf("en", "ur", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "id")

    /** One trade that stands for each family. */
    private val sample = linkedMapOf(
        Family.GOODS to "agri",
        Family.FRESH to "bakery",
        Family.COLLECTION to "clothing",
        Family.MENU to "restaurant",
        Family.SERVICE to "salon"
    )

    private fun rtl(locale: Locale) = locale.language in setOf("ur", "ar", "fa", "sd") && locale.script != "Latn"

    private fun context(tag: String, trade: String = "agri"): Context {
        val tmp = Files.createTempDirectory("rk-newstock").toFile()
        val settings = FileShopSettings(File(tmp, "s.properties"))
        settings.setBusinessName("Al-Noor Traders")
        settings.setTradeKey(trade)
        val locale = Locale.forLanguageTag(tag)
        return Context(desktopWords(locale), settings, ProfileImages(tmp), rtl(locale), locale)
    }

    @Test
    fun `every kind of shop has its three lines in every language`() {
        for (tag in tags) {
            val ctx = context(tag)
            val titles = HashSet<String>()
            for ((family, trade) in sample) {
                val kicker = NewStockWords.kicker(ctx, trade)
                val title = NewStockWords.title(ctx, trade)
                val message = NewStockWords.message(ctx, trade)
                assertTrue("$tag $family kicker is empty", kicker.isNotBlank())
                assertTrue("$tag $family title is empty", title.isNotBlank())
                assertTrue("$tag $family message is empty", message.isNotBlank())
                // Three lines, three different things: the corner label, the headline, what to do.
                assertEquals("$tag $family says one thing twice", 3, setOf(kicker, title, message).size)
                titles += title
            }
            assertEquals("$tag: two kinds of shop share one headline", sample.size, titles.size)
        }
    }

    @Test
    fun `a shop that sells no stock is never told it has stock, in English`() {
        val ctx = context("en")
        for ((family, trade) in sample) {
            if (family == Family.GOODS) continue
            for (line in listOf(NewStockWords.kicker(ctx, trade), NewStockWords.title(ctx, trade), NewStockWords.message(ctx, trade))) {
                assertFalse("$family says stock: \"$line\"", line.contains("stock", ignoreCase = true))
            }
        }
        assertEquals("New stock has arrived!", NewStockWords.title(ctx, "agri"))
        assertEquals("Now on our menu!", NewStockWords.title(ctx, "restaurant"))
    }

    @Test
    fun `the occasion takes the shop's words for New stock and its own for the rest`() {
        val ctx = context("en")
        for (o in PosterOccasion.entries) {
            when (o) {
                PosterOccasion.NEW_STOCK -> {
                    assertTrue(o.hasWords)
                    assertEquals(NewStockWords.title(ctx, "restaurant"), o.titleIn(ctx, "restaurant"))
                    assertEquals(NewStockWords.message(ctx, "salon"), o.messageIn(ctx, "salon"))
                }
                PosterOccasion.CUSTOM -> {
                    assertFalse(o.hasWords)
                    assertEquals("", o.titleIn(ctx, "restaurant"))
                    assertEquals("", o.messageIn(ctx, "restaurant"))
                }
                else -> {
                    assertTrue(o.hasWords)
                    assertEquals(ctx.getString(o.title), o.titleIn(ctx, "restaurant"))
                    assertEquals(ctx.getString(o.message), o.messageIn(ctx, "salon"))
                }
            }
        }
    }

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
    fun `the five kinds of shop draw their New stock poster in all nine languages`() {
        val dir = File("build/poster-proof").apply { mkdirs() }
        val thin = ArrayList<String>()
        for (tag in tags) {
            val locale = Locale.forLanguageTag(tag)
            val right = rtl(locale)
            for ((family, trade) in sample) {
                val ctx = context(tag, trade)
                val o = PosterOccasion.NEW_STOCK
                val img = PosterDraw.image(ctx, PosterInput(
                    occasion = o,
                    title = o.titleIn(ctx, trade),
                    message = o.messageIn(ctx, trade),
                    shopName = if (right) "النور ٹریڈرز" else "Al-Noor Traders",
                    phone = "0300 1234567",
                    address = "Main Bazaar, Sample Town",
                    logo = null,
                    mark = null,
                    fields = PosterFields(),
                    email = null,
                    whatsapp = false
                ))
                assertEquals(PosterDraw.WIDTH, img.width)
                assertEquals(PosterDraw.HEIGHT, img.height)
                ImageIO.write(img, "png", File(dir, "newstock-$tag-${family.name.lowercase()}.png"))
                val n = detail(img)
                if (n < 400) thin += "$tag/$family=$n"
            }
        }
        assertTrue("posters that look empty: $thin", thin.isEmpty())
    }
}
