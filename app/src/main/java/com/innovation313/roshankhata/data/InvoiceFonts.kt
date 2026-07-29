package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.innovation313.roshankhata.R

/**
 * The four fonts the finalised invoice-template mockups actually specify
 * (see the invoice-templates spec), bundled once here rather than left as
 * Android's generic sans/serif/monospace — Sora, Manrope and Playfair
 * Display all under the SIL Open Font License (free to bundle in any app),
 * matching Google's own distribution of them.
 *
 * Each is loaded once per process and cached — [ResourcesCompat.getFont]
 * already caches internally, but a local cache avoids even that lookup on
 * every line of every invoice being drawn.
 *
 * Sora, Manrope and Playfair Display are variable-weight font files (a
 * single file covering the whole weight range) rather than one file per
 * weight — Android renders a variable font at its default instance weight
 * when loaded plainly like this, and [bold] below asks Android to
 * synthesise a bold from that rather than needing a second bundled file
 * per family. IBM Plex Mono is the one exception: a true bold file exists
 * and is used directly, since synthetic bold on a monospace face used for
 * numbers and money is more noticeably rougher than on the others.
 */
object InvoiceFonts {

    private var sora: Typeface? = null
    private var manrope: Typeface? = null
    private var playfairDisplay: Typeface? = null
    private var ibmPlexMono: Typeface? = null
    private var ibmPlexMonoBold: Typeface? = null

    fun sora(context: Context): Typeface =
        sora ?: (ResourcesCompat.getFont(context, R.font.sora_variable) ?: Typeface.DEFAULT)
            .also { sora = it }

    fun manrope(context: Context): Typeface =
        manrope ?: (ResourcesCompat.getFont(context, R.font.manrope_variable) ?: Typeface.DEFAULT)
            .also { manrope = it }

    fun playfairDisplay(context: Context): Typeface =
        playfairDisplay ?: (ResourcesCompat.getFont(context, R.font.playfair_display_variable) ?: Typeface.SERIF)
            .also { playfairDisplay = it }

    fun ibmPlexMono(context: Context): Typeface =
        ibmPlexMono ?: (ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular) ?: Typeface.MONOSPACE)
            .also { ibmPlexMono = it }

    fun ibmPlexMonoBold(context: Context): Typeface =
        ibmPlexMonoBold ?: (ResourcesCompat.getFont(context, R.font.ibm_plex_mono_bold) ?: Typeface.MONOSPACE)
            .also { ibmPlexMonoBold = it }

    /** A bold variant of any of the above, synthesised — see the class doc for why this is fine except for IBM Plex Mono, which has its own true bold. */
    fun bold(typeface: Typeface): Typeface = Typeface.create(typeface, Typeface.BOLD)
}
