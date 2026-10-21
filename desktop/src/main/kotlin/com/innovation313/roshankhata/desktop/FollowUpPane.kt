package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.FollowUpRank
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PaymentHabit
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.WhatsAppNumber
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import java.net.URI
import java.net.URLEncoder
import java.util.TimeZone

/**
 * Follow-up on the computer (KMP Phase 7o): who owes the shop, in the
 * phone's shared order (FollowUpRank: a promised date due first, then late
 * by their own paying habit, then quiet longest), with each customer's
 * habit and promise. Message opens WhatsApp (the app, or WhatsApp Web) with
 * the phone's polite reminder ready; nothing is sent until the owner sends it.
 */
@Composable
fun FollowUpPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, shopName: String?) {
    val c = LocalRoshanColors.current
    val points by remember { dao.observeOwingLedgerPoints() }.collectAsState(emptyList())
    val promiseRows by remember { dao.observePromises() }.collectAsState(emptyList())
    val now = System.currentTimeMillis()
    val tz = TimeZone.getDefault()
    val habits = remember(points) { PaymentHabit.forAll(points) }
    val promises = promiseRows.associate { it.partyId to it.due }
    val debtors = parties.filter { Money.isPositive(it.balance) }
    val ordered = remember(debtors, habits, promises) { FollowUpRank.order(debtors, habits, promises, now, tz) }
    val today = ordered.count { FollowUpRank.remindToday(it.id, habits, promises, now, tz, emptySet()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaneTitle(word(W.followup_title), word(W.followup_subtitle))
        if (ordered.isEmpty()) {
            Text(word(W.followup_empty), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
            return@Column
        }
        Text(
            plural(WP.followup_summary, ordered.size, ordered.size, Format.money(ordered.sumOf { it.balance })) +
                if (today > 0) "  ·  " + plural(WP.followup_today, today, today) else "",
            color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        LazyColumn(Modifier.fillMaxSize().card()) {
            items(ordered, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(p.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val days = ((now - p.lastActivity) / (24L * 60 * 60 * 1000)).toInt()
                        Text(
                            if (days <= 0) word(W.followup_today) else plural(WP.followup_last_ago, days, days),
                            color = if (days >= 30) c.owedToMe else c.textMuted, fontSize = 13.sp
                        )
                        val habit = habits[p.id]
                        val promise = promises[p.id]
                        val line = listOfNotNull(
                            promise?.let { word(W.followup_promised, Format.dateOnly(it)) },
                            habit?.let {
                                if (it.isLate) plural(WP.followup_habit_late, it.waitingDays, it.waitingDays, it.typicalDays)
                                else plural(WP.followup_habit, it.typicalDays, it.typicalDays)
                            }
                        ).joinToString("  ·  ")
                        if (line.isNotEmpty()) {
                            val urgent = FollowUpRank.lateByHabit(p.id, habits, promises, now, tz) || FollowUpRank.isDue(promise, now, tz)
                            Text(line, color = if (urgent) c.brandGreenText else c.textMuted, fontSize = 12.sp,
                                fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Text(Format.money(p.balance), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val number = p.phone?.let { WhatsAppNumber.of(it) }
                    TextButton(enabled = number != null, onClick = {
                        val text = reminderText(words, p.name, p.balance, promises[p.id], shopName)
                        runCatching {
                            java.awt.Desktop.getDesktop().browse(URI("https://wa.me/$number?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")))
                        }
                    }) { Text(if (number != null) word(W.promo_send) else "–", color = c.brandGreenText) }
                }
                Rule()
            }
        }
    }
}

/**
 * The phone's first, gentle reminder (Reminder.buildMessage, step 1): the
 * amount, the date they agreed when there is one (never an invented date),
 * and the shop's name. The phone's firmer second and third steps and its
 * payment-details block are not on the computer yet.
 */
fun reminderText(words: Words, name: String, balance: Double, promised: Long?, shopName: String?): String {
    // The figure and the names sealed in their own direction, as the phone's (21 Oct 2026).
    val amount = Format.ltr(Format.money(balance))
    val who = Format.isolate(name)
    val body = if (promised != null) words.text(W.reminder_they_owe_by_date, who, amount, Format.dateOnly(promised))
    else words.text(W.reminder_they_owe, who, amount)
    return body + if (shopName.isNullOrBlank()) "" else "\n\n— " + Format.isolate(shopName)
}
