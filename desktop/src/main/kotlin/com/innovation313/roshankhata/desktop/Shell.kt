package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent

/** One line of the header's More menu; a null [onClick] is a group's heading. */
class MenuLine(val text: String, val onClick: (() -> Unit)?)

/**
 * The open page's head, held by the window (Marhala 2, the owner's mockup of
 * 25 Oct 2026): a page's [PageHeader] or [PaneTitle] writes its title, its
 * line under it and its own actions here, and the window draws them in its
 * one top row beside the app's own buttons — no second band, no title twice.
 * Outside the window (a dialog, a test) [LocalPageHead] is null and the page
 * draws its own head as before.
 */
class PageHead {
    var title: String? by mutableStateOf(null)
    var subtitle: String? by mutableStateOf(null)
    var actions: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalPageHead = androidx.compose.runtime.staticCompositionLocalOf<PageHead?> { null }

/**
 * The shop at the top of the rail (the owner's mockup): its logo on the deep
 * green with a gold edge, its name, Profile · Switch shop under it, a chevron;
 * the device it syncs with live, when it does. A click offers the two it names:
 * the shop's profile and the other shops (25 Oct 2026, the audit: it only ever
 * opened the list of businesses).
 */
@Composable
fun ShopCard(shopName: String, logo: ImageBitmap?, live: String?, onProfile: () -> Unit, onSwitch: () -> Unit) {
    val c = LocalRoshanColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Look.layer)
                .border(1.5.dp, Look.accent, RoundedCornerShape(16.dp))
                .press(RoundedCornerShape(16.dp)) { open = true }.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Look.hero).border(2.dp, Look.goldEdge, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (logo != null) Image(logo, contentDescription = null, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                else Glyph("ic_storefront", Look.goldEdge, 19)
            }
            Column(Modifier.weight(1f)) {
                Text(shopName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(word(W.shop_sub_switch), color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live != null) { Spacer(Modifier.height(3.dp)); LiveBadge(live) }
            }
            Glyph("ic_chevron_down", c.inkSoft, 14)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Look.layer) {
            DropdownMenuItem(text = { Text(word(W.business_profile), color = c.ink, fontSize = 14.sp) }, onClick = { open = false; onProfile() })
            DropdownMenuItem(text = { Text(word(W.businesses_title), color = c.ink, fontSize = 14.sp) }, onClick = { open = false; onSwitch() })
        }
    }
}

/**
 * The header's backup: a cloud and two lines (the owner's mockup) — "Backed up" over when, or, once a
 * week has passed or there was never one, how old it is in the warning colour over "Back up now".
 * [compact] keeps only the cloud, its words as the hover tip.
 */
@Composable
fun BackupChip(at: Long?, old: Boolean, compact: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    val now = System.currentTimeMillis()
    val days = at?.let { ((now - it) / (24L * 60 * 60 * 1000)).toInt() }
    val top = when {
        at == null -> word(W.summary_backup_never)
        old -> com.innovation313.roshankhata.words.plural(com.innovation313.roshankhata.words.WP.summary_backup_days, days ?: 0, days ?: 0)
        else -> word(W.header_backed_up)
    }
    val bottom = if (at == null || old) word(W.backup_now) else whenWords(at)
    val ink = if (at == null || old) Look.warn else Look.accent
    val tint = if (at == null || old) Look.warnTint else Look.accentTint
    Tip("$top · $bottom", show = compact) {
        Row(
            Modifier.heightIn(min = 40.dp).clip(Look.ctl).background(Look.control).border(1.dp, Look.edge, Look.ctl)
                .press(Look.ctl, onClick = onClick).padding(start = 6.dp, end = if (compact) 6.dp else 12.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(28.dp).background(tint, Look.ctl), contentAlignment = Alignment.Center) { Glyph("ic_feature_backup", ink, 17) }
            if (!compact) Column {
                Text(top, color = if (at == null || old) Look.warn else c.textMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false)
                Text(bottom, color = c.ink, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The window's top row (Marhala 2): the open page's title and its line on the
 * start side ([head]), its own actions, then the app's — the backup, the phone,
 * the bell, More and the one accent button, Add Party. Below [roomy] widths the
 * backup keeps only its cloud (its words as the hover tip).
 */
@Composable
fun AppHeader(
    head: PageHead?,
    /** When the newest real backup was made (a "Back up now" file or the outside folder's copy), or null. */
    backupAt: Long?,
    /** A week or more since [backupAt], or never. */
    backupOld: Boolean,
    onBackup: () -> Unit,
    onPhone: () -> Unit,
    alerts: Int,
    onAlerts: () -> Unit,
    menu: List<MenuLine>,
    onAddParty: () -> Unit
) {
    val c = LocalRoshanColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val roomy = maxWidth >= 760.dp
        // Read here: inside the Row below, BoxWithConstraints' maxWidth is not reachable (both scopes are layout
        // scopes, and Kotlin's DSL marker hides the outer one — the 1.18 build failed on it).
        val actionsMax = maxWidth * 0.42f
        Row(
            Modifier.fillMaxWidth().background(Look.layerAlt).padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                head?.title?.let { Text(it, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                head?.subtitle?.let { Text(it, color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            // The page's own actions: the room they need, at most 42% of the row (a weight here reserved half the
            // row even for no buttons, and Home's day was cut to "Sunday, 25 Oct…" on the laptop).
            head?.actions?.let { pageActions ->
                Row(
                    Modifier.widthIn(max = actionsMax).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) { pageActions() }
                Spacer(Modifier.width(4.dp))
            }
            BackupChip(backupAt, backupOld, compact = !roomy, onClick = onBackup)
            IconAction("ic_phone", word(W.wifi_pc_button), onPhone, size = 36)
            // The bell: what needs the owner today, the phone's own list (LedgerAlerts).
            Box {
                IconAction("ic_bell", word(W.na_title), onAlerts, filled = alerts > 0, size = 36)
                Badge(alerts, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
            }
            Box {
                var open by remember { mutableStateOf(false) }
                IconAction("ic_more_vert", word(W.more_options), { open = true }, size = 36)
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Look.layer) {
                    menu.forEachIndexed { i, line ->
                        val go = line.onClick
                        if (go == null) {
                            if (i > 0) HorizontalDivider(color = Look.line)
                            Text(
                                line.text,
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                                color = c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(line.text, color = c.ink, fontSize = 14.sp) },
                                onClick = { open = false; go() }
                            )
                        }
                    }
                }
            }
            // The one filled button: a new party, as the phone's Khata button (Ctrl+N).
            FluentButton(word(W.add_party), onAddParty, kind = ButtonKind.ACCENT, icon = "ic_add_plain", tall = true)
        }
    }
}

/** The words of an icon-only button as its hover tip; [show] false leaves the button as it is. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tip(text: String, show: Boolean, content: @Composable () -> Unit) {
    if (!show) { content(); return }
    val c = LocalRoshanColors.current
    TooltipArea(
        tooltip = {
            Text(
                text,
                Modifier.background(c.ink, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                color = c.surface, fontSize = 13.sp
            )
        },
        delayMillis = 400
    ) { content() }
}

/** A quiet outlined button with its own content inside: Fluent's standard button, 32 tall. */
@Composable
fun OutlineAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.heightIn(min = 32.dp).clip(Look.ctl)
            .border(BorderStroke(1.dp, Look.edge), Look.ctl)
            .background(Look.control)
            .press(Look.ctl, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** One of the phone's white glyphs (ic_bell, ic_phone...) painted in [tint]. */
@Composable
fun Glyph(icon: String, tint: Color, size: Int) {
    val flip = iconFlipped(icon)
    tileIcon(icon)?.let {
        Image(it, contentDescription = null, modifier = Modifier.size(size.dp).then(if (flip) Modifier.scale(-1f, 1f) else Modifier), colorFilter = ColorFilter.tint(tint))
    }
}

/**
 * The shared theme with the computer's type (Marhala 2, 25 Oct 2026, the audit): Material's styles
 * keep their sizes, not their fixed line heights and tracking. bodyLarge's 24 sp line made every 12-14 sp
 * line on the computer 24 sp tall — a laptop showed two or three rows of a list — and its 0.5 sp tracking
 * pulled Arabic-script words apart. Each line now takes its font's own height, so Urdu's taller letters
 * keep their room. Dialog windows inherit it from the window that opens them.
 */
@Composable
fun DeskTheme(dark: Boolean, content: @Composable () -> Unit) {
    com.innovation313.roshankhata.screens.RoshanTheme(dark = dark) {
        val type = remember { deskTypography() }
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
            typography = type,
            content = content
        )
    }
}

/** Material's type scale with each line's height left to its font and no added tracking (see [DeskTheme]). */
fun deskTypography(base: androidx.compose.material3.Typography = androidx.compose.material3.Typography()): androidx.compose.material3.Typography {
    fun androidx.compose.ui.text.TextStyle.own() = copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = 0.sp)
    return base.copy(
        displayLarge = base.displayLarge.own(), displayMedium = base.displayMedium.own(), displaySmall = base.displaySmall.own(),
        headlineLarge = base.headlineLarge.own(), headlineMedium = base.headlineMedium.own(), headlineSmall = base.headlineSmall.own(),
        titleLarge = base.titleLarge.own(), titleMedium = base.titleMedium.own(), titleSmall = base.titleSmall.own(),
        bodyLarge = base.bodyLarge.own(), bodyMedium = base.bodyMedium.own(), bodySmall = base.bodySmall.own(),
        labelLarge = base.labelLarge.own(), labelMedium = base.labelMedium.own(), labelSmall = base.labelSmall.own()
    )
}

/**
 * Esc closes a window, as every Windows dialog does; [close] is the window's own close. Something small
 * open inside it ([EscFirst]: a product box's suggestions) is closed first, and the dialog stays.
 */
fun escCloses(close: () -> Unit): (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { e ->
    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Escape) {
        if (!EscFirst.take()) close()
        true
    } else false
}

/**
 * What Esc closes before the dialog it is in (25 Oct 2026): a suggestion list ([SuggestBox]) sits in a
 * popup that never takes the keys, so the dialog's own Esc came first and threw a half-written invoice
 * away to close a list. The newest one open is closed; the dialog's Esc works again once none is open.
 */
object EscFirst {
    private val open = ArrayList<() -> Unit>()
    fun push(close: () -> Unit) { open.add(close) }
    fun pop(close: () -> Unit) { open.remove(close) }
    /** Closes the newest one open and says so; false when none is. */
    fun take(): Boolean {
        val last = open.removeLastOrNull() ?: return false
        last()
        return true
    }
}

/**
 * How many of the app's dialogs are open now. They are drawn inside the window
 * (Marhala 2), so a key typed in one passes the window's own shortcuts first:
 * KeyActions does nothing while any is open (F2 in an entry form opened a
 * second one; with a window of its own the dialog took its keys itself).
 */
object OpenDialogs {
    var count by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    fun opened() { count++ }
    fun closed() { count = (count - 1).coerceAtLeast(0) }
}

/** What a dialog's body tells its frame: it draws its own head (a DialogFrame, or a form with its own). */
class DialogChrome { var ownHead by mutableStateOf(false) }

val LocalDialogChrome = androidx.compose.runtime.staticCompositionLocalOf<DialogChrome?> { null }

/**
 * Said by a body that draws its own title and ✕ (DialogFrame, the entry form): the dialog then draws
 * none, so there is one title and one ✕. Outside a dialog it does nothing.
 */
@Composable
fun OwnDialogHead() {
    val chrome = LocalDialogChrome.current ?: return
    androidx.compose.runtime.DisposableEffect(chrome) { chrome.ownHead = true; onDispose { chrome.ownHead = false } }
}

/** True inside one of the app's dialogs, whose head names the dialog: a form there leaves its own title out. */
@Composable
fun inDialog(): Boolean = LocalDialogChrome.current != null

/**
 * Every dialog of the app (Marhala 2, 25 Oct 2026, the owner's mockup): a card
 * in the window over a dimmed page — one title and one ✕, the phone's corners
 * and colours — instead of a window of its own, which put Windows' title bar
 * and ✕ over the dialog's own (the audit: two titles, two ✕, a hundred dp of
 * chrome) and could open off the laptop's screen. [state]'s size is the card's,
 * kept inside the window; [title] heads a body that has no head of its own
 * ([OwnDialogHead]); [closable] false hides the ✕ of a question that must be
 * answered (the first shop type). Esc closes, as before. The card shares the
 * window's direction and text size, so nothing is carried in (R13 needed
 * KeepAppLook for a window of its own).
 */
@Composable
fun AppDialog(
    onCloseRequest: () -> Unit,
    state: androidx.compose.ui.window.DialogState = androidx.compose.ui.window.rememberDialogState(),
    title: String = "",
    onPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = escCloses(onCloseRequest),
    closable: Boolean = true,
    content: @Composable () -> Unit
) {
    val chrome = remember { DialogChrome() }
    androidx.compose.runtime.DisposableEffect(Unit) { OpenDialogs.opened(); onDispose { OpenDialogs.closed() } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCloseRequest,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            // A form's typing is never thrown away by a click beside it.
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            usePlatformInsets = true,
            useSoftwareKeyboardInset = true,
            scrimColor = Color.Black.copy(alpha = 0.42f)
        )
    ) {
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val want = state.size
            val w = if (want.width.value.isFinite()) minOf(want.width, maxWidth) else maxWidth
            val h = if (want.height.value.isFinite()) minOf(want.height, maxHeight) else maxHeight
            Column(
                Modifier.size(w, h)
                    .shadow(24.dp, Look.card, clip = false)
                    .clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)
                    .onPreviewKeyEvent(onPreviewKeyEvent)
                    .focusRequester(focus).focusable()
            ) {
                if (!chrome.ownHead) DialogHead(title, if (closable) onCloseRequest else null)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalDialogChrome provides chrome) { content() }
                }
            }
        }
    }
}

/** The head of a dialog whose body has none: its title and the ✕, as DialogFrame draws them. */
@Composable
private fun DialogHead(title: String, onClose: (() -> Unit)?) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClose != null) Box(Modifier.size(32.dp).clip(Look.ctl).press(Look.ctl, onClick = onClose), contentAlignment = Alignment.Center) {
            Glyph("ic_close", c.inkSoft, 14)
        }
    }
}

/** The app's direction and text size inside a window of its own (dialogs, the customer's screen). */
@Composable
fun KeepAppLook(dir: androidx.compose.ui.unit.LayoutDirection, fontScale: Float, content: @Composable () -> Unit) {
    val own = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides dir,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(own.density, fontScale),
        content = content
    )
}

/** "● Live · <device>": the book on the screen is the one kept live with that device (22 Oct 2026). */
@Composable
fun LiveBadge(with: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Look.accent).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Look.onAccent))
        Spacer(Modifier.width(6.dp))
        Text(word(W.live_badge, with), color = Look.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}
