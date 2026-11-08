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
        // Nothing of the vault is left but the removed list, which holds the vault itself for good (W5).
        assertEquals(listOf(Waris.K_REMOVED), store.map.keys.filter { Waris.isSecret(it) })
        assertTrue(vault in owner.removed())
    }

    // ------------------------------------------------------------------ the security review (7 Nov 2026)

    /** An owner and [n] linked heirs on the two relays; the heirs' links in order. */
    private suspend fun ownerWith(n: Int, relays: Waris.Relays, store: MemStore = MemStore(), wait: Int = 30):
        Pair<Waris.Owner, List<Pair<Waris.HeirEntry, Waris.Link>>> {
        val owner = Waris.Owner(store, relays)
        assertTrue(owner.enable() is Waris.Result.Done)
        val out = ArrayList<Pair<Waris.HeirEntry, Waris.Link>>()
        repeat(n) { i ->
            val (e, q) = owner.startHeir("Heir $i", wait, "Shop")!!
            val (l, r) = Waris.Heir(MemStore(), relays).take(q)!!
            assertTrue(owner.finishHeir(e.hid, r, book) is Waris.Result.Done)
            out += owner.heirs().single { it.hid == e.hid } to l
        }
        return owner to out
    }

    @Test
    fun `a second turn off while the first is unfinished keeps both vaults to remove`() = runBlocking {
        // L9: the second off used to erase the first one's record, leaving its vault on the relay for ever.
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val store = MemStore()
        val owner = Waris.Owner(store, relays)
        assertTrue(owner.enable() is Waris.Result.Done)
        val first = owner.state()!!.vault
        b.down = true
        owner.turnOff()
        assertNotNull(b.vaults[first])
        assertTrue(owner.enable() is Waris.Result.Done)
        val second = owner.state()!!.vault
        owner.turnOff()
        assertNotNull(b.vaults[first]); assertTrue(owner.offPending()) // the second vault was made while b was down, so b never held it
        b.down = false
        owner.retry(null)
        assertNull(b.vaults[first]); assertNull(b.vaults[second])
        assertFalse(owner.offPending())
    }

    /** What someone holding the owner's pass (a stolen backup) can put on a relay: an heir of his own, allowed. */
    private fun outsider(relays: Waris.Relays, road: LiveCloud.Road, st: Waris.OwnerState, hid: String = WarisCrypto.newHid()): String {
        val pass = WarisCrypto.heirPass(WarisCrypto.newSecret(), st.vault, hid)
        val box = WarisCrypto.sealBox(WarisCrypto.newKeyPair().pub, WarisCrypto.newSecret(), 1, st.vault, hid)
        val body = JSONObject().put("passHash", WarisCrypto.passHash(pass)).put("box", B64.encode(box)).put("wait", 30)
            .toString().toByteArray()
        assertTrue(relays.call(road, "PUT", st.vault, "/heir/$hid", st.pass, body, "application/json") is Waris.Reply.Ok)
        assertTrue(relays.call(road, "POST", st.vault, "/heir/$hid/allow", st.pass) is Waris.Reply.Ok)
        return hid
    }

    @Test
    fun `while live sync is halted the owner still reads, refuses, allows, removes and turns off - new copies and the heir wait`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        var halted = false
        val relays = Waris.Relays(Both(a, b), roads) { halted }
        val (owner, heirs) = ownerWith(2, relays, wait = 7)
        val (e, l) = heirs[0]
        val heir = Waris.Heir(MemStore(), relays)
        heir.ask(l)
        halted = true
        // The relays' clocks run on: the owner must still see the request and refuse it.
        assertTrue(owner.status()!!.getValue(e.hid).pending)
        assertEquals(Waris.Result.Done(2, 2), owner.refuse(e.hid))
        for (r in listOf(a, b)) {
            assertNull(r.vaults[l.vault]!!.heirs[e.hid]!!.askedAt)
            assertNotNull(r.vaults[l.vault]!!.heirs[e.hid]!!.rejectedAt)
        }
        assertEquals(Waris.Result.Done(2, 2), owner.allow(e.hid))
        assertNotNull(a.vaults[l.vault]!!.heirs[e.hid]!!.allowedAt)
        // A new copy waits, and so does the heir's own call.
        assertEquals(Waris.Result.Failed(Waris.Why.HALTED), owner.snapshot(book))
        assertEquals(Waris.Result.Failed(Waris.Why.HALTED), heir.ask(heirs[1].second).second)
        // A removed heir comes off every relay at once; the rest of the push waits for the switch.
        val gone = heirs[1].first.hid
        assertEquals(Waris.Result.Failed(Waris.Why.HALTED), owner.removeHeir(gone, book))
        assertNull(a.vaults[l.vault]!!.heirs[gone]); assertNull(b.vaults[l.vault]!!.heirs[gone])
        owner.turnOff()
        assertNull(a.vaults[l.vault]); assertNull(b.vaults[l.vault])
        assertFalse(owner.offPending())
    }

    @Test
    fun `a removed heir is deleted before any heir is put - a relay that held five takes the new one`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val (owner, heirs) = ownerWith(5, relays)
        val vault = owner.state()!!.vault
        val fifth = heirs[4].first.hid
        b.down = true
        owner.removeHeir(fifth, book)
        b.down = false
        val (e, q) = owner.startHeir("New", 30, "Shop")!!
        val (_, r) = Waris.Heir(MemStore(), relays).take(q)!!
        b.calls.clear()
        assertEquals(Waris.Result.Done(2, 2), owner.finishHeir(e.hid, r, book))
        assertNull("the removed heir is gone from the relay that missed his removal", b.vaults[vault]!!.heirs[fifth])
        assertNotNull(b.vaults[vault]!!.heirs[e.hid])
        val delete = b.calls.indexOfFirst { it == "DELETE $vault/heir/$fifth" }
        val put = b.calls.indexOfFirst { it.startsWith("PUT $vault/heir/") }
        assertTrue("the delete comes before every put", delete in 0 until put)
        assertTrue(fifth in owner.removed())
    }

    @Test
    fun `an heir a relay holds that the book does not name is deleted there, remembered, and his allow never counts`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val (owner, heirs) = ownerWith(1, relays)
        val st = owner.state()!!
        val stranger = outsider(relays, roads[0], st)
        val views = owner.status()!!
        assertEquals(setOf(heirs[0].first.hid), views.keys)
        assertTrue(views.values.none { it.allowed })
        assertNull(a.vaults[st.vault]!!.heirs[stranger])
        assertTrue(stranger in owner.removed())
        // Put there again with the pass: deleted again, and never put back by the owner's own runs.
        outsider(relays, roads[1], st, stranger)
        owner.retry(book)
        owner.status()
        assertNull(b.vaults[st.vault]!!.heirs[stranger])
        assertTrue(owner.heirs().none { it.hid == stranger })
    }

    @Test
    fun `merge - a stranger on a relay is unknown, an heir still awaiting their code is not, a foreign allow is refused again`() {
        val linked = "0011223344556677"; val awaiting = "8899aabbccddeeff"; val stranger = "0123456789abcdef"
        val heirs = listOf(heir(linked), Waris.HeirEntry(awaiting, "B", null, null, "c2VjcmV0", 30, 0L))
        val m = Waris.merge(
            heirs,
            listOf(
                Waris.RelayStatus("", 100, 1, listOf(rh(linked, asked = 50, allowed = 60), rh(awaiting), rh(stranger, allowed = 70))),
                Waris.RelayStatus("hs", 100, 1, listOf(rh(linked, asked = 50, open = 900)))
            ),
            100
        )
        assertEquals(listOf("" to stranger), m.unknown)
        assertEquals(listOf("" to linked), m.rejectAgain)
        val v = m.views.getValue(linked)
        assertFalse(v.allowed)
        // Still asked on the other relay, which never saw the foreign allow: the owner is asked.
        assertTrue(v.pending)
        assertEquals(900L, v.openAt)
        assertFalse(stranger in m.views)
    }

    @Test
    fun `an allow this phone never gave is refused again on the relay that shows it`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val (owner, heirs) = ownerWith(1, relays)
        val (e, l) = heirs[0]
        val st = owner.state()!!
        assertTrue(relays.call(roads[1], "POST", st.vault, "/heir/${e.hid}/allow", st.pass) is Waris.Reply.Ok)
        assertFalse(owner.status()!!.getValue(e.hid).allowed)
        assertNull(b.vaults[l.vault]!!.heirs[e.hid]!!.allowedAt)
        assertEquals(Waris.Why.WAITING, (Waris.Heir(MemStore(), relays).open(l, ByteArrayOutputStream()) as Waris.Opened.Failed).why)
        // The owner's own allow still opens the book.
        owner.allow(e.hid)
        owner.status()
        assertNotNull(b.vaults[l.vault]!!.heirs[e.hid]!!.allowedAt)
        assertEquals(Waris.Opened.Done, Waris.Heir(MemStore(), relays).open(l, ByteArrayOutputStream()))
    }

    @Test
    fun `a W from before this review is replaced once, and the heirs open the new copy, which the old W cannot`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val store = MemStore()
        val (owner, heirs) = ownerWith(1, relays, store)
        // As an older build left it: W in every full backup, nothing saying which copy it sealed.
        store.map.remove(Waris.K_KEY_SAFE); store.map.remove(Waris.K_SNAP_KV)
        val old = B64.decode(store.map.getValue(Waris.K_KEY))
        val version = store.map.getValue(Waris.K_KEY_VERSION).toInt()
        val before = owner.state()!!
        assertFalse(before.w.contentEquals(old))
        assertEquals(version + 1, before.keyVersion)
        assertEquals("1", store.map[Waris.K_KEY_SAFE])
        // Once: the next read keeps the new one.
        assertTrue(owner.state()!!.w.contentEquals(before.w))
        assertEquals(Waris.Result.Done(2, 2), owner.retry(book))
        assertEquals(before.keyVersion.toString(), store.map[Waris.K_SNAP_KV])
        owner.allow(heirs[0].first.hid)
        val l = heirs[0].second
        val out = ByteArrayOutputStream()
        assertEquals(Waris.Opened.Done, Waris.Heir(MemStore(), relays).open(l, out))
        assertEquals(bookJson, out.toString("UTF-8"))
        val v = a.vaults[l.vault]!!
        val sealed = (0 until v.snap!![1]).fold(ByteArray(0)) { acc, i -> acc + v.chunks.getValue(v.snap!![0] to i) }
        assertNull("the old W opens nothing the relay holds", WarisCrypto.openSnapshot(old, sealed, l.vault, v.snap!![0]))
    }

    @Test
    fun `a book restored without W makes a new one and seals the next copy and every box with it`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val store = MemStore()
        val (_, heirs) = ownerWith(1, relays, store)
        // A copy of the book restored on a new phone: everything but W (no copy carries it).
        val restored = MemStore()
        Waris.restored(emptyList(), store.map.map { (k, v) -> BookSetting(k, v) }, pairing = false)
            .forEach { restored.map[it.key] = it.value!! }
        assertNull(restored.map[Waris.K_KEY])
        val owner = Waris.Owner(restored, relays)
        val st = owner.state()!!
        assertEquals(store.map.getValue(Waris.K_KEY_VERSION).toInt() + 1, st.keyVersion)
        val dirty = JSONArray(restored.map.getValue(Waris.K_DIRTY))
        assertEquals(setOf("", "hs"), (0 until dirty.length()).map { dirty.getString(it) }.toSet())
        assertEquals(Waris.Result.Done(2, 2), owner.retry(book))
        owner.allow(heirs[0].first.hid)
        assertEquals(Waris.Opened.Done, Waris.Heir(MemStore(), relays).open(heirs[0].second, ByteArrayOutputStream()))
    }

    @Test
    fun `restored - a vault this book keeps stays as it is, a book with none takes the copy's but never its W, a pairing takes none`() {
        fun s(vararg kv: Pair<String, String>) = kv.map { BookSetting(it.first, it.second) }
        fun heirsJson(vararg hids: String) = JSONArray().apply { hids.forEach { put(JSONObject().put("hid", it).put("name", "N")) } }.toString()
        fun map(l: List<BookSetting>): Map<String, String> = l.associate { it.key to it.value.orEmpty() }
        val secret = B64.encode(ByteArray(32) { 1 })
        val v1 = "0123456789abcdef0123456789abcdef"; val v2 = "fedcba9876543210fedcba9876543210"
        val heirA = "00000000000000aa"; val heirB = "00000000000000bb"; val removed1 = "00000000000000e1"; val removed2 = "00000000000000ff"
        val here = s(
            Waris.K_VAULT to v1, Waris.K_OWNER_SECRET to secret, Waris.K_KEY to "W-here", Waris.K_KEY_SAFE to "1",
            Waris.K_HEIRS to heirsJson(heirA, heirB), Waris.K_REMOVED to JSONArray(listOf(removed1)).toString(),
            Waris.K_LINKS to "links-here"
        )
        // An older copy: an heir it had removed still in its list as written before, and an older W.
        val copy = s(
            Waris.K_VAULT to v1, Waris.K_OWNER_SECRET to secret, Waris.K_KEY to "W-copy", Waris.K_KEY_SAFE to "1",
            Waris.K_HEIRS to heirsJson(heirA, removed2), Waris.K_REMOVED to JSONArray(listOf(removed2)).toString(),
            Waris.K_LINKS to "links-copy", Waris.K_OFF to "off-copy", BookSettings.PRICES_INCLUDE_TAX to "1"
        )
        // This book's vault: its own list and W; both removed lists; a turn-off the copy still owed.
        val kept = map(Waris.restored(here, copy, pairing = false))
        assertEquals("W-here", kept[Waris.K_KEY])
        assertEquals(listOf(heirA, heirB), JSONArray(kept.getValue(Waris.K_HEIRS)).let { j -> (0 until j.length()).map { j.getJSONObject(it).getString("hid") } })
        assertEquals(listOf(removed1, removed2), JSONArray(kept.getValue(Waris.K_REMOVED)).let { j -> (0 until j.length()).map { j.getString(it) } })
        assertEquals("links-here", kept[Waris.K_LINKS])
        assertEquals("off-copy", kept[Waris.K_OFF])
        assertFalse("never another setting", BookSettings.PRICES_INCLUDE_TAX in kept)
        // A new phone: the copy's vault and heirs, never its W, and never a removed heir.
        val taken = map(Waris.restored(emptyList(), copy, pairing = false))
        assertEquals(v1, taken[Waris.K_VAULT])
        assertNull(taken[Waris.K_KEY]); assertNull(taken[Waris.K_KEY_SAFE])
        assertEquals(listOf(heirA), JSONArray(taken.getValue(Waris.K_HEIRS)).let { j -> (0 until j.length()).map { j.getJSONObject(it).getString("hid") } })
        assertEquals("links-copy", taken[Waris.K_LINKS])
        // A vault turned off here never comes back from a copy.
        val off = map(Waris.restored(s(Waris.K_REMOVED to JSONArray(listOf(v1)).toString()), copy, pairing = false))
        assertNull(off[Waris.K_VAULT]); assertNull(off[Waris.K_HEIRS])
        // A pairing: nothing of the other device's vault or links; this book's own (none on a pairing) stays.
        val paired = map(Waris.restored(emptyList(), copy, pairing = true))
        assertNull(paired[Waris.K_VAULT]); assertNull(paired[Waris.K_LINKS]); assertNull(paired[Waris.K_OFF])
        // A copy whose vault is another: this book's own stays, the copy's is not set up.
        val other = map(Waris.restored(here, copy.map { if (it.key == Waris.K_VAULT) BookSetting(it.key, v2) else it }, pairing = false))
        assertEquals(v1, other[Waris.K_VAULT])
    }

    @Test
    fun `a book whose shop changed while it was written seals nothing and sends nothing`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val (owner, _) = ownerWith(1, relays)
        val before = owner.state()!!
        a.calls.clear(); b.calls.clear()
        val moved = try {
            owner.snapshot(Waris.Book { out -> out.write(bookJson.toByteArray()); throw Waris.BookMoved() })
            false
        } catch (e: Waris.BookMoved) {
            true
        }
        assertTrue(moved)
        assertTrue(a.calls.none { it.contains("snap") } && b.calls.none { it.contains("snap") })
        assertEquals(before.snapV, owner.state()!!.snapV)
    }

    @Test
    fun `a refusal a relay did not take is owed to it until it answers, and then it is refused there`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val (owner, heirs) = ownerWith(1, relays)
        val (e, l) = heirs[0]
        Waris.Heir(MemStore(), relays).ask(l)
        b.down = true
        assertEquals(Waris.Result.Done(1, 2), owner.refuse(e.hid))
        assertEquals(setOf("hs"), owner.refuseLeft())
        owner.status()
        assertEquals(setOf("hs"), owner.refuseLeft())
        b.down = false
        owner.status()
        assertTrue(owner.refuseLeft().isEmpty())
        assertNull(b.vaults[l.vault]!!.heirs[e.hid]!!.askedAt)
    }

    @Test
    fun `a relay that refuses the vault's pass is noted, a relay busy today is said as busy, a full one as full`() = runBlocking {
        val a = FakeRelay(); val b = FakeRelay()
        val relays = Waris.Relays(Both(a, b), roads) { false }
        val (owner, _) = ownerWith(1, relays)
        val vault = owner.state()!!.vault
        // Someone else's pass holds the vault id on the second relay.
        b.vaults.remove(vault)
        b.call("POST", "https://two.test/v1/w/$vault/setup", mapOf("x-rk-pass" to "f".repeat(64)), null)
        owner.status()
        assertEquals(setOf("hs"), owner.relaysRefusing())
        b.vaults.remove(vault)
        owner.status()
        assertTrue(owner.relaysRefusing().isEmpty())
        // The relays' daily cap and their full store, on a new vault.
        fun answering(code: Int, why: String) = Waris.Relays(
            { _, _, _, _ -> code to JSONObject().put("ok", false).put("why", why).toString().toByteArray() }, roads) { false }
        assertEquals(Waris.Result.Failed(Waris.Why.BUSY), Waris.Owner(MemStore(), answering(429, "waris-day")).enable())
        assertEquals(Waris.Result.Failed(Waris.Why.FULL), Waris.Owner(MemStore(), answering(507, "waris-full")).enable())
    }
}
