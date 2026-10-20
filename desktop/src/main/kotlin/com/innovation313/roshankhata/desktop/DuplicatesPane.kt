package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DismissedDuplicate
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.DuplicateDetector
import com.innovation313.roshankhata.ui.Format
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
fun DuplicatesPane(dao: KhataDao, parties: List<PartyWithBalance>, onChanged: () -> Unit) {
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

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaneTitle(word(W.duplicate_customers), word(W.duplicate_customers_subtitle))
        Text(word(W.duplicate_note), color = c.textMuted, fontSize = 12.sp)
        if (groups.isEmpty()) {
            Text(word(W.no_duplicates), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { DuplicateDetector.groupKey(it.members) }) { g ->
                    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text(
                            word(when (g.reason) {
                                DuplicateDetector.Reason.NAME -> W.duplicate_reason_name
                                DuplicateDetector.Reason.PHONE -> W.duplicate_reason_phone
                                DuplicateDetector.Reason.BOTH -> W.duplicate_reason_both
                            }),
                            color = c.textMuted, fontSize = 12.sp
                        )
                        g.members.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(Format.name(m.name), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(Format.ltr(m.phone?.takeIf { it.isNotBlank() } ?: "—") + "  ·  " + Format.money(m.balance), color = c.inkSoft, fontSize = 13.sp)
                            }
                        }
                        TextButton({ reviewing = g; survivor = 0 }) { Text(word(W.duplicate_review_merge), color = c.brandGreenText) }
                    }
                }
            }
        }
    }

    // Step 1: which record survives. Nothing is written yet.
    reviewing?.takeIf { !confirming }?.let { g ->
        AlertDialog(
            onDismissRequest = { reviewing = null },
            title = { Text(word(W.duplicate_pick_primary_title)) },
            text = {
                Column {
                    Text(word(W.duplicate_pick_primary_message), color = c.textMuted, fontSize = 13.sp)
                    g.members.forEachIndexed { i, m ->
                        val on = i == survivor
                        Column(
                            Modifier.fillMaxWidth().padding(top = 6.dp)
                                .background(if (on) c.brandGreen else c.pageBg, RoundedCornerShape(10.dp))
                                .press { survivor = i }.padding(10.dp)
                        ) {
                            Text(Format.name(m.name), color = if (on) Color.White else c.ink, fontWeight = FontWeight.Bold)
                            Text(Format.ltr(m.phone?.takeIf { it.isNotBlank() } ?: "—") + "  ·  " + Format.money(m.balance),
                                color = if (on) Color.White else c.inkSoft, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ confirming = true }) { Text(word(W.duplicate_next)) } },
            dismissButton = {
                TextButton({
                    val key = DuplicateDetector.groupKey(g.members)
                    reviewing = null
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.dismissDuplicate(DismissedDuplicate(key, System.currentTimeMillis())) }
                        again++
                        notice = doneWord
                    }
                }) { Text(word(W.duplicate_not_duplicate)) }
            }
        )
    }
    // Step 2: a full account of what merging does, before it does it.
    if (confirming) {
        val g = reviewing
        if (g != null) {
            val keep = g.members[survivor]
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(word(W.duplicate_merge_confirm_title, keep.name)) },
                text = { Text(word(W.duplicate_merge_confirm_message)) },
                confirmButton = {
                    val done = word(W.duplicate_merge_done, keep.name)
                    TextButton(
                        onClick = {
                            confirming = false; reviewing = null
                            scope.launch {
                                notice = runCatching {
                                    withContext(Dispatchers.IO) { dao.mergeParties(g.members.filter { it.partyId != keep.partyId }.map { it.partyId }, keep.partyId) }
                                    done
                                }.getOrElse { failed }
                                onChanged()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                    ) { Text(word(W.duplicate_merge_go)) }
                },
                dismissButton = { TextButton({ confirming = false }) { Text(word(W.cancel)) } }
            )
        }
    }
    notice?.let { n ->
        AlertDialog(onDismissRequest = { notice = null }, confirmButton = { TextButton({ notice = null }) { Text(word(W.ok)) } }, text = { Text(n) })
    }
}
