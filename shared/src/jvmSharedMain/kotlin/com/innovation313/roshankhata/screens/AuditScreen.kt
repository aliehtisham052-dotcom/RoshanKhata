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
    LaunchedEffect(rows.size) { seal = withContext(Dispatchers.IO) { AuditChain.verify(dao.auditInOrder()) } }
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
                    Text(auditActionWord(a), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(Format.dateTime(a.timestamp), color = c.textMuted, fontSize = 12.sp)
                }
                Text(
                    listOfNotNull(word(W.audit_by, a.user?.let { if (it == Access.OWNER) word(W.access_owner) else Format.name(it) } ?: word(W.access_owner)), auditDetail(a, staff)).joinToString(" · "),
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
fun auditDetail(a: AuditLog, staff: List<com.innovation313.roshankhata.data.Staff>): String? {
    val detail = a.detail?.takeIf { it.isNotBlank() } ?: return null
    return when (a.action) {
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

@Composable
fun auditActionWord(a: AuditLog): String = when (a.action) {
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
    AuditAction.EXPORT -> word(W.audit_export)
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
    AuditAction.CASH_ADDED -> word(W.audit_cash_added)
    AuditAction.CASH_EDITED -> word(W.audit_cash_edited)
    AuditAction.CASH_DELETED -> word(W.audit_cash_deleted)
    AuditAction.PARTY_DELETED -> word(W.audit_party_deleted)
    AuditAction.DAY_CLOSED -> word(W.audit_day_closed)
    AuditAction.IMPORT -> word(W.audit_import)
    AuditAction.BILL_DELETED -> word(W.audit_bill_deleted)
    AuditAction.BILL_EDITED -> word(W.audit_bill_edited)
    AuditAction.DOC_CONVERTED -> word(W.audit_doc_converted)
    AuditAction.DOC_ACCEPTED -> word(W.audit_doc_accepted)
    AuditAction.DOC_CANCELLED -> word(W.audit_doc_cancelled)
    else -> a.action
}
