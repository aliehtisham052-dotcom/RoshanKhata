package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.screens.DraftKeep
import org.json.JSONArray

/**
 * A long form's typed text, kept in the phone's own preferences (8 Nov 2026 review, M88): it survives a rotation, dark
 * mode and a killed process. A screen clears it when it starts fresh ([clearOnFreshStart]) so a form left by Back is
 * never offered again days later. Text only, in the app's private storage; it holds counts and quantities, nothing secret.
 */
class PrefsDraftKeep(context: Context, name: String) : DraftKeep {
    // Per shop (24 Nov 2026, stage 4): the key was the bare name, so a count, return or order typed in one shop and left
    // by a kill was offered in the shop opened next. Business 1 keeps its old key, so an installed draft still reads.
    private val key = com.innovation313.roshankhata.data.BusinessKeys.keyOf(
        name, com.innovation313.roshankhata.data.Businesses.active(context.applicationContext).id)
    private val prefs = context.applicationContext.getSharedPreferences("form_drafts", Context.MODE_PRIVATE)

    override fun read(): List<String>? = runCatching {
        prefs.getString(key, null)?.let { s -> JSONArray(s).let { a -> List(a.length()) { a.getString(it) } } }
    }.getOrNull()

    override fun write(values: List<String>?) {
        prefs.edit().apply { if (values == null) remove(key) else putString(key, JSONArray(values).toString()) }.apply()
    }

    /** Call from onCreate with savedInstanceState == null: a screen opened fresh never resumes an old draft. */
    fun clearOnFreshStart(savedInstanceState: Any?) { if (savedInstanceState == null) write(null) }
}
