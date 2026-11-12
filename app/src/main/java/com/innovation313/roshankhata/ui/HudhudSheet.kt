package com.innovation313.roshankhata.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.innovation313.roshankhata.PartyDetailActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.VoiceLanguage
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.data.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Hudhud's sheet (12 Nov 2026): the shop's assistant, opened from his bar on Home (and from Insights' microphone,
 * the one other place a question was asked by voice: one way in, NN/g). He stands at the top in the pose of what he
 * is doing — listening, thinking, speaking, glad, or concerned — beside his name; under him, what he understood, then
 * his answer on a card with the one screen it comes from; the questions he knows as chips; a box and a microphone.
 *
 * Rules kept here:
 *  - Every answer only READS ([HudhudBook]); a figure behind REPORTS is not shown to a cashier.
 *  - What was understood is always shown with the answer ([Hudhud], VoiceQuestion's rule).
 *  - Asked by voice, he answers aloud; typed or tapped, in writing, with "Hear again" (voice in, voice out: a typed
 *    question in a full shop is not answered across the counter). He speaks only through the phone's own offline
 *    voice ([HudhudVoice]); an amount is never said aloud while Home's balance is hidden (a customer may be standing
 *    at the counter); the speaker button turns his voice off for good.
 *  - With the phone's animations off he changes pose without fading, and never hops, bobs or breathes.
 *  - The microphone is the phone's own recogniser (RecognizerIntent, as voice entry): no microphone permission, and
 *    the app never holds the audio.
 */
object HudhudSheet {

    private var shown: WeakReference<BottomSheetDialog>? = null

    /** Whether his sheet is open now (HudhudOnDeviceTest asks). */
    fun isOpen(): Boolean = shown?.get()?.isShowing == true

    /** Hudhud's poses: one drawing each, the same size and the same feet, so one can fade into the next. */
    private enum class Pose(val drawing: Int) {
        IDLE(R.drawable.hudhud_idle),
        LISTENING(R.drawable.hudhud_listening),
        THINKING(R.drawable.hudhud_thinking),
        TALKING(R.drawable.hudhud_talking),
        HAPPY(R.drawable.hudhud_happy),
        ALERT(R.drawable.hudhud_alert)
    }

    /**
     * Opens the sheet, or brings back the one already open. [ask] is answered at once (the proofs, a chip from
     * elsewhere), [listen] starts the microphone, [typing] puts the cursor in the box, [today] answers with today's
     * summary (the morning greeting on Home); [speak] false keeps him silent for this sheet (the screen proofs).
     */
    fun open(
        activity: Activity,
        ask: String? = null,
        listen: Boolean = false,
        typing: Boolean = false,
        speak: Boolean = true,
        today: Boolean = false
    ): BottomSheetDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val host = activity as? ComponentActivity ?: return null
        shown?.get()?.let { if (it.isShowing) return it }
        // The page's colour with a rounded top is the sheet frame's own (its theme), so it also runs under the
        // navigation bar; the layout inflates in the sheet's theme, as Material inflates its frame.
        val sheet = BottomSheetDialog(activity, R.style.ThemeOverlay_RoshanKhata_HudhudSheet)
        val view = LayoutInflater.from(sheet.context).inflate(R.layout.sheet_hudhud, null)
        val ui = Ui(host, sheet, view, speak)
        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        DialogLife.bind(activity, sheet, onDismiss = { ui.close() })
        sheet.show()
        KeyboardFollow.dialog(sheet)
        shown = WeakReference(sheet)
        ui.start(ask, listen, typing, today)
        return sheet
    }

    private class Ui(
        val host: ComponentActivity,
        val sheet: BottomSheetDialog,
        val view: View,
        val speakAllowed: Boolean
    ) {
        val ctx: Context = host
        val dao = KhataDatabase.get(host).khataDao()
        val voice = HudhudVoice(host)
        val stage: View = view.findViewById(R.id.hudStage)
        val poseA: ImageView = view.findViewById(R.id.ivHudPoseA)
        val poseB: ImageView = view.findViewById(R.id.ivHudPoseB)
        val tvState: TextView = view.findViewById(R.id.tvHudState)
        val btnVoice: ImageButton = view.findViewById(R.id.btnHudVoice)
        val tvHeard: TextView = view.findViewById(R.id.tvHudHeard)
        val tvLabel: TextView = view.findViewById(R.id.tvHudLabel)
        val tvFigure: TextView = view.findViewById(R.id.tvHudFigure)
        val tvLines: TextView = view.findViewById(R.id.tvHudLines)
        val rows: LinearLayout = view.findViewById(R.id.hudRows)
        val tvNote: TextView = view.findViewById(R.id.tvHudNote)
        val buttons: View = view.findViewById(R.id.hudButtons)
        val btnOpen: View = view.findViewById(R.id.btnHudOpen)
        val btnReplay: View = view.findViewById(R.id.btnHudReplay)
        val chips: LinearLayout = view.findViewById(R.id.hudChips)
        val etAsk: EditText = view.findViewById(R.id.etHudAsk)
        val btnMic: View = view.findViewById(R.id.btnHudMic)
        val prefs = host.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var pose = Pose.IDLE
        var front: ImageView = poseA
        var last: HudhudBook.Answer? = null
        var job: Job? = null
        var thinkingLater: Runnable? = null
        var listenLauncher: ActivityResultLauncher<Intent>? = null
        var breath: ValueAnimator? = null
        var closed = false

        /** The book's customers and suppliers, read once for the sheet: names to match, balances to answer with. */
        val parties: Deferred<List<PartyWithBalance>> = host.lifecycleScope.async(Dispatchers.IO) {
            try { dao.partiesWithBalanceOnce() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        }

        val topics: List<Hudhud.Topic> by lazy { HudhudBook.topics(host) }

        var voiceOn: Boolean
            get() = prefs.getBoolean(KEY_VOICE, true)
            set(value) { prefs.edit().putBoolean(KEY_VOICE, value).apply() }

        fun start(ask: String?, listenNow: Boolean, typing: Boolean, today: Boolean) {
            // He faces into the sheet: the drawings look right, so they are turned in right-to-left.
            if (view.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                poseA.scaleX = -1f
                poseB.scaleX = -1f
            }
            showVoiceSwitch()
            btnVoice.setOnClickListener {
                voiceOn = !voiceOn
                showVoiceSwitch()
                if (!voiceOn) voice.stop()
                last?.let { showButtons(it) }
            }
            view.findViewById<View>(R.id.btnHudClose).setOnClickListener { sheet.dismiss() }
            btnReplay.setOnClickListener { last?.let { say(it) } }
            btnMic.setOnClickListener { listen() }
            etAsk.setOnEditorActionListener { _, action, event ->
                val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (action == EditorInfo.IME_ACTION_SEND || enter) { sendTyped(); true } else false
            }
            buildChips()
            show(HudhudBook.hello(ctx), speak = false)
            breathe()
            when {
                ask != null -> question(listOf(ask), spoken = false)
                today -> chip(ctx.getString(R.string.hudhud_chip_today), Hudhud.Ask.Today)
                listenNow -> listen()
                typing -> etAsk.postDelayed({
                    if (!closed) {
                        etAsk.requestFocus()
                        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.showSoftInput(etAsk, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, KEYBOARD_AFTER_MS)
            }
        }

        fun close() {
            closed = true
            job?.cancel()
            parties.cancel()
            thinkingLater?.let { view.removeCallbacks(it) }
            breath?.cancel()
            voice.shutdown()
            listenLauncher?.unregister()
            listenLauncher = null
            if (shown?.get() === sheet) shown = null
        }

        // ------------------------------------------------------------------------------------- asking

        private fun sendTyped() {
            val text = etAsk.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return
            etAsk.setText("")
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(etAsk.windowToken, 0)
            question(listOf(text), spoken = false)
        }

        private fun listen() {
            voice.stop()
            // Asked by voice, answered by voice: the engine wakes while the owner speaks, so the answer is not kept
            // waiting for it. A typed question or a chip never starts it.
            if (speakAllowed && voiceOn) voice.warmUp()
            val launcher = listenLauncher ?: host.activityResultRegistry.register(
                "hudhud-" + System.nanoTime(), ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (closed) return@register
                val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                if (heard.isEmpty()) {
                    setState(null)
                    showPose(Pose.IDLE)
                } else {
                    question(heard, spoken = true)
                }
            }.also { listenLauncher = it }
            // The recogniser in the app's language, as voice entry and Insights asked it.
            val tag = VoiceLanguage.preferred(Locale.getDefault().toLanguageTag()).firstOrNull()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                tag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.vq_prompt))
            }
            try {
                showPose(Pose.LISTENING)
                setState(R.string.hudhud_listening)
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                setState(null)
                show(HudhudBook.Answer(HudhudBook.Mood.CALM, lines = listOf(ctx.getString(R.string.vq_no_recognizer))), speak = false)
            }
        }

        /**
         * A question in words: the first of the recogniser's guesses Hudhud knows is the one answered. [spoken]: asked
         * by voice, so answered aloud (as assistants do: voice in, voice out); typed, it is answered in writing, with
         * "Hear again" to hear it.
         */
        private fun question(candidates: List<String>, spoken: Boolean) {
            // The recogniser's language, so a Persian or Indonesian amount is read its own way (voice entry's rule).
            val language = Locale.getDefault().toLanguageTag()
            answer(candidates.first(), spoken) { names ->
                Hudhud.understand(candidates, names, topics, language)
            }
        }

        /** A chip: its question is known already, and its own words are what was asked. */
        private fun chip(words: String, ask: Hudhud.Ask) {
            answer(words, spoken = false) { words to ask }
        }

        /** "Heard" for what the recogniser made of the owner's voice (he can see a word it got wrong); "You asked" else. */
        private fun asked(words: String, spoken: Boolean): String =
            ctx.getString(if (spoken) R.string.vq_heard else R.string.hudhud_you_asked, words)

        private fun answer(heardFirst: String, spoken: Boolean, understand: (List<Hudhud.Name>) -> Pair<String, Hudhud.Ask>) {
            job?.cancel()
            voice.stop()
            setState(null)
            tvHeard.visibility = View.VISIBLE
            tvHeard.text = asked(heardFirst, spoken)
            thinkingLater?.let { view.removeCallbacks(it) }
            // Thinking shows only when the answer takes long enough to be noticed (Nielsen's tenth of a second, and
            // then some): a quick answer goes straight to speaking, without a flicker of the thinking pose.
            val later = Runnable {
                if (!closed) {
                    showPose(Pose.THINKING)
                    setState(R.string.hudhud_thinking)
                }
            }
            thinkingLater = later
            view.postDelayed(later, THINK_AFTER_MS)
            job = host.lifecycleScope.launch {
                val result = try {
                    val known = parties.await()
                    val names = known.map { Hudhud.Name(it.id, it.name) }
                    val (heard, ask) = withContext(Dispatchers.Default) { understand(names) }
                    heard to withContext(Dispatchers.IO) { HudhudBook.answer(ctx, dao, ask, known) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    heardFirst to HudhudBook.failed(ctx)
                }
                view.removeCallbacks(later)
                if (closed) return@launch
                tvHeard.text = asked(result.first, spoken)
                show(result.second, speak = spoken)
            }
        }

        // ------------------------------------------------------------------------------------- the card

        private fun show(a: HudhudBook.Answer, speak: Boolean) {
            last = a
            tvLabel.textOrGone(a.label)
            tvFigure.textOrGone(a.figure)
            tvLines.textOrGone(a.lines.joinToString("\n").takeIf { it.isNotBlank() })
            rows.removeAllViews()
            rows.visibility = if (a.rows.isEmpty()) View.GONE else View.VISIBLE
            val inflater = LayoutInflater.from(ctx)
            for (r in a.rows) {
                val row = inflater.inflate(R.layout.item_hudhud_row, rows, false)
                row.findViewById<TextView>(R.id.tvHudRowName).text = r.name
                row.findViewById<TextView>(R.id.tvHudRowFigure).text = r.figure
                row.findViewById<TextView>(R.id.tvHudRowSub).textOrGone(r.sub)
                row.setOnClickListener {
                    val amount = r.amount
                    openParty(r.partyId, if (amount != null) HudhudBook.entryForm(amount, r.isGiven) else null)
                }
                TextFit.relax(row)
                rows.addView(row)
                TextFit.stackWhenLarge(row.findViewById(R.id.hudRowNameColumn), row.findViewById(R.id.tvHudRowFigure))
            }
            showButtons(a)
            // Asked aloud and not answered aloud: he says why (an amount, while Home's balance is hidden).
            val quiet = speak && speakAllowed && voiceOn && a.money && a.spoken != null && BalancePrivacy.isHidden(ctx)
            note(if (quiet) R.string.hudhud_quiet else null)
            if (speak && canSay(a)) say(a) else settle(a.mood)
        }

        private fun showButtons(a: HudhudBook.Answer) {
            val canOpen = a.open != null || a.openParty != null
            btnOpen.visibility = if (canOpen) View.VISIBLE else View.GONE
            (btnOpen as? TextView)?.setText(a.openLabel ?: R.string.vq_open)
            btnOpen.setOnClickListener { go(a) }
            btnReplay.visibility = if (canSay(a)) View.VISIBLE else View.GONE
            buttons.visibility = if (canOpen || canSay(a)) View.VISIBLE else View.GONE
        }

        /** He says an answer when it has words, his voice is on, and it is not an amount while balances are hidden. */
        private fun canSay(a: HudhudBook.Answer): Boolean =
            speakAllowed && voiceOn && a.spoken != null && !(a.money && BalancePrivacy.isHidden(ctx))

        private fun say(a: HudhudBook.Answer) {
            val words = a.spoken ?: return
            if (!canSay(a)) return
            // What he was saying ends first (Hear again while he speaks), then he speaks from the moment the answer is
            // his to say, not only once the engine starts: no stale "Listening…" while the phone's voice wakes up.
            voice.stop()
            showPose(Pose.TALKING)
            setState(R.string.hudhud_speaking)
            voice.speak(words, object : HudhudVoice.Listener {
                override fun onStart() {
                    if (closed) return
                    showPose(Pose.TALKING)
                    setState(R.string.hudhud_speaking)
                }

                override fun onWord() {
                    if (!closed) bob()
                }

                override fun onDone() {
                    if (!closed) settle(a.mood)
                }
            }, noVoice = {
                if (!closed) {
                    note(R.string.hudhud_no_voice)
                    settle(a.mood)
                }
            })
        }

        /** Where he rests after an answer: glad of good news a moment, concerned while there is something to see to. */
        private fun settle(mood: HudhudBook.Mood) {
            setState(null)
            when (mood) {
                HudhudBook.Mood.GOOD -> {
                    showPose(Pose.HAPPY)
                    hop()
                    view.postDelayed({ if (!closed && pose == Pose.HAPPY) showPose(Pose.IDLE) }, HAPPY_MS)
                }
                HudhudBook.Mood.CARE -> showPose(Pose.ALERT)
                HudhudBook.Mood.CALM -> showPose(Pose.IDLE)
            }
        }

        private fun go(a: HudhudBook.Answer) {
            val party = a.openParty
            val screen = a.open
            val intent = when {
                party != null -> Intent(host, PartyDetailActivity::class.java).putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party)
                // Asked from the very screen the answer comes from (Insights' microphone): the sheet closes onto it,
                // unless the answer opens it on days of its own.
                screen != null && screen == host.javaClass && a.openExtras == null -> null
                screen != null -> Intent(host, screen)
                else -> return
            }
            a.openExtras?.let { intent?.putExtras(it) }
            sheet.dismiss()
            intent?.let { host.startActivity(it) }
        }

        /** His page; [extras] the entry form filled in, when he was offered for an entry. */
        private fun openParty(id: Long, extras: android.os.Bundle? = null) {
            sheet.dismiss()
            val intent = Intent(host, PartyDetailActivity::class.java).putExtra(PartyDetailActivity.EXTRA_PARTY_ID, id)
            extras?.let { intent.putExtras(it) }
            host.startActivity(intent)
        }

        // ------------------------------------------------------------------------------------- chips

        /** The questions he knows, as chips: the figures only for those who may see them (REPORTS). */
        private fun buildChips() {
            chips.removeAllViews()
            val reports = Access.can(Access.Permission.REPORTS)
            val list = buildList<Pair<Int, Hudhud.Ask>> {
                // What needs the owner today first: his morning's question.
                add(R.string.hudhud_chip_today to Hudhud.Ask.Today)
                if (reports) add(R.string.vq_sales_today to Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
                // Who is in his usual paying days (the Payment Radar, where it is on).
                if (reports && RadarUi.on(ctx)) add(R.string.hudhud_chip_likely to Hudhud.Ask.WhenPays(emptyList()))
                if (reports) add(R.string.vq_who_owes_most to Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST))
                add(R.string.hudhud_chip_backup to Hudhud.Ask.BackupStatus)
                if (reports) {
                    add(R.string.hudhud_chip_drawer to Hudhud.Ask.Drawer)
                    add(R.string.vq_profit_month to Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH))
                    add(R.string.vq_to_get to Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
                    add(R.string.vq_to_give to Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE))
                    add(R.string.vq_not_paid to Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID))
                    add(R.string.vq_expiring to Hudhud.Ask.Figure(VoiceQuestion.Kind.EXPIRING))
                }
            }
            val density = ctx.resources.displayMetrics.density
            for ((label, ask) in list) {
                val words = ctx.getString(label)
                val chip = TextView(ctx).apply {
                    text = words
                    textSize = 14f
                    maxLines = 1
                    gravity = Gravity.CENTER
                    minHeight = (40 * density).toInt()
                    setPaddingRelative((14 * density).toInt(), (6 * density).toInt(), (14 * density).toInt(), (6 * density).toInt())
                    setTextColor(ContextCompat.getColor(ctx, R.color.brand_green_text))
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_hudhud_chip)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { chip(words, ask) }
                }
                chips.addView(chip, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * density).toInt() })
            }
        }

        // ------------------------------------------------------------------------------------- Hudhud himself

        private fun showPose(p: Pose) {
            if (p == pose) return
            pose = p
            val back = if (front === poseA) poseB else poseA
            back.setImageResource(p.drawing)
            val fromFront = front
            front = back
            if (!Motion.enabled(ctx)) {
                back.alpha = 1f
                fromFront.alpha = 0f
                return
            }
            back.animate().cancel()
            fromFront.animate().cancel()
            back.alpha = 0f
            back.animate().alpha(1f).setDuration(POSE_MS).start()
            fromFront.animate().alpha(0f).setDuration(POSE_MS).start()
        }

        private fun setState(words: Int?) {
            tvState.text = if (words == null) "" else ctx.getString(words)
        }

        private fun note(words: Int?) {
            tvNote.textOrGone(words?.let { ctx.getString(it) })
        }

        private fun showVoiceSwitch() {
            val on = voiceOn
            btnVoice.setImageResource(if (on) R.drawable.ic_volume_on else R.drawable.ic_volume_off)
            btnVoice.contentDescription = ctx.getString(if (on) R.string.hudhud_voice_on else R.string.hudhud_voice_off)
        }

        /** Three slow breaths when the sheet opens, then still: alive without spending the battery. */
        private fun breathe() {
            if (!Motion.enabled(ctx)) return
            stage.post {
                if (closed) return@post
                stage.pivotY = stage.height.toFloat()
                breath = ObjectAnimator.ofFloat(stage, View.SCALE_Y, 1f, BREATH_SCALE).apply {
                    duration = BREATH_MS
                    repeatCount = BREATHS * 2 - 1
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }

        /** A small hop for good news — only his first few, then the glad pose alone (Motion.firstTimes). */
        private fun hop() {
            if (!Motion.enabled(ctx) || !Motion.firstTimes(ctx, HOP_KEY, HOPS)) return
            val up = -HOP_DP * ctx.resources.displayMetrics.density
            stage.animate().cancel()
            stage.animate().translationY(up).setDuration(160).withEndAction {
                stage.animate().translationY(0f).setDuration(220).start()
            }.start()
        }

        /** A nod with each word he says. */
        private fun bob() {
            if (!Motion.enabled(ctx)) return
            val up = -BOB_DP * ctx.resources.displayMetrics.density
            stage.animate().cancel()
            stage.animate().translationY(up).setDuration(90).withEndAction {
                stage.animate().translationY(0f).setDuration(110).start()
            }.start()
        }

        private fun TextView.textOrGone(words: CharSequence?) {
            if (words.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                text = words
                visibility = View.VISIBLE
            }
        }
    }

    private const val PREFS = "hudhud"
    private const val KEY_VOICE = "voice"
    private const val HOP_KEY = "hudhud_hop"
    private const val HOPS = 5
    private const val THINK_AFTER_MS = 300L
    private const val POSE_MS = 160L
    private const val HAPPY_MS = 1600L
    private const val KEYBOARD_AFTER_MS = 250L
    private const val BREATHS = 3
    private const val BREATH_MS = 1400L
    private const val BREATH_SCALE = 1.015f
    private const val HOP_DP = 8f
    private const val BOB_DP = 2f
}
