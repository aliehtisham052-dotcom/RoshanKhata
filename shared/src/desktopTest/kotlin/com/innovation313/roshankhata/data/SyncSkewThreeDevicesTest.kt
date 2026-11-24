package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * Three devices on the relay (24 Nov 2026, BC-259): one of them runs another version. The round that carried the good
 * device's changes must still say that the other was refused (it said "Live" and hid it), without stopping the good
 * device's sync, and a later quiet round must keep saying it until that device is taken again or removed.
 */
class SyncSkewThreeDevicesTest {

    /** A relay in a few lines: hello, send, take, ack (no "round": the app falls back to these). */
    private class Relay : LiveCloud.Http {
        val parcels = ArrayList<Triple<Long, String, ByteArray>>()
        var seq = 0L
        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            val dev = headers["x-device"]!!
            val action = url.substringAfterLast('/').substringBefore('?')
            fun ok(o: JSONObject) = 200 to o.put("ok", true).toString().toByteArray()
            return when (action) {
                "hello" -> ok(JSONObject().put("top", seq))
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
    private lateinit var third: KhataDatabase
    private val relay = Relay()
    private var clock = 1_000_000L
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity
    private lateinit var thirdId: LiveSync.Identity
    private val roads = listOf(LiveCloud.Road("https://relay.test", ""))
    private val old = "schema ${KHATA_DB_VERSION - 1}"

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-skew-three").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        third = DesktopDatabase.open(dir.resolve("third.db"))
        runBlocking {
            for (d in listOf(phone, pc, third)) d.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            phoneId = LiveSync.enable(phone.syncSql(), "install-phone", "Phone")
            pcId = LiveSync.enable(pc.syncSql(), "install-pc", "PC", adopt = phoneId.book to phoneId.key)
            thirdId = LiveSync.enable(third.syncSql(), "install-third", "Old tablet", adopt = phoneId.book to phoneId.key)
            for ((db, others) in listOf(
                phone to listOf(pcId to "PC", thirdId to "Old tablet"),
                pc to listOf(phoneId to "Phone", thirdId to "Old tablet"),
                third to listOf(phoneId to "Phone", pcId to "PC")
            )) for ((o, name) in others) LiveSync.putPeer(db.syncSql(), o.device, name = name, sent = 0, recv = 0)
        }
    }

    @After
    fun close() {
        phone.close(); pc.close(); third.close()
        dir.deleteRecursively()
    }

    private fun leave(msg: JSONObject, from: String) {
        relay.seq++
        relay.parcels.add(Triple(relay.seq, from, LiveWire.seal(phoneId.key, msg.toString().toByteArray(Charsets.UTF_8), LiveWire.Road.RELAY)))
    }

    /** The old tablet's message: another schema number and changes this version could not read. */
    private suspend fun leaveSkewed() {
        third.khataDao().insertParty(Party(name = "Carol"))
        val m = JSONObject(LiveCloud.message(third.syncSql(), 0)!!.toString())
        m.getJSONObject("parcel").put("schema", KHATA_DB_VERSION - 1).put("c", "changes in a shape this version never saw")
        leave(m, thirdId.device)
    }

    private suspend fun leaveGood() {
        pc.khataDao().insertParty(Party(name = "Bilal"))
        leave(LiveCloud.message(pc.syncSql(), 0)!!, pcId.device)
    }

    private suspend fun names() = phone.khataDao().allPartiesForBackup().map { it.name }

    @Test
    fun `a refused device is named when another device's changes were taken in the same round, whichever came first`() = runBlocking {
        leaveSkewed()
        leaveGood()
        val a = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock++) { false }
        assertTrue("$a", a is LiveCloud.Result.Done)
        assertEquals(old, (a as LiveCloud.Result.Done).refused)
        assertEquals(1, a.took)
        // The good device's changes were written; the refused one's were not.
        assertEquals(listOf("Bilal"), names())
        assertEquals(mapOf(thirdId.device to old), LiveSync.skewed(phone.syncSql()))
    }

    @Test
    fun `the good device first and the refused one after says the same`() = runBlocking {
        leaveGood()
        leaveSkewed()
        val a = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock++) { false }
        assertEquals(old, (a as LiveCloud.Result.Done).refused)
        assertEquals(1, a.took)
        assertEquals(listOf("Bilal"), names())
    }

    @Test
    fun `a round with nobody refused says nothing, and roundAll carries the refusal`() = runBlocking {
        leaveGood()
        val clean = LiveCloud.round(phone.syncSql(), relay, "https://relay.test", clock++) { false }
        assertNull((clean as LiveCloud.Result.Done).refused)
        // roundAll hands on the refusal of its first road too.
        leaveSkewed()
        leaveGood()
        val all = LiveCloud.roundAll(phone.syncSql(), relay, roads, clock++, { false })
        assertEquals(old, (all as LiveCloud.Result.Done).refused)
    }

    @Test
    fun `a later quiet round keeps naming the device until it is taken again, and a removed device is not named`() = runBlocking {
        leaveSkewed()
        leaveGood()
        LiveCloud.roundAll(phone.syncSql(), relay, roads, clock++, { false })
        // Nothing new at the relay: the round is Done and quiet, and the refusal is still the book's news.
        val quiet = LiveCloud.roundAll(phone.syncSql(), relay, roads, clock++, { false })
        assertTrue("$quiet", quiet is LiveCloud.Result.Done)
        assertEquals(old, (quiet as LiveCloud.Result.Done).refused)
        assertEquals(old, LiveCloud.skewNow(phone.syncSql()))
        // The old tablet is updated: its message of this version is taken and the note goes.
        third.khataDao().insertParty(Party(name = "Dina"))
        leave(LiveCloud.message(third.syncSql(), 0)!!, thirdId.device)
        val healed = LiveCloud.roundAll(phone.syncSql(), relay, roads, clock++, { false })
        assertNull((healed as LiveCloud.Result.Done).refused)
        assertNull(LiveCloud.skewNow(phone.syncSql()))
        // A note outliving its device (removePeer does not clear it) is not a reason to say "update" again.
        leaveSkewedAgain()
        LiveCloud.roundAll(phone.syncSql(), relay, roads, clock++, { false })
        assertEquals(old, LiveCloud.skewNow(phone.syncSql()))
        LiveSync.removePeer(phone.syncSql(), thirdId.device)
        assertNull(LiveCloud.skewNow(phone.syncSql()))
    }

    private suspend fun leaveSkewedAgain() {
        third.khataDao().insertParty(Party(name = "Esra"))
        val m = JSONObject(LiveCloud.message(third.syncSql(), 0)!!.toString())
        m.getJSONObject("parcel").put("schema", KHATA_DB_VERSION - 1).put("c", "unreadable")
        leave(m, thirdId.device)
    }
}
