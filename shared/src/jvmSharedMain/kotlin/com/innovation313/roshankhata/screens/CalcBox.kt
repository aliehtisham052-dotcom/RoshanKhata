package com.innovation313.roshankhata.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The calculator in a typed box (21 Oct 2026, the owner: "where there is a
 * box for money there should be a calculator — on the phone and on the
 * computer"). A box in calc mode:
 *
 * - shows the running total under itself while it holds a sum ("= Rs 800");
 * - works the sum out on Enter or "=", and when the cursor leaves it, so what
 *   is saved is the figure the owner saw;
 * - (in [CalcField]) has a pad button for the phone, whose number keyboard
 *   has no + or ×.
 *
 * A lone "10%" is left as typed: in a discount box it is a share of the line,
 * in a tax box it is the rate, and the box's own reader knows which.
 */
enum class CalcKind { MONEY, QUANTITY, PERCENT }

object CalcText {

    private val OPERATOR = Regex("[+*/%]")

    /** True when [text] is a sum to work out, not a plain figure (a lone "10%" is not). */
    fun isSum(text: String): Boolean {
        val n = Calc.normalize(text)
        if (n.isEmpty()) return false
        val core = n.removeSuffix("%")
        if (core.isNotEmpty() && core.toDoubleOrNull() != null) return false
        return OPERATOR.containsMatchIn(core) || core.indexOf('-', startIndex = 1) > 0
    }

    private fun value(text: String, kind: CalcKind): Double? =
        if (kind == CalcKind.PERCENT) Calc.evalPercent(text) else Calc.evalPad(text)

    private fun rounded(v: Double, kind: CalcKind): Double =
        if (kind == CalcKind.QUANTITY) Math.round(v * 1000) / 1000.0 else Math.round(v * 100) / 100.0

    /** The line under the box while it holds a sum: "= Rs 800", "= 12.5", "= 17%"; null otherwise. */
    fun line(text: String, kind: CalcKind): String? {
        if (!isSum(text)) return null
        val v = value(text, kind) ?: return null
        val r = rounded(v, kind)
        return "= " + when (kind) {
            CalcKind.MONEY -> Format.signedIfNegative(r)
            CalcKind.QUANTITY -> Calc.trim(r)
            CalcKind.PERCENT -> Calc.trim(r) + "%"
        }
    }

    /** What the box should hold once the sum is worked out; null to leave it as typed. */
    fun settled(text: String, kind: CalcKind): String? {
        if (!isSum(text)) return null
        val v = value(text, kind) ?: return null
        return Calc.trim(rounded(v, kind))
    }
}

/**
 * Enter or "=" works the sum out, and so does leaving the box. Enter is only
 * taken when there was a sum to work out, so a second Enter still does what
 * the form does with it (saves, moves on).
 */
fun Modifier.calcKeys(value: () -> String, kind: CalcKind, onValueChange: (String) -> Unit): Modifier =
    this
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val equals = (e.key == Key.Equals && !e.isShiftPressed) || e.key == Key.NumPadEquals
            val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!equals && !enter) return@onPreviewKeyEvent false
            val done = CalcText.settled(value(), kind)
            if (done != null) onValueChange(done)
            // "=" never lands in the box; Enter passes on when there was nothing to work out.
            equals || done != null
        }
        // A box not being typed in holds a figure, not a sum (settled is nothing for a plain figure).
        .onFocusChanged { f -> if (!f.isFocused) CalcText.settled(value(), kind)?.let(onValueChange) }

/**
 * The shared screens' number box in calc mode: Material's outlined box, the
 * running total under it, Enter / "=" / leaving works it out, and a pad.
 */
@Composable
fun CalcField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    kind: CalcKind = CalcKind.MONEY,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true
) {
    val c = LocalRoshanColors.current
    var pad by remember { mutableStateOf(false) }
    val line = CalcText.line(value, kind)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.calcKeys({ value }, kind, onValueChange),
        // One line ending in "…", never cut mid-word (30 Oct 2026, the phone audit).
        label = label?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (kind == CalcKind.MONEY) KeyboardType.Decimal else KeyboardType.Number),
        supportingText = line?.let { { Text(it, color = c.brandGreenText, fontWeight = FontWeight.SemiBold, maxLines = 1) } },
        trailingIcon = if (!enabled) null else {
            {
                // Small, so three boxes still fit a row on a phone (qty, rate, discount).
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable { pad = true }.testTag("calcPad"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+−", color = c.brandGreenText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    )
    if (pad) CalcPadDialog(value, kind, onDone = { onValueChange(it); pad = false }, onDismiss = { pad = false })
}

/**
 * The pad, as a dialog over any screen: digits, the four operators, %, ".",
 * clear and back, and the total as it is typed. Done puts the worked-out
 * figure in the box (or the text as typed when it is not a sum).
 */
@Composable
fun CalcPadDialog(start: String, kind: CalcKind, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoshanColors.current
    var text by remember { mutableStateOf(start) }
    val keys = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "%", "+")
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            // As wide as the screen allows up to 340 dp (a fixed 300 pressed the edges of a 320 dp phone or a split screen).
            Modifier.fillMaxWidth().widthIn(max = 340.dp).background(c.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text.ifEmpty { "0" }, color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.fillMaxWidth()
            )
            Text(
                CalcText.line(text, kind) ?: " ", color = c.brandGreenText, fontSize = 15.sp,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.fillMaxWidth()
            )
            for (row in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in row) PadKey(k, Modifier.weight(1f)) { text += k }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadKey("C", Modifier.weight(1f)) { text = "" }
                PadKey("⌫", Modifier.weight(1f)) { text = text.dropLast(1) }
                PadKey("=", Modifier.weight(2f), strong = true) { CalcText.settled(text, kind)?.let { text = it } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text(word(W.cancel)) }
                TextButton({ onDone(CalcText.settled(text, kind) ?: text) }) { Text(word(W.ok), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier, strong: Boolean = false, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Box(
        modifier.height(48.dp)
            .background(if (strong) c.brandGreen else c.pageBg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (strong) c.onDark else c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
