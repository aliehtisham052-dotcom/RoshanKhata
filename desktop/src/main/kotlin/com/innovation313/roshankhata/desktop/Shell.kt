package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
 * The window's header, the phone's Home header made wide (20 Oct redesign):
 * the shop button on the start side (its logo, its name, Profile · Switch
 * shop), then on the end side the backup, the phone, the bell, More and the
 * one green button, Add Party. Eight equal buttons were here before.
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
        Column {
            Row(
                Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The shop button: the phone's shopButton (logo tile with the gold rim, name, Profile · Switch shop).
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .border(2.dp, c.brandGreen, RoundedCornerShape(16.dp))
                        .clickable(onClick = onShop)
                        .padding(start = 5.dp, top = 5.dp, bottom = 5.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Look.hero)
                            .border(2.dp, Look.gold, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logo != null) {
                            Image(logo, contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                        } else {
                            Glyph("ic_storefront", Look.gold, 22)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.widthIn(max = if (roomy) 320.dp else 200.dp)) {
                        Text(shopName, color = c.brandGreenText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(word(W.shop_sub_switch), color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("▾", color = c.brandGreenText, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                Tip(backupLabel, show = !roomy) {
                    OutlineAction(onClick = onBackup) {
                        TileGlyph(Section.DAILY)
                        if (roomy) {
                            Spacer(Modifier.width(8.dp))
                            Text(backupLabel, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                Tip(word(W.wifi_pc_button), show = !roomy) {
                    OutlineAction(onClick = onPhone) {
                        Glyph("ic_phone", c.inkSoft, 18)
                        if (roomy) {
                            Spacer(Modifier.width(8.dp))
                            Text(word(W.wifi_pc_button), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                // The bell: what needs the owner today, the phone's own list (LedgerAlerts).
                Box {
                    OutlineAction(onClick = onAlerts) { Glyph("ic_bell", c.brandGreenText, 20) }
                    if (alerts > 0) {
                        Text(
                            if (alerts > 99) "99+" else alerts.toString(),
                            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                                .background(c.gaveFill, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box {
                    var open by remember { mutableStateOf(false) }
                    OutlineAction(onClick = { open = true }) {
                        Text(word(W.more_options), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Text("▾", color = c.inkSoft, fontSize = 13.sp)
                    }
                    DropdownMenu(open, onDismissRequest = { open = false }) {
                        menu.forEachIndexed { i, line ->
                            val go = line.onClick
                            if (go == null) {
                                if (i > 0) HorizontalDivider(color = Look.line)
                                Text(
                                    line.text,
                                    Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                                    color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold
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
                Row(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(c.brandGreen)
                        .clickable(onClick = onAddParty).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(word(W.add_party), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (roomy) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ctrl N",
                            Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            HorizontalDivider(color = Look.line)
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

/** A quiet header button: an outline, the content inside, 40 tall. */
@Composable
fun OutlineAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Look.line), RoundedCornerShape(12.dp))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** One of the phone's white glyphs (ic_bell, ic_phone...) painted in [tint]. */
@Composable
fun Glyph(icon: String, tint: Color, size: Int) {
    tileIcon(icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint)) }
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
