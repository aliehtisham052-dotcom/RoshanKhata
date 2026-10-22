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
        val a = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock)
        val b = LiveCloud.round(pc.syncSql(), relay, "https://relay.test", clock)
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
        assertEquals(LiveCloud.Result.Unreachable, LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock))
        relay.down = false
        both()
    }

    @Test
    fun `closed until the relay has an address, and the pass needs the key`() = runBlocking {
        assertEquals(LiveCloud.Result.Off, LiveCloud.round(phone.syncSql(), relay, null, clock))
        val id = LiveSync.identity(phone.syncSql())!!
        assertEquals(LiveCloud.token(id.key, id.book), LiveCloud.token(id.key, id.book))
        assertFalse(LiveCloud.token(LiveSync.newHex(32), id.book) == LiveCloud.token(id.key, id.book))
        assertEquals(64, LiveCloud.token(id.key, id.book).length)
    }
}
