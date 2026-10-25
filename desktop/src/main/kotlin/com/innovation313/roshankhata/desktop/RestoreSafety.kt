package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.RestoreCheck
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restore safety on the computer (21 Oct 2026, the critical review): before
 * a copy replaces the book, what would be lost is worked out
 * ([RestoreCheck]) and said in figures, and Replace waits for a tick that
 * says the owner understands. The book itself is kept first as a daily copy
 * (the callers), so a restore of the wrong copy is undone from Daily copies.
 */

/** What restoring [data] would take away from this book; null while it is worked out. */
@Composable
fun rememberLoss(dao: KhataDao, data: BackupCodec.ParsedBackup): RestoreCheck.Loss? {
    var loss by remember(data) { mutableStateOf<RestoreCheck.Loss?>(null) }
    LaunchedEffect(data) {
        loss = withContext(Dispatchers.IO) {
            // A check that cannot run must not stop a restore the owner needs (a damaged book is
            // exactly when one is restored) — but it is not "nothing lost" either: it says it could not
            // count, and the owner ticks before replacing. The copy below still keeps the book first.
            runCatching { RestoreCheck.of(dao, data) }.getOrNull() ?: RestoreCheck.UNKNOWN
        }
    }
    return loss
}

/** True when Replace may be pressed: the loss is known, and either nothing is lost or the owner ticked. */
fun mayReplace(loss: RestoreCheck.Loss?, agreed: Boolean): Boolean = loss != null && (loss.none || agreed)

/** The loss in lines, and the tick, inside a restore's confirmation. Nothing when nothing is lost. */
@Composable
fun LossNote(loss: RestoreCheck.Loss?, agreed: Boolean, onAgree: (Boolean) -> Unit) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    if (loss == null) { Text(word(W.restore_loss_counting), color = c.textMuted, fontSize = 13.sp); return }
    if (loss.none) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(word(W.restore_loss_title), color = c.owedToMe, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(word(W.restore_loss_intro), color = c.ink, fontSize = 13.sp)
        RestoreCheck.lines(loss, words) { Format.dateTime(it) }.forEach { Text("•  $it", color = c.ink, fontSize = 13.sp) }
        Text(word(W.restore_loss_kept), color = c.inkSoft, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.press { onAgree(!agreed) }) {
            Checkbox(agreed, onCheckedChange = onAgree)
            Text(word(W.restore_loss_agree), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * A copy merged in instead of replacing the book (21 Oct 2026, BookMerge):
 * what it would add, in figures, before anything is written; [keepFirst]
 * keeps this book as a daily copy; then only the rows it lacks come in, in
 * one transaction. [onDone] gets the words to tell the owner.
 */
@Composable
fun MergeDialog(
    dao: KhataDao, data: BackupCodec.ParsedBackup, shopHere: String?,
    keepFirst: suspend () -> Unit, onDone: (String) -> Unit, onClose: () -> Unit
) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var plan by remember(data) { mutableStateOf<com.innovation313.roshankhata.data.BookMerge.Plan?>(null) }
    var refused by remember(data) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(data) {
        withContext(Dispatchers.IO) {
            runCatching { com.innovation313.roshankhata.data.BookMerge.plan(dao, data, shopHere) }
                .onSuccess { plan = it }
                .onFailure { e ->
                    refused = if (e is com.innovation313.roshankhata.data.BookMerge.Refused) words.text(com.innovation313.roshankhata.data.BookMerge.refusalWord(e))
                    else failText(words, e)
                }
        }
    }
    val p = plan
    val close = { if (!busy) onClose() }
    val title = word(W.merge_title)
    // In the app's frame (Marhala 4); nothing closes it while the merge runs.
    AppDialog(onCloseRequest = close, title = title, state = androidx.compose.ui.window.rememberDialogState(width = fitW(560.dp), height = fitH(480.dp))) {
        DialogFrame(title, onClose = close, footer = {
            FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD, enabled = !busy)
            if (p != null && !p.none && refused == null) FluentButton(word(W.merge_button), {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { keepFirst(); dao.mergeBook(data, shopHere) } }
                    busy = false
                    result.onSuccess { done -> onDone(words.text(W.merge_done, done.rows, done.renumbered)) }
                        .onFailure { onDone(failText(words, it)) }
                    onClose()
                }
            }, kind = ButtonKind.ACCENT, enabled = !busy)
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    refused != null -> Text(refused!!, color = c.owedToMe, fontSize = 14.sp)
                    p == null -> Text(word(W.restore_loss_counting), color = c.textMuted, fontSize = 13.sp)
                    p.none -> Text(word(W.merge_nothing), color = c.ink, fontSize = 14.sp)
                    else -> {
                        Text(word(W.merge_help), color = c.inkSoft, fontSize = 13.sp)
                        com.innovation313.roshankhata.data.BookMerge.lines(p, words).forEach { Text("•  $it", color = c.ink, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
