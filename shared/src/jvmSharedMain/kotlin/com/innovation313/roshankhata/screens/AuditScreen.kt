package com.innovation313.roshankhata.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AdjustReason
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.AuditChain
import com.innovation313.roshankhata.data.AuditLog
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The audit log as the phone shows it (POS Phase 5): newest first, read-only,
 * with whether its seal ([AuditChain], schema 36) still holds.
 */
@Composable
fun AuditScreen(dao: KhataDao, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    val rows by remember { dao.observeAudit(500) }.collectAsState(emptyList())
    val staff by remember { dao.observeStaff() }.collectAsState(emptyList())
    var seal by remember { mutableStateOf<AuditChain.Result?>(null) }
    // L35: re-checked when the newest row changes (the window holds 500, so its size never moves once full), not per count.
    LaunchedEffect(rows.firstOrNull()?.id) { seal = withContext(Dispatchers.IO) { AuditChain.verifyAll { after, n -> dao.auditSliceAfter(after, n) } } }
    // Who a row concerns and which way a cash row went, looked up now (8 Nov 2026): the log keeps neither.
    val names by produceState(AuditNames.None, rows) { value = withContext(Dispatchers.IO) { AuditNames.load(dao, rows) } }
    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(word(W.audit_empty), color = c.textMuted) }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        seal?.let { s ->
            item(key = "seal") {
                Text(
                    if (s.intact) plural(WP.audit_seal_ok_rows, s.sealed, s.sealed) else word(W.audit_seal_broken, s.brokenAt ?: 0L),
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = if (s.intact) c.iOwe else c.owedToMe, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        items(rows, key = { it.id }) { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(auditActionWord(a, names), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(Format.dateTime(a.timestamp), color = c.textMuted, fontSize = 12.sp)
                }
                Text(
                    listOfNotNull(word(W.audit_by, a.user?.let { if (it == Access.OWNER) word(W.access_owner) else Format.name(it) } ?: word(W.access_owner)), auditDetail(a, staff, names)).joinToString(" · "),
                    color = c.inkSoft, fontSize = 13.sp
                )
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/**
 * A row's detail in the app's words (23 Oct 2026, the review: an Urdu owner
 * read "damage -3.00", "staff:12" and an English merge summary). What is
 * stored stays as it was, so old rows read the same way; only the codes the
 * app itself writes are turned into words, and anything else shows as saved.
 */
@Composable
fun auditDetail(a: AuditLog, staff: List<com.innovation313.roshankhata.data.Staff>, names: AuditNames = AuditNames.None): String? {
    // A khata link's customer (new rows carry no detail; older ones "Send khata link · <name>").
    if (isKhataLinkRow(a)) return names.party[a.refId] ?: a.detail?.substringAfter(" \u00b7 ", "")?.takeIf { it.isNotBlank() }
    val detail = a.detail?.takeIf { it.isNotBlank() } ?: return null
    return when (a.action) {
        // "Entry added · <customer> · Rs 800": the amount was stored as "800.00", the customer is looked up (8 Nov 2026).
        AuditAction.ENTRY_ADDED -> listOfNotNull(names.entryParty[a.refId], AuditNames.amount(detail)?.let { Format.moneyLtr(it) } ?: detail).joinToString(" \u00b7 ")
        AuditAction.CASH_ADDED, AuditAction.CASH_EDITED, AuditAction.CASH_DELETED ->
            AuditNames.amount(detail)?.let { Format.moneyLtr(it) } ?: detail
        // "4,555,225.00 (4,554,000.00 / 555.00)": the figure COUNTED, and the count it replaced when the day was closed again.
        AuditAction.DAY_CLOSED -> {
            val m = Regex("^(-?[0-9,.]+)(?: \\((-?[0-9,.]+) / (-?[0-9,.]+)\\))?\$").find(detail.trim())
            val now = m?.groupValues?.get(1)?.let { AuditNames.amount(it) }
            val was = m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let { AuditNames.amount(it) }
            when {
                now == null -> detail
                was == null -> word(W.audit_day_counted, Format.moneyLtr(now))
                else -> word(W.audit_day_counted_was, Format.moneyLtr(now), Format.moneyLtr(was))
            }
        }
        AuditAction.STOCK_ADJUSTED -> {
            // "<reason> <signed quantity>", written by KhataDao.adjustStock.
            val reason = detail.substringBefore(' ')
            val figure = detail.substringAfter(' ', "").toDoubleOrNull()
            val known = reason in AdjustReason.ALL || reason == AdjustReason.TRANSFER
            if (!known) detail
            else listOfNotNull(
                word(adjustReasonName(reason)),
                figure?.let { (if (it > 0) "+" else "") + com.innovation313.roshankhata.ui.Calc.trim(it) }
            ).joinToString(" ")
        }
        AuditAction.PIN_WAIT -> when {
            // The account whose PIN was guessed (Access.account): the owner, a staff row, or none named.
            detail == Access.OWNER -> word(W.access_owner)
            detail.startsWith("staff:") ->
                detail.removePrefix("staff:").toLongOrNull()?.let { id -> staff.firstOrNull { it.id == id }?.name }?.let { Format.name(it) }
            detail == "any" -> null
            else -> detail
        }
        AuditAction.BOOK_MERGED -> {
            // "entries 3 · cash 2 · sales 1 · other 0 · parties 1 · products 0 · renumbered 1" (BookMerge.detail).
            val n = Regex("([a-z]+) (\\d+)").findAll(detail).associate { it.groupValues[1] to it.groupValues[2].toInt() }
            val rows = listOf("entries", "cash", "sales", "other").sumOf { n[it] ?: 0 }
            if (n.isEmpty()) detail else word(W.merge_done, rows, n["renumbered"] ?: 0)
        }
        // The promised day as a date (PromiseLog keeps its millis).
        AuditAction.PROMISE_SET -> detail.toLongOrNull()?.let { Format.dateOnly(it) } ?: detail
        else -> detail
    }
}

private fun adjustReasonName(reason: String): String = when (reason) {
    AdjustReason.COUNT -> W.adjust_reason_count
    AdjustReason.DAMAGE -> W.adjust_reason_damage
    AdjustReason.EXPIRED -> W.adjust_reason_expired
    AdjustReason.THEFT -> W.adjust_reason_theft
    AdjustReason.TRANSFER -> W.adjust_reason_transfer
    else -> W.adjust_reason_other
}

/** A khata link's audit row, new (its own actions) or old (an EXPORT on "parties" whose detail starts with the menu's words). */
private fun isKhataLinkRow(a: AuditLog): Boolean =
    a.action == AuditAction.KHATA_LINK_SENT || a.action == AuditAction.KHATA_LINK_STOPPED ||
        (a.action == AuditAction.EXPORT && a.refTable == "parties")

@Composable
fun auditActionWord(a: AuditLog, names: AuditNames = AuditNames.None): String = when (a.action) {
    AuditAction.SALE_POSTED -> word(W.audit_sale_posted)
    AuditAction.SALE_VOIDED -> word(W.audit_sale_voided)
    AuditAction.SALE_HELD -> word(W.audit_sale_held)
    AuditAction.RETURN_POSTED -> word(W.audit_return_posted)
    AuditAction.RETURN_VOIDED -> word(W.audit_return_voided)
    AuditAction.DISCOUNT_OVERRIDE -> word(W.audit_discount_override)
    AuditAction.MANAGER_OVERRIDE -> word(W.audit_manager_override)
    AuditAction.PRICE_CHANGED -> word(W.audit_price_changed)
    AuditAction.SIGN_IN -> word(W.audit_sign_in)
    AuditAction.RESTORE -> word(W.audit_restore)
    AuditAction.EXPORT -> {
        // Older khata-link rows were written as an export of a customer's page; the menu's words in the detail tell which.
        val d = a.detail.orEmpty()
        when {
            a.refTable != "parties" -> word(W.audit_export)
            d.startsWith(word(W.clink_menu)) || d.startsWith("Send khata link") -> word(W.audit_khata_link_sent)
            d.startsWith(word(W.clink_stop)) || d.startsWith("Stop link") -> word(W.audit_khata_link_stopped)
            else -> word(W.audit_export)
        }
    }
    AuditAction.KHATA_LINK_SENT -> word(W.audit_khata_link_sent)
    AuditAction.KHATA_LINK_STOPPED -> word(W.audit_khata_link_stopped)
    AuditAction.STOCK_COUNT -> word(W.audit_stock_count)
    AuditAction.STOCK_ADJUSTED -> word(W.audit_stock_adjusted)
    AuditAction.ORDER_RECEIVED -> word(W.audit_order_received)
    AuditAction.OWNER_PIN_SET -> word(W.audit_owner_pin_set)
    AuditAction.OWNER_PIN_ADOPTED -> word(W.audit_owner_pin_adopted)
    AuditAction.COUNTER_ON -> word(W.audit_counter_on)
    AuditAction.COUNTER_OFF -> word(W.audit_counter_off)
    AuditAction.PIN_WAIT -> word(W.audit_pin_wait)
    AuditAction.SIGN_OUT -> word(W.audit_sign_out)
    AuditAction.SHIFT_OPENED -> word(W.audit_shift_opened)
    AuditAction.SHIFT_CLOSED -> word(W.audit_shift_closed)
    AuditAction.TRANSFER_OUT -> word(W.audit_transfer_out)
    AuditAction.TRANSFER_IN -> word(W.audit_transfer_in)
    AuditAction.BOOK_MERGED -> word(W.audit_book_merged)
    AuditAction.ENTRY_EDITED -> word(W.audit_entry_edited)
    AuditAction.ENTRY_DELETED -> word(W.audit_entry_deleted)
    AuditAction.ENTRY_ADDED -> word(W.audit_entry_added)
    // Money in / Money out by the row's direction when it can still be read, else the neutral words.
    AuditAction.CASH_ADDED -> when (names.cashIncome[a.refId]) {
        true -> word(W.audit_cash_in_added)
        false -> word(W.audit_cash_out_added)
        null -> word(W.audit_cash_added)
    }
    AuditAction.CASH_EDITED -> word(W.audit_cash_edited)
    AuditAction.CASH_DELETED -> when (names.cashIncome[a.refId]) {
        true -> word(W.audit_cash_in_deleted)
        false -> word(W.audit_cash_out_deleted)
        null -> word(W.audit_cash_deleted)
    }
    AuditAction.PARTY_DELETED -> word(W.audit_party_deleted)
    AuditAction.PARTY_MERGED -> word(W.audit_party_merged)
    AuditAction.PARTY_TYPE_CHANGED -> word(W.audit_party_type)
    AuditAction.CHEQUE_DELETED -> word(W.audit_cheque_deleted)
    AuditAction.BIN_RESTORED -> word(W.audit_bin_restored)
    AuditAction.BIN_PURGED -> word(W.audit_bin_purged)
    AuditAction.BIN_EMPTIED -> word(W.audit_bin_emptied)
    AuditAction.DAY_CLOSED -> word(W.audit_day_closed)
    AuditAction.IMPORT -> word(W.audit_import)
    AuditAction.BILL_DELETED -> word(W.audit_bill_deleted)
    AuditAction.BILL_EDITED -> word(W.audit_bill_edited)
    AuditAction.DOC_CONVERTED -> word(W.audit_doc_converted)
    AuditAction.DOC_ACCEPTED -> word(W.audit_doc_accepted)
    AuditAction.DOC_CANCELLED -> word(W.audit_doc_cancelled)
    AuditAction.DOC_DELETED -> word(W.audit_doc_deleted)
    AuditAction.PROMISE_SET -> word(W.audit_promise_set)
    // The book seal's switch (7 Nov 2026, the seal's review M9: audited, never synced).
    com.innovation313.roshankhata.data.BookSeals.AUDIT_ON -> word(W.audit_seal_on)
    com.innovation313.roshankhata.data.BookSeals.AUDIT_OFF -> word(W.audit_seal_off)
    else -> a.action
}

/**
 * What an audit row's ids stand for, read when the log is shown and never stored (8 Nov 2026, the owner's test: "Entry
 * added 800.00" named nobody and a cashbook row did not say in or out). [party]: a customer by id; [entryParty]: the
 * customer of a khata entry; [cashIncome]: which way a cashbook row went. A row whose target is gone simply has none.
 */
class AuditNames(
    val party: Map<Long?, String>,
    val entryParty: Map<Long?, String>,
    val cashIncome: Map<Long?, Boolean>
) {
    companion object {
        val None = AuditNames(emptyMap(), emptyMap(), emptyMap())

        /** "1,250.00" (what [com.innovation313.roshankhata.data.auditAmount] writes) as a number. */
        fun amount(text: String): Double? = text.trim().replace(",", "").toDoubleOrNull()

        suspend fun load(dao: KhataDao, rows: List<AuditLog>): AuditNames {
            val party = HashMap<Long?, String>()
            val entryParty = HashMap<Long?, String>()
            val cash = HashMap<Long?, Boolean>()
            for (a in rows) {
                val id = a.refId ?: continue
                runCatching {
                    when {
                        a.refTable == "parties" && (a.action == AuditAction.EXPORT || a.action == AuditAction.KHATA_LINK_SENT || a.action == AuditAction.KHATA_LINK_STOPPED) ->
                            if (id !in party) dao.getPartyName(id)?.let { party[id] = it }
                        a.refTable == "transactions" && a.action == AuditAction.ENTRY_ADDED ->
                            if (id !in entryParty) dao.getEntry(id)?.let { e -> dao.getPartyName(e.partyId)?.let { entryParty[id] = it } }
                        a.refTable == "cashbook" && (a.action == AuditAction.CASH_ADDED || a.action == AuditAction.CASH_DELETED) ->
                            if (id !in cash) dao.cashIsIncome(id)?.let { cash[id] = it }
                    }
                }
            }
            return AuditNames(party, entryParty, cash)
        }
    }
}
