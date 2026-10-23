package com.innovation313.roshankhata.data

import java.io.File
import java.io.IOException

/**
 * A device giving its live book to a new phone from a code it shows (23 Oct 2026, the owner: the computer has no
 * camera, so until now it could never be the one that gives a shop's book — only a phone could, by scanning the
 * joiner's code). The computer shows a "give" code ([LiveWire.Invite.give]); the phone scans it and asks
 * ([LiveWire.takeGift]); the computer answers with exactly the offer a phone sender builds (Backup's pairWith):
 * the book's identity, the joiner's letter, the other devices as the copy holds them ([LiveCrew.offerPeers], held
 * at those lines — [LiveCrew.holdForCopy]), every letter it knows is taken, a pairing id, and the book.
 *
 * The pairing then waits as a phone sender's does ([LiveRound.setPending], `phone = true`): the joined phone's
 * first word — through the relay ([LiveCrew.confirmFromRelay], from [LiveCloud.absorb]) or in its first WiFi round
 * ([LiveRound.answer]) — names the pairing and makes it one of the book's devices ([LiveCrew.joined]). Not
 * answered in [PENDING_MS], it is given up ([expire]), as the phone's runner gives up its own.
 */
object LiveGive {

    /** How long a given book waits for the joiner's word: the phone's own wait for a pairing (LiveRunner). */
    const val PENDING_MS = 15 * 60_000L

    /** One give made ready: its [offer], the book in [file], and whether live sync was on before ([wasOn]). */
    class Prepared(val offer: LiveWire.Offer, val file: File, val wasOn: Boolean)

    /**
     * The giving device's side, once a give request opened with the code's secret: live sync turned on for this
     * book (if it was not), the relay turned on for it before the copy is taken (a phone joiner meets the book's
     * other devices through it, and the copy then has it on too), the copy's view of the other devices held, the
     * book written to a file in [spool] by [export], and the pairing left waiting for the joiner [joiner] at
     * [host]. Anything failing on the way undoes what was done ([undo]) and throws.
     */
    suspend fun prepare(
        sql: SyncSql, dao: KhataDao, install: String, name: String, marks: LiveSync.LogMarks?,
        joiner: String, host: String, spool: File, export: suspend (File) -> Unit
    ): Prepared {
        val wasOn = LiveSync.identity(sql) != null
        // The device's lock not answering decides nothing (lesson 48): no new identity is made over it.
        if (!wasOn) LiveSync.lockTrouble?.let { throw IOException("the key's lock does not answer: $it") }
        var file: File? = null
        var pair: String? = null
        try {
            val id = LiveSync.enable(sql, install, name, marks = marks)
            val joinerMark = LiveSync.nextJoinerMark(sql)
            dao.setSetting(BookSettings.LIVE_RELAY, "1")
            val peersAtCopy = LiveCrew.offerPeers(sql)
            LiveCrew.holdForCopy(sql, peersAtCopy)
            // The book is taken after this line: whatever changes after it goes in the first rounds, and a row that
            // lands twice lands as one.
            val seq = LiveSync.lastSeq(sql)
            spool.mkdirs()
            val f = File.createTempFile("rk-give-", ".part", spool)
            file = f
            export(f)
            if (f.length() > LiveWire.MAX_BYTES) throw IOException("the book is bigger than a pairing carries")
            val made = LiveSync.newHex(8)
            pair = made
            val offer = LiveWire.Offer(
                id.book, id.key, id.device, id.name, seq, ByteArray(0), joinerMark, made, peers = peersAtCopy,
                marks = LiveSync.knownMarks(sql)
            )
            // Waiting before the book leaves: the joiner's word can never come before the pairing it names.
            LiveRound.setPending(
                sql, LiveRound.Target(listOf(host.ifBlank { "0.0.0.0" }), 0, seq, joiner.ifBlank { null }, pair = made, phone = true),
                wasOff = !wasOn
            )
            return Prepared(offer, f, wasOn)
        } catch (e: Throwable) {
            file?.delete()
            runCatching { undo(sql, wasOn, pair) }
            throw e
        }
    }

    /**
     * The joiner said whether it kept the book ([kept]); the file goes either way (the whole book, PIN hashes
     * included, does not linger). Not kept: as before the give ([undo]).
     */
    suspend fun settle(sql: SyncSql, prepared: Prepared, kept: Boolean) {
        try {
            if (!kept) undo(sql, prepared.wasOn, prepared.offer.pair)
        } finally {
            prepared.file.delete()
        }
    }

    /**
     * Nothing reached the joiner: this give's pairing ([pair]) no longer waits, the copy's holds go, and a book
     * that did not sync before goes back to not syncing.
     */
    suspend fun undo(sql: SyncSql, wasOn: Boolean, pair: String?) {
        if (pair != null && LiveRound.pending(sql)?.pair == pair) LiveRound.dropPending(sql)
        LiveCrew.dropCopyHold(sql)
        if (!wasOn) LiveSync.disable(sql)
    }

    /**
     * A given book whose joiner never answered within [PENDING_MS] (its owner said no, or the phone never came
     * back): given up, as the phone's runner gives up its own pairing. True when live sync went off with it (the
     * book did not sync before the give); [leave] is then run first, so the relays forget this device.
     */
    suspend fun expire(sql: SyncSql, now: Long = System.currentTimeMillis(), leave: suspend () -> Unit = {}): Boolean {
        val pending = LiveRound.pending(sql) ?: return false
        if (!pending.phone || now - (pending.since ?: now) <= PENDING_MS) return false
        LiveCrew.dropCopyHold(sql)
        if (!LiveRound.dropPending(sql)) return false
        // The book synced with nobody before: off again. The relay may have been greeted while it waited.
        if (LiveSync.state(sql).peers.isNotEmpty()) return false
        runCatching { leave() }
        LiveSync.disable(sql)
        return true
    }
}
