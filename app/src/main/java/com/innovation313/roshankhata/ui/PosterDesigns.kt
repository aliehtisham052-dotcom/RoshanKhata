package com.innovation313.roshankhata.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.InvoiceFonts
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.ui.PosterKit.GOLD
import com.innovation313.roshankhata.ui.PosterKit.H
import com.innovation313.roshankhata.ui.PosterKit.Page
import com.innovation313.roshankhata.ui.PosterKit.W
import com.innovation313.roshankhata.ui.poster3d.Hero

/**
 * The premium occasion posters (11 Oct), each drawn from the owner's approved
 * mockup at 1080 × 1920 (the mockups' CSS pixels × 3):
 *
 *  - Eid ul-Fitr: teal night, hanging lanterns and a gold crescent (3D), the
 *    greeting in calligraphy, the message on glass, signed "from" the shop.
 *  - Eid ul-Adha: deep maroon with a faint eight-point pattern, a gold arch
 *    framing a mosque (3D) — a mosque, not the Kaaba, until a scholar is asked.
 *  - Ramazan: midnight blue, a large crescent and a lantern (3D).
 *  - Closed: a neon sign on a dark brick wall; the call button for urgent work.
 *  - Hours: a gold clock (3D, live time in the preview) and the timings in rows.
 *  - Thank you: cream paper, a green ribbon, a wax seal (3D) with the shop's mark.
 *  - Your own words: a glass panel over a drifting aurora.
 *
 * No people and no animals, as everywhere in the app. Every design reads
 * correctly in Urdu (right to left, Nastaliq) and in English.
 */
object PosterDesigns {

    /** The 3D hero a design shows, if any. */
    fun hero(o: PosterOccasion): Hero? = when (o) {
        PosterOccasion.NEW_STOCK -> Hero.CRATE
        PosterOccasion.EID_FITR -> Hero.EID_FITR
        PosterOccasion.EID_ADHA -> Hero.EID_ADHA
        PosterOccasion.RAMAZAN -> Hero.RAMAZAN
        PosterOccasion.HOURS -> Hero.HOURS
        PosterOccasion.THANKS -> Hero.THANKS
        PosterOccasion.CLOSED, PosterOccasion.CUSTOM -> null
    }

    fun draw(o: PosterOccasion, page: Page): PosterKit.Out = when (o) {
        PosterOccasion.NEW_STOCK -> PosterNewStock.draw(page)
        PosterOccasion.EID_FITR -> eidFitr(page)
        PosterOccasion.EID_ADHA -> eidAdha(page)
        PosterOccasion.RAMAZAN -> ramazan(page)
        PosterOccasion.CLOSED -> closed(page)
        PosterOccasion.HOURS -> hours(page)
        PosterOccasion.THANKS -> thanks(page)
        PosterOccasion.CUSTOM -> custom(page)
    }

    private fun str(page: Page, id: Int) = page.context.getString(id)

    private fun ground(c: Canvas, colours: IntArray, stops: FloatArray? = null) {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, H, colours, stops, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
    }

    private fun out(head: Pair<android.text.TextPaint, PosterMotion.Glint>, msg: android.text.StaticLayout?,
                    motion: PosterMotion.Spec) =
        PosterKit.Out(head.first.textSize, head.second.layout?.lineCount ?: 1,
            msg?.paint?.textSize ?: 0f, msg?.lineCount ?: 0, motion)

    // ---------------------------------------------------------------- Eid ul-Fitr

    private val FITR_STARS = listOf(
        PosterMotion.Star(210f, 360f, 4.2f), PosterMotion.Star(888f, 288f, 3.6f), PosterMotion.Star(780f, 630f, 4.5f),
        PosterMotion.Star(150f, 640f, 3.3f), PosterMotion.Star(960f, 560f, 3.0f), PosterMotion.Star(420f, 240f, 3.0f),
        PosterMotion.Star(660f, 150f, 3.6f), PosterMotion.Star(330f, 760f, 2.7f)
    )

    private fun eidFitr(page: Page): PosterKit.Out {
        val c = page.c
        ground(c, intArrayOf(0xFF0F5752.toInt(), 0xFF0A3D3A.toInt(), 0xFF052523.toInt(), 0xFF031716.toInt()),
            floatArrayOf(0f, 0.38f, 0.75f, 1f))
        PosterKit.halo(c, 540f, 690f, 900f, 0x66146F66)
        // Soft bokeh, as in the mockup.
        PosterKit.halo(c, 195f, 1005f, 150f, 0x2EF3CF6A)
        PosterKit.halo(c, 945f, 1275f, 190f, 0x1F78DCC8)
        PosterKit.halo(c, 744f, 804f, 80f, 0x38F3CF6A)
        PosterKit.halo(c, 279f, 1479f, 100f, 0x24F3CF6A)
        PosterKit.halo(c, 540f, 510f, 330f, 0x52F3CF6A)
        PosterKit.stars(c, FITR_STARS)
        page.drawHero(Hero.EID_FITR.rect) { fc ->
            PosterFlat.crescent(fc, 528f, 516f, 156f)
            PosterFlat.lantern(fc, 132f, 234f, 3f)
            PosterFlat.lantern(fc, 936f, 108f, 2.46f)
            PosterFlat.lantern(fc, 810f, 45f, 1.86f)
        }
        val head = PosterKit.foilHeadline(page, page.title, if (page.urdu) 198f else 180f, 84f, 834f)
        PosterKit.kicker(page, str(page, R.string.poster_kicker_eid), 1000f, 0xFF9FE3D6.toInt(), size = 30f, spaced = false)
        val panel = RectF(78f, 1098f, W - 78f, 1410f)
        PosterKit.glass(c, panel)
        val msg = PosterKit.message(page, page.message, panel.centerY(), 0xFFFFFFFF.toInt(), if (page.urdu) 48f else 46f,
            lines = 3, width = (panel.width() - 108f).toInt())
        PosterKit.signOff(page, str(page, R.string.poster_from), 1689f)
        PosterKit.mark(page)
        return out(head, msg, PosterMotion.Spec(stars = FITR_STARS, glint = head.second))
    }

    // ---------------------------------------------------------------- Ramazan

    private val RAMAZAN_STARS = listOf(
        PosterMotion.Star(600f, 180f, 3.6f), PosterMotion.Star(630f, 696f, 3.0f), PosterMotion.Star(360f, 60f, 3.3f),
        PosterMotion.Star(954f, 990f, 3.0f), PosterMotion.Star(108f, 1080f, 2.7f), PosterMotion.Star(780f, 420f, 4.2f),
        PosterMotion.Star(150f, 300f, 3.0f), PosterMotion.Star(1000f, 620f, 3.3f)
    )

    private fun ramazan(page: Page): PosterKit.Out {
        val c = page.c
        ground(c, intArrayOf(0xFF1A2E5E.toInt(), 0xFF101E44.toInt(), 0xFF080F27.toInt(), 0xFF050A1A.toInt()),
            floatArrayOf(0f, 0.35f, 0.75f, 1f))
        PosterKit.halo(c, 420f, 420f, 420f, 0x47F3CF6A)
        PosterKit.halo(c, 900f, 300f, 160f, 0x33F5A623)
        PosterKit.stars(c, RAMAZAN_STARS)
        page.drawHero(Hero.RAMAZAN.rect) { fc ->
            PosterFlat.crescent(fc, 408f, 426f, 225f)
            PosterFlat.lantern(fc, 900f, 150f, 2.6f)
        }
        val head = PosterKit.foilHeadline(page, page.title, if (page.urdu) 186f else 170f, 84f, 846f)
        PosterKit.kicker(page, str(page, R.string.poster_kicker_ramazan), 1000f, 0xFFB9C8F0.toInt(), size = 30f)
        val panel = RectF(78f, 1100f, W - 78f, 1440f)
        PosterKit.glass(c, panel, fill = 0x14FFFFFF)
        val msg = PosterKit.message(page, page.message, panel.centerY(), 0xF2F6EBD4.toInt(), if (page.urdu) 48f else 46f,
            lines = 3, width = (panel.width() - 108f).toInt())
        PosterKit.signOff(page, str(page, R.string.poster_from), 1700f, fromColour = 0xFFB9C8F0.toInt(),
            inner = 0xFF2A4B9A.toInt(), innerDark = 0xFF0A1430.toInt())
        PosterKit.mark(page)
        return out(head, msg, PosterMotion.Spec(stars = RAMAZAN_STARS, glint = head.second))
    }

    // ---------------------------------------------------------------- Eid ul-Adha

    private fun eidAdha(page: Page): PosterKit.Out {
        val c = page.c
        ground(c, intArrayOf(0xFF6A2433.toInt(), 0xFF4A1824.toInt(), 0xFF2E0F17.toInt(), 0xFF1E0A10.toInt()),
            floatArrayOf(0f, 0.4f, 0.8f, 1f))
        // A faint field of eight-point stars.
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x14FFE2A0 }
        var y = 60f
        var row = 0
        while (y < H) {
            var x = if (row % 2 == 0) 60f else 150f
            while (x < W + 60f) { c.drawPath(PosterKit.eightPoint(x, y, 34f, 24f), tile); c.drawCircle(x, y, 12f, tile); x += 180f }
            y += 156f; row++
        }
        PosterKit.halo(c, 540f, 560f, 520f, 0x59F3B45A)
        // The arch: straight sides rising into a pointed top, a finial over it.
        val arch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xD9E7C268.toInt() }
        for ((inset, w) in listOf(0f to 4.5f, 22f to 1.8f)) {
            arch.strokeWidth = w
            val l = 90f + inset
            val r = W - 90f - inset
            val p = Path().apply {
                moveTo(l, 1010f); lineTo(l, 520f)
                cubicTo(l, 300f + inset, 380f, 200f + inset, 540f, 132f + inset)
                cubicTo(700f, 200f + inset, r, 300f + inset, r, 520f)
                lineTo(r, 1010f)
            }
            c.drawPath(p, arch)
        }
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7C268.toInt() }
        c.drawLine(540f, 132f, 540f, 84f, Paint(gold).apply { strokeWidth = 4f })
        PosterFlat.crescent(c, 540f, 66f, 20f)
        for (x in floatArrayOf(90f, W - 90f)) diamond(c, x, 1010f, 14f, gold)
        page.drawHero(Hero.EID_ADHA.rect) { fc -> PosterFlat.mosque(fc, 540f, 819f, 630f) }
        val head = PosterKit.foilHeadline(page, page.title, if (page.urdu) 150f else 140f, 72f, 1059f, width = 900f)
        // The Eid prayer, in calligraphy for Arabic-script languages.
        val dua = str(page, R.string.poster_kicker_eid)
        val dp = PosterKit.greetingPaint(page.context, dua, if (page.urdu) 60f else 44f, 0xFFFBEBD0.toInt(), page.locale, page.urdu)
        if (!PosterKit.hasArabic(dua)) dp.typeface = Typeface.create(InvoiceFonts.playfairDisplay(page.context), Typeface.ITALIC)
        dp.textAlign = Paint.Align.CENTER
        PosterKit.fitWidth(dp, dua, 860f)
        PosterKit.inkLine(c, dua, dp, W / 2, 1218f)
        for (dx in floatArrayOf(-30f, 0f, 30f)) diamond(c, W / 2 + dx, 1294f, 9f, gold)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = 0x66E7C268 }
        c.drawLine(300f, 1294f, 480f, 1294f, line); c.drawLine(600f, 1294f, 780f, 1294f, line)
        val msg = PosterKit.message(page, page.message, 1400f, 0xD9FBEBD0.toInt(), if (page.urdu) 44f else 42f, lines = 2, width = 900)
        // The sign-off as a column: seal, "from", name, phone.
        PosterKit.seal(page.context, c, 540f, 1586f, 60f, page.shopName, page.logo,
            0xFFFBEBD0.toInt(), 0xFFE9D3B0.toInt(), 0xFF5A1E2B.toInt())
        val from = str(page, R.string.poster_from)
        val fp = PosterKit.textPaint(page.context, from, 31f, 0xD9FBEBD0.toInt(), false, page.locale, page.urdu)
        if (!page.urdu && !PosterKit.hasArabic(from)) fp.typeface = Typeface.create(InvoiceFonts.playfairDisplay(page.context), Typeface.ITALIC)
        fp.textAlign = Paint.Align.CENTER
        PosterKit.inkLine(c, from, fp, W / 2, 1688f)
        val np = PosterKit.textPaint(page.context, page.shopName, if (page.urdu) 60f else 58f, GOLD, true, page.locale, page.urdu)
        np.textAlign = Paint.Align.CENTER
        PosterKit.fitWidth(np, page.shopName, 860f)
        PosterKit.inkLine(c, page.shopName, np, W / 2, 1752f)
        if (!page.phone.isNullOrBlank()) {
            val pp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(InvoiceFonts.sora(page.context), Typeface.BOLD)
                textSize = 36f; color = 0xB3FBEBD0.toInt(); textAlign = Paint.Align.CENTER; letterSpacing = 0.14f
            }
            PosterKit.inkLine(c, page.phone.trim(), pp, W / 2, 1818f)
        }
        // A gold rule with diamonds along the foot.
        c.drawLine(60f, 1858f, W - 60f, 1858f, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = 0x80E7C268.toInt() })
        var dx = 120f
        while (dx < W - 60f) { diamond(c, dx, 1858f, 7f, gold); dx += 140f }
        PosterKit.mark(page, 0x59FBEBD0, cy = 1892f)
        return out(head, msg, PosterMotion.Spec(glint = head.second,
            stars = listOf(PosterMotion.Star(190f, 300f, 3.3f), PosterMotion.Star(890f, 330f, 3.0f), PosterMotion.Star(250f, 820f, 2.7f),
                PosterMotion.Star(840f, 760f, 3.0f))))
    }

    private fun diamond(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        c.drawPath(Path().apply { moveTo(cx, cy - s); lineTo(cx + s, cy); lineTo(cx, cy + s); lineTo(cx - s, cy); close() }, p)
    }

    // ---------------------------------------------------------------- Closed

    private const val NEON = 0xFFFF4D5E.toInt()

    private fun closed(page: Page): PosterKit.Out {
        val c = page.c
        ground(c, intArrayOf(0xFF1C1414.toInt(), 0xFF140E0E.toInt(), 0xFF0B0808.toInt()), floatArrayOf(0f, 0.5f, 1f))
        // Bricks: mortar lines, each course offset by half a brick, a faint highlight on each top edge.
        val mortar = Paint().apply { color = 0x66000000; strokeWidth = 6f }
        val lip = Paint().apply { color = 0x0FFFFFFF; strokeWidth = 2f }
        var y = 0f
        var course = 0
        while (y < H) {
            c.drawLine(0f, y, W, y, mortar)
            c.drawLine(0f, y + 4f, W, y + 4f, lip)
            var x = if (course % 2 == 0) 0f else 84f
            while (x < W) { c.drawLine(x, y, x, y + 84f, mortar); x += 168f }
            y += 84f; course++
        }
        val vignette = Paint()
        vignette.shader = RadialGradient(540f, 770f, 1250f, intArrayOf(0x00000000, 0x00000000, 0xB3000000.toInt()),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, vignette)
        // The red light the sign throws on the wall.
        PosterKit.halo(c, 540f, 590f, 620f, 0x3DFF4D5E)
        PosterKit.header(page, top = 60f, sealSize = 96f, centred = true, nameColour = 0xFFF2EDE8.toInt(),
            inner = 0xFF3A2A28.toInt(), innerDark = 0xFF140E0E.toInt())
        // The neon sign, hung on two short posts.
        val sign = RectF(126f, 276f, W - 126f, 906f)
        val post = Paint().apply { color = 0xFF555555.toInt() }
        c.drawRect(180f, 258f, 189f, 288f, post); c.drawRect(W - 189f, 258f, W - 180f, 288f, post)
        val tubeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 30f; color = 0x99FF4D5E.toInt()
            maskFilter = android.graphics.BlurMaskFilter(34f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(sign, 90f, 90f, tubeGlow)
        val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FF4D5E }
        c.drawRoundRect(sign, 90f, 90f, inside)
        val tube = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12f; color = NEON }
        c.drawRoundRect(sign, 90f, 90f, tube)
        tube.strokeWidth = 4f; tube.color = 0xFFFFC2C8.toInt()
        c.drawRoundRect(sign, 90f, 90f, tube)
        // The word in light: a red halo under a near-white core.
        val word = str(page, R.string.poster_neon_closed)
        val wp = if (PosterKit.hasArabic(word)) PosterKit.greetingPaint(page.context, word, 300f, 0xFFFFE3E6.toInt(), page.locale, page.urdu)
            else android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(InvoiceFonts.sora(page.context), Typeface.BOLD)
                textSize = 170f; color = 0xFFFFE3E6.toInt(); letterSpacing = 0.12f
            }
        wp.textAlign = Paint.Align.CENTER
        PosterKit.fitWidth(wp, word, sign.width() - 120f, 60f)
        val wordY = if (PosterKit.hasArabic(word)) 548f else 560f
        val halo = android.text.TextPaint(wp).apply {
            color = NEON; maskFilter = android.graphics.BlurMaskFilter(26f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        PosterKit.inkLine(c, word, halo, W / 2, wordY)
        PosterKit.inkLine(c, word, wp, W / 2, wordY)
        // The owner's big line under it, in warm light.
        val title = page.title.ifBlank { " " }
        val tp = PosterKit.textPaint(page.context, title, if (page.urdu) 72f else 66f, 0xFFF7D58A.toInt(), true, page.locale, page.urdu)
        tp.setShadowLayer(18f, 0f, 0f, 0xCCF3A623.toInt())
        val tl = PosterKit.fitBlock(title, tp, (sign.width() - 110f).toInt(), page.rtl, 2, 40f, 1.0f)
        PosterKit.put(c, tl, sign.left + 55f, 790f - tl.height / 2f)
        val panel = RectF(78f, 1008f, W - 78f, 1360f)
        PosterKit.glass(c, panel, fill = 0x14FFFFFF, edge = 0x33FFFFFF)
        val msg = PosterKit.message(page, page.message, panel.centerY(), 0xFFF2EDE8.toInt(), if (page.urdu) 48f else 46f,
            lines = 3, width = (panel.width() - 108f).toInt())
        if (!page.phone.isNullOrBlank()) {
            val call = str(page, R.string.poster_call_urgent)
            val cp = PosterKit.textPaint(page.context, call, if (page.urdu) 38f else 36f, 0xB3F2EDE8.toInt(), false, page.locale, page.urdu)
            cp.textAlign = Paint.Align.CENTER
            PosterKit.inkLine(c, call, cp, W / 2, 1470f)
        }
        PosterKit.phonePill(page, RectF(102f, 1536f, W - 102f, 1716f), disc = 0xFF2A2120.toInt(), discDark = 0xFF120D0C.toInt())
        PosterKit.address(page, 1790f, colour = 0xB3F2EDE8.toInt())
        PosterKit.mark(page, 0x59F2EDE8)
        val fit = PosterKit.Out(tp.textSize, tl.lineCount, msg?.paint?.textSize ?: 0f, msg?.lineCount ?: 0,
            PosterMotion.Spec(neon = PosterMotion.Neon(sign, 90f, NEON)))
        return fit
    }

    // ---------------------------------------------------------------- Hours

    private fun hours(page: Page): PosterKit.Out {
        val c = page.c
        PosterNewStock.drawBackground(c, spotlight = false)
        PosterKit.halo(c, 540f, 426f, 380f, 0x4DF3CF6A)
        PosterKit.header(page, top = 54f, sealSize = 90f, centred = true)
        page.drawHero(Hero.HOURS.rect) { fc -> PosterFlat.clock(fc, 540f, 426f, 240f) }
        val head = PosterKit.foilHeadline(page, page.title, if (page.urdu) 120f else 112f, 64f, 812f, greeting = false)
        // The timings: one row per line of the message; "Mon – Sat: 9 – 8" splits into label and time.
        val rows = page.message.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        // The panel is as tall as its rows need, centred in the space under the headline.
        val wanted = if (rows.size <= 1) 300f else 48f + rows.size * 118f
        val panelH = wanted.coerceAtMost(510f)
        val panel = RectF(72f, 1191f - panelH / 2, W - 72f, 1191f + panelH / 2)
        PosterKit.glass(c, panel)
        var msgSize = 0f
        if (rows.isNotEmpty()) {
            val inner = panel.width() - 96f
            val rowH = (panel.height() - 48f) / rows.size
            val rule = Paint().apply { color = 0x26FFFFFF; strokeWidth = 2f }
            for ((i, row) in rows.withIndex()) {
                val cy = panel.top + 24f + rowH * (i + 0.5f)
                if (i > 0) c.drawLine(panel.left + 48f, panel.top + 24f + rowH * i, panel.right - 48f, panel.top + 24f + rowH * i, rule)
                val cut = row.indexOfFirst { it == ':' || it == '：' }
                val size = if (page.urdu) 48f else 44f
                if (cut in 1 until row.length - 1) {
                    val label = row.substring(0, cut).trim()
                    val value = row.substring(cut + 1).trim()
                    val lp = PosterKit.textPaint(page.context, label, size, GOLD, true, page.locale, page.urdu)
                    val vp = PosterKit.textPaint(page.context, value, size, 0xFFFFFFFF.toInt(), true, page.locale, page.urdu)
                    if (value.contains(Regex("(?i)closed|بند|chutti|چھٹی|band"))) vp.color = 0xFFFF8A80.toInt()
                    PosterKit.fitWidth(lp, label, inner * 0.45f, 26f)
                    PosterKit.fitWidth(vp, value, inner * 0.52f, 26f)
                    lp.textAlign = if (page.rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                    vp.textAlign = if (page.rtl) Paint.Align.LEFT else Paint.Align.RIGHT
                    PosterKit.inkLine(c, label, lp, if (page.rtl) panel.right - 48f else panel.left + 48f, cy)
                    PosterKit.inkLine(c, value, vp, if (page.rtl) panel.left + 48f else panel.right - 48f, cy)
                    msgSize = maxOf(msgSize, vp.textSize)
                } else {
                    val p = PosterKit.textPaint(page.context, row, size, 0xFFFFFFFF.toInt(), false, page.locale, page.urdu)
                    val l = PosterKit.fitBlock(row, p, inner.toInt(), page.rtl, if (rows.size == 1) 3 else 1, 26f)
                    PosterKit.put(c, l, panel.left + 48f, cy - l.height / 2f)
                    msgSize = maxOf(msgSize, p.textSize)
                }
            }
        }
        PosterKit.phonePill(page, RectF(102f, 1494f, W - 102f, 1662f), size = 64f)
        PosterKit.address(page, 1734f)
        PosterKit.mark(page)
        return PosterKit.Out(head.first.textSize, head.second.layout?.lineCount ?: 1, msgSize, rows.size,
            PosterMotion.Spec(glint = head.second))
    }

    // ---------------------------------------------------------------- Thank you

    private const val INK_GREEN = 0xFF0F4A33.toInt()

    private fun thanks(page: Page): PosterKit.Out {
        val c = page.c
        ground(c, intArrayOf(0xFFFDF8EC.toInt(), 0xFFF7EDD6.toInt(), 0xFFEFDFBD.toInt()), floatArrayOf(0f, 0.55f, 1f))
        // Laid paper: faint diagonal lines.
        val laid = Paint().apply { color = 0x0D8A5E14; strokeWidth = 2f }
        var d = -H
        while (d < W) { c.drawLine(d, H, d + H * 0.47f, 0f, laid); d += 18f }
        PosterKit.halo(c, 540f, 700f, 700f, 0x66FFFFFF)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x8CB8862B.toInt(); strokeWidth = 3f }
        c.drawRoundRect(RectF(42f, 42f, W - 42f, H - 42f), 12f, 12f, border)
        border.strokeWidth = 1.5f; border.color = 0x66B8862B
        c.drawRoundRect(RectF(60f, 60f, W - 60f, H - 60f), 6f, 6f, border)
        // The green ribbon across the top corner, on the reading side's start.
        c.save()
        val rx = if (page.rtl) W - 270f else 270f
        c.rotate(if (page.rtl) 38f else -38f, rx, 261f)
        val band = RectF(rx - 470f, 261f - 51f, rx + 470f, 261f + 51f)
        val green = Paint(Paint.ANTI_ALIAS_FLAG)
        green.shader = LinearGradient(0f, band.top, 0f, band.bottom,
            intArrayOf(0xFF2C8C62.toInt(), 0xFF0F4A33.toInt(), 0xFF0A3524.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        green.setShadowLayer(24f, 0f, 14f, 0x59000000)
        c.drawRect(band, green)
        val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCF3CF6A.toInt(); strokeWidth = 3f }
        c.drawLine(band.left, band.top + 12f, band.right, band.top + 12f, thread)
        c.drawLine(band.left, band.bottom - 12f, band.right, band.bottom - 12f, thread)
        c.restore()
        // The big word: Nastaliq in Urdu (as the mockup), Playfair italic in English.
        val head = PosterKit.foilHeadline(page, page.title, if (page.urdu) 250f else 190f, 90f, 640f, width = 900f,
            greeting = false, shadow = 0x338A5E14, light = 0xFFD9A441.toInt(), mid = 0xFFB07A22.toInt(), dark = 0xFF6E4A10.toInt())
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8962B.toInt() }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = 0x99C8962B.toInt() }
        c.drawLine(330f, 1010f, 510f, 1010f, rule); c.drawLine(570f, 1010f, 750f, 1010f, rule)
        diamond(c, 540f, 1010f, 10f, gold)
        val msg = PosterKit.message(page, page.message, 1150f, 0xFF1F3A2C.toInt(), if (page.urdu) 54f else 50f, lines = 3, width = 880)
        // The seal's shadow on the paper, under the 3D seal.
        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        shade.shader = RadialGradient(540f, 1560f, 150f, 0x400A3524, 0x000A3524, Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.35f, 540f, 1560f); c.drawCircle(540f, 1560f, 150f, shade); c.restore()
        page.drawHero(Hero.THANKS.rect) { fc -> PosterFlat.seal(page.context, fc, 540f, 1476f, 118f, page.shopName, page.logo) }
        val np = PosterKit.textPaint(page.context, page.shopName, if (page.urdu) 58f else 54f, INK_GREEN, true, page.locale, page.urdu)
        np.textAlign = Paint.Align.CENTER
        PosterKit.fitWidth(np, page.shopName, 860f)
        PosterKit.inkLine(c, page.shopName, np, W / 2, 1694f)
        // The number isolated left-to-right: inside an Urdu line its two groups would swap.
        val bits = listOfNotNull(page.phone?.trim()?.takeIf { it.isNotEmpty() }?.let { Format.ltr(it) },
            page.address?.trim()?.takeIf { it.isNotEmpty() })
        if (bits.isNotEmpty()) {
            val line = bits.joinToString("  •  ")
            val lp = PosterKit.textPaint(page.context, line, if (page.urdu) 36f else 34f, 0xFF5F584D.toInt(), false, page.locale, page.urdu)
            lp.textAlign = Paint.Align.CENTER
            PosterKit.fitWidth(lp, line, 880f, 24f)
            PosterKit.inkLine(c, line, lp, W / 2, 1764f)
        }
        PosterKit.mark(page, 0x662A2620, cy = 1830f)
        return out(head, msg, PosterMotion.Spec(glint = head.second))
    }

    // ---------------------------------------------------------------- Your own words

    private fun custom(page: Page): PosterKit.Out {
        val c = page.c
        ground(c, intArrayOf(0xFF1A2F7A.toInt(), 0xFF13235C.toInt(), 0xFF0B1638.toInt()), floatArrayOf(0f, 0.5f, 1f))
        // The aurora, still: two blues and a gold light, as in the mockup.
        PosterKit.halo(c, 270f, 330f, 760f, 0xE63D63E0.toInt())
        PosterKit.halo(c, 1050f, 930f, 660f, 0x73C8962B)
        PosterKit.halo(c, 330f, 1740f, 720f, 0xB32A4FC8.toInt())
        val scan = Paint().apply { color = 0x06FFFFFF }
        var sy = 0f
        while (sy < H) { c.drawRect(0f, sy, W, sy + 3f, scan); sy += 9f }
        val trade = Trade.current(page.context).example(page.context)
        PosterKit.header(page, top = 66f, sealSize = 120f, tradeLine = trade,
            inner = 0xFF2A4FC8.toInt(), innerDark = 0xFF0B1638.toInt())
        val panel = RectF(66f, 372f, W - 66f, 1404f)
        PosterKit.glass(c, panel, radius = 84f, fill = 0x29FFFFFF, edge = 0x40FFFFFF)
        // The "announcement" tag at the panel's top, reading side's end.
        val tag = str(page, R.string.poster_kicker_custom)
        val tgp = PosterKit.textPaint(page.context, tag, if (page.urdu) 38f else 30f, 0xFF1F1505.toInt(), true, page.locale, page.urdu)
        if (!page.urdu && !PosterKit.hasArabic(tag)) tgp.letterSpacing = 0.2f
        val tw = tgp.measureText(tag) + 84f
        val tagR = if (page.rtl) RectF(panel.right - 54f - tw, panel.top + 54f, panel.right - 54f, panel.top + 132f)
            else RectF(panel.left + 54f, panel.top + 54f, panel.left + 54f + tw, panel.top + 132f)
        val tgFill = Paint(Paint.ANTI_ALIAS_FLAG)
        tgFill.shader = LinearGradient(0f, tagR.top, 0f, tagR.bottom, 0xFFFBE6A2.toInt(), 0xFFD9A441.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(tagR, 39f, 39f, tgFill)
        tgp.textAlign = Paint.Align.CENTER
        PosterKit.inkLine(c, tag, tgp, tagR.centerX(), tagR.centerY())
        // The big line, white, set from the reading side.
        val align = Layout.Alignment.ALIGN_NORMAL
        val inner = (panel.width() - 120f).toInt()
        val title = page.title.ifBlank { " " }
        val tp = PosterKit.textPaint(page.context, title, if (page.urdu) 112f else 100f, 0xFFFFFFFF.toInt(), true, page.locale, page.urdu)
        if (!page.urdu && !PosterKit.hasArabic(title)) tp.typeface = Typeface.create(InvoiceFonts.playfairDisplay(page.context), Typeface.BOLD)
        tp.setShadowLayer(24f, 0f, 10f, 0x66000000)
        var tl = PosterKit.block(title, tp, inner, page.rtl, if (page.urdu) 1.0f else 1.05f, align)
        while ((tl.lineCount > 4 || tl.height > 560) && tp.textSize > 54f) {
            tp.textSize -= 4f
            tl = PosterKit.block(title, tp, inner, page.rtl, if (page.urdu) 1.0f else 1.05f, align)
        }
        val titleTop = panel.top + 170f
        PosterKit.put(c, tl, panel.left + 60f, titleTop)
        val underY = titleTop + tl.height + 30f
        val under = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 9f; color = GOLD; strokeCap = Paint.Cap.ROUND }
        if (page.rtl) c.drawLine(panel.right - 60f - 150f, underY, panel.right - 60f, underY, under)
        else c.drawLine(panel.left + 60f, underY, panel.left + 210f, underY, under)
        var msgLayout: android.text.StaticLayout? = null
        if (page.message.isNotBlank()) {
            val mp = PosterKit.textPaint(page.context, page.message, if (page.urdu) 46f else 44f, 0xE6FFFFFF.toInt(), false, page.locale, page.urdu)
            val room = panel.bottom - 50f - (underY + 40f)
            var ml = PosterKit.block(page.message, mp, inner, page.rtl, if (page.urdu) 1.0f else 1.15f, align)
            while (ml.height > room && mp.textSize > 28f) {
                mp.textSize -= 2f
                ml = PosterKit.block(page.message, mp, inner, page.rtl, if (page.urdu) 1.0f else 1.15f, align)
            }
            PosterKit.put(c, ml, panel.left + 60f, underY + 40f)
            msgLayout = ml
        }
        val pill = RectF(102f, 1446f, W - 102f, 1614f)
        PosterKit.phonePill(page, pill, disc = 0xFF16244F.toInt(), discDark = 0xFF0B1220.toInt(), size = 64f)
        PosterKit.address(page, 1690f, colour = 0xD9FFFFFF.toInt())
        PosterKit.mark(page, 0x59FFFFFF)
        return PosterKit.Out(tp.textSize, tl.lineCount, msgLayout?.paint?.textSize ?: 0f, msgLayout?.lineCount ?: 0,
            PosterMotion.Spec(aurora = PosterMotion.Aurora(intArrayOf(0xFF5B83FF.toInt(), 0xFFF3CF6A.toInt(), 0xFF7A5BFF.toInt()),
                listOf(panel, pill))))
    }
}
