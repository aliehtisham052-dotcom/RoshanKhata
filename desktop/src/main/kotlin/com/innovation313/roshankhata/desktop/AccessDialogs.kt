package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.AuditChain
import com.innovation313.roshankhata.data.AuditLog
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PinHash
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.auditActionWord
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who is at the counter (POS Phase 5; the owner's PIN, counter mode and the
 * wait after wrong PINs since 21 Oct 2026 — the critical review).
 *
 * Every PIN typed here goes through [Access.tryPin]: five wrong in a row and
 * the computer waits (30 s, then twice as long each time), and the log says
 * so. The owner's PIN is the book's ([Access.ownerPin]); a forgotten one is
 * replaced with the recovery code shown when it was set.
 */

/** A PIN box: digits only, hidden, Enter to go. */
@Composable
private fun PinBox(value: String, onChange: (String) -> Unit, label: String, help: String?, onEnter: () -> Unit) {
    FluentField(
        value, { typed -> onChange(typed.filter { it in '0'..'9' }.take(8)) },
        Modifier.fillMaxWidth(), label = label, help = help, password = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        keyboardActions = KeyboardActions(onDone = { onEnter() })
    )
}

/** The words for a PIN that was refused: wrong, or the wait still running. */
private fun pinProblem(words: com.innovation313.roshankhata.words.Words, result: Boolean?): String? = when (result) {
    null -> words.text(W.lock_pin_wait, Access.waitSeconds())
    false -> words.text(W.access_wrong_pin)
    true -> null
}

/**
 * [pin] checked away from the window's thread (PBKDF2 takes a moment), the
 * wait after wrong PINs in force; a miss that starts a wait is logged.
 */
private suspend fun checkPin(dao: KhataDao, pin: String, matches: (String) -> Boolean): Boolean? =
    withContext(Dispatchers.Default) {
        var waited = false
        val r = Access.tryPin(pin, onWait = { waited = true }, check = matches)
        if (waited) withContext(Dispatchers.IO) { dao.log(AuditAction.PIN_WAIT, Access.userName) }
        r
    }

/**
 * The sign-in form, as a dialog ([SignInDialog]) or as the whole window of a
 * locked counter ([CounterGate]). The owner's row takes the owner's PIN once
 * the book has one; on the owner's own computer, a book with none lets the
 * owner straight back in.
 */
@Composable
private fun SignInFrame(dao: KhataDao, gate: Boolean, onDone: () -> Unit, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    var staff by remember { mutableStateOf<List<Staff>>(emptyList()) }
    var owner by remember { mutableStateOf<Access.OwnerPin?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { staff = dao.loginStaffOnce(); owner = Access.ownerPin(dao) }
    }
    var chosen by remember { mutableStateOf<Staff?>(null) }
    var pin by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var recovering by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    if (recovering) {
        OwnerPinFrame(dao, OwnerPinStart.RECOVER, onClose = { recovering = false }) {
            recovering = false
            Access.signInOwner()
            onDone()
        }
        return
    }
    val needsPin = chosen != null || owner != null
    fun go() {
        if (busy) return
        val s = chosen
        val o = owner
        if (s == null && o == null) {
            // A counter is never without an owner PIN (it is refused until one is set).
            if (Access.counterMode) { problem = words.text(W.access_owner_pin_needed); return }
            Access.signOut(); onDone(); return
        }
        busy = true
        scope.launch {
            val r = checkPin(dao, pin) { typed -> if (s == null) o!!.matches(typed) else Access.pinMatches(s, typed) }
            busy = false
            problem = pinProblem(words, r)
            if (r == true) {
                if (s == null) Access.signInOwner() else Access.signIn(s)
                withContext(Dispatchers.IO) { dao.log(AuditAction.SIGN_IN, s?.name ?: Access.OWNER) }
                onDone()
            } else pin = ""
        }
    }
    DialogFrame(
        word(W.access_who), subtitle = if (gate) word(W.access_counter_locked) else null, onClose = onClose,
        footerStart = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!gate && (Access.current != null || Access.ownerIn)) FluentButton(word(W.access_sign_out), {
                    scope.launch { withContext(Dispatchers.IO) { dao.log(AuditAction.SIGN_OUT, Access.userName ?: Access.OWNER) } }
                    Access.signOut(); onDone()
                }, kind = ButtonKind.SUBTLE)
                if (chosen == null && owner != null) FluentButton(word(W.access_forgot_pin), { recovering = true }, kind = ButtonKind.SUBTLE)
            }
        },
        footer = {
            FluentButton(word(if (gate) W.access_close_app else W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.access_sign_in), ::go, kind = ButtonKind.ACCENT, enabled = !busy && (!needsPin || pin.isNotEmpty()))
        }
    ) {
        Row(Modifier.fillMaxWidth().press { chosen = null; pin = ""; problem = null }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(word(W.access_owner), 32, 0)
            Text(word(W.access_owner), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (chosen == null) Tag(word(W.access_sign_in), Look.accent, Look.accentTint)
        }
        Rule()
        staff.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().press { chosen = s; pin = ""; problem = null }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(s.name, 32, i + 1)
                Column(Modifier.weight(1f)) {
                    Text(Format.name(s.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(roleWord(s.role), color = c.textMuted, fontSize = 12.sp)
                }
                if (chosen?.id == s.id) Tag(word(W.access_sign_in), Look.accent, Look.accentTint)
            }
            Rule()
        }
        if (needsPin) PinBox(pin, { pin = it; problem = null }, word(W.staff_pin), problem, ::go)
        else problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
    }
}

/** Who is at the counter: the owner, or a staff member with a role and a PIN. Each sign-in is one audit row. */
@Composable
fun SignInDialog(dao: KhataDao, onClose: () -> Unit) {
    val title = word(W.access_who)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 460.dp, height = 520.dp)) {
        SignInFrame(dao, gate = false, onDone = onClose, onClose = onClose)
    }
}

/**
 * A counter computer with nobody signed in (Access.locked): this is the whole
 * window until someone signs in. [onClose] closes the app.
 */
@Composable
fun CounterGate(dao: KhataDao, shop: String?, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    Box(Modifier.fillMaxSize().background(Look.base), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(shop ?: word(W.app_name), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(width = 460.dp, height = 540.dp).card()) {
                SignInFrame(dao, gate = true, onDone = {}, onClose = onClose)
            }
        }
    }
}

/**
 * A manager's or the owner's PIN over a cashier's shoulder, for one thing.
 * [ownerOnly]: only the owner may allow it (settings, backups, staff).
 * [allowed] gets who allowed it (the audit row says so).
 */
@Composable
fun OverrideDialog(dao: KhataDao, what: String, ownerOnly: Boolean = false, onClose: () -> Unit, allowed: (by: String) -> Unit) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    var managers by remember { mutableStateOf<List<Staff>>(emptyList()) }
    var owner by remember { mutableStateOf<Access.OwnerPin?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            managers = if (ownerOnly) emptyList() else dao.loginStaffOnce().filter { it.role == Role.MANAGER }
            owner = Access.ownerPin(dao)
        }
        loaded = true
    }
    // Who is typing: null = the owner.
    var chosen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(loaded) { if (loaded && owner == null) chosen = managers.firstOrNull()?.id }
    var pin by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val nobody = loaded && owner == null && managers.isEmpty()
    fun go() {
        if (busy || pin.isEmpty() || nobody) return
        busy = true
        val o = owner
        scope.launch {
            val r = checkPin(dao, pin) { typed -> Access.override(managers, chosen, typed, o?.let { p -> { t: String -> p.matches(t) } }) != null }
            busy = false
            problem = pinProblem(words, r)
            if (r == true) {
                val by = if (chosen == null) Access.OWNER else managers.first { it.id == chosen }.name
                withContext(Dispatchers.IO) { dao.log(AuditAction.MANAGER_OVERRIDE, by, detail = what) }
                onClose(); allowed(by)
            } else pin = ""
        }
    }
    val title = word(if (ownerOnly) W.access_owner_needed_title else W.access_override_title)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 460.dp, height = 420.dp)) {
        DialogFrame(title, subtitle = what, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.access_sign_in), ::go, kind = ButtonKind.ACCENT, enabled = !busy && !nobody && pin.isNotEmpty())
        }) {
            if (nobody) {
                Text(word(W.access_denied), color = c.owedToMe, fontSize = 14.sp)
                Text(word(W.access_owner_pin_needed), color = c.inkSoft, fontSize = 13.sp)
                return@DialogFrame
            }
            Text(word(if (ownerOnly) W.access_owner_needed_help else W.access_override_help), color = c.inkSoft, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (owner != null) FluentPill(word(W.access_owner), chosen == null) { chosen = null; pin = ""; problem = null }
                managers.forEach { m -> FluentPill(Format.name(m.name), chosen == m.id) { chosen = m.id; pin = ""; problem = null } }
            }
            PinBox(pin, { pin = it; problem = null }, word(W.staff_pin), problem, ::go)
        }
    }
}

/** Where the owner's PIN form starts. */
enum class OwnerPinStart {
    /** The book has none yet: a new one, twice. */
    NEW,
    /** Change: the current PIN first. */
    CHANGE,
    /** Forgotten: the recovery code first. */
    RECOVER
}

/**
 * The owner's PIN: typed twice, kept as a hash in the book, and a new
 * recovery code shown once at the end. [onSet] after the code was seen.
 */
@Composable
private fun OwnerPinFrame(dao: KhataDao, start: OwnerPinStart, onClose: () -> Unit, onSet: () -> Unit) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    // 0 = prove (current PIN or recovery code), 1 = new PIN, 2 = the recovery code to write down.
    var step by remember { mutableStateOf(if (start == OwnerPinStart.NEW) 1 else 0) }
    var recover by remember { mutableStateOf(start == OwnerPinStart.RECOVER) }
    var proof by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun prove() {
        if (busy || proof.isBlank()) return
        busy = true
        scope.launch {
            val r: Boolean? = if (recover) {
                if (Access.waitMillis() > 0L) null
                else {
                    val right = withContext(Dispatchers.Default) { Access.recoveryMatches(dao, proof) }
                    if (Access.pinTried(right)) withContext(Dispatchers.IO) { dao.log(AuditAction.PIN_WAIT, Access.userName) }
                    right
                }
            } else {
                val o = withContext(Dispatchers.IO) { Access.ownerPin(dao) }
                if (o == null) true else checkPin(dao, proof) { o.matches(it) }
            }
            busy = false
            problem = when (r) {
                null -> words.text(W.lock_pin_wait, Access.waitSeconds())
                false -> words.text(if (recover) W.access_recovery_wrong else W.access_wrong_pin)
                true -> null
            }
            if (r == true) { step = 1; proof = "" } else proof = ""
        }
    }
    fun save() {
        if (busy) return
        when {
            !PinHash.valid(first) -> { problem = words.text(W.staff_pin_invalid); return }
            first != second -> { problem = words.text(W.lock_pin_mismatch); second = ""; return }
        }
        busy = true
        scope.launch {
            code = withContext(Dispatchers.IO) { Access.setOwnerPin(dao, first) }
            busy = false; problem = null; step = 2
        }
    }
    DialogFrame(
        word(if (step == 2) W.access_recovery_title else W.access_owner_pin), onClose = onClose,
        footerStart = { if (step == 0 && !recover) FluentButton(word(W.access_forgot_pin), { recover = true; proof = ""; problem = null }, kind = ButtonKind.SUBTLE) },
        footer = {
            when (step) {
                0 -> { FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD); FluentButton(word(W.ok), ::prove, kind = ButtonKind.ACCENT, enabled = !busy && proof.isNotBlank()) }
                1 -> { FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD); FluentButton(word(W.save), ::save, kind = ButtonKind.ACCENT, enabled = !busy && first.isNotEmpty() && second.isNotEmpty()) }
                else -> FluentButton(word(W.access_recovery_written), onSet, kind = ButtonKind.ACCENT)
            }
        }
    ) {
        when (step) {
            0 -> if (recover) {
                Text(word(W.access_recovery_ask), color = c.inkSoft, fontSize = 13.sp)
                FluentField(proof, { proof = it.uppercase().take(12); problem = null }, Modifier.fillMaxWidth(), label = word(W.access_recovery_code), help = problem,
                    keyboardActions = KeyboardActions(onDone = { prove() }))
            } else {
                PinBox(proof, { proof = it; problem = null }, word(W.access_owner_pin_current), problem, ::prove)
            }
            1 -> {
                Text(word(W.access_owner_pin_help), color = c.inkSoft, fontSize = 13.sp)
                PinBox(first, { first = it; problem = null }, word(W.lock_pin_new), null) {}
                PinBox(second, { second = it; problem = null }, word(W.lock_pin_again), problem, ::save)
            }
            else -> {
                Text(word(W.access_recovery_help), color = c.inkSoft, fontSize = 13.sp)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(code, Modifier.fillMaxWidth(), color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The owner's PIN, from the Staff page: set it, or change it after the current one (or the recovery code). */
@Composable
fun OwnerPinDialog(dao: KhataDao, hasPin: Boolean, onClose: () -> Unit) {
    val title = word(W.access_owner_pin)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 480.dp, height = 460.dp)) {
        OwnerPinFrame(dao, if (hasPin) OwnerPinStart.CHANGE else OwnerPinStart.NEW, onClose = onClose, onSet = onClose)
    }
}

/**
 * Counter mode on this computer, from the Staff page. On needs the book's
 * owner PIN (set here first if there is none); off needs it too. One audit
 * row either way.
 */
@Composable
fun CounterModeDialog(dao: KhataDao, turnOn: Boolean, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf<Access.OwnerPin?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    LaunchedEffect(reloadTick) { owner = withContext(Dispatchers.IO) { Access.ownerPin(dao) }; loaded = true }
    var settingPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val title = word(W.access_counter_mode)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 480.dp, height = 460.dp)) {
        if (settingPin) {
            OwnerPinFrame(dao, OwnerPinStart.NEW, onClose = { settingPin = false }) { settingPin = false; reloadTick++ }
            return@DialogWindow
        }
        fun go() {
            val o = owner ?: return
            if (busy || pin.isEmpty()) return
            busy = true
            scope.launch {
                val r = checkPin(dao, pin) { o.matches(it) }
                busy = false
                problem = pinProblem(words, r)
                if (r == true) {
                    Access.setCounterMode(turnOn)
                    withContext(Dispatchers.IO) { dao.log(if (turnOn) AuditAction.COUNTER_ON else AuditAction.COUNTER_OFF, Access.OWNER) }
                    onClose()
                } else pin = ""
            }
        }
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            if (loaded && owner == null) FluentButton(word(W.access_owner_pin_set), { settingPin = true }, kind = ButtonKind.ACCENT)
            else FluentButton(word(if (turnOn) W.access_counter_turn_on else W.access_counter_turn_off), ::go, kind = ButtonKind.ACCENT, enabled = !busy && pin.isNotEmpty())
        }) {
            Text(word(if (turnOn) W.access_counter_mode_help else W.access_counter_off_help), color = c.inkSoft, fontSize = 13.sp)
            if (loaded && owner == null) Text(word(W.access_owner_pin_needed), color = c.owedToMe, fontSize = 13.sp)
            else PinBox(pin, { pin = it; problem = null }, word(W.access_owner_pin), problem, ::go)
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

/** The audit log (POS Phase 5): who did what, newest first, and whether its seal holds. Read-only. */
@Composable
fun AuditPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val rows by remember { dao.observeAudit(500) }.collectAsState(emptyList())
    var seal by remember { mutableStateOf<AuditChain.Result?>(null) }
    LaunchedEffect(rows.size) { seal = withContext(Dispatchers.IO) { AuditChain.verify(dao.auditInOrder()) } }
    FluentPage(word(W.audit_title), word(W.audit_subtitle), scroll = false) {
        seal?.let { s ->
            Text(
                if (s.intact) word(W.audit_seal_ok, s.sealed) else word(W.audit_seal_broken, s.brokenAt ?: 0L),
                color = if (s.intact) c.iOwe else c.owedToMe, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
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

/** An audit row's action in words: the shared screen's own list, so both devices name it alike. */
@Composable
fun auditWord(a: AuditLog): String = auditActionWord(a)
