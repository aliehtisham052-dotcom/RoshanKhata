package com.innovation313.roshankhata

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.innovation313.roshankhata.data.IncomingPayment

/**
 * More → Payment voice (5 Nov 2026, [PaymentVoice]): the switch, notification access, which apps are listened to,
 * the voice, a test, the battery hint and the privacy line. Everything here is this phone's own setting.
 */
class PaymentVoiceActivity : BaseActivity() {

    private var speaker: PaymentSpeaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_voice)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        setSupportActionBar(findViewById<Toolbar>(R.id.payToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val row = findViewById<View>(R.id.payRowOn)
        row.findViewById<TextView>(R.id.tvFeatureTitle).setText(R.string.paisa_switch)
        row.findViewById<TextView>(R.id.tvFeatureDesc).setText(R.string.paisa_switch_desc)
        val switch = row.findViewById<Switch>(R.id.switchFeature)
        switch.isChecked = PaymentVoice.isOn(this)
        // After the state is set (lesson 4): the listener only follows the owner's taps.
        switch.setOnCheckedChangeListener { _, on ->
            PaymentVoice.setOn(this, on)
            if (on) askNotifications()
        }

        findViewById<MaterialButton>(R.id.btnPayAccess).setOnClickListener {
            runCatching { startActivity(PaymentVoice.accessIntent(this)) }.onFailure {
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }

        val group = findViewById<android.widget.RadioGroup>(R.id.rgPayVoice)
        group.check(
            when (PaymentVoice.voice(this)) {
                PaymentVoice.Voice.URDU -> R.id.rbPayUrdu
                PaymentVoice.Voice.APP -> R.id.rbPayApp
                PaymentVoice.Voice.CHIME -> R.id.rbPayChime
            }
        )
        group.setOnCheckedChangeListener { _, id ->
            PaymentVoice.setVoice(
                this, when (id) {
                    R.id.rbPayApp -> PaymentVoice.Voice.APP
                    R.id.rbPayChime -> PaymentVoice.Voice.CHIME
                    else -> PaymentVoice.Voice.URDU
                }
            )
        }

        findViewById<MaterialButton>(R.id.btnPaySpeech).setOnClickListener { openSpeechSettings() }
        findViewById<MaterialButton>(R.id.btnPayTest).setOnClickListener { test() }
        findViewById<MaterialButton>(R.id.btnPayBattery).setOnClickListener { openBattery() }
    }

    override fun onResume() {
        super.onResume()
        val access = PaymentVoice.hasAccess(this)
        if (access) PaymentVoice.settleDefaults(this)
        findViewById<TextView>(R.id.tvPayAccess).apply {
            setText(if (access) R.string.paisa_access_given else R.string.paisa_access_not_given)
            setTextColor(ContextCompat.getColor(this@PaymentVoiceActivity, if (access) R.color.green_got_text else R.color.red_gave_text))
        }
        // Android 13+ may call a sideloaded app's access a "restricted setting": the way past it, said once here.
        findViewById<View>(R.id.tvPayRestricted).visibility =
            if (!access && Build.VERSION.SDK_INT >= 33) View.VISIBLE else View.GONE
        // Nothing can be said without the access: the switch waits for it, and says so (6 Nov 2026).
        val row = findViewById<View>(R.id.payRowOn)
        row.findViewById<Switch>(R.id.switchFeature).isEnabled = access
        row.alpha = if (access) 1f else 0.55f
        row.findViewById<TextView>(R.id.tvFeatureDesc)
            .setText(if (access) R.string.paisa_switch_desc else R.string.paisa_switch_needs_access)
        renderApps()
        checkUrdu()
    }

    /** JazzCash and Easypaisa first (shown even before they are seen), then the apps seen since, newest first. */
    private fun renderApps() {
        val list = findViewById<LinearLayout>(R.id.payAppsList)
        list.removeAllViews()
        val chosen = PaymentVoice.chosen(this)
        val rows = PaymentVoice.KNOWN.map { (pkg, name) ->
            Triple(pkg, if (PaymentVoice.installed(this, pkg)) name else getString(R.string.paisa_not_installed, name), true)
        } + PaymentVoice.seen(this).map { Triple(it.pkg, it.label, false) }
        val dp = resources.displayMetrics.density
        for ((pkg, label, known) in rows) {
            // An app not on this phone reads muted: ticked, it waits for the app to be installed (6 Nov 2026).
            val here = !known || PaymentVoice.installed(this, pkg)
            val box = MaterialCheckBox(this).apply {
                text = label
                textSize = 15f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setTextColor(ContextCompat.getColor(this@PaymentVoiceActivity, if (here) R.color.ink else R.color.text_muted))
                minHeight = (48 * dp).toInt()
                isChecked = pkg in chosen
                setOnCheckedChangeListener { _, on -> PaymentVoice.setChosen(this@PaymentVoiceActivity, pkg, on) }
            }
            list.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        findViewById<View>(R.id.tvPayAppsNone).visibility = if (rows.size > PaymentVoice.KNOWN.size) View.GONE else View.VISIBLE
        com.innovation313.roshankhata.ui.TextFit.relax(list)
    }

    private fun speakerNow(): PaymentSpeaker = speaker ?: PaymentSpeaker(this).also { speaker = it }

    private fun checkUrdu() {
        val line = findViewById<TextView>(R.id.tvPayUrdu)
        speakerNow().checkUrdu { has ->
            if (isFinishing || isDestroyed) return@checkUrdu
            line.setText(if (has == true) R.string.paisa_urdu_yes else R.string.paisa_urdu_no)
        }
    }

    /** A sample, said exactly as a real payment would be, in the chosen voice. */
    private fun test() {
        speakerNow().announce(PaymentVoice.voice(this)) { words ->
            PaymentVoice.spoken(words, IncomingPayment(5000.0, sender = words.getString(R.string.paisa_test_name)), "JazzCash")
        }
    }

    /** The speech engine's voice data (where Urdu is added), else the phone's text-to-speech settings. */
    private fun openSpeechSettings() {
        val install = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        if (runCatching { startActivity(install) }.isSuccess) return
        runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
    }

    /** Some phones stop a listener to save battery: the phone's own list, else this app's page. */
    private fun openBattery() {
        val list = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (runCatching { startActivity(list) }.isSuccess) return
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", packageName, null))
            )
        }
    }

    /** Android 13+: the notice with Add to khata needs the app's notification permission. */
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) androidx.core.app.ActivityCompat.requestPermissions(
            this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 314
        )
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
