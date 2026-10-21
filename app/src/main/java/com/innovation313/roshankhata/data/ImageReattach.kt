package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File

/**
 * Pictures kept by row id, put back on the right rows after a Replace restore
 * (21 Oct 2026, the critical review).
 *
 * A customer's signature is the file sig_<entry id>.png and a customer's photo
 * party_<customer id>.jpg. A restore brings the copy's rows with the COPY's
 * ids, so a signature left by its old number could sit on a different entry —
 * worst of all, on a new entry written next week that happens to get that
 * number. Before the restore the book's ids are read with what identifies the
 * row (an entry's uid; a customer's name and phone); after it, each picture
 * is renamed to the id its row now has. A picture whose row is not in the
 * restored book is kept, not deleted, in an "unmatched" folder beside the
 * others, so nothing the owner took is ever lost by a restore.
 */
object ImageReattach {

    /** What identifies the book's rows before a restore. */
    class Before(val entryUid: Map<Long, String>, val partyKey: Map<Long, String>)

    private fun key(name: String?, phone: String?): String =
        name.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ") + "|" + phone.orEmpty().filter(Char::isDigit).takeLast(10)

    private suspend fun parties(dao: KhataDao): List<Party> {
        val out = ArrayList<Party>()
        var after = 0L
        while (true) {
            val page = dao.partiesPageForBackup(after, 2_000)
            out += page
            if (page.size < 2_000) break
            after = page.last().id
        }
        return out
    }

    /** Read before the restore. Never throws: no reading, nothing moved afterwards. */
    suspend fun before(dao: KhataDao): Before? = runCatching {
        Before(
            entryUid = dao.entryUids().mapNotNull { r -> r.uid?.let { r.id to it } }.toMap(),
            partyKey = parties(dao).associate { it.id to key(it.name, it.phone) }
        )
    }.getOrNull()

    /** After the restore: every picture renamed to its row's new id, or set aside. */
    suspend fun after(context: Context, dao: KhataDao, before: Before) {
        runCatching {
            val uidToNew = dao.entryUids().mapNotNull { r -> r.uid?.let { it to r.id } }.toMap()
            val newParties = parties(dao)
            // A key two customers share is not a match for either.
            val keyToNew = newParties.groupBy { key(it.name, it.phone) }.filterValues { it.size == 1 }.mapValues { it.value.single().id }
            move(EntrySignature.folder(context), Regex("^sig_(\\d+)\\.png$"), "sig_%d.png") { old ->
                before.entryUid[old]?.let { uidToNew[it] }
            }
            move(PartyPhoto.folder(context), Regex("^party_(\\d+)\\.jpg$"), "party_%d.jpg") { old ->
                before.partyKey[old]?.let { keyToNew[it] }
            }
            PartyPhoto.dropCaches()
        }
    }

    /** Two steps, so a picture moving to an id another picture is leaving never overwrites it. */
    private fun move(dir: File, pattern: Regex, name: String, newIdOf: (Long) -> Long?) {
        val files = dir.listFiles()?.mapNotNull { f -> pattern.find(f.name)?.groupValues?.get(1)?.toLongOrNull()?.let { f to it } } ?: return
        if (files.isEmpty()) return
        val unmatched = File(dir, "unmatched-" + System.currentTimeMillis())
        val staged = files.map { (f, old) ->
            val tmp = File(dir, f.name + ".moving")
            f.renameTo(tmp)
            Triple(tmp, f.name, newIdOf(old))
        }
        for ((tmp, oldName, newId) in staged) {
            if (newId != null) {
                tmp.renameTo(File(dir, String.format(java.util.Locale.ROOT, name, newId)))
            } else {
                unmatched.mkdirs()
                tmp.renameTo(File(unmatched, oldName))
            }
        }
    }
}
