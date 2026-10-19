package com.innovation313.roshankhata.desktop.invoice

import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Typeface
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/*
 * What the phone's printed designs ask an Android Context for, on the
 * computer (Phase 10): words in the app's language, the shop's profile, its
 * four pictures (logo, signature, stamp, payment QR), and whether the page
 * is right to left. The phone's drawing code is copied with its imports
 * pointed here and at ..gfx; its calls keep their shape.
 */

/** The words, the shop and its pictures, for one printing. */
class Context(val words: Words, val settings: ShopSettings, val images: ProfileImages, val rtl: Boolean) {
    fun getString(name: String, vararg args: Any): String = words.text(name, *args)
}

/** `R.string.x` in the copied code names the same string as `W.x`. */
object R {
    val string = W
}

/**
 * The shop's four pictures on the computer, one folder per business. The
 * text backup leaves pictures out by the owner's own instruction, so on the
 * computer they are chosen again, on the Business Profile page.
 */
class ProfileImages(val dir: File) {
    enum class Kind(val file: String) { LOGO("logo.png"), SIGNATURE("signature.png"), STAMP("stamp.png"), QR("payment_qr.png") }

    fun file(kind: Kind) = File(dir, kind.file)
    fun has(kind: Kind) = file(kind).isFile

    fun load(kind: Kind): Bitmap? = runCatching { ImageIO.read(file(kind))?.let(::Bitmap) }.getOrNull()

    /**
     * Copies [source] in as [kind]: read, kept to [MAX] pixels on its long
     * side (a 12-megapixel phone photo of a stamp is not needed on an
     * invoice), and written as PNG. False when the file is not a picture.
     */
    fun set(kind: Kind, source: File): Boolean {
        val img = runCatching { ImageIO.read(source) }.getOrNull() ?: return false
        val scale = minOf(1.0, MAX.toDouble() / maxOf(img.width, img.height))
        val w = (img.width * scale).toInt().coerceAtLeast(1)
        val h = (img.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        dir.mkdirs()
        val tmp = File(dir, kind.file + ".tmp")
        if (!ImageIO.write(out, "png", tmp)) return false
        file(kind).delete()
        return tmp.renameTo(file(kind))
    }

    fun remove(kind: Kind) { file(kind).delete() }

    companion object {
        const val MAX = 1024
        fun forBusiness(home: File, businessId: Long) = ProfileImages(File(home, "profile/b$businessId"))
    }
}

/** The phone's BusinessProfile calls, answered from the computer's shop settings and pictures. */
object BusinessProfile {
    fun businessName(c: Context): String? = c.settings.businessName()?.takeIf { it.isNotBlank() }
    fun businessAddress(c: Context): String? = c.settings.businessAddress()?.takeIf { it.isNotBlank() }
    fun businessPhone(c: Context): String? = c.settings.businessPhone()?.takeIf { it.isNotBlank() }
    fun strn(c: Context): String? = c.settings.strn()?.takeIf { it.isNotBlank() }
    fun taxLabel(c: Context): String =
        if (c.settings.currency() == Currency.DEFAULT) "STRN" else c.getString(W.tax_number_label)
    fun bankName(c: Context): String? = c.settings.bankName()?.takeIf { it.isNotBlank() }
    fun bankAccountTitle(c: Context): String? = c.settings.bankAccountTitle()?.takeIf { it.isNotBlank() }
    fun bankIban(c: Context): String? = c.settings.bankIban()?.takeIf { it.isNotBlank() }
    fun bankJazzCash(c: Context): String? = c.settings.bankJazzCash()?.takeIf { it.isNotBlank() }
    fun termsAndConditions(c: Context): String? = c.settings.termsAndConditions()?.takeIf { it.isNotBlank() }
    fun hasQr(c: Context): Boolean = c.images.has(ProfileImages.Kind.QR)
    fun loadQr(c: Context): Bitmap? = c.images.load(ProfileImages.Kind.QR)
    fun loadSignature(c: Context): Bitmap? = c.images.load(ProfileImages.Kind.SIGNATURE)
    fun loadStamp(c: Context): Bitmap? = c.images.load(ProfileImages.Kind.STAMP)
    fun loadLogo(c: Context): Bitmap? = c.images.load(ProfileImages.Kind.LOGO)
}

/** The phone's invoice fonts (SIL OFL), copied from app/src/main/res/font at build time. */
object InvoiceFonts {
    private val cache = HashMap<String, Typeface>()

    private fun load(name: String, fallback: Typeface): Typeface = synchronized(cache) {
        cache.getOrPut(name) {
            runCatching {
                InvoiceFonts::class.java.getResourceAsStream("/fonts/$name.ttf")!!.use { Typeface.of(Font.createFont(Font.TRUETYPE_FONT, it)) }
            }.getOrDefault(fallback)
        }
    }

    fun sora(c: Context) = load("sora_variable", Typeface.DEFAULT)
    fun manrope(c: Context) = load("manrope_regular", Typeface.DEFAULT)
    fun manropeBold(c: Context) = load("manrope_bold", Typeface.DEFAULT_BOLD)
    fun playfairDisplay(c: Context) = load("playfair_display_variable", Typeface.SERIF)
    fun ibmPlexMono(c: Context) = load("ibm_plex_mono_regular", Typeface.MONOSPACE)
    fun ibmPlexMonoBold(c: Context) = load("ibm_plex_mono_bold", Typeface.MONOSPACE)
    fun nastaliq(c: Context) = load("noto_nastaliq_urdu", Typeface.DEFAULT)
    fun arefRuqaa(c: Context) = load("aref_ruqaa_bold", Typeface.DEFAULT_BOLD)

    /** True when every bundled face loaded (a test holds the build to it). */
    fun allPresent(): Boolean = listOf(
        "sora_variable", "manrope_regular", "manrope_bold", "playfair_display_variable",
        "ibm_plex_mono_regular", "ibm_plex_mono_bold"
    ).all { InvoiceFonts::class.java.getResource("/fonts/$it.ttf") != null }
}
