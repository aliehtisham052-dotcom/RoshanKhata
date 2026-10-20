package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    val c = LocalRoshanColors.current
    var text by remember { mutableStateOf("") }
    val result = Calc.evalPad(text)
    val keys = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("00", "0", ".", "+"),
        listOf("%", "C", "⌫", "=")
    )
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaneTitle(word(W.calculator), null)
        Column(Modifier.width(420.dp).background(c.surface, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, textAlign = TextAlign.End))
            Text(result?.let { Format.money(it) } ?: "", Modifier.fillMaxWidth(), color = c.ink, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        val op = k in listOf("÷", "×", "−", "+", "=", "%")
                        Box(
                            Modifier.weight(1f).height(54.dp)
                                .background(if (k == "=") c.brandGreen else if (op) c.pageBg else c.pageBg.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .press {
                                    text = when (k) {
                                        "C" -> ""
                                        "⌫" -> text.dropLast(1)
                                        "=" -> result?.let { Calc.trim(it) } ?: text
                                        else -> text + k
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, color = if (k == "=") androidx.compose.ui.graphics.Color.White else c.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            TextButton(enabled = result != null, onClick = {
                result?.let { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(Calc.trim(it)), null) }
            }) { Text(word(W.copy_details), color = c.brandGreenText) }
        }
    }
}
