package com.innovation313.roshankhata.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The shops' links this phone opened as a customer (7 Nov 2026, "Mera Khata"): each link, the last sealed page it
 * brought and that answer's ETag, when it was last asked and what the server last said of it. Kept in the phone's
 * preferences — never in a shop's book or a backup: these are the customer's own, not any shop's.
 *
 * A link is kept only once its page has opened ([confirm]; 7 Nov 2026, the review's L10): until then it waits apart
 * ([pending], at most [MAX_PENDING]), so links that never open cannot push a real shop out of the list.
 */
object MeraKhataStore {

    const val MAX = 50
    /** Links opened whose page has not opened yet, newest first. */
    const val MAX_PENDING = 5
    private const val PREFS = "mera_khata"
    private const val KEY = "pages"
    private const val PENDING = "pending"

    enum class State { OK, GONE, WRONG }

    data class Saved(
        val link: CustomerLink.Link,
        val blob: String?,
        val etag: String?,
        val askedAt: Long,
        val state: State,
        /** The highest page counter seen for this link: a page under a lower one is an older page, not taken. */
        val seq: Long = 0L,
        /** The last answer was an older page than the one kept (it stays; the card says so). */
        val older: Boolean = false
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Saved> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val link = CustomerLink.parse(o.optString("l")) ?: return@mapNotNull null
            Saved(
                link, o.optString("b").ifEmpty { null }, o.optString("e").ifEmpty { null }, o.optLong("a"),
                runCatching { State.valueOf(o.optString("s", "OK")) }.getOrDefault(State.OK),
                o.optLong("q").coerceAtLeast(0L), o.optBoolean("o")
            )
        }
    }.getOrDefault(emptyList())

    /** Links opened and not yet kept: their page has not opened yet. */
    fun pending(context: Context): List<CustomerLink.Link> = runCatching {
        val arr = JSONArray(prefs(context).getString(PENDING, "[]"))
        (0 until arr.length()).mapNotNull { i -> CustomerLink.parse(arr.optString(i)) }
    }.getOrDefault(emptyList())

    fun any(context: Context): Boolean = all(context).isNotEmpty() || pending(context).isNotEmpty()

    private fun save(context: Context, list: List<Saved>) {
        val arr = JSONArray()
        list.take(MAX).forEach { s ->
            arr.put(JSONObject().apply {
                put("l", s.link.id + "." + s.link.key)
                s.blob?.let { put("b", it) }
                s.etag?.let { put("e", it) }
                put("a", s.askedAt)
                put("s", s.state.name)
                if (s.seq > 0L) put("q", s.seq)
                if (s.older) put("o", true)
            })
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun savePending(context: Context, list: List<CustomerLink.Link>) {
        val arr = JSONArray()
        list.take(MAX_PENDING).forEach { arr.put(it.id + "." + it.key) }
        prefs(context).edit().putString(PENDING, arr.toString()).apply()
    }

    /** A link opened: a shop already kept goes first in the list; a new one waits apart until its page opens. */
    @Synchronized
    fun add(context: Context, link: CustomerLink.Link) {
        val list = all(context)
        list.firstOrNull { it.link == link }?.let { known ->
            save(context, listOf(known) + list.filter { it.link != link })
            return
        }
        savePending(context, listOf(link) + pending(context).filter { it != link })
    }

    /** A link whose page opened: kept from now on, first in the list. */
    @Synchronized
    fun confirm(context: Context, saved: Saved) {
        savePending(context, pending(context).filter { it != saved.link })
        save(context, listOf(saved) + all(context).filter { it.link != saved.link })
    }

    /** A link that waited and never opened (taken back, or not a page this key opens): it goes. */
    @Synchronized
    fun dropPending(context: Context, link: CustomerLink.Link) {
        savePending(context, pending(context).filter { it != link })
    }

    @Synchronized
    fun update(context: Context, saved: Saved) {
        save(context, all(context).map { if (it.link == saved.link) saved else it })
    }

    @Synchronized
    fun remove(context: Context, link: CustomerLink.Link) {
        savePending(context, pending(context).filter { it != link })
        save(context, all(context).filter { it.link != link })
    }
}
