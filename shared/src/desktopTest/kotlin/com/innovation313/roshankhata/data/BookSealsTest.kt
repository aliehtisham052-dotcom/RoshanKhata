package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Kitab ki Muhar on a real book (schema 44 through Room, the computer's driver): off by default; the first seal marks
 * where sealing starts; the next day's seal holds the new entries' leaves, chained; an entry's proof says sealed,
 * changed, not yet or before; the witnesses are asked once each, 15 s apart to one TSA, never while the kill switch
 * says so, and given up after a week; the backup (format 19) carries the seals whole, the helper's copy too.
 * The TSA here is a fake that answers with a structurally valid token for the request's own imprint and nonce (the
 * real replies are Rfc3161Test's). Made-up names and figures only.
 */
class BookSealsTest {

    private val dir: File = Files.createTempDirectory("rk-seal").toFile()
    private val db = DesktopDatabase.open(dir.resolve("khata.db"))
    private val dao = db.khataDao()
    private val day = 24L * 60 * 60 * 1000
    /** Noon of a day a month ago, so a second later is the same calendar day in any zone. */
    private val t0 = DayMath.startOfDay(System.currentTimeMillis() - 30 * day) + 12 * 60 * 60 * 1000

    @After
    fun close() { db.close(); dir.deleteRecursively() }

    private suspend fun entry(party: Long, amount: Double, note: String? = null): Long =
        dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = amount, isGiven = true, note = note, entryNumber = ""), emptyList())

    // ---------- A fake TSA: a TimeStampResp around a TSTInfo for the request's own imprint and nonce ----------

    private fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), (n ushr 8).toByte(), n.toByte())
    }

    private fun tlv(tag: Int, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()
        return byteArrayOf(tag.toByte()) + len(body.size) + body
    }

    private val signedData = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
    private val tstInfoOid = byteArrayOf(0x06, 0x0B, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x09, 0x10, 0x01, 0x04)

    private val asked = mutableListOf<String>()

    private val tsa = Rfc3161.Http { url, body ->
        asked += url
        val req = Rfc3161.read(body, 0).children()
        val imprint = req[1].whole
        val nonce = req[2].whole
        val info = tlv(0x30, tlv(0x02, byteArrayOf(1)), tlv(0x06, byteArrayOf(0x2A, 0x03)), imprint, tlv(0x02, byteArrayOf(7)),
            tlv(0x18, "20261105112509Z".toByteArray()), nonce)
        val encap = tlv(0x30, tstInfoOid, tlv(0xA0, tlv(0x04, info)))
        val signed = tlv(0x30, tlv(0x02, byteArrayOf(3)), tlv(0x31), encap, tlv(0x31))
        val token = tlv(0x30, signedData, tlv(0xA0, signed))
        200 to tlv(0x30, tlv(0x30, tlv(0x02, byteArrayOf(0))), token)
    }

    private val down = Rfc3161.Http { url, _ -> asked += url; throw java.io.IOException("no network") }

    @Test
    fun offUntilTurnedOnAndTheFirstSealMarksWhereSealingStarts() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val before = entry(ali, 500.0)
        assertFalse(BookSeals.isOn(dao))
        assertNull(BookSeals.daily(dao, t0, tsa, halted = { false }))
        assertNull(dao.latestSeal())
        assertTrue("nothing asked while off", asked.isEmpty())

        val first = BookSeals.turnOn(dao, t0)!!
        assertTrue(BookSeals.isOn(dao))
        assertEquals(before, first.fromId); assertEquals(before, first.toId)
        assertEquals(0, first.leafCount); assertNull(first.leaves); assertNull(first.prevDigest)
        assertEquals(SealMath.digest(SealMath.header(null, t0, before, before, 0, dao.lastAuditHash()), null), first.digest)
        assertNull("turned on again, no second first seal", BookSeals.turnOn(dao, t0))
        assertTrue(BookSeals.proofOf(dao, before) is BookSeals.Proof.Before)
    }

    @Test
    fun theNextDaysSealHoldsTheNewEntriesChainedAndEachEntrySaysWhereItStands() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali", phone = "03000000001"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        entry(ali, 100.0)
        val first = BookSeals.turnOn(dao, t0)!!
        val e1 = entry(ali, 1250.5, note = "Urea 2 bags")
        val e2 = entry(bilal, 300.0)
        assertNull("once a calendar day", BookSeals.sealIfDue(dao, t0 + 1000))
        assertTrue(BookSeals.proofOf(dao, e1) is BookSeals.Proof.NotYet)

        val second = BookSeals.sealIfDue(dao, t0 + day)!!
        assertEquals(first.toId, second.fromId); assertEquals(e2, second.toId)
        assertEquals(2, second.leafCount); assertEquals(64, second.leaves!!.size)
        assertEquals(first.digest, second.prevDigest)
        val leaves = listOf(e1, e2).map { SealMath.leafHash(SealMath.leafText(dao.sealLineOf(it)!!)) }
        assertArrayEquals(leaves[0] + leaves[1], second.leaves)
        assertEquals(SealMath.digest(SealMath.header(first.digest, t0 + day, first.toId, e2, 2, dao.lastAuditHash()), second.leaves), second.digest)
        // The party's uid is in the leaf: the same entry under another customer is another leaf.
        assertTrue(SealMath.leafText(dao.sealLineOf(e1)!!).endsWith("|" + dao.getParty(ali)!!.uid))

        val proof = BookSeals.proofOf(dao, e2) as BookSeals.Proof.Sealed
        assertEquals(1, proof.index)
        assertEquals(second.id, proof.seal.id)
        val file = JSONObject(BookSeals.proofFile(proof, "RK-000003", madeAt = t0 + day))
        assertEquals(proof.leafText, file.getJSONObject("entry").getString("leafText"))
        // Three decimals, and nothing of the customer but his random uid: no name, no phone.
        assertTrue(SealMath.leafText(dao.sealLineOf(e1)!!).contains("|1250.500|1|"))
        // (The leaves are hashes in base64; taken out so a chance "Ali" in them cannot fail the check.)
        val whole = JSONObject(BookSeals.proofFile(proof, "RK-000003", madeAt = t0 + day))
            .also { it.getJSONObject("seal").remove("leaves") }.toString()
        assertFalse(whole.contains("Ali")); assertFalse(whole.contains("Bilal")); assertFalse(whole.contains("03000000001"))
        assertEquals(second.digest, file.getJSONObject("seal").getString("digest"))
        // A verifier's sum, from the file alone: SHA-256(header + base64-decoded leaves) is the digest.
        val s = file.getJSONObject("seal")
        assertEquals(second.digest, SealMath.digest(s.getString("digestHeader"), B64.decode(s.getString("leaves"))))

        // Edited after the seal: the leaf no longer matches, said as changed.
        dao.updateEntry(dao.getEntry(e1)!!.copy(amount = 1350.5))
        assertTrue(BookSeals.proofOf(dao, e1) is BookSeals.Proof.Changed)
        assertTrue(BookSeals.proofOf(dao, 99_999) is BookSeals.Proof.Unknown)

        // Nothing new the day after: no seal. Only the audit log moved: a seal of no entries, its head the new one.
        assertNull(BookSeals.sealIfDue(dao, t0 + 2 * day))
        dao.log(AuditAction.ENTRY_EDITED, "Owner", "transactions", e1, "1250.50 -> 1350.50")
        val third = BookSeals.sealIfDue(dao, t0 + 3 * day)!!
        assertEquals(0, third.leafCount); assertNull(third.leaves)
        assertEquals(dao.lastAuditHash(), third.auditHead)
        assertEquals(second.digest, third.prevDigest)
    }

    @Test
    fun theWitnessesAreAskedOnceEachApartAndOnlyWhenAllowed() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - 2 * day)
        entry(ali, 100.0)
        BookSeals.sealIfDue(dao, now - day)

        // The kill switch: nothing asked.
        assertEquals(0, BookSeals.witness(dao, now, tsa, halted = { true }, pause = {}))
        assertTrue(asked.isEmpty())

        // No network: every witness waits, nothing written.
        val pauses = mutableListOf<Long>()
        assertEquals(0, BookSeals.witness(dao, now, down, halted = { false }, pause = { pauses += it }))
        assertEquals(6, asked.size)
        assertTrue(dao.sealsSince(0).all { BookSeals.stamps(it).isEmpty() })
        // Two seals waiting for each TSA: one pause of 15 s between them, per TSA.
        assertEquals(listOf(BookSeals.GAP_MS, BookSeals.GAP_MS, BookSeals.GAP_MS), pauses)

        asked.clear()
        assertEquals(6, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        assertEquals(BookSeals.WITNESSES.map { it.url }.flatMap { listOf(it, it) }, asked)
        for (s in dao.sealsSince(0)) {
            val stamps = BookSeals.stamps(s)
            assertEquals(BookSeals.WITNESSES.map { it.name }.toSet(), stamps.keys)
            assertEquals(Rfc3161.generalizedTime("20261105112509Z"), stamps.getValue("Sectigo").time)
            assertNotNull(Rfc3161.tokenTime(B64.decode(stamps.getValue("FreeTSA").token), SealMath.unhex(s.digest)))
        }
        assertEquals(3, BookSeals.status(dao).stamped)

        // All stamped: nobody is asked again.
        asked.clear()
        assertEquals(0, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aWitnessThatNeverStampedIsGivenUpAfterAWeek() = runBlocking {
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - 8 * day)
        assertEquals(0, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun theBackupCarriesTheSealsWholeTheHelpersCopyTooAndARestoreKeepsThem() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - day)
        entry(ali, 100.0); entry(ali, 200.0)
        BookSeals.sealIfDue(dao, now)
        BookSeals.witness(dao, now, tsa, halted = { false }, pause = {})
        val mine = dao.allSealsForBackup()
        assertEquals(2, mine.size)

        val text = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }.toString()
        val (result, data) = BackupCodec.parseText(text)
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        assertEquals(19, data!!.version)
        assertEquals(mine, data.bookSeals)

        val other = DesktopDatabase.open(dir.resolve("other.db"))
        try {
            BackupCodec.restoreLedger(other.khataDao(), data)
            assertEquals(mine, other.khataDao().allSealsForBackup())
            assertEquals("1", other.khataDao().settingValue(BookSeals.SWITCH))
        } finally { other.close() }

        // The helper's copy: no PIN, but the seals (hashes and signed times only).
        val helper = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it, secrets = false) }.toString()
        assertEquals(mine, BackupCodec.parseText(helper).second!!.bookSeals)

        // A copy from before format 19 has none, and restores with none.
        val root = JSONObject(text); root.remove("bookSeals"); root.put("version", 18)
        assertTrue(BackupCodec.parseText(root.toString()).second!!.bookSeals.isEmpty())
    }

    @Test
    fun theKillSwitchKeyIsReadAloneAndItsAbsenceAllows() {
        assertEquals(false, LiveControl.parseSeal("""{"live": false}"""))
        assertEquals(true, LiveControl.parseSeal("""{"live": true, "seal": false}"""))
        assertEquals(false, LiveControl.parseSeal("""{"seal": true}"""))
        assertEquals(null, LiveControl.parseSeal("not json"))
        assertEquals(false, LiveControl.parse("""{"seal": false}"""))
    }
}
