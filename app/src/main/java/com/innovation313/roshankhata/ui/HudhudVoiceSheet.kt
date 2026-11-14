package com.innovation313.roshankhata.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.HudhudChirp
import com.innovation313.roshankhata.data.HudhudPace
import com.innovation313.roshankhata.data.HudhudPronounce
import com.innovation313.roshankhata.data.HudhudSay
import com.innovation313.roshankhata.data.string
import java.util.Locale

/**
 * Hudhud's voice (13-14 Nov 2026, the owner: "awaazon ko behtar… do teen qism ki awaaz… professionally", "cartoonish,
 * interacting awaaz"): his sheet's "Voice settings" and More → Hudhud's voice. What he calls the owner (his greeting says
 * it); how he speaks — five styles (professional, gentle, lively, serious, cartoon: the phone's voice at a pace and
 * pitch) and the pace on a slider; which of the phone's own voices for the app's language; how he says his own name and
 * Alhamdulillah, each heard when chosen; his little bird calls; a sample; the phone's own voice downloads for a language
 * it lacks or for more voices; how he listens — the microphone to allow, the language's offline pack to download — and,
 * folded away at the foot, the testing tools. A choice is heard at once (the sample in it).
 *
 * The choices are chips (the poster screen's look: filled green and ticked when chosen), which wrap onto as many lines as
 * their words need at any text size. The voices are the phone's own speech engine's (Google's on most phones): they work
 * without internet and add nothing to the app, and the bird calls are made as numbers ([HudhudChirp]). Everything is
 * kept on this phone only ([HudhudPrefs]).
 */
object HudhudVoiceSheet {

    /** At most this many of the phone's voices are offered (an English phone may have twenty). */
    private const val MAX_VOICES = 6

    /** How long the chevron of the folded testing tools takes to turn. */
    private const val TOOLS_TURN_MS = 160L

    /** Opens the settings over [activity]; the sheet, for the on-device test (null when the screen is going). */
    fun show(activity: Activity): BottomSheetDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val host = activity as? ComponentActivity ?: return null
        val sheet = BottomSheetDialog(activity, R.style.ThemeOverlay_RoshanKhata_HudhudSheet)
        val view = LayoutInflater.from(sheet.context).inflate(R.layout.sheet_hudhud_voice, null)
        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val ui = Ui(host, sheet, view)
        DialogLife.bind(activity, sheet, onDismiss = { ui.close() })
        sheet.show()
        KeyboardFollow.dialog(sheet)
        ui.start()
        return sheet
    }

    private class Ui(val host: ComponentActivity, val sheet: BottomSheetDialog, val view: View) {
        val ctx: Context = host
        val inflater: LayoutInflater = LayoutInflater.from(view.context)
        val voice = HudhudVoice(host)
        // Only to ask the phone for the language's pack: nothing is listened to here.
        val ears = HudhudEars(host, object : HudhudEars.Callbacks {
            override fun onListening() {}
            override fun onPartial(words: String) {}
            override fun onLevel(level: Float) {}
            override fun onHeard(guesses: List<String>) {}
            override fun onTrouble(trouble: HudhudEars.Trouble) {}
        })
        val etCallMe: EditText = view.findViewById(R.id.etHudCallMe)
        val cgStyle: ChipGroup = view.findViewById(R.id.cgHudStyle)
        val slPace: Slider = view.findViewById(R.id.slHudPace)
        val tvPace: TextView = view.findViewById(R.id.tvHudPaceValue)
        val cgVoices: ChipGroup = view.findViewById(R.id.cgHudVoices)
        val tvNoVoice: TextView = view.findViewById(R.id.tvHudNoVoice)
        val btnMore: View = view.findViewById(R.id.btnHudMoreVoices)
        val llName: View = view.findViewById(R.id.llHudName)
        val cgNameWay: ChipGroup = view.findViewById(R.id.cgHudNameWay)
        val llPron: View = view.findViewById(R.id.llHudPron)
        val cgPronWay: ChipGroup = view.findViewById(R.id.cgHudPronWay)
        val swChirps: CompoundButton = view.findViewById(R.id.swHudChirps)
        val cgCalls: ChipGroup = view.findViewById(R.id.cgHudCalls)
        val btnSample: View = view.findViewById(R.id.btnHudSample)
        val tvListen: TextView = view.findViewById(R.id.tvHudListen)
        val btnListen: MaterialButton = view.findViewById(R.id.btnHudListen)
        val rowTools: View = view.findViewById(R.id.rowHudTools)
        val ivToolsChevron: ImageView = view.findViewById(R.id.ivHudToolsChevron)
        val llTools: View = view.findViewById(R.id.llHudTools)
        val swCheck: CompoundButton = view.findViewById(R.id.swHudCheck)
        val tvCheckCount: TextView = view.findViewById(R.id.tvHudCheckCount)
        val btnCheckShare: View = view.findViewById(R.id.btnHudCheckShare)
        val btnCheckClear: View = view.findViewById(R.id.btnHudCheckClear)
        var micAsk: ActivityResultLauncher<String>? = null
        var closed = false

        /**
         * True while this screen itself puts a control right (fills a group of chips, sets the bird-call switch to what
         * is kept), so that its listener does not take that for the owner's own choice.
         */
        var building = false

        private inline fun building(block: () -> Unit) {
            val was = building
            building = true
            try { block() } finally { building = was }
        }

        fun start() {
            view.findViewById<View>(R.id.btnHudVoiceClose).setOnClickListener { sheet.dismiss() }
            startName()
            startStyles()
            startPace()
            startChirps()
            voice.voices { list -> if (!closed) voice.plan { plan -> if (!closed) fillVoices(list, plan) } }
            btnMore.setOnClickListener {
                voice.engine { engine -> if (!closed) HudhudVoice.openVoiceDownloads(host, engine) }
            }
            btnSample.setOnClickListener { sample() }
            startTools()
            refreshListening()
        }

        fun close() {
            closed = true
            saveName()
            voice.shutdown()
            ears.destroy()
            micAsk?.unregister()
            micAsk = null
        }

        // ------------------------------------------------------------------------------------------ what he calls you

        private fun startName() {
            etCallMe.setText(HudhudPrefs.callMe(ctx).orEmpty())
            etCallMe.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    saveName()
                    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(etCallMe.windowToken, 0)
                    etCallMe.clearFocus()
                    true
                } else false
            }
        }

        private fun saveName() {
            HudhudPrefs.setCallMe(ctx, etCallMe.text?.toString())
        }

        // ------------------------------------------------------------------------------------------ how he speaks

        /** A choice on the sheet: the poster screen's chip, the ChipGroup's own layout parameters, an id of its own. */
        private fun choiceChip(group: ChipGroup, text: CharSequence): Chip {
            val chip = inflater.inflate(R.layout.item_hudhud_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = text
            return chip
        }

        /** The five styles, the one in use chosen; another is kept and heard at once. */
        private fun startStyles() {
            val chosen = HudhudPrefs.style(ctx)
            building {
                for (style in HudhudStyle.values()) {
                    val chip = choiceChip(cgStyle, ctx.getString(style.label))
                    chip.tag = style
                    cgStyle.addView(chip)
                    if (style == chosen) chip.isChecked = true
                }
            }
            TextFit.relax(cgStyle)
            cgStyle.setOnCheckedStateChangeListener { group, ids ->
                if (building) return@setOnCheckedStateChangeListener
                val style = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }?.tag as? HudhudStyle
                    ?: return@setOnCheckedStateChangeListener
                HudhudPrefs.setStyle(ctx, style)
                // Until the owner has said yes or no to the bird calls they follow the style (on with the cartoon voice).
                showChirps()
                sample()
            }
        }

        /** His pace on a slider of tenths; kept as it moves, heard when the finger lifts. */
        private fun startPace() {
            // The range is HudhudPace's, the same one the "slower" and "faster" he is asked for keep to (the layout's
            // own numbers are only where it starts).
            slPace.valueFrom = HudhudPace.MIN
            slPace.valueTo = HudhudPace.MAX
            slPace.stepSize = HudhudPace.STEP
            slPace.value = HudhudPace.snap(HudhudPrefs.pace(ctx))
            slPace.setLabelFormatter { value -> HudhudPace.label(value) }
            tvPace.text = HudhudPace.label(slPace.value)
            slPace.addOnChangeListener { _, value, fromUser ->
                tvPace.text = HudhudPace.label(value)
                if (fromUser) HudhudPrefs.setPace(ctx, value)
            }
            slPace.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) { sample() }
            })
        }

        /** The bird-call switch and the three calls to hear; a tap on a call plays it whether the switch is on or not. */
        private fun startChirps() {
            showChirps()
            swChirps.setOnCheckedChangeListener { _, on -> if (!building) HudhudPrefs.setChirps(ctx, on) }
            val calls = listOf(
                R.string.hudhud_bird_hoop to HudhudChirp.Kind.HOOP,
                R.string.hudhud_bird_ready to HudhudChirp.Kind.READY,
                R.string.hudhud_bird_glad to HudhudChirp.Kind.GLAD
            )
            for ((words, kind) in calls) {
                val chip = inflater.inflate(R.layout.item_hudhud_call_chip, cgCalls, false) as Chip
                chip.id = View.generateViewId()
                chip.text = ctx.getString(words)
                chip.setOnClickListener { HudhudChirpPlayer.playAlways(kind) }
                cgCalls.addView(chip)
            }
            TextFit.relax(cgCalls)
        }

        /** The switch as it stands now: the owner's word when he gave one, else what the style in use asks for. */
        private fun showChirps() {
            building { swChirps.isChecked = HudhudPrefs.chirps(ctx) }
        }

        /**
         * The phone's voices for the language he speaks, "Voice 1" first (the best), the chosen one ticked; when that is
         * not the app's own language (the phone has no voice for it), the line under them says which he speaks. The
         * ways of saying his name and Alhamdulillah follow the language he speaks, and are offered only with a voice to
         * hear them in.
         */
        private fun fillVoices(list: List<HudhudVoice.Choice>, plan: HudhudVoice.Plan?) {
            building { cgVoices.removeAllViews() }
            val language = plan?.language ?: return noVoiceHere()
            fillNameWays(language)
            fillPronWays(language)
            val chosen = HudhudPrefs.voiceName(ctx, language)
            val shown = list.take(MAX_VOICES)
            if (plan.fallback) showNoVoice(plan) else tvNoVoice.visibility = View.GONE
            val tick = shown.indexOfFirst { it.name == chosen }.takeIf { it >= 0 } ?: 0
            building {
                shown.forEachIndexed { i, v ->
                    // The number as written on every screen: 0-9 (Digits' rule), not the language's own digits.
                    val chip = choiceChip(cgVoices, ctx.getString(R.string.hudhud_voice_n, (i + 1).toString()))
                    chip.tag = v.name
                    cgVoices.addView(chip)
                    if (i == tick) chip.isChecked = true
                }
            }
            TextFit.relax(cgVoices)
            cgVoices.setOnCheckedStateChangeListener { group, ids ->
                if (building) return@setOnCheckedStateChangeListener
                val name = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }?.tag as? String
                    ?: return@setOnCheckedStateChangeListener
                HudhudPrefs.setVoiceName(ctx, language, name)
                sample()
            }
        }

        /** The phone has no voice at all: say so, and leave out what can only be heard in one. */
        private fun noVoiceHere() {
            showNoVoice(null)
            llName.visibility = View.GONE
            llPron.visibility = View.GONE
        }

        /** No voice at all ([plan] null), or a voice of another language ([plan]'s fallback): said under the voices. */
        private fun showNoVoice(plan: HudhudVoice.Plan?) {
            if (plan == null) {
                tvNoVoice.setText(R.string.hudhud_voice_none_here)
                tvNoVoice.visibility = View.VISIBLE
                return
            }
            val app = ctx.resources.configuration.locales[0]
            val wanted = Locale(plan.wanted).getDisplayLanguage(app).ifBlank { plan.wanted }
            val spoken = Locale(plan.language).getDisplayLanguage(app).ifBlank { plan.language }
            tvNoVoice.text = ctx.getString(R.string.hudhud_voice_fallback, wanted, spoken)
            tvNoVoice.visibility = View.VISIBLE
        }

        /** The ways his name can be given to the voice, each written as the voice gets it; the chosen one ticked. */
        private fun fillNameWays(language: String) {
            val ways = HudhudSay.nameWays(language)
            // One way is nothing to choose between.
            llName.visibility = if (ways.size > 1) View.VISIBLE else View.GONE
            if (ways.size <= 1) return
            val chosen = (HudhudPrefs.nameWay(ctx, language) ?: 0).coerceIn(0, ways.lastIndex)
            building {
                cgNameWay.removeAllViews()
                ways.forEachIndexed { i, way ->
                    val chip = choiceChip(cgNameWay, way)
                    chip.tag = i
                    cgNameWay.addView(chip)
                    if (i == chosen) chip.isChecked = true
                }
            }
            TextFit.relax(cgNameWay)
            cgNameWay.setOnCheckedStateChangeListener { group, ids ->
                if (building) return@setOnCheckedStateChangeListener
                val way = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }?.tag as? Int
                    ?: return@setOnCheckedStateChangeListener
                HudhudPrefs.setNameWay(ctx, language, way)
                sample()
            }
        }

        /**
         * The ways Alhamdulillah can be given to the voice (14 Nov 2026, the owner: the phone's voice read it wrongly),
         * each heard in a sentence when chosen; offered only for a language that has more than one.
         */
        private fun fillPronWays(language: String) {
            val word = HudhudPronounce.Word.ALHAMDULILLAH
            val ways = HudhudPronounce.ways(word, language)
            llPron.visibility = if (ways.size > 1) View.VISIBLE else View.GONE
            if (ways.size <= 1) return
            val chosen = HudhudPrefs.wayOf(ctx, word, language).coerceIn(0, ways.lastIndex)
            building {
                cgPronWay.removeAllViews()
                ways.forEachIndexed { i, way ->
                    val chip = choiceChip(cgPronWay, way)
                    chip.tag = i
                    cgPronWay.addView(chip)
                    if (i == chosen) chip.isChecked = true
                }
            }
            TextFit.relax(cgPronWay)
            cgPronWay.setOnCheckedStateChangeListener { group, ids ->
                if (building) return@setOnCheckedStateChangeListener
                val way = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }?.tag as? Int
                    ?: return@setOnCheckedStateChangeListener
                HudhudPrefs.setWayOf(ctx, word, language, way)
                samplePron()
            }
        }

        /**
         * A sample in the chosen style, pace, voice and way of saying his name, with his call before it when bird calls
         * are on; a phone with no voice says so.
         */
        private fun sample() {
            voice.speak(
                { it.getString(R.string.hudhud_voice_sample) },
                object : HudhudVoice.Listener {},
                noVoice = { if (!closed) showNoVoice(null) },
                chirp = HudhudChirp.Kind.HOOP
            )
        }

        /** A sentence with Alhamdulillah in it, said the way just chosen. */
        private fun samplePron() {
            voice.speak(
                { it.getString(R.string.hudhud_pron_sample) },
                object : HudhudVoice.Listener {},
                noVoice = { if (!closed) showNoVoice(null) }
            )
        }

        // ------------------------------------------------------------------------------------------ how he listens

        private fun appTag(): String = Locale.getDefault().toLanguageTag()

        private fun refreshListening() {
            if (!HudhudEars.canHear(ctx)) {
                tvListen.setText(R.string.vq_no_recognizer)
                btnListen.visibility = View.GONE
                return
            }
            HudhudEars.status(ctx, appTag()) { status -> if (!closed) showListening(status) }
        }

        private fun showListening(status: HudhudEars.Status) {
            when (status) {
                HudhudEars.Status.NO_MIC -> {
                    tvListen.setText(R.string.hudhud_listen_box)
                    button(R.string.hudhud_mic_allow) { askForMic() }
                }
                HudhudEars.Status.READY_OFFLINE -> {
                    tvListen.setText(R.string.hudhud_listen_ready)
                    btnListen.visibility = View.GONE
                }
                HudhudEars.Status.NEEDS_PACK -> {
                    tvListen.setText(R.string.hudhud_listen_pack)
                    button(R.string.hudhud_pack_download) {
                        val asked = ears.downloadPack(appTag())
                        tvListen.setText(if (asked) R.string.hudhud_pack_started else R.string.hudhud_listen_online)
                        btnListen.visibility = View.GONE
                    }
                }
                HudhudEars.Status.ONLINE_ONLY -> {
                    tvListen.setText(R.string.hudhud_listen_online)
                    btnListen.visibility = View.GONE
                }
            }
        }

        private fun button(words: Int, onClick: () -> Unit) {
            btnListen.setText(words)
            btnListen.visibility = View.VISIBLE
            btnListen.setOnClickListener { onClick() }
        }

        /** Android asks for the microphone; once it will not ask again, its own page for the app's permissions. */
        private fun askForMic() {
            val launcher = micAsk ?: host.activityResultRegistry.register(
                "hudhud-voice-mic-" + System.nanoTime(), ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (closed) return@register
                HudhudPrefs.setMicDenied(ctx, !granted)
                if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(host, Manifest.permission.RECORD_AUDIO)) {
                    openAppSettings()
                }
                refreshListening()
            }.also { micAsk = it }
            try {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                openAppSettings()
            }
        }

        private fun openAppSettings() {
            try {
                host.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", host.packageName, null))
                )
            } catch (e: ActivityNotFoundException) {
                // nothing more to open
            }
        }

        // ------------------------------------------------------------------------------------------ the testing tools

        /** The tools are folded away until the owner opens them; the chevron turns the way the row opens. */
        private fun startTools() {
            startCheck()
            rowTools.setOnClickListener { toggleTools() }
        }

        private fun toggleTools() {
            val open = llTools.visibility != View.VISIBLE
            llTools.visibility = if (open) View.VISIBLE else View.GONE
            val turn = if (open) 180f else 0f
            ivToolsChevron.animate().cancel()
            if (Motion.enabled(ctx)) ivToolsChevron.animate().rotation(turn).setDuration(TOOLS_TURN_MS).start()
            else ivToolsChevron.rotation = turn
            // What was just opened is brought into view, its row at the top.
            if (open) (view as? NestedScrollView)?.let { scroll -> scroll.post { scroll.smoothScrollTo(0, rowTools.top) } }
        }

        /** The owner's check of a test build (14 Nov 2026, [HudhudCheck]): the switch, the counts, Share and Clear. */
        private fun startCheck() {
            swCheck.isChecked = HudhudCheck.isOn(ctx)
            swCheck.setOnCheckedChangeListener { _, on -> HudhudCheck.setOn(ctx, on) }
            showCheckCount()
            btnCheckShare.setOnClickListener {
                if (!HudhudCheck.share(host)) {
                    android.widget.Toast.makeText(ctx, R.string.hudhud_check_nothing, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            btnCheckClear.setOnClickListener {
                HudhudCheck.clear(ctx)
                showCheckCount()
                android.widget.Toast.makeText(ctx, R.string.hudhud_check_cleared, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun showCheckCount() {
            val c = HudhudCheck.counts(ctx)
            val percent = c.percent
            // Figures in 0-9 in every language, as every screen writes them (Digits' rule).
            tvCheckCount.text = if (percent == null) Digits.string(ctx.resources, R.string.hudhud_check_none, c.asked)
            else Digits.string(ctx.resources, R.string.hudhud_check_count, c.asked, c.right, c.wrong, percent)
        }
    }
}
