package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * Live sync through the relay (Marhala 2), against a relay kept in memory
 * that does what cloud/worker does: a pass set by the first hello, parcels in
 * order, a device never given its own, a parcel gone once the others acked it.
 * The two books meet through it alone, and what the relay holds never shows a
 * name or a figure.
 */
class LiveCloudTest {

    /** cloud/worker's rules in a few lines. */
    private class FakeRelay : LiveCloud.Http {
        var pass: String? = null
        val devices = HashMap<String, Long>() // device -> acked
        val parcels = ArrayList<Triple<Long, String, ByteArray>>()
        var seq = 0L
        var down = false
        /** The relay's owner halted it: 503 to every call. */
        var halted = false
        /** The relay forgot every device (its box emptied): the next call of each is told to say hello first. */
        fun forget() { devices.clear(); pass = null }
        /** The next sent parcel is lost (the relay forgot it). */
        var loseNext = false
        /** The relay files every parcel under this device, whoever sent it (a bug or a lie there). */
        var misfileAs: String? = null
        /** Calls answered past the pass and the hello check. */
        var calls = 0
        /** Each device's wake-up address, as the worker's devices.fcm column. */
        val wakes = HashMap<String, String?>()
        /** A relay uploaded before "round" existed: it answers 404 to it, and the app falls back to three calls. */
        var noRound = false
        var rounds = 0
        /** Every hello is answered with this refusal (the relay's capacity: 507 full, 429 new books). */
        var helloRefusal: Pair<Int, String>? = null
        var hellos = 0
        /** The book's day limit is reached: a round's parcel is refused, the ack kept and the page still given (23 Oct 2026). */
        var dayFull = false
        /** A whole page says how far the book goes ("through"), as the relays do since 23 Oct 2026. */
        var sayThrough = false

        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            if (down) throw java.io.IOException("no internet")
            if (halted) return 503 to JSONObject().put("ok", false).put("why", "halted").toString().toByteArray()
            val token = headers["authorization"]!!.removePrefix("RK ")
            val dev = headers["x-device"]!!
            val action = url.substringAfterLast('/').substringBefore('?')
            fun ok(o: JSONObject) = 200 to o.put("ok", true).toString().toByteArray()
            fun no(code: Int, why: String) = code to JSONObject().put("ok", false).put("why", why).toString().toByteArray()
            if (action == "hello") { hellos++; helloRefusal?.let { return no(it.first, it.second) } }
            if (pass == null) { if (action != "hello") return no(403, "say-hello-first"); pass = token }
            else if (pass != token) return no(403, "wrong-pass")
            if (dev !in devices && action != "hello") return no(403, "say-hello-first")
            calls++
            return when (action) {
                "hello" -> { devices.putIfAbsent(dev, 0L); ok(JSONObject().put("top", seq)) }
                "send" -> {
                    seq++
                    if (loseNext) loseNext = false else parcels.add(Triple(seq, misfileAs ?: dev, body!!))
                    ok(JSONObject().put("seq", seq))
                }
                "take" -> {
                    val after = url.substringAfter("after=").toLong()
                    val arr = JSONArray()
                    parcels.filter { it.first > after && it.second != dev }.forEach {
                        arr.put(JSONObject().put("seq", it.first).put("from", it.second).put("body", Base64.getEncoder().encodeToString(it.third)))
                    }
                    ok(JSONObject().put("parcels", arr).put("more", false))
                }
                "ack" -> {
                    devices[dev] = maxOf(devices[dev]!!, JSONObject(String(body!!)).getLong("upTo"))
                    parcels.removeAll { p -> devices.filterKeys { it != p.second }.values.all { it >= p.first } }
                    ok(JSONObject())
                }
                "round" -> {
                    if (noRound) return no(404, "no-such-path")
                    rounds++
                    val o = JSONObject(String(body!!))
                    if (!o.isNull("upTo")) {
                        devices[dev] = maxOf(devices[dev]!!, o.getLong("upTo"))
                        parcels.removeAll { p -> devices.filterKeys { it != p.second }.values.all { it >= p.first } }
                    }
                    var sent: Long? = null
                    val refusedFull = dayFull && !o.isNull("parcel")
                    if (!o.isNull("parcel") && !refusedFull) {
                        seq++
                        if (loseNext) loseNext = false else parcels.add(Triple(seq, misfileAs ?: dev, B64.decode(o.getString("parcel"))))
                        sent = seq
                    }
                    val after = o.getLong("after")
                    val arr = JSONArray()
                    parcels.filter { it.first > after && it.second != dev }.forEach {
                        arr.put(JSONObject().put("seq", it.first).put("from", it.second).put("body", Base64.getEncoder().encodeToString(it.third)))
                    }
                    val answer = JSONObject().put("seq", sent ?: JSONObject.NULL).put("parcels", arr).put("more", false)
                    if (sayThrough) answer.put("through", maxOf(after, seq))
                    if (refusedFull) 429 to answer.put("ok", false).put("why", "day-limit").toString().toByteArray() else ok(answer)
                }
                "wake" -> {
                    val o = JSONObject(String(body!!))
                    wakes[dev] = if (o.isNull("token")) null else o.getString("token")
                    ok(JSONObject().put("wake", wakes[dev] != null))
                }
                else -> no(404, "no-such-path")
            }
        }
    }

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val relay = FakeRelay()
    private var clock = 1_000_000L

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-cloud").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        runBlocking {
            // The owner turned the relay on for this book (off until then, 22 Oct 2026): on both, as sync carries it.
            phone.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            pc.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            // Paired as over WiFi: the same book, each knowing the other.
            val id = LiveSync.enable(phone.syncSql(), "install-phone", "Phone")
            val pcId = LiveSync.enable(pc.syncSql(), "install-pc", "PC", adopt = id.book to id.key)
            LiveSync.putPeer(pc.syncSql(), id.device, name = "Phone", sent = 0, recv = 0)
            LiveSync.putPeer(phone.syncSql(), pcId.device, name = "PC", sent = 0, recv = 0)
        }
    }

    @After
    fun close() {
        LiveLines.maker = null
        LiveLines.closeAll()
        phone.close(); pc.close()
        dir.deleteRecursively()
    }

    /**
     * The live line in memory: each device's line carries the HTTP round's body to [relay] as cloud/worker's
     * webSocketMessage does, and a parcel left over a line nudges the others' lines ({"t":"new"}).
     */
    private inner class FakeLines(private val relay: FakeRelay) : LiveLines.Maker {
        val open = ArrayList<Pair<String, (String) -> Unit>>()
        var messages = 0
        override fun open(url: String, headers: Map<String, String>, onText: (String) -> Unit, onClosed: (Int) -> Unit): LiveLines.Line {
            val dev = headers.getValue("x-device")
            val me = dev to onText
            open += me
            val http = url.replace("wss://", "https://").removeSuffix("/live")
            return object : LiveLines.Line {
                override fun send(text: String): Boolean {
                    messages++
                    val o = JSONObject(text)
                    val (code, bytes) = relay.call("POST", "$http/round", headers, o.getJSONObject("round").toString().toByteArray())
                    val answer = JSONObject(String(bytes)).put("id", o.getInt("id")).put("status", code)
                    onText(answer.toString())
                    if (code == 200 && !answer.isNull("seq")) open.filter { it.first != dev }.forEach { it.second("{\"t\":\"new\"}") }
                    return true
                }
                override fun close() { open.remove(me); onClosed(1000) }
            }
        }
    }

    private suspend fun both(times: Int = 2) = repeat(times) {
        clock += 1000
        val a = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
        val b = LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { false }
        assertTrue("$a", a is LiveCloud.Result.Done)
        assertTrue("$b", b is LiveCloud.Result.Done)
    }

    @Test
    fun `entries made on each side meet through the relay alone`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        pc.khataDao().insertParty(Party(name = "Bilal"))
        // Three rounds: the rows, then each side's word on how far it read (a round's parcel is composed
        // before its take, so the acknowledgements go a round later).
        both(3)
        assertEquals(setOf("Ali", "Bilal"), phone.khataDao().allPartiesForBackup().map { it.name }.toSet())
        assertEquals(setOf("Ali", "Bilal"), pc.khataDao().allPartiesForBackup().map { it.name }.toSet())
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
        // Nothing the relay holds reads as a name.
        relay.parcels.forEach { assertFalse(String(it.third, Charsets.ISO_8859_1).contains("Ali")) }
        // Quiet rounds leave nothing new, and the relay empties as both acknowledge.
        val before = relay.seq
        both(3)
        assertEquals(before, relay.seq)
        assertTrue(relay.parcels.isEmpty())
    }

    @Test
    fun `the phone's wake-up address is told once, changed and taken away`() = runBlocking {
        val sql = phone.syncSql()
        val dev = LiveSync.identity(sql)!!.device.take(16).padEnd(16, '0')
        assertTrue(LiveCloud.tellWake(sql, "token-one-0123456789", relay, "https://relay.test") { false })
        assertEquals("token-one-0123456789", relay.wakes[dev])
        // Said once: the same address again does not call the relay.
        val calls = relay.calls
        assertTrue(LiveCloud.tellWake(sql, "token-one-0123456789", relay, "https://relay.test") { false })
        assertEquals(calls, relay.calls)
        // A new token (Firebase made another) is told; null takes it away.
        assertTrue(LiveCloud.tellWake(sql, "token-two-0123456789", relay, "https://relay.test") { false })
        assertEquals("token-two-0123456789", relay.wakes[dev])
        assertTrue(LiveCloud.tellWake(sql, null, relay, "https://relay.test") { false })
        assertEquals(null, relay.wakes[dev])
        // The phone's rounds still meet the computer's after it said hello this way.
        pc.khataDao().insertParty(Party(name = "Woken"))
        both()
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Woken" })
    }

    @Test
    fun `no wake-up address leaves the phone while the relay is off or stopped`() = runBlocking {
        phone.khataDao().setSetting(BookSettings.LIVE_RELAY, null)
        assertFalse(LiveCloud.tellWake(phone.syncSql(), "token-one-0123456789", relay, "https://relay.test") { false })
        phone.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
        assertFalse(LiveCloud.tellWake(phone.syncSql(), "token-one-0123456789", relay, "https://relay.test") { true })
        assertFalse(LiveCloud.tellWake(phone.syncSql(), "token-one-0123456789", relay, null) { false })
        assertEquals(0, relay.calls)
        assertTrue(relay.wakes.isEmpty())
    }

    @Test
    fun `a relay that forgot the phone is told its address again`() = runBlocking {
        val sql = phone.syncSql()
        val dev = LiveSync.identity(sql)!!.device.take(16).padEnd(16, '0')
        assertTrue(LiveCloud.tellWake(sql, "token-one-0123456789", relay, "https://relay.test") { false })
        both(1)
        // The relay's box emptied (a month unused): it knows neither device nor address.
        relay.devices.clear(); relay.wakes.clear(); relay.parcels.clear()
        both(1)
        assertTrue(LiveCloud.tellWake(sql, "token-one-0123456789", relay, "https://relay.test") { false })
        assertEquals("token-one-0123456789", relay.wakes[dev])
    }

    @Test
    fun `a quiet round is one request, and a relay without round is spoken to the old way`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "One call"))
        both()
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "One call" })
        val calls = relay.calls
        val rounds = relay.rounds
        both(1)
        // Two quiet rounds (phone and computer): two requests, both "round".
        assertEquals(2, relay.calls - calls)
        assertEquals(2, relay.rounds - rounds)
        // The old relay: the same book meets through take/ack/send.
        val old = FakeRelay().also { it.noRound = true }
        pc.khataDao().insertParty(Party(name = "Old relay"))
        repeat(3) {
            clock += 1000
            assertTrue(LiveCloud.round(phone.syncSql(), old, "https://old.test", clock) { false } is LiveCloud.Result.Done)
            assertTrue(LiveCloud.round(pc.syncSql(), old, "https://old.test", clock) { false } is LiveCloud.Result.Done)
        }
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Old relay" })
        assertEquals(0, old.rounds)
    }

    @Test
    fun `a parcel nobody acknowledges is sent again after a growing wait, not every round`() = runBlocking {
        // The computer never comes: the phone's parcel waits at the relay.
        phone.khataDao().insertParty(Party(name = "Waiting"))
        clock += 1000
        assertTrue(LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
        val first = relay.seq
        // Every 15 s for an hour: a resend after 2 min, then 4, 8, 16, 32 min — five, not two hundred.
        repeat(240) {
            clock += 15_000
            LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
        }
        val resent = relay.seq - first
        assertTrue("resent $resent times", resent in 4..6)
    }

    @Test
    fun `a computer whose pointer went back is caught up, not asked for ever`() = runBlocking {
        // The phone's line reaches the computer, but the computer's word that it read it has not come back yet,
        // so the phone still keeps the line.
        phone.khataDao().insertParty(Party(name = "Before"))
        clock += 1000
        assertTrue(LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
        assertTrue(LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Before" })
        // The computer's book went back (rebuilt from an older copy): it has read nothing of the phone's log.
        val phoneDev = LiveSync.identity(phone.syncSql())!!.device
        LiveSync.putPeer(pc.syncSql(), phoneDev, recv = 0L)
        phone.khataDao().insertParty(Party(name = "After the gap"))
        // The phone's next parcel starts past 0: a gap at the computer, said back as "need"; the phone starts again at 0.
        both(6)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "After the gap" })
        assertEquals(1, pc.khataDao().allPartiesForBackup().count { it.name == "Before" })
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
        // And no conflict in a money table for the bell.
        assertEquals(0, LiveSync.moneyConflicts(pc.syncSql()))
    }

    @Test
    fun `a computer that went back past what the phone still keeps is told to join again, the round does not throw`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Long ago"))
        // Read and acknowledged on both sides: the phone lets the line go.
        both(4)
        val phoneDev = LiveSync.identity(phone.syncSql())!!.device
        LiveSync.putPeer(pc.syncSql(), phoneDev, recv = 0L)
        phone.khataDao().insertParty(Party(name = "Later"))
        val seen = ArrayList<LiveCloud.Result>()
        repeat(8) {
            clock += 1000
            seen += LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
            seen += LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { false }
        }
        assertTrue("$seen", seen.any { it is LiveCloud.Result.Refused && it.why == LiveRound.LOST })
    }

    @Test
    fun `a parcel the relay files under another device is not taken as that device's`() = runBlocking {
        both(1)
        relay.misfileAs = "someone-else"
        phone.khataDao().insertParty(Party(name = "Misfiled"))
        both(2)
        assertTrue(pc.khataDao().allPartiesForBackup().none { it.name == "Misfiled" })
        // Filed right again, it arrives (the sender resends what was never acknowledged).
        relay.misfileAs = null
        clock += LiveCloud.RESEND_MS + 1
        both(2)
        assertEquals(1, pc.khataDao().allPartiesForBackup().count { it.name == "Misfiled" })
    }

    @Test
    fun `over the live line a round is one message, and a parcel nudges the other side at once`() = runBlocking {
        val lines = FakeLines(relay)
        LiveLines.maker = lines
        var httpCalls = 0
        val counted = LiveCloud.Http { m, u, h, b -> httpCalls++; relay.call(m, u, h, b) }
        suspend fun bothOver(times: Int) = repeat(times) {
            clock += 1000
            assertTrue(LiveCloud.round(phone.syncSql(), counted, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
            assertTrue(LiveCloud.round(pc.syncSql(), counted, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
        }
        phone.khataDao().insertParty(Party(name = "Over the line"))
        bothOver(3)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Over the line" })
        // Two hellos over HTTP; every round after went over the lines.
        assertEquals(2, httpCalls)
        assertTrue(lines.messages >= 6)
        // The computer's entry nudges the phone's open line: a waiting runner wakes without asking on a clock.
        val since = LiveLines.newsMark()
        pc.khataDao().insertParty(Party(name = "Nudged"))
        clock += 1000
        LiveCloud.round(pc.syncSql(), counted, "https://relay.test", clock) { false }
        assertTrue(LiveLines.waitForNews(2_000, since))
        // A line that ends leaves the round on HTTP, unchanged.
        LiveLines.closeAll()
        LiveLines.maker = null
        bothOver(2)
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Nudged" })
        assertTrue(httpCalls > 2)
    }

    @Test
    fun `a parcel carried by the push is written in without a relay call, and a gap asks for a round`() = runBlocking {
        both(2)
        val pcDev = LiveSync.identity(pc.syncSql())!!.device.take(16).padEnd(16, '0')
        // The computer leaves a parcel; the relay would put it in the push as base64.
        pc.khataDao().insertParty(Party(name = "Carried"))
        clock += 1000
        LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { false }
        val first = relay.parcels.last()
        val calls = relay.calls
        assertEquals(LiveCloud.Carried.TOOK, LiveCloud.takeCarried(phone.syncSql(), pcDev, B64.encode(first.third), clock))
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Carried" })
        assertEquals(calls, relay.calls)
        // The relay is still the record: the next round reads it again and nothing lands twice.
        both(2)
        assertEquals(1, phone.khataDao().allPartiesForBackup().count { it.name == "Carried" })
        // A parcel whose sender the relay misnames, or that another key sealed, is left alone.
        assertEquals(LiveCloud.Carried.NOT_MINE, LiveCloud.takeCarried(phone.syncSql(), "ffffffffffffffff", B64.encode(first.third), clock))
        assertEquals(LiveCloud.Carried.NOT_MINE, LiveCloud.takeCarried(phone.syncSql(), pcDev, B64.encode(ByteArray(64) { 3 }), clock))
        // Two parcels, only the second carried (the first was lost on the way): a round is asked for.
        relay.loseNext = true
        pc.khataDao().insertParty(Party(name = "Lost on the way"))
        clock += 1000
        LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { false }
        pc.khataDao().insertParty(Party(name = "Second"))
        clock += LiveCloud.RESEND_MS + 1
        val beforeSeq = relay.seq
        LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { false }
        val later = relay.parcels.last { it.first > beforeSeq }
        val r = LiveCloud.takeCarried(phone.syncSql(), pcDev, B64.encode(later.third), clock)
        assertTrue("$r", r != LiveCloud.Carried.NOT_MINE)
        both(3)
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Lost on the way" })
        assertEquals(1, phone.khataDao().allPartiesForBackup().count { it.name == "Second" })
    }

    /**
     * Three devices, one book (23 Oct 2026): the computer and the owner's phone sync; a new phone joins by taking
     * the book from the owner's phone (as LiveReceive does) and answering through the relay. Every device's entry
     * reaches the two others, the computer learns of the new phone from the owner's phone, and the three books
     * end the same. The new phone was never paired with the computer.
     */
    @Test
    fun `a third device joins through a phone and every device hears every other`() = runBlocking {
        both(2)
        val b = DesktopDatabase.open(dir.resolve("phone-b.db"))
        try {
            val aSql = phone.syncSql(); val cSql = pc.syncSql(); val bSql = b.syncSql()
            b.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            phone.khataDao().insertParty(Party(name = "Before B"))
            both(2)
            // The owner's phone gives its book: the copy's view of its other devices, held until they know B.
            val peersAtCopy = LiveCrew.offerPeers(aSql)
            LiveCrew.holdForCopy(aSql, peersAtCopy)
            val seq = LiveSync.lastSeq(aSql)
            val text = java.io.StringWriter().also { BackupCodec.exportTo(phone.khataDao(), "Shop", JSONObject(), it) }.toString()
            val (r, data) = BackupCodec.parseText(text)
            assertTrue("$r", r is BackupCodec.ImportResult.Ok)
            val aId = LiveSync.identity(aSql)!!
            val pair = LiveSync.newHex(8)
            val offer = LiveWire.Offer(aId.book, aId.key, aId.device, "Owner phone", seq, ByteArray(0), LiveSync.nextJoinerMark(aSql), pair, peers = peersAtCopy)
            LiveRound.setPending(aSql, LiveRound.Target(listOf("192.168.1.9"), 47313, seq, "Munshi phone", pair = pair, phone = true), wasOff = false)
            BackupCodec.restoreLedger(b.khataDao(), data!!)
            LiveCrew.join(bSql, "install-b", "Munshi phone", offer, offer.peers)
            assertEquals(2, LiveSync.state(bSql).peers.size)

            suspend fun all(times: Int) = repeat(times) {
                clock += 1000
                for (s in listOf(aSql, cSql, bSql)) {
                    val res = LiveCloud.round(s, relay, "https://relay.test", clock) { false }
                    assertTrue("$res", res is LiveCloud.Result.Done)
                }
            }
            // The computer writes while the new phone is still being heard of: nothing of it may be lost to B.
            pc.khataDao().insertParty(Party(name = "From the computer"))
            all(6)
            // The owner's phone took B's yes through the relay; the computer took B from the owner's announcement.
            assertTrue(LiveSync.state(aSql).peers.any { it.name == "Munshi phone" })
            assertTrue(LiveSync.state(cSql).peers.any { it.name == "Munshi phone" })
            assertEquals(null, LiveRound.pending(aSql))
            b.khataDao().insertParty(Party(name = "From the munshi"))
            phone.khataDao().insertParty(Party(name = "From the owner"))
            all(8)
            for (db in listOf(phone, pc, b)) {
                val names = db.khataDao().allPartiesForBackup().map { it.name }
                for (n in listOf("Before B", "From the computer", "From the munshi", "From the owner")) {
                    assertEquals("$n on ${db === b}", 1, names.count { it == n })
                }
            }
            assertEquals(LiveSync.fingerprint(aSql), LiveSync.fingerprint(cSql))
            assertEquals(LiveSync.fingerprint(aSql), LiveSync.fingerprint(bSql))
            // The holds went once every device knew B; nothing more is announced.
            assertEquals(null, LiveCrew.joinsOut(aSql))
            assertEquals(LiveSync.state(aSql).peers.first { it.device == LiveSync.identity(cSql)!!.device }.recv,
                LiveCrew.ackFor(aSql, LiveSync.identity(cSql)!!.device, LiveSync.state(aSql).peers.first { it.device == LiveSync.identity(cSql)!!.device }.recv))
        } finally {
            b.close()
        }
    }

    /**
     * Letters in a chain of pairings (23 Oct 2026, the review report R05): the owner's phone gives the book to a
     * second phone, which gives it to a third; each joiner's letter is new to every device, so no two devices print
     * the same S- number. Two devices that come to one letter anyway (an older pairing) are moved apart by the
     * relay rounds alone, as the WiFi rounds always did.
     */
    @Test
    fun `phones paired one from another never share a letter, and the relay settles two that do`() = runBlocking {
        both(2)
        val b = DesktopDatabase.open(dir.resolve("phone-b.db"))
        val d = DesktopDatabase.open(dir.resolve("phone-d.db"))
        try {
            val aSql = phone.syncSql(); val cSql = pc.syncSql(); val bSql = b.syncSql(); val dSql = d.syncSql()
            // The owner's phone heard the computer's letter through the relay.
            assertTrue(LiveSync.knownMarks(aSql).contains(LiveSync.mark(cSql)!!))
            val everyone = arrayListOf(aSql, cSql)

            suspend fun all(times: Int) = repeat(times) {
                clock += 1000
                for (s in everyone) {
                    val res = LiveCloud.round(s, relay, "https://relay.test", clock) { false }
                    assertTrue("$res", res is LiveCloud.Result.Done)
                }
            }
            /** [from] gives its book to [to] as Backup's pairing and LiveReceive do, and the joiner is heard of. */
            suspend fun give(from: KhataDatabase, to: KhataDatabase, install: String, name: String) {
                val fSql = from.syncSql(); val tSql = to.syncSql()
                val peersAtCopy = LiveCrew.offerPeers(fSql)
                LiveCrew.holdForCopy(fSql, peersAtCopy)
                val seq = LiveSync.lastSeq(fSql)
                val text = java.io.StringWriter().also { BackupCodec.exportTo(from.khataDao(), "Shop", JSONObject(), it) }.toString()
                val (r, data) = BackupCodec.parseText(text)
                assertTrue("$r", r is BackupCodec.ImportResult.Ok)
                val fId = LiveSync.identity(fSql)!!
                val pair = LiveSync.newHex(8)
                val given = LiveSync.nextJoinerMark(fSql)
                val offer = LiveWire.Offer(
                    fId.book, fId.key, fId.device, fId.name, seq, ByteArray(0), given, pair, peers = peersAtCopy,
                    marks = LiveSync.knownMarks(fSql)
                )
                // The offer travels as the pairing frame's head does.
                val head = LiveWire.decodeHead(LiveWire.offerHead(offer))!!
                assertEquals(offer.marks, head.marks)
                LiveRound.setPending(fSql, LiveRound.Target(listOf("192.168.1.9"), 47313, seq, name, pair = pair, phone = true), wasOff = false)
                BackupCodec.restoreLedger(to.khataDao(), data!!)
                to.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
                LiveCrew.join(tSql, install, name, head, head.peers)
                assertEquals(given, LiveSync.mark(tSql))
                everyone += tSql
                all(6)
                assertEquals(null, LiveRound.pending(fSql))
            }

            give(phone, b, "install-b", "Second phone")
            give(b, d, "install-d", "Third phone")
            val marks = everyone.map { LiveSync.mark(it)!! }
            assertEquals("$marks", 4, marks.toSet().size)
            // So every device's next ticket has a number of its own.
            try {
                val numbers = marks.map { m -> NumberMark.mark = m; SaleNumber.next(19) }
                assertEquals("$numbers", 4, numbers.toSet().size)
            } finally {
                NumberMark.mark = null
            }

            // The third phone comes to the computer's letter (as a pairing before letters were known could):
            // relay rounds that carry a change of each move one of them, to a letter nobody holds.
            dSql.write { exec("UPDATE sync_meta SET v = ? WHERE k = 'mark'", listOf(LiveSync.mark(cSql)!!)) }
            pc.khataDao().insertParty(Party(name = "On the computer"))
            d.khataDao().insertParty(Party(name = "On the third phone"))
            all(3)
            val after = everyone.map { LiveSync.mark(it)!! }
            assertEquals("$after", 4, after.toSet().size)
            assertTrue("$after", "A" == after[0])
        } finally {
            b.close(); d.close()
        }
    }

    @Test
    fun `a lost parcel is sent again and nothing arrives twice`() = runBlocking {
        both(1)
        relay.loseNext = true
        phone.khataDao().insertParty(Party(name = "Lost once"))
        both(1)
        assertTrue(pc.khataDao().allPartiesForBackup().none { it.name == "Lost once" })
        clock += LiveCloud.RESEND_MS + 1
        both(2)
        assertEquals(1, pc.khataDao().allPartiesForBackup().count { it.name == "Lost once" })
    }

    @Test
    fun `no internet is said as such and changes nothing`() = runBlocking {
        relay.down = true
        assertEquals(LiveCloud.Result.Unreachable, LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false })
        relay.down = false
        both()
    }

    @Test
    fun `closed until the relay has an address, and the pass needs the key`() = runBlocking {
        assertEquals(LiveCloud.Result.Off, LiveCloud.round(phone.syncSql(), relay, null, clock) { false })
        val id = LiveSync.identity(phone.syncSql())!!
        assertEquals(LiveCloud.token(id.key, id.book), LiveCloud.token(id.key, id.book))
        assertFalse(LiveCloud.token(LiveSync.newHex(32), id.book) == LiveCloud.token(id.key, id.book))
        assertEquals(64, LiveCloud.token(id.key, id.book).length)
    }

    @Test
    fun `nothing goes to the relay until the owner turns it on, nor while live sync is stopped for everyone`() = runBlocking {
        phone.khataDao().setSetting(BookSettings.LIVE_RELAY, null)
        assertEquals(LiveCloud.Result.Off, LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false })
        assertEquals(LiveCloud.Result.Halted, LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock) { true })
        assertEquals(0, relay.calls)
        assertTrue(relay.pass == null)
    }

    @Test
    fun `a relay that forgot this device is greeted again, not asked for ever`() = runBlocking {
        both()
        // The relay's box for the book was emptied (a month unused, or made again).
        relay.devices.clear()
        relay.parcels.clear()
        phone.khataDao().insertParty(Party(name = "After the relay forgot"))
        both()
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "After the relay forgot" })
    }

    // ---- Several relays (22 Oct 2026: Cloudflare, then Supabase, then the owner's hosting) ----

    private val second = FakeRelay()
    private val roads = listOf(LiveCloud.Road("https://one.test", ""), LiveCloud.Road("https://two.test", "two"))

    /** Calls to "one.test" go to [relay], to "two.test" to [second]; [cutOne] cuts this caller off the first. */
    private fun router(cutOne: () -> Boolean) = LiveCloud.Http { m, url, h, b ->
        if (url.startsWith("https://one.test")) {
            if (cutOne()) throw java.io.IOException("cannot reach the first relay")
            relay.call(m, url, h, b)
        } else second.call(m, url, h, b)
    }

    private var phoneCut = false
    private var pcCut = false
    private val phoneWay = router { phoneCut }
    private val pcWay = router { pcCut }

    private suspend fun allRoads(times: Int = 2, readOthers: Boolean = true) = repeat(times) {
        clock += 1000
        val a = LiveCloud.roundAll(phone.syncSql(), phoneWay, roads, clock, { false }, readOthers)
        val b = LiveCloud.roundAll(pc.syncSql(), pcWay, roads, clock, { false }, readOthers)
        assertTrue("$a", a is LiveCloud.Result.Done)
        assertTrue("$b", b is LiveCloud.Result.Done)
    }

    @Test
    fun `when the first relay is down for both, the second carries the book`() = runBlocking {
        relay.down = true
        phone.khataDao().insertParty(Party(name = "Through the second"))
        pc.khataDao().insertParty(Party(name = "Back through the second"))
        allRoads()
        assertEquals(setOf("Through the second", "Back through the second"), phone.khataDao().allPartiesForBackup().map { it.name }.toSet())
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
        assertTrue(second.parcels.isNotEmpty() || second.seq > 0)
        assertEquals(null, relay.pass)
    }

    @Test
    fun `a phone that cannot reach the first relay still meets a computer that can`() = runBlocking {
        phoneCut = true
        phone.khataDao().insertParty(Party(name = "Phone, cut off the first"))
        pc.khataDao().insertParty(Party(name = "Computer, on the first"))
        allRoads(3)
        val names = setOf("Phone, cut off the first", "Computer, on the first")
        assertEquals(names, phone.khataDao().allPartiesForBackup().map { it.name }.toSet())
        assertEquals(names, pc.khataDao().allPartiesForBackup().map { it.name }.toSet())
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
        // Later changes still go both ways while the phone stays cut off.
        pc.khataDao().insertParty(Party(name = "Later, from the computer"))
        allRoads(2, readOthers = false)
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Later, from the computer" })
    }

    @Test
    fun `the later relays are not asked while the first answers and nobody uses them`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Only the first"))
        allRoads(2, readOthers = false)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Only the first" })
        assertEquals(0, second.calls)
        assertEquals(null, second.pass)
    }

    @Test
    fun `a relay that is down or halted is passed over, and with none left the round says unreachable`() = runBlocking {
        relay.down = true
        second.down = true
        assertEquals(LiveCloud.Result.Unreachable, LiveCloud.roundAll(phone.syncSql(), phoneWay, roads, clock, { false }))
        // The kill switch is the book's own state: no relay is asked at all.
        second.down = false
        val before = second.calls
        assertEquals(LiveCloud.Result.Halted, LiveCloud.roundAll(phone.syncSql(), phoneWay, roads, clock, { true }))
        assertEquals(before, second.calls)
        assertEquals(LiveCloud.Result.Off, LiveCloud.roundAll(phone.syncSql(), phoneWay, emptyList(), clock, { false }))
        // A relay answering 503 is that relay's trouble, never "stopped for everyone": the next relay carries the book,
        // and with none left the round says unreachable, not halted (23 Oct 2026, the second review).
        relay.down = false; relay.halted = true
        assertTrue(LiveCloud.roundAll(phone.syncSql(), phoneWay, roads, clock, { false }) is LiveCloud.Result.Done)
        second.halted = true
        assertEquals(LiveCloud.Result.Unreachable, LiveCloud.roundAll(phone.syncSql(), phoneWay, roads, clock, { false }))
    }

    @Test
    fun `a relay that forgot the phone is told its wake-up address again after one hello, not refused for ever`() = runBlocking {
        val sql = phone.syncSql()
        val dev = LiveSync.identity(sql)!!.device.take(16).padEnd(16, '0')
        assertTrue(LiveCloud.tellWake(sql, "token-one-0123456789", relay, "https://relay.test") { false })
        relay.forget()
        val calls = relay.calls
        assertTrue(LiveCloud.tellWake(sql, "token-two-0123456789", relay, "https://relay.test") { false })
        assertEquals("token-two-0123456789", relay.wakes[dev])
        // Past the relay's door: the hello and the wake again (the refused wake never got that far).
        assertEquals(2, relay.calls - calls)
    }

    @Test
    fun `a pairing after live sync was turned off is a new conversation at the relay`() = runBlocking {
        val before = LiveSync.identity(phone.syncSql())!!.book
        LiveSync.disable(phone.syncSql(), LiveSync.OFF_RESTORED)
        val after = LiveSync.enable(phone.syncSql(), "install-phone", "Phone").book
        assertTrue(before != after)
        // Joining a computer's book keeps that book's id, as before.
        val pcId = LiveSync.identity(pc.syncSql())!!
        assertEquals(pcId.book, LiveSync.enable(phone.syncSql(), "install-phone", "Phone", adopt = pcId.book to pcId.key).book)
    }

    @Test
    fun `each relay gets its own pass from the key, and cloudflare keeps the first`() = runBlocking {
        val id = LiveSync.identity(phone.syncSql())!!
        val cf = LiveCloud.token(id.key, id.book, LiveCloud.RELAY)
        assertEquals(LiveCloud.token(id.key, id.book), cf)
        val host = LiveCloud.token(id.key, id.book, LiveCloud.HOSTING)
        val sb = LiveCloud.token(id.key, id.book, LiveCloud.SUPABASE)
        assertTrue(cf != host && host != sb && cf != sb)
        assertEquals(64, host.length)
        // The same on the other device: the pass is the key's and the host's, nothing of the device.
        assertEquals(host, LiveCloud.token(LiveSync.identity(pc.syncSql())!!.key, id.book, LiveCloud.HOSTING + "/"))
        // Two relays met in one session: each saw its own pass.
        allRoads(1)
        assertTrue(relay.pass != null && second.pass != null && relay.pass != second.pass)
    }

    @Test
    fun `cloudflare keeps the cursors it had before there were several relays`() = runBlocking {
        both()
        val id = LiveSync.identity(phone.syncSql())!!
        val keys = phone.syncSql().read { rows("SELECT k FROM sync_meta WHERE k LIKE 'cloud%'", emptyList(), "T").map { it[0] as String } }
        assertTrue(keys.toString(), keys.contains("cloud_after:" + id.book))
        assertTrue(keys.none { it.startsWith("cloud__") })
    }

    // ---- The 23 Oct 2026 review: the relays' limits, the resend base, the cursor ----

    private suspend fun meta(db: KhataDatabase, key: String): String? =
        db.syncSql().read { rows("SELECT v FROM sync_meta WHERE k = ?", listOf(key), "T").firstOrNull()?.get(0) as String? }

    @Test
    fun `a relay that refused a hello for its capacity is not asked again for an hour`() = runBlocking {
        relay.helloRefusal = 507 to "full"
        val first = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
        assertEquals(LiveCloud.Result.Refused("full"), first)
        assertEquals(1, relay.hellos)
        // Every round of the next hour is refused here at once, the relay not asked (each refused hello cost it a scan).
        repeat(30) {
            clock += 60_000
            assertEquals(LiveCloud.Result.Refused("full"), LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false })
        }
        assertEquals(1, relay.hellos)
        // The wake-up address waits as well.
        assertFalse(LiveCloud.tellWake(phone.syncSql(), "token-one-0123456789", relay, "https://relay.test", clock) { false })
        assertEquals(1, relay.hellos)
        // After the hour it is asked again, and a relay with room takes the book.
        relay.helloRefusal = null
        clock += LiveCloud.REFUSED_MS
        phone.khataDao().insertParty(Party(name = "After the hour"))
        both(3)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "After the hour" })
    }

    @Test
    fun `a relay at its day limit still gives the page, and the parcel goes by the next relay`() = runBlocking {
        allRoads(2)
        pc.khataDao().insertParty(Party(name = "Before the limit"))
        allRoads(1)
        relay.dayFull = true
        phone.khataDao().insertParty(Party(name = "Past the limit"))
        clock += 1000
        // The first relay alone: the parcel is refused, the computer's page still comes.
        val alone = LiveCloud.round(phone.syncSql(), phoneWay, "https://one.test", clock) { false }
        assertEquals(LiveCloud.Result.Refused("day-limit"), alone)
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Before the limit" })
        // Over every road the round carries on through the second relay, and the computer reads it there.
        allRoads(3)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Past the limit" })
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
    }

    @Test
    fun `a whole page moves the cursor past this device's own parcels, and nothing is missed`() = runBlocking {
        relay.sayThrough = true
        val book = LiveSync.identity(phone.syncSql())!!.book
        repeat(3) { i ->
            phone.khataDao().insertParty(Party(name = "Own $i"))
            clock += 1000
            assertTrue(LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
        }
        // Only its own parcels at the relay: its cursor still stands at the book's last number, not at 0.
        assertEquals(relay.seq.toString(), meta(phone, "cloud_after:$book"))
        pc.khataDao().insertParty(Party(name = "From the computer"))
        both(3)
        assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "From the computer" })
        assertEquals(3, pc.khataDao().allPartiesForBackup().count { it.name.startsWith("Own ") })
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
    }

    @Test
    fun `a peer silent for over a week holds nothing back, and is caught up when it comes back`() = runBlocking {
        both(3)
        // The computer goes quiet past the relays' week: the phone writes on, and resends nothing for it every hour.
        clock += LiveCloud.STALE_MS + 60_000
        phone.khataDao().insertParty(Party(name = "While away"))
        val before = relay.seq
        repeat(10) {
            clock += 2 * 3_600_000L
            assertTrue(LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false } is LiveCloud.Result.Done)
        }
        // At most the day's word that the phone is still there, never a resend an hour.
        assertTrue("posted ${relay.seq - before}", relay.seq - before <= 1)
        // The computer comes back: it is sent what it lacks.
        both(4)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "While away" })
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
    }

    @Test
    fun `a resend is one page, and a peer taking it is given the next at once`() = runBlocking {
        phone.khataDao().insertParties((1..1100).map { Party(name = "Bulk $it") })
        // Posted page after page while nobody answers.
        repeat(6) {
            clock += 1000
            LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
        }
        assertTrue(relay.seq >= 3)
        // The relay lost them all; the computer never acknowledged: after the wait, one page goes, not the backlog.
        relay.parcels.clear()
        clock += LiveCloud.RESEND_MS + 1
        val seq = relay.seq
        val r = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
        assertTrue("$r", r is LiveCloud.Result.Done && !r.more)
        assertEquals(seq + 1, relay.seq)
        clock += 1000
        LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock) { false }
        assertEquals(seq + 1, relay.seq)
        // The computer takes it and says so: the next page follows round by round until it has every one.
        both(10)
        assertEquals(1100, pc.khataDao().allPartiesForBackup().count { it.name.startsWith("Bulk ") })
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
    }
}
