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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.AuditLog
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/** The audit log as the phone shows it (POS Phase 5): newest first, read-only. */
@Composable
fun AuditScreen(dao: KhataDao, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    val rows by remember { dao.observeAudit(500) }.collectAsState(emptyList())
    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(word(W.audit_empty), color = c.textMuted) }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(rows, key = { it.id }) { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(auditActionWord(a), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(Format.dateTime(a.timestamp), color = c.textMuted, fontSize = 12.sp)
                }
                Text(
                    listOfNotNull(word(W.audit_by, a.user?.let { if (it == Access.OWNER) word(W.access_owner) else Format.name(it) } ?: word(W.access_owner)), a.detail).joinToString(" · "),
                    color = c.inkSoft, fontSize = 13.sp
                )
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
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
    else -> a.action
}
