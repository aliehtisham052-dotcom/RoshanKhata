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
}
