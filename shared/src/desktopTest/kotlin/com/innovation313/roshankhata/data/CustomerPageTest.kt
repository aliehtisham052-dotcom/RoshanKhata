package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The customer's side of a shop's link (7 Nov 2026, "Mera Khata"): the link in each of its shapes, the page read
 * as the browser reads it, and the server asked only for what changed (304 with the last ETag).
 *
 * And the security review of the same day: never a key from an https link's query; a page opens only under its own
 * link's id; no more is read than the shop's app writes, a day only between 2000 and 2100; the page's counter tells
 * an older page served again from a newer one.
 */
class CustomerPageTest {

    private val look = CustomerLink.Look("Al-Noor Traders", "Rs", "ur-Latn")
    private val id = "0123456789abcdef0123456789abcdef"
    private val key = "AAAAAAAAAAAAAAAAAAAAAA"
    private val link = CustomerLink.Link(id, key)

    @Test
    fun `a link is read in each of its shapes and only from the shop's host`() {
        assertEquals(link, CustomerPage.linkFrom("https://khata.ehtisham.shop/#$id.$key"))
        assertEquals(link, CustomerPage.linkFrom("https://KHATA.ehtisham.shop/#$id.$key"))
        assertEquals(link, CustomerPage.linkFrom("roshankhata://khata?l=$id.$key"))
        assertEquals(link, CustomerPage.linkFrom("roshankhata://khata#$id.$key"))
        assertEquals(link, CustomerPage.linkFrom("  https://khata.ehtisham.shop/#$id.$key  "))
        // Never a key in an https link's query: a server on the way could keep it in its logs.
        assertNull(CustomerPage.linkFrom("https://KHATA.ehtisham.shop/app?l=$id.$key"))
        assertNull(CustomerPage.linkFrom("https://khata.ehtisham.shop/?l=$id.$key"))
        assertNull(CustomerPage.linkFrom("https://evil.example/#$id.$key"))
        assertNull(CustomerPage.linkFrom("http://khata.ehtisham.shop/#$id.$key"))
        assertNull(CustomerPage.linkFrom("https://khata.ehtisham.shop/#$id"))
        assertNull(CustomerPage.linkFrom("roshankhata://other?l=$id.$key"))
        assertNull(CustomerPage.linkFrom(null))
        assertNull(CustomerPage.linkFrom("not a link at all"))
    }

    @Test
    fun `a page reads back as the shop wrote it, with its side`() {
        val text = CustomerLink.page(look, "Test Customer", false, 4500.0, 1_700_000_000_000L, emptyList(), CustomerLink.Mode.BALANCE, 0L)
        val page = CustomerPage.read(text)!!
        assertEquals("Al-Noor Traders", page.shop)
        assertEquals("Test Customer", page.name)
        assertEquals(4500.0, page.balance, 0.0)
        assertEquals("Rs", page.currency)
        assertEquals(2, page.decimals)
        assertEquals(1_700_000_000_000L, page.updated)
        assertFalse(page.supplier)
        assertFalse(page.paused)
        assertTrue(page.lines.isEmpty())
        assertEquals(CustomerPage.Side.OWES, CustomerPage.sideOf(page.balance))
        assertEquals(CustomerPage.Side.OWED, CustomerPage.sideOf(-10.0))
        assertEquals(CustomerPage.Side.SETTLED, CustomerPage.sideOf(0.0004))

        val withLines = CustomerLink.page(look, "Test Customer", true, -20.0, 5L,
            listOf(CustomerLink.Line(5L, 20.0, false, true, "Item A")), CustomerLink.Mode.LAST3, 0L)
        val p2 = CustomerPage.read(withLines)!!
        assertTrue(p2.supplier)
        assertEquals(1, p2.lines.size)
        assertEquals("Item A", p2.lines[0].words)

        assertTrue(CustomerPage.read(CustomerLink.pausedPage(look, 0L))!!.paused)
        assertNull(CustomerPage.read("{\"v\":1}"))
        assertNull(CustomerPage.read("not json"))
    }

    @Test
    fun `money reads as the browser page writes it`() {
        assertEquals("\u2066Rs 4,500\u2069", CustomerPage.money("Rs", 2, "ur", -4500.0))
        assertEquals("\u2066Rs 1,234,567.50\u2069", CustomerPage.money("Rs", 2, "en", 1234567.5))
        assertEquals("\u2066Rp 1.250,50\u2069", CustomerPage.money("Rp", 2, "id", 1250.5))
        assertEquals("\u2066KD 12.125\u2069", CustomerPage.money("KD", 3, "ar", 12.125))
        assertEquals("\u2066999\u2069", CustomerPage.money("", 2, "en", 999.0))
    }

    private class Host(var code: Int, var body: String, var tag: String?) : CustomerPage.Http {
        val asked = ArrayList<Pair<String, String?>>()
        override fun get(url: String, etag: String?): Triple<Int, ByteArray, String?> {
            asked += url to etag
            if (code == -1) throw java.io.IOException("offline")
            return Triple(if (etag != null && etag == tag && code == 200) 304 else code, body.toByteArray(), tag)
        }
    }

    @Test
    fun `the page is asked with its ETag, and every answer means one thing`() {
        val text = CustomerLink.page(look, "Test Customer", false, 100.0, 9L, emptyList(), CustomerLink.Mode.BALANCE, 0L)
        val host = Host(200, CustomerLink.seal(link, text), "\"v1\"")
        val first = CustomerPage.fetch(link, null, "https://khata.ehtisham.shop/", host)
        first as CustomerPage.Fetched.Ok
        assertEquals(100.0, first.page.balance, 0.0)
        assertEquals("https://khata.ehtisham.shop/d/$id.txt", host.asked.last().first)
        assertEquals(100.0, CustomerPage.reopen(link, first.blob)!!.balance, 0.0)

        assertEquals(CustomerPage.Fetched.Same, CustomerPage.fetch(link, first.etag, "https://khata.ehtisham.shop/", host))
        assertEquals("\"v1\"", host.asked.last().second)

        host.body = "!gone"; host.tag = "\"v2\""
        assertEquals(CustomerPage.Fetched.Gone, CustomerPage.fetch(link, first.etag, "https://khata.ehtisham.shop/", host))
        host.code = 404
        assertEquals(CustomerPage.Fetched.Gone, CustomerPage.fetch(link, null, "https://khata.ehtisham.shop/", host))
        host.code = 200; host.body = CustomerLink.seal(link.copy(key = "QUFBQUFBQUFBQUFBQUFBQQ"), text)
        assertEquals(CustomerPage.Fetched.Wrong, CustomerPage.fetch(link, null, "https://khata.ehtisham.shop/", host))
        // The right key under another link's id: a page moved from one link to another does not open.
        host.body = CustomerLink.seal(link.copy(id = "f".repeat(32)), text)
        assertEquals(CustomerPage.Fetched.Wrong, CustomerPage.fetch(link, null, "https://khata.ehtisham.shop/", host))
        host.code = -1
        assertEquals(CustomerPage.Fetched.Offline, CustomerPage.fetch(link, null, "https://khata.ehtisham.shop/", host))
        host.code = 500
        assertEquals(CustomerPage.Fetched.Offline, CustomerPage.fetch(link, null, "https://khata.ehtisham.shop/", host))
    }

    @Test
    fun `no more is read than the shop's app writes, and a day outside 2000 to 2100 is no day`() {
        val long = "x".repeat(80)
        val text = "{\"v\":3,\"s\":\"$long\",\"n\":\"$long\",\"p\":\"c\",\"b\":12,\"c\":\"RsRsRsRs\",\"k\":9,\"l\":\"xx\"," +
            "\"t\":9223372036854775807,\"r\":0,\"q\":-5,\"e\":[" +
            List(5) { "{\"d\":-1,\"a\":1,\"g\":1,\"m\":1,\"x\":\"$long\"}" }.joinToString(",") + "]}"
        val page = CustomerPage.read(text)!!
        assertEquals(60, page.shop.length)
        assertEquals(60, page.name.length)
        assertEquals("RsRsR", page.currency)
        assertEquals(2, page.decimals)
        assertEquals("en", page.language)
        assertEquals("a day past 2100 (Long.MAX_VALUE crashed the Jalali months) is no day", 0L, page.updated)
        assertEquals(0L, page.seq)
        assertEquals(CustomerLink.MAX_ENTRIES, page.lines.size)
        assertEquals(40, page.lines[0].words!!.length)
        assertEquals(0L, page.lines[0].at)
        // A letter written as two is never split at the edge.
        val emoji = CustomerPage.read("{\"v\":3,\"n\":\"" + "y".repeat(59) + "😀\"}")!!
        assertEquals("y".repeat(59), emoji.name)
        // Days inside the years a page may carry are read as written.
        val fine = CustomerPage.read("{\"v\":3,\"t\":1700000000000,\"q\":1700000000123,\"l\":\"ur-Latn\"}")!!
        assertEquals(1_700_000_000_000L, fine.updated)
        assertEquals(1_700_000_000_123L, fine.seq)
        assertEquals("ur-Latn", fine.language)
    }

    @Test
    fun `a page under a lower counter is an older one, unless it is the same page`() {
        val base = CustomerLink.page(look, "Test Customer", false, 100.0, 9L, emptyList(), CustomerLink.Mode.BALANCE, 0L)
        val kept = CustomerPage.read(CustomerLink.stamped(base, 200L))!!
        assertEquals(200L, kept.seq)
        assertEquals(CustomerPage.Fresh.NEWER, CustomerPage.fresh(null, kept))
        assertEquals(CustomerPage.Fresh.NEWER, CustomerPage.fresh(kept, CustomerPage.read(CustomerLink.stamped(base, 300L))!!))
        assertEquals(CustomerPage.Fresh.NEWER, CustomerPage.fresh(kept, CustomerPage.read(CustomerLink.stamped(base, 200L))!!))
        // The same page under a lower counter: another phone of the shop's book sent it.
        assertEquals(CustomerPage.Fresh.SAME, CustomerPage.fresh(kept, CustomerPage.read(CustomerLink.stamped(base, 100L))!!))
        // A different page under a lower counter: an older page served again.
        val older = CustomerLink.page(look, "Test Customer", false, 50.0, 8L, emptyList(), CustomerLink.Mode.BALANCE, 0L)
        assertEquals(CustomerPage.Fresh.OLDER, CustomerPage.fresh(kept, CustomerPage.read(CustomerLink.stamped(older, 100L))!!))
        // A page made before the counter (none in it) reads as 0: older than any page that has one.
        assertEquals(CustomerPage.Fresh.OLDER, CustomerPage.fresh(kept, CustomerPage.read(older)!!))
        // Through the seal and back, the counter and the padding change nothing else.
        val sealed = CustomerLink.seal(link, CustomerLink.stamped(base, 200L))
        assertEquals(kept, CustomerPage.reopen(link, sealed))
        assertEquals(CustomerPage.read(base)!!.copy(seq = 200L), kept)
    }
}
