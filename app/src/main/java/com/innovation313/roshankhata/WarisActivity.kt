package com.innovation313.roshankhata

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.string
import com.innovation313.roshankhata.data.ViewerMode
import com.innovation313.roshankhata.data.ViewerSync
import com.innovation313.roshankhata.data.Waris
import com.innovation313.roshankhata.data.WarisAndroid
import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.ui.FormBox
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.QrImage
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.TextFit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Waris ki Chaabi (4 Nov 2026). Part 1, the owner's: turn on, name heirs (QR1 shown, their QR2 scanned and checked),
 * see who asked and when it opens, refuse or allow, update the locked copy, remove an heir, turn off. Part 2, the
 * heir's: scan the owner's code (or one's own Waris card), show one's code back, save the card, ask, and open the
 * book when it opens — into the same read-only copy a helper's phone takes from a backup file ([ViewerSync]).
 *
 * Owner-only on a counter (AccessUi: BACKUP). On a helper's read-only phone only a note shows: a viewer never
 * manages a vault, and the heir's own links live in this phone's own book, which comes back once the copy stops.
 * Every write runs on [AppScope]: leaving the screen never cuts a refusal or an upload half way.
 */
class WarisActivity : BaseActivity() {

    private lateinit var heirsBox: LinearLayout
    private lateinit var linksBox: LinearLayout
    private var busy = false
    private var codeDialog: AppCompatDialog? = null

    /**
     * The Waris card's picture goes the moment its share comes back (the security review, W7): whoever holds it, or a
     * photo of it, can ask in the heir's name. By its path, so a screen rebuilt meanwhile still deletes it.
     */
    // Not at once (8 Nov 2026 review, M9): the chooser returns the moment an app is picked, before that app has read the
    // file. Ten minutes later on AppScope; a process that dies meanwhile loses it to the sweep when this screen next opens.
    private val shareCard = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val card = cardFile()
        AppScope.launch { kotlinx.coroutines.delay(10 * 60_000L); runCatching { card.delete() } }
    }

    private fun cardFile(): java.io.File = java.io.File(java.io.File(cacheDir, "receipts"), CARD_FILE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waris)
        ScreenInsets.on(this)
        // A card left behind by a share that never came back (the app closed meanwhile) goes when the screen opens.
        if (savedInstanceState == null) lifecycleScope.launch(Dispatchers.IO) { runCatching { cardFile().delete() } }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        heirsBox = findViewById(R.id.warisHeirs)
        linksBox = findViewById(R.id.warisLinks)
        findViewById<View>(R.id.btnWarisViewerLeave).setOnClickListener { startActivity(Intent(this, ViewerActivity::class.java)) }
        findViewById<View>(R.id.btnWarisOn).setOnClickListener { askTurnOn() }
        findViewById<View>(R.id.btnWarisAddHeir).setOnClickListener { addHeir() }
        findViewById<View>(R.id.btnWarisUpdate).setOnClickListener { updateCopy() }
        findViewById<View>(R.id.btnWarisOff).setOnClickListener { askTurnOff() }
        findViewById<View>(R.id.btnWarisScanOwner).setOnClickListener { scan { takeOwnerCode(it) } }
        render()
        // The relays' word, read now (never more than the screen's own visit needs).
        if (!ViewerMode.isOn(this)) lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { WarisAndroid.checkIfDue(applicationContext, force = true) } }
            render()
        }
    }

    override fun onDestroy() {
        codeDialog?.dismiss()
        codeDialog = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ drawing

    private class Read(
        val state: Waris.OwnerState?,
        val views: Map<String, Waris.HeirView>,
        val links: List<Waris.Link>,
        /** A refusal some relay has not taken yet (W6). */
        val refusalOwed: Boolean = false,
        /** Some relay refuses this vault's owner pass: someone else holds the vault id there (W9). */
        val relayRefuses: Boolean = false
    )

    private fun render() {
        val viewer = ViewerMode.isOn(this)
        findViewById<View>(R.id.warisViewer).visibility = if (viewer) View.VISIBLE else View.GONE
        findViewById<View>(R.id.warisMain).visibility = if (viewer) View.GONE else View.VISIBLE
        if (viewer) {
            findViewById<TextView>(R.id.tvWarisViewer).text = WarisAndroid.heirCopyOf(this)
                ?.let { getString(R.string.waris_heir_copy_banner, Format.isolate(it)) + "\n\n" + getString(R.string.waris_viewer_note) }
                ?: getString(R.string.waris_viewer_note)
            return
        }
        lifecycleScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val owner = WarisAndroid.owner(applicationContext)
                    Read(
                        owner.state(), owner.views(), WarisAndroid.heir(applicationContext).links(),
                        owner.refuseLeft().isNotEmpty(), owner.relaysRefusing().isNotEmpty()
                    )
                }.getOrNull()
            } ?: return@launch
            if (isFinishing || isDestroyed) return@launch
            paintOwner(read)
            paintLinks(read.links)
        }
    }

    private fun days(n: Int) = Digits.quantity(resources, R.plurals.waris_days, n, n)

    private fun paintOwner(read: Read) {
        val st = read.state
        findViewById<View>(R.id.warisOwnerOff).visibility = if (st == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.warisOwnerOn).visibility = if (st == null) View.GONE else View.VISIBLE
        heirsBox.removeAllViews()
        if (st == null) return
        // What the owner must know first: a refusal not on every relay yet (W6), a relay that refuses this vault (W9).
        findViewById<TextView>(R.id.tvWarisNote).apply {
            val notes = listOfNotNull(
                getString(R.string.waris_refusal_partial).takeIf { read.refusalOwed },
                getString(R.string.waris_relay_refuses).takeIf { read.relayRefuses }
            )
            text = notes.joinToString("\n\n")
            visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.tvWarisCopy).text = if (st.snapAt > 0L)
            getString(R.string.waris_copy_line, Format.dateTime(st.snapAt), android.text.format.Formatter.formatShortFileSize(this, st.snapBytes))
        else getString(R.string.waris_copy_none)
        val now = System.currentTimeMillis()
        val inflater = LayoutInflater.from(this)
        for (h in st.heirs) {
            val row = inflater.inflate(R.layout.item_waris_heir, heirsBox, false)
            val v = read.views[h.hid]
            row.findViewById<TextView>(R.id.tvHeirName).text = h.name
            val last = v?.lastSeen
            val seen = when {
                !h.linked -> null
                last == null -> getString(R.string.waris_seen_never)
                else -> {
                    val d = ((now - last) / Waris.DAY_MS).toInt().coerceAtLeast(0)
                    if (d == 0) getString(R.string.waris_seen_today) else Digits.quantity(resources, R.plurals.waris_seen_days, d, d)
                }
            }
            row.findViewById<TextView>(R.id.tvHeirDetail).text =
                listOfNotNull(getString(R.string.waris_wait_line, days(h.wait)), seen).joinToString(" · ")
            val state = row.findViewById<TextView>(R.id.tvHeirState)
            val first = row.findViewById<MaterialButton>(R.id.btnHeirFirst)
            val second = row.findViewById<MaterialButton>(R.id.btnHeirSecond)
            second.visibility = View.GONE
            when {
                !h.linked -> {
                    state.setText(R.string.waris_state_waiting_scan)
                    state.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
                    first.text = getString(R.string.waris_scan_heir, h.name)
                    first.setOnClickListener { showOwnerCode(h) }
                }
                v != null && v.pending -> {
                    // `openAt` comes from :shared — read once into a local (no smart cast across modules).
                    val at = v.openAt
                    state.text = when {
                        at == null -> getString(R.string.waris_asked_title)
                        // The day has come: open to them now, not "opens on" a day already past (W9).
                        at <= now -> getString(R.string.waris_state_open_now)
                        else -> getString(R.string.waris_state_asked, Format.dateOnly(at))
                    }
                    state.setTextColor(ContextCompat.getColor(this, R.color.red_gave_text))
                    first.setText(R.string.waris_refuse)
                    first.setOnClickListener { refuse(h) }
                    second.visibility = View.VISIBLE
                    second.setText(R.string.waris_allow_now)
                    second.setOnClickListener { askAllow(h) }
                }
                v != null && v.allowed -> {
                    state.setText(R.string.waris_state_allowed)
                    state.setTextColor(ContextCompat.getColor(this, R.color.red_gave_text))
                    first.setText(R.string.waris_refuse)
                    first.setOnClickListener { refuse(h) }
                }
                else -> {
                    state.setText(R.string.waris_state_linked)
                    state.setTextColor(ContextCompat.getColor(this, R.color.brand_green_text))
                    row.findViewById<View>(R.id.heirActions).visibility = View.GONE
                }
            }
            row.findViewById<View>(R.id.btnHeirRemove).setOnClickListener { askRemove(h) }
            TextFit.relax(row)
            heirsBox.addView(row)
        }
    }

    private fun paintLinks(links: List<Waris.Link>) {
        linksBox.removeAllViews()
        findViewById<View>(R.id.tvWarisLinksEmpty).visibility = if (links.isEmpty()) View.VISIBLE else View.GONE
        val now = System.currentTimeMillis()
        val inflater = LayoutInflater.from(this)
        for (l in links) {
            val row = inflater.inflate(R.layout.item_waris_link, linksBox, false)
            row.findViewById<TextView>(R.id.tvLinkShop).text = l.shop.ifBlank { getString(R.string.app_name) }
            val state = row.findViewById<TextView>(R.id.tvLinkState)
            val main = row.findViewById<MaterialButton>(R.id.btnLinkMain)
            val refusedUntil = l.retryAt?.takeIf { it > now && l.askedAt == null }
            when {
                l.open -> {
                    state.setText(R.string.waris_link_open)
                    main.setText(R.string.waris_open)
                    main.setOnClickListener { askOpen(l) }
                }
                !l.known -> {
                    state.setText(R.string.waris_link_waiting)
                    main.visibility = View.GONE
                }
                refusedUntil != null -> {
                    state.text = getString(R.string.waris_link_refused, Format.dateTime(refusedUntil))
                    main.visibility = View.GONE
                }
                l.askedAt != null -> {
                    val opens = l.openAt?.let { ((it - now + Waris.DAY_MS - 1) / Waris.DAY_MS).toInt().coerceAtLeast(0) }
                    state.text = listOfNotNull(
                        getString(R.string.waris_link_asked, Format.dateOnly(l.askedAt ?: now)),
                        opens?.let { Digits.quantity(resources, R.plurals.waris_opens_in, it, it) }
                    ).joinToString(" ")
                    // Past its day, a last word from the relays may not have come yet: Open tries.
                    if (opens == 0) { main.setText(R.string.waris_open); main.setOnClickListener { askOpen(l) } }
                    else main.visibility = View.GONE
                }
                else -> {
                    state.text = getString(R.string.waris_link_linked, days(l.wait))
                    main.setText(R.string.waris_ask)
                    main.setOnClickListener { askAsk(l) }
                }
            }
            row.findViewById<View>(R.id.btnLinkCode).apply {
                visibility = if (l.known) View.GONE else View.VISIBLE
                setOnClickListener { showHeirCode(l) }
            }
            row.findViewById<View>(R.id.btnLinkCard).setOnClickListener { saveCard(l) }
            row.findViewById<View>(R.id.btnLinkForget).setOnClickListener { askForget(l) }
            TextFit.relax(row)
            linksBox.addView(row)
        }
    }

    // ------------------------------------------------------------------ helpers

    /** [work] on the app's scope (never cut by leaving), then [done] here if the screen is still up. */
    private fun <T> act(work: suspend () -> T, done: (T) -> Unit) {
        if (busy) return
        busy = true
        Toast.makeText(this, R.string.waris_working, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = runCatching { AppScope.async { work() }.await() }
            busy = false
            if (isFinishing || isDestroyed) return@launch
            result.onSuccess(done).onFailure {
                android.util.Log.e("Waris", "failed", it)
                tell(getString(R.string.waris_open_failed))
            }
            render()
        }
    }

    private fun tell(message: String) {
        MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(R.string.ok, null).show()
    }

    private fun say(r: Waris.Result, ok: String) = tell(
        when (r) {
            is Waris.Result.Done -> if (r.partly) ok + "\n\n" + getString(R.string.waris_partly) else ok
            is Waris.Result.Failed -> whyWords(r.why)
        }
    )

    private fun whyWords(why: Waris.Why): String = getString(
        when (why) {
            Waris.Why.HALTED -> R.string.waris_halted
            Waris.Why.FULL -> R.string.waris_full
            Waris.Why.BUSY -> R.string.waris_busy_today
            Waris.Why.TOO_BIG -> R.string.waris_too_big
            Waris.Why.BAD_CODE -> R.string.waris_code_wrong
            Waris.Why.PASS -> R.string.waris_removed_you
            Waris.Why.WAITING -> R.string.waris_not_open_yet
            Waris.Why.NO_COPY -> R.string.waris_no_copy
            Waris.Why.OFFLINE -> R.string.waris_offline
            else -> R.string.waris_open_failed
        }
    )

    private fun confirm(title: String?, body: String, yes: String, go: () -> Unit) {
        MaterialAlertDialogBuilder(this).apply { title?.let { setTitle(it) } }
            .setMessage(body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(yes) { _, _ -> go() }
            .show()
    }

    private fun scan(then: (String?) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { then(it.rawValue) }
            .addOnFailureListener { Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_LONG).show() }
    }

    /** A code full screen, bright, with its steps and (when given) one main button. */
    private fun showCode(title: String, steps: String, payload: String, mainLabel: String?, onMain: (() -> Unit)?) {
        codeDialog?.dismiss()
        val d = AppCompatDialog(this, R.style.Theme_RoshanKhata)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_waris_qr, null)
        v.findViewById<TextView>(R.id.tvQrTitle).text = title
        v.findViewById<TextView>(R.id.tvQrSteps).text = steps
        v.findViewById<ImageView>(R.id.ivQrCode).setImageBitmap(QrImage.of(payload, 900))
        v.findViewById<MaterialButton>(R.id.btnQrMain).apply {
            if (mainLabel == null) visibility = View.GONE
            else { text = mainLabel; setOnClickListener { onMain?.invoke() } }
        }
        v.findViewById<View>(R.id.btnQrClose).setOnClickListener { d.dismiss() }
        TextFit.relax(v)
        d.setContentView(v)
        d.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            // A code on a dim screen does not scan.
            attributes = attributes.apply { screenBrightness = 1f }
        }
        d.setOnDismissListener { if (codeDialog === d) codeDialog = null }
        codeDialog = d
        d.show()
    }

    // ------------------------------------------------------------------ the owner's part

    private fun askTurnOn() = confirm(getString(R.string.waris_title), getString(R.string.waris_turn_on_body), getString(R.string.waris_turn_on)) {
        act({ WarisAndroid.owner(applicationContext).enable() }) { r ->
            if (r is Waris.Result.Done) {
                // An heir's request is told by a notification: on Android 13+ only with the app's permission (W6).
                askNotifications()
                if (r.partly) tell(getString(R.string.waris_partly))
            } else say(r, "")
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) androidx.core.app.ActivityCompat.requestPermissions(
            this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 315
        )
    }

    private fun addHeir() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { WarisAndroid.owner(applicationContext).heirs().size }.getOrDefault(0) }
            if (count >= Waris.MAX_HEIRS) { tell(Digits.string(resources, R.string.waris_max_heirs, Waris.MAX_HEIRS)); return@launch }
            val box = FormBox.style(EditText(this@WarisActivity).apply {
                hint = getString(R.string.waris_heir_name)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                maxLines = 1
            })
            val body = FormBox.column(this@WarisActivity, box)
            TextFit.relax(body)
            MaterialAlertDialogBuilder(this@WarisActivity)
                .setTitle(R.string.waris_add_heir)
                .setView(body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val name = box.text.toString().trim()
                    if (name.isNotEmpty()) pickWait(name)
                }
                .show()
        }
    }

    private fun pickWait(name: String) {
        val choices = Waris.WAITS.map { w ->
            ChoiceSheet.date(days(w), if (w == Waris.DEFAULT_WAIT) getString(R.string.waris_recommended) else null)
        }
        ChoiceSheet.show(
            this, getString(R.string.waris_wait_title), choices, Waris.WAITS.indexOf(Waris.DEFAULT_WAIT),
            message = getString(R.string.waris_wait_help)
        ) { which ->
            val shop = WarisAndroid.shopName(this)
            act({ WarisAndroid.owner(applicationContext).startHeir(name, Waris.WAITS[which], shop) }) { made ->
                made?.let { (h, _) -> showOwnerCode(h) } ?: tell(Digits.string(resources, R.string.waris_max_heirs, Waris.MAX_HEIRS))
            }
        }
    }

    /** QR1 for an heir not yet linked, with the plain steps and the button that scans their code back. */
    private fun showOwnerCode(h: Waris.HeirEntry) {
        lifecycleScope.launch {
            val code = withContext(Dispatchers.IO) {
                runCatching { WarisAndroid.owner(applicationContext).ownerCodeOf(h.hid, WarisAndroid.shopName(applicationContext)) }.getOrNull()
            } ?: return@launch
            if (isFinishing || isDestroyed) return@launch
            val name = Format.isolate(h.name)
            showCode(
                getString(R.string.waris_show_code_title, name),
                getString(R.string.waris_show_code_steps, name, getString(R.string.app_name), getString(R.string.waris_title)),
                code, getString(R.string.waris_scan_heir, name)
            ) { scan { raw -> finishHeir(h, raw) } }
        }
    }

    private fun finishHeir(h: Waris.HeirEntry, raw: String?) {
        if (WarisCryptoCheck.isHeirCode(raw) == false) { tell(getString(R.string.waris_code_wrong)); return }
        val app = applicationContext
        act({ WarisAndroid.owner(app).finishHeir(h.hid, raw, WarisAndroid.book(app)) }) { r ->
            if (r is Waris.Result.Done) {
                codeDialog?.dismiss()
                say(r, getString(R.string.waris_heir_added, Format.isolate(h.name)))
            } else say(r, "")
        }
    }

    private fun updateCopy() {
        val app = applicationContext
        act({ WarisAndroid.owner(app).snapshot(WarisAndroid.book(app), force = true) }) { r -> say(r, getString(R.string.waris_copy_sent)) }
    }

    private fun askRemove(h: Waris.HeirEntry) = confirm(
        getString(R.string.waris_remove_heir), getString(R.string.waris_remove_heir_body, Format.isolate(h.name)), getString(R.string.waris_remove_heir)
    ) {
        val app = applicationContext
        act({ WarisAndroid.owner(app).removeHeir(h.hid, WarisAndroid.book(app)) }) { r -> if (r is Waris.Result.Failed) say(r, "") }
    }

    private fun refuse(h: Waris.HeirEntry) {
        act({ WarisAndroid.refuse(applicationContext, h.hid) }) { r ->
            // Not on every relay yet: said as such; the app keeps asking them in the background (W6).
            if (r is Waris.Result.Done && r.partly) tell(getString(R.string.waris_refusal_partial))
            else say(r, getString(R.string.waris_refused))
        }
    }

    private fun askAllow(h: Waris.HeirEntry) = confirm(
        getString(R.string.waris_allow_now), getString(R.string.waris_allow_body, Format.isolate(h.name)), getString(R.string.waris_allow_now)
    ) {
        act({ WarisAndroid.owner(applicationContext).allow(h.hid) }) { r -> say(r, getString(R.string.waris_allowed)) }
    }

    private fun askTurnOff() = confirm(getString(R.string.waris_turn_off), getString(R.string.waris_turn_off_body), getString(R.string.waris_turn_off)) {
        act({ WarisAndroid.owner(applicationContext).turnOff() }) { r ->
            if (r is Waris.Result.Done && r.partly) tell(getString(R.string.waris_partly))
        }
    }

    // ------------------------------------------------------------------ the heir's part

    private fun takeOwnerCode(raw: String?) {
        act({ WarisAndroid.heir(applicationContext).take(raw) }) { taken ->
            when {
                taken == null -> tell(getString(R.string.waris_not_code))
                taken.second == null -> tell(getString(R.string.waris_card_read, Format.isolate(taken.first.shop)))
                else -> showHeirCode(taken.first, taken.second)
            }
        }
    }

    private fun showHeirCode(l: Waris.Link, code: String? = null) {
        val qr = code ?: runCatching { WarisAndroid.heir(applicationContext).heirCode(l) }.getOrNull() ?: return
        showCode(getString(R.string.waris_my_code_title), getString(R.string.waris_my_code_steps, Format.isolate(l.shop)), qr, null, null)
    }

    private fun askAsk(l: Waris.Link) = confirm(
        getString(R.string.waris_ask), getString(R.string.waris_ask_body, Format.isolate(l.shop)), getString(R.string.waris_ask)
    ) {
        act({ WarisAndroid.heir(applicationContext).ask(l).second }) { r ->
            // `at` comes from :shared — no smart cast across modules, so it is read once into a local.
            val retryAt = (r as? Waris.Result.Failed)?.takeIf { it.why == Waris.Why.WAIT }?.at
            if (retryAt != null) tell(getString(R.string.waris_link_refused, Format.dateTime(retryAt)))
            else say(r, getString(R.string.waris_asked_sent))
        }
    }

    private fun askOpen(l: Waris.Link) = confirm(
        getString(R.string.waris_open), getString(R.string.waris_open_body, Format.isolate(l.shop), getString(R.string.viewer_menu)), getString(R.string.waris_open)
    ) {
        act({ WarisAndroid.openHeirCopy(this, l) }) { r ->
            when (r) {
                is WarisAndroid.HeirCopy.NotOpened -> tell(whyWords(r.failed.why))
                is WarisAndroid.HeirCopy.Loaded -> when (r.outcome) {
                    is ViewerSync.Outcome.Loaded, is ViewerSync.Outcome.LoadedWithoutPhotos ->
                        // The open book changed: every screen goes, and the app starts again through the gate (App Lock).
                        startActivity(Intent(this, GateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    else -> tell(getString(R.string.waris_open_failed))
                }
            }
        }
    }

    private fun askForget(l: Waris.Link) = confirm(
        getString(R.string.waris_forget), getString(R.string.waris_forget_body, Format.isolate(l.shop)), getString(R.string.waris_forget)
    ) {
        act({ WarisAndroid.heir(applicationContext).forget(l) }) { }
    }

    /**
     * The Waris card: what it is said first, in the words printed on it (W7: the card holds the heir's keys, so
     * whoever holds it, or a photo of it, can ask in the heir's name and open the book if the owner does not refuse
     * in time), then the picture.
     */
    private fun saveCard(l: Waris.Link) = confirm(
        getString(R.string.waris_save_card),
        getString(R.string.waris_card_steps, getString(R.string.app_name), getString(R.string.waris_title)),
        getString(R.string.waris_save_card)
    ) { shareCardOf(l) }

    /**
     * The Waris card as a picture: its heading, the card's code and the short steps, black on white for print.
     * Shared through the cache's receipts/ folder (declared in file_paths.xml), and deleted as soon as the share
     * comes back ([shareCard]) or, failing that, when this screen next opens.
     */
    private fun shareCardOf(l: Waris.Link) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val payload = WarisAndroid.heir(applicationContext).card(l)
                    val w = 1080
                    val qr = QrImage.of(payload, 900)
                    val heading = getString(R.string.waris_card_heading, l.shop)
                    val steps = getString(R.string.waris_card_steps, getString(R.string.app_name), getString(R.string.waris_title))
                    val headPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 54f; isFakeBoldText = true }
                    val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 38f }
                    fun layout(text: String, p: TextPaint) =
                        StaticLayout.Builder.obtain(text, 0, text.length, p, w - 120).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                    val head = layout(heading, headPaint)
                    val body = layout(steps, bodyPaint)
                    val h = 60 + head.height + 40 + qr.height + 40 + body.height + 60
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    c.drawColor(Color.WHITE)
                    c.save(); c.translate(60f, 60f); head.draw(c); c.restore()
                    c.drawBitmap(qr, ((w - qr.width) / 2).toFloat(), (60 + head.height + 40).toFloat(), null)
                    c.save(); c.translate(60f, (60 + head.height + 40 + qr.height + 40).toFloat()); body.draw(c); c.restore()
                    val f = cardFile().apply { parentFile?.mkdirs() }
                    java.io.FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    f
                }.getOrNull()
            }
            if (file == null || isFinishing || isDestroyed) {
                if (file == null) Toast.makeText(this@WarisActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                else withContext(Dispatchers.IO) { runCatching { file.delete() } }
                return@launch
            }
            val uri = FileProvider.getUriForFile(this@WarisActivity, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareCard.launch(Intent.createChooser(share, getString(R.string.waris_save_card)))
        }
    }

    private companion object {
        /** The card's picture in the cache's receipts/ folder; never left there (W7). */
        const val CARD_FILE = "waris_card.png"
    }
}

/** A scanned text that is plainly not an heir's code is said at once, without a trip to the relays. */
private object WarisCryptoCheck {
    fun isHeirCode(raw: String?): Boolean? = raw?.trim()?.startsWith(com.innovation313.roshankhata.data.WarisCrypto.PREFIX_HEIR)
}
