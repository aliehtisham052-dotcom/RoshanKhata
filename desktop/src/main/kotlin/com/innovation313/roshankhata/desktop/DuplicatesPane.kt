package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.DismissedDuplicate
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.DuplicateDetector
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Duplicate customers on the computer (KMP Phase 7t), the phone's
 * DuplicateCustomersActivity on the shared DuplicateDetector: the same name
 * or the same number entered twice. Pick which record stays, read what
 * merging does, then merge (KhataDao.mergeParties: one transaction, entries,
 * bills, cheques and plans move, no money added or removed). "Not a
 * duplicate" is remembered until the group itself changes.
 */
@Composable
fun DuplicatesPane(
    dao: KhataDao, parties: List<PartyWithBalance>,
    /** Where this business keeps its customers' faces, so a merged-away one's goes to the one who stays. */
    home: java.io.File? = null, businessId: Long = 1L,
    onChanged: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var again by remember { mutableIntStateOf(0) }
    var reviewing by remember { mutableStateOf<DuplicateDetector.Group?>(null) }
    var survivor by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(again) { dismissed = withContext(Dispatchers.IO) { dao.getDismissedDuplicateKeys().toSet() } }
    val groups = remember(parties, dismissed) {
        DuplicateDetector.find(parties.map { DuplicateDetector.Candidate(it.id, it.name, it.phone, it.isCustomer, it.balance) }, dismissed)
    }
    val doneWord = word(W.duplicate_dismissed)
    val failed = word(W.duplicate_merge_failed)
    val words = LocalWords.current

    // In the page frame (Marhala 4): the head and its line, the note, the groups as cards.
    FluentPage(word(W.duplicate_customers), word(W.duplicate_customers_subtitle), scroll = false) {
        Text(word(W.duplicate_note), color = c.textMuted, fontSize = 12.sp)
        if (groups.isEmpty()) {
            EmptyState("ic_tile_khata", word(W.no_duplicates), word(W.duplicate_customers_subtitle), Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { DuplicateDetector.groupKey(it.members) }) { g ->
                    Column(Modifier.fillMaxWidth().card().padding(14.dp)) {
                        Text(
                            word(when (g.reason) {
                                DuplicateDetector.Reason.NAME -> W.duplicate_reason_name
                                DuplicateDetector.Reason.PHONE -> W.duplicate_reason_phone
                                DuplicateDetector.Reason.BOTH -> W.duplicate_reason_both
                            }),
                            color = c.textMuted, fontSize = 12.sp
                        )
                        g.members.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(m.name, size = 30, id = m.partyId)
                                Text(Format.name(m.name), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Format.ltr(m.phone?.takeIf { it.isNotBlank() } ?: "—") + "  ·  " + Format.money(m.balance), color = c.inkSoft, fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        FluentButton(word(W.duplicate_review_merge), { reviewing = g; survivor = 0 }, kind = ButtonKind.SUBTLE)
                    }
                }
            }
        }
    }

    // Step 1: which record survives. Nothing is written yet. In the app's frame (Marhala 4), the chosen one
    // marked as PickDialog marks it; "Not a duplicate" on the start side, as the phone's third answer.
    reviewing?.takeIf { !confirming }?.let { g ->
        val close = { reviewing = null }
        val title = word(W.duplicate_pick_primary_title)
        AppDialog(onCloseRequest = close, title = title, state = rememberDialogState(width = fitW(520.dp), height = fitH((250 + 62 * g.members.size).dp))) {
            DialogFrame(title, onClose = close, footerStart = {
                FluentButton(word(W.duplicate_not_duplicate), {
                    val key = DuplicateDetector.groupKey(g.members)
                    reviewing = null
                    scope.launch {
                        // Said when it could not be kept (it failed in silence before).
                        notice = runCatching {
                            withContext(Dispatchers.IO) { dao.dismissDuplicate(DismissedDuplicate(key, System.currentTimeMillis())) }
                            doneWord
                        }.getOrElse { failText(words, it) }
                        again++
                    }
                }, kind = ButtonKind.SUBTLE)
            }, footer = {
                FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD)
                FluentButton(word(W.duplicate_next), { confirming = true }, kind = ButtonKind.ACCENT)
            }) {
                Text(word(W.duplicate_pick_primary_message), color = c.inkSoft, fontSize = 13.sp)
                Column {
                    g.members.forEachIndexed { i, m ->
                        val on = i == survivor
                        Row(
                            Modifier.fillMaxWidth().clip(Look.ctl).background(if (on) Look.accentTint else Color.Transparent)
                                .press(Look.ctl) { survivor = i }.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Avatar(m.name, size = 32, id = m.partyId)
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(m.name), color = c.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Format.ltr(m.phone?.takeIf { it.isNotBlank() } ?: "—") + "  ·  " + Format.money(m.balance), color = c.inkSoft, fontSize = 12.sp, maxLines = 1)
                            }
                            if (on) Glyph("ic_check_plain", Look.accent, 16)
                        }
                        if (i < g.members.lastIndex) Rule()
                    }
                }
            }
        }
    }
    // Step 2: a full account of what merging does, before it does it.
    if (confirming) {
        val g = reviewing
        if (g != null) {
            val keep = g.members[survivor]
            val done = word(W.duplicate_merge_done, keep.name)
            AskDialog(
                word(W.duplicate_merge_confirm_title, keep.name), word(W.duplicate_merge_confirm_message), word(W.duplicate_merge_go),
                danger = true, height = 380,
                onDismiss = { confirming = false },
                onConfirm = {
                    confirming = false; reviewing = null
                    scope.launch {
                        notice = runCatching {
                            withContext(Dispatchers.IO) {
                                val losers = g.members.filter { it.partyId != keep.partyId }.map { it.partyId }
                                dao.mergeParties(losers, keep.partyId)
                                // The face follows the account; a picture that cannot move never fails the merge.
                                if (home != null) runCatching { Photos.moveOnMerge(dao, home, businessId, losers, keep.partyId) }
                            }
                            done
                        }.getOrElse { failed }
                        onChanged()
                    }
                }
            )
        }
    }
    notice?.let { n -> TellDialog(word(W.duplicate_customers), n, { notice = null }) }
}
