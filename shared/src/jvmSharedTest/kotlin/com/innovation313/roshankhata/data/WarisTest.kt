package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Waris ki Chaabi (4 Nov 2026): the crypto to its rules (RFC 5869's first vector, boxes and snapshots that open only
 * with the right key and words), the three codes (a QR2 whose check was not made with QR1's secret is refused), the
 * chunks' edges, the status merge (ANY relay's request counts, the earliest openAt wins, a refusal covers the requests
 * before it) and the rule that a refusal a relay missed is sent to it again on every status run — and the whole road,
 * owner to heir, through [FakeRelay], a small relay that keeps the protocol's rules in memory.
 */
class WarisTest {

    private fun unhex(s: String) = WarisCrypto.unhex(s)!!

    @Test
    fun `HKDF-SHA256 gives RFC 5869's test case 1`() {
        val okm = WarisCrypto.hkdf(
            ByteArray(22) { 0x0b }, unhex("000102030405060708090a0b0c"), unhex("f0f1f2f3f4f5f6f7f8f9"), 42
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            WarisCrypto.hex(okm)
        )
    }

    @Test
    fun `passes are the HMAC of the protocol's words, and the relay keeps their SHA-256`() {
        val secret = ByteArray(32) { it.toByte() }
        val vault = "0123456789abcdef0123456789abcdef"
        val p = WarisCrypto.ownerPass(secret, vault)
        assertTrue(WarisCrypto.isHex(p, 64))
        assertEquals(WarisCrypto.hex(WarisCrypto.hmac(secret, "rk-waris-v1:$vault".toByteArray())), p)
        val h = WarisCrypto.heirPass(secret, vault, "0011223344556677")
        assertEquals(WarisCrypto.hex(WarisCrypto.hmac(secret, "rk-waris-heir-v1:$vault:0011223344556677".toByteArray())), h)
        assertTrue(p != h)
        assertEquals(WarisCrypto.sha256hex(p), WarisCrypto.passHash(p))
    }

    @Test
    fun `a box opens only with the heir's key, for its own vault and hid`() {
        val heir = WarisCrypto.newKeyPair()
        val other = WarisCrypto.newKeyPair()
        val w = WarisCrypto.newSecret()
        val vault = WarisCrypto.newVault(); val hid = WarisCrypto.newHid()
        val box = WarisCrypto.sealBox(heir.pub, w, 7, vault, hid)
        assertEquals(1, box[0].toInt())
        val opened = WarisCrypto.openBox(heir.priv, box, vault, hid)!!
        assertArrayEquals(w, opened.w)
        assertEquals(7, opened.keyVersion)
        assertNull(WarisCrypto.openBox(other.priv, box, vault, hid))
        assertNull(WarisCrypto.openBox(heir.priv, box, vault, WarisCrypto.newHid()))
        assertNull(WarisCrypto.openBox(heir.priv, box, WarisCrypto.newVault(), hid))
        val changed = box.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(WarisCrypto.openBox(heir.priv, changed, vault, hid))
        assertTrue(B64.encode(box).length <= 4096)
    }

    @Test
    fun `a snapshot opens only with W and its own version`() {
        val w = WarisCrypto.newSecret()
        val vault = WarisCrypto.newVault()
        val plain = "hello book".toByteArray()
        val sealed = WarisCrypto.sealSnapshot(w, 3, vault, 5, plain)
        assertEquals(3, WarisCrypto.snapshotKeyVersion(sealed))
        assertArrayEquals(plain, WarisCrypto.openSnapshot(w, sealed, vault, 5))
        assertNull(WarisCrypto.openSnapshot(w, sealed, vault, 6))
        assertNull(WarisCrypto.openSnapshot(WarisCrypto.newSecret(), sealed, vault, 5))
        assertNull(WarisCrypto.openSnapshot(w, sealed, WarisCrypto.newVault(), 5))
        assertEquals(plain.size + WarisCrypto.SNAP_OVERHEAD, sealed.size)
    }

    @Test
    fun `chunks are at most 524288 bytes and at most 16 of them`() {
        assertEquals(1, WarisCrypto.chunks(ByteArray(1))!!.size)
        assertEquals(1, WarisCrypto.chunks(ByteArray(WarisCrypto.CHUNK))!!.size)
        val two = WarisCrypto.chunks(ByteArray(WarisCrypto.CHUNK + 1))!!
        assertEquals(listOf(WarisCrypto.CHUNK, 1), two.map { it.size })
        assertEquals(16, WarisCrypto.chunks(ByteArray(WarisCrypto.MAX_SEALED))!!.size)
        assertNull(WarisCrypto.chunks(ByteArray(WarisCrypto.MAX_SEALED + 1)))
        assertNull(WarisCrypto.chunks(ByteArray(0)))
        val data = ByteArray(3 * WarisCrypto.CHUNK - 7) { (it * 31).toByte() }
        val back = ByteArrayOutputStream().apply { WarisCrypto.chunks(data)!!.forEach { write(it) } }.toByteArray()
        assertArrayEquals(data, back)
    }

    @Test
    fun `the codes read back, and a QR2 whose check is not from QR1's secret is refused`() {
        val vault = WarisCrypto.newVault(); val hid = WarisCrypto.newHid(); val secret = WarisCrypto.newSecret()
        val qr1 = WarisCrypto.ownerCode(vault, hid, secret, 30, "Al-Noor Traders & Sons")
        assertTrue(qr1.startsWith("rkwaris:1?"))
        val o = WarisCrypto.parseOwnerCode(qr1)!!
        assertEquals(vault, o.vault); assertEquals(hid, o.hid); assertArrayEquals(secret, o.secret)
        assertEquals(30, o.wait); assertEquals("Al-Noor Traders & Sons", o.shop)
        assertFalse(WarisCrypto.b64url(secret).contains("="))

        val kp = WarisCrypto.newKeyPair()
        val qr2 = WarisCrypto.heirCode(secret, vault, hid, kp.pub)
        val h = WarisCrypto.parseHeirCode(qr2)!!
        assertArrayEquals(kp.pub, h.pub)
        assertTrue(WarisCrypto.heirCodeMatches(h, secret, vault, hid))
        assertFalse(WarisCrypto.heirCodeMatches(h, WarisCrypto.newSecret(), vault, hid))
        // Another key put in by someone who never saw QR1: the check no longer matches.
        val swapped = qr2.replace(h.k, WarisCrypto.b64url(WarisCrypto.newKeyPair().pub))
        assertFalse(WarisCrypto.heirCodeMatches(WarisCrypto.parseHeirCode(swapped)!!, secret, vault, hid))
        val badC = qr2.substring(0, qr2.length - 1) + (if (qr2.last() == '0') '1' else '0')
        assertFalse(WarisCrypto.heirCodeMatches(WarisCrypto.parseHeirCode(badC)!!, secret, vault, hid))

        assertNull(WarisCrypto.parseOwnerCode("rkwaris:1?v=zz&h=$hid"))
        assertNull(WarisCrypto.parseOwnerCode(qr1.replace("&w=30", "&w=91")))
        assertNull(WarisCrypto.parseOwnerCode("RK1:0123"))
        assertNull(WarisCrypto.parseHeirCode(qr1))
        assertNull(WarisCrypto.parseCard(qr1))

        val card = WarisCrypto.card(vault, hid, secret, kp.priv, 60, "Shop")
        val c = WarisCrypto.parseCard(card)!!
        assertArrayEquals(kp.priv, c.priv)
        assertEquals(60, c.wait)
        assertArrayEquals(kp.pub, WarisCrypto.publicOf(c.priv))
    }

    private fun heir(hid: String, refusedAt: Long? = null, allowedAt: Long? = null) =
        Waris.HeirEntry(hid, "A", "pub", "hash", null, 30, 0L, refusedAt, allowedAt)

    private fun rh(hid: String, asked: Long? = null, open: Long? = null, allowed: Long? = null, seen: Long? = null, rejected: Long? = null) =
        Waris.RelayHeir(hid, seen, asked, rejected, allowed, open)

    @Test
    fun `merge - any relay's request counts, the earliest openAt wins, clocks are moved to the phone's`() {
        val hid = "0011223344556677"
        val m = Waris.merge(
            listOf(heir(hid)),
            listOf(
                Waris.RelayStatus("", 1_000_000, 1, listOf(rh(hid))),
                // This relay's clock runs 500 ahead: its 900,500 is the phone's 900,000.
                Waris.RelayStatus("hs", 1_000_500, 1, listOf(rh(hid, asked = 900_500, open = 5_000_500, seen = 990_500)))
            ),
            1_000_000
        )
        val v = m.views.getValue(hid)
        assertTrue(v.asked); assertTrue(v.pending)
        assertEquals(900_000L, v.askedAt); assertEquals(5_000_000L, v.openAt); assertEquals(990_000L, v.lastSeen)
        val two = Waris.merge(
            listOf(heir(hid)),
            listOf(Waris.RelayStatus("", 10, 1, listOf(rh(hid, asked = 5, open = 900))), Waris.RelayStatus("hs", 10, 1, listOf(rh(hid, asked = 7, open = 400)))),
            10
        ).views.getValue(hid)
        assertEquals(5L, two.askedAt); assertEquals(400L, two.openAt)
    }

    @Test
    fun `merge - a request from before the owner's refusal is sent the refusal again, a new one counts`() {
        val hid = "0011223344556677"
        val day = Waris.DAY_MS
        val refusedAt = 10 * day
        val m = Waris.merge(
            listOf(heir(hid, refusedAt = refusedAt)),
            listOf(
                Waris.RelayStatus("", 11 * day, 1, listOf(rh(hid, rejected = refusedAt))),
                Waris.RelayStatus("hs", 11 * day, 1, listOf(rh(hid, asked = 9 * day, open = 39 * day)))
            ),
            11 * day
        )
        assertEquals(listOf("hs" to hid), m.rejectAgain)
        assertFalse(m.views.getValue(hid).asked)
        // Asked again after the refusal: a new request, said to the owner.
        val again = Waris.merge(
            listOf(heir(hid, refusedAt = refusedAt)),
            listOf(Waris.RelayStatus("", 12 * day, 1, listOf(rh(hid, asked = 11 * day + 5, open = 41 * day)))),
            12 * day
        )
        assertTrue(again.rejectAgain.isEmpty())
        assertTrue(again.views.getValue(hid).pending)
        // Allowed by the owner: a relay without the allow is told again, and nothing is pending.
        val allowed = Waris.merge(
            listOf(heir(hid, allowedAt = 5 * day)),
            listOf(Waris.RelayStatus("", 6 * day, 1, listOf(rh(hid, asked = 4 * day, open = 34 * day)))),
            6 * day
        )
        assertEquals(listOf("" to hid), allowed.allowAgain)
        assertFalse(allowed.views.getValue(hid).pending)
    }

    // ------------------------------------------------------------------ the road, through a relay in memory

    class MemStore : Waris.Store {
        val map = HashMap<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    /** The protocol's rules in memory (one vault store per relay); [down] makes it unreachable. */
    class FakeRelay(var now: Long = 1_000_000L) : LiveCloud.Http {
        var down = false
        val calls = ArrayList<String>()
        class H(var pass: String, var box: String, var wait: Int, var lastSeen: Long? = null, var askedAt: Long? = null,
                var rejectedAt: Long? = null, var allowedAt: Long? = null)
        class V(val pass: String) {
            val heirs = LinkedHashMap<String, H>()
            val chunks = HashMap<Pair<Int, Int>, ByteArray>()
            var snap: IntArray? = null; var snapAt = 0L; var bytes = 0
        }
        val vaults = HashMap<String, V>()
        private fun j(code: Int, o: JSONObject) = code to o.toString().toByteArray()
        private fun no(code: Int, why: String) = j(code, JSONObject().put("ok", false).put("why", why))
        private fun openAt(h: H) = h.allowedAt ?: h.askedAt?.let { it + h.wait * Waris.DAY_MS }
        private fun isOpen(h: H) = h.allowedAt != null || (h.askedAt != null && now >= h.askedAt!! + h.wait * Waris.DAY_MS)

        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            if (down) throw java.io.IOException("down")
            val path = url.substringAfter("/v1/w/").substringBefore('?')
            val q = url.substringAfter('?', "").split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
            calls.add("$method $path")
            val parts = path.split('/')
            val vault = parts[0]
            val hash = WarisCrypto.passHash(headers["x-rk-pass"] ?: return no(403, "pass"))
            val v = vaults[vault]
            val tail = parts.drop(1).joinToString("/")
            if (tail == "setup") {
                if (v == null) { vaults[vault] = V(hash); return j(200, JSONObject().put("ok", true).put("created", true)) }
                return if (v.pass == hash) j(200, JSONObject().put("ok", true).put("created", false)) else no(403, "pass")
            }
            v ?: return no(404, "unknown")
            val hid = if (parts.size > 2 && parts[1] == "heir") parts[2] else null
            val sub = if (parts.size > 3) parts[3] else ""
            val heirRoute = hid != null && sub in setOf("ask", "alive", "open", "snap")
            val h = hid?.let { v.heirs[it] }
            if (heirRoute) {
                if (h == null) return no(404, "unknown")
                if (h.pass != hash) return no(403, "pass")
                h.lastSeen = now
            } else if (v.pass != hash) return no(403, "pass")
            when {
                tail == "" && method == "DELETE" -> { vaults.remove(vault); return j(200, JSONObject().put("ok", true)) }
                tail == "snap" -> {
                    val sv = q["v"]!!; val i = q["i"]!!; val n = q["n"]!!
                    if (v.snap != null && sv < v.snap!![0]) return no(409, "old")
                    v.chunks.keys.removeAll { it.first != sv && it.first != (v.snap?.get(0) ?: -1) }
                    v.chunks[sv to i] = body!!
                    val complete = (0 until n).all { (sv to it) in v.chunks }
                    if (complete) {
                        v.snap = intArrayOf(sv, n); v.snapAt = now; v.bytes = (0 until n).sumOf { v.chunks[sv to it]!!.size }
                        v.chunks.keys.removeAll { it.first != sv }
                    }
                    return j(200, JSONObject().put("ok", true).put("complete", complete))
                }
                tail == "status" -> {
                    val arr = JSONArray()
                    v.heirs.forEach { (id, x) ->
                        arr.put(JSONObject().put("hid", id).put("wait", x.wait).put("lastSeen", x.lastSeen ?: JSONObject.NULL)
                            .put("askedAt", x.askedAt ?: JSONObject.NULL).put("rejectedAt", x.rejectedAt ?: JSONObject.NULL)
                            .put("allowedAt", x.allowedAt ?: JSONObject.NULL).put("openAt", openAt(x) ?: JSONObject.NULL))
                    }
                    return j(200, JSONObject().put("ok", true).put("now", now).put("heirs", arr).put("snap", snapJson(v)))
                }
                hid != null && sub == "" && method == "PUT" -> {
                    val o = JSONObject(String(body!!))
                    val ph = o.getString("passHash")
                    if (h == null) {
                        if (v.heirs.size >= 5) return no(409, "full")
                        v.heirs[hid] = H(ph, o.getString("box"), o.optInt("wait"))
                    } else if (h.pass == ph) { h.box = o.getString("box"); h.wait = o.optInt("wait") }
                    else v.heirs[hid] = H(ph, o.getString("box"), o.optInt("wait"))
                    return j(200, JSONObject().put("ok", true))
                }
                hid != null && sub == "" && method == "DELETE" -> { h ?: return no(404, "unknown"); v.heirs.remove(hid); return j(200, JSONObject().put("ok", true)) }
            }
            h ?: return no(404, "unknown")
            when (sub) {
                "reject" -> { h.askedAt = null; h.allowedAt = null; h.rejectedAt = now }
                "allow" -> { h.allowedAt = now; if (h.askedAt == null) h.askedAt = now }
                "ask" -> {
                    if (h.askedAt == null) {
                        val r = h.rejectedAt
                        if (r != null && now - r < Waris.DAY_MS) return j(429, JSONObject().put("ok", false).put("why", "wait").put("retryAt", r + Waris.DAY_MS))
                        h.askedAt = now
                    }
                    return j(200, JSONObject().put("ok", true).put("askedAt", h.askedAt).put("openAt", openAt(h)))
                }
                "alive" -> return j(200, JSONObject().put("ok", true).put("askedAt", h.askedAt ?: JSONObject.NULL)
                    .put("openAt", openAt(h) ?: JSONObject.NULL).put("rejectedAt", h.rejectedAt ?: JSONObject.NULL).put("open", isOpen(h)))
                "open" -> return if (!isOpen(h)) j(403, JSONObject().put("ok", false).put("why", "waiting").put("openAt", openAt(h) ?: JSONObject.NULL))
                    else j(200, JSONObject().put("ok", true).put("box", h.box).put("snap", snapJson(v)))
                "snap" -> {
                    if (!isOpen(h)) return no(403, "waiting")
                    val c = if (v.snap?.get(0) == q["v"]) v.chunks[q["v"]!! to q["i"]!!] else null
                    return if (c == null) no(404, "absent") else 200 to c
                }
            }
            return j(200, JSONObject().put("ok", true))
        }
        private fun snapJson(v: V): Any = v.snap?.let { JSONObject().put("v", it[0]).put("n", it[1]).put("bytes", v.bytes).put("at", v.snapAt) } ?: JSONObject.NULL
    }

    private val roads = listOf(LiveCloud.Road("https://one.test", ""), LiveCloud.Road("https://two.test", "hs"))

    /** Two relays, one Http each, behind one Http. */
    private class Both(val a: FakeRelay, val b: FakeRelay) : LiveCloud.Http {
        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?) =
            (if (url.startsWith("https://one.test")) a else b).call(method, url, headers, body)
    }

    private val bookJson = "{\n  \"format\": \"RoshanKhata\",\n  \"version\": 18,\n  \"exportedAt\": 123,\n  \"parties\": []\n}"
    private val book = Waris.Book { it.write(bookJson.toByteArray()) }

    @Test
    fun `owner to heir - linked, asked, refused, allowed, opened`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val ownerStore = MemStore(); val heirStore = MemStore()
        val owner = Waris.Owner(ownerStore, relays)
        val heir = Waris.Heir(heirStore, relays)
        assertTrue(owner.enable() is Waris.Result.Done)
        val (entry, qr1) = owner.startHeir("Bilal", 30, "Al-Noor Traders")!!
        val (link, qr2) = heir.take(qr1)!!
        assertEquals("Al-Noor Traders", link.shop)
        // A code from a phone that never scanned QR1 is refused.
        val stranger = WarisCrypto.heirCode(WarisCrypto.newSecret(), link.vault, link.hid, WarisCrypto.newKeyPair().pub)
        assertEquals(Waris.Result.Failed(Waris.Why.BAD_CODE), owner.finishHeir(entry.hid, stranger, book))
        assertEquals(Waris.Result.Done(2, 2), owner.finishHeir(entry.hid, qr2, book))
        assertNotNull(a.vaults[link.vault]!!.snap)
        assertNull(owner.heirs().single().secret)

        // Not open before asking, nor after asking within the wait.
        assertTrue((heir.open(link, ByteArrayOutputStream()) as Waris.Opened.Failed).why == Waris.Why.WAITING)
        val (asked, r) = heir.ask(link)
        assertEquals(Waris.Result.Done(2, 2), r)
        assertNotNull(asked.openAt)
        assertTrue(owner.status()!!.getValue(entry.hid).pending)

        // Refused while one relay is down: that relay keeps the request, and the next status run refuses it there.
        b.down = true
        owner.refuse(entry.hid)
        b.down = false
        assertNotNull(b.vaults[link.vault]!!.heirs[entry.hid]!!.askedAt)
        val after = owner.status()!!
        assertFalse(after.getValue(entry.hid).pending)
        assertNull(b.vaults[link.vault]!!.heirs[entry.hid]!!.askedAt)
        assertNotNull(b.vaults[link.vault]!!.heirs[entry.hid]!!.rejectedAt)
        // Asking again within a day is refused by the relays.
        assertEquals(Waris.Why.WAIT, (heir.ask(link).second as Waris.Result.Failed).why)

        // Allowed: the heir opens the book.
        owner.allow(entry.hid)
        val out = ByteArrayOutputStream()
        assertEquals(Waris.Opened.Done, heir.open(link, out))
        assertEquals(bookJson, out.toString("UTF-8"))
    }

    @Test
    fun `the wait passes without a refusal and the book opens, from the second relay when the first is down`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val owner = Waris.Owner(MemStore(), relays)
        val heir = Waris.Heir(MemStore(), relays)
        owner.enable()
        val (entry, qr1) = owner.startHeir("Sana", 7, "Shop")!!
        val (link, qr2) = heir.take(qr1)!!
        owner.finishHeir(entry.hid, qr2, book)
        heir.ask(link)
        a.now += 7 * Waris.DAY_MS + 1; b.now += 7 * Waris.DAY_MS + 1
        a.down = true
        val out = ByteArrayOutputStream()
        assertEquals(Waris.Opened.Done, heir.open(link, out))
        assertEquals(bookJson, out.toString("UTF-8"))
        // The heir's phone lost: the paper card on a new phone opens the same book, with no new code to scan.
        val newPhone = Waris.Heir(MemStore(), relays)
        val (again, code) = newPhone.take(heir.card(link))!!
        assertNull(code)
        assertTrue(again.known)
        val out2 = ByteArrayOutputStream()
        assertEquals(Waris.Opened.Done, newPhone.open(again, out2))
        assertEquals(bookJson, out2.toString("UTF-8"))
    }

    @Test
    fun `removing an heir turns the key, re-boxes the others, and deletes the removed one everywhere`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val ownerStore = MemStore()
        val owner = Waris.Owner(ownerStore, relays)
        val heir1 = Waris.Heir(MemStore(), relays); val heir2 = Waris.Heir(MemStore(), relays)
        owner.enable()
        val (e1, q1) = owner.startHeir("One", 30, "S")!!
        val (l1, r1) = heir1.take(q1)!!
        owner.finishHeir(e1.hid, r1, book)
        val (e2, q2) = owner.startHeir("Two", 30, "S")!!
        val (l2, r2) = heir2.take(q2)!!
        owner.finishHeir(e2.hid, r2, book)
        val before = owner.state()!!
        b.down = true
        owner.removeHeir(e1.hid, book)
        val st = owner.state()!!
        assertEquals(before.keyVersion + 1, st.keyVersion)
        assertFalse(st.w.contentEquals(before.w))
        assertNull(a.vaults[l1.vault]!!.heirs[e1.hid])
        assertNotNull(b.vaults[l1.vault]!!.heirs[e1.hid])
        b.down = false
        owner.retry(book)
        assertNull(b.vaults[l1.vault]!!.heirs[e1.hid])
        // The heir still named opens the new copy with the new key (the box was re-sealed).
        owner.allow(e2.hid)
        val out = ByteArrayOutputStream()
        assertEquals(Waris.Opened.Done, heir2.open(l2, out))
        assertEquals(bookJson, out.toString("UTF-8"))
        // The removed heir cannot even ask.
        assertTrue(heir1.ask(l1).second is Waris.Result.Failed)
    }

    @Test
    fun `a book too big for 8 MiB is refused, an unchanged book is not sent again, and turning off deletes the vault`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val store = MemStore()
        val owner = Waris.Owner(store, relays)
        owner.enable()
        val (e, q) = owner.startHeir("X", 30, "S")!!
        val (_, r) = Waris.Heir(MemStore(), relays).take(q)!!
        owner.finishHeir(e.hid, r, book)
        val noise = Waris.Book { out -> val rnd = java.util.Random(1); val buf = ByteArray(65536); repeat(140) { rnd.nextBytes(buf); out.write(buf) } }
        assertEquals(Waris.Result.Failed(Waris.Why.TOO_BIG), owner.snapshot(noise))
        val v = owner.state()!!.snapV
        a.calls.clear()
        // The same book with another exportedAt is the same book.
        val again = Waris.Book { it.write(bookJson.replace("123", "999").toByteArray()) }
        owner.snapshot(again, force = false)
        assertEquals(v, owner.state()!!.snapV)
        assertTrue(a.calls.none { it.contains("snap") })
        val vault = owner.state()!!.vault
        b.down = true
        owner.turnOff()
        assertNull(owner.state())
        assertNull(a.vaults[vault])
        assertTrue(owner.offPending())
        b.down = false
        owner.retry(null)
        assertNull(b.vaults[vault])
        assertFalse(owner.offPending())
        assertTrue(store.map.keys.none { Waris.isSecret(it) })
    }
}
