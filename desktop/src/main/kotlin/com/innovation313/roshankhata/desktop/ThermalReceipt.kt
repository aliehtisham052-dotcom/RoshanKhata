package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.EscPos
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.text.AttributedString
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

/**
 * A receipt on the shop's thermal printer (Phase 9e), the phone's way: the
 * receipt is drawn as a picture 58 mm wide and sent as ESC/POS raster bytes
 * (the shared EscPos, unit-tested), so Urdu, Arabic, Hindi and Bengali come
 * out as they read on screen — the printer's own font is never asked to
 * shape a script it does not know.
 *
 * On Windows the printer is the one installed with its driver (USB or
 * Bluetooth "POS-58", "XP-58" and the like); the bytes go through the
 * spooler unchanged (raw), which is what these printers expect.
 */
object ThermalReceipt {

    /** One line of the receipt: text, how big, bold, and whether it is the figure (centred, large). */
    class Line(val text: String, val size: Float = 22f, val bold: Boolean = false, val centre: Boolean = false)

    /**
     * A receipt on its way to the printer (21 Oct 2026): its lines, and
     * whether the cash drawer opens with it — a counter sale paid at least
     * partly in cash, when the drawer setting is on.
     */
    class Job(val lines: List<Line>, val cash: Boolean = false)

    /**
     * This computer's receipt printer (21 Oct 2026, kept in app.properties —
     * the printer belongs to the computer, not to the book): an 80 mm roll
     * instead of 58 mm, the shop's logo at the top, and the cash drawer wired
     * to the printer opened on a cash sale.
     */
    data class Settings(val wide: Boolean = false, val logo: Boolean = false, val drawer: Boolean = false) {
        val width: Int get() = if (wide) EscPos.WIDTH_80MM else EscPos.WIDTH_58MM
    }

    private const val MARGIN = 8

    /** The logo's box at the top of the roll: at most this tall, and 60% of the roll across. */
    private const val LOGO_HEIGHT = 120

    /**
     * The receipt as black on white, [width] dots across (58 mm by default),
     * as tall as it needs; the shop's [logo], when given, centred at the top
     * and dithered ([EscPos.dither]) so a coloured logo prints as dots.
     */
    fun image(lines: List<Line>, rtl: Boolean, width: Int = EscPos.WIDTH_58MM, logo: BufferedImage? = null): BufferedImage {
        val frc = FontRenderContext(null, true, true)
        val wrap = width - 2 * MARGIN
        // Lay out first (wrapping long names and notes), then draw on a canvas of the right height.
        val laid = lines.flatMap { line ->
            if (line.text.isEmpty()) return@flatMap listOf<Pair<TextLayout?, Line>>(null to line)
            val font = Font(PrintFonts.family, if (line.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(line.size)
            val text = AttributedString(com.innovation313.roshankhata.data.Digits.forPrint(line.text)).apply {
                addAttribute(TextAttribute.FONT, font)
                addAttribute(TextAttribute.RUN_DIRECTION, if (rtl) TextAttribute.RUN_DIRECTION_RTL else TextAttribute.RUN_DIRECTION_LTR)
            }
            val measurer = LineBreakMeasurer(text.iterator, frc)
            buildList<Pair<TextLayout?, Line>> { while (measurer.position < line.text.length) add(measurer.nextLayout(wrap.toFloat()) to line) }
        }
        val mark = logo?.let { printable(it, (width * 0.6).toInt(), LOGO_HEIGHT) }
        val top = mark?.let { it.height + 10 } ?: 0
        val height = MARGIN * 2 + top + laid.sumOf { (layout, line) -> lineHeight(layout, line) } + 8
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        mark?.let { g.drawImage(it, (width - it.width) / 2, MARGIN, null) }
        g.color = Color.BLACK
        var y = (MARGIN + top).toFloat()
        laid.forEach { (layout, line) ->
            if (layout == null) {
                // An empty line is a rule across the roll.
                g.fillRect(MARGIN, (y + 6).toInt(), wrap, 2)
            } else {
                y += layout.ascent
                val w = layout.advance
                val x = when {
                    line.centre -> (width - w) / 2f
                    rtl -> width - MARGIN - w
                    else -> MARGIN.toFloat()
                }
                layout.draw(g, x, y)
                y += layout.descent + layout.leading
                return@forEach
            }
            y += lineHeight(null, line)
        }
        g.dispose()
        return img
    }

    private fun lineHeight(layout: TextLayout?, line: Line): Int =
        if (layout == null) 14 else kotlin.math.ceil(layout.ascent + layout.descent + layout.leading).toInt()

    /** The logo scaled into [maxW] x [maxH] (never enlarged), on paper, then dithered to dots. */
    fun printable(logo: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val scale = minOf(1.0, maxW.toDouble() / logo.width, maxH.toDouble() / logo.height)
        val w = (logo.width * scale).toInt().coerceAtLeast(1)
        val h = (logo.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(logo, 0, 0, w, h, null)
        g.dispose()
        val px = IntArray(w * h)
        out.getRGB(0, 0, w, h, px, 0, w)
        out.setRGB(0, 0, w, h, EscPos.dither(px, w, h), 0, w)
        return out
    }

    /**
     * The printer's bytes: the picture at the roll's width ([settings]), the
     * logo on top when it is on and the shop has one, and the drawer's pulse
     * first when [cash] and the drawer setting is on.
     */
    fun bytes(lines: List<Line>, rtl: Boolean, settings: Settings = Settings(), logo: BufferedImage? = null, cash: Boolean = false): ByteArray {
        val img = image(lines, rtl, settings.width, logo.takeIf { settings.logo })
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return EscPos.withDrawer(EscPos.raster(px, img.width, img.height), cash && settings.drawer)
    }

    /** The drawer opened with no receipt (a cash sale rung up without printing): its pulse alone. */
    fun openDrawer(printer: String): Boolean = send(printer, EscPos.DRAWER_KICK)

    fun printers(): List<String> = PrintServiceLookup.lookupPrintServices(null, null).map { it.name }

    /** The bytes to the named printer, raw. False when it is not there or refused them. */
    fun send(printer: String, data: ByteArray): Boolean = runCatching {
        val service = PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == printer } ?: return false
        service.createPrintJob().print(SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null)
        true
    }.getOrDefault(false)

    // A small file beside the books, not java.util.prefs: the MSI's trimmed Java runtime
    // carries only the modules listed in desktop/build.gradle.kts, and java.prefs is not one.
    private val lastFile get() = java.io.File(com.innovation313.roshankhata.data.DesktopDatabase.home(), "receipt_printer.txt")
    var lastPrinter: String?
        get() = runCatching { lastFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()?.ifEmpty { null } }.getOrNull()
        set(value) { runCatching { if (value == null) lastFile.delete() else lastFile.writeText(value, Charsets.UTF_8) } }
}

/** The printers Windows knows, the one used last on top. */
@Composable
fun PrinterChoiceDialog(names: List<String>, onCancel: () -> Unit, onSavePicture: (() -> Unit)? = null, onPick: (String) -> Unit) {
    val c = LocalRoshanColors.current
    val last = ThermalReceipt.lastPrinter
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(word(W.print_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (names.isEmpty()) Text(word(W.print_failed), color = c.owedToMe, fontSize = 14.sp)
                names.sortedByDescending { it == last }.forEach { n ->
                    TextButton({ onPick(n) }) { Text(n, color = if (n == last) c.brandGreenText else c.ink, fontSize = 15.sp) }
                }
                if (onSavePicture != null) TextButton(onSavePicture) { Text(word(W.receipt_save_picture), color = c.brandGreenText, fontSize = 15.sp) }
            }
        },
        confirmButton = {},
        dismissButton = { FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD) }
    )
}
