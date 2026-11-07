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
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.util.Collections

/**
 * Kitab ki Muhar on a real book (schema 44 through Room, the computer's driver): off by default, the switch written in
 * the audit log and never sent by live sync; the first seal marks where sealing starts; the next day's seal holds the
 * new entries' leaves — their goods lines, the way they were paid, the loan and bin marks (e2) — chained; a seal made
 * with e1 still checks; more than 5,000 new entries make several chained seals; an entry's proof works the seal out
 * again and holds each answer to it, so an edited seal never reads as sealed; the witnesses are asked once each, 15 s
 * apart to one TSA, each on its own, never while the kill switch says so, and given up after a week; an answer lands
 * only on the seal it was asked for; the backup (format 19) carries the seals whole, a page at a time, the helper's
 * copy too. The TSA here signs real tokens with a key and certificate made in the test ([TestTsa]); OpenSSL's own
 * replies are Rfc3161Test's. Made-up names and figures only.
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

    private suspend fun entry(party: Long, amount: Double, note: String? = null, items: List<EntryItem> = emptyList(), given: Boolean = true,
                              method: String? = null, loan: Boolean = false): Long =
        dao.insertEntryWithItems(
            LedgerEntry(partyId = party, amount = amount, isGiven = given, note = note, entryNumber = "", paymentMethod = method, isQarzeHasna = loan),
            items
        )

    private suspend fun leafOf(id: Long): String = SealMath.leafTextV2(dao.sealEntryOf(id)!!, dao.sealGoodsOf(id))

    // ---------- A TSA that signs: every request answered with a real token for its own imprint and nonce ----------

    private val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val tsa = Rfc3161.Http { url, body -> asked += url; 200 to TestTsa.ecdsa.answer(body) }

    private val down = Rfc3161.Http { url, _ -> asked += url; throw java.io.IOException("no network") }

    /** A SignedData carrying the digest and the nonce but no signature that holds: the review's forgery. */
    private val forger = Rfc3161.Http { url, body -> asked += url; 200 to TestTsa.ecdsa.answer(body, fault = TestTsa.Fault.ZERO_SIGNATURE) }

    @Test
    fun offUntilTurnedOnTheSwitchIsAuditedAndTheFirstSealMarksWhereSealingStarts() = runBlocking {
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
        assertEquals(SealMath.digest(SealMath.headerV2(null, t0, 0, dao.lastAuditHash()), null), first.digest)
        assertEquals(2, BookSeals.versionOf(first))
        assertNull("turned on again, no second first seal", BookSeals.turnOn(dao, t0))
        assertTrue(BookSeals.proofOf(dao, before) is BookSeals.Proof.Before)

        // The switch is written in Who did what, once per change, and it is this device's own: live sync never sends it.
        BookSeals.turnOff(dao)
        BookSeals.turnOff(dao)
        assertEquals(listOf(BookSeals.AUDIT_ON, BookSeals.AUDIT_OFF), dao.auditInOrder().map { it.action })
        assertNull(dao.settingValue(BookSeals.SWITCH))
        assertTrue(BookSeals.SWITCH in BookSettings.ACCESS_KEYS)
    }

    @Test
    fun theNextDaysSealHoldsTheGoodsTheWayItWasPaidTheLoanAndTheBinAndAChangeToAnyShows() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali", phone = "03000000001"))
        val supplier = dao.insertParty(Party(name = "Supplier One", isCustomer = false))
        val urea = dao.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea")))
        dao.restoreBills(listOf(SupplierBill(id = 1, partyId = supplier, totalAmount = 48_000.0)))
        dao.restoreBillItems(listOf(BillItem(id = 1, billId = 1, productName = "Urea", batchNumber = "B-7", quantity = 10.0, unit = "bag", productId = urea)))
        entry(ali, 100.0)
        val first = BookSeals.turnOn(dao, t0)!!
        val line = EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0, productId = urea, billItemId = 1)
        val sale = entry(ali, 9600.0, note = "Urea 2 bags", items = listOf(line))
        val paid = entry(ali, 3000.0, given = false, method = PaymentMethod.CASH)
        val loan = entry(ali, 500.0, loan = true)
        assertNull("once a calendar day", BookSeals.sealIfDue(dao, t0 + 1000))
        assertTrue(BookSeals.proofOf(dao, sale) is BookSeals.Proof.NotYet)

        val second = BookSeals.sealIfDue(dao, t0 + day)!!
        assertEquals(first.toId, second.fromId); assertEquals(loan, second.toId)
        assertEquals(3, second.leafCount); assertEquals(96, second.leaves!!.size)
        assertEquals(first.digest, second.prevDigest)
        assertEquals(2, BookSeals.versionOf(second))
        val leaves = listOf(sale, paid, loan).map { SealMath.leafHash(leafOf(it)) }
        assertArrayEquals(leaves[0] + leaves[1] + leaves[2], second.leaves)
        assertEquals(SealMath.digest(SealMath.headerV2(first.digest, t0 + day, 3, dao.lastAuditHash()), second.leaves), second.digest)
        // The goods line with its unit's key and its batch's uid; the customer's uid, never his name.
        val batchUid = dao.allBillItemsForBackup().single().uid!!
        assertTrue(leafOf(sale), leafOf(sale).contains("|1:1|4:Urea|1:2|3:bag|4:4800|1:0|${batchUid.length}:$batchUid"))
        // The product link is not in the leaf: tying the goods to products later (Products' button) is no change.
        val productUid = dao.allProductsForBackup().single().uid!!
        assertFalse(leafOf(sale).contains(productUid))
        assertTrue(leafOf(sale).contains("|" + dao.getParty(ali)!!.uid!!.let { "${it.length}:$it" } + "|1:1|"))
        assertTrue(leafOf(paid).contains("|4:cash|"))

        val proof = BookSeals.proofOf(dao, sale) as BookSeals.Proof.Sealed
        assertEquals(0, proof.index); assertEquals(2, proof.version)
        assertEquals(second.id, proof.seal.id)
        assertTrue(proof.chain.intact); assertEquals(1, proof.chain.seals)
        assertTrue("no answer yet", proof.answers.isEmpty())
        val file = JSONObject(BookSeals.proofFile(proof, "RK-000002", madeAt = t0 + day))
        assertEquals(proof.leafText, file.getJSONObject("entry").getString("leafText"))
        val s = file.getJSONObject("seal")
        // No seal id and no entry range: they would tell how many days and entries the shop has written (M7).
        for (k in listOf("id", "fromId", "toId", "leafCount", "sealedAt")) assertFalse(k, s.has(k))
        assertEquals(t0 + day, s.getLong("sealedAtByPhoneClock"))
        assertEquals(second.digest, SealMath.digest(s.getString("digestHeader"), B64.decode(s.getString("leaves"))))
        val whole = JSONObject(file.toString()).also { it.getJSONObject("seal").remove("leaves") }.toString()
        assertFalse(whole.contains("Ali")); assertFalse(whole.contains("03000000001"))

        // Each field the seal covers, changed after the seal, shows: a goods line, the way it was paid, the bin, the loan.
        dao.updateEntryWithItems(dao.getEntry(sale)!!, listOf(line.copy(quantity = 3.0)))
        assertTrue(BookSeals.proofOf(dao, sale) is BookSeals.Proof.Changed)
        dao.updateEntryWithItems(dao.getEntry(sale)!!, listOf(line))
        assertTrue("put back as it was", BookSeals.proofOf(dao, sale) is BookSeals.Proof.Sealed)
        dao.updateEntry(dao.getEntry(paid)!!.copy(paymentMethod = PaymentMethod.BANK))
        assertTrue(BookSeals.proofOf(dao, paid) is BookSeals.Proof.Changed)
        dao.updateEntry(dao.getEntry(loan)!!.copy(isQarzeHasna = false))
        assertTrue(BookSeals.proofOf(dao, loan) is BookSeals.Proof.Changed)
        dao.updateEntry(dao.getEntry(loan)!!.copy(isQarzeHasna = true))
        dao.softDeleteEntry(loan)
        assertTrue(BookSeals.proofOf(dao, loan) is BookSeals.Proof.Changed)
        assertTrue(BookSeals.proofOf(dao, 99_999) is BookSeals.Proof.Unknown)

        // Nothing new the day after: no seal. Only the audit log moved: a seal of no entries, its head the new one.
        assertNull(BookSeals.sealIfDue(dao, t0 + 2 * day))
        dao.log(AuditAction.ENTRY_EDITED, "Owner", "transactions", sale, "9600.00 -> 9600.00")
        val third = BookSeals.sealIfDue(dao, t0 + 3 * day)!!
        assertEquals(0, third.leafCount); assertNull(third.leaves)
        assertEquals(dao.lastAuditHash(), third.auditHead)
        assertEquals(second.digest, third.prevDigest)
    }

    @Test
    fun goodsTiedToProductsAfterTheSealAreNoChangeToIt() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        BookSeals.turnOn(dao, t0)
        val loose = entry(ali, 50.0, items = listOf(EntryItem(entryId = 0, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 50.0)))
        BookSeals.sealIfDue(dao, t0 + day)
        assertTrue(BookSeals.proofOf(dao, loose) is BookSeals.Proof.Sealed)
        // Products' "tie the goods" fills the line's product link afterwards: the entry's dealing is as it was.
        assertTrue(dao.linkGoodsToProducts() > 0)
        assertNotNull(dao.itemsOfEntry(loose).single().productId)
        assertTrue(BookSeals.proofOf(dao, loose) is BookSeals.Proof.Sealed)
    }

    @Test
    fun aSealMadeWithE1StillChecksAndTheChainGoesOnInE2() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val a = entry(ali, 1250.5, note = "Urea 2 bags")
        // As the 4–6 Nov builds made them: a first seal and one over entry a, both with the version-1 header.
        val firstDigest = SealMath.digest(SealMath.header(null, t0, 0, 0, 0, null), null)
        dao.insertSeal(BookSeal(sealedAt = t0, fromId = 0, toId = 0, leafCount = 0, digest = firstDigest))
        val leaf = SealMath.leafHash(SealMath.leafText(dao.sealLineOf(a)!!))
        val digest = SealMath.digest(SealMath.header(firstDigest, t0 + day, 0, a, 1, null), leaf)
        dao.insertSeal(BookSeal(sealedAt = t0 + day, fromId = 0, toId = a, leafCount = 1, leaves = leaf, prevDigest = firstDigest, digest = digest))
        dao.setSealSwitch(true, Access.OWNER)

        val p = BookSeals.proofOf(dao, a) as BookSeals.Proof.Sealed
        assertEquals(1, p.version)
        assertTrue(p.leafText.startsWith("e1|"))
        assertTrue(p.chain.intact)
        val header = JSONObject(BookSeals.proofFile(p, "RK-000001", t0)).getJSONObject("seal").getString("digestHeader")
        assertTrue(header.startsWith("rk-seal-v1\n"))

        val b = entry(ali, 300.0)
        val next = BookSeals.sealIfDue(dao, t0 + 2 * day)!!
        assertEquals(2, BookSeals.versionOf(next))
        assertEquals(digest, next.prevDigest)
        val pb = BookSeals.proofOf(dao, b) as BookSeals.Proof.Sealed
        assertEquals(2, pb.version); assertTrue(pb.chain.intact); assertEquals(2, pb.chain.seals)
    }

    @Test
    fun aCatchUpPastFiveThousandEntriesIsSeveralChainedSealsAndTheBackupCarriesThemAll() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        BookSeals.turnOn(dao, t0)
        dao.restoreEntries((1..BookSeals.MAX_LEAVES + 3).map { i ->
            LedgerEntry(partyId = ali, amount = i.toDouble(), isGiven = true, entryNumber = "RK-%06d".format(i), timestamp = t0 + i)
        })
        val newest = BookSeals.sealIfDue(dao, t0 + day)!!
        val all = dao.allSealsForBackup()
        assertEquals(listOf(0, BookSeals.MAX_LEAVES, 3), all.map { it.leafCount })
        assertEquals(all.last(), newest)
        for (i in 1 until all.size) {
            assertEquals(all[i - 1].digest, all[i].prevDigest)
            assertEquals(all[i - 1].toId, all[i].fromId)
            assertEquals(2, BookSeals.versionOf(all[i]))
        }
        assertEquals(dao.maxEntryId(), newest.toId)
        val ids = dao.allEntriesForBackup().map { it.id }.sorted()
        assertEquals(ids[BookSeals.MAX_LEAVES - 1], all[1].toId)
        val past = BookSeals.proofOf(dao, ids[BookSeals.MAX_LEAVES]) as BookSeals.Proof.Sealed
        assertEquals(all[2].id, past.seal.id); assertEquals(0, past.index); assertTrue(past.chain.intact)
        // The backup writes them a few a page and reads them back whole.
        val text = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }.toString()
        assertEquals(all, BackupCodec.parseText(text).second!!.bookSeals)
    }

    @Test
    fun aProofWorksTheSealOutAgainSoAnEditedSealNeverReadsAsSealed() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - 2 * day)
        val a = entry(ali, 100.0)
        BookSeals.sealIfDue(dao, now - day)
        assertEquals(6, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        val sealed = BookSeals.proofOf(dao, a) as BookSeals.Proof.Sealed
        assertEquals(BookSeals.WITNESSES.map { it.name }, sealed.answers.map { it.name })
        // Each time is read from the token itself, never from the JSON beside it.
        for (ans in sealed.answers) assertEquals(Rfc3161.tokenTime(B64.decode(ans.token), SealMath.unhex(sealed.seal.digest)), ans.genTime)

        // An answer that is not for this seal's digest is not counted, and its witness is asked again.
        val seal = dao.sealCovering(a)!!
        val elsewhere = TestTsa.ecdsa.token(ByteArray(32) { 7 }, null, now)
        val tokens = BookSeals.stamps(seal) + ("Sectigo" to BookSeals.Stamp(B64.encode(elsewhere), now))
        assertEquals(1, dao.setSealTokens(seal.id, seal.digest, BookSeals.tokensJson(tokens)))
        assertEquals(listOf("FreeTSA", "DigiCert"), (BookSeals.proofOf(dao, a) as BookSeals.Proof.Sealed).answers.map { it.name })
        asked.clear()
        assertEquals(1, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        assertEquals(listOf("http://timestamp.sectigo.com"), asked.toList())
        assertEquals(3, BookSeals.status(dao).answered)

        // A backup edited by hand: an entry's leaf slipped into the seal. Restored, the seal does not add up.
        val text = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }.toString()
        val opened = ArrayList<KhataDatabase>()
        suspend fun restored(edit: (JSONObject) -> Unit): KhataDao {
            val root = JSONObject(text)
            val arr = root.getJSONArray("bookSeals")
            edit(arr.getJSONObject(arr.length() - 1))
            val other = DesktopDatabase.open(dir.resolve("other-${opened.size}.db")).also { opened += it }
            BackupCodec.restoreLedger(other.khataDao(), BackupCodec.parseText(root.toString()).second!!)
            return other.khataDao()
        }
        try {
            val forged = SealMath.leafHash("e2|forged")
            val broken = restored { s ->
                s.put("leaves", B64.encode(B64.decode(s.getString("leaves")) + forged)); s.put("leafCount", 2)
            }
            assertTrue(BookSeals.proofOf(broken, a) is BookSeals.Proof.Broken)
            // The digest worked out again too: the seal adds up, but no witness answered for that digest.
            val recomputed = restored { s ->
                val leaves = B64.decode(s.getString("leaves")) + forged
                s.put("leaves", B64.encode(leaves)); s.put("leafCount", 2)
                val head = SealMath.headerV2(s.getString("prevDigest"), s.getLong("sealedAt"), 2, s.optString("auditHead", "").ifEmpty { null })
                s.put("digest", SealMath.digest(head, leaves))
            }
            val p = BookSeals.proofOf(recomputed, a) as BookSeals.Proof.Sealed
            assertTrue("no answer holds for a digest worked out again", p.answers.isEmpty())
            assertEquals(0, BookSeals.status(recomputed).answered)
        } finally {
            opened.forEach { it.close() }
        }
    }

    @Test
    fun theWitnessesAreAskedOnceEachApartOnlyWhenAllowedAndAForgeryIsNotKept() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - 2 * day)
        entry(ali, 100.0)
        BookSeals.sealIfDue(dao, now - day)

        // The kill switch: nothing asked.
        assertEquals(0, BookSeals.witness(dao, now, tsa, halted = { true }, pause = {}))
        assertTrue(asked.isEmpty())

        // No network: every witness waits, nothing written.
        val pauses = Collections.synchronizedList(mutableListOf<Long>())
        assertEquals(0, BookSeals.witness(dao, now, down, halted = { false }, pause = { pauses += it }))
        assertEquals(6, asked.size)
        assertTrue(dao.sealHeadsSince(0).all { BookSeals.stamps(it.tokens).isEmpty() })
        // Two seals waiting for each TSA: one pause of 15 s between them, per TSA.
        assertEquals(listOf(BookSeals.GAP_MS, BookSeals.GAP_MS, BookSeals.GAP_MS), pauses.toList())

        // An answer whose signature does not hold is not kept: the real one can still come.
        asked.clear()
        assertEquals(0, BookSeals.witness(dao, now, forger, halted = { false }, pause = {}))
        assertEquals(6, asked.size)
        assertTrue(dao.sealHeadsSince(0).all { it.tokens == null })

        asked.clear()
        assertEquals(6, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        // Each witness on its own, side by side: every TSA asked for both seals, in whatever order they answered.
        assertEquals(BookSeals.WITNESSES.map { it.url }.flatMap { listOf(it, it) }.sorted(), asked.toList().sorted())
        for (s in dao.sealHeadsSince(0)) assertEquals(BookSeals.WITNESSES.map { it.name }.toSet(), BookSeals.answers(s).keys)
        assertEquals(3, BookSeals.status(dao).answered)

        // All answered: nobody is asked again.
        asked.clear()
        assertEquals(0, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun anAnswerLandsOnlyOnTheSealItWasAskedFor() = runBlocking {
        val now = System.currentTimeMillis()
        val first = BookSeals.turnOn(dao, now - day)!!
        val token = B64.encode(TestTsa.ecdsa.token(SealMath.unhex(first.digest), null, now))
        // A restore put back another seal with the same id: an answer for the old digest is not written on it.
        assertEquals(0, dao.setSealTokens(first.id, "00".repeat(32), "{}"))
        assertFalse(dao.addSealStamp(first.id, "00".repeat(32), "FreeTSA", BookSeals.Stamp(token, now)))
        assertFalse(dao.addSealStamp(first.id + 1, first.digest, "FreeTSA", BookSeals.Stamp(token, now)))
        assertNull(dao.sealById(first.id)!!.tokens)
        // Its own seal takes it, and a second witness's answer joins the first.
        assertTrue(dao.addSealStamp(first.id, first.digest, "FreeTSA", BookSeals.Stamp(token, now)))
        assertTrue(dao.addSealStamp(first.id, first.digest, "DigiCert", BookSeals.Stamp(token, now)))
        assertEquals(setOf("FreeTSA", "DigiCert"), BookSeals.answers(dao.sealHeadById(first.id)!!).keys)
    }

    @Test
    fun aWitnessThatNeverStampedIsGivenUpAfterAWeek() = runBlocking {
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - 8 * day)
        assertEquals(0, BookSeals.witness(dao, now, tsa, halted = { false }, pause = {}))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun theBackupCarriesTheSealsWholeAPageAtATimeTheHelpersCopyTooAndARestoreKeepsThem() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val now = System.currentTimeMillis()
        BookSeals.turnOn(dao, now - 11 * day)
        // More seals than one page of the backup holds.
        for (d in 10 downTo 1) { entry(ali, 100.0 + d); BookSeals.sealIfDue(dao, now - d * day) }
        BookSeals.witness(dao, now, tsa, halted = { false }, pause = {})
        val mine = dao.allSealsForBackup()
        assertEquals(11, mine.size)
        assertTrue(mine.size > BookSeals.WITH_LEAVES_PAGE)

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
            assertNotNull(BookSeals.proofOf(other.khataDao(), dao.maxEntryId()) as? BookSeals.Proof.Sealed)
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
