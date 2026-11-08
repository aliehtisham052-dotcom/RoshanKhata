package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * The sum on the calculator and its last three answers (the phone's calculator keeps three), kept while the
 * app is open, so leaving for the Khata and coming back finds them where they were.
 */
internal object CalcMemory {
    var text by mutableStateOf("")
    val history = mutableStateListOf<String>()

    /**
     * Puts the answer in place of the sum, so the next sum can build on it; a real sum (not "3400 = 3400")
     * goes in the history, the oldest of four dropped.
     */
    fun settle() {
        val typed = text
        val value = Calc.evalPad(typed) ?: return
        if (value.isNaN() || value.isInfinite()) return
        val answer = Calc.trim(value)
        if (typed.trim() != answer) {
            history += "$typed = ${Format.signedIfNegative(value)}"
            while (history.size > 3) history.removeAt(0)
        }
        text = answer
    }
}

/**
 * The calculator on the computer (KMP Phase 7p; Marhala 4, part 7 — the page frame and the rest of the phone's
 * CalculatorActivity): the phone's pad and the keyboard (Enter or "=" works it out), the same evaluator as every
 * amount box (Calc.evalPad: percent, Urdu and Persian digits), the answer live with its minus when it is one,
 * the last three sums, Copy, and "Add to a khata" — [onKhata] asks whose, and that customer's entry opens filled
 * in, saved only when the owner saves it (the phone's way; an amount of nothing or less goes nowhere). Wide: the
 * figure beside the pad; narrow: one column.
 */
@Composable
fun CalculatorPane(onKhata: ((Double) -> Unit)? = null) {
    val c = LocalRoshanColors.current
    val text = CalcMemory.text
    val result = Calc.evalPad(text)?.takeIf { !it.isNaN() && !it.isInfinite() }
    var refused by remember { mutableStateOf(false) }
    val keys = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("00", "0", ".", "+"),
        listOf("%", "C", "⌫", "=")
    )
    val press = { k: String ->
        when (k) {
            "C" -> CalcMemory.text = ""
            "⌫" -> CalcMemory.text = CalcMemory.text.dropLast(1)
            "=" -> CalcMemory.settle()
            else -> CalcMemory.text = CalcMemory.text + k
        }
    }
    FluentPage(word(W.calculator), null) {
        // Laid out for the window it is in (25 Oct 2026, the audit: on a laptop the bottom row — % C ⌫ = —
        // and Copy were cut off, with 400 dp empty beside the pad). Left to right in every language, as the
        // phone's display and keypad are (layoutDirection="ltr"): in Urdu the pad was mirrored (÷ 9 8 7) and a
        // typed "12500+3700*2" read "2*12500+3700".
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            val keyHeight = if (wide) 54.dp else 48.dp
            val display: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Outlined(text, { CalcMemory.text = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                            val equals = (e.key == Key.Equals && !e.isShiftPressed) || e.key == Key.NumPadEquals
                            val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                            if (e.type == KeyEventType.KeyDown && (equals || enter)) { CalcMemory.settle(); true } else false
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, textAlign = TextAlign.End))
                    Text(result?.let { Format.signedIfNegative(it) } ?: "", Modifier.fillMaxWidth(), color = c.ink, fontSize = 30.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The last three sums, oldest first, as the phone shows them.
                    CalcMemory.history.forEach { line ->
                        Text(line, Modifier.fillMaxWidth(), color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        FluentButton(word(W.copy_details), {
                            result?.let { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(Calc.trim(it)), null) }
                        }, kind = ButtonKind.STANDARD, enabled = result != null)
                        if (onKhata != null) FluentButton(word(W.calc_add_to_khata), {
                            val v = result
                            if (v != null) { if (v > 0.0) onKhata(v) else refused = true }
                        }, kind = ButtonKind.ACCENT, enabled = result != null)
                    }
                }
            }
            if (wide) {
                Row(Modifier.widthIn(max = 900.dp).card().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    display(Modifier.weight(1f))
                    CalcPad(keys, keyHeight, press, Modifier.width(380.dp))
                }
            } else {
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    display(Modifier.fillMaxWidth())
                    CalcPad(keys, keyHeight, press, Modifier.fillMaxWidth())
                }
            }
        }
        }
    }
    if (refused) TellDialog(word(W.calculator), word(W.calc_khata_positive), { refused = false })
}

/** The phone's pad: five rows of four keys, each [keyHeight] tall. */
@Composable
private fun CalcPad(keys: List<List<String>>, keyHeight: androidx.compose.ui.unit.Dp, onKey: (String) -> Unit, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val op = k in listOf("÷", "×", "−", "+", "=", "%")
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.weight(1f).height(keyHeight).clip(shape)
                            .background(if (k == "=") Look.accent else if (op) Look.subtle else Look.layerAlt, shape)
                            .press(shape) { onKey(k) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(k, color = if (k == "=") Look.onAccent else c.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
