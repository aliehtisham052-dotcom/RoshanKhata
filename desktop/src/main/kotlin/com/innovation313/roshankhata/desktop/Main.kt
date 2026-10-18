package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.AboutScreen
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.ui.BidiPort
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.ProvideWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.desktopWords
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale

/**
 * Roshan Khata on the computer, first version (KMP Phase 6a): the book's
 * customers with their balances, search, a phone backup restored into it,
 * and About. Everything it shows comes from the shared module — the
 * database, the backup format, the money format, the words.
 */
fun main() = application {
    val home = DesktopDatabase.home()
    val db = remember { DesktopDatabase.open(File(home, "khata.db")) }
    val settings = remember { FileShopSettings(File(home, "shop.properties")).also { it.refreshDisplay() } }
    val words = remember { desktopWords(Locale.getDefault()) }
    val rtl = BidiPort.isRtlLocale(Locale.getDefault())
    Window(
        onCloseRequest = { db.close(); exitApplication() },
        title = words.text(W.app_name),
        icon = painterResource("roshan_khata_icon.png"),
        state = rememberWindowState(width = 1100.dp, height = 740.dp)
    ) {
        RoshanTheme {
            ProvideWords(words) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Book(
                        load = { db.khataDao().partiesWithBalanceOnce() },
                        shopName = { settings.businessName() },
                        restore = { data -> restore(data, db, settings) },
                        words = words
                    )
                }
            }
        }
    }
}

/** What a chosen file came to: a parsed book to confirm, or why it was refused. */
private sealed interface Picked {
    data class Ready(val file: File, val data: BackupCodec.ParsedBackup, val ok: BackupCodec.ImportResult.Ok) : Picked
    data class Refused(val why: String) : Picked
}

/** The ledger in one transaction, then the shop's details — the phone's order. */
private suspend fun restore(
    data: BackupCodec.ParsedBackup,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    settings: FileShopSettings
) = withContext(Dispatchers.IO) {
    BackupCodec.restoreLedger(db.khataDao(), data)
    data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
}

private fun reasonOf(failed: BackupCodec.ImportResult.Failed, words: Words): String {
    val text = when (failed.why) {
        BackupCodec.ImportResult.Why.UNREADABLE -> words.text(W.backup_err_unreadable)
        BackupCodec.ImportResult.Why.NOT_OURS -> words.text(W.backup_err_not_ours)
        BackupCodec.ImportResult.Why.TOO_NEW -> words.text(W.backup_err_too_new)
        BackupCodec.ImportResult.Why.INCOMPLETE -> words.text(W.backup_err_incomplete, failed.count)
    }
    return if (failed.detail.isNullOrBlank()) text else "$text\n(${failed.detail})"
}

@Composable
private fun Book(
    load: suspend () -> List<PartyWithBalance>,
    shopName: () -> String?,
    restore: suspend (BackupCodec.ParsedBackup) -> Unit,
    words: Words
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var parties by remember { mutableStateOf<List<PartyWithBalance>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }

    LaunchedEffect(reload) { parties = withContext(Dispatchers.IO) { load() } }

    Column(Modifier.fillMaxSize().background(c.pageBg)) {
        // The green header, as on the phone: the shop's name and the two actions.
        Row(
            Modifier.fillMaxWidth().background(c.sectionKhata).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shopName() ?: word(W.app_name),
                Modifier.weight(1f),
                color = c.onDark, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            HeaderButton(word(W.restore_from_file)) {
                val dialog = FileDialog(null as Frame?, words.text(W.restore_from_file), FileDialog.LOAD)
                dialog.isVisible = true
                val name = dialog.file ?: return@HeaderButton
                val file = File(dialog.directory, name)
                scope.launch {
                    val (result, data) = withContext(Dispatchers.IO) { BackupCodec.parseFile(file) }
                    picked = when {
                        result is BackupCodec.ImportResult.Ok && data != null -> Picked.Ready(file, data, result)
                        result is BackupCodec.ImportResult.Failed -> Picked.Refused(reasonOf(result, words))
                        else -> Picked.Refused(words.text(W.backup_err_unreadable))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.about_us)) { about = true }
        }

        // Totals: what the shop will get, and what it will give.
        val get = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
        val give = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Total(word(W.you_will_get), Format.money(get), c.owedToMe, Modifier.weight(1f))
            Total(word(W.you_will_give), Format.money(give), c.iOwe, Modifier.weight(1f))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(word(W.search_parties)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        val q = query.trim().lowercase()
        val shown = parties.filter { NameSearch.matches(it.name, it.phone, q) }
        Text(
            plural(WP.customer_count, shown.size, shown.size),
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = c.textMuted, fontSize = 13.sp
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            items(shown, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(p.name), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        p.phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 13.sp) }
                    }
                    Text(
                        Format.customerBalance(p.balance),
                        color = when {
                            Money.isPositive(p.balance) -> c.owedToMe
                            Money.isNegative(p.balance) -> c.iOwe
                            else -> c.textMuted
                        },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = c.pageBg)
            }
        }
    }

    when (val p = picked) {
        is Picked.Refused -> AlertDialog(
            onDismissRequest = { picked = null },
            confirmButton = { TextButton({ picked = null }) { Text(word(W.ok)) } },
            text = { Text(word(W.restore_failed, p.why)) }
        )
        is Picked.Ready -> AlertDialog(
            onDismissRequest = { if (!busy) picked = null },
            title = { Text(word(W.restore_warning_title)) },
            text = {
                Text(
                    word(
                        W.restore_warning_message,
                        p.ok.parties, p.ok.entries, p.ok.cheques, p.ok.cash, p.ok.plans, p.ok.bills, p.ok.invoices
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            restore(p.data)
                            busy = false
                            picked = null
                            done = true
                            reload++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.restore_replace)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { picked = null }) { Text(word(W.cancel)) } }
        )
        null -> {}
    }
    if (done) {
        AlertDialog(
            onDismissRequest = { done = false },
            confirmButton = { TextButton({ done = false }) { Text(word(W.ok)) } },
            text = { Text(word(W.restore_file_no_images)) }
        )
    }
    if (about) {
        DialogWindow(
            onCloseRequest = { about = false },
            title = word(W.about_us),
            state = rememberDialogState(width = 520.dp, height = 680.dp)
        ) {
            AboutScreen(
                icon = painterResource("roshan_khata_icon.png"),
                versionName = System.getProperty("jpackage.app-version") ?: "1.0.0",
                supportEmail = "innovation313.support@gmail.com",
                onPrivacyPolicy = {
                    runCatching {
                        java.awt.Desktop.getDesktop()
                            .browse(java.net.URI("https://aliehtisham052-dotcom.github.io/RoshanKhata/privacy-policy.html"))
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Total(label: String, amount: String, color: Color, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier.background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(label, color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(amount, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
