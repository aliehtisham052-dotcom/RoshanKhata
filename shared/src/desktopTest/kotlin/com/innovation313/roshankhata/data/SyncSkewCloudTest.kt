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
 * The relay road between two versions (24 Nov 2026, stage 5 / B10): a message of another schema number is read to its
 * head, refused with its reason (the round says "schema N", never "Live"), takes no device, no acknowledgement and no
 * wish to be sent again from it, and the sender's own cursor stays, so one version later everything arrives.
 */
class SyncSkewCloudTest {

    /** A relay in a few lines: hello, send, take, ack (no "round": the app falls back to these). */
    private class Relay : LiveCloud.Http {
        val parcels = ArrayList<Triple<Long, String, ByteArray>>()
        val devices = HashSet<String>()
        var seq = 0L
        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            val dev = headers["x-device"]!!
            val action = url.substringAfterLast('/').substringBefore('?')
            fun ok(o: JSONObject) = 200 to o.put("ok", true).toString().toByteArray()
            return when (action) {
                "hello" -> { devices += dev; ok(JSONObject().put("top", seq)) }
                "send" -> { seq++; parcels.add(Triple(seq, dev, body!!)); ok(JSONObject().put("seq", seq)) }
                "take" -> {
                    val after = url.substringAfter("after=").toLong()
                    val arr = JSONArray()
                    parcels.filter { it.first > after && it.second != dev }.forEach {
                        arr.put(JSONObject().put("seq", it.first).put("from", it.second).put("body", Base64.getEncoder().encodeToString(it.third)))
                    }
                    ok(JSONObject().put("parcels", arr).put("more", false))
                }
                "ack" -> ok(JSONObject())
                else -> 404 to JSONObject().put("ok", false).put("why", "no-such-path").toString().toByteArray()
            }
        }
    }

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val relay = Relay()
    private var clock = 1_000_000L
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-skew-cloud").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        runBlocking {
            phone.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            pc.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            phoneId = LiveSync.enable(phone.syncSql(), "install-phone", "Phone")
            pcId = LiveSync.enable(pc.syncSql(), "install-pc", "PC", adopt = phoneId.book to phoneId.key)
            LiveSync.putPeer(pc.syncSql(), phoneId.device, name = "Phone", sent = 0, recv = 0)
            LiveSync.putPeer(phone.syncSql(), pcId.device, name = "PC", sent = 0, recv = 0)
        }
    }

    @After
    fun close() {
        phone.close(); pc.close()
        dir.deleteRecursively()
    }

    /** What the computer of another version leaves at the relay: its own message, but another schema number and a body this version cannot read. */
    private fun leave(msg: JSONObject, from: String) {
        relay.seq++
        relay.parcels.add(Triple(relay.seq, from, LiveWire.seal(phoneId.key, msg.toString().toByteArray(Charsets.UTF_8), LiveWire.Road.RELAY)))
    }

    private suspend fun phoneRound() = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock++) { false }

    @Test
    fun `a message of another schema is refused by name, takes nothing, and the same message of this version arrives whole`() = runBlocking {
        pc.khataDao().insertParty(Party(name = "Bilal"))
        val ghost = "cd".repeat(8)
        val good = LiveCloud.message(pc.syncSql(), 0)!!
        val skewed = JSONObject(good.toString())
        skewed.getJSONObject("parcel").put("schema", KHATA_DB_VERSION - 1).put("c", "changes in a shape this version never saw")
        skewed.put("joins", JSONObject().put(ghost, JSONObject().put("name", "Ghost")))
        skewed.put("acks", JSONObject().put(phoneId.device, 9_999))
        leave(skewed, pcId.device)
        val before = LiveSync.state(phone.syncSql()).peers.first { it.device == pcId.device }
        val r = phoneRound()
        assertEquals(LiveCloud.Result.Refused("schema ${KHATA_DB_VERSION - 1}"), r)
        assertTrue(phone.khataDao().allPartiesForBackup().isEmpty())
        assertTrue(LiveSync.state(phone.syncSql()).peers.none { it.device == ghost })
        assertEquals(before, LiveSync.state(phone.syncSql()).peers.first { it.device == pcId.device })
        assertEquals(mapOf(pcId.device to "schema ${KHATA_DB_VERSION - 1}"), LiveSync.skewed(phone.syncSql()))
        // The computer is updated: the same changes, written again this version's way, arrive.
        leave(good, pcId.device)
        val again = phoneRound()
        assertTrue("$again", again is LiveCloud.Result.Done)
        assertFalse(phone.khataDao().allPartiesForBackup().isEmpty())
        assertEquals(listOf("Bilal"), phone.khataDao().allPartiesForBackup().map { it.name })
        assertTrue(LiveSync.skewed(phone.syncSql()).isEmpty())
    }
}
