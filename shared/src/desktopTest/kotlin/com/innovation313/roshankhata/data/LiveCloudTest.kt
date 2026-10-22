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
        /** The next sent parcel is lost (the relay forgot it). */
        var loseNext = false
        /** Calls answered past the pass and the hello check. */
        var calls = 0
        /** Each device's wake-up address, as the worker's devices.fcm column. */
        val wakes = HashMap<String, String?>()
        /** A relay uploaded before "round" existed: it answers 404 to it, and the app falls back to three calls. */
        var noRound = false
        var rounds = 0

        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            if (down) throw java.io.IOException("no internet")
            val token = headers["authorization"]!!.removePrefix("RK ")
            val dev = headers["x-device"]!!
            val action = url.substringAfterLast('/').substringBefore('?')
            fun ok(o: JSONObject) = 200 to o.put("ok", true).toString().toByteArray()
            fun no(code: Int, why: String) = code to JSONObject().put("ok", false).put("why", why).toString().toByteArray()
            if (pass == null) { if (action != "hello") return no(403, "say-hello-first"); pass = token }
            else if (pass != token) return no(403, "wrong-pass")
            if (dev !in devices && action != "hello") return no(403, "say-hello-first")
            calls++
            return when (action) {
                "hello" -> { devices.putIfAbsent(dev, 0L); ok(JSONObject().put("top", seq)) }
                "send" -> {
                    seq++
                    if (loseNext) loseNext = false else parcels.add(Triple(seq, dev, body!!))
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
                    if (!o.isNull("parcel")) {
                        seq++
                        if (loseNext) loseNext = false else parcels.add(Triple(seq, dev, B64.decode(o.getString("parcel"))))
                        sent = seq
                    }
                    val after = o.getLong("after")
                    val arr = JSONArray()
                    parcels.filter { it.first > after && it.second != dev }.forEach {
                        arr.put(JSONObject().put("seq", it.first).put("from", it.second).put("body", Base64.getEncoder().encodeToString(it.third)))
                    }
                    ok(JSONObject().put("seq", sent ?: JSONObject.NULL).put("parcels", arr).put("more", false))
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
        phone.close(); pc.close()
        dir.deleteRecursively()
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
        both()
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
        phone.khataDao().insertParty(Party(name = "Before"))
        both(2)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Before" })
        // The computer's book went back (rebuilt from an older copy): it has read nothing of the phone's log.
        val phoneDev = LiveSync.identity(phone.syncSql())!!.device
        LiveSync.putPeer(pc.syncSql(), phoneDev, recv = 0L)
        phone.khataDao().insertParty(Party(name = "After the gap"))
        // The phone's parcel starts past 0: a gap at the computer, said back as "need"; the phone starts again at 0.
        both(4)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "After the gap" })
        assertEquals(1, pc.khataDao().allPartiesForBackup().count { it.name == "Before" })
        assertEquals(LiveSync.fingerprint(phone.syncSql()), LiveSync.fingerprint(pc.syncSql()))
        // And a conflict in a money table is counted for the bell, one not in it is not.
        assertEquals(0, LiveSync.moneyConflicts(pc.syncSql()))
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
}
