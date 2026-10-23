package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * A device removed from the book (the phone's Devices screen and the
 * computer's Live sync window, 23 Oct 2026) holds nothing up afterwards; the
 * relays after the first are read by time; and a device whose parcels come by
 * a relay without a live line keeps the HTTP clock (the review's R83).
 */
class LiveDevicesTest {

    /** cloud/worker's hello and one-call round, in a few lines (as LiveCloudTest's relay). */
    private class Relay : LiveCloud.Http {
        var pass: String? = null
        val devices = HashMap<String, Long>()
        val parcels = ArrayList<Triple<Long, String, ByteArray>>()
        var seq = 0L
        var calls = 0

        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
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
                "round" -> {
                    val o = JSONObject(String(body!!))
                    if (!o.isNull("upTo")) {
                        devices[dev] = maxOf(devices[dev]!!, o.getLong("upTo"))
                        parcels.removeAll { p -> devices.filterKeys { it != p.second }.values.all { it >= p.first } }
                    }
                    var sent: Long? = null
                    if (!o.isNull("parcel")) {
                        seq++
                        parcels.add(Triple(seq, dev, B64.decode(o.getString("parcel"))))
                        sent = seq
                    }
                    val after = o.getLong("after")
                    val arr = JSONArray()
                    parcels.filter { it.first > after && it.second != dev }.forEach {
                        arr.put(JSONObject().put("seq", it.first).put("from", it.second).put("body", Base64.getEncoder().encodeToString(it.third)))
                    }
                    ok(JSONObject().put("seq", sent ?: JSONObject.NULL).put("parcels", arr).put("more", false))
                }
                else -> no(404, "no-such-path")
            }
        }
    }

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private lateinit var phoneDev: String
    private lateinit var pcDev: String
    private val first = Relay()
    private val second = Relay()
    private val roads = listOf(LiveCloud.Road("https://one.test", ""), LiveCloud.Road("https://two.test", "hs"))
    private var clock = 5_000_000L
    private var phoneCut = false

    private fun way(cut: () -> Boolean) = LiveCloud.Http { m, url, h, b ->
        if (url.startsWith("https://one.test")) {
            if (cut()) throw java.io.IOException("cannot reach the first relay")
            first.call(m, url, h, b)
        } else second.call(m, url, h, b)
    }
    private val phoneWay = way { phoneCut }
    private val pcWay = way { false }

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-devices").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        runBlocking {
            phone.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            pc.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            val id = LiveSync.enable(phone.syncSql(), "install-phone", "Phone")
            val pcId = LiveSync.enable(pc.syncSql(), "install-pc", "PC", adopt = id.book to id.key)
            phoneDev = id.device
            pcDev = pcId.device
            LiveSync.putPeer(pc.syncSql(), id.device, name = "Phone", sent = 0, recv = 0)
            LiveSync.putPeer(phone.syncSql(), pcId.device, name = "PC", sent = 0, recv = 0)
        }
    }

    @After
    fun close() {
        phone.close(); pc.close()
        dir.deleteRecursively()
    }

    private suspend fun rounds(times: Int, readOthers: Boolean = true) = repeat(times) {
        clock += 1000
        val a = LiveCloud.roundAll(phone.syncSql(), phoneWay, roads, clock, { false }, readOthers)
        val b = LiveCloud.roundAll(pc.syncSql(), pcWay, roads, clock, { false }, readOthers)
        assertTrue("$a", a is LiveCloud.Result.Done)
        assertTrue("$b", b is LiveCloud.Result.Done)
    }

    @Test
    fun `the later relays are due by time, two minutes after they were last read`() = runBlocking {
        val sql = pc.syncSql()
        assertTrue(LiveCloud.othersDue(sql, clock))
        clock += 1000
        assertTrue(LiveCloud.roundAll(sql, pcWay, roads, clock, { false }, readOthers = true) is LiveCloud.Result.Done)
        assertFalse(LiveCloud.othersDue(sql, clock + 1000))
        assertFalse(LiveCloud.othersDue(sql, clock + LiveCloud.OTHERS_MS - 1))
        assertTrue(LiveCloud.othersDue(sql, clock + LiveCloud.OTHERS_MS))
        // A round that did not read them does not move the time.
        assertTrue(LiveCloud.roundAll(sql, pcWay, roads, clock + 5000, { false }, readOthers = false) is LiveCloud.Result.Done)
        assertTrue(LiveCloud.othersDue(sql, clock + LiveCloud.OTHERS_MS))
        // A book that does not sync has nothing to read.
        LiveSync.disable(sql)
        assertFalse(LiveCloud.othersDue(sql, clock + LiveCloud.OTHERS_MS))
    }

    @Test
    fun `a device whose parcels come by the hosting is said to be on a road without a line`() = runBlocking {
        assertFalse(LiveCloud.peerOnLinelessRoad(pc.syncSql()))
        // The phone cannot reach the first relay: its parcels go to the second, and the computer reads them there.
        phoneCut = true
        phone.khataDao().insertParty(Party(name = "From the phone, by the hosting"))
        rounds(2)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "From the phone, by the hosting" })
        assertTrue(LiveCloud.peerOnLinelessRoad(pc.syncSql()))
        // Removed from the computer's book, the phone no longer keeps it on the quick clock.
        LiveForget.device(pc.syncSql(), phoneDev)
        assertFalse(LiveCloud.peerOnLinelessRoad(pc.syncSql()))
        assertTrue(LiveSync.state(pc.syncSql()).peers.isEmpty())
    }

    @Test
    fun `both on the first relay is not a road without a line`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "On the first"))
        rounds(2, readOthers = false)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "On the first" })
        assertFalse(LiveCloud.peerOnLinelessRoad(pc.syncSql()))
        assertFalse(LiveCloud.peerOnLinelessRoad(phone.syncSql()))
    }

    @Test
    fun `a removed joiner no longer holds back what this device tells the others`() = runBlocking {
        val sql = phone.syncSql()
        val joiner = "0123456789abcdef0123456789abcdef"
        // The phone gave the book to a second phone while it knew the computer: what it tells the computer of its
        // reading is held at the copy's line until the computer has heard of the joiner.
        LiveCrew.holdForCopy(sql, LiveCrew.offerPeers(sql))
        LiveCrew.joined(sql, joiner, "Phone two", from = LiveSync.lastSeq(sql))
        assertEquals(0L, LiveCrew.ackFor(sql, pcDev, 50L))
        assertTrue(LiveCrew.joinsOut(sql)?.has(joiner) == true)
        // The second phone was lost and never came back: the owner removes it.
        LiveForget.device(sql, joiner)
        assertEquals(50L, LiveCrew.ackFor(sql, pcDev, 50L))
        assertNull(LiveCrew.joinsOut(sql))
        assertFalse(LiveSync.state(sql).peers.any { it.device == joiner })
        // The computer is still this phone's device.
        assertTrue(LiveSync.state(sql).peers.any { it.device == pcDev })
    }

    @Test
    fun `removing the phone's computer ends the wifi road to it, another removal leaves it`() = runBlocking {
        val sql = phone.syncSql()
        LiveRound.setTarget(sql, LiveRound.Target(listOf("192.168.1.20"), LiveWire.PORT, 0L, "PC", device = pcDev))
        LiveSync.putPeer(sql, "fedcba9876543210fedcba9876543210", name = "Old phone", sent = 0, recv = 0)
        LiveForget.device(sql, "fedcba9876543210fedcba9876543210")
        assertEquals(pcDev, LiveRound.target(sql)?.device)
        LiveForget.device(sql, pcDev)
        assertNull(LiveRound.target(sql))
        assertTrue(LiveSync.state(sql).peers.isEmpty())
    }
}
