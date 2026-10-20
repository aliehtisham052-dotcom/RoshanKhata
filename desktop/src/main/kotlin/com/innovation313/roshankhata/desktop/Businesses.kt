package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.BusinessDeleteSummary
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Several businesses on one computer (KMP Phase 7z), the phone's Businesses
 * rules: each shop is its own book (its own database file and its own shop
 * settings), one is open at a time, and switching closes the old book
 * before the new one opens.
 *
 * Business 1 is the book the computer already had (khata.db,
 * shop.properties) and can never be deleted; others are khata_b<id>.db and
 * shop_b<id>.properties. The open business cannot be deleted either: switch
 * away first. The registry lives in businesses.properties, written whole
 * through a temporary file, so a crash mid-write leaves the old one.
 */
class Businesses(private val home: File) {

    data class Business(val id: Long, val name: String?)

    private val file = File(home, "businesses.properties")
    private val props = Properties().apply { if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) } }

    fun dbFile(id: Long) = File(home, if (id == 1L) "khata.db" else "khata_b$id.db")
    fun settingsFile(id: Long) = File(home, if (id == 1L) "shop.properties" else "shop_b$id.properties")

    /** Every business, Business 1 first; Business 1 is always there. */
    fun list(): List<Business> {
        val ids = (props.getProperty(KEY_IDS)?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList())
        return (listOf(1L) + ids.filter { it != 1L }).distinct().map { Business(it, props.getProperty("name.$it")?.takeIf { n -> n.isNotBlank() }) }
    }

    fun activeId(): Long = props.getProperty(KEY_ACTIVE)?.toLongOrNull()?.takeIf { id -> list().any { it.id == id } } ?: 1L

    @Synchronized
    fun create(name: String): Business {
        val id = (list().maxOf { it.id }) + 1
        props.setProperty(KEY_IDS, (list().map { it.id } + id).joinToString(","))
        props.setProperty("name.$id", name.trim())
        save()
        return Business(id, name.trim())
    }

    @Synchronized
    fun rename(id: Long, name: String) { props.setProperty("name.$id", name.trim()); save() }

    @Synchronized
    fun switchTo(id: Long) { props.setProperty(KEY_ACTIVE, id.toString()); save() }

    fun canDelete(id: Long): Boolean = id != 1L && id != activeId()

    /** The business's book and settings removed from this computer, then its row. Backup files are not touched. */
    @Synchronized
    fun delete(id: Long) {
        check(canDelete(id)) { "Business $id cannot be deleted (it is Business 1 or the open one)" }
        val db = dbFile(id)
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"), File(db.path + "-journal"), settingsFile(id)).forEach { it.delete() }
        props.setProperty(KEY_IDS, list().map { it.id }.filter { it != id && it != 1L }.joinToString(","))
        props.remove("name.$id")
        save()
    }

    private fun save() {
        home.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata businesses") }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_ACTIVE = "active"
    }
}

/**
 * The businesses on this computer: open one, add one, rename, and delete
 * one that is not Business 1 and not open — after its own numbers are shown
 * and its name typed back, as on the phone.
 */
@Composable
fun BusinessesDialog(businesses: Businesses, active: Long, onSwitch: (Long) -> Unit, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val list = remember(version) { businesses.list() }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Businesses.Business?>(null) }
    var deleting by remember { mutableStateOf<Pair<Businesses.Business, BusinessDeleteSummary>?>(null) }
    val needName = word(W.business_name_needed)
    var problem by remember { mutableStateOf<String?>(null) }

    fun shown(b: Businesses.Business): String =
        FileShopSettings(businesses.settingsFile(b.id)).businessName() ?: b.name ?: "Roshan Khata"

    DialogWindow(
        onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose),
        title = word(W.businesses_title),
        state = rememberDialogState(width = 520.dp, height = 640.dp)
    ) {
        Column(
            Modifier.fillMaxSize().background(c.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            list.forEach { b ->
                val on = b.id == active
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (on) c.brandGreen.copy(alpha = 0.25f) else c.pageBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(shown(b), Modifier.weight(1f), color = c.ink,
                        fontWeight = FontWeight.Bold)
                    if (!on) FluentButton(word(W.business_switch_open), { onSwitch(b.id) }, kind = ButtonKind.SUBTLE)
                    TextButton({ renaming = b }) {
                        Text(word(W.business_rename), color = c.ink)
                    }
                    if (businesses.canDelete(b.id)) TextButton({
                        scope.launch {
                            val summary = withContext(Dispatchers.IO) {
                                // Opened on its own and closed again: never the open book's instance.
                                val db = DesktopDatabase.open(businesses.dbFile(b.id))
                                try { db.khataDao().deleteSummary() } finally { db.close() }
                            }
                            deleting = b to summary
                        }
                    }) {
                        Text(word(W.business_delete), color = c.owedToMe)
                    }
                }
            }
            Outlined(
                newName, { newName = it; problem = null }, singleLine = true,
                label = { Text(word(W.business_name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            problem?.let { Text(it, color = c.owedToMe) }
            FillButton(word(W.business_add), c.brandGreen) {
                if (newName.isBlank()) { problem = needName; return@FillButton }
                val b = businesses.create(newName)
                // The new shop's own name in its own settings, so statements print it.
                FileShopSettings(businesses.settingsFile(b.id)).setBusinessName(newName)
                newName = ""
                onSwitch(b.id)
            }
        }
    }

    renaming?.let { b ->
        var name by remember(b) { mutableStateOf(shown(b)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(word(W.business_rename)) },
            text = { Outlined(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    businesses.rename(b.id, name)
                    FileShopSettings(businesses.settingsFile(b.id)).setBusinessName(name)
                    renaming = null; version++
                }) { Text(word(W.save)) }
            },
            dismissButton = { TextButton({ renaming = null }) { Text(word(W.cancel)) } }
        )
    }

    deleting?.let { (b, summary) ->
        val name = shown(b)
        var typed by remember(b) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(word(W.business_delete_confirm_title, name)) },
            text = {
                Column {
                    Text(
                        word(W.business_delete_confirm_body,
                            summary.partyCount, Format.money(summary.netBalance))
                    )
                    Outlined(typed, { typed = it }, singleLine = true,
                        label = { Text(word(W.business_delete_type_hint, name)) })
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == name.trim(), onClick = {
                    businesses.delete(b.id)
                    deleting = null; version++
                }) { Text(word(W.delete_forever), color = c.owedToMe) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
}
