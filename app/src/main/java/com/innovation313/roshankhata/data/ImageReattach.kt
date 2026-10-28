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
        // A failure is written down (23 Oct 2026, review R39): it used to be swallowed in silence.
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
        }.onFailure { CrashLog.record(context, it) }
    }

    /**
     * The plan of a move under way, beside the pictures: "original<TAB>target" a line. Named
     * [PLAN] while the first step runs (no picture has a new name yet) and [STAGED] once every
     * picture is "*.moving" and the second step may run.
     */
    private const val PLAN = "reattach.plan"
    private const val STAGED = "reattach.staged"
    private const val MOVING = ".moving"

    /**
     * A move a killed run left half done is finished (23 Oct 2026, review R39): every picture of
     * every shop's folder had been renamed "*.moving" in the first step, and nothing ever renamed
     * it back, so a restore cut short there hid every signature and customer photo for good.
     * Cheap when there is nothing to do; from the app's start and before every move. Never throws.
     */
    fun recover(context: Context) {
        runCatching {
            context.filesDir.listFiles { f ->
                f.isDirectory && (f.name.startsWith(EntrySignature.DIR) || f.name.startsWith("party_photos"))
            }?.forEach { finish(it) }
        }.onFailure { CrashLog.record(context, it) }
    }

    private fun readPlan(file: File): List<Pair<String, String>> = file.readLines().mapNotNull { l ->
        val parts = l.split('\t')
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
    }

    /**
     * Runs or finishes [dir]'s plan if one is there. A "*.moving" with no plan (a build before the
     * plan) cannot be told which row it now belongs to; it is set aside whole, never put on a row
     * that may not be its own.
     */
    @Synchronized
    internal fun finish(dir: File) {
        val plan = File(dir, PLAN)
        val staged = File(dir, STAGED)
        if (plan.isFile && !staged.exists()) {
            // Step one: while the plan is [PLAN] no picture has its new name yet, so a picture under
            // an original name is one still to stage.
            for ((original, _) in readPlan(plan)) {
                val f = File(dir, original)
                val tmp = File(dir, original + MOVING)
                if (f.isFile && !tmp.exists()) f.renameTo(tmp)
            }
            if (!plan.renameTo(staged)) throw java.io.IOException("reattach plan not staged")
        }
        plan.delete()
        if (staged.isFile) {
            // Step two: each staged picture to its new name (or set aside); one already moved is passed.
            for ((original, target) in readPlan(staged)) {
                val tmp = File(dir, original + MOVING)
                if (!tmp.isFile) continue
                val to = File(dir, target)
                to.parentFile?.mkdirs()
                tmp.renameTo(to)
            }
            staged.delete()
        }
        val left = dir.listFiles { f -> f.isFile && f.name.endsWith(MOVING) }.orEmpty()
        if (left.isNotEmpty()) {
            val aside = File(dir, "unmatched-" + System.currentTimeMillis()).apply { mkdirs() }
            for (f in left) f.renameTo(File(aside, f.name.removeSuffix(MOVING)))
        }
    }

    /**
     * Two steps, so a picture moving to an id another picture is leaving never overwrites it. The
     * plan is on disk before the first rename and goes after the last ([finish] completes it when
     * the run is killed between).
     */
    @Synchronized
    internal fun move(dir: File, pattern: Regex, name: String, newIdOf: (Long) -> Long?) {
        finish(dir)
        val files = dir.listFiles()?.mapNotNull { f -> pattern.find(f.name)?.groupValues?.get(1)?.toLongOrNull()?.let { f to it } } ?: return
        if (files.isEmpty()) return
        val unmatched = "unmatched-" + System.currentTimeMillis()
        val moves = files.map { (f, old) ->
            val newId = newIdOf(old)
            f.name to (if (newId != null) String.format(java.util.Locale.ROOT, name, newId) else unmatched + "/" + f.name)
        }
        // Written whole and renamed into place: a plan is either all there or not there.
        val planTmp = File(dir, PLAN + ".tmp")
        planTmp.writeText(moves.joinToString("") { (from, to) -> from + "\t" + to + "\n" })
        if (!planTmp.renameTo(File(dir, PLAN))) { planTmp.delete(); throw java.io.IOException("reattach plan not written") }
        finish(dir)
    }
}
