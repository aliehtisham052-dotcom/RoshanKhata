package com.innovation313.roshankhata.desktop

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
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.AuditLog
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who is at the counter (POS Phase 5): the owner, or a staff member with a
 * role and a PIN. Signing in is remembered until the app closes; the sign-in
 * is one audit row.
 */
@Composable
fun SignInDialog(dao: KhataDao, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var staff by remember { mutableStateOf<List<Staff>>(emptyList()) }
    LaunchedEffect(Unit) { staff = withContext(Dispatchers.IO) { dao.loginStaffOnce() } }
    var chosen by remember { mutableStateOf<Staff?>(null) }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val title = word(W.access_who)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 440.dp, height = 460.dp)) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.access_sign_in), {
                val s = chosen
                if (s == null) { Access.signOut(); onClose(); return@FluentButton }
                if (Access.pinMatches(s, pin)) {
                    Access.signIn(s)
                    scope.launch { withContext(Dispatchers.IO) { dao.log(AuditAction.SIGN_IN, s.name) } }
                    onClose()
                } else wrong = true
            }, kind = ButtonKind.ACCENT, enabled = chosen == null || pin.isNotEmpty())
        }) {
            Row(Modifier.fillMaxWidth().press { chosen = null; wrong = false }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(word(W.access_owner), 32, 0)
                Text(word(W.access_owner), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (chosen == null) Tag(word(W.access_sign_in), Look.accent, Look.accentTint)
            }
            Rule()
            staff.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().press { chosen = s; wrong = false }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(s.name, 32, i + 1)
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(s.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(roleWord(s.role), color = c.textMuted, fontSize = 12.sp)
                    }
                    if (chosen?.id == s.id) Tag(word(W.access_sign_in), Look.accent, Look.accentTint)
                }
                Rule()
            }
            if (chosen != null) {
                FluentField(pin, { pin = it.filter { ch -> ch.isDigit() }.take(8); wrong = false }, Modifier.fillMaxWidth().padding(top = 8.dp), label = word(W.staff_pin), help = if (wrong) word(W.access_wrong_pin) else null)
            }
        }
    }
}

/**
 * A manager's PIN over a cashier's shoulder, for one thing. [allowed] gets
 * who allowed it (the audit row says so); nothing happens on a wrong PIN.
 */
@Composable
fun OverrideDialog(dao: KhataDao, what: String, ownerPin: ((String) -> Boolean)?, onClose: () -> Unit, allowed: (by: String) -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var managers by remember { mutableStateOf<List<Staff>>(emptyList()) }
    LaunchedEffect(Unit) { managers = withContext(Dispatchers.IO) { dao.loginStaffOnce().filter { it.role == Role.MANAGER } } }
    var chosen by remember { mutableStateOf<Long?>(null) }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val title = word(W.access_override_title)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 440.dp, height = 420.dp)) {
        DialogFrame(title, subtitle = what, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.access_sign_in), {
                val by = Access.override(managers, chosen, pin, ownerPin)
                if (by == null) { wrong = true; return@FluentButton }
                scope.launch { withContext(Dispatchers.IO) { dao.log(AuditAction.MANAGER_OVERRIDE, by, detail = what) } }
                onClose(); allowed(by)
            }, kind = ButtonKind.ACCENT, enabled = pin.isNotEmpty())
        }) {
            Text(word(W.access_override_help), color = c.inkSoft, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ownerPin != null) FluentPill(word(W.access_owner), chosen == null) { chosen = null }
                managers.forEach { m -> FluentPill(Format.name(m.name), chosen == m.id) { chosen = m.id } }
            }
            FluentField(pin, { pin = it.filter { ch -> ch.isDigit() }.take(8); wrong = false }, Modifier.fillMaxWidth().padding(top = 8.dp), label = word(W.staff_pin), help = if (wrong) word(W.access_wrong_pin) else null)
        }
    }
}

@Composable
fun roleWord(role: String?): String = word(
    when (role) {
        Role.MANAGER -> W.staff_role_manager
        Role.CASHIER -> W.staff_role_cashier
        else -> W.staff_role_none
    }
)

/** The audit log (POS Phase 5): who did what, newest first. Read-only. */
@Composable
fun AuditPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val rows by remember { dao.observeAudit(500) }.collectAsState(emptyList())
    FluentPage(word(W.audit_title), word(W.audit_subtitle), scroll = false) {
        if (rows.isEmpty()) EmptyState("ic_tile_insights", word(W.audit_empty), null, Modifier.weight(1f))
        else ListCard(Modifier.weight(1f)) {
            WeightedHead(listOf(word(W.date_label) to 3f, word(W.audit_title) to 3f, word(W.staff_name) to 2f, word(W.note_optional) to 4f))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(rows, key = { it.id }) { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Format.dateTime(a.timestamp), Modifier.weight(3f), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        Text(auditWord(a), Modifier.weight(3f), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.user?.let { if (it == Access.OWNER) word(W.access_owner) else Format.name(it) } ?: word(W.access_owner), Modifier.weight(2f), color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.detail.orEmpty(), Modifier.weight(4f), color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Rule()
                }
            }
        }
    }
}

@Composable
fun auditWord(a: AuditLog): String = when (a.action) {
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
