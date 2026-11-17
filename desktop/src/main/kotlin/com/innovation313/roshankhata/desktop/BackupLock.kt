package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.BackupPassword
import com.innovation313.roshankhata.data.BackupSeal
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.DeviceBackupLock
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The backup password on the computer (17 Nov 2026, the owner's decision; the format is the shared [BackupSeal]).
 * Optional: with none set, Back up now and the outside folder's copies are written exactly as before.
 *
 * Kept in its own file beside the books ([file]: "backup-password.dat" in %APPDATA%\Roshan Khata), wrapped by Windows'
 * DPAPI for the signed-in Windows user ([DeviceBackupLock]) — never in app.properties, never in a book, a daily copy
 * or anything synced. One password for every business on this computer. The daily copies on this computer's own disk
 * stay plain, as the phone's own safety copies do: what leaves the computer is what the password locks.
 */
object DeskBackupPassword {
    fun file(home: File): File = File(home, "backup-password.dat")

    private fun keep(home: File): BackupPassword.Keep = object : BackupPassword.Keep {
        override fun read(): String? = file(home).takeIf { it.isFile }?.readText(Charsets.US_ASCII)?.trim()
        override fun write(value: String?) {
            val f = file(home)
            if (value == null) {
                if (f.exists() && !f.delete()) throw java.io.IOException("backup password not removed")
                return
            }
            home.mkdirs()
            // Written beside it and moved over, so a power cut leaves the old one or the new one, never half of one.
            val tmp = File(home, f.name + ".tmp")
            tmp.writeText(value, Charsets.US_ASCII)
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun state(home: File = DesktopDatabase.home()): BackupPassword.State = BackupPassword.state(keep(home), DeviceBackupLock)

    fun save(password: String, home: File = DesktopDatabase.home()): BackupPassword.Saved =
        BackupPassword.save(keep(home), DeviceBackupLock, password)

    fun remove(home: File = DesktopDatabase.home()) = BackupPassword.remove(keep(home))

    /** What to try on a locked backup: [typed], then this computer's own. */
    fun toTry(typed: String?, home: File = DesktopDatabase.home()): List<String> = listOfNotNull(
        typed?.takeIf { it.isNotBlank() },
        (state(home) as? BackupPassword.State.Set)?.password
    )

    /** The password to lock with now, null when none is set; [BackupPassword.Unavailable] when set and unreadable. */
    fun forSealing(home: File = DesktopDatabase.home()): String? = BackupPassword.forSealing(state(home))
}

/**
 * The question a locked backup asks (17 Nov 2026): any page awaits [ask]; the one [BackupPasswordHost] of the window
 * shows it. Null when cancelled or when no window hosts it — the reader then says what it found.
 */
object BackupPasswordPrompt {
    class Ask(val wrong: Boolean, val answer: CompletableDeferred<String?>)

    internal val asking = mutableStateOf<Ask?>(null)
    @Volatile internal var hosts = 0

    suspend fun ask(wrong: Boolean): String? {
        if (hosts == 0) return null
        val a = Ask(wrong, CompletableDeferred())
        asking.value?.answer?.complete(null)
        asking.value = a
        return try { a.answer.await() } finally { if (asking.value === a) asking.value = null }
    }
}

/**
 * A backup file read for a restore: a locked one asks its password (this computer's own is tried first); a wrong one
 * asks again, saying so, at most [BackupPassword.MAX_TRIES] times — then it is WRONG_PASSWORD ("did not open with the
 * passwords tried") instead of asking until Cancel (the review, 17 Nov 2026). Cancel is LOCKED. Nothing is restored
 * from what this returns unless it is Ok.
 */
suspend fun readBackup(file: File, home: File = DesktopDatabase.home()): Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?> {
    var typed: String? = null
    var tries = 0
    while (true) {
        val result = withContext(Dispatchers.IO) { BackupCodec.parseFile(file, DeskBackupPassword.toTry(typed, home)) }
        val why = (result.first as? BackupCodec.ImportResult.Failed)?.why
        if (why != BackupCodec.ImportResult.Why.LOCKED && why != BackupCodec.ImportResult.Why.WRONG_PASSWORD) return result
        when (BackupPassword.next(tries)) {
            BackupPassword.Next.GIVE_UP -> return BackupCodec.ImportResult.Failed(BackupCodec.ImportResult.Why.WRONG_PASSWORD) to null
            BackupPassword.Next.ASK, BackupPassword.Next.ASK_AGAIN -> {
                typed = BackupPasswordPrompt.ask(wrong = tries > 0)
                    ?: return BackupCodec.ImportResult.Failed(BackupCodec.ImportResult.Why.LOCKED) to null
                tries++
            }
        }
    }
}

/** The one place in the window where a locked backup's question is shown. */
@Composable
fun BackupPasswordHost() {
    DisposableEffect(Unit) {
        BackupPasswordPrompt.hosts++
        onDispose {
            BackupPasswordPrompt.hosts--
            BackupPasswordPrompt.asking.value?.answer?.complete(null)
        }
    }
    val a = BackupPasswordPrompt.asking.value ?: return
    val c = LocalRoshanColors.current
    var typed by remember(a) { mutableStateOf("") }
    fun done(v: String?) {
        if (BackupPasswordPrompt.asking.value === a) BackupPasswordPrompt.asking.value = null
        a.answer.complete(v)
    }
    val title = word(W.backup_pw_ask_title)
    AppDialog(onCloseRequest = { done(null) }, onPreviewKeyEvent = escCloses { done(null) }, title = title,
        state = rememberDialogState(width = fitW(480.dp), height = fitH(330.dp))) {
        DialogFrame(title, onClose = { done(null) }, footer = {
            FluentButton(word(W.cancel), { done(null) }, kind = ButtonKind.STANDARD)
            FluentButton(word(W.ok), { if (typed.isNotBlank()) done(typed) }, kind = ButtonKind.ACCENT, enabled = typed.isNotBlank())
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (a.wrong) Text(word(W.backup_pw_wrong), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(word(W.backup_pw_ask_body), color = c.ink, fontSize = 14.sp)
                FluentField(
                    typed, { typed = it }, Modifier.fillMaxWidth(), password = true,
                    keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) done(typed) })
                )
            }
        }
    }
}

/**
 * The password's card on the Daily copies page: where it stands, and Set / Change / Remove — the owner's alone
 * ([Access.Permission.BACKUP] is owner-only), each written in Who did what (never the password).
 */
@Composable
fun BackupPasswordCard(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<BackupPassword.State?>(null) }
    LaunchedEffect(version) { state = withContext(Dispatchers.IO) { DeskBackupPassword.state() } }
    var setting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val title = word(W.backup_pw_title)
    val st = state
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(word(W.backup_pw_explain), color = c.textMuted, fontSize = 13.sp)
        when (st) {
            null -> Unit
            BackupPassword.State.None -> Text(word(W.backup_pw_status_off), color = c.ink, fontSize = 13.sp)
            is BackupPassword.State.Set -> Text(word(W.backup_pw_status_on), color = c.brandGreenText, fontSize = 13.sp)
            is BackupPassword.State.Unreadable -> Text(word(W.backup_pw_status_unreadable), color = c.owedToMe, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentButton(word(if (st is BackupPassword.State.Set) W.backup_pw_change else W.backup_pw_set), {
                Gates.need(Access.Permission.BACKUP) { setting = true }
            }, kind = ButtonKind.STANDARD)
            if (st != null && st !is BackupPassword.State.None) FluentButton(word(W.backup_pw_remove), {
                Gates.need(Access.Permission.BACKUP) { removing = true }
            }, kind = ButtonKind.DANGER)
        }
    }
    val savedWord = word(W.backup_pw_saved)
    val noLockWord = word(W.backup_pw_no_lock)
    val failedWord = word(W.save_failed_not_saved)
    val removedWord = word(W.backup_pw_removed)
    if (setting) NewBackupPasswordDialog(onClose = { setting = false }) { typed ->
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val had = DeskBackupPassword.state() !is BackupPassword.State.None
                val saved = runCatching { DeskBackupPassword.save(typed) }.getOrNull()
                if (saved == BackupPassword.Saved.OK) runCatching {
                    dao.log(if (had) BackupPassword.AUDIT_CHANGED else BackupPassword.AUDIT_SET, Access.userName)
                }
                saved
            }
            setting = false
            notice = when (outcome) {
                BackupPassword.Saved.OK -> savedWord
                BackupPassword.Saved.NO_LOCK -> noLockWord
                else -> failedWord
            }
            version++
        }
    }
    if (removing) AskDialog(title = word(W.backup_pw_remove), text = word(W.backup_pw_remove_body), confirm = word(W.backup_pw_remove),
        onConfirm = {
            removing = false
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        DeskBackupPassword.remove()
                        runCatching { dao.log(BackupPassword.AUDIT_REMOVED, Access.userName) }
                    }.isSuccess
                }
                notice = if (ok) removedWord else failedWord
                version++
            }
        },
        onDismiss = { removing = false }, danger = true)
    notice?.let { TellDialog(title, it, { notice = null }) }
}

/**
 * Typed twice, its rule and the warning said before it is kept (a forgotten password opens nothing, not even for us),
 * and a tick that the owner understood. Stays open over a mistake, saying which.
 */
@Composable
private fun NewBackupPasswordDialog(onClose: () -> Unit, onSet: (String) -> Unit) {
    val c = LocalRoshanColors.current
    var first by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mismatch = word(W.backup_pw_mismatch)
    val tooShort = word(W.backup_pw_too_short)
    val tooLong = word(W.backup_pw_too_long)
    fun set() {
        if (busy || !agreed) return
        problem = when {
            BackupSeal.normalize(first) != BackupSeal.normalize(again) -> mismatch
            else -> when (BackupPassword.check(first)) {
                BackupPassword.Saved.TOO_SHORT -> tooShort
                BackupPassword.Saved.TOO_LONG -> tooLong
                else -> null
            }
        }
        if (problem == null) { busy = true; onSet(first) }
    }
    val title = word(W.backup_pw_title)
    AppDialog(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title,
        state = rememberDialogState(width = fitW(560.dp), height = fitH(560.dp)), dirty = { first.isNotEmpty() || again.isNotEmpty() }) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.backup_pw_set), { set() }, kind = ButtonKind.ACCENT, enabled = agreed && !busy && first.isNotEmpty())
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FluentField(first, { first = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.backup_pw_new_hint), password = true)
                FluentField(again, { again = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.backup_pw_again_hint), password = true,
                    keyboardActions = KeyboardActions(onDone = { set() }))
                Text(word(W.backup_pw_rule), color = c.textMuted, fontSize = 13.sp)
                problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Text(word(W.backup_pw_warning), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.press { agreed = !agreed }) {
                    Checkbox(agreed, onCheckedChange = { agreed = it })
                    Text(word(W.backup_pw_agree), color = c.ink, fontSize = 13.sp)
                }
            }
        }
    }
}
