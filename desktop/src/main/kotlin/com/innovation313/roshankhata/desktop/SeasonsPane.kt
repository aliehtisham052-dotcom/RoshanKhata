package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Fasal ka Hisaab on the computer (KMP Phase 7u), the phone's
 * SeasonsActivity on the shared SeasonBook: the current crop season and the
 * one before, how many customers took credit, how much went out, how much is
 * back and still out, and how long after harvest they usually clear. A
 * season opens to who still owes for it, biggest first, and a name there opens that customer's account.
 */
@Composable
fun SeasonsPane(dao: KhataDao, parties: List<PartyWithBalance>, reload: Int,
    /** A customer in the owing list opens his account, as the phone's list does (Marhala 4, the audit). */
    onParty: (Long) -> Unit = {}) {
    val c = LocalRoshanColors.current
    var rows by remember { mutableStateOf<List<SeasonBook.PartySeason>>(emptyList()) }
    var open by remember { mutableStateOf<SeasonBook.Season?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        rows = withContext(Dispatchers.IO) {
            // Only the two seasons' lines, with each balance on the first day carried in (as the phone).
            val first = SeasonBook.window(System.currentTimeMillis())
            val since = SeasonBook.seasonStart(first)
            val lines = dao.observeSeasonLinesSince(since).first()
            val carried = dao.observeSeasonCarriedIn(since).first()
            SeasonBook.book(lines, carriedIn = carried.associate { it.partyId to it.net }).filter { it.season >= first }
        }
        loaded = true
    }
    val names = remember(parties) { parties.associate { it.id to it.name } }
    val summaries = SeasonBook.summaries(rows)

    // In the page frame (Marhala 4), each season a card that opens to who still owes for it.
    FluentPage(word(W.season_title), word(W.season_subtitle), scroll = false) {
        if (summaries.isEmpty()) {
            if (loaded) EmptyState("ic_tile_season", word(W.season_empty), null, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(summaries) { s ->
                    Column(
                        Modifier.fillMaxWidth().card().press(Look.card) { open = s.season }.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(seasonName(s.season), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(harvestLine(s.season), color = c.textMuted, fontSize = 12.sp)
                        Text(plural(WP.season_given, s.customers, s.customers, Format.money(s.given)), color = c.ink, fontSize = 14.sp)
                        val back = if (s.given > 0) ((s.cleared / s.given) * 100).toInt().coerceIn(0, 100) else 0
                        LinearProgressIndicator(progress = { back / 100f }, modifier = Modifier.fillMaxWidth(), color = Look.accent, trackColor = Look.subtle)
                        Text(word(W.season_back_out, Format.money(s.cleared), Format.money(s.outstanding)), color = c.inkSoft, fontSize = 13.sp)
                        val d = s.medianDaysAfterHarvest
                        Text(
                            when {
                                d == null -> word(W.season_pace_none, s.clearedCustomers, s.customers)
                                d > 0 -> plural(WP.season_pace_after, d, s.clearedCustomers, s.customers, d)
                                else -> word(W.season_pace_before, s.clearedCustomers, s.customers)
                            },
                            color = c.textMuted, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    open?.let { season ->
        val owing = rows.filter { it.season == season && Money.isPositive(it.outstanding) }.sortedByDescending { it.outstanding }
        val close = { open = null }
        val title = word(W.season_owing_title, seasonName(season))
        // In the app's frame (Marhala 4): the list scrolls in the height left, biggest first, faces in the khata's colours.
        AppDialog(onCloseRequest = close, title = title, state = rememberDialogState(width = fitW(520.dp), height = fitH(minOf(600, 220 + 50 * owing.size.coerceAtLeast(1)).dp))) {
            DialogFrame(title, onClose = close, scroll = false, footer = { FluentButton(word(W.ok), close, kind = ButtonKind.ACCENT) }) {
                if (owing.isEmpty()) Text(word(W.season_all_clear), color = c.inkSoft, fontSize = 14.sp)
                else LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(owing, key = { it.partyId }) { r ->
                        val who = names[r.partyId] ?: "—"
                        Row(Modifier.fillMaxWidth().press(Look.ctl) { open = null; onParty(r.partyId) }.padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(who, size = 30, id = r.partyId)
                            Text(Format.name(who), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(Format.money(r.outstanding), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                        Rule()
                    }
                }
            }
        }
    }
}

@Composable
internal fun seasonName(s: SeasonBook.Season): String =
    if (s.crop == SeasonBook.Crop.RABI) word(W.season_rabi, s.year, (s.year + 1) % 100) else word(W.season_kharif, s.year)

@Composable
private fun harvestLine(s: SeasonBook.Season): String =
    if (s.crop == SeasonBook.Crop.RABI) word(W.season_harvest_rabi, s.year + 1) else word(W.season_harvest_kharif, s.year)
