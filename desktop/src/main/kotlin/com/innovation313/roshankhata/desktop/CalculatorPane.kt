package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.innovation313.roshankhata.screens.calcKeys
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * The calculator on the computer (KMP Phase 7p): the phone's pad, and the
 * keyboard too. The same evaluator as every amount box (Calc.evalPad:
 * percent, Urdu and Persian digits), the result shown live as the shop's
 * money, Copy puts the figure on the clipboard.
 */
@Composable
fun CalculatorPane() {
    var text by remember { mutableStateOf("") }
    val result = Calc.evalPad(text)
    val keys = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("00", "0", ".", "+"),
        listOf("%", "C", "⌫", "=")
    )
    // Laid out for the window it is in (25 Oct 2026, the audit: on a laptop the bottom row — % C ⌫ = —
    // and Copy were cut off, with 400 dp empty beside the pad). Wide: the figure on the start side, the
    // pad beside it; narrow: one column that scrolls.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val keyHeight = if (wide) 54.dp else 48.dp
        val press = { k: String ->
            text = when (k) {
                "C" -> ""
                "⌫" -> text.dropLast(1)
                "=" -> result?.let { Calc.trim(it) } ?: text
                else -> text + k
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PaneTitle(word(W.calculator), null)
            if (wide) {
                Row(Modifier.widthIn(max = 900.dp).card().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CalcDisplay(text, { text = it }, result, Modifier.weight(1f))
                    CalcPad(keys, keyHeight, press, Modifier.width(380.dp))
                }
            } else {
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalcDisplay(text, { text = it }, result, Modifier.fillMaxWidth())
                    CalcPad(keys, keyHeight, press, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The sum being typed, its answer in the shop's money, and Copy. */
@Composable
private fun CalcDisplay(text: String, onText: (String) -> Unit, result: Double?, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Enter or "=" from the keyboard works it out, as the pad's own "=" does (21 Oct 2026).
        Outlined(text, onText, singleLine = true,
            modifier = Modifier.fillMaxWidth().calcKeys({ text }, com.innovation313.roshankhata.screens.CalcKind.MONEY) { onText(it) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, textAlign = TextAlign.End))
        Text(result?.let { Format.signedIfNegative(it) } ?: "", Modifier.fillMaxWidth(), color = c.ink, fontSize = 28.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        TextButton(enabled = result != null, onClick = {
            result?.let { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(Calc.trim(it)), null) }
        }) { Text(word(W.copy_details), color = c.brandGreenText) }
    }
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
                    Box(
                        Modifier.weight(1f).height(keyHeight)
                            .background(if (k == "=") c.brandGreen else if (op) c.pageBg else c.pageBg.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .press { onKey(k) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(k, color = if (k == "=") androidx.compose.ui.graphics.Color.White else c.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
