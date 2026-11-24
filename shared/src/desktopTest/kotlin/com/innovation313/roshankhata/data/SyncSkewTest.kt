package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Live sync between two devices on another version of the app (24 Nov 2026, stage 5 / B10): a device N (this build)
 * and a device N-1 (another schema number or another parcel protocol). The rule, for every road: the other version's
 * words are read only as far as their head, refused with the reason, and nothing of them is taken — no change, no
 * pointer, no device it announces, no word on what it heard. Both sides keep their logs, so when both run one version
 * the sender's next parcel carries everything it never got. CI only (the desktop test source set).
 */
class SyncSkewTest {

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val phoneSql get() = phone.syncSql()
    private val pcSql get() = pc.syncSql()
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity
    private val older = KHATA_DB_VERSION - 1

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-skew").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        runBlocking {
            phoneId = LiveSync.enable(phoneSql, install = "install-phone", name = "Phone")
            pcId = LiveSync.enable(pcSql, install = "install-pc", name = "PC", adopt = phoneId.book to phoneId.key)
            LiveSync.putPeer(pcSql, phoneId.device, name = "Phone", sent = 0, recv = 0)
            LiveSync.putPeer(phoneSql, pcId.device, name = "PC", sent = 0, recv = 0)
        }
    }

    @After
    fun close() {
        phone.close(); pc.close()
        dir.deleteRecursively()
    }

    private suspend fun peerOf(sql: SyncSql, device: String) = LiveSync.state(sql).peers.first { it.device == device }

    /** The phone's next parcel for the computer, as the phone of another version would write it: another number, any shape of changes. */
    private suspend fun fromOlderPhone(schema: Int = older, protocol: Int = LiveSync.PROTOCOL): JSONObject =
        LiveSync.export(phoneSql, peerOf(pcSql, phoneId.device).recv).toJson().apply {
            put("schema", schema); put("p", protocol)
            // Changes this version could not read at all: it must not matter, the head alone is read.
            put("c", JSONArray().put(JSONObject().put("t", "parties").put("u", 12345).put("zz", JSONArray())))
        }

    /** A normal round of the engine, as LiveSyncTest's. */
    private suspend fun round() {
        val toPc = LiveSync.export(phoneSql, peerOf(pcSql, phoneId.device).recv)
        assertTrue(LiveSync.applyFrom(pcSql, toPc, theirAck = peerOf(phoneSql, pcId.device).recv) is LiveSync.Applied.Done)
        val toPhone = LiveSync.export(pcSql, peerOf(phoneSql, pcId.device).recv)
        assertTrue(LiveSync.applyFrom(phoneSql, toPhone, theirAck = peerOf(pcSql, phoneId.device).recv) is LiveSync.Applied.Done)
    }

    @Test
    fun `a parcel of another schema is refused with its number, nothing moves, and the next normal round catches up`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        val before = peerOf(pcSql, phoneId.device)
        val parcel = LiveSync.Parcel.of(fromOlderPhone())
        assertEquals(older, parcel.schema)
        val r = LiveSync.applyFrom(pcSql, parcel, theirAck = 0, now = 5_000)
        assertEquals(LiveSync.Applied.Refused("schema $older"), r)
        assertTrue(pc.khataDao().allPartiesForBackup().isEmpty())
        // Not heard: the pointers and the clock are as they were, so nothing loops on it and "never heard" is not "Live".
        assertEquals(before, peerOf(pcSql, phoneId.device))
        assertEquals(mapOf(phoneId.device to "schema $older"), LiveSync.skewed(pcSql))
        // Both on one version again: the same log, from the same line, arrives whole and the note is gone.
        round()
        assertEquals(listOf("Ali"), pc.khataDao().allPartiesForBackup().map { it.name })
        assertTrue(LiveSync.skewed(pcSql).isEmpty())
    }

    @Test
    fun `a parcel of another protocol is refused, not thrown`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        val parcel = LiveSync.Parcel.of(fromOlderPhone(schema = KHATA_DB_VERSION, protocol = LiveSync.PROTOCOL + 1))
        assertEquals(LiveSync.Applied.Refused("protocol ${LiveSync.PROTOCOL + 1}"), LiveSync.applyFrom(pcSql, parcel, theirAck = 0))
        assertTrue(pc.khataDao().allPartiesForBackup().isEmpty())
        assertTrue(LiveSync.needsUpdate("protocol 2") && LiveSync.needsUpdate("schema 45"))
        assertFalse(LiveSync.needsUpdate("another book") || LiveSync.needsUpdate(null) || LiveSync.needsUpdate("lost"))
        // Told to the other device in this device's own number, never its own back to it.
        assertEquals("protocol ${LiveSync.PROTOCOL}", LiveSync.oursFor("protocol 7"))
        assertEquals("schema $KHATA_DB_VERSION", LiveSync.oursFor("schema 3"))
    }

    @Test
    fun `a stranger or another book of another version leaves no note`() = runBlocking {
        val stranger = LiveSync.Parcel.of(fromOlderPhone().put("dev", "f".repeat(32)))
        assertTrue(LiveSync.applyFrom(pcSql, stranger, theirAck = 0) is LiveSync.Applied.Refused)
        val other = LiveSync.Parcel.of(fromOlderPhone().put("book", "e".repeat(32)))
        assertTrue(LiveSync.applyFrom(pcSql, other, theirAck = 0) is LiveSync.Applied.Refused)
        assertTrue(LiveSync.skewed(pcSql).isEmpty())
    }

    @Test
    fun `a request of another version takes no devices, no words on what was heard, and is answered with this version's number`() = runBlocking {
        val ghost = "ab".repeat(8)
        val request = JSONObject().put("dev", phoneId.device).put("name", "Phone").put("ack", 0)
            .put("parcel", fromOlderPhone())
            .put("joins", JSONObject().put(ghost, JSONObject().put("name", "Ghost")))
            .put("knows", JSONArray())
        val reply = LiveRound.answer(pcSql, request, now = 9_000)!!
        assertEquals("schema $KHATA_DB_VERSION", reply.getString("refused"))
        assertFalse(reply.has("parcel"))
        assertTrue(LiveSync.state(pcSql).peers.none { it.device == ghost })
        assertEquals(mapOf(phoneId.device to "schema $older"), LiveSync.skewed(pcSql))
        // The control: the same request in this version's number does take the announced device.
        request.put("parcel", LiveSync.export(phoneSql, 0).toJson())
        val ok = LiveRound.answer(pcSql, request, now = 9_500)!!
        assertFalse(ok.has("refused"))
        assertTrue(LiveSync.state(pcSql).peers.any { it.device == ghost })
        assertTrue(LiveSync.skewed(pcSql).isEmpty())
    }

    @Test
    fun `a reply of another version does not settle a pairing and says the other device's number`() = runBlocking {
        LiveRound.setPending(phoneSql, LiveRound.Target(listOf("127.0.0.1"), 9, 0, "PC", pair = "aa11bb22"), wasOff = true)
        val request = JSONObject().put("pair", "aa11bb22")
        val reply = JSONObject().put("dev", pcId.device).put("name", "PC").put("ack", 0)
            .put("parcel", LiveSync.export(pcSql, 0).toJson().put("schema", older + 2).put("c", "not a list"))
        val took = LiveRound.take(phoneSql, request, reply, "127.0.0.1", now = 3_000)
        assertEquals("schema ${older + 2}", took.refused)
        assertFalse(took.joined)
        assertNotNull(LiveRound.pending(phoneSql))
        assertNull(LiveRound.target(phoneSql))
    }

    @Test
    fun `a change in a table this version does not have is held, then listed for the owner after a day, not dropped`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        val real = LiveSync.export(phoneSql, 0)
        val future = LiveSync.Change("future_table", "u".repeat(32), "I", JSONObject().put("name", "x"))
        val now = 10_000L
        val r = LiveSync.applyFrom(pcSql, real.copy(changes = listOf(future) + real.changes), theirAck = 0, now = now)
        assertTrue("$r", r is LiveSync.Applied.Done)
        // The rest of the parcel was written and the pointer moved past it; the one change is kept, not lost.
        assertEquals(listOf("Ali"), pc.khataDao().allPartiesForBackup().map { it.name })
        val held = pcSql.read { rows("SELECT tbl FROM sync_held", types = "T").map { it[0] as String } }
        assertEquals(listOf("future_table"), held)
        assertTrue(LiveSync.conflicts(pcSql).none { it.table == "future_table" })
        // A day later, with the next parcel, it is given up to the owner's list.
        phone.khataDao().insertParty(Party(name = "Bilal"))
        val next = LiveSync.export(phoneSql, peerOf(pcSql, phoneId.device).recv)
        val later = now + LiveSync.HELD_GIVE_UP_MS + 1
        assertTrue(LiveSync.applyFrom(pcSql, next, theirAck = 0, now = later) is LiveSync.Applied.Done)
        assertTrue(LiveSync.conflicts(pcSql).any { it.table == "future_table" })
        assertTrue(pcSql.read { rows("SELECT tbl FROM sync_held", types = "T") }.isEmpty())
    }

    @Test
    fun `a field this version has no column for is noted for the owner and the rest of the row is written`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        val real = LiveSync.export(phoneSql, 0)
        real.changes.filter { it.table == "parties" }.forEach { it.row!!.put("futureColumn", "x") }
        assertTrue(LiveSync.applyFrom(pcSql, real, theirAck = 0) is LiveSync.Applied.Done)
        assertEquals(listOf("Ali"), pc.khataDao().allPartiesForBackup().map { it.name })
        val c = LiveSync.conflicts(pcSql).single { it.table == "parties" }
        assertEquals("both", c.kept)
        assertTrue(c.lost!!.contains("futureColumn"))
    }
}
