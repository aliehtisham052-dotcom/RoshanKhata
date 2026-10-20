package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format

/*
 * Fluent controls (1.10.0): the Figma mockups of 20 Oct drawn as Windows 11
 * draws its own — 32 dp controls with 4 dp corners, cards with 8 dp corners and
 * a 1 px edge, a label above a text box, one accent button per page. Every
 * pane reaches for these instead of Material's taller defaults.
 */

/** A button's kind: the one filled accent button, the standard outlined one, or a quiet one with no edge. */
enum class ButtonKind { ACCENT, STANDARD, SUBTLE, DANGER }

/**
 * A Fluent button: 32 tall, 4 dp corners, bold 14 sp words, an optional glyph
 * ([icon], one of the phone's white glyphs) before them.
 */
@Composable
fun FluentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.STANDARD,
    icon: String? = null,
    enabled: Boolean = true,
    tall: Boolean = false
) {
    val c = LocalRoshanColors.current
    val fill = when (kind) {
        ButtonKind.ACCENT -> Look.accent
        ButtonKind.STANDARD -> Look.control
        ButtonKind.SUBTLE -> Color.Transparent
        ButtonKind.DANGER -> Look.getTint
    }
    val words = when (kind) {
        ButtonKind.ACCENT -> Look.onAccent
        ButtonKind.DANGER -> c.owedToMe
        else -> c.ink
    }
    val wash = if (kind == ButtonKind.ACCENT) Color.Black.copy(alpha = 0.14f) else Look.hover
    Row(
        modifier.heightIn(min = if (tall) 40.dp else 32.dp)
            .clip(Look.ctl)
            .background(eased(if (enabled) fill else Look.subtle))
            .then(if (kind == ButtonKind.STANDARD) Modifier.border(1.dp, Look.edge, Look.ctl) else Modifier)
            .press(Look.ctl, wash = wash, enabled = enabled, onClick = onClick)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Glyph(icon, if (enabled) words else c.textMuted, 16)
        Text(text, color = if (enabled) words else c.textMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** A square 32 dp button holding one glyph; [tip] is its words for the hover tip. */
@Composable
fun IconAction(icon: String, tip: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null, filled: Boolean = false) {
    val c = LocalRoshanColors.current
    HoverTip(tip) {
        Box(
            modifier.size(32.dp).clip(Look.ctl)
                .background(if (filled) Look.accentTint else Look.control)
                .border(1.dp, if (filled) Look.accent else Look.edge, Look.ctl)
                .press(Look.ctl, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Glyph(icon, tint ?: if (filled) Look.accent else c.inkSoft, 18) }
    }
}

/** A small round count on a button's corner (the bell's alerts). */
@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    if (count <= 0) return
    Text(
        if (count > 99) "99+" else count.toString(),
        modifier.background(c.gaveFill, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
    )
}

/** A choice pill: 28 tall, round, filled with the accent when [on]. */
@Composable
fun FluentPill(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Text(
        text,
        modifier.clip(CircleShape).background(eased(if (on) Look.pickedFill else Look.subtle))
            .press(CircleShape, wash = if (on) Color.Black.copy(alpha = 0.12f) else Look.hover, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (on) Look.onPicked else c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
    )
}

/** A card: the layer colour, 8 dp corners, a 1 px edge, a whisper of shadow. */
@Composable
fun FluentCard(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable () -> Unit) {
    Box(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card).padding(padding.dp)) { content() }
}

/** A card's title row: bold 16 sp, with anything on the end side. */
@Composable
fun CardTitle(text: String, end: (@Composable () -> Unit)? = null) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        end?.invoke()
    }
}

/**
 * A page's head, as every Figma screen: the title at 28 sp with its line under
 * it, then the page's own actions on the end side (the last one the accent).
 */
@Composable
fun PageHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val c = LocalRoshanColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = c.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

/**
 * A Fluent text box: a 12 sp label above, a 32 dp box with 4 dp corners, the
 * accent line along its foot while it has the focus, and a help line under it.
 */
@Composable
fun FluentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    help: String? = null,
    prefix: String? = null,
    leadingIcon: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    big: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true
) {
    val c = LocalRoshanColors.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        label?.let { Text(it, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        Column(
            Modifier.fillMaxWidth().clip(Look.ctl).background(if (enabled) Look.control else Look.subtle)
                .border(1.dp, eased(if (focused) Look.accent else Look.edge), Look.ctl)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (big) 48.dp else 32.dp).padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                leadingIcon?.let { Glyph(it, c.inkSoft, 16) }
                prefix?.let { Text(it, color = c.inkSoft, fontSize = if (big) 16.sp else 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = c.textMuted, fontSize = if (big) 20.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = c.ink, fontSize = if (big) 22.sp else 14.sp, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal),
                        cursorBrush = SolidColor(Look.accent),
                        singleLine = singleLine,
                        interactionSource = source,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        enabled = enabled
                    )
                }
                trailing?.invoke()
            }
            // Fluent's focus line: 2 dp of accent along the foot of the box.
            Box(Modifier.fillMaxWidth().height(2.dp).background(eased(if (focused) Look.accent else Color.Transparent)))
        }
        help?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
    }
}

/** The initials of a name on a tinted disc, [size] across; [index] picks one of four tints. */
@Composable
fun Avatar(name: String, size: Int = 36, index: Int = 0) {
    val c = LocalRoshanColors.current
    val tints = listOf(
        Look.accentTint to Look.accent,
        Look.giveTint to Look.give,
        Look.getTint to c.owedToMe,
        Look.warnTint to Look.warn
    )
    val (fill, ink) = tints[((index % tints.size) + tints.size) % tints.size]
    Box(Modifier.size(size.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        Text(Format.initials(name), color = ink, fontSize = (size * 0.36).sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The Home's figure cards (the "colourful" mockup): a soft gradient of the
 * figure's tint into the card, the glyph on a solid chip, the figure at 26 sp
 * in its colour, and a line of context under it.
 */
@Composable
fun StatCard(label: String, amount: String, under: String?, color: Color, tint: Color, icon: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalRoshanColors.current
    Column(
        modifier.clip(Look.card)
            .background(Brush.linearGradient(listOf(tint, Look.layer)))
            .border(1.dp, color.copy(alpha = 0.25f), Look.card)
            .then(if (onClick != null) Modifier.press(Look.card, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).background(color, Look.ctl), contentAlignment = Alignment.Center) { Glyph(icon, Look.layer, 16) }
            Text(label, Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amount, color = color, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        under?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A table's header row: 12 sp bold labels on the subtle fill. [columns]: each label with its width in dp (0 fills). */
@Composable
fun TableHead(columns: List<Pair<String, Int>>, endAligned: Set<Int> = emptySet()) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().background(Look.subtle).padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        columns.forEachIndexed { i, (label, w) ->
            Text(
                label, if (w > 0) Modifier.width(w.dp) else Modifier.weight(1f),
                color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                textAlign = if (i in endAligned) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start
            )
        }
    }
}

/** The quiet line between rows and under a card's title. */
@Composable
fun Rule() { HorizontalDivider(color = Look.line) }

/** A tinted chip with a glyph in its colour (an alert's kind, a quick action). */
@Composable
fun GlyphChip(icon: String, color: Color, tint: Color, size: Int = 32) {
    Box(Modifier.size(size.dp).background(tint, Look.ctl), contentAlignment = Alignment.Center) { Glyph(icon, color, (size / 2)) }
}

/** The phone's tile picture painted in one colour, [size] across (the rail's and the chips' glyphs). */
@Composable
fun MonoTile(icon: String, tint: Color, size: Int) {
    tileIcon(icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint)) }
}

/** The words of an icon-only control as its hover tip. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HoverTip(text: String, content: @Composable () -> Unit) {
    val c = LocalRoshanColors.current
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(
                text,
                Modifier.background(c.ink, Look.ctl).padding(horizontal = 10.dp, vertical = 6.dp),
                color = c.surface, fontSize = 12.sp
            )
        },
        delayMillis = 400
    ) { content() }
}

/** A key's name in a small outlined box ("Ctrl K"). */
@Composable
fun KeyCap(text: String) {
    val c = LocalRoshanColors.current
    Text(text, Modifier.border(1.dp, Look.edge, Look.ctl).padding(horizontal = 5.dp, vertical = 1.dp), color = c.textMuted, fontSize = 11.sp, maxLines = 1, softWrap = false)
}

/** Space between a header's actions. */
@Composable
fun Gap(w: Int = 8) { Spacer(Modifier.width(w.dp)) }
