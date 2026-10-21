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
            // exactly when one is restored): it reports nothing lost, and the copy below still keeps the book.
            runCatching { RestoreCheck.of(dao, data) }.getOrNull() ?: RestoreCheck.Loss(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null)
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
