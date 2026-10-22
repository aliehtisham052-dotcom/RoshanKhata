package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A customer's own page (22 Oct 2026, and its review the same day): at most
 * three entries leave the device, goods but never the shop's note; a page
 * goes when it changed; the text is the same on every device; what a device
 * sent is its own, per link; a removal is never lost; another copy of the
 * book waits for the owner's word; a syncing book makes one shop and one
 * link per customer on both devices; a link is sent before it is shared.
 */
class CustomerLinkTest {

    private val root = "https://x.test/khata/"
    private val look = CustomerLink.Look("Bhatti Traders", "Rs", "ur")
    private val key = "ab".repeat(32)

    /** The server's rules, as web/khata/api.php keeps them, in a few lines. */
    private class Server : CustomerLink.Http {
        val calls = ArrayList<JSONObject>()
        var pass: String? = null
        var writer: String? = null
        val pages = HashMap<String, String>()
        val gone = HashSet<String>()
        var unreachable = false
        var refuse: Map<String, String> = emptyMap()
        var down = false

        override fun post(url: String, body: ByteArray): Pair<Int, ByteArray> {
            if (unreachable) throw java.io.IOException("no route")
            if (down) return 503 to """{"ok":false,"why":"halted"}""".toByteArray()
            val o = JSONObject(String(body, Charsets.UTF_8))
            calls += o
            val token = o.getString("token")
            if (pass == null) { pass = token; writer = o.getString("writer") }
            else if (pass != token) return 403 to """{"ok":false,"why":"wrong-pass"}""".toByteArray()
            if (writer != o.getString("writer")) {
                if (!o.optBoolean("claim")) return 409 to """{"ok":false,"why":"writer"}""".toByteArray()
                writer = o.getString("writer")
            }
            val refused = JSONArray()
            var written = 0
            var removed = 0
            val put = o.getJSONArray("put")
            for (i in 0 until put.length()) {
                val p = put.getJSONObject(i)
                val id = p.getString("id")
                val why = refuse[id] ?: if (id in gone) "gone" else null
                if (why != null) { refused.put(JSONObject().put("id", id).put("why", why)); continue }
                pages[id] = p.getString("blob"); written++
            }
            val drop = o.getJSONArray("drop")
            for (i in 0 until drop.length()) { val id = drop.getString(i); pages.remove(id); gone += id; removed++ }
            return 200 to JSONObject().put("ok", true).put("written", written).put("removed", removed).put("refused", refused).toString().toByteArray()
        }
    }

    /** One device's own notes. */
    private class Notes : CustomerLink.Place {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    private fun <T> book(block: suspend (KhataDao, File) -> T): T = runBlocking {
        val dir = Files.createTempDirectory("rk-clink").toFile()
        val db = DesktopDatabase.open(dir.resolve("book.db"))
        try { block(db.khataDao(), dir) } finally { db.close(); dir.deleteRecursively() }
    }

    private suspend fun KhataDao.flushWith(server: Server, notes: Notes, scope: String = "khata.db", bookKey: String? = null, claim: Boolean = false, now: Long = 1_000_000L) =
        CustomerLink.flush(this, notes, scope, look, bookKey, claim, server, root, now)

    @Test
    fun `a linked customer's page goes sealed, three entries at most, goods but never the note, and only when it changed`() = book { d, _ ->
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertParty(Party(name = "Bilal (no link)"))
        repeat(4) { i -> d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 100.0 + i, isGiven = true, entryNumber = "", timestamp = 1000L + i), emptyList()) }
        d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 104.0, isGiven = true, entryNumber = "", timestamp = 2000L, note = "cheque bounced, guarantor 0300"),
            listOf(EntryItem(entryId = 0, itemName = "Urea"), EntryItem(entryId = 0, itemName = "DAP"))
        )
        val uid = d.getParty(ali)!!.uid!!
        val server = Server()
        val notes = Notes()

        assertEquals(CustomerLink.Result.Off, CustomerLink.flush(d, notes, "khata.db", look, http = server, root = null))
        assertEquals(CustomerLink.Result.Idle, d.flushWith(server, notes))

        val link = CustomerLink.create(d, uid, look)
        assertEquals(link, CustomerLink.create(d, uid, look))
        assertEquals(22, link.key.length)
        assertEquals("https://x.test/khata/#" + link.id + "." + link.key, CustomerLink.url(link, "https://x.test/khata"))
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes))
        val blob = server.pages.getValue(link.id)
        assertFalse("the server never sees a name", blob.contains("Ali"))
        val text = CustomerLink.open(link.key, blob)!!
        assertFalse("the shop's own note never leaves the device", text.contains("cheque") || text.contains("0300"))
        val page = JSONObject(text)
        assertEquals(2, page.getInt("v"))
        assertEquals("Ali", page.getString("n"))
        assertEquals("Bhatti Traders", page.getString("s"))
        assertEquals("c", page.getString("p"))
        assertEquals("ur", page.getString("l"))
        assertEquals(2, page.getInt("k"))
        assertEquals(510.0, page.getDouble("b"), 0.001)
        val e = page.getJSONArray("e")
        assertEquals(3, e.length())
        assertEquals(104.0, e.getJSONObject(0).getDouble("a"), 0.001)
        assertEquals("DAP, Urea", e.getJSONObject(0).getString("x"))
        assertEquals(1, e.getJSONObject(0).getInt("m"))
        assertEquals(0, e.getJSONObject(1).getInt("m"))

        // Nothing changed: nothing sent. Balance only: the page goes once more, with no entries in it.
        assertEquals(CustomerLink.Result.Idle, d.flushWith(server, notes))
        d.setSetting(CustomerLink.MODE, CustomerLink.Mode.BALANCE.key)
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes))
        assertFalse(JSONObject(CustomerLink.open(link.key, server.pages.getValue(link.id))!!).has("e"))
        // A month on, every page goes again, so the server keeps it.
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes, now = 1_000_000L + 31L * 24 * 60 * 60 * 1000))

        // Taken back: removed at the server on the next flush, and the link no longer opens here.
        CustomerLink.remove(d, uid)
        assertNull(CustomerLink.linkOf(d, uid))
        assertEquals(CustomerLink.Result.Done(0, 1), d.flushWith(server, notes))
        assertEquals(link.id, server.calls.last().getJSONArray("drop").getString(0))
        assertFalse(link.id in server.pages)
        assertEquals(CustomerLink.Result.Idle, d.flushWith(server, notes))
        // A new link for him is a new one, and goes, though his page reads the same.
        val again = CustomerLink.create(d, uid, look)
        assertNotEquals(link.id, again.id)
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes, now = 1_000_000L + 31L * 24 * 60 * 60 * 1000))
        // The same shop every time.
        assertEquals(1, server.calls.map { it.getString("shop") }.toSet().size)
    }

    @Test
    fun `the page's text is the same on every device, figure by figure`() {
        val lines = listOf(CustomerLink.Line(5L, 1500.5, gave = false, goods = false, words = null))
        // The computer's SQLite sums with compensation, older phones left to right: 10.55 either way.
        val a = CustomerLink.page(look, "Ali", false, 10.550000000000002, 7L, lines, CustomerLink.Mode.LAST3, 0L)
        val b = CustomerLink.page(look, "Ali", false, 10.55, 7L, lines, CustomerLink.Mode.LAST3, 0L)
        assertEquals(a, b)
        assertEquals(
            "{\"v\":2,\"s\":\"Bhatti Traders\",\"n\":\"Ali\",\"p\":\"c\",\"b\":10.55,\"c\":\"Rs\",\"k\":2,\"l\":\"ur\",\"t\":7,\"r\":0," +
                "\"e\":[{\"d\":5,\"a\":1500.5,\"g\":0,\"m\":0}]}",
            a
        )
        assertEquals("10000000", CustomerLink.figure(1.0E7, 2))
        assertEquals("0", CustomerLink.figure(-0.0, 2))
        assertEquals("12.345", CustomerLink.figure(12.345, 3))
        assertEquals("1250", CustomerLink.figure(1249.6, 0))
        assertEquals("0", CustomerLink.figure(Double.NaN, 2))
        // A name with quotes and a backslash, cut at 60 without splitting the emoji that straddles it.
        val odd = CustomerLink.page(look, "\"A\"/\\" + "x".repeat(54) + "\uD83D\uDE00", false, 0.0, 0L, emptyList(), CustomerLink.Mode.BALANCE, 0L)
        assertTrue(odd, odd.contains("\"n\":\"\\\"A\\\"/\\\\" + "x".repeat(54) + "\","))
        assertEquals("ur-Latn", CustomerLink.pageLanguage("b+ur+Latn"))
        assertEquals("id", CustomerLink.pageLanguage("in"))
        assertEquals("en", CustomerLink.pageLanguage("xx"))
    }

    @Test
    fun `a customer purged is taken back for good, even when the server could not be reached`() = book { d, _ ->
        val ali = d.insertParty(Party(name = "Ali"))
        val uid = d.getParty(ali)!!.uid!!
        val server = Server()
        val notes = Notes()
        val link = CustomerLink.create(d, uid, look)
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes))
        d.softDeleteParty(ali)
        d.purgeParty(ali)
        server.unreachable = true
        assertEquals(CustomerLink.Result.Unreachable, d.flushWith(server, notes))
        assertEquals("1", d.settingValue(CustomerLink.GONE + link.id))
        server.unreachable = false
        assertEquals(CustomerLink.Result.Done(0, 1), d.flushWith(server, notes))
        assertNull(d.settingValue(CustomerLink.GONE + link.id))
        assertFalse(link.id in server.pages)
    }

    @Test
    fun `a customer in the bin shows a page that is only paused, and comes back whole`() = book { d, _ ->
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""))
        val uid = d.getParty(ali)!!.uid!!
        val server = Server()
        val notes = Notes()
        val link = CustomerLink.create(d, uid, look)
        d.flushWith(server, notes)
        d.softDeleteParties(listOf(ali))
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes))
        val paused = CustomerLink.open(link.key, server.pages.getValue(link.id))!!
        assertEquals(1, JSONObject(paused).getInt("z"))
        assertFalse(paused.contains("Ali") || paused.contains("900"))
        val p = d.getParty(ali)!!
        d.restoreParty(ali); d.restoreEntriesOfParty(ali, p.deletedAt!!)
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes))
        assertEquals(900.0, JSONObject(CustomerLink.open(link.key, server.pages.getValue(link.id))!!).getDouble("b"), 0.001)
    }

    @Test
    fun `a page the server will not take is not marked sent - gone or taken costs the link, full waits`() = book { d, _ ->
        val ali = d.insertParty(Party(name = "Ali"))
        val uid = d.getParty(ali)!!.uid!!
        val server = Server()
        val notes = Notes()
        val first = CustomerLink.create(d, uid, look)
        server.refuse = mapOf(first.id to "full")
        assertEquals(CustomerLink.Result.Done(0, 0, 0, 1), d.flushWith(server, notes))
        assertEquals(first, CustomerLink.linkOf(d, uid))
        server.refuse = mapOf(first.id to "gone")
        assertEquals(CustomerLink.Result.Done(0, 0, 1, 0), d.flushWith(server, notes))
        assertNull(CustomerLink.linkOf(d, uid))
        val second = CustomerLink.create(d, uid, look)
        assertNotEquals(first.id, second.id)
        server.refuse = emptyMap()
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, notes))
    }

    @Test
    fun `another copy of the book waits for the owner's word, and a device whose writer changed takes over itself`() = book { d, _ ->
        val ali = d.insertParty(Party(name = "Ali"))
        val uid = d.getParty(ali)!!.uid!!
        val server = Server()
        val phone = Notes()
        CustomerLink.create(d, uid, look)
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, phone))
        // The same book restored on a new phone (or copied to a second business): not here until claimed.
        val newPhone = Notes()
        d.setSetting(CustomerLink.MODE, CustomerLink.Mode.BALANCE.key)
        assertEquals(CustomerLink.Result.NotHere, d.flushWith(server, newPhone))
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, newPhone, claim = true))
        // The old phone is now the other copy: it does not take the pages back by itself. (A new entry, so its
        // page is not the one its own notes say it sent already.)
        d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 50.0, isGiven = true, entryNumber = ""))
        assertEquals(CustomerLink.Result.NotHere, d.flushWith(server, phone))
        // A book that starts to sync writes with the shared writer, and takes over by itself.
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(server, newPhone, bookKey = key))
        assertEquals(CustomerLink.writer(newPhone, "khata.db", key), server.writer)
    }

    @Test
    fun `a syncing book makes one shop, one link per customer and one writer on both devices`() = runBlocking {
        val dirs = List(2) { Files.createTempDirectory("rk-clink-two").toFile() }
        val dbs = dirs.map { DesktopDatabase.open(it.resolve("book.db")) }
        try {
            val (a, b) = dbs.map { it.khataDao() }
            for (dao in listOf(a, b)) dao.insertParty(Party(name = "Ali", uid = "0123456789abcdef0123456789abcdef"))
            val la = CustomerLink.create(a, "0123456789abcdef0123456789abcdef", look, key)
            val lb = CustomerLink.create(b, "0123456789abcdef0123456789abcdef", look, key)
            assertEquals(la, lb)
            val server = Server()
            assertEquals(CustomerLink.Result.Done(1, 0), a.flushWith(server, Notes(), "khata.db", key))
            assertEquals(CustomerLink.Result.Done(1, 0), b.flushWith(server, Notes(), "khata_b2.db", key))
            assertEquals(a.settingValue(CustomerLink.SHOP), b.settingValue(CustomerLink.SHOP))
            assertEquals(CustomerLink.writer(Notes(), "x", key), CustomerLink.writer(Notes(), "y", key))
            // Taken back on both: the next link is the same new one on both, not an old one.
            CustomerLink.remove(a, "0123456789abcdef0123456789abcdef")
            CustomerLink.remove(b, "0123456789abcdef0123456789abcdef")
            val na = CustomerLink.create(a, "0123456789abcdef0123456789abcdef", look, key)
            assertEquals(na, CustomerLink.create(b, "0123456789abcdef0123456789abcdef", look, key))
            assertNotEquals(la, na)
        } finally {
            dbs.forEach { it.close() }; dirs.forEach { it.deleteRecursively() }
        }
    }

    @Test
    fun `more than one request's worth goes in several, and no removal is left behind`() = book { d, _ ->
        val server = Server()
        val notes = Notes()
        repeat(CustomerLink.PER_CALL + 1) { i ->
            val id = d.insertParty(Party(name = "C$i"))
            CustomerLink.create(d, d.getParty(id)!!.uid!!, look)
        }
        repeat(3) { d.setSetting(CustomerLink.GONE + "%032x".format(it), "1") }
        assertEquals(CustomerLink.Result.Done(CustomerLink.PER_CALL + 1, 3), d.flushWith(server, notes))
        assertEquals(2, server.calls.size)
        assertTrue(d.allSettingsForBackup().none { it.key.startsWith(CustomerLink.GONE) })
    }

    @Test
    fun `a damaged link in the book is passed over and the others still go`() = book { d, _ ->
        val a = d.insertParty(Party(name = "Ali"))
        val b = d.insertParty(Party(name = "Bilal"))
        CustomerLink.create(d, d.getParty(a)!!.uid!!, look)
        d.setSetting(CustomerLink.PREFIX + d.getParty(b)!!.uid!!, "0123456789abcdef0123456789abcdef.notakey")
        assertNull(CustomerLink.parse("0123456789abcdef0123456789abcdef.notakey"))
        assertNull(CustomerLink.parse("0123456789ABCDEF0123456789abcdef.AAECAwQFBgcICQoLDA0ODw"))
        assertNotNull(CustomerLink.parse("0123456789abcdef0123456789abcdef.AAECAwQFBgcICQoLDA0ODw"))
        assertEquals(CustomerLink.Result.Done(1, 0), d.flushWith(Server(), Notes()))
    }

    @Test
    fun `a link is given only once its page is at the server`() = book { d, _ ->
        val ali = d.insertParty(Party(name = "Ali"))
        val uid = d.getParty(ali)!!.uid!!
        val server = Server()
        server.down = true
        val notYet = CustomerLink.share(d, Notes(), "khata.db", look, uid, http = server, root = root)
        assertTrue(notYet is CustomerLink.Shared.NotYet)
        server.down = false
        val notes = Notes()
        val ready = CustomerLink.share(d, notes, "khata.db", look, uid, http = server, root = root)
        assertTrue(ready is CustomerLink.Shared.Ready)
        ready as CustomerLink.Shared.Ready
        assertTrue(ready.link.id in server.pages)
        assertEquals(CustomerLink.url(ready.link, root), ready.url)
        assertTrue(CustomerLink.share(d, notes, "khata.db", look, uid, root = null) is CustomerLink.Shared.NotYet)
    }

    @Test
    fun `a page opens only with its own key`() {
        val key = "AAECAwQFBgcICQoLDA0ODw"
        val sealed = CustomerLink.seal(key, "{\"n\":\"Ali\"}")
        assertEquals("{\"n\":\"Ali\"}", CustomerLink.open(key, sealed))
        assertNull(CustomerLink.open("DwAAAAAAAAAAAAAAAAAAAA", sealed))
    }
}
