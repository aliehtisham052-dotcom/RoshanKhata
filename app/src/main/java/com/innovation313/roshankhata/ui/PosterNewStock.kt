package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.innovation313.roshankhata.data.NewStockWords
import com.innovation313.roshankhata.data.TradeVocab
import com.innovation313.roshankhata.ui.poster3d.Hero

/**
 * New stock, the premium design (11 Oct, the owner's approved mockup): a dark
 * emerald studio, a spotlight from above, the shop's crate turning on a gold
 * rimmed plinth (a real 3D render, see poster3d.HeroStage), the headline in
 * gold foil, what arrived on a glass strip, and a gold call button.
 *
 * Everything on it speaks the poster's language: in Urdu the type is
 * Nastaliq and the layout reads from the right; in English it is Playfair
 * and Manrope from the left. The phone number stays in 0–9 so it can be
 * dialled as printed.
 *
 * Drawn at 1080 × 1920. [HERO] is where the 3D crate sits — the live preview
 * lays its TextureView over the same fractions of the picture.
 */
object PosterNewStock {

    /** The crate's place on the 1080 × 1920 poster. */
    val HERO: RectF get() = Hero.CRATE.rect

    private const val W = 1080f
    private const val GOLD = 0xFFF3CF6A.toInt()

    /** The emerald studio; [spotlight] adds the cone of light from above. */
    fun drawBackground(c: Canvas, spotlight: Boolean = true, keepClear: RectF? = null) {
        val bg = Paint()
        bg.shader = LinearGradient(0f, 0f, 0f, 1920f,
            intArrayOf(0xFF071A12.toInt(), 0xFF0A2A1C.toInt(), 0xFF040C08.toInt()),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, 1920f, bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(540f, 960f, 820f, 0x991E6B4A.toInt(), 0x001E6B4A, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, 1920f, glow)
        val dust = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        for ((x, y, r) in DUST) {
            // A speck that falls on a panel reads as a bullet before its first word or a stain on its edge (the Hours
            // poster, 13 Nov), so a design with a panel says where it stands and the specks keep clear of it.
            if (keepClear != null && x > keepClear.left - r - 8f && x < keepClear.right + r + 8f &&
                y > keepClear.top - r - 8f && y < keepClear.bottom + r + 8f) continue
            dust.alpha = 170
            c.drawCircle(x, y, r, dust)
        }
        if (!spotlight) return
        // The spotlight: a soft cone from the top centre down to the plinth.
        val cone = Path().apply { moveTo(470f, -30f); lineTo(610f, -30f); lineTo(900f, 1380f); lineTo(180f, 1380f); close() }
        val beam = Paint(Paint.ANTI_ALIAS_FLAG)
        beam.shader = LinearGradient(0f, 0f, 0f, 1380f,
            intArrayOf(0x38FFF0C8, 0x0DFFF0C8, 0x00FFF0C8), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        beam.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(cone, beam)
        // The plinth's pool of light, under where the hero stands.
        val pool = Paint(Paint.ANTI_ALIAS_FLAG)
        pool.shader = RadialGradient(540f, 1100f, 340f, 0x59F3CF6A, 0x00F3CF6A, Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.25f, 540f, 1100f); c.drawCircle(540f, 1100f, 340f, pool); c.restore()
    }

    private val DUST = listOf(Triple(192f, 630f, 4.8f), Triple(900f, 570f, 3.9f), Triple(288f, 900f, 3.3f),
        Triple(828f, 858f, 5.1f), Triple(120f, 1140f, 3.6f), Triple(978f, 1110f, 3.6f), Triple(666f, 642f, 4.2f))

    fun draw(page: PosterKit.Page): PosterKit.Out {
        val c = page.c
        val context = page.context
        val urdu = page.urdu
        // The shop's trade words the corner label and the line under the headline (NewStockWords, 13 Nov 2026).
        val trade = TradeVocab.key(context)
        drawBackground(c)

        // The heading is the shop's name alone (12 Oct, the owner: never a list of products).
        PosterKit.header(page, top = 66f, sealSize = 138f)

        PosterKit.kicker(page, NewStockWords.kicker(context, trade), 300f, GOLD, size = 33f)

        // Headline in gold foil.
        val head = PosterKit.foilHeadline(page, page.title, if (urdu) 138f else 126f, 72f, 470f, greeting = false)

        // The hero: the owner's own photo of the stock if he gave one, else the
        // 3D crate if the phone could render it, else a flat one.
        val photo = page.photo
        if (photo != null) photoCard(c, photo) else page.drawHero(HERO) { flatCrate(it, trade) }

        // What arrived, on a strip of glass: the owner's list, or his trade's usual three.
        val list = page.fields.items.ifEmpty { TradeVocab.items(context).take(3) }
        val items = list.joinToString("   ◆   ")
        val strip = RectF(66f, 1230f, W - 66f, 1374f)
        PosterKit.glass(c, strip, radius = 72f, fill = 0x12FFFFFF)
        val ip = PosterKit.textPaint(context, items, if (urdu) 42f else 39f, Color.WHITE, urdu, page.locale, urdu)
        ip.textAlign = Paint.Align.CENTER
        PosterKit.fitWidth(ip, items, strip.width() - 96f, floor = 26f)
        PosterKit.inkLine(c, items, ip, 540f, strip.centerY())

        // The owner's line under it.
        val msg = page.message.ifBlank { NewStockWords.message(context, trade) }
        val ml = PosterKit.message(page, msg, 1460f, 0xDDF6EBD0.toInt(), if (urdu) 45f else 46f, lines = 2, italicLatin = true, floor = 34f)

        PosterKit.phonePill(page, RectF(102f, 1530f, W - 102f, 1710f))
        PosterKit.address(page, 1770f)
        PosterKit.mark(page)
        return PosterKit.Out(head.first.textSize, head.second.layout?.lineCount ?: 1, ml?.paint?.textSize ?: 0f, ml?.lineCount ?: 0,
            PosterMotion.Spec(glint = head.second,
                stars = DUST.map { PosterMotion.Star(it.first, it.second, it.third) }))
    }

    /**
     * The owner's photo of what arrived, as a print in a gold frame, a little
     * turned, standing in the crate's light. Cropped to fill, never stretched.
     */
    private fun photoCard(c: Canvas, photo: Bitmap) {
        val card = RectF(240f, 600f, 840f, 1150f)
        c.save()
        c.rotate(-3f, card.centerX(), card.centerY())
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1F15.toInt(); setShadowLayer(40f, 0f, 26f, 0x99000000.toInt()) }
        c.drawRoundRect(card, 28f, 28f, shadow)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.shader = LinearGradient(card.left, card.top, card.right, card.bottom,
            intArrayOf(0xFFFFF1C1.toInt(), 0xFFC8962B.toInt(), 0xFFF7DC8C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(card, 28f, 28f, gold)
        val inner = RectF(card.left + 16f, card.top + 16f, card.right - 16f, card.bottom - 16f)
        val clip = Path().apply { addRoundRect(inner, 16f, 16f, Path.Direction.CW) }
        c.clipPath(clip)
        val s = maxOf(inner.width() / photo.width, inner.height() / photo.height)
        val w = photo.width * s
        val h = photo.height * s
        c.drawBitmap(photo, null, RectF(inner.centerX() - w / 2, inner.centerY() - h / 2, inner.centerX() + w / 2, inner.centerY() + h / 2),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
    }

    /**
     * A flat crate for phones that cannot render the 3D one, for the list's thumbnails and for the computer (13 Nov
     * 2026, the poster review). It was an empty green square floating above its plinth, the same for every shop;
     * now it stands on the plinth, closed with gold straps, and wears the shop's own glyph ([PosterEmblems], the
     * shop-type icon) in gold on a round plaque, so a medical shop's poster shows a capsule and a mobile shop's a
     * phone. Drawn inside [HERO].
     */
    private fun flatCrate(c: Canvas, trade: String) {
        val cx = 540f
        // The plinth, its gold rim, and the crate's shadow on it.
        val plinthTop = RectF(250f, 975f, 830f, 1105f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, 1020f, 320f, 0xFF2F8A62.toInt(), 0xFF12452F.toInt(), Shader.TileMode.CLAMP)
        c.drawOval(plinthTop, p)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4.5f; color = 0xFFD9B25A.toInt() }
        c.drawOval(plinthTop, rim)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        shade.shader = RadialGradient(cx, 1040f, 250f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.2f, cx, 1040f); c.drawCircle(cx, 1040f, 250f, shade); c.restore()

        // The crate: the front, the lid seen from a little above, a lip, gold straps at both corners.
        val front = RectF(330f, 725f, 750f, 1040f)
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = LinearGradient(front.left, front.top, front.right, front.bottom,
            0xFF2C8C62.toInt(), 0xFF0B3B27.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(front, body)
        val top = Path().apply {
            moveTo(front.left, front.top); lineTo(front.right, front.top)
            lineTo(front.right - 34f, front.top - 66f); lineTo(front.left + 34f, front.top - 66f); close()
        }
        val lid = Paint(Paint.ANTI_ALIAS_FLAG)
        lid.shader = LinearGradient(0f, front.top - 66f, 0f, front.top, 0xFF4FB488.toInt(), 0xFF2C8C62.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(top, lid)
        // Planks: a dark seam with a light edge under it.
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f; color = 0x40000000 }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = 0x1AFFFFFF }
        for (y in floatArrayOf(front.top + 124f, front.top + 222f)) {
            c.drawLine(front.left + 36f, y, front.right - 36f, y, seam)
            c.drawLine(front.left + 36f, y + 3f, front.right - 36f, y + 3f, edge)
        }
        // The lip across the top of the front, and the straps.
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.shader = LinearGradient(0f, front.top, 0f, front.bottom, 0xFFF3CF6A.toInt(), 0xFFC8962B.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(RectF(front.left - 10f, front.top, front.right + 10f, front.top + 30f), gold)
        c.drawRect(RectF(front.left, front.top + 30f, front.left + 36f, front.bottom), gold)
        c.drawRect(RectF(front.right - 36f, front.top + 30f, front.right, front.bottom), gold)
        val rivet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A641B.toInt() }
        for (y in floatArrayOf(front.top + 66f, front.top + 160f, front.top + 254f)) {
            c.drawCircle(front.left + 18f, y, 5.5f, rivet); c.drawCircle(front.right - 18f, y, 5.5f, rivet)
        }
        // A soft sheen from the top left.
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
        sheen.shader = LinearGradient(front.left, front.top, front.centerX(), front.bottom, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(RectF(front.left + 36f, front.top + 30f, front.right - 36f, front.bottom), sheen)

        // The plaque: a deep green disc in a gold ring, the shop's glyph in gold on it.
        val py = 905f
        val disc = Paint(Paint.ANTI_ALIAS_FLAG)
        disc.shader = RadialGradient(cx, py - 22f, 124f, 0xFF145A3E.toInt(), 0xFF06241A.toInt(), Shader.TileMode.CLAMP)
        disc.setShadowLayer(16f, 0f, 9f, 0x66000000)
        c.drawCircle(cx, py, 100f, disc)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
        ring.shader = LinearGradient(cx - 100f, py - 100f, cx + 100f, py + 100f, 0xFFFFF1C1.toInt(), 0xFFC8962B.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, py, 100f, ring)
        ring.shader = null; ring.strokeWidth = 2f; ring.color = 0x66F3CF6A
        c.drawCircle(cx, py, 84f, ring)
        PosterEmblems.draw(c, trade, cx, py, 124f, 0xFFFFF1C1.toInt(), 0xFFD9A441.toInt())
    }

}
