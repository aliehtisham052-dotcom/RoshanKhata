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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily copies on the computer (KMP Phase 7bb), the phone's Snapshots: the
 * open business's book copied once a day, the last seven kept, each copy
 * read back and checked before it counts. Written in the backup format
 * (BackupCodec), so a copy is also a file the phone can restore.
 */
object DailyCopies {
    private const val KEEP = 7

    fun dir(home: File, businessId: Long) = File(home, "daily/b$businessId")

    fun list(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /**
     * Today's copy, when there is none yet: written to a temporary file,
     * read back whole, and only then given its name. A copy that does not
     * read back is thrown away, never kept as if it were good.
     */
    suspend fun makeToday(dao: KhataDao, settings: FileShopSettings, dir: File, label: String? = null) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
        val name = if (label == null) "$day.txt" else "$day-$label.txt"
        val target = File(dir, name)
        if (label == null && target.isFile) return@withContext
        val tmp = File(dir, "$name.tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use {
            BackupCodec.exportTo(dao, settings.businessName(), BackupCodec.profileToJson(settings), it)
        }
        if (BackupCodec.parseFile(tmp).first is BackupCodec.ImportResult.Ok) {
            target.delete()
            tmp.renameTo(target)
        } else {
            tmp.delete()
        }
        list(dir).drop(KEEP).forEach { it.delete() }
    }
}

@Composable
fun DailyCopiesPane(dao: KhataDao, settings: FileShopSettings, dir: File, onRestored: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var copies by remember { mutableStateOf<List<File>>(emptyList()) }
    var asking by remember { mutableStateOf<File?>(null) }
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(version) { copies = withContext(Dispatchers.IO) { DailyCopies.list(dir) } }
    val restoredTitle = word(W.snapshot_restored_title)
    val damagedTitle = word(W.snapshot_unhealthy_title)
    val damagedBody = word(W.snapshot_unhealthy_body)
    val failed = word(W.snapshot_restore_failed)

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaneTitle(word(W.snapshots_title), word(W.snapshots_subtitle))
        if (copies.isEmpty()) {
            Text(word(W.snapshots_empty), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp))) {
                items(copies, key = { it.name }) { f ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Format.dateTime(f.lastModified()), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(word(W.snapshot_size_kb, (f.length() / 1024).toInt()), color = c.textMuted, fontSize = 12.sp)
                        }
                        TextButton({ asking = f }) { Text(word(W.snapshot_restore_action), color = c.brandGreenText) }
                    }
                    HorizontalDivider(color = c.pageBg)
                }
            }
        }
    }

    asking?.let { f ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(word(W.snapshot_restore_title)) },
            text = { Text(word(W.snapshot_restore_warning, Format.dateTime(f.lastModified()))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = null
                        scope.launch {
                            val (result, data) = withContext(Dispatchers.IO) { BackupCodec.parseFile(f) }
                            notice = if (result !is BackupCodec.ImportResult.Ok || data == null) {
                                damagedTitle to damagedBody
                            } else runCatching {
                                // Today's book is copied and kept first, so going back can be undone.
                                DailyCopies.makeToday(dao, settings, dir, label = "before-restore-" + System.currentTimeMillis())
                                withContext(Dispatchers.IO) {
                                    BackupCodec.restoreLedger(dao, data)
                                    data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
                                }
                                onRestored()
                                restoredTitle to ""
                            }.getOrElse { damagedTitle to failed }
                            version++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.snapshot_restore_action)) }
            },
            dismissButton = { TextButton({ asking = null }) { Text(word(W.cancel)) } }
        )
    }
    notice?.let { (title, body) ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text(title) }, text = { if (body.isNotEmpty()) Text(body) },
            confirmButton = { TextButton({ notice = null }) { Text(word(W.ok)) } })
    }
}
