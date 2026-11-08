package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.screens.DraftKeep
import org.json.JSONArray

/**
 * A long form's typed text, kept in the phone's own preferences (8 Nov 2026 review, M88): it survives a rotation, dark
 * mode and a killed process. A screen clears it when it starts fresh ([clearOnFreshStart]) so a form left by Back is
 * never offered again days later. Text only, in the app's private storage; it holds counts and quantities, nothing secret.
 */
class PrefsDraftKeep(context: Context, private val name: String) : DraftKeep {
    private val prefs = context.applicationContext.getSharedPreferences("form_drafts", Context.MODE_PRIVATE)

    override fun read(): List<String>? = runCatching {
        prefs.getString(name, null)?.let { s -> JSONArray(s).let { a -> List(a.length()) { a.getString(it) } } }
    }.getOrNull()

    override fun write(values: List<String>?) {
        prefs.edit().apply { if (values == null) remove(name) else putString(name, JSONArray(values).toString()) }.apply()
    }

    /** Call from onCreate with savedInstanceState == null: a screen opened fresh never resumes an old draft. */
    fun clearOnFreshStart(savedInstanceState: Any?) { if (savedInstanceState == null) write(null) }
}
