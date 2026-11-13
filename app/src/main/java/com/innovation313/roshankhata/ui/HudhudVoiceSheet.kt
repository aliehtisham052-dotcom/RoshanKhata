package com.innovation313.roshankhata.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.HudhudSay
import java.util.Locale

/**
 * Hudhud's voice (13 Nov 2026, the owner: "awaazon ko behtar… do teen qism ki awaaz… professionally"): his sheet's
 * "Voice settings" and More → Hudhud's voice. What he calls the owner (his greeting says it); how he speaks (gentle,
 * lively, serious — the phone's voice at a pace and pitch); which of the phone's own voices for the app's language;
 * a sample; the phone's own voice downloads for a language it lacks or for more voices; and how he listens — the
 * microphone to allow, the language's offline pack to download. A choice is heard at once (the sample in it).
 *
 * The voices are the phone's own speech engine's (Google's on most phones): they work without internet and add
 * nothing to the app. Everything is kept on this phone only ([HudhudPrefs]).
 */
object HudhudVoiceSheet {

    /** At most this many of the phone's voices are offered (an English phone may have twenty). */
    private const val MAX_VOICES = 6

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
        val rgStyle: RadioGroup = view.findViewById(R.id.rgHudStyle)
        val rgVoices: RadioGroup = view.findViewById(R.id.rgHudVoices)
        val rgNameWay: RadioGroup = view.findViewById(R.id.rgHudNameWay)
        val tvNoVoice: TextView = view.findViewById(R.id.tvHudNoVoice)
        val btnMore: View = view.findViewById(R.id.btnHudMoreVoices)
        val btnSample: View = view.findViewById(R.id.btnHudSample)
        val tvListen: TextView = view.findViewById(R.id.tvHudListen)
        val btnListen: MaterialButton = view.findViewById(R.id.btnHudListen)
        var micAsk: ActivityResultLauncher<String>? = null
        var closed = false

        fun start() {
            view.findViewById<View>(R.id.btnHudVoiceClose).setOnClickListener { sheet.dismiss() }

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

            // His style, heard at once when another is chosen.
            rgStyle.check(idOf(HudhudPrefs.style(ctx)))
            rgStyle.setOnCheckedChangeListener { _, id ->
                styleOf(id)?.let {
                    HudhudPrefs.setStyle(ctx, it)
                    sample()
                }
            }

            voice.voices { list -> if (!closed) voice.plan { plan -> if (!closed) fillVoices(list, plan) } }
            btnMore.setOnClickListener {
                voice.engine { engine -> if (!closed) HudhudVoice.openVoiceDownloads(host, engine) }
            }
            btnSample.setOnClickListener { sample() }
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

        private fun saveName() {
            HudhudPrefs.setCallMe(ctx, etCallMe.text?.toString())
        }

        /**
         * The phone's voices for the language he speaks, "Voice 1" first (the best), the chosen one ticked; when that is
         * not the app's own language (the phone has no voice for it), the line under them says which he speaks.
         */
        private fun fillVoices(list: List<HudhudVoice.Choice>, plan: HudhudVoice.Plan?) {
            rgVoices.setOnCheckedChangeListener(null)
            rgVoices.removeAllViews()
            val language = plan?.language ?: return showNoVoice(null)
            fillNameWays(language)
            val chosen = HudhudPrefs.voiceName(ctx, language)
            val shown = list.take(MAX_VOICES)
            if (plan.fallback) showNoVoice(plan) else tvNoVoice.visibility = View.GONE
            val tick = shown.indexOfFirst { it.name == chosen }.takeIf { it >= 0 } ?: 0
            val minHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, ctx.resources.displayMetrics).toInt()
            shown.forEachIndexed { i, v ->
                val rb = RadioButton(ctx).apply {
                    id = View.generateViewId()
                    // The number as written on every screen: 0-9 (Digits' rule), not the language's own digits.
                    text = ctx.getString(R.string.hudhud_voice_n, (i + 1).toString())
                    textSize = 16f
                    this.minHeight = minHeight
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setTextColor(ContextCompat.getColor(ctx, R.color.ink))
                    tag = v.name
                }
                rgVoices.addView(rb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                if (i == tick) rb.isChecked = true
            }
            TextFit.relax(rgVoices)
            rgVoices.setOnCheckedChangeListener { group, id ->
                val name = group.findViewById<RadioButton>(id)?.tag as? String ?: return@setOnCheckedChangeListener
                HudhudPrefs.setVoiceName(ctx, language, name)
                sample()
            }
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
            rgNameWay.setOnCheckedChangeListener(null)
            rgNameWay.removeAllViews()
            val ways = HudhudSay.nameWays(language)
            val chosen = HudhudPrefs.nameWay(ctx, language) ?: 0
            val minHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, ctx.resources.displayMetrics).toInt()
            ways.forEachIndexed { i, way ->
                val rb = RadioButton(ctx).apply {
                    id = View.generateViewId()
                    text = way
                    textSize = 16f
                    this.minHeight = minHeight
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setTextColor(ContextCompat.getColor(ctx, R.color.ink))
                    tag = i
                }
                rgNameWay.addView(rb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                if (i == chosen.coerceIn(0, ways.size - 1)) rb.isChecked = true
            }
            TextFit.relax(rgNameWay)
            rgNameWay.setOnCheckedChangeListener { group, id ->
                val way = group.findViewById<RadioButton>(id)?.tag as? Int ?: return@setOnCheckedChangeListener
                HudhudPrefs.setNameWay(ctx, language, way)
                sample()
            }
        }

        /** A sample in the chosen style, voice and way of saying his name; a phone with no voice says so. */
        private fun sample() {
            voice.speak(
                { it.getString(R.string.hudhud_voice_sample) },
                object : HudhudVoice.Listener {},
                noVoice = { if (!closed) showNoVoice(null) }
            )
        }

        // -------------------------------------------------------------------------------- how he listens

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

        private fun idOf(style: HudhudStyle): Int = when (style) {
            HudhudStyle.PROFESSIONAL -> R.id.rbHudProfessional
            HudhudStyle.GENTLE -> R.id.rbHudGentle
            HudhudStyle.LIVELY -> R.id.rbHudLively
            HudhudStyle.SERIOUS -> R.id.rbHudSerious
        }

        private fun styleOf(id: Int): HudhudStyle? = when (id) {
            R.id.rbHudProfessional -> HudhudStyle.PROFESSIONAL
            R.id.rbHudGentle -> HudhudStyle.GENTLE
            R.id.rbHudLively -> HudhudStyle.LIVELY
            R.id.rbHudSerious -> HudhudStyle.SERIOUS
            else -> null
        }
    }
}
