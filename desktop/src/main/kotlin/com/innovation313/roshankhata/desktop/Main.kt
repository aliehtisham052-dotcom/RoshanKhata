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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.screens.AddPartyForm
import com.innovation313.roshankhata.screens.EntryForm
import com.innovation313.roshankhata.screens.PartyLedgerScreen
import com.innovation313.roshankhata.screens.RecycleBinScreen
import com.innovation313.roshankhata.screens.balanceColour
import com.innovation313.roshankhata.screens.AboutScreen
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.ui.BidiPort
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.AppLanguages
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
    // The app's language: the owner's choice (app.properties), else the computer's.
    val appPrefs = remember { AppPrefs(File(home, "app.properties")) }
    var language by remember { mutableStateOf(appPrefs.language() ?: Locale.getDefault().toLanguageTag()) }
    val locale = remember(language) { Locale.forLanguageTag(language).also { Locale.setDefault(it) } }
    val words = remember(locale) { desktopWords(locale) }
    val rtl = BidiPort.isRtlLocale(locale)
    Window(
        onCloseRequest = { db.close(); exitApplication() },
        title = words.text(W.app_name),
        icon = painterResource("roshan_khata_icon.png"),
        // Opened filling the screen: a laptop's 768 rows otherwise cut off the entry buttons.
        state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1100.dp, height = 740.dp)
    ) {
        RoshanTheme {
            ProvideWords(words) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Book(
                        dao = db.khataDao(),
                        load = { db.khataDao().partiesWithBalanceOnce() },
                        shopName = { settings.businessName() },
                        settings = settings,
                        restore = { data -> restore(data, db, settings) },
                        backup = { file -> backup(file, db, settings) },
                        onLanguage = { tag -> appPrefs.setLanguage(tag); language = tag },
                        rtl = rtl,
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

/** The whole book to [file] in the phone's format, streamed; returns the file's size. */
private suspend fun backup(
    file: File,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    settings: FileShopSettings
): Long = withContext(Dispatchers.IO) {
    file.bufferedWriter(Charsets.UTF_8).use {
        BackupCodec.exportTo(db.khataDao(), settings.businessName(), BackupCodec.profileToJson(settings), it)
    }
    file.length()
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
    dao: KhataDao,
    load: suspend () -> List<PartyWithBalance>,
    shopName: () -> String?,
    settings: FileShopSettings,
    restore: suspend (BackupCodec.ParsedBackup) -> Unit,
    backup: suspend (File) -> Long,
    onLanguage: (String) -> Unit,
    rtl: Boolean,
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
    var selected by remember { mutableStateOf<Long?>(null) }
    var entryWay by remember { mutableStateOf<Boolean?>(null) }
    var addParty by remember { mutableStateOf(false) }
    var backedUp by remember { mutableStateOf<String?>(null) }
    var choosingLanguage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var deleting by remember { mutableStateOf<LedgerEntry?>(null) }
    var halves by remember { mutableStateOf<Pair<LedgerEntry, LedgerEntry>?>(null) }
    var bin by remember { mutableStateOf(false) }
    var deletingParty by remember { mutableStateOf<PartyWithBalance?>(null) }
    var section by remember { mutableStateOf(Section.KHATA) }
    var shop by remember { mutableStateOf(shopName()) }

    LaunchedEffect(reload) { parties = withContext(Dispatchers.IO) { load() } }

    Column(Modifier.fillMaxSize().background(c.pageBg)) {
        // The green header, as on the phone: the shop's name and the actions.
        Row(
            Modifier.fillMaxWidth().background(c.sectionKhata).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shop ?: word(W.app_name),
                Modifier.weight(1f),
                color = c.onDark, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            HeaderButton(word(W.add_party)) { addParty = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.backup_now)) {
                val dialog = FileDialog(null as Frame?, words.text(W.save_backup), FileDialog.SAVE)
                // The phone's file name and .txt (WhatsApp sends .txt; the content is JSON).
                dialog.file = "RoshanKhata_Backup_" +
                    java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH).format(java.util.Date()) + ".txt"
                dialog.isVisible = true
                val name = dialog.file ?: return@HeaderButton
                val file = File(dialog.directory, name)
                scope.launch {
                    backedUp = runCatching { backup(file) }
                        .map { size -> words.text(W.backup_saved_at, file.absolutePath, (size / 1024).toInt()) }
                        .getOrElse { words.text(W.backup_failed) }
                }
            }
            Spacer(Modifier.width(8.dp))
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
            HeaderButton(word(W.recycle_bin)) { bin = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.language)) { choosingLanguage = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.about_us)) { about = true }
        }

        Row(Modifier.fillMaxSize()) {
            SectionRail(section, { section = it })
            when (section) {
                Section.CASHBOOK -> Box(Modifier.weight(1f)) { CashbookPane(dao) }
                Section.CHEQUES -> Box(Modifier.weight(1f)) { ChequesPane(dao, parties, words) { reload++ } }
                Section.SHOP -> Box(Modifier.weight(1f)) { ShopDetailsPane(settings) { shop = shopName() } }
                Section.KHATA -> Row(Modifier.weight(1f).fillMaxSize()) {
                    // The book on the left, as the phone's Khata list.
                    Column(Modifier.weight(0.42f).fillMaxSize()) {
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
                        LazyColumn(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                            items(shown, key = { it.id }) { p ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (p.id == selected) c.pageBg else c.surface)
                                        .clickable { selected = p.id }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(Format.name(p.name), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        p.phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 13.sp) }
                                    }
                                    Text(
                                        Format.customerBalance(p.balance),
                                        color = balanceColour(p.balance),
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                HorizontalDivider(color = c.pageBg)
                            }
                        }
                    }
                    // The chosen customer's page on the right: the shared ledger screen.
                    val party = parties.firstOrNull { it.id == selected }
                    Box(Modifier.weight(0.58f).fillMaxSize()) {
                        if (party != null) {
                            val entries by remember(party.id, reload) { dao.observeEntries(party.id) }.collectAsState(emptyList())
                            PartyLedgerScreen(
                                name = party.name,
                                phone = party.phone,
                                balance = party.balance,
                                entries = entries.sortedByDescending { it.timestamp },
                                onGave = { entryWay = true },
                                onGot = { entryWay = false },
                                onEntry = { editing = it },
                                onDeleteParty = { deletingParty = party },
                                onStatement = {
                                    scope.launch {
                                        val rows = withContext(Dispatchers.IO) { dao.latestEntriesWithItems(party.id, Int.MAX_VALUE) }
                                        StatementPrint.show(
                                            StatementPrint(words, rtl, shopName() ?: words.text(W.app_name), party.name, party.phone, rows),
                                            words.text(W.pdf_stmt_title, party.name)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
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
                            shop = shopName()
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
    val way = entryWay
    val forParty = parties.firstOrNull { it.id == selected }
    if (way != null && forParty != null) {
        DialogWindow(
            onCloseRequest = { entryWay = null },
            title = word(if (way) W.you_gave else W.you_got),
            state = rememberDialogState(width = 460.dp, height = 430.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryForm(
                    partyName = forParty.name,
                    isGiven = way,
                    onSave = { amount, note ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // The phone's own write: numbered RK-, all or nothing.
                                dao.insertEntryWithItems(
                                    LedgerEntry(partyId = forParty.id, amount = amount, isGiven = way, note = note, entryNumber = ""),
                                    emptyList()
                                )
                            }
                            entryWay = null
                            reload++
                        }
                    },
                    onCancel = { entryWay = null }
                )
            }
        }
    }
    editing?.let { e ->
        DialogWindow(
            onCloseRequest = { editing = null },
            title = word(W.edit),
            state = rememberDialogState(width = 460.dp, height = 430.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryForm(
                    partyName = forParty?.name.orEmpty(),
                    isGiven = e.isGiven,
                    initialAmount = e.amount,
                    initialNote = e.note,
                    onSave = { amount, note ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // The phone's edit: the entry and its goods lines, all or nothing.
                                dao.updateEntryWithItems(e.copy(amount = amount, note = note), dao.itemsOfEntry(e.id))
                            }
                            editing = null
                            reload++
                        }
                    },
                    onCancel = { editing = null },
                    onDelete = { deleting = e; editing = null }
                )
            }
        }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(word(W.delete_entry_title)) },
            text = { Text(word(W.delete_entry_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            // Half of a cash sale: ask about the other half, as the phone does.
                            val pair = withContext(Dispatchers.IO) {
                                e.pairedEntryId?.let { dao.getEntry(it) }?.takeIf { !it.isDeleted }
                            }
                            if (pair != null) {
                                halves = e to pair
                            } else {
                                withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id) }
                                reload++
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
    halves?.let { (e, pair) ->
        AlertDialog(
            onDismissRequest = { halves = null },
            title = { Text(word(W.delete_pair_title)) },
            text = { Text(word(W.delete_pair_message, pair.entryNumber)) },
            confirmButton = {
                TextButton({
                    halves = null
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id); dao.softDeleteEntry(pair.id) }
                        reload++
                    }
                }) { Text(word(W.delete_pair_both)) }
            },
            dismissButton = {
                TextButton({
                    halves = null
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id) }
                        reload++
                    }
                }) { Text(word(W.delete_pair_one)) }
            }
        )
    }
    deletingParty?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingParty = null },
            title = { Text(word(W.delete_party_title)) },
            text = { Text(word(W.delete_party_confirm, p.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingParty = null
                        scope.launch {
                            // The phone's delete: the customer and their entries, one timestamp, one transaction.
                            withContext(Dispatchers.IO) { dao.softDeleteParties(listOf(p.id)) }
                            selected = null
                            reload++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deletingParty = null }) { Text(word(W.cancel)) } }
        )
    }
    if (bin) {
        DialogWindow(
            onCloseRequest = { bin = false },
            title = word(W.recycle_bin),
            state = rememberDialogState(width = 560.dp, height = 640.dp)
        ) {
            val binParties by remember(reload) { dao.observeDeletedParties() }.collectAsState(emptyList())
            val binEntries by remember(reload) { dao.observeDeletedEntries() }.collectAsState(emptyList())
            RecycleBinScreen(
                parties = binParties,
                entries = binEntries,
                partyName = { id -> parties.firstOrNull { it.id == id }?.name.orEmpty() },
                onRestoreParty = { p ->
                    scope.launch {
                        // The phone's restore: the customer and the entries binned with them.
                        withContext(Dispatchers.IO) {
                            dao.restoreParty(p.id)
                            p.deletedAt?.let { dao.restoreEntriesOfParty(p.id, it) }
                        }
                        reload++
                    }
                },
                onRestoreEntry = { e ->
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.restoreEntry(e.id) }
                        reload++
                    }
                }
            )
        }
    }
    if (addParty) {
        DialogWindow(
            onCloseRequest = { addParty = false },
            title = word(W.add_party),
            state = rememberDialogState(width = 460.dp, height = 400.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                AddPartyForm(
                    onSave = { name, phone, isCustomer ->
                        scope.launch {
                            val id = withContext(Dispatchers.IO) {
                                dao.insertParty(Party(name = name, phone = phone, isCustomer = isCustomer))
                            }
                            addParty = false
                            reload++
                            selected = id
                            section = Section.KHATA
                        }
                    },
                    onCancel = { addParty = false }
                )
            }
        }
    }
    backedUp?.let { text ->
        AlertDialog(
            onDismissRequest = { backedUp = null },
            title = { Text(word(W.backup_done_title)) },
            confirmButton = { TextButton({ backedUp = null }) { Text(word(W.ok)) } },
            text = { Text(text) }
        )
    }
    if (choosingLanguage) {
        DialogWindow(
            onCloseRequest = { choosingLanguage = false },
            title = word(W.language),
            state = rememberDialogState(width = 360.dp, height = 560.dp)
        ) {
            Column(Modifier.fillMaxSize().background(c.surface).padding(12.dp)) {
                AppLanguages.ALL.forEach { lang ->
                    Text(
                        lang.nativeName,
                        Modifier.fillMaxWidth()
                            .clickable { choosingLanguage = false; onLanguage(lang.tag) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = c.ink, fontSize = 18.sp
                    )
                    HorizontalDivider(color = c.pageBg)
                }
            }
        }
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
