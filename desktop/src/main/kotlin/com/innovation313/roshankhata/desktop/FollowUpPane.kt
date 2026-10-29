package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.FollowUpRank
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PaymentHabit
import com.innovation313.roshankhata.data.PaymentRequest
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.ReminderText
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.data.WhatsAppNumber
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Follow-up on the computer (KMP Phase 7o; Marhala 4, part 7 — the page frame): who owes the shop, in the
 * phone's shared order (FollowUpRank: a promised date due first, then late by their own paying habit, then quiet
 * longest), with each customer's habit and promise — the line that says he is due or late in red, never green.
 * Remind opens the reminder read over first ([ReminderDialog]); a customer reminded today on this computer
 * ([ReminderStore.openedToday]) goes to the foot and leaves the "to remind today" count, as on the phone.
 */
@Composable
fun FollowUpPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, shopName: String?, settings: ShopSettings, reminders: ReminderStore) {
    val c = LocalRoshanColors.current
    // The reminder read over before WhatsApp opens, at the ladder's next step (Marhala 4, as on the Khata page),
    // with the date the phone's reminder quotes (a plan's next due day, else the promised harvest day) and the
    // sale being chased (ReminderText.facts).
    var reminding by remember { mutableStateOf<Pair<PartyWithBalance, ReminderText.Facts>?>(null) }
    var closed by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val points by remember { dao.observeOwingLedgerPoints() }.collectAsState(emptyList())
    val promiseRows by remember { dao.observePromises() }.collectAsState(emptyList())
    val now = System.currentTimeMillis()
    val tz = TimeZone.getDefault()
    val habits = remember(points) { PaymentHabit.forAll(points) }
    val promises = promiseRows.associate { it.partyId to it.due }
    val debtors = parties.filter { Money.isPositive(it.balance) }
    // Re-read when a reminder closes: one opened just now counts.
    val reminded = remember(reminders, closed) { reminders.openedToday(now, tz) }
    // "Reminded on …, nothing received since" (PaymentRequest, 29 Oct 2026): the last reminder from this
    // computer against the customer's newest payment, from the points already read for his habit.
    val lastReminded = remember(reminders, closed) { reminders.lastReminded() }
    val lastGot = remember(points) { PaymentRequest.lastGotAt(points) }
    val ordered = remember(debtors, habits, promises, reminded) { FollowUpRank.order(debtors, habits, promises, now, tz, reminded) }
    val today = ordered.count { FollowUpRank.remindToday(it.id, habits, promises, now, tz, reminded) }

    FluentPage(word(W.followup_title), word(W.followup_subtitle), scroll = false) {
        if (ordered.isEmpty()) {
            EmptyState("ic_tile_followup", word(W.followup_empty), null, Modifier.weight(1f))
            return@FluentPage
        }
        // The phone's summary: how many owe and how much in all, and how many are to be reminded today.
        Row(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plural(WP.followup_summary, ordered.size, ordered.size, Format.money(ordered.sumOf { it.balance })), Modifier.weight(1f),
                color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (today > 0) Tag(plural(WP.followup_today, today, today), c.owedToMe, Look.getTint)
        }
        ListCard(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(ordered, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(p.name, 34, id = p.id)
                        Column(Modifier.weight(1f)) {
                            Text(Format.name(p.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val days = ((now - p.lastActivity) / (24L * 60 * 60 * 1000)).toInt()
                            Text(
                                if (days <= 0) word(W.followup_today) else plural(WP.followup_last_ago, days, days),
                                color = if (days >= 30) c.owedToMe else c.textMuted, fontSize = 12.sp, maxLines = 1
                            )
                            val habit = habits[p.id]
                            val promise = promises[p.id]
                            val line = listOfNotNull(
                                promise?.let { word(W.followup_promised, Format.dateOnly(it)) },
                                habit?.let {
                                    if (it.isLate) plural(WP.followup_habit_late, it.waitingDays, it.waitingDays, it.typicalDays)
                                    else plural(WP.followup_habit, it.typicalDays, it.typicalDays)
                                }
                            ).joinToString("  \u00b7  ")
                            if (line.isNotEmpty()) {
                                val urgent = FollowUpRank.lateByHabit(p.id, habits, promises, now, tz) || FollowUpRank.isDue(promise, now, tz)
                                Text(line, color = if (urgent) c.owedToMe else c.textMuted, fontSize = 12.sp,
                                    fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            val remindedAt = lastReminded[p.id]
                            if (remindedAt != null && PaymentRequest.waitingSince(remindedAt, lastGot[p.id])) {
                                Tag(word(W.followup_waiting, Format.dateOnly(remindedAt)), c.owedToMe, Look.getTint)
                            }
                        }
                        Text(Format.money(p.balance), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        val number = p.phone?.let { WhatsAppNumber.of(it) }
                        FluentButton(word(W.promo_send), {
                            scope.launch { reminding = p to withContext(Dispatchers.IO) { reminderFacts(dao, p.id) } }
                        }, kind = ButtonKind.SUBTLE, icon = "ic_action_whatsapp", enabled = number != null)
                    }
                    Rule()
                }
            }
        }
    }
    reminding?.let { (p, facts) -> ReminderDialog(p.id, p.name, p.phone, p.balance, facts, shopName, settings, reminders) { reminding = null; closed++ } }
}

/** ReminderText.facts with a book that cannot be read as nothing: the reminder still goes, without a date or number. */
suspend fun reminderFacts(dao: KhataDao, partyId: Long): ReminderText.Facts =
    runCatching { ReminderText.facts(dao, partyId) }.getOrDefault(ReminderText.Facts(null, null))
