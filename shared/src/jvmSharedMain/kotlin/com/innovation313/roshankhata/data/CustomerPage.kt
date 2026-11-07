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
     * The days a page may carry (7 Nov 2026, the review's L8): 1 Jan 2000 to 1 Jan 2100 (UTC); anything else is no
     * day. A day is drawn in the phone's own calendar, and one far outside (Long.MAX_VALUE) gave the Jalali months a
     * 743rd month, which crashed the screen.
     */
    const val EARLIEST = 946_684_800_000L
    const val LATEST = 4_102_444_800_000L

    /** The page languages the app writes ([CustomerLink.pageLanguage]); anything else reads as English. */
    private val LANGUAGES = setOf("en", "ur", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "id")

    /**
     * The link in its two shapes: the shop's https link (…/#<id>.<key>: the key only after the #, which no browser
     * sends to a server), or the page's button (roshankhata://khata?l=<id>.<key>, which the browser hands to this app
     * alone). Only the shop's own host or the app's scheme; anything else is no link. Never a key in an https link's
     * query (7 Nov 2026, the review's L6): a server on the way could keep it in its logs.
     */
    fun linkFrom(url: String?, host: String = CustomerLink.HOST): CustomerLink.Link? {
        val u = runCatching { URI(url?.trim().orEmpty()) }.getOrNull() ?: return null
        return when (u.scheme?.lowercase()) {
            "https" -> if (u.host.equals(host, ignoreCase = true)) CustomerLink.parse(u.rawFragment) else null
            SCHEME -> if (!u.host.equals("khata", ignoreCase = true)) null
                else CustomerLink.parse(u.rawFragment)
                    ?: CustomerLink.parse(u.rawQuery?.split('&')?.firstOrNull { it.startsWith("l=") }?.removePrefix("l="))
            else -> null
        }
    }

    /** One entry as the page carries it (only when the shop shows them). */
    data class Line(val at: Long, val amount: Double, val gave: Boolean, val goods: Boolean, val words: String?)

    /**
     * A page, read. [balance] > 0: the customer owes the shop; < 0 the shop owes him. [paused]: the customer is in
     * the shop's bin, and the page says only that. [seq]: the shop's counter for this page, which only grows (0 on a
     * page made before 7 Nov 2026).
     */
    data class Page(
        val shop: String, val name: String, val supplier: Boolean, val balance: Double,
        val currency: String, val decimals: Int, val language: String, val updated: Long,
        val paused: Boolean, val lines: List<Line>, val seq: Long = 0L
    )

    /**
     * The page's text (opened JSON) as a [Page]; null when it is not one. No more is taken than the shop's app
     * writes (7 Nov 2026, the review's L9): three entries, a shop and a name of 60 characters, goods of 40, a
     * currency sign of 5, a day between [EARLIEST] and [LATEST], a figure that is a number.
     */
    fun read(text: String?): Page? = runCatching {
        val o = JSONObject(text ?: return null)
        if (o.optInt("v") < 2) return null
        val seq = o.optLong("q").takeIf { it in 0..LATEST } ?: 0L
        val language = o.optString("l", "en").takeIf { it in LANGUAGES } ?: "en"
        if (o.optInt("z") == 1) return Page("", "", false, 0.0, "", 2, language, 0L, true, emptyList(), seq)
        val k = o.optInt("k", 2).takeIf { it in 0..3 } ?: 2
        val e = o.optJSONArray("e")
        val lines = if (e == null) emptyList() else (0 until minOf(e.length(), CustomerLink.MAX_ENTRIES)).mapNotNull { i ->
            e.optJSONObject(i)?.let { x ->
                Line(day(x.optLong("d")), figure(x.optDouble("a", 0.0)), x.optInt("g") == 1, x.optInt("m") == 1,
                    clip(x.optString("x").trim(), 40).ifEmpty { null })
            }
        }
        Page(
            shop = clip(o.optString("s").trim(), 60), name = clip(o.optString("n").trim(), 60), supplier = o.optString("p") == "s",
            balance = figure(o.optDouble("b", 0.0)),
            currency = clip(o.optString("c"), 5), decimals = k, language = language,
            updated = day(o.optLong("t")), paused = false, lines = lines, seq = seq
        )
    }.getOrNull()

    private fun day(t: Long): Long = t.takeIf { it in EARLIEST..LATEST } ?: 0L
    private fun figure(v: Double): Double = v.takeIf { it.isFinite() } ?: 0.0

    /** [s] cut to [n] characters without splitting a letter written as two, as the shop's app cuts it. */
    private fun clip(s: String, n: Int): String {
        if (s.length <= n) return s
        val end = if (Character.isHighSurrogate(s[n - 1])) n - 1 else n
        return s.substring(0, end)
    }

    /** What a page fetched is, beside the one kept (7 Nov 2026, the review's L4). */
    enum class Fresh {
        /** The first, or as new as the one kept or newer: it is taken. */
        NEWER,
        /** The same page under a lower counter (two phones of one book sent it): nothing to say. */
        SAME,
        /** A different page under a lower counter: an older page served again; the one kept stays. */
        OLDER
    }

    fun fresh(kept: Page?, got: Page): Fresh = when {
        kept == null || got.seq >= kept.seq -> Fresh.NEWER
        got.copy(seq = kept.seq) == kept -> Fresh.SAME
        else -> Fresh.OLDER
    }

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
                val page = read(CustomerLink.open(link, blob)) ?: return Fetched.Wrong
                Fetched.Ok(page, blob, tag)
            }
            else -> Fetched.Offline
        }
    }

    /** A page kept from an earlier look, opened again without the network. */
    fun reopen(link: CustomerLink.Link, blob: String?): Page? = blob?.let { read(CustomerLink.open(link, it)) }
}
