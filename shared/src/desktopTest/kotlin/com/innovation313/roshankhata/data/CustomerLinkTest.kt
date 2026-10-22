package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * A customer's own page (22 Oct 2026): at most three entries ever leave the
 * device, only linked customers go, a page goes again only when it changed,
 * and the server is sent sealed text it cannot read.
 */
class CustomerLinkTest {

    private class FakeServer : CustomerLink.Http {
        val calls = ArrayList<JSONObject>()
        override fun post(url: String, body: ByteArray): Pair<Int, ByteArray> {
            calls += JSONObject(String(body))
            return 200 to """{"ok":true}""".toByteArray()
        }
    }

    @Test
    fun `a linked customer's page goes sealed, with three entries at most, and only when it changed`() = runBlocking {
        val dir = Files.createTempDirectory("rk-clink").toFile()
        val db = DesktopDatabase.open(dir.resolve("book.db"))
        try {
            val d = db.khataDao()
            val ali = d.insertParty(Party(name = "Ali"))
            d.insertParty(Party(name = "Bilal (no link)"))
            repeat(5) { i -> d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 100.0 + i, isGiven = true, entryNumber = "", timestamp = 1000L + i), emptyList()) }
            val uid = d.getParty(ali)!!.uid!!
            val server = FakeServer()

            assertEquals(CustomerLink.Result.Off, CustomerLink.flush(d, "Shop", "Rs", server, null))
            assertEquals(CustomerLink.Result.Idle, CustomerLink.flush(d, "Shop", "Rs", server, "https://x.test/khata/"))

            val link = CustomerLink.create(d, uid)
            assertEquals(link, CustomerLink.create(d, uid))
            assertEquals("https://x.test/khata/#" + link.id + "." + link.key, CustomerLink.url(link, "https://x.test/khata"))
            val r = CustomerLink.flush(d, "Shop", "Rs", server, "https://x.test/khata/")
            assertEquals(CustomerLink.Result.Done(1, 0), r)
            val put = server.calls.single().getJSONArray("put")
            assertEquals(1, put.length())
            val blob = put.getJSONObject(0).getString("blob")
            assertFalse("the server never sees a name", blob.contains("Ali"))
            val page = JSONObject(CustomerLink.open(link.key, blob)!!)
            assertEquals("Ali", page.getString("n"))
            assertEquals(510.0, page.getDouble("b"), 0.001)
            assertEquals(3, page.getJSONArray("e").length())
            assertEquals(104.0, page.getJSONArray("e").getJSONObject(0).getDouble("a"), 0.001)

            // Nothing changed: nothing sent.
            assertEquals(CustomerLink.Result.Idle, CustomerLink.flush(d, "Shop", "Rs", server, "https://x.test/khata/"))
            // Balance only: the page changes once, with no entries in it.
            d.setSetting(CustomerLink.MODE, CustomerLink.Mode.BALANCE.key)
            assertEquals(CustomerLink.Result.Done(1, 0), CustomerLink.flush(d, "Shop", "Rs", server, "https://x.test/khata/"))
            val second = JSONObject(CustomerLink.open(link.key, server.calls.last().getJSONArray("put").getJSONObject(0).getString("blob"))!!)
            assertFalse(second.has("e"))

            // Taken back: removed at the server on the next flush, and the link no longer opens here.
            CustomerLink.remove(d, uid)
            assertNull(CustomerLink.linkOf(d, uid))
            assertEquals(CustomerLink.Result.Done(0, 1), CustomerLink.flush(d, "Shop", "Rs", server, "https://x.test/khata/"))
            assertEquals(link.id, server.calls.last().getJSONArray("drop").getString(0))
            assertEquals(CustomerLink.Result.Idle, CustomerLink.flush(d, "Shop", "Rs", server, "https://x.test/khata/"))
            // The same shop every time.
            assertTrue(server.calls.map { it.getString("shop") }.toSet().size == 1)
        } finally {
            db.close(); dir.deleteRecursively()
        }
    }

    @Test
    fun `a page opens only with its own key`() {
        val key = "AAECAwQFBgcICQoLDA0ODw"
        val sealed = CustomerLink.seal(key, "{\"n\":\"Ali\"}")
        assertEquals("{\"n\":\"Ali\"}", CustomerLink.open(key, sealed))
        assertNull(CustomerLink.open("DwAAAAAAAAAAAAAAAAAAAA", sealed))
    }
}
