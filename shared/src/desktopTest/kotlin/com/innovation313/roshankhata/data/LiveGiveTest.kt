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
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The computer gives its live book to a new phone (23 Oct 2026, LiveGive): the computer shows a give code, the
 * phone asks with it over the WiFi (here the loopback), and gets the same offer a phone sender builds. The joined
 * phone's first word — a WiFi round, or a relay message — is the computer's sign that it joined.
 */
class LiveGiveTest {

    private lateinit var dir: File
    private lateinit var pc: KhataDatabase
    private lateinit var phone1: KhataDatabase
    private lateinit var phone2: KhataDatabase
    private lateinit var server: ServerSocket
    private val stop = AtomicBoolean(false)
    /** The give code's secret on the computer's screen (LiveDesk's, one use). */
    private val giveSecret = AtomicReference<String?>(null)
    /** How many give requests reached the point of making the book ready. */
    private val prepared = AtomicInteger(0)
    private var worker: Thread? = null

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-give").toFile()
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        phone1 = DesktopDatabase.open(dir.resolve("phone1.db"))
        phone2 = DesktopDatabase.open(dir.resolve("phone2.db"))
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val pcSql = pc.syncSql()
        // The computer's side as LiveDesk wires it: the give code, its one use, LiveGive.prepare/settle, the rounds.
        worker = thread {
            LiveWire.serve(
                server, { stop.get() }, { null }, { false },
                bookKey = { runBlocking { LiveSync.identity(pcSql)?.key } },
                spool = dir.resolve("from-phone"),
                giveSecret = { giveSecret.get() },
                onGive = { ask ->
                    if (!giveSecret.compareAndSet(ask.secret, null)) null
                    else {
                        prepared.incrementAndGet()
                        val p = runBlocking {
                            LiveGive.prepare(pcSql, pc.khataDao(), "install-pc", "PC", null, ask.name, ask.host, dir.resolve("to-phone")) { f ->
                                f.bufferedWriter(Charsets.UTF_8).use { BackupCodec.exportTo(pc.khataDao(), "Shop", JSONObject(), it) }
                            }
                        }
                        LiveWire.Gift(p.offer, p.file) { kept -> runBlocking { LiveGive.settle(pcSql, p, kept) } }
                    }
                }
            ) { r -> runBlocking { LiveRound.answer(pcSql, r) } }
        }
    }

    @After
    fun close() {
        stop.set(true)
        runCatching { server.close() }
        worker?.join(5000)
        pc.close(); phone1.close(); phone2.close()
        dir.deleteRecursively()
    }

    /** The computer's book already syncs with a first phone, as when that phone joined it (the computer made the book: A). */
    private suspend fun pcWithFirstPhone(): Pair<LiveSync.Identity, LiveSync.Identity> {
        val pcSql = pc.syncSql(); val p1Sql = phone1.syncSql()
        val pcId = LiveSync.enable(pcSql, "install-pc", "PC")
        val p1Id = LiveSync.enable(p1Sql, "install-p1", "Phone 1", adopt = pcId.book to pcId.key, mark = LiveSync.nextJoinerMark(pcSql))
        LiveSync.putPeer(pcSql, p1Id.device, name = "Phone 1", sent = 0, recv = 0)
        LiveSync.putPeer(p1Sql, pcId.device, name = "PC", sent = 0, recv = 0)
        // The computer heard the first phone's letter in a round (LiveSync.settleMark).
        LiveSync.settleMark(pcSql, p1Id.device, LiveSync.mark(p1Sql))
        return pcId to p1Id
    }

    private fun invite(secret: String) = LiveWire.Invite(listOf("127.0.0.1"), server.localPort, secret, "PC", give = true)

    /** The phone's side (LiveReceive.pull): the book onto the disk, read, kept only when it parses. */
    private fun take(secret: String, file: File): LiveWire.Given =
        LiveWire.takeGift(invite(secret), "Phone 2", file, accept = { o ->
            val (r, d) = BackupCodec.parseFile(o.file!!)
            r is BackupCodec.ImportResult.Ok && d != null
        })

    /** The owner said yes on the phone (LiveReceive.take): the copy restored, the phone joined, the computer its WiFi road. */
    private suspend fun join(offer: LiveWire.Offer, file: File) {
        val (r, data) = BackupCodec.parseFile(file)
        assertTrue("$r", r is BackupCodec.ImportResult.Ok)
        BackupCodec.restoreLedger(phone2.khataDao(), data!!)
        val sql = phone2.syncSql()
        LiveCrew.join(sql, "install-p2", "Phone 2", offer, offer.peers)
        LiveRound.setTarget(sql, LiveRound.Target(listOf("127.0.0.1"), server.localPort, 0L, offer.name, pair = offer.pair, device = offer.device))
    }

    private suspend fun wifiRound(key: String): LiveRound.Took {
        val sql = phone2.syncSql()
        val req = LiveRound.request(sql)!!
        val a = LiveWire.round(listOf("127.0.0.1"), server.localPort, key, req)
        assertTrue("$a", a is LiveWire.Answer.Got)
        return LiveRound.take(sql, req, (a as LiveWire.Answer.Got).reply, a.host)
    }

    @Test
    fun `the computer gives its book to a phone over the WiFi, and the phone joins with its own letter`() = runBlocking {
        val (pcId, p1Id) = pcWithFirstPhone()
        val pcSql = pc.syncSql(); val p2Sql = phone2.syncSql()
        pc.khataDao().insertParty(Party(name = "Before the give"))
        val secret = WifiSync.newSecret()
        giveSecret.set(secret)
        val file = dir.resolve("p2-book.json")

        val given = take(secret, file)
        assertTrue("$given", given is LiveWire.Given.Got)
        val offer = (given as LiveWire.Given.Got).offer
        // The book's identity, the computer as the sender, the first phone as the copy held it.
        assertEquals(pcId.book, offer.book); assertEquals(pcId.key, offer.key); assertEquals(pcId.device, offer.device)
        assertNotNull(offer.pair)
        assertEquals(LiveWire.pairCode(secret, offer.pair!!), offer.code)
        assertTrue("${offer.code}", Regex("\\d{4}").matches(offer.code!!))
        assertTrue(offer.peers!!.has(p1Id.device))
        assertEquals(LiveSync.mark(phone1.syncSql()), offer.peers!!.getJSONObject(p1Id.device).optString("mark"))
        // The relay was turned on before the copy, and the pairing waits for the phone's word.
        assertEquals("1", pc.khataDao().settingValue(BookSettings.LIVE_RELAY))
        val pending = LiveRound.pending(pcSql)!!
        assertTrue(pending.phone)
        assertEquals(offer.pair, pending.pair)
        // The code is spent: a second phone with it gets nothing.
        assertEquals(LiveWire.Given.Refused, take(secret, dir.resolve("again.json")))
        assertEquals(1, prepared.get())

        join(offer, file)
        val p2Id = LiveSync.identity(p2Sql)!!
        assertEquals(pcId.book, p2Id.book); assertEquals(pcId.key, p2Id.key)
        val p2Peers = LiveSync.state(p2Sql).peers
        assertEquals(setOf(pcId.device, p1Id.device), p2Peers.map { it.device }.toSet())
        assertEquals(offer.seq, p2Peers.first { it.device == pcId.device }.recv)
        // The copy carried the relay's switch: the new phone meets the first one through it.
        assertEquals("1", phone2.khataDao().settingValue(BookSettings.LIVE_RELAY))
        assertTrue(phone2.khataDao().allPartiesForBackup().any { it.name == "Before the give" })
        // Three devices, three letters.
        val marks = listOf(LiveSync.mark(pcSql), LiveSync.mark(phone1.syncSql()), LiveSync.mark(p2Sql))
        assertEquals("$marks", 3, marks.toSet().size)
        assertEquals("A", marks[0])

        // The phone's first WiFi round names the pairing: the computer adds it, for that pairing only.
        pc.khataDao().insertParty(Party(name = "From the computer"))
        val first = wifiRound(pcId.key)
        assertNull(first.refused)
        assertNull(LiveRound.pending(pcSql))
        assertTrue(LiveSync.state(pcSql).peers.any { it.device == p2Id.device && it.name == "Phone 2" })
        assertEquals(offer.pair, LiveSync.peerPair(pcSql, p2Id.device))
        assertNull(LiveCrew.pairSaid(p2Sql))
        // The first phone is told of the new one (LiveCrew's announcement).
        assertTrue(LiveCrew.joinsOut(pcSql)!!.has(p2Id.device))
        phone2.khataDao().insertParty(Party(name = "From phone 2"))
        repeat(2) { assertNull(wifiRound(pcId.key).refused) }
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "From phone 2" })
        assertTrue(phone2.khataDao().allPartiesForBackup().any { it.name == "From the computer" })
        // The computer's copy of the book does not linger on its disk once given.
        assertEquals(0, dir.resolve("to-phone").listFiles()?.size ?: 0)
    }

    @Test
    fun `a stranger without the give code's secret gets nothing, and nothing is made ready or kept`() = runBlocking {
        val pcSql = pc.syncSql()
        val secret = WifiSync.newSecret()
        // No code on the screen: refused at once.
        java.net.Socket("127.0.0.1", server.localPort).use { s ->
            s.soTimeout = 5000
            val out = java.io.DataOutputStream(s.getOutputStream())
            out.write("RKT2".toByteArray()); out.write(ByteArray(8)); out.writeInt(16); out.writeByte(1); out.flush()
            assertEquals(LiveWire.BAD, s.getInputStream().read())
        }
        giveSecret.set(secret)
        // A request that announces a big record: refused before it is read (nothing is gathered for a stranger).
        java.net.Socket("127.0.0.1", server.localPort).use { s ->
            s.soTimeout = 5000
            val out = java.io.DataOutputStream(s.getOutputStream())
            out.write("RKT2".toByteArray()); out.write(ByteArray(8)); out.writeInt(200 * 1024 * 1024); out.writeByte(1); out.flush()
            assertEquals(LiveWire.BAD, s.getInputStream().read())
        }
        // A request sealed with another secret (an older code, someone else's photo of it): refused on that alone.
        val file = dir.resolve("stranger.json")
        assertEquals(LiveWire.Given.Refused, take(WifiSync.newSecret(), file))
        assertFalse(file.exists())
        // A phone's join code is no give code: its pairing frame gets no book either.
        assertEquals(0, prepared.get())
        assertNull(LiveSync.identity(pcSql))
        assertNull(pc.khataDao().settingValue(BookSettings.LIVE_RELAY))
        assertNull(LiveRound.pending(pcSql))
        // The code is still good for the owner's own phone.
        assertEquals(secret, giveSecret.get())
        assertTrue(take(secret, dir.resolve("owner.json")) is LiveWire.Given.Got)
    }

    @Test
    fun `a give code reads back as written, and a code that both gives and takes is not ours`() {
        val i = LiveWire.Invite(listOf("192.168.1.5"), LiveWire.PORT, LiveSync.newHex(32), "SHOP-PC", give = true)
        assertEquals(i, LiveWire.parseInvite(LiveWire.inviteText(i)))
        assertFalse(LiveWire.parseInvite(LiveWire.inviteText(i.copy(give = false)))!!.give)
        assertNull(LiveWire.parseInvite(LiveWire.inviteText(i.copy(phone = true))))
    }

    /** cloud/worker's hello and round in a few lines: enough for a joiner's word through the relay. */
    private class Relay : LiveCloud.Http {
        val devices = HashSet<String>()
        val parcels = ArrayList<Triple<Long, String, ByteArray>>()
        var seq = 0L
        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            val dev = headers.getValue("x-device")
            val action = url.substringAfterLast('/').substringBefore('?')
            fun ok(o: JSONObject) = 200 to o.put("ok", true).toString().toByteArray()
            return when (action) {
                "hello" -> { devices += dev; ok(JSONObject().put("top", seq)) }
                "round" -> {
                    if (dev !in devices) return 403 to JSONObject().put("ok", false).put("why", "say-hello-first").toString().toByteArray()
                    val o = JSONObject(String(body!!))
                    var sent: Long? = null
                    if (!o.isNull("parcel")) { seq++; parcels.add(Triple(seq, dev, B64.decode(o.getString("parcel")))); sent = seq }
                    val arr = JSONArray()
                    parcels.filter { it.first > o.getLong("after") && it.second != dev }.forEach {
                        arr.put(JSONObject().put("seq", it.first).put("from", it.second).put("body", Base64.getEncoder().encodeToString(it.third)))
                    }
                    ok(JSONObject().put("seq", sent ?: JSONObject.NULL).put("parcels", arr).put("more", false))
                }
                else -> 404 to JSONObject().put("ok", false).put("why", "no-such-path").toString().toByteArray()
            }
        }
    }

    @Test
    fun `a computer that synced with nobody gives its book, and hears the phone's yes through the relay`() = runBlocking {
        val pcSql = pc.syncSql(); val p2Sql = phone2.syncSql()
        pc.khataDao().insertParty(Party(name = "Made on the computer"))
        val secret = WifiSync.newSecret()
        giveSecret.set(secret)
        val file = dir.resolve("p2-book.json")
        val given = take(secret, file)
        assertTrue("$given", given is LiveWire.Given.Got)
        val offer = (given as LiveWire.Given.Got).offer
        val pcId = LiveSync.identity(pcSql)!!
        // The computer made the book: A; the phone it gives to another letter.
        assertEquals("A", LiveSync.mark(pcSql))
        assertTrue(LiveSync.state(pcSql).peers.isEmpty())
        val (r, data) = BackupCodec.parseFile(file)
        assertTrue("$r", r is BackupCodec.ImportResult.Ok)
        BackupCodec.restoreLedger(phone2.khataDao(), data!!)
        LiveCrew.join(p2Sql, "install-p2", "Phone 2", offer, offer.peers)
        assertTrue(LiveSync.mark(p2Sql) != "A")

        // Through the relay alone: the computer is asked even with no other device yet, for the phone's word.
        val relay = Relay()
        var clock = 1_000_000L
        suspend fun all(times: Int) = repeat(times) {
            clock += 1000
            for (s in listOf(pcSql, p2Sql)) {
                val res = LiveCloud.round(s, relay, "https://give.relay.test", clock, useLines = false) { false }
                assertTrue("$res", res is LiveCloud.Result.Done)
            }
        }
        all(2)
        assertNull(LiveRound.pending(pcSql))
        val p2Id = LiveSync.identity(p2Sql)!!
        assertTrue(LiveSync.state(pcSql).peers.any { it.device == p2Id.device })
        assertEquals(offer.pair, LiveSync.peerPair(pcSql, p2Id.device))
        phone2.khataDao().insertParty(Party(name = "On the phone"))
        pc.khataDao().insertParty(Party(name = "On the computer"))
        all(3)
        for (db in listOf(pc, phone2)) {
            val names = db.khataDao().allPartiesForBackup().map { it.name }
            for (n in listOf("Made on the computer", "On the phone", "On the computer")) assertEquals(n, 1, names.count { it == n })
        }
        assertEquals(LiveSync.fingerprint(pcSql), LiveSync.fingerprint(p2Sql))
        assertEquals(pcId.book, p2Id.book)
    }

    @Test
    fun `a give whose phone never answers is given up after its wait, and a book that did not sync is off again`() = runBlocking {
        val pcSql = pc.syncSql()
        val secret = WifiSync.newSecret()
        giveSecret.set(secret)
        assertTrue(take(secret, dir.resolve("p2-book.json")) is LiveWire.Given.Got)
        assertNotNull(LiveSync.identity(pcSql))
        // Not yet: the wait is the phone's own fifteen minutes.
        assertFalse(LiveGive.expire(pcSql, System.currentTimeMillis()))
        assertNotNull(LiveRound.pending(pcSql))
        assertEquals("1", pc.khataDao().settingValue(BookSettings.LIVE_RELAY))
        var left = 0
        assertTrue(LiveGive.expire(pcSql, System.currentTimeMillis() + LiveGive.PENDING_MS + 60_000L) { left++ })
        assertEquals(1, left)
        assertNull(LiveRound.pending(pcSql))
        assertNull(LiveSync.identity(pcSql))
        // The relay only this give turned on is off again with it (R11): the owner never chose it.
        assertNull(pc.khataDao().settingValue(BookSettings.LIVE_RELAY))
    }

    @Test
    fun `a book the phone could not read is taken back on the computer as if never given`() = runBlocking {
        val (pcId, _) = pcWithFirstPhone()
        val pcSql = pc.syncSql()
        val secret = WifiSync.newSecret()
        giveSecret.set(secret)
        val file = dir.resolve("p2-book.json")
        val given = LiveWire.takeGift(invite(secret), "Phone 2", file, accept = { false })
        assertEquals(LiveWire.Given.Refused, given)
        assertFalse(file.exists())
        // The computer heard the phone's no: nothing waits, still the same book with the same devices.
        Thread.sleep(300)
        assertNull(LiveRound.pending(pcSql))
        assertEquals(pcId.book, LiveSync.identity(pcSql)!!.book)
        assertEquals(1, LiveSync.state(pcSql).peers.size)
        assertEquals(0, dir.resolve("to-phone").listFiles()?.size ?: 0)
        // The relay was off before this give, and the give turned it on: the phone's no turns it off again (R11).
        assertNull(pc.khataDao().settingValue(BookSettings.LIVE_RELAY))
    }
}
