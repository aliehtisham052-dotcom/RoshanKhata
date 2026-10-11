package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.innovation313.roshankhata.data.PdfRtl
import com.innovation313.roshankhata.data.PosterFields
import com.innovation313.roshankhata.data.PosterOccasion
import java.util.Locale

/**
 * A WhatsApp Status poster, 1080 × 1920 (9:16, the size Status shows full
 * screen), in the occasion's premium design (see [PosterDesigns]).
 *
 * The words, the layout direction and the type follow the app's language:
 * Urdu reads from the right in Nastaliq, English from the left in Playfair
 * and Manrope. NOTHING IS CUT: lines shrink step by step until they fit.
 *
 * Fixed colours, not the theme's: the picture goes to other people's phones.
 */
object PosterImage {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    data class Input(
        val occasion: PosterOccasion,
        val title: String,
        val message: String,
        val shopName: String,
        val phone: String?,
        val address: String?,
        val logo: Bitmap?,
        val mark: String?,
        /** The 3D hero, already rendered for this design (see poster3d.HeroStage); null draws it flat. */
        val hero: Bitmap? = null,
        /** True when a live 3D view is laid over the hero's place: leave that place empty. */
        val heroOverlay: Boolean = false,
        /** The owner's choices beyond the two lines (see PosterFields). */
        val fields: PosterFields = PosterFields(),
        /** His own photo of the stock, for New stock. */
        val photo: Bitmap? = null
    )

    /** Sizes the big line and message ended at (for the on-device test), and what the preview animates. */
    data class Fit(
        val titleSize: Float,
        val titleLines: Int,
        val messageSize: Float,
        val messageLines: Int,
        val motion: PosterMotion.Spec = PosterMotion.Spec()
    )

    /** Draw the poster. Returns the bitmap (caller recycles) and how the text fitted. */
    fun draw(context: Context, input: Input): Pair<Bitmap, Fit> {
        val rtl = PdfRtl.isRtl(context)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val page = PosterKit.Page(
            context, Canvas(bmp), locale, rtl, input.title, input.message,
            input.shopName.ifBlank { " " }, input.phone, input.address, input.logo, input.mark,
            input.hero, input.heroOverlay, input.fields, input.photo
        )
        val o = PosterDesigns.draw(input.occasion, page)
        return bmp to Fit(o.titleSize, o.titleLines, o.messageSize, o.messageLines, o.motion)
    }
}
