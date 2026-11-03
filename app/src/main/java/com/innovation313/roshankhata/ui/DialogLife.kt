package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * A dialog that lives and goes with its screen (3 Nov 2026, the review: the sale-saved sheet, the convert's sheet,
 * the statement's pages and the restore's steps each put a watcher on the screen and never took it away, so every
 * one of them stayed in memory until the screen closed — on a counter left open all day, one sheet per sale).
 * The dialog is closed when the screen is destroyed, and the watcher is taken away as soon as the dialog is closed.
 * A dialog has one dismiss listener: [onDismiss] is the dialog's own closing work, run through it.
 */
object DialogLife {

    fun bind(
        activity: Activity,
        dialog: Dialog,
        onDismiss: (() -> Unit)? = null,
        /** What the screen's end does to it: by default, closes it (on the main thread, at once). */
        whenScreenGoes: () -> Unit = { if (dialog.isShowing) runCatching { dialog.dismiss() } }
    ) {
        val life = (activity as? ComponentActivity)?.lifecycle
        val watcher = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = whenScreenGoes()
        }
        life?.addObserver(watcher)
        dialog.setOnDismissListener {
            life?.removeObserver(watcher)
            onDismiss?.invoke()
        }
    }
}
