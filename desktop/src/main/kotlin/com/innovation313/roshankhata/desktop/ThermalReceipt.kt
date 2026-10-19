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
import java.util.prefs.Preferences
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

    private const val MARGIN = 8

    /** The receipt as black on white, [EscPos.WIDTH_58MM] dots across, as tall as it needs. */
    fun image(lines: List<Line>, rtl: Boolean): BufferedImage {
        val width = EscPos.WIDTH_58MM
        val frc = FontRenderContext(null, true, true)
        val wrap = width - 2 * MARGIN
        // Lay out first (wrapping long names and notes), then draw on a canvas of the right height.
        val laid = lines.flatMap { line ->
            if (line.text.isEmpty()) return@flatMap listOf<Pair<TextLayout?, Line>>(null to line)
            val font = Font(Font.DIALOG, if (line.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(line.size)
            val text = AttributedString(line.text).apply {
                addAttribute(TextAttribute.FONT, font)
                addAttribute(TextAttribute.RUN_DIRECTION, if (rtl) TextAttribute.RUN_DIRECTION_RTL else TextAttribute.RUN_DIRECTION_LTR)
            }
            val measurer = LineBreakMeasurer(text.iterator, frc)
            buildList<Pair<TextLayout?, Line>> { while (measurer.position < line.text.length) add(measurer.nextLayout(wrap.toFloat()) to line) }
        }
        val height = MARGIN * 2 + laid.sumOf { (layout, line) -> lineHeight(layout, line) } + 8
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK
        var y = MARGIN.toFloat()
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

    fun bytes(lines: List<Line>, rtl: Boolean): ByteArray {
        val img = image(lines, rtl)
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return EscPos.raster(px, img.width, img.height)
    }

    fun printers(): List<String> = PrintServiceLookup.lookupPrintServices(null, null).map { it.name }

    /** The bytes to the named printer, raw. False when it is not there or refused them. */
    fun send(printer: String, data: ByteArray): Boolean = runCatching {
        val service = PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == printer } ?: return false
        service.createPrintJob().print(SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null)
        true
    }.getOrDefault(false)

    private val prefs: Preferences? = runCatching { Preferences.userRoot().node("roshankhata") }.getOrNull()
    var lastPrinter: String?
        get() = runCatching { prefs?.get("receipt_printer", null) }.getOrNull()
        set(value) { runCatching { if (value == null) prefs?.remove("receipt_printer") else prefs?.put("receipt_printer", value) } }
}

/** The printers Windows knows, the one used last on top. */
@Composable
fun PrinterChoiceDialog(names: List<String>, onCancel: () -> Unit, onPick: (String) -> Unit) {
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
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onCancel) { Text(word(W.cancel)) } }
    )
}
