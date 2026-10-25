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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.innovation313.roshankhata.screens.calcKeys
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
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/*
 * Fluent controls (1.10.0): the Figma mockups of 20 Oct drawn as Windows 11
 * draws its own — 32 dp controls with 4 dp corners, cards with 8 dp corners and
 * a 1 px edge, a label above a text box, one accent button per page. Every
 * pane reaches for these instead of Material's taller defaults.
 */

/** A button's kind: the one filled accent button, the standard outlined one, or a quiet one with no edge. */
enum class ButtonKind { ACCENT, STANDARD, SUBTLE, DANGER }

/**
 * A button: 32 tall (40 [tall]), the phone's 8 dp corners, bold 14 sp words,
 * an optional glyph ([icon], one of the phone's white glyphs) before them. The
 * accent one is the phone's deep green with a gold edge.
 */
@Composable
fun FluentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.STANDARD,
    icon: String? = null,
    enabled: Boolean = true,
    tall: Boolean = false,
    /**
     * Something at the button's far end after its words (the counter's F12 key, Marhala 4): the button then
     * fills the width it is given, the words at its start and this at its end.
     */
    trailing: (@Composable () -> Unit)? = null
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
            // The phone's primary button: deep green with its gold edge (the owner's mockup, 25 Oct 2026).
            .then(
                when {
                    kind == ButtonKind.STANDARD -> Modifier.border(1.dp, Look.edge, Look.ctl)
                    kind == ButtonKind.ACCENT && enabled -> Modifier.border(1.5.dp, Look.goldEdge, Look.ctl)
                    else -> Modifier
                }
            )
            .press(Look.ctl, wash = wash, enabled = enabled, onClick = onClick)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Glyph(icon, if (enabled) words else c.textMuted, 16)
        Text(text, color = if (enabled) words else c.textMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** A square button holding one glyph, 32 dp ([size] for the header's 36); [tip] is its words for the hover tip. */
@Composable
fun IconAction(icon: String, tip: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null, filled: Boolean = false, size: Int = 32) {
    val c = LocalRoshanColors.current
    HoverTip(tip) {
        Box(
            modifier.size(size.dp).clip(Look.ctl)
                .background(if (filled) Look.accentTint else Look.control)
                .border(1.dp, if (filled) Look.accent else Look.edge, Look.ctl)
                .press(Look.ctl, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Glyph(icon, tint ?: if (filled) Look.accent else c.inkSoft, if (size >= 36) 19 else 18) }
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
fun FluentPill(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
    // Inside the window its top row draws the head (Marhala 2): written there once composed, nothing here.
    val head = LocalPageHead.current
    if (head != null) {
        androidx.compose.runtime.SideEffect { head.title = title; head.subtitle = subtitle; head.actions = actions }
        return
    }
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
    enabled: Boolean = true,
    /** Shown as dots (a PIN). */
    password: Boolean = false,
    /**
     * Calc mode (21 Oct 2026): the running total under the box while it holds
     * a sum, worked out on Enter / "=" and when the cursor leaves.
     */
    calc: com.innovation313.roshankhata.screens.CalcKind? = null,
    /**
     * Left to right whatever the page's direction (a phone number, R15 23 Oct
     * 2026): digits alone take the paragraph's direction, so in an RTL window
     * "0300 1234567" read "1234567 0300" while it was typed.
     */
    ltr: Boolean = false
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
                        modifier = Modifier.fillMaxWidth().then(
                            if (calc != null) Modifier.calcKeys({ value }, calc, onValueChange) else Modifier
                        ),
                        textStyle = TextStyle(
                            color = c.ink, fontSize = if (big) 22.sp else 14.sp, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal,
                            textDirection = if (ltr) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Unspecified
                        ),
                        cursorBrush = SolidColor(Look.accent),
                        singleLine = singleLine,
                        interactionSource = source,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        enabled = enabled,
                        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
                            else androidx.compose.ui.text.input.VisualTransformation.None
                    )
                }
                trailing?.invoke()
            }
            // Fluent's focus line: 2 dp of accent along the foot of the box.
            Box(Modifier.fillMaxWidth().height(2.dp).background(eased(if (focused) Look.accent else Color.Transparent)))
        }
        // While the box holds a sum, its total takes the help line's place (a box whose help is
        // already its figure would otherwise say it twice).
        val sumLine = calc?.let { com.innovation313.roshankhata.screens.CalcText.line(value, it) }
        if (sumLine != null) Text(sumLine, color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        else help?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
    }
}

/** The initials of a name on a tinted disc, [size] across; [index] picks one of four tints. */
@Composable
fun Avatar(name: String, size: Int = 36, index: Int = 0, rounded: Boolean = false, id: Long? = null) {
    val c = LocalRoshanColors.current
    val tints = listOf(
        Look.accentTint to Look.accent,
        Look.giveTint to Look.give,
        Look.getTint to c.owedToMe,
        Look.warnTint to Look.warn
    )
    // [id]: the party's own colours, the Khata list's (Marhala 4, the audit: by the row's place they moved as a
    // list was sorted or filtered); [index] only where there is no party.
    val (fill, ink) = if (id != null) Look.avatar(id) else tints[((index % tints.size) + tints.size) % tints.size]
    // [rounded]: the phone's soft square (the owner's mockup of 25 Oct 2026) instead of a circle.
    Box(Modifier.size(size.dp).background(fill, if (rounded) RoundedCornerShape((size * 0.3f).dp) else CircleShape), contentAlignment = Alignment.Center) {
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

/** A table's header row whose columns share the width by weight, for rows laid out with Modifier.weight. */
@Composable
fun WeightedHead(columns: List<Pair<String, Float>>, endAligned: Set<Int> = emptySet()) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().background(Look.subtle).padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        columns.forEachIndexed { i, (label, w) ->
            Text(
                label, Modifier.weight(w), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    val flip = iconFlipped(icon)
    tileIcon(icon)?.let {
        Image(it, contentDescription = null, modifier = Modifier.size(size.dp).then(if (flip) Modifier.scale(-1f, 1f) else Modifier), colorFilter = ColorFilter.tint(tint))
    }
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

// ---- Page and dialog frames (Phase 2, 20 Oct): every pane on the same rhythm as the Figma screens ----

/**
 * A page as the Figma screens draw one: 24 dp from the sides, the head (title,
 * its line, the actions on the end side), then the content 16 dp apart. [scroll]
 * false when the content holds its own list (LazyColumn) and must fill the height.
 */
@Composable
fun FluentPage(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxSize()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(title, subtitle, actions = actions)
        content()
    }
}

/** A card holding rows (a list, a table): no padding of its own, clipped, the rows draw their own dividers. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card), content = content)
}

/** A card's head row inside a [ListCard]: the title, a count or note on the end side, the line under it. */
@Composable
fun ListHead(title: String, end: String? = null, endContent: (@Composable () -> Unit)? = null) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        end?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false) }
        endContent?.invoke()
    }
    Rule()
}

/**
 * An empty page's middle (the Figma empty state): the section's tile on a soft
 * disc, a 20 sp line, two lines of why, and the one action that fills it.
 */
@Composable
fun EmptyState(icon: String, title: String, text: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalRoshanColors.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(88.dp).background(Look.accentTint, CircleShape), contentAlignment = Alignment.Center) {
                tileIcon(icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
            }
            Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            text?.let { Text(it, color = c.inkSoft, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp) }
            action?.let { Spacer(Modifier.height(4.dp)); it() }
        }
    }
}

/**
 * The inside of a DialogWindow drawn as a Fluent ContentDialog: the title
 * with its line and the close cross, the body (scrolling), and the footer
 * band with the buttons on the end side. [onClose] is the cross and Esc.
 */
@Composable
fun DialogFrame(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    footerStart: (@Composable () -> Unit)? = null,
    /**
     * False for a body that scrolls itself — the shared forms (ReturnForm,
     * StockCountForm, ShiftScreen) and anything with a LazyColumn. A scrolling
     * body inside this frame's own scroll is measured with no height limit:
     * a nested verticalScroll throws, and a weighted LazyColumn gets no
     * height at all (21 Oct 2026, found reading the return dialog).
     */
    scroll: Boolean = true,
    footer: @Composable () -> Unit,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val c = LocalRoshanColors.current
    // This frame's head is the dialog's one title and ✕ (Marhala 2): the dialog draws none of its own.
    OwnDialogHead()
    Column(Modifier.fillMaxSize().background(Look.layer)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box(Modifier.size(32.dp).clip(Look.ctl).press(Look.ctl, onClick = onClose), contentAlignment = Alignment.Center) { Glyph("ic_close", c.inkSoft, 14) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = if (scroll) 20.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = body
        )
        Rule()
        Row(Modifier.fillMaxWidth().background(Look.layerAlt).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (footerStart != null) Box(Modifier.weight(1f)) { footerStart() } else Spacer(Modifier.weight(1f))
            footer()
        }
    }
}

/** A line of a figure in a card: its label small, the figure in its colour, a count or note under it. */
@Composable
fun FigureCard(label: String, amount: String, color: Color, modifier: Modifier = Modifier, under: String? = null, icon: String? = null, tint: Color? = null,
    /** A small square of this colour before the label: the key to a chart's colours (dues by age). */
    swatch: Color? = null) {
    val c = LocalRoshanColors.current
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Box(Modifier.size(26.dp).background(tint ?: Look.subtle, Look.ctl), contentAlignment = Alignment.Center) { Glyph(icon, color, 14) }
            if (swatch != null) Box(Modifier.size(12.dp).background(swatch, RoundedCornerShape(3.dp)))
            Text(label, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amount, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        under?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** When something was, said shortly as the mockup does (Home's dealings, the backup chip): Today 9:40 AM, Yesterday 5:15 PM, Thu 4:10 PM, 12 Oct 6:20 PM. */
@Composable
fun whenWords(at: Long): String {
    val appWords = com.innovation313.roshankhata.words.LocalWords.current
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val time = remember(appWords) { DateWords.formatter("h:mm a") }.format(java.util.Date(at))
    val dayWord = when {
        day == today -> word(W.range_today)
        day == today.minusDays(1) -> word(W.range_yesterday)
        day.isAfter(today.minusDays(7)) -> remember(appWords) { DateWords.formatter("EEE") }.format(java.util.Date(at))
        else -> remember(appWords) { DateWords.formatter("d MMM") }.format(java.util.Date(at))
    }
    return "$dayWord $time"
}

/** A quiet note line under a head: 12 sp, muted. */
@Composable
fun Note(text: String) { Text(text, color = LocalRoshanColors.current.textMuted, fontSize = 12.sp) }

/** A small status tag: words on a tint (Pending, Cleared, Bounced...). */
@Composable
fun Tag(text: String, color: Color, tint: Color) {
    Text(text, Modifier.background(tint, Look.ctl).padding(horizontal = 8.dp, vertical = 2.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
}

/** A day shown in a Fluent box with the calendar glyph; a click opens the picker. [tall] matches a big amount box beside it. */
@Composable
fun DateBox(label: String?, value: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, tall: Boolean = false) =
    ChoiceBox(label, value, placeholder, onClick, modifier, tall, icon = "ic_calendar")

/**
 * A choice shown in a Fluent box (a supplier, a day): the label above, what is chosen — else [placeholder], muted —
 * and the [icon] at the end; a click opens the list or the picker (Marhala 4: the scheme form's whole supplier
 * list stood open in the form).
 */
@Composable
fun ChoiceBox(label: String?, value: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, tall: Boolean = false, icon: String = "ic_chevron_down") {
    val c = LocalRoshanColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        label?.let { Text(it, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        Row(
            Modifier.fillMaxWidth().height(if (tall) 48.dp else 32.dp).clip(Look.ctl).background(Look.control).border(1.dp, Look.edge, Look.ctl)
                .press(Look.ctl, onClick = onClick).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(value ?: placeholder, Modifier.weight(1f), color = if (value != null) c.ink else c.textMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Glyph(icon, c.inkSoft, if (icon == "ic_calendar") 16 else 14)
        }
    }
}

/**
 * A party picked from the book for a form (a cheque, a plan, a bill): the chosen
 * one as a row with its avatar and a Change button; otherwise a search box with
 * the matches under it (NameSearch, the app's one rule).
 */
@Composable
fun PartyPicker(
    parties: List<com.innovation313.roshankhata.data.PartyWithBalance>,
    chosenId: Long?,
    onPick: (Long?) -> Unit,
    label: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    var search by remember { mutableStateOf("") }
    val chosen = parties.firstOrNull { it.id == chosenId }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (chosen != null) {
            Row(
                Modifier.fillMaxWidth().clip(Look.ctl).background(Look.accentTint).border(1.dp, Look.accent, Look.ctl).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Avatar(chosen.name, 28, id = chosen.id)
                Column(Modifier.weight(1f)) {
                    Text(Format.name(chosen.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The balance with its side's words, as the khata says it (Marhala 4: "- Rs 5,000" said nothing of
                    // which way it ran); a settled account says so.
                    val side = com.innovation313.roshankhata.words.word(com.innovation313.roshankhata.screens.balanceSideWord(chosen.balance))
                    val owed = if (com.innovation313.roshankhata.data.Money.isZero(chosen.balance)) side else Format.money(chosen.balance) + " " + side
                    Text(
                        listOfNotNull(chosen.phone?.takeIf { !samePhone(it, chosen.name) }?.let { Format.ltr(it) }, owed).joinToString(" · "),
                        color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                FluentButton(com.innovation313.roshankhata.words.word(com.innovation313.roshankhata.words.W.edit), { onPick(null) }, kind = ButtonKind.SUBTLE)
            }
        } else {
            FluentField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = hint, leadingIcon = "ic_search")
            val q = search.trim().lowercase()
            val shown = parties.filter { com.innovation313.roshankhata.ui.NameSearch.matches(it.name, it.phone, q) }.take(50)
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(Look.ctl).background(Look.layer).border(1.dp, Look.strokeCard, Look.ctl).verticalScroll(rememberScrollState())) {
                shown.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().press { onPick(p.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Avatar(p.name, 26, id = p.id)
                        Text(Format.name(p.name), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        p.phone?.takeIf { !samePhone(it, p.name) }?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                    }
                    if (i < shown.lastIndex) Rule()
                }
            }
        }
    }
}

/**
 * A row of pills that scrolls sideways, with Windows' own scrollbar under it
 * while it does not fit (25 Oct 2026, the audit: the language, country and
 * report pills ran off the edge with no sign that more were there).
 */
@Composable
fun PillRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val state = rememberScrollState()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(state), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
        if (state.maxValue in 1 until Int.MAX_VALUE) {
            androidx.compose.foundation.HorizontalScrollbar(
                androidx.compose.foundation.rememberScrollbarAdapter(state),
                Modifier.fillMaxWidth().padding(top = 3.dp)
            )
        }
    }
}

/** A card's modifier: the layer colour, 8 dp corners, the 1 px edge — for a list or a block that is not a [FluentCard]. */
@Composable
fun Modifier.card(): Modifier = this.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)

/**
 * Material's OutlinedTextField in Fluent's clothes (4 dp corners, the accent
 * on focus, the control fill), for the forms not yet rewritten on [FluentField]:
 * the same call shape, so a pane changes one word.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Outlined(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    /** Calc mode, as on [FluentField]: the running total, worked out on Enter / "=" / leaving. */
    calc: com.innovation313.roshankhata.screens.CalcKind? = null
) {
    val c = LocalRoshanColors.current
    val sumLine = calc?.let { com.innovation313.roshankhata.screens.CalcText.line(value, it) }
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = if (calc != null) modifier.calcKeys({ value }, calc, onValueChange) else modifier,
        enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        supportingText = supportingText ?: sumLine?.let { line -> { Text(line, color = Look.accent, fontWeight = FontWeight.SemiBold, maxLines = 1) } },
        isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = Look.ctl,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Look.accent,
            unfocusedBorderColor = Look.edge,
            focusedLabelColor = Look.accent,
            unfocusedLabelColor = c.inkSoft,
            cursorColor = Look.accent,
            focusedContainerColor = Look.control,
            unfocusedContainerColor = Look.control,
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink
        )
    )
}

/**
 * A dialog window's size, no larger than the screen it opens on (21 Oct 2026,
 * the critical review): 29 of the 46 dialogs were taller than a 1366x768
 * laptop at 125% shows, and their Save buttons sat under the taskbar. Java's
 * usable screen (taskbar left out) is in the same units as a window's dp.
 */
fun fitH(h: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    minOf(h, (usableScreen().height - 24).coerceAtLeast(320).dp)

fun fitW(w: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    minOf(w, (usableScreen().width - 24).coerceAtLeast(360).dp)

private fun usableScreen(): java.awt.Rectangle =
    runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
        .getOrDefault(java.awt.Rectangle(0, 0, 1366, 728))

/**
 * A question in the app's own dialog (Marhala 4, the audit: 58 questions were Material's, some with no title):
 * the dialog's one title and ✕, the question, Cancel and the one answer — red when it removes or undoes.
 */
@Composable
fun AskDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    danger: Boolean = false,
    height: Int = 280
) {
    AppDialog(onCloseRequest = onDismiss, title = title, state = rememberDialogState(width = fitW(480.dp), height = fitH(height.dp))) {
        DialogFrame(title, subtitle = subtitle, onClose = onDismiss, footer = {
            FluentButton(word(W.cancel), onDismiss, kind = ButtonKind.STANDARD)
            FluentButton(confirm, onConfirm, kind = if (danger) ButtonKind.DANGER else ButtonKind.ACCENT)
        }) {
            Text(text, color = LocalRoshanColors.current.ink, fontSize = 14.sp)
        }
    }
}

/** Something said in the app's own dialog, with one OK (Marhala 4: the Material ones had no frame of the app's). */
@Composable
fun TellDialog(title: String, text: String, onClose: () -> Unit, height: Int = 260) {
    AppDialog(onCloseRequest = onClose, title = title, state = rememberDialogState(width = fitW(480.dp), height = fitH(height.dp))) {
        DialogFrame(title, onClose = onClose, footer = { FluentButton(word(W.ok), onClose, kind = ButtonKind.ACCENT) }) {
            Text(text, color = LocalRoshanColors.current.ink, fontSize = 14.sp)
        }
    }
}
