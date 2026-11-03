package com.innovation313.roshankhata.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.quantity

/**
 * A restore, step by step (3 Nov 2026, the owner's mockup "Restore steps"; it took the place of a
 * "Restoring…" toast that said nothing until the end). Each step is ticked when the work it names has really
 * finished — the caller says so — never on a timer: a tick may come a little after its work (each arrival
 * waits for the one before it, about a third of a second), never before it. A failure shows on the step
 * that failed, with what it means; the end shows what to know and the one button.
 *
 * Every call may come from any thread and after the screen has gone: it is posted to the main thread, and
 * a dialog that is no longer showing does nothing. The dialog cannot be dismissed while the work runs.
 */
class RestoreSteps private constructor(
    private val activity: Activity,
    private val dialog: AlertDialog,
    private val root: View,
    private val marks: List<StepMark>,
    private val labels: List<TextView>,
    private val details: List<TextView>
) {
    /** One step: what it does, said as done ("Backup checked"), and a line under it. */
    class Step(val label: CharSequence, val detail: CharSequence? = null)

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<() -> Long>()
    private var running = false
    private var gone = false

    private val title: TextView = root.findViewById(R.id.tvStepsTitle)
    private val note: TextView = root.findViewById(R.id.tvStepsNote)
    private val hint: TextView = root.findViewById(R.id.tvStepsHint)
    private val button: MaterialButton = root.findViewById(R.id.btnStepsDone)

    /** The work for step [i] has begun. */
    fun start(i: Int) = later {
        if (i !in marks.indices) return@later 0L
        labels[i].setTextColor(ContextCompat.getColor(activity, R.color.ink))
        labels[i].setTypeface(faces[i], android.graphics.Typeface.BOLD)
        marks[i].show(StepMark.State.RUN)
    }

    /** Step [i]'s work has finished; [detail], when given, takes its line's place. */
    fun done(i: Int, detail: CharSequence? = null) = later {
        if (i !in marks.indices) return@later 0L
        settle(i)
        detail?.let { details[i].text = it; details[i].visibility = View.VISIBLE }
        marks[i].show(StepMark.State.DONE)
    }

    /** Step [i] had nothing to do (a backup with no photos): a dash, not a tick. */
    fun skipped(i: Int, detail: CharSequence? = null) = later {
        if (i !in marks.indices) return@later 0L
        settle(i)
        detail?.let { details[i].text = it; details[i].visibility = View.VISIBLE }
        labels[i].setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
        marks[i].show(StepMark.State.SKIPPED)
    }

    /** Step [i] failed. The steps after it stay as they were: they never ran. */
    fun failed(i: Int) = later {
        if (i !in marks.indices) return@later 0L
        settle(i)
        labels[i].setTextColor(ContextCompat.getColor(activity, R.color.red_gave_text))
        marks[i].show(StepMark.State.FAILED)
    }

    /** The step that is running, failed: what a thrown error reaches, whichever step it came in. */
    fun failRunning() = later {
        val i = marks.indexOfFirst { it.state == StepMark.State.RUN }
        if (i < 0) return@later 0L
        settle(i)
        labels[i].setTextColor(ContextCompat.getColor(activity, R.color.red_gave_text))
        marks[i].show(StepMark.State.FAILED)
    }

    /**
     * The end, once every tick has arrived: [heading] in the title's place, [message] (what to know, or what a
     * failure means) above the one [action] button, which also closes the dialog.
     */
    fun finish(heading: CharSequence, message: CharSequence?, action: CharSequence, onAction: () -> Unit) = later {
        hint.visibility = View.GONE
        crossFade(title, heading)
        if (!message.isNullOrBlank()) {
            note.text = message
            note.visibility = View.VISIBLE
            MotionKit.riseIn(note, duration = 200)
            // What to know is brought into sight under the steps, once it is laid out (the button stays in sight
            // below them).
            val scroll = root.findViewById<androidx.core.widget.NestedScrollView>(R.id.stepsScroll)
            note.doOnNextLayout { scroll.smoothScrollTo(0, (scroll.getChildAt(0)?.height ?: 0) - scroll.height) }
        }
        button.text = action
        button.visibility = View.VISIBLE
        button.setOnClickListener {
            if (dialog.isShowing) dialog.dismiss()
            onAction()
        }
        MotionKit.fadeIn(button, delay = 60, duration = 200)
        if (Motion.enabled(activity)) 260L else 0L
    }

    /**
     * Closes it without an answer (the screen is going): at once on the main thread — posted, it was still on the
     * screen when Android closed the screen's windows, and Android logged it as leaked (3 Nov 2026, the review).
     */
    fun dismiss() {
        val close = {
            gone = true
            queue.clear()
            marks.forEach { it.stop() }
            if (dialog.isShowing) runCatching { dialog.dismiss() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) close() else main.post { close() }
    }

    /** Each label's own face (Urdu's, a language's), bold only while its step runs. */
    private val faces = labels.map { it.typeface }

    private fun settle(i: Int) {
        labels[i].setTextColor(ContextCompat.getColor(activity, R.color.ink))
        labels[i].setTypeface(faces[i], android.graphics.Typeface.NORMAL)
    }

    private fun crossFade(view: TextView, to: CharSequence) {
        if (!Motion.enabled(activity)) { view.text = to; return }
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(90).setInterpolator(MotionKit.STANDARD_ACCELERATE).withEndAction {
            view.text = to
            view.animate().alpha(1f).setDuration(150).setInterpolator(MotionKit.STANDARD).start()
        }.start()
    }

    /** Runs [what] on the main thread after every arrival before it; [what] says how long its own takes. */
    private fun later(what: () -> Long) {
        main.post {
            if (gone) return@post
            queue.addLast(what)
            if (!running) next()
        }
    }

    private fun next() {
        if (gone || activity.isFinishing || activity.isDestroyed || !dialog.isShowing) { queue.clear(); running = false; return }
        val what = queue.removeFirstOrNull() ?: run { running = false; return }
        running = true
        val ms = runCatching { what() }.getOrDefault(0L)
        if (ms <= 0L) next() else main.postDelayed({ next() }, ms)
    }

    companion object {
        /**
         * Shows the dialog with its steps waiting; the caller ticks them. Steps already finished before it
         * opened (the backup was read and checked before the question) are ticked with [done] right after.
         */
        fun show(activity: Activity, heading: CharSequence, sub: CharSequence?, steps: List<Step>): RestoreSteps {
            val inflater = LayoutInflater.from(activity)
            val root = inflater.inflate(R.layout.dialog_restore_steps, null)
            root.findViewById<TextView>(R.id.tvStepsTitle).text = heading
            root.findViewById<TextView>(R.id.tvStepsSub).apply {
                if (!sub.isNullOrBlank()) { text = sub; visibility = View.VISIBLE }
            }
            val list = root.findViewById<LinearLayout>(R.id.stepsList)
            val marks = mutableListOf<StepMark>()
            val labels = mutableListOf<TextView>()
            val details = mutableListOf<TextView>()
            steps.forEachIndexed { i, step ->
                val row = inflater.inflate(R.layout.item_restore_step, list, false)
                val mark = StepMark(activity).apply { hasLine = i < steps.lastIndex }
                row.findViewById<FrameLayout>(R.id.stepMarkSlot).addView(
                    mark, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
                val label = row.findViewById<TextView>(R.id.tvStepLabel).apply { text = step.label }
                val detail = row.findViewById<TextView>(R.id.tvStepDetail).apply {
                    if (!step.detail.isNullOrBlank()) { text = step.detail; visibility = View.VISIBLE }
                }
                list.addView(row)
                marks += mark; labels += label; details += detail
            }
            TextFit.relax(root)
            val dialog = MaterialAlertDialogBuilder(activity)
                .setView(root)
                .setCancelable(false)
                .create()
            dialog.setCanceledOnTouchOutside(false)
            val steps2 = RestoreSteps(activity, dialog, root, marks, labels, details)
            // Nothing turns on once it is closed, whether or not its rows were ever drawn; it goes with the screen,
            // and the screen lets go of it once it is closed.
            DialogLife.bind(activity, dialog, onDismiss = { marks.forEach { it.stop() } }, whenScreenGoes = { steps2.dismiss() })
            dialog.show()
            return steps2
        }

        /**
         * What a copy holds, as the restore question counted it: "1240 entries · 86 customers · 12 suppliers", its
         * figures in 0-9 like the money beside them. The one line the restore and its proof picture both say.
         */
        fun counts(res: android.content.res.Resources, entries: Int, customers: Int, suppliers: Int): String = listOfNotNull(
            Digits.quantity(res, R.plurals.bin_party_entries, entries, entries),
            if (customers > 0) Digits.quantity(res, R.plurals.customer_count, customers, customers) else null,
            if (suppliers > 0) Digits.quantity(res, R.plurals.supplier_count, suppliers, suppliers) else null
        ).joinToString(" · ")
    }
}
