package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

/** One line of the header's More menu; a null [onClick] is a group's heading. */
class MenuLine(val text: String, val onClick: (() -> Unit)?)

/**
 * The window's header (1.10.0, the Fluent face): one flat 48 dp band on the
 * page colour — the shop on the start side (its logo, its name, a chevron),
 * then on the end side the backup, the phone, the bell, More and the one
 * accent button, Add Party. Every control is 32 dp with 4 dp corners, as
 * Windows 11 draws its own.
 */
@Composable
fun AppHeader(
    shopName: String,
    logo: ImageBitmap?,
    onShop: () -> Unit,
    /** "Last backup …" with the time of the newest copy, or Back up now when there is none. */
    backupLabel: String,
    onBackup: () -> Unit,
    onPhone: () -> Unit,
    alerts: Int,
    onAlerts: () -> Unit,
    menu: List<MenuLine>,
    onAddParty: () -> Unit
) {
    val c = LocalRoshanColors.current
    // On a laptop's own screen at a big text size the labels did not fit ("From pho…",
    // the owner's photo, 20 Oct): below [roomy] the side buttons keep only their icons
    // (their words as the hover tip) and Add Party drops its shortcut pill.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val roomy = maxWidth >= 1180.dp
        Row(
            Modifier.fillMaxWidth().background(Look.base).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The shop: its logo on the brand green, its name, Profile · Switch shop under it.
            Row(
                Modifier.clip(Look.ctl).press(Look.ctl, onClick = onShop).padding(start = 4.dp, top = 3.dp, bottom = 3.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(Look.accent), contentAlignment = Alignment.Center) {
                    if (logo != null) {
                        Image(logo, contentDescription = null, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Glyph("ic_storefront", Look.onAccent, 18)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.widthIn(max = if (roomy) 320.dp else 200.dp)) {
                    Text(shopName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(word(W.shop_sub_switch), color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Glyph("ic_chevron_down", c.inkSoft, 14)
            }
            Spacer(Modifier.weight(1f))
            if (roomy) {
                FluentButton(backupLabel, onBackup, icon = "ic_tile_backup")
                FluentButton(word(W.wifi_pc_button), onPhone, icon = "ic_phone")
            } else {
                IconAction("ic_tile_backup", backupLabel, onBackup)
                IconAction("ic_phone", word(W.wifi_pc_button), onPhone)
            }
            // The bell: what needs the owner today, the phone's own list (LedgerAlerts).
            Box {
                IconAction("ic_bell", word(W.na_title), onAlerts, filled = alerts > 0)
                Badge(alerts, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
            }
            Box {
                var open by remember { mutableStateOf(false) }
                IconAction("ic_more_vert", word(W.more_options), { open = true })
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluentButton(word(W.add_party), onAddParty, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
            }
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

/** A section's own tile picture, small, without its tint behind it. */
@Composable
private fun TileGlyph(s: Section) {
    tileIcon(s.icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
}

/** Esc closes a window, as every Windows dialog does; [close] is the window's own close. */
fun escCloses(close: () -> Unit): (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { e ->
    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Escape) {
        close(); true
    } else false
}
