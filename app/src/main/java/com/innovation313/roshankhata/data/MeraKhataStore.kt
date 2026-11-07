package com.innovation313.roshankhata.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The shops' links this phone opened as a customer (7 Nov 2026, "Mera Khata"): each link, the last sealed page it
 * brought and that answer's ETag, when it was last asked and what the server last said of it. Kept in the phone's
 * preferences — never in a shop's book or a backup: these are the customer's own, not any shop's.
 */
object MeraKhataStore {

    const val MAX = 50
    private const val PREFS = "mera_khata"
    private const val KEY = "pages"

    enum class State { OK, GONE, WRONG }

    data class Saved(
        val link: CustomerLink.Link,
        val blob: String?,
        val etag: String?,
        val askedAt: Long,
        val state: State
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Saved> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val link = CustomerLink.parse(o.optString("l")) ?: return@mapNotNull null
            Saved(
                link, o.optString("b").ifEmpty { null }, o.optString("e").ifEmpty { null }, o.optLong("a"),
                runCatching { State.valueOf(o.optString("s", "OK")) }.getOrDefault(State.OK)
            )
        }
    }.getOrDefault(emptyList())

    fun any(context: Context): Boolean = all(context).isNotEmpty()

    private fun save(context: Context, list: List<Saved>) {
        val arr = JSONArray()
        list.take(MAX).forEach { s ->
            arr.put(JSONObject().apply {
                put("l", s.link.id + "." + s.link.key)
                s.blob?.let { put("b", it) }
                s.etag?.let { put("e", it) }
                put("a", s.askedAt)
                put("s", s.state.name)
            })
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    /** A link opened: first in the list (a link seen before keeps what it brought). */
    @Synchronized
    fun add(context: Context, link: CustomerLink.Link) {
        val list = all(context)
        val old = list.firstOrNull { it.link == link } ?: Saved(link, null, null, 0L, State.OK)
        save(context, listOf(old) + list.filter { it.link != link })
    }

    @Synchronized
    fun update(context: Context, saved: Saved) {
        save(context, all(context).map { if (it.link == saved.link) saved else it })
    }

    @Synchronized
    fun remove(context: Context, link: CustomerLink.Link) {
        save(context, all(context).filter { it.link != link })
    }
}
