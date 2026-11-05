package com.innovation313.roshankhata.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Waris
import com.innovation313.roshankhata.data.WarisAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home's question for the owner (Waris ki Chaabi, 4 Nov 2026): while an heir's request waits — neither refused nor
 * allowed — every time Home opens, "<name> asked to open your book. It opens on <date> unless you refuse." with
 * Refuse and Allow now. The relays are read at most every six hours ([WarisAndroid.checkIfDue]); between, the last
 * word read is used. Never on a helper's read-only phone, never to a cashier on a counter.
 */
object WarisAsk {

    @Volatile private var showing = false

    fun onHome(activity: AppCompatActivity) {
        if (!WarisAndroid.ownerMay(activity)) return
        val app = activity.applicationContext
        activity.lifecycleScope.launch {
            val ask = withContext(Dispatchers.IO) {
                runCatching {
                    val owner = WarisAndroid.owner(app)
                    val read = WarisAndroid.checkIfDue(app)
                    if (owner.state() == null) return@runCatching null
                    val views = read ?: owner.views()
                    val heirs = owner.heirs()
                    views.values.filter { it.pending }.sortedBy { it.openAt ?: Long.MAX_VALUE }
                        .firstNotNullOfOrNull { v -> heirs.firstOrNull { it.hid == v.hid && it.linked }?.let { it to v } }
                }.getOrNull()
            } ?: return@launch
            if (showing || activity.isFinishing || activity.isDestroyed ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            show(activity, ask.first, ask.second)
        }
    }

    private fun show(activity: AppCompatActivity, heir: Waris.HeirEntry, v: Waris.HeirView) {
        val name = Format.isolate(heir.name)
        val body = v.openAt?.let { activity.getString(R.string.waris_asked_body, name, Format.dateOnly(it)) }
            ?: activity.getString(R.string.waris_ask_body, name)
        showing = true
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.waris_asked_title)
            .setMessage(body)
            .setPositiveButton(R.string.waris_refuse) { _, _ -> decide(activity, heir, refuse = true) }
            .setNeutralButton(R.string.waris_allow_now) { _, _ ->
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.waris_allow_now)
                    .setMessage(activity.getString(R.string.waris_allow_body, name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.waris_allow_now) { _, _ -> decide(activity, heir, refuse = false) }
                    .show()
            }
            .setNegativeButton(R.string.close, null)
            .setOnDismissListener { showing = false }
            .show()
    }

    private fun decide(activity: AppCompatActivity, heir: Waris.HeirEntry, refuse: Boolean) {
        val app = activity.applicationContext
        activity.lifecycleScope.launch {
            // On the app's scope: leaving Home never cuts a refusal half way (a relay that missed it hears it at the
            // next status run in any case).
            val r = runCatching {
                AppScope.async {
                    val owner = WarisAndroid.owner(app)
                    if (refuse) owner.refuse(heir.hid) else owner.allow(heir.hid)
                }.await()
            }.getOrNull()
            if (activity.isFinishing || activity.isDestroyed) return@launch
            val words = when {
                r is Waris.Result.Done -> activity.getString(if (refuse) R.string.waris_refused else R.string.waris_allowed)
                else -> activity.getString(R.string.waris_offline)
            }
            Toast.makeText(activity, words, Toast.LENGTH_LONG).show()
        }
    }
}
