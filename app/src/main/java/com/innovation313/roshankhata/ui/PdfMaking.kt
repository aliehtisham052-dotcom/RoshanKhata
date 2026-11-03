package com.innovation313.roshankhata.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.string

/**
 * A statement on its way (3 Nov 2026, the owner's mockup "Statement PDF — pages being made"; a "Preparing
 * statement…" toast said nothing of a long one): nothing at all for a statement made within [SHOW_AFTER_MS] —
 * no dialog flashes for a short account — else a small dialog whose page stack grows with the pages the writer
 * has really finished, "Page 2 of 5" under it, and a check on the stack once the file is written, before the
 * ready dialog. Every call may come from the writer's thread; after the screen has gone they do nothing.
 */
class PdfMaking private constructor(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null
    private var stack: PageStack? = null
    private var line: TextView? = null
    private var made = 0
    private var total = 0
    private var finished = false
    private val open = Runnable { if (!finished && !activity.isFinishing && !activity.isDestroyed) show() }

    private fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_pdf_making, null)
        stack = view.findViewById(R.id.pdfStack)
        line = view.findViewById(R.id.tvPdfPage)
        TextFit.relax(view)
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.generating_statement)
            .setView(view)
            // Back only hides it: the statement is still made and offered.
            .setCancelable(true)
            .create()
            .also { it.setCanceledOnTouchOutside(false); it.show() }
        (activity as? ComponentActivity)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog?.let { if (it.isShowing) it.dismiss() } }
        })
        say()
    }

    private fun say() {
        stack?.pages(made)
        line?.text = if (total > 0) Digits.string(activity.resources, R.string.pdf_stmt_page, made.coerceAtLeast(1), total) else ""
    }

    /** The writer finished page [done] of [all]. */
    fun page(done: Int, all: Int) {
        main.post {
            made = done
            total = all
            say()
        }
    }

    /** Written: the check lands on the stack (when it was showing), then [then] — the ready dialog. */
    fun done(then: () -> Unit) {
        main.post {
            finished = true
            main.removeCallbacks(open)
            if (activity.isFinishing || activity.isDestroyed) return@post
            val d = dialog
            if (d == null || !d.isShowing) { then(); return@post }
            stack?.pages(made.coerceAtLeast(1))
            stack?.finish()
            main.postDelayed({
                // The ready dialog first, then this one goes: the dimmed page never flickers between the two.
                if (!activity.isFinishing && !activity.isDestroyed) then()
                if (d.isShowing) d.dismiss()
            }, if (Motion.enabled(activity)) 480L else 0L)
        }
    }

    /** Not written: the dialog goes (the caller says why). */
    fun failed() {
        main.post {
            finished = true
            main.removeCallbacks(open)
            dialog?.let { if (it.isShowing) it.dismiss() }
        }
    }

    companion object {
        /** A statement made within this long never shows the dialog at all. */
        const val SHOW_AFTER_MS = 400L

        fun start(activity: Activity): PdfMaking = PdfMaking(activity).also { it.main.postDelayed(it.open, SHOW_AFTER_MS) }
    }
}
