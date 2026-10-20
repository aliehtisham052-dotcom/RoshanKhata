package com.innovation313.roshankhata.desktop.poster

import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.invoice.Context
import java.awt.image.BufferedImage

/*
 * The phone's posters on the computer: the hand-written edges around the
 * generated copies (scripts/gen_desktop_poster.py). What the phone's
 * PosterImage and TradeVocab do, answered from the computer's shop settings
 * and words. The heroes are always drawn flat (PosterFlat): the 3D ones
 * need Filament, which is the phone's.
 */

/** One poster: what PosterImage.Input carries on the phone, without the 3D hero and the video's moving layer. */
data class PosterInput(
    val occasion: PosterOccasion,
    val title: String,
    val message: String,
    val shopName: String,
    val phone: String?,
    val address: String?,
    val logo: Bitmap?,
    val mark: String?,
    val fields: PosterFields = PosterFields(),
    val photo: Bitmap? = null,
    val email: String? = null,
    val whatsapp: Boolean = false
)

object PosterDraw {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    /**
     * The still poster, 1080 x 1920 (WhatsApp Status's full screen), in the
     * occasion's design. [context] speaks the poster's language: its words,
     * its locale and whether it reads from the right.
     */
    fun image(context: Context, input: PosterInput): BufferedImage {
        val canvas = Canvas(WIDTH, HEIGHT)
        val page = PosterKit.Page(
            context, canvas, context.locale, context.rtl, input.title, input.message,
            input.shopName.ifBlank { " " }, input.phone, input.address, input.logo, input.mark,
            hero = null, heroOverlay = false, fields = input.fields, photo = input.photo, animated = false,
            email = input.email, whatsapp = input.whatsapp
        )
        PosterDesigns.draw(input.occasion, page)
        return canvas.picture(WIDTH, HEIGHT)
    }
}

/**
 * The phone's TradeVocab.items: three example goods of the shop's trade, in
 * the poster's language, the owner's own first for an Other shop.
 */
object TradeVocab {
    fun items(context: Context): List<String> {
        val key = context.settings.tradeKey()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "agri"
        fun list(k: String) = runCatching { context.words.list("vocab_${k}_items") }.getOrDefault(emptyList())
        val base = list(key).ifEmpty { list("agri") }
        if (key != "other") return base
        val own = context.settings.otherTradeItems().orEmpty()
            .split(',', '،', ';', '\n', '·').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
        return if (own.isEmpty()) base else (own + base).distinct().take(3)
    }
}
