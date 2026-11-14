package com.innovation313.roshankhata.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.innovation313.roshankhata.PartyDetailActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import com.innovation313.roshankhata.data.HudhudChirp
import com.innovation313.roshankhata.data.HudhudGate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Hudhud's sheet (12 Nov 2026): the shop's assistant, opened from his bar on Home (and from Insights' microphone,
 * the one other place a question was asked by voice: one way in, NN/g). He stands at the top in the pose of what he
 * is doing — listening, thinking, speaking, glad, or concerned — beside his name; under him, what he understood, then
 * his answer on a card with the one screen it comes from; the questions he knows as chips; a box and a microphone.
 *
 * Rules kept here:
 *  - Every answer only READS ([HudhudBook]); a figure behind REPORTS is not shown to a cashier.
 *  - What was understood is always shown with the answer ([Hudhud], VoiceQuestion's rule).
 *  - With his voice on (the speaker at the top) he says every answer aloud, however it was asked (13 Nov 2026, the
 *    owner: "jab upar on hai to neeche bhi awaaz aani chahiye"), the whole card and not only its first line
 *    ([HudhudBook.speech]), and "Hear again" says it once more; off, he only writes it. A guess or a choice is written,
 *    not said ("jo doubtful hai us par chup rahe"). He speaks only through the phone's own offline voice
 *    ([HudhudVoice]: the app's language, else the nearest the phone has, in that language's own words), and only once
 *    the screen is his again: an answer ready the moment the voice-search box closed was said into its closing and
 *    lost ([sayWhenHere]). No amount is said aloud while Home's balance is hidden (a customer may be standing at the
 *    counter), nor a phone or account number.
 *  - A tap on Hudhud plays one of his acts ([HudhudActs]: he flies, beats his wings, dances, hops or turns), with a
 *    light touch of the phone's haptics.
 *  - With the phone's animations off he changes pose without fading, and never hops, bobs or breathes.
 *  - He listens himself ([HudhudEars], 13 Nov 2026: no voice-search box) once the microphone is allowed — on the
 *    phone itself when it has the language; without the permission, the phone's voice-search box as before
 *    (RecognizerIntent, as voice entry). Nothing is recorded or kept; the words come back as text.
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
     * [intro]: opened from his face — he introduces himself aloud each time (13 Nov 2026, the owner).
     */
    fun open(
        activity: Activity,
        ask: String? = null,
        listen: Boolean = false,
        typing: Boolean = false,
        speak: Boolean = true,
        today: Boolean = false,
        intro: Boolean = false
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
        ui.start(ask, listen, typing, today, intro)
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
        val scroll: NestedScrollView = view.findViewById(R.id.hudScroll)
        val tvHeard: TextView = view.findViewById(R.id.tvHudHeard)
        val tvTook: TextView = view.findViewById(R.id.tvHudTook)
        val tvLabel: TextView = view.findViewById(R.id.tvHudLabel)
        val tvFigure: TextView = view.findViewById(R.id.tvHudFigure)
        val tvLines: TextView = view.findViewById(R.id.tvHudLines)
        val rows: LinearLayout = view.findViewById(R.id.hudRows)
        val tvNote: TextView = view.findViewById(R.id.tvHudNote)
        val buttons: View = view.findViewById(R.id.hudButtons)
        val btnOpen: View = view.findViewById(R.id.btnHudOpen)
        val btnReplay: View = view.findViewById(R.id.btnHudReplay)
        val checkRow: View = view.findViewById(R.id.hudCheckRow)
        val tvCheck: TextView = view.findViewById(R.id.tvHudCheck)
        val btnRight: View = view.findViewById(R.id.btnHudRight)
        val btnWrong: View = view.findViewById(R.id.btnHudWrong)
        val chips: LinearLayout = view.findViewById(R.id.hudChips)
        val etAsk: EditText = view.findViewById(R.id.etHudAsk)
        val btnMic: ImageButton = view.findViewById(R.id.btnHudMic)
        val btnSettings: ImageButton = view.findViewById(R.id.btnHudSettings)
        val sky: FrameLayout = view.findViewById(R.id.hudSky)
        val chipsScroll: View = view.findViewById(R.id.hudChipsScroll)
        /** The two tabs under the card: the shop's questions, and the quiet Personal one (his own story, matters of faith). */
        val hudTabs: View = view.findViewById(R.id.hudTabs)
        val tabAsk: TextView = view.findViewById(R.id.tabHudAsk)
        val tabPersonal: TextView = view.findViewById(R.id.tabHudPersonal)
        /** Verses under his words (Tawheed, hamd, risalat): shown, never said. */
        val verses: LinearLayout = view.findViewById(R.id.hudVerses)
        val tvVerseNote: TextView = view.findViewById(R.id.tvHudVerseNote)
        val prefs = host.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var pose = Pose.IDLE
        /** The card he opened with: while it is on show, the chips are the day's own and his usual ones. */
        var helloCard: HudhudBook.Answer? = null
        var front: ImageView = poseA
        var last: HudhudBook.Answer? = null
        var job: Job? = null
        var thinkingLater: Runnable? = null
        var listenLauncher: ActivityResultLauncher<Intent>? = null
        var micAsk: ActivityResultLauncher<String>? = null
        var speakJob: Job? = null
        var voiceJob: Job? = null
        /**
         * His acts, in the open sky at the top of the sheet and nowhere else (14 Nov 2026, the owner: he never leaves the
         * sheet): off in the pose the stage shows, back in the one it shows then, on a perch.
         */
        val acts = HudhudActs(
            stage, listOf(poseA, poseB), { pose.drawing }, sky,
            listOf(view.findViewById(R.id.hudPerchA), view.findViewById(R.id.hudPerchB), view.findViewById(R.id.hudPerchC)),
            view.findViewById(R.id.hudShadow)
        )

        /**
         * A conversation (14 Nov 2026, the owner: "baar baar tap nahi, guftagu ho"): the microphone, tapped once with his
         * voice on, goes on listening by itself after each spoken answer, until the owner says enough, is silent, types,
         * stops it, or leaves; the talk before is carried ([before]), so "uska number?" keeps its sense.
         */
        var conversing = false
        var turns = 0
        var listenAgain: Runnable? = null

        /** What the round button does now: the microphone, a stop while he listens, send once something is typed. */
        var action = Action.MIC
        var compact = false
        var keyboardWatch: ViewTreeObserver.OnGlobalLayoutListener? = null
        /** The sheet grows and shrinks to each answer smoothly (13 Nov 2026, the owner's video: it dropped in one frame). */
        var ease: SheetEase? = null
        var breath: ValueAnimator? = null
        var closed = false

        /** His own ears: the words as they come, the voice's loudness on the microphone, and why nothing was heard. */
        val ears = HudhudEars(host, object : HudhudEars.Callbacks {
            override fun onListening() {
                if (closed) return
                setState(R.string.hudhud_listening)
            }

            override fun onPartial(words: String) {
                if (closed) return
                tvHeard.visibility = View.VISIBLE
                tvHeard.text = asked(words, spoken = true)
            }

            override fun onLevel(level: Float) {
                if (closed || !Motion.enabled(ctx)) return
                val scale = 1f + MIC_GROW * level
                btnMic.scaleX = scale
                btnMic.scaleY = scale
                attend(level)
            }

            override fun onHeard(guesses: List<String>) {
                if (closed) return
                micAtRest()
                question(guesses, spoken = true)
            }

            override fun onTrouble(trouble: HudhudEars.Trouble) {
                if (closed) return
                micAtRest()
                // Silence after an answer ends the conversation without a word: he waits, he does not nag (the owner).
                if (conversing && turns > 0 && trouble == HudhudEars.Trouble.NOTHING_HEARD) {
                    endConversation()
                    setState(null)
                    tvHeard.visibility = View.GONE
                    showPose(Pose.IDLE)
                    note(R.string.hudhud_conversation_wait)
                    return
                }
                endConversation()
                heardNothing(trouble)
            }
        })

        /** The book's customers and suppliers, read once for the sheet: names to match, balances to answer with. */
        val parties: Deferred<List<PartyWithBalance>> = host.lifecycleScope.async(Dispatchers.IO) {
            try { dao.partiesWithBalanceOnce() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        }

        val topics: List<Hudhud.Topic> by lazy { HudhudBook.topics(host) }

        /** The shop's products by name, read once for the sheet: "Urea ka rate" is the product's (13 Nov 2026). */
        val goods: Deferred<List<Hudhud.Name>> = host.lifecycleScope.async(Dispatchers.IO) {
            try { dao.productsOnce().map { Hudhud.Name(it.id, it.name) } } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        }

        /** What the last question was taken for, so "uska number?" or "aur kal?" carry on from it. */
        var before: Hudhud.Ask? = null

        /** The day's own questions (what the bell has), first among the chips until something is asked. */
        var starters: List<Hudhud.Ask> = emptyList()

        /**
         * The Personal tab (14 Nov 2026, the owner: "personal tab ho, highlight na ho"): his own story and the questions
         * of faith keep a quiet tab of their own, so the shop's sheet stays the shop's. It follows the answer on show — a
         * matter of faith opens it, any other question brings the shop's tab back — and either can be chosen by hand.
         */
        var personal = false

        /** What followed the card on show (the Ask tab's chips), kept for the tab to return to. */
        var nextChips: List<Hudhud.Ask> = emptyList()

        /** What the card on show was asked as: the Personal tab does not offer it again under itself. */
        var shownAsk: Hudhud.Ask? = null

        var voiceOn: Boolean
            get() = prefs.getBoolean(KEY_VOICE, true)
            set(value) { prefs.edit().putBoolean(KEY_VOICE, value).apply() }

        fun start(ask: String?, listenNow: Boolean, typing: Boolean, today: Boolean, intro: Boolean) {
            // He faces into the sheet: the drawings look right, so they are turned in right-to-left.
            if (view.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                poseA.scaleX = -1f
                poseB.scaleX = -1f
            }
            showVoiceSwitch()
            btnVoice.setOnClickListener {
                voiceOn = !voiceOn
                showVoiceSwitch()
                if (!voiceOn) {
                    speakJob?.cancel()
                    voiceJob?.cancel()
                    voice.stop()
                    // Turned off before he began: he does not stay turned to speak.
                    if (pose == Pose.TALKING) last?.let { settle(it.mood) }
                }
                last?.let { showButtons(it) }
            }
            view.findViewById<View>(R.id.btnHudClose).setOnClickListener { sheet.dismiss() }
            stage.setOnClickListener { tapped() }
            ease = SheetEase.attach(view, scroll.getChildAt(0) ?: scroll)
            btnSettings.setOnClickListener { HudhudVoiceSheet.show(host) }
            btnReplay.setOnClickListener { last?.let { say(it) } }
            // One round button, three jobs: the microphone, a stop while he listens, send once something is typed.
            btnMic.setOnClickListener { if (action == Action.SEND) sendTyped() else listen() }
            // The sky shrinks to a low band while the keyboard is up, so what is typed and answered stays in sight.
            val watch = ViewTreeObserver.OnGlobalLayoutListener {
                if (!closed) {
                    val up = keyboardUp()
                    if (up != compact) applyCompact(up)
                }
            }
            keyboardWatch = watch
            view.viewTreeObserver.addOnGlobalLayoutListener(watch)
            etAsk.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (closed) return
                    // Typing takes the turn: a conversation ends and a microphone that is open is closed.
                    if (!s.isNullOrEmpty()) {
                        endConversation()
                        if (ears.listening) {
                            ears.cancel()
                            micAtRest()
                            setState(null)
                            tvHeard.visibility = View.GONE
                        }
                    }
                    syncAction()
                    if (s.isNullOrEmpty() || ears.listening || job?.isActive == true) return
                    if (pose != Pose.LISTENING && pose != Pose.TALKING) showPose(Pose.LISTENING)
                    etAsk.removeCallbacks(easeAfterTyping)
                    etAsk.postDelayed(easeAfterTyping, EASE_AFTER_TYPING_MS)
                }
            })
            etAsk.setOnEditorActionListener { _, action, event ->
                val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (action == EditorInfo.IME_ACTION_SEND || enter) { sendTyped(); true } else false
            }
            tabAsk.setOnClickListener { selectTab(toPersonal = false) }
            tabPersonal.setOnClickListener { selectTab(toPersonal = true) }
            styleTabs()
            showChips(emptyList())
            // The day's own questions first (what the bell has now), once they are read.
            host.lifecycleScope.launch {
                val day = try { withContext(Dispatchers.IO) { HudhudBook.suggested(ctx, dao) } } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                if (!closed && day.isNotEmpty()) {
                    starters = day
                    if (last === helloCard) showChips(emptyList())
                }
            }
            // His voice wakes with the sheet when it is on, so the first answer is not kept waiting for it.
            if (speakAllowed && voiceOn) voice.warmUp()
            // He greets aloud once a day, the first time he is opened just to talk (not for a question already asked).
            val greet = speakAllowed && voiceOn && ask == null && !listenNow && !today && HudhudPrefs.helloDue(ctx)
            if (greet) HudhudPrefs.helloSaid(ctx)
            if (intro && ask == null && !listenNow && !today) {
                // Tapped on his face: who he is and to ask, aloud every time his voice is on; the greeting the day's first.
                val card = HudhudBook.intro(ctx, greet)
                helloCard = card
                show(card, speak = speakAllowed && voiceOn)
                // Touched, he answers with his wings as he arrives (13 Nov 2026, the owner).
                stage.postDelayed({ if (!closed) acts.play(HudhudActs.Act.FLAP) }, ARRIVE_ACT_MS)
            } else {
                val card = HudhudBook.hello(ctx)
                helloCard = card
                show(card, speak = greet)
            }
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
            endConversation()
            keyboardWatch?.let { w -> view.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(w) }
            keyboardWatch = null
            acts.stop()
            ease?.stop()
            ease = null
            job?.cancel()
            speakJob?.cancel()
            voiceJob?.cancel()
            parties.cancel()
            thinkingLater?.let { view.removeCallbacks(it) }
            breath?.cancel()
            voice.shutdown()
            ears.destroy()
            listenLauncher?.unregister()
            listenLauncher = null
            micAsk?.unregister()
            micAsk = null
            if (shown?.get() === sheet) shown = null
        }

        // ------------------------------------------------------------------------------------- asking

        private fun sendTyped() {
            val text = etAsk.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return
            etAsk.setText("")
            endConversation()
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(etAsk.windowToken, 0)
            question(listOf(text), spoken = false)
        }

        /**
         * The microphone (13 Nov 2026): a second tap while he listens ends it and he makes out what was said. He
         * listens himself when the microphone is allowed; the first time, he says why he needs it and Android asks;
         * after a no, the phone's voice-search box as before — but never a box that cannot work without internet.
         */
        private fun listen() {
            if (ears.listening) {
                // A tap while he listens is "that is all": the conversation ends with it.
                endConversation()
                if (ears.open) {
                    // What was said so far is made out and answered.
                    ears.stop()
                } else {
                    // Stopped before the microphone opened: nothing was said, so nothing is answered.
                    ears.cancel()
                    micAtRest()
                    setState(null)
                    tvHeard.visibility = View.GONE
                    showPose(Pose.IDLE)
                }
                return
            }
            voice.stop()
            speakJob?.cancel()
            if (speakAllowed && voiceOn) voice.warmUp()
            // The microphone tapped with his voice on starts a conversation: he answers aloud and listens again by himself.
            // Only through his own ears: the phone's voice-search box (no permission, or no internet) is a single question.
            conversing = speakAllowed && voiceOn && HudhudEars.canHear(ctx) && HudhudEars.micGranted(ctx)
            turns = 0
            when {
                !HudhudEars.canHear(ctx) -> googleBox()
                HudhudEars.micGranted(ctx) -> hearMyself()
                HudhudPrefs.micDenied(ctx) ->
                    if (HudhudEars.online(ctx)) googleBox() else heardNothing(HudhudEars.Trouble.OFFLINE)
                else -> askForMic()
            }
        }

        /** He says why he needs the microphone, then Android asks; a yes and he listens, a no and the box opens. */
        private fun askForMic() {
            showCard(lines = listOf(ctx.getString(R.string.hudhud_mic_why)))
            val launcher = micAsk ?: host.activityResultRegistry.register(
                "hudhud-mic-" + System.nanoTime(), ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (closed) return@register
                if (granted) {
                    HudhudPrefs.setMicDenied(ctx, false)
                    conversing = speakAllowed && voiceOn
                    turns = 0
                    hearMyself()
                } else {
                    HudhudPrefs.setMicDenied(ctx, true)
                    if (HudhudEars.online(ctx)) googleBox() else heardNothing(HudhudEars.Trouble.OFFLINE)
                }
            }.also { micAsk = it }
            try {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                googleBox()
            }
        }

        /** He listens himself: his listening pose, the words as they come, the microphone a stop button. */
        private fun hearMyself() {
            job?.cancel()
            showPose(Pose.LISTENING)
            setState(R.string.hudhud_listening)
            tvHeard.visibility = View.VISIBLE
            tvHeard.text = asked("\u2026", spoken = true)
            // His little "I am listening" call first when bird calls are on (the speaker switch is the master of every
            // sound he makes): the microphone opens when it has ended, so it does not hear him.
            val lead = if (voiceOn) HudhudChirpPlayer.play(ctx, HudhudChirp.Kind.READY) else 0
            ears.start(Locale.getDefault().toLanguageTag(), if (lead > 0) lead + READY_GAP_MS else 0L)
            syncAction(stopping = true)
        }

        /** The microphone looks like itself again. */
        private fun micAtRest() {
            btnMic.animate().cancel()
            btnMic.scaleX = 1f
            btnMic.scaleY = 1f
            syncAction(stopping = false)
            heardLevel = 0f
            if (!acts.busy) stage.animate().rotation(0f).setDuration(SIT_BACK_MS).start()
        }

        /** The voice he is hearing, smoothed so he leans with it and does not tremble with every sound. */
        private var heardLevel = 0f

        /**
         * He listens to the voice (14 Nov 2026, the owner: "jab hum baat karein to us ki movement hum se related ho"):
         * he leans in, from his feet, towards the microphone as the voice rises and settles back as it falls; with the
         * phone's animations off he stays still. Never while one of his acts has the stage.
         */
        private fun attend(level: Float) {
            if (acts.busy) return
            heardLevel = heardLevel * 0.7f + level * 0.3f
            if (stage.width > 0) {
                stage.pivotX = stage.width / 2f
                stage.pivotY = stage.height.toFloat()
            }
            val forward = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
            stage.rotation = forward * LEAN_DEG * heardLevel
        }

        /** While the owner types, he turns to listen; a little after the typing stops he is at ease again. */
        private val easeAfterTyping = Runnable {
            if (!closed && pose == Pose.LISTENING && !ears.listening && job?.isActive != true) last?.let { settle(it.mood) } ?: showPose(Pose.IDLE)
        }

        /**
         * Why nothing was heard, said on the card (and aloud, short, with his voice on: asked by voice). Without
         * internet he points at typing and the chips, and at the language's own pack when the phone can download it.
         * Anything he cannot explain falls back to the voice-search box while there is internet.
         */
        private fun heardNothing(trouble: HudhudEars.Trouble) {
            setState(null)
            when (trouble) {
                HudhudEars.Trouble.NOTHING_HEARD -> spokenCard(R.string.hudhud_heard_nothing)
                HudhudEars.Trouble.OFFLINE -> spokenCard(R.string.hudhud_offline)
                HudhudEars.Trouble.NOT_OFFLINE_HERE -> spokenCard(R.string.hudhud_offline_none)
                HudhudEars.Trouble.BUSY -> spokenCard(R.string.hudhud_mic_busy)
                HudhudEars.Trouble.PACK_MISSING -> show(
                    HudhudBook.Answer(
                        HudhudBook.Mood.CALM,
                        lines = listOf(ctx.getString(R.string.hudhud_offline), ctx.getString(R.string.hudhud_offline_pack)),
                        openLabel = R.string.hudhud_pack_download,
                        spoken = { it.getString(R.string.hudhud_offline) },
                        doIt = { downloadPack() }
                    ),
                    speak = true
                )
                HudhudEars.Trouble.MIC, HudhudEars.Trouble.OTHER ->
                    if (HudhudEars.online(ctx)) googleBox() else spokenCard(R.string.hudhud_offline)
            }
        }

        private fun spokenCard(words: Int) {
            tvHeard.visibility = View.GONE
            show(HudhudBook.Answer(HudhudBook.Mood.CALM, lines = listOf(ctx.getString(words)), spoken = { it.getString(words) }), speak = true)
        }

        /** The phone downloads its own pack for the language; he says so, and to tap the microphone once it is done. */
        private fun downloadPack() {
            val asked = ears.downloadPack(Locale.getDefault().toLanguageTag())
            showCard(lines = listOf(ctx.getString(if (asked) R.string.hudhud_pack_started else R.string.hudhud_offline_none)))
        }

        /** A plain card of words, no button, nothing said. */
        private fun showCard(lines: List<String>) {
            show(HudhudBook.Answer(HudhudBook.Mood.CALM, lines = lines), speak = false)
        }

        /** The phone's voice-search box (RecognizerIntent), as voice entry asks it: without the microphone permission. */
        private fun googleBox() {
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
                // Without internet the box uses the phone's own pack for the language, when it has one.
                if (!HudhudEars.online(ctx)) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
         * by voice ("Heard:"), else typed ("You asked:"); either way he says the answer when his voice is on.
         */
        private fun question(candidates: List<String>, spoken: Boolean) {
            // The recogniser's language, so a Persian or Indonesian amount is read its own way (voice entry's rule).
            val language = Locale.getDefault().toLanguageTag()
            answer(candidates.first(), spoken) { names, products ->
                Hudhud.understand(candidates, names, topics, language, products, before)
            }
        }

        /** A chip: its question is known already, and its own words are what was asked. */
        private fun chip(words: String, ask: Hudhud.Ask) {
            endConversation()
            answer(words, spoken = false) { _, _ -> words to ask }
        }

        /**
         * "Heard" for what the recogniser made of the owner's voice (he can see a word it got wrong); "You asked" else.
         * The words are isolated in the sentence, as every name or typed word dropped into one is (Format.isolate): a
         * question that begins with a figure ("500 Ali ko diye") keeps the figure in front in an Urdu line.
         */
        private fun asked(words: String, spoken: Boolean): String =
            ctx.getString(if (spoken) R.string.vq_heard else R.string.hudhud_you_asked, Format.isolate(words))

        private fun answer(
            heardFirst: String, spoken: Boolean, understand: (List<Hudhud.Name>, List<Hudhud.Name>) -> Pair<String, Hudhud.Ask>
        ) {
            job?.cancel()
            speakJob?.cancel()
            if (ears.listening) {
                ears.cancel()
                micAtRest()
            }
            voice.stop()
            setState(null)
            tvHeard.visibility = View.VISIBLE
            tvHeard.text = asked(heardFirst, spoken)
            // A new question is read from its top: the last answer may have been scrolled to its end.
            scroll.scrollTo(0, 0)
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
                    val products = goods.await()
                    val (heard, ask) = withContext(Dispatchers.Default) { understand(names, products) }
                    // "Phir se batao", "samajh nahi aya": the last answer again, as it was (14 Nov 2026).
                    val again = (ask as? Hudhud.Ask.Voice)?.change == Hudhud.VoiceChange.AGAIN
                    val answer = lastReal.takeIf { again } ?: withContext(Dispatchers.IO) { HudhudBook.answer(ctx, dao, ask, known) }
                    val next = withContext(Dispatchers.Default) { chipsFor(heard, ask, answer, names, products) }
                    Answered(heard, ask, answer, next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Answered(heardFirst, null, HudhudBook.failed(ctx), emptyList())
                }
                view.removeCallbacks(later)
                if (closed) return@launch
                tvHeard.text = asked(result.heard, spoken)
                // "Bas", "chup", "khuda hafiz": the talk is over — he answers and does not listen again.
                val stops = (result.ask as? Hudhud.Ask.Voice)?.change.let {
                    it == Hudhud.VoiceChange.QUIET || it == Hudhud.VoiceChange.OFF
                } || (result.ask as? Hudhud.Ask.Chat)?.talk == Hudhud.Talk.BYE
                if (stops) endConversation()
                show(result.answer, speak = true)
                offerCheck(result.heard, result.ask, result.answer)
                // What the talk is about now, so "uska number?" or "aur kal?" carry on: a guess counts as what it was
                // taken for; a list of names offered ("whose?") keeps the one before. A matter of faith or his own story
                // is a detour: the shop's talk goes on from where it was, not from it.
                val taken = (result.ask as? Hudhud.Ask.Guessed)?.ask ?: result.ask
                if (!result.answer.reverent && taken != null && taken !is Hudhud.Ask.DidYouMean && taken != Hudhud.Ask.Unknown &&
                    taken !is Hudhud.Ask.NeedName && taken !is Hudhud.Ask.Voice && taken !is Hudhud.Ask.NeedGoods &&
                    taken !is Hudhud.Ask.CallMe
                ) {
                    before = taken
                }
                // The tab follows the answer: Personal for a matter of faith or his story, the shop's for the rest.
                shownAsk = taken
                nextChips = result.next
                personal = result.answer.reverent
                styleTabs()
                showChips(result.next)
            }
        }

        /**
         * The owner's check of a test build (14 Nov 2026, [HudhudCheck]): with it on, the answer is written down on this
         * phone and he marks it right or wrong under the card; off, the row is never shown and nothing is written.
         */
        private fun offerCheck(heard: String, ask: Hudhud.Ask?, answer: HudhudBook.Answer) {
            checkRow.visibility = View.GONE
            // Matters of faith and his own story are not written into the check log: personal things stay personal.
            if (answer.reverent) return
            if (!HudhudCheck.isOn(ctx)) return
            val language = ctx.resources.configuration.locales[0].toLanguageTag()
            host.lifecycleScope.launch {
                val row = withContext(Dispatchers.IO) {
                    runCatching { HudhudCheck.record(ctx, heard, HudhudCheck.describe(ask), HudhudCheck.heading(answer), language) }
                        .getOrNull()
                } ?: return@launch
                if (closed) return@launch
                tvCheck.setText(R.string.hudhud_check_ask)
                btnRight.isEnabled = true
                btnWrong.isEnabled = true
                btnRight.setOnClickListener { markCheck(row, right = true) }
                btnWrong.setOnClickListener { markCheck(row, right = false) }
                checkRow.visibility = View.VISIBLE
            }
        }

        private fun markCheck(row: Int, right: Boolean) {
            btnRight.isEnabled = false
            btnWrong.isEnabled = false
            tvCheck.setText(if (right) R.string.hudhud_check_marked_right else R.string.hudhud_check_marked_wrong)
            host.lifecycleScope.launch(Dispatchers.IO) { runCatching { HudhudCheck.mark(ctx, row, right) } }
        }

        /** A question answered: what was heard, what it was taken for, the answer, and the chips that follow it. */
        private class Answered(val heard: String, val ask: Hudhud.Ask?, val answer: HudhudBook.Answer, val next: List<Hudhud.Ask>)

        /**
         * The chips under an answer (13 Nov 2026, the owner: "kehne wala jo keyword de raha hai, us hisaab se suggestion
         * de"): for a guess, the other meanings its words could have, then what follows the guess; for words he could
         * not place, the nearest meanings he knows; else what follows the answer.
         */
        private fun chipsFor(
            heard: String, ask: Hudhud.Ask?, answer: HudhudBook.Answer, names: List<Hudhud.Name>, products: List<Hudhud.Name>
        ): List<Hudhud.Ask> {
            val language = Locale.getDefault().toLanguageTag()
            return when {
                ask == null -> emptyList()
                ask == Hudhud.Ask.Unknown -> Hudhud.suggest(heard, names, topics, language, products)
                answer.tookAs != null -> answer.options + Hudhud.suggest(heard, names, topics, language, products, skip = ask) +
                    HudhudBook.next(ctx, ask)
                else -> answer.options + HudhudBook.next(ctx, ask)
            }
        }

        // ------------------------------------------------------------------------------------- the card

        /** The last answer that was an answer (not a change to his voice): what "phir se batao" shows again. */
        private var lastReal: HudhudBook.Answer? = null

        /**
         * A change to how he speaks, asked of him (14 Nov 2026, the owner's list: "ahista bolo", "chup ho jao", "awaaz
         * band karo"): made at once, kept on this phone; a new pace is heard in the very line that says it.
         */
        private fun change(c: Hudhud.VoiceChange) {
            when (c) {
                Hudhud.VoiceChange.SLOWER -> HudhudPrefs.setPace(ctx, HudhudPrefs.pace(ctx) - HudhudPrefs.PACE_STEP)
                Hudhud.VoiceChange.FASTER -> HudhudPrefs.setPace(ctx, HudhudPrefs.pace(ctx) + HudhudPrefs.PACE_STEP)
                Hudhud.VoiceChange.QUIET -> { speakJob?.cancel(); voice.stop() }
                Hudhud.VoiceChange.OFF -> { voiceOn = false; showVoiceSwitch(); speakJob?.cancel(); voice.stop() }
                Hudhud.VoiceChange.ON -> { voiceOn = true; showVoiceSwitch(); if (speakAllowed) voice.warmUp() }
                Hudhud.VoiceChange.AGAIN, Hudhud.VoiceChange.VOLUME -> {}
            }
        }

        private fun show(a: HudhudBook.Answer, speak: Boolean) {
            if (a.voice == null) lastReal = a
            // A check row belongs to the answer it was offered under; any other card shows none (offerCheck brings it back).
            checkRow.visibility = View.GONE
            last = a
            tvTook.textOrGone(a.tookAs)
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
                    val screen = r.open
                    val amount = r.amount
                    val rowAsk = r.ask
                    when {
                        rowAsk != null -> chip(r.name, rowAsk)
                        screen != null -> {
                            sheet.dismiss()
                            host.startActivity(Intent(host, screen))
                        }
                        r.partyId > 0 -> openParty(r.partyId, if (amount != null) HudhudBook.entryForm(amount, r.isGiven) else null)
                    }
                }
                TextFit.relax(row)
                rows.addView(row)
                TextFit.stackWhenLarge(row.findViewById(R.id.hudRowNameColumn), row.findViewById(R.id.tvHudRowFigure))
            }
            // Verses (Tawheed, hamd, the Prophet's risalat): the Arabic as quotations under his words, never said.
            verses.removeAllViews()
            verses.visibility = if (a.verses.isEmpty()) View.GONE else View.VISIBLE
            for (v in a.verses) {
                val item = inflater.inflate(R.layout.item_hudhud_verse, verses, false)
                item.findViewById<TextView>(R.id.tvVerseArabic).text = v.arabic
                item.findViewById<TextView>(R.id.tvVerseRef).text = if (v.ref.isBlank()) v.surah else v.surah + " \u00B7 " + v.ref
                verses.addView(item)
            }
            tvVerseNote.textOrGone(a.verseNote)
            showButtons(a)
            note(null)
            a.voice?.let { change(it) }
            // One of his acts, asked for ("urr ke dikhao"): it plays as the card arrives.
            a.act?.let { move -> stage.post { if (!closed && last === a) acts.play(HudhudActs.Act.of(move)) } }
            if (speak && canSay(a)) {
                // He turns to speak as the answer arrives; the words follow once the screen is his ([sayWhenHere]).
                showPose(Pose.TALKING)
                sayWhenHere(a)
            } else {
                settle(a.mood)
            }
        }

        /**
         * Says [a] once the screen is his again (13 Nov 2026, the owner's report: with his voice on, an answer to a
         * spoken question was not said until "Hear again"). The voice-search box hands its words back before this
         * screen is resumed and while the box still holds the microphone; a quick answer (a choice of names) was said
         * into that and lost, a slow one (a sum) came late enough to be heard. So: resumed, the sheet's window
         * focused (at most a short wait), a breath, and then the answer — if it is still the one on the card. An answer
         * whose screen came back only much later (the owner left the app meanwhile) stays on the card unsaid.
         */
        private fun sayWhenHere(a: HudhudBook.Answer) {
            speakJob?.cancel()
            val ready = SystemClock.uptimeMillis()
            speakJob = host.lifecycleScope.launch {
                host.lifecycle.withResumed { }
                withTimeoutOrNull(FOCUS_WAIT_MS) { windowFocus() }
                delay(SETTLE_MS)
                if (closed || last !== a) return@launch
                if (SystemClock.uptimeMillis() - ready > STALE_MS) settle(a.mood) else say(a)
            }
        }

        /** Returns once the sheet's window has the focus (the voice-search box has gone from in front of it). */
        private suspend fun windowFocus() {
            val decor = sheet.window?.decorView ?: return
            if (decor.hasWindowFocus()) return
            suspendCancellableCoroutine<Unit> { wait ->
                val watch = object : ViewTreeObserver.OnWindowFocusChangeListener {
                    override fun onWindowFocusChanged(hasFocus: Boolean) {
                        if (!hasFocus) return
                        decor.post { decor.viewTreeObserver.removeOnWindowFocusChangeListener(this) }
                        if (wait.isActive) wait.resume(Unit)
                    }
                }
                decor.viewTreeObserver.addOnWindowFocusChangeListener(watch)
                wait.invokeOnCancellation { decor.post { decor.viewTreeObserver.removeOnWindowFocusChangeListener(watch) } }
            }
        }

        private fun showButtons(a: HudhudBook.Answer) {
            val canOpen = a.open != null || a.openParty != null || a.doIt != null || a.voiceSettings || a.automations
            btnOpen.visibility = if (canOpen) View.VISIBLE else View.GONE
            (btnOpen as? TextView)?.setText(a.openLabel ?: R.string.vq_open)
            btnOpen.setOnClickListener {
                when {
                    a.voiceSettings -> HudhudVoiceSheet.show(host)
                    a.automations -> AutomationsDialog.show(host)
                    a.doIt != null -> a.doIt.invoke()
                    else -> go(a)
                }
            }
            btnReplay.visibility = if (canSay(a)) View.VISIBLE else View.GONE
            buttons.visibility = if (canOpen || canSay(a)) View.VISIBLE else View.GONE
        }

        /** He says an answer when his voice is on and it is no guess or choice ([HudhudBook.Answer.quiet]). */
        private fun canSay(a: HudhudBook.Answer): Boolean = speakAllowed && voiceOn && !a.quiet

        /** Whether the voice says the app's own words (else the card is asked again in the voice's language). */
        private fun sameWords(plan: HudhudVoice.Plan): Boolean {
            val app = ctx.resources.configuration.locales[0]
            val said = plan.words.resources.configuration.locales[0]
            return app.language == said.language && app.script == said.script
        }

        /**
         * Says [a] in full ([HudhudBook.speech]): with the phone's voice for the app's language, else the nearest one it
         * has, the card then asked again in that language's own words (and in the Urdu words for Roman Urdu, which an
         * Urdu voice reads). What he leaves out, and a voice of another language, he says once on the card.
         */
        private fun say(a: HudhudBook.Answer) {
            if (!canSay(a)) return
            // What he was saying ends first (Hear again while he speaks), then he speaks from the moment the answer is
            // his to say, not only once the engine starts: no stale "Listening…" while the phone's voice wakes up.
            voiceJob?.cancel()
            voice.stop()
            showPose(Pose.TALKING)
            setState(R.string.hudhud_speaking)
            voiceJob = host.lifecycleScope.launch {
                val plan = voice.plan()
                if (closed || last !== a) return@launch
                if (plan == null) {
                    note(R.string.hudhud_no_voice)
                    settle(a.mood)
                    return@launch
                }
                val ask = a.ask
                val card = if (sameWords(plan) || ask == null || a.spoken != null) a else try {
                    withContext(Dispatchers.IO) { HudhudBook.answer(plan.words, dao, ask, parties.await()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (closed || last !== a) return@launch
                val hidden = BalancePrivacy.isHidden(ctx)
                val speech = card?.let { HudhudBook.speech(plan.words, it, hidden) }
                when {
                    plan.fallback -> fallbackNote(plan)
                    speech == null && a.money && hidden -> note(R.string.hudhud_quiet)
                    speech?.leftOutMoney == true -> note(R.string.hudhud_quiet)
                }
                if (speech == null) {
                    settle(a.mood)
                    return@launch
                }
                speakNow(a, speech.text, plan)
            }
        }

        /** The phone has no voice for the app's language: which one he speaks instead, and where to get the voice. */
        private fun fallbackNote(plan: HudhudVoice.Plan) {
            val app = ctx.resources.configuration.locales[0]
            val wanted = Locale(plan.wanted).getDisplayLanguage(app).ifBlank { plan.wanted }
            val spoken = Locale(plan.language).getDisplayLanguage(app).ifBlank { plan.language }
            tvNote.textOrGone(ctx.getString(R.string.hudhud_voice_fallback, wanted, spoken))
        }

        private fun speakNow(a: HudhudBook.Answer, text: String, plan: HudhudVoice.Plan) {
            // A bright trill before good news, the hoopoe's own call before the rest (only when bird calls are on) — and
            // none before a matter of faith or his own story, which he tells in his plain voice (14 Nov 2026).
            val call = when {
                a.reverent -> null
                a.mood == HudhudBook.Mood.GOOD -> HudhudChirp.Kind.GLAD
                else -> HudhudChirp.Kind.HOOP
            }
            voice.speak(text, plan, chirp = call, listener = object : HudhudVoice.Listener {
                override fun onStart() {
                    if (closed) return
                    showPose(Pose.TALKING)
                    setState(R.string.hudhud_speaking)
                }

                // No nod with each word (14 Nov 2026, the owner: "bolte waqt hilta rehta hai"): while he speaks he sits steady
                // in his speaking drawing; only his breath moves.
                override fun onWord() {}

                override fun onDone() {
                    if (!closed) settle(a.mood)
                }

                override fun onFailed() {
                    if (closed) return
                    note(R.string.hudhud_voice_failed)
                    settle(a.mood)
                }
            })
        }

        /**
         * A tap on Hudhud (13 Nov 2026, the owner: "touch karne par… kabhi urre, kabhi pankh maare, kabhi alag alag
         * activities"): one of his acts, never the same twice in a row; with the phone's animations off, a light touch of
         * the haptics and his glad pose a moment.
         */
        private fun tapped() {
            if (acts.busy) return
            breath?.cancel()
            stage.scaleY = 1f
            if (!Motion.enabled(ctx)) {
                acts.tap()
                showPose(Pose.HAPPY)
                view.postDelayed({ if (!closed && pose == Pose.HAPPY) showPose(Pose.IDLE) }, HAPPY_MS)
                return
            }
            acts.play(acts.random())
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
            if (conversing) listenSoon()
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

        /** The questions he always knows, as chips: the figures only for those who may see them (REPORTS). */
        private fun usualChips(): List<Hudhud.Ask> {
            val reports = HudhudGate.reports()
            return buildList {
                // What he can tell first (13 Nov 2026): nobody has to guess what to ask him.
                add(Hudhud.Ask.Abilities)
                // What needs the owner today: his morning's question.
                add(Hudhud.Ask.Today)
                if (reports) add(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
                // Who is in his usual paying days (the Payment Radar, where it is on).
                if (reports && RadarUi.on(ctx)) add(Hudhud.Ask.WhenPays(emptyList()))
                if (reports) add(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST))
                if (reports) add(Hudhud.Ask.Activity(VoiceQuestion.Window.TODAY))
                add(Hudhud.Ask.BackupStatus)
                if (reports) {
                    add(Hudhud.Ask.Drawer)
                    add(Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH))
                    add(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
                    add(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE))
                    add(Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID))
                    add(Hudhud.Ask.Figure(VoiceQuestion.Kind.EXPIRING))
                }
                add(Hudhud.Ask.Status(Hudhud.StatusOf.BOOK))
                add(Hudhud.Ask.Clock)
            }
        }

        /**
         * The chips under the card (13 Nov 2026): [first] — what follows from the answer, the choices it offers, or the
         * nearest questions to words he could not place — then the day's own (while his first card is up) and his
         * usual ones; each once, each with its own words.
         */
        private fun showChips(first: List<Hudhud.Ask>) {
            chips.removeAllViews()
            val list = LinkedHashMap<Hudhud.Ask, String>()
            if (personal) {
                // The Personal tab: his story and the matters of faith, none of them offered under its own card.
                val onShow = if (last?.reverent == true) shownAsk else null
                for (a in HudhudBook.personalChips()) {
                    if (a == onShow) continue
                    HudhudBook.chipLabel(ctx, a)?.let { list[a] = it }
                }
            } else {
                nextChips = first
                val opening = last === helloCard
                for (a in first + (if (opening) starters else emptyList()) + usualChips()) {
                    if (a in list) continue
                    HudhudBook.chipLabel(ctx, a)?.let { list[a] = it }
                }
            }
            val density = ctx.resources.displayMetrics.density
            for ((ask, words) in list) {
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
            // A new set is read from its start.
            (chips.parent as? android.widget.HorizontalScrollView)?.scrollTo(0, 0)
        }

        /** A tab chosen by hand: the chips under the card are that tab's; the card itself is left as it is. */
        private fun selectTab(toPersonal: Boolean) {
            if (personal == toPersonal) return
            personal = toPersonal
            styleTabs()
            showChips(nextChips)
        }

        /** The open tab in the app's green with a line under it, the other in the muted ink (lesson 60: marks are night-aware). */
        private fun styleTabs() {
            for ((tab, open) in listOf(tabAsk to !personal, tabPersonal to personal)) {
                tab.isSelected = open
                tab.setTextColor(ContextCompat.getColor(ctx, if (open) R.color.brand_green_text else R.color.text_muted))
                tab.setTypeface(null, if (open) Typeface.BOLD else Typeface.NORMAL)
                tab.background = if (open) ContextCompat.getDrawable(ctx, R.drawable.bg_hudhud_tab_on) else null
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
            back.animate().cancel()
            fromFront.animate().cancel()
            // The new drawing is the one on top.
            back.translationZ = 1f
            fromFront.translationZ = 0f
            if (!Motion.enabled(ctx)) {
                back.alpha = 1f
                fromFront.alpha = 0f
                return
            }
            // It comes in over the old one, and the old one goes only once the new one is half there: two drawings
            // fading through each other at the same time left him half see-through for a moment (13 Nov 2026).
            back.alpha = 0f
            back.animate().alpha(1f).setStartDelay(0).setDuration(POSE_MS).setInterpolator(MotionKit.STANDARD).start()
            fromFront.animate().alpha(0f).setStartDelay(POSE_MS / 2).setDuration(POSE_MS / 2).setInterpolator(MotionKit.STANDARD).start()
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

        // --------------------------------------------------------------------------------------- the conversation

        /** The talk is over (or was never begun): no more listening by himself. */
        fun endConversation() {
            conversing = false
            listenAgain?.let { view.removeCallbacks(it) }
            listenAgain = null
        }

        /**
         * After an answer he listens again by himself, a breath after his voice has gone quiet (so the microphone does not
         * hear the end of his own words), for as long as the conversation lasts: at most [MAX_TURNS] answers, never while
         * something is being worked out, spoken or typed.
         */
        private fun listenSoon() {
            listenAgain?.let { view.removeCallbacks(it) }
            listenAgain = null
            if (!conversing || closed) return
            if (turns >= MAX_TURNS) {
                endConversation()
                return
            }
            val again = Runnable {
                listenAgain = null
                if (conversing && !closed && !ears.listening && job?.isActive != true && etAsk.text.isNullOrEmpty()) {
                    turns++
                    hearMyself()
                }
            }
            listenAgain = again
            view.postDelayed(again, LISTEN_AGAIN_MS)
        }

        /** The round button shows what it does now: the microphone, a stop while he listens, send once something is typed. */
        private fun syncAction(stopping: Boolean? = null) {
            val listening = stopping ?: ears.listening
            val next = when {
                listening -> Action.STOP
                !etAsk.text.isNullOrBlank() -> Action.SEND
                else -> Action.MIC
            }
            if (next == action) return
            action = next
            val icon = when (next) {
                Action.STOP -> R.drawable.ic_stop_listening
                Action.SEND -> R.drawable.ic_hudhud_send
                Action.MIC -> R.drawable.ic_mic
            }
            val label = when (next) {
                Action.STOP -> R.string.hudhud_stop_listening
                Action.SEND -> R.string.hudhud_send
                Action.MIC -> R.string.vq_ask
            }
            val swap = {
                btnMic.setImageResource(icon)
                btnMic.contentDescription = ctx.getString(label)
            }
            if (!Motion.enabled(ctx) || !btnMic.isLaidOut) {
                swap()
                return
            }
            // A small pop: the old picture shrinks away and the new one grows in.
            btnMic.animate().cancel()
            btnMic.animate().scaleX(0.7f).scaleY(0.7f).setDuration(70).withEndAction {
                swap()
                btnMic.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }.start()
        }

        // ---------------------------------------------------------------------------------------------- the sky

        /** Whether the keyboard is up: the insets say so on Android 11+, a window much shorter than the screen before. */
        private fun keyboardUp(): Boolean {
            val insets = ViewCompat.getRootWindowInsets(view)
            if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) return true
            return view.rootView.height < ctx.resources.displayMetrics.heightPixels * SHORT_WINDOW
        }

        /**
         * With the keyboard up the sky is a low band and the chips give way (14 Nov 2026), so what is typed and what he
         * answers stay in sight; his acts shrink with it ([HudhudActs.compact]). The keyboard gone, all returns.
         */
        private fun applyCompact(on: Boolean) {
            acts.stop()
            compact = on
            acts.compact = on
            val d = ctx.resources.displayMetrics.density
            fun px(dp: Int) = (dp * d).toInt()
            sky.layoutParams = sky.layoutParams.apply { height = px(if (on) 100 else 148) }
            (stage.layoutParams as FrameLayout.LayoutParams).apply {
                width = px(if (on) 60 else 84)
                height = px(if (on) 66 else 92)
                bottomMargin = px(if (on) 16 else 20)
            }.also { stage.layoutParams = it }
            for (id in listOf(R.id.hudPerchA, R.id.hudPerchB, R.id.hudPerchC)) {
                val bar = view.findViewById<View>(id)
                (bar.layoutParams as FrameLayout.LayoutParams).apply { bottomMargin = px(if (on) 12 else 16) }.also { bar.layoutParams = it }
            }
            view.findViewById<View>(R.id.hudShadow).let { shade ->
                (shade.layoutParams as FrameLayout.LayoutParams).apply {
                    width = px(if (on) 40 else 56)
                    bottomMargin = px(if (on) 9 else 12)
                }.also { shade.layoutParams = it }
            }
            chipsScroll.visibility = if (on) View.GONE else View.VISIBLE
            hudTabs.visibility = if (on) View.GONE else View.VISIBLE
            sky.requestLayout()
            sky.post { if (!closed) acts.layoutPerches() }
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
    /** The round button's three jobs. */
    private enum class Action { MIC, SEND, STOP }
    /** A breath after his voice has gone quiet before he listens again, so the microphone does not hear his last words. */
    private const val LISTEN_AGAIN_MS = 450L

    /** A breath after his "I am listening" call before the microphone opens. */
    private const val READY_GAP_MS = 60L
    /** A conversation ends after this many answers (the microphone is never left open for ever). */
    private const val MAX_TURNS = 12
    /** A window shorter than this share of the screen has the keyboard in it. */
    private const val SHORT_WINDOW = 0.62f
    /** The longest wait for the sheet's window to have the focus back before he speaks anyway. */
    private const val FOCUS_WAIT_MS = 1500L
    /** A breath between the microphone closing and his answer. */
    private const val SETTLE_MS = 150L
    /** An answer not said within this long (the app was left meanwhile) is not said out of nowhere later. */
    private const val STALE_MS = 10_000L
    /** How much the microphone grows with a loud voice while he listens. */
    private const val MIC_GROW = 0.14f
    /** How far he leans towards a voice at its loudest, in degrees. */
    private const val LEAN_DEG = 6f
    private const val SIT_BACK_MS = 220L
    private const val EASE_AFTER_TYPING_MS = 1600L
    /** The flap he answers a tap on his face with, once the sheet has risen. */
    private const val ARRIVE_ACT_MS = 380L
}
