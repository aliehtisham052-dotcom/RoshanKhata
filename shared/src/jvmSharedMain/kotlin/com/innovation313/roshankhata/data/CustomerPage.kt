package com.innovation313.roshankhata.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * The customer's side of [CustomerLink] (7 Nov 2026, "Mera Khata"): a customer who got a shop's link opens it in
 * Roshan Khata and sees what he owes that shop — the same sealed page the browser opens (web/khata/view.js), read
 * the same way, so the two never disagree.
 *
 * Kept light for the server, as the owner asked ("zyada log apna balance dekh sakein"): a page is a plain file on
 * the hosting, asked with the last answer's ETag (an unchanged page is a 304 of a few bytes), and the app asks
 * only when the screen opens and the last look is older than [FRESH_MS]; a tap on refresh no sooner than
 * [AGAIN_MS]. Nothing runs in the background and nothing goes through live sync's relays.
 */
object CustomerPage {

    const val FRESH_MS = 10L * 60 * 1000
    const val AGAIN_MS = 60L * 1000
    /** The custom address the web page's "Open in the app" button uses: roshankhata://khata?l=<id>.<key>. */
    const val SCHEME = "roshankhata"

    /**
     * The link in any of its shapes: the shop's https link (…/#<id>.<key>), the same with ?l= (an App Link
     * whose fragment a browser dropped), or the page's button (roshankhata://khata?l=<id>.<key>). Only the
     * shop's own host or the app's scheme; anything else is no link.
     */
    fun linkFrom(url: String?, host: String = CustomerLink.HOST): CustomerLink.Link? {
        val u = runCatching { URI(url?.trim().orEmpty()) }.getOrNull() ?: return null
        val ok = when (u.scheme?.lowercase()) {
            "https" -> u.host.equals(host, ignoreCase = true)
            SCHEME -> u.host.equals("khata", ignoreCase = true)
            else -> false
        }
        if (!ok) return null
        u.rawFragment?.let { CustomerLink.parse(it) }?.let { return it }
        val l = u.rawQuery?.split('&')?.firstOrNull { it.startsWith("l=") }?.removePrefix("l=")
        return CustomerLink.parse(l)
    }

    /** One entry as the page carries it (only when the shop shows them). */
    data class Line(val at: Long, val amount: Double, val gave: Boolean, val goods: Boolean, val words: String?)

    /**
     * A page, read. [balance] > 0: the customer owes the shop; < 0 the shop owes him. [paused]: the customer is in
     * the shop's bin, and the page says only that.
     */
    data class Page(
        val shop: String, val name: String, val supplier: Boolean, val balance: Double,
        val currency: String, val decimals: Int, val language: String, val updated: Long,
        val paused: Boolean, val lines: List<Line>
    )

    /** The page's text (opened JSON) as a [Page]; null when it is not one. */
    fun read(text: String?): Page? = runCatching {
        val o = JSONObject(text ?: return null)
        if (o.optInt("v") < 2) return null
        if (o.optInt("z") == 1) return Page("", "", false, 0.0, "", 2, o.optString("l", "en"), 0L, true, emptyList())
        val k = o.optInt("k", 2).takeIf { it in 0..3 } ?: 2
        val e = o.optJSONArray("e")
        val lines = if (e == null) emptyList() else (0 until e.length()).mapNotNull { i ->
            e.optJSONObject(i)?.let { x ->
                Line(x.optLong("d"), x.optDouble("a", 0.0), x.optInt("g") == 1, x.optInt("m") == 1,
                    x.optString("x").trim().ifEmpty { null })
            }
        }
        Page(
            shop = o.optString("s").trim(), name = o.optString("n").trim(), supplier = o.optString("p") == "s",
            balance = o.optDouble("b", 0.0).takeIf { it.isFinite() } ?: 0.0,
            currency = o.optString("c").take(5), decimals = k, language = o.optString("l", "en"),
            updated = o.optLong("t"), paused = false, lines = lines
        )
    }.getOrNull()

    /** Which way a balance runs, as the page says it (view.js: half a thousandth either side is settled). */
    enum class Side { OWES, OWED, SETTLED }

    fun sideOf(balance: Double): Side = when {
        balance > 0.0005 -> Side.OWES
        balance < -0.0005 -> Side.OWED
        else -> Side.SETTLED
    }

    /**
     * An amount as the page shows it (view.js's money): no sign, whole amounts without decimals, a fraction to the
     * currency's own [decimals], thousands with a comma and the fraction with a point — Indonesian the other way
     * round — and the currency in front, the whole kept left to right inside any sentence.
     */
    fun money(currency: String, decimals: Int, language: String, amount: Double): String {
        val v = java.math.BigDecimal.valueOf(kotlin.math.abs(if (amount.isFinite()) amount else 0.0))
        val k = decimals.coerceIn(0, 3)
        val whole = v.stripTrailingZeros().scale() <= 0 || k == 0
        val fixed = v.setScale(if (whole) 0 else k, java.math.RoundingMode.HALF_UP).toPlainString()
        val parts = fixed.split('.')
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        var s = if (parts.size > 1) grouped + "." + parts[1] else grouped
        if (language == "id") s = s.map { when (it) { ',' -> '.'; '.' -> ','; else -> it } }.joinToString("")
        val c = currency.trim()
        return "\u2066" + (if (c.isEmpty()) "" else "$c ") + s + "\u2069"
    }

    sealed interface Fetched {
        /** The page as it is now; [blob] is the sealed text, kept so the screen opens without the network. */
        data class Ok(val page: Page, val blob: String, val etag: String?) : Fetched
        /** Unchanged since [etag]: the page kept is still right. */
        object Same : Fetched
        /** The shop took the link back, or it was never there. */
        object Gone : Fetched
        /** The file is there but this link's key does not open it. */
        object Wrong : Fetched
        object Offline : Fetched
    }

    /** The way out to the server; a test passes its own. Answers the status, the body and the ETag. */
    fun interface Http {
        fun get(url: String, etag: String?): Triple<Int, ByteArray, String?>
    }

    val plain = Http { url, etag ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 15_000
            c.useCaches = false
            c.instanceFollowRedirects = false
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            val code = c.responseCode
            val bytes = if (code == 200) c.inputStream.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > 16 * 1024) break }
                out.toByteArray()
            } else ByteArray(0)
            Triple(code, bytes, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }

    /** The page behind [link] from [root] (the hosting), asked with [etag] when one was kept. */
    fun fetch(link: CustomerLink.Link, etag: String?, root: String? = CustomerLink.base, http: Http = plain): Fetched {
        val base = root?.trimEnd('/') ?: return Fetched.Offline
        val (code, bytes, tag) = try { http.get("$base/d/${link.id}.txt", etag) } catch (e: java.io.IOException) { return Fetched.Offline }
        return when (code) {
            304 -> Fetched.Same
            404, 410 -> Fetched.Gone
            200 -> {
                val blob = String(bytes, Charsets.UTF_8).trim()
                if (blob == "!gone") return Fetched.Gone
                val page = read(CustomerLink.open(link.key, blob)) ?: return Fetched.Wrong
                Fetched.Ok(page, blob, tag)
            }
            else -> Fetched.Offline
        }
    }

    /** A page kept from an earlier look, opened again without the network. */
    fun reopen(link: CustomerLink.Link, blob: String?): Page? = blob?.let { read(CustomerLink.open(link.key, it)) }
}
