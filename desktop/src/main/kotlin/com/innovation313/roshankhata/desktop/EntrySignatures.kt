package com.innovation313.roshankhata.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * A customer's signature on one entry, on the computer (Marhala 4, part 6; the phone's EntrySignature since
 * 6 Oct): proof for the day someone says "I never took that". Filed by entry number, one folder per shop
 * (entry ids repeat across shops), so no database column is needed. Kept on this computer only — the text
 * backup carries no pictures, which the form says (`sig_hint_pc`).
 */
object EntrySignatures {
    fun dir(home: File, businessId: Long): File = File(home, if (businessId == 1L) "entry_signatures" else "entry_signatures_b$businessId")

    fun file(home: File, businessId: Long, entryId: Long): File = File(dir(home, businessId), "sig_$entryId.png")

    fun load(home: File, businessId: Long, entryId: Long): BufferedImage? =
        file(home, businessId, entryId).takeIf { it.isFile }?.let { f -> runCatching { ImageIO.read(f) }.getOrNull() }

    /** When it was signed (the file's time), or null. */
    fun signedAt(home: File, businessId: Long, entryId: Long): Long? =
        file(home, businessId, entryId).takeIf { it.isFile }?.lastModified()?.takeIf { it > 0 }

    /** Written whole or not at all. */
    fun save(home: File, businessId: Long, entryId: Long, picture: BufferedImage) {
        val f = file(home, businessId, entryId)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        check(ImageIO.write(picture, "png", tmp)) { "no PNG writer" }
        if (f.exists()) f.delete()
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }

    fun delete(home: File, businessId: Long, entryId: Long) { file(home, businessId, entryId).delete() }

    /**
     * The strokes drawn on the pad as a picture of the pad's own size: dark ink on nothing (it sits on the
     * receipt's white), round ends. [strokes] are points in the pad's pixels.
     */
    fun picture(strokes: List<List<Pair<Float, Float>>>, width: Int, height: Int, ink: Float): BufferedImage {
        val img = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.color = Color(0x1B, 0x1B, 0x1B)
            g.stroke = BasicStroke(ink, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (s in strokes) {
                if (s.size == 1) {
                    val (x, y) = s[0]
                    g.fill(java.awt.geom.Ellipse2D.Float(x - ink / 2, y - ink / 2, ink, ink))
                    continue
                }
                val path = java.awt.geom.Path2D.Float()
                path.moveTo(s[0].first, s[0].second)
                for (k in 1 until s.size) path.lineTo(s[k].first, s[k].second)
                g.draw(path)
            }
        } finally {
            g.dispose()
        }
        return img
    }
}
