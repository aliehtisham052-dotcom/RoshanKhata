package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.Digits

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Backup
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.reason
import com.innovation313.roshankhata.data.BackupImages
import com.innovation313.roshankhata.data.BackupReminder
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.DriveAuth
import com.innovation313.roshankhata.data.DriveBackup
import com.innovation313.roshankhata.data.DriveFeature
import com.innovation313.roshankhata.data.EraseAll
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.WifiSync
import com.innovation313.roshankhata.data.LiveRound
import com.innovation313.roshankhata.data.LiveRunner
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.LiveWire
import com.innovation313.roshankhata.data.syncSql
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backup and restore.
 *
 * The app holds everything on one phone. That is a real risk, and this screen
 * says so plainly rather than leaving the owner to discover it the day the
 * phone breaks.
 */
class BackupActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) beginRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        // The file-backup fold. Closed by default now that Drive is the main
        // path; one tap opens it. The chevron points right when closed and
        // down when open, so the state is readable without tapping.
        val fileHeader = findViewById<View>(R.id.fileSectionHeader)
        val fileBody = findViewById<View>(R.id.fileSectionBody)
        val fileChevron = findViewById<android.widget.ImageView>(R.id.ivFileSectionChevron)
        fileChevron.rotation = 0f
        fileHeader.setOnClickListener {
            val open = fileBody.visibility == View.VISIBLE
            fileBody.visibility = if (open) View.GONE else View.VISIBLE
            fileChevron.rotation = if (open) 0f else 90f
        }

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        findViewById<MaterialButton>(R.id.btnBackup).setOnClickListener { createBackup() }

        findViewById<MaterialButton>(R.id.btnRestore).setOnClickListener {
            showRestoreOptions()
        }

        // The cloud-backup section stays hidden until the app is verified by
        // Google for the Drive scope. Until then, showing "Connect" would only
        // lead a real user into Google's "unverified app" warning. The whole
        // section is gated on one flag; when it is off, we neither show it nor
        // wire any of its buttons.
        // Say whose book this screen is about, before anything is backed up or
        // replaced. Only worth saying when there is more than one shop.
        val businesses = Businesses.list(this)
        if (businesses.size > 1) {
            val open = Businesses.active(this)
            val shop = Businesses.displayName(this, open)
                ?: Digits.string(resources, R.string.business_numbered, open.id)
            findViewById<android.widget.TextView>(R.id.tvBackupBusiness).apply {
                text = getString(R.string.backup_for_business, shop)
                visibility = android.view.View.VISIBLE
            }
        }

        findViewById<MaterialButton>(R.id.btnDailyCopies).setOnClickListener {
            startActivity(Intent(this, SnapshotsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnEraseAll).setOnClickListener { confirmEraseAll() }

        findViewById<MaterialButton>(R.id.btnSendToComputer).setOnClickListener { scanComputer(take = false) }
        findViewById<MaterialButton>(R.id.btnTakeFromComputer).setOnClickListener { scanComputer(take = true) }

        // Live sync with the computer (22 Oct 2026): join once, then the same book on both.
        // Joining gives a device the whole book: the owner's PIN first, whoever is signed in (22 Oct 2026).
        findViewById<MaterialButton>(R.id.btnLiveJoin).setOnClickListener {
            com.innovation313.roshankhata.ui.AccessUi.ownerFirst(this, dao, getString(R.string.live_needs_owner)) { scanLive() }
        }
        findViewById<MaterialButton>(R.id.btnLiveStop).setOnClickListener { confirmLiveStop() }
        // The relay on the internet: the owner's choice for the book (one switch for both devices), off until then.
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLiveRelay).setOnCheckedChangeListener { b, on ->
            if (showingRelay) return@setOnCheckedChangeListener
            if (on) {
                // Not on until the owner says so (the book's data would leave the shop's WiFi, sealed).
                showingRelay = true; b.isChecked = false; showingRelay = false
                com.innovation313.roshankhata.ui.AccessUi.ownerFirst(this, dao, getString(R.string.live_relay)) { setRelay(true) }
            } else setRelay(false)
        }
        lifecycleScope.launch { LiveRunner.status.collect { showLive(it) } }

        if (DriveFeature.ENABLED) {
            findViewById<android.view.View>(R.id.driveSection).visibility = android.view.View.VISIBLE

            findViewById<MaterialButton>(R.id.btnDriveConnect).setOnClickListener {
                connectDrive()
            }
            findViewById<MaterialButton>(R.id.btnDriveBackupNow).setOnClickListener { driveBackup() }
            findViewById<MaterialButton>(R.id.btnDriveRestore).setOnClickListener { confirmDriveRestore() }
            findViewById<MaterialButton>(R.id.btnDriveSignOut).setOnClickListener { driveSignOut() }

            // Include-images toggle: reflect the saved choice, and remember any
            // change. Plain switch, no warning — the owner asked for exactly a
            // toggle and nothing more.
            val imagesSwitch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(
                R.id.switchBackupImages
            )
            imagesSwitch.isChecked = DriveBackup.includeImages(this)
            imagesSwitch.setOnCheckedChangeListener { _, checked ->
                DriveBackup.setIncludeImages(this, checked)
            }

            // Automatic daily backup toggle: reflect the saved choice and
            // remember any change. When on, the daily worker backs up on its
            // own once a day if connected and something changed.
            val autoSwitch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(
                R.id.switchAutoBackup
            )
            autoSwitch.isChecked = DriveBackup.autoBackup(this)
            autoSwitch.setOnCheckedChangeListener { _, checked ->
                DriveBackup.setAutoBackup(this, checked)
            }

            refreshDriveUi()

            // Home's "Continue with Google" chip, its one-time offer and the
            // Khata screen's cloud button (10 Oct) open this screen straight
            // into the account picker, so connecting is one tap from the front
            // rather than a screen to find and a button to look for. Not after
            // a rotation, and never once connected.
            if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_CONNECT, false) &&
                !DriveAuth.isConnected(this)
            ) {
                window.decorView.post { if (!isFinishing && !isDestroyed) connectDrive() }
            }
        }
    }

    companion object {
        /** Open straight into "Continue with Google" when not connected yet. */
        const val EXTRA_CONNECT = "connect_google"

        /** The Backup screen, opening on the Google account picker if no account is connected. */
        fun connectIntent(context: android.content.Context): Intent =
            Intent(context, BackupActivity::class.java)
                .putExtra(EXTRA_CONNECT, !DriveAuth.isConnected(context))
    }

    /**
     * Save the backup in three places, because the owner could not find it in one.
     *
     * The file used to be written only to the cache directory and handed
     * straight to a share sheet. Two things went wrong with that, and both of
     * them cost the owner their backup:
     *
     *   - Android empties the cache whenever it wants space. The single file
     *     standing between a shopkeeper and the loss of their whole ledger
     *     could vanish with nobody touching it.
     *   - It was declared as application/json, which WhatsApp will not send and
     *     most file managers hide. The owner watched the share sheet succeed
     *     and then could not find the file anywhere.
     *
     * So now: Downloads (visible, permanent, no permission needed), an internal
     * copy the app can always restore from without the owner hunting for
     * anything, and only then the share sheet as an extra.
     */
    private fun createBackup() {
        Toast.makeText(this, R.string.creating_backup, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Streamed to one cache file, then copied (P6, 7 Oct): the
                // book is never held in memory as a string, however big.
                val shareFile = try { Backup.exportToCache(this@BackupActivity, dao) } catch (e: Exception) { null }
                val downloadPath = shareFile?.let { Backup.saveToDownloads(this@BackupActivity, it) }
                shareFile?.let { Backup.writeInternalCopy(this@BackupActivity, it) }

                Pair(downloadPath, shareFile)
            }

            val (downloadPath, shareFile) = result

            if (downloadPath == null && shareFile == null) {
                Toast.makeText(this@BackupActivity, R.string.backup_failed, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            // A successful backup clears the home-screen reminder.
            com.innovation313.roshankhata.data.BackupReminder.recordBackup(this@BackupActivity)

            // Tell the owner exactly where it went. "Backup created" is useless
            // if they cannot then find the thing.
            MaterialAlertDialogBuilder(this@BackupActivity)
                .setTitle(R.string.backup_done_title)
                .setMessage(
                    if (downloadPath != null) {
                        getString(R.string.backup_done_saved, downloadPath)
                    } else {
                        getString(R.string.backup_done_internal)
                    }
                )
                .setNegativeButton(R.string.ok, null)
                .setPositiveButton(R.string.share_copy) { _, _ ->
                    if (shareFile != null) shareBackup(shareFile)
                }
                .show()
        }
    }

    /**
     * To the computer over WiFi (20 Oct): scan the code the Windows app shows
     * (its window "From phone"), then send this book there, sealed with the
     * code's secret (data/WifiSync). The scanner is Play services' own, as
     * for customer cards, so the app still asks for no camera permission.
     * Cancelling the scanner says nothing.
     */
    private fun scanComputer(take: Boolean) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val pairing = WifiSync.parsePairing(barcode.rawValue)
                if (pairing == null) {
                    Toast.makeText(this, R.string.wifi_send_not_computer, Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                if (take) takeFromComputer(pairing) else sendToComputer(pairing)
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun sendToComputer(pairing: WifiSync.Pairing) {
        Toast.makeText(this, R.string.wifi_send_sending, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                // The same streamed backup file every other path uses (P6), then its bytes.
                val file = try { Backup.exportToCache(this@BackupActivity, dao) } catch (e: Exception) { null }
                    ?: return@withContext null
                if (file.length() > WifiSync.MAX_BYTES) return@withContext WifiSync.Outcome.Refused(WifiSync.TOO_BIG)
                try { WifiSync.send(pairing, file.readBytes()) } catch (e: Exception) { WifiSync.Outcome.Unreachable }
            }
            if (isFinishing || isDestroyed) return@launch
            val message = when (outcome) {
                null -> getString(R.string.backup_failed)
                is WifiSync.Outcome.Sent -> getString(R.string.wifi_send_done)
                is WifiSync.Outcome.Refused -> getString(R.string.wifi_send_refused)
                WifiSync.Outcome.Unreachable -> getString(R.string.wifi_send_unreachable)
            }
            MaterialAlertDialogBuilder(this@BackupActivity)
                .setTitle(R.string.wifi_send_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    /**
     * The computer's book onto this phone (20 Oct): asked for over the same
     * pairing, then through the same parse-and-confirm as a chosen file, so
     * nothing here is replaced before the owner has read what is in it.
     */
    private fun takeFromComputer(pairing: WifiSync.Pairing) {
        Toast.makeText(this, R.string.wifi_take_asking, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val pulled = withContext(Dispatchers.IO) {
                try { WifiSync.pull(pairing) } catch (e: Exception) { WifiSync.Pulled.Unreachable }
            }
            if (isFinishing || isDestroyed) return@launch
            when (pulled) {
                is WifiSync.Pulled.Got -> {
                    val file = withContext(Dispatchers.IO) {
                        File(File(cacheDir, "backups").apply { mkdirs() }, "from_computer.txt").also { it.writeBytes(pulled.book) }
                    }
                    val (result, data) = withContext(Dispatchers.IO) { BackupCodec.parseFile(file) }
                    handleParseResult(result, data)
                }
                WifiSync.Pulled.Refused -> tell(R.string.wifi_take_refused)
                WifiSync.Pulled.Unreachable -> tell(R.string.wifi_send_unreachable)
            }
        }
    }

    /** What live sync is doing, in a line; and whether it may be turned off. */
    private fun showLive(s: LiveRunner.Status) {
        val bidi = androidx.core.text.BidiFormatter.getInstance()
        val text = when (s) {
            LiveRunner.Status.Off -> getString(R.string.live_status_off)
            LiveRunner.Status.NotPaired -> getString(R.string.live_status_not_paired)
            LiveRunner.Status.Waiting -> getString(R.string.live_status_waiting)
            LiveRunner.Status.NotHere -> getString(R.string.live_status_not_here)
            is LiveRunner.Status.Live -> getString(
                if (s.relay) R.string.live_status_relay else R.string.live_status_live,
                // The computer's name and the time kept whole in a right-to-left sentence (lesson 30).
                bidi.unicodeWrap(s.computer?.takeIf { it.isNotBlank() } ?: getString(R.string.live_title)),
                bidi.unicodeWrap(Digits.toLatin(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(s.at))))
            )
            LiveRunner.Status.Unreachable -> getString(R.string.live_status_unreachable)
            LiveRunner.Status.Halted -> getString(R.string.live_status_halted)
            LiveRunner.Status.NotAccepted -> getString(R.string.live_status_not_accepted)
            LiveRunner.Status.Rejoin -> getString(R.string.live_status_rejoin)
            is LiveRunner.Status.Failed -> getString(R.string.live_status_failed, bidi.unicodeWrap(s.why))
        }
        findViewById<android.widget.TextView>(R.id.tvLiveStatus).text =
            if (LiveRunner.mismatch.value) text + "\n" + getString(R.string.live_mismatch) else text
        val off = s == LiveRunner.Status.Off || s == LiveRunner.Status.Rejoin || s == LiveRunner.Status.NotAccepted
        findViewById<MaterialButton>(R.id.btnLiveStop).visibility = if (off) android.view.View.GONE else android.view.View.VISIBLE
        val relay = if (off) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.switchLiveRelay).visibility = relay
        findViewById<android.view.View>(R.id.tvLiveRelayHint).visibility = relay
        if (!off) lifecycleScope.launch {
            val on = withContext(Dispatchers.IO) { runCatching { dao.settingValue(com.innovation313.roshankhata.data.BookSettings.LIVE_RELAY) == "1" }.getOrDefault(false) }
            showRelay(on)
        }
    }

    /** The relay switch set from the book, without asking again. */
    private var showingRelay = false

    private fun showRelay(on: Boolean) {
        val sw = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLiveRelay)
        if (sw.isChecked == on) return
        showingRelay = true
        sw.isChecked = on
        showingRelay = false
    }

    private fun setRelay(on: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { dao.setSetting(com.innovation313.roshankhata.data.BookSettings.LIVE_RELAY, if (on) "1" else null) }
            }
            showRelay(on)
        }
    }

    /** The computer's live code (More → Live sync on Windows), then this book sent there to join. */
    private fun scanLive() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val invite = LiveWire.parseInvite(barcode.rawValue)
                if (invite == null) {
                    Toast.makeText(this, R.string.live_not_live_code, Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                joinComputer(invite)
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Turns live sync on for this book (if it was not), and sends the book
     * with its sync identity to the computer. The computer shows its usual
     * restore confirmation; once the owner says yes there, the rounds begin.
     *
     * Until that yes, nothing this phone knew changes (22 Oct 2026, the
     * review): a computer it already synced with keeps its place, a book
     * that did not sync before goes back to not syncing when the book never
     * reached the computer, and the whole pairing runs on the app's own
     * scope, so turning the phone or pressing Back cannot leave it half made.
     */
    private fun joinComputer(invite: LiveWire.Invite) {
        Toast.makeText(this, R.string.wifi_send_sending, Toast.LENGTH_SHORT).show()
        val appContext = applicationContext
        lifecycleScope.launch {
            val sent = com.innovation313.roshankhata.data.AppScope.async { pairWith(appContext, invite) }.await()
            if (isFinishing || isDestroyed) return@launch
            val message = when (sent) {
                null -> getString(R.string.backup_failed)
                is LiveWire.Sent.Taken -> getString(R.string.live_sent)
                LiveWire.Sent.Refused -> getString(R.string.wifi_send_refused)
                LiveWire.Sent.Unreachable -> getString(R.string.wifi_send_unreachable)
            }
            MaterialAlertDialogBuilder(this@BackupActivity)
                .setTitle(R.string.live_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    /** The pairing itself; null when the book could not be made into a copy to send. */
    private suspend fun pairWith(context: android.content.Context, invite: LiveWire.Invite): LiveWire.Sent? {
        val sql = KhataDatabase.get(context).syncSql()
        val wasOn = LiveSync.identity(sql) != null
        val sent: LiveWire.Sent? = try {
            val id = LiveSync.enable(sql, LiveRunner.install(context), LiveRunner.deviceName(), marks = LiveRunner.marks(context))
            val joinerMark = LiveSync.nextJoinerMark(sql)
            // The book is taken after this line: whatever changes after it is sent again
            // in the first rounds, and a row that lands twice lands as one.
            val seq = LiveSync.lastSeq(sql)
            val file = Backup.exportToCache(context, KhataDatabase.get(context).khataDao())
            if (file.length() > LiveWire.MAX_BYTES) LiveWire.Sent.Refused
            else {
                val pair = LiveSync.newHex(8)
                val outcome = LiveWire.sendOffer(invite, LiveWire.Offer(id.book, id.key, id.device, id.name, seq, file.readBytes(), joinerMark, pair))
                if (outcome is LiveWire.Sent.Taken) {
                    // The computer has the book; its owner has not said yes yet. The first round it answers
                    // makes it this phone's computer (LiveRound.take); until then any earlier one stays.
                    val hosts = listOf(outcome.host) + invite.hosts.filter { it != outcome.host }
                    LiveRound.setPending(sql, LiveRound.Target(hosts, invite.port, seq, invite.computer, pair = pair), wasOff = !wasOn)
                }
                outcome
            }
        } catch (e: Exception) {
            null
        }
        // Nothing reached the computer, and this phone did not sync before: as it was.
        if (sent !is LiveWire.Sent.Taken && !wasOn) runCatching { LiveSync.disable(sql) }
        com.innovation313.roshankhata.data.NumberMark.mark = runCatching { LiveSync.mark(sql) }.getOrNull()
        return sent
    }

    private fun confirmLiveStop() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.live_title)
            .setMessage(R.string.live_stop_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.live_stop) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val sql = KhataDatabase.get(this@BackupActivity).syncSql()
                            LiveRound.setTarget(sql, null)
                            LiveSync.disable(sql)
                            com.innovation313.roshankhata.data.NumberMark.mark = null
                        }
                    }
                    showLive(LiveRunner.Status.Off)
                }
            }
            .show()
    }

    private fun tell(message: Int) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.wifi_send_title).setMessage(message).setPositiveButton(R.string.ok, null).show()
    }

    private fun shareBackup(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )

        // text/plain, not application/json. WhatsApp refuses to send an unknown
        // type, and that refusal is why the owner's backup never arrived. It is
        // text; declaring it as text lets every app handle it.
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(share, getString(R.string.save_backup)))
    }

    private fun beginRestore(uri: Uri) {
        Toast.makeText(this, R.string.reading_backup, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            // Parse and validate BEFORE anything is touched. Wiping first and
            // finding out afterwards that the file was rubbish would destroy
            // the owner's books to import nothing.
            val (result, data) = withContext(Dispatchers.IO) {
                Backup.parse(this@BackupActivity, uri)
            }

            handleParseResult(result, data)
        }
    }

    /**
     * Shared by both restore routes — the file picker and the app's own saved
     * copies. Neither can skip the validation the other performs.
     */
    private fun handleParseResult(
        result: BackupCodec.ImportResult,
        data: BackupCodec.ParsedBackup?,
        driveAccount: String? = null
    ) {
        when (result) {
            is BackupCodec.ImportResult.Failed -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.restore_from_file)
                    .setMessage(getString(R.string.restore_failed, result.reason(this)))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }

            is BackupCodec.ImportResult.Ok -> {
                if (data == null) return
                confirmRestore(result, data, driveAccount)
            }
        }
    }

    /**
     * Restore is destructive and irreversible. The dialog says exactly what is
     * in the file, exactly what will be lost, and tells the owner to back up
     * the current state first if they have anything here that is not in the
     * file. No amount of convenience justifies hiding that.
     */
    private fun confirmRestore(
        counts: BackupCodec.ImportResult.Ok,
        data: BackupCodec.ParsedBackup,
        driveAccount: String? = null
    ) {
        // What the restore would take away, worked out before the question is
        // asked (21 Oct 2026): a phone's newer work must never vanish under an
        // older copy without the owner being told, in figures.
        lifecycleScope.launch {
            val loss = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.RestoreCheck.of(dao, data) }.getOrNull()
                // A count that could not be made is said, and ticked, never read as "nothing lost".
                ?: com.innovation313.roshankhata.data.RestoreCheck.UNKNOWN }
            val live = withContext(Dispatchers.IO) { Backup.liveOn(this@BackupActivity) }
            if (!isFinishing) askRestore(counts, data, driveAccount, loss, live)
        }
    }

    private fun askRestore(
        counts: BackupCodec.ImportResult.Ok,
        data: BackupCodec.ParsedBackup,
        driveAccount: String?,
        loss: com.innovation313.roshankhata.data.RestoreCheck.Loss?,
        live: Boolean = false
    ) {
        val body = Digits.string(resources, R.string.restore_warning_message,
            counts.parties,
            counts.entries,
            counts.cheques,
            counts.cash,
            counts.plans,
            counts.bills,
            counts.invoices
        )

        // The wrong-shop guard. A backup now says whose book it is; if that
        // name and the open shop's name both exist and differ, the owner is
        // told in the same breath as the counts — before anything happens,
        // not after. Told, never blocked: restoring another shop's book into
        // this one is how a book is deliberately moved, so the act stays
        // possible and only the accident is caught.
        val openShop = BusinessProfile.businessName(this)
        val mismatch = data.businessName != null && openShop != null &&
            !data.businessName.equals(openShop, ignoreCase = true)
        val lost = loss?.takeIf { !it.none }?.let { l ->
            getString(R.string.restore_loss_intro) + "\n" +
                com.innovation313.roshankhata.data.RestoreCheck.lines(l, com.innovation313.roshankhata.data.AndroidWords(this)) { Format.dateTime(it) }
                    .joinToString("\n") { "• $it" } +
                "\n\n" + getString(R.string.restore_loss_kept)
        }
        val message = listOfNotNull(
            lost,
            if (mismatch) getString(R.string.restore_business_mismatch, data.businessName, openShop) else null,
            // A replaced book is no longer the computer's: live sync stops with it (22 Oct 2026, the review).
            if (live) getString(R.string.restore_stops_live) else null,
            body
        ).joinToString("\n\n")

        val b = MaterialAlertDialogBuilder(this)
            .setTitle(if (lost != null) R.string.restore_loss_title else R.string.restore_warning_title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore_replace) { _, _ -> doRestore(data, driveAccount) }
            // Or keep this book and add only what the copy has that it lacks (21 Oct 2026).
            .setNeutralButton(R.string.merge_button) { _, _ -> askMerge(data) }
        if (lost == null) { b.show(); return }
        // Work would be lost: the owner ticks that they understand before Replace works.
        val agree = android.widget.CheckBox(this).apply { setText(R.string.restore_loss_agree) }
        val pad = (20 * resources.displayMetrics.density).toInt()
        b.setView(android.widget.FrameLayout(this).apply { setPadding(pad, 0, pad, 0); addView(agree) })
        val dialog = b.show()
        val replace = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        replace.isEnabled = false
        agree.setOnCheckedChangeListener { _, on -> replace.isEnabled = on }
    }

    /**
     * A copy merged in instead of replacing the book (21 Oct 2026,
     * BookMerge): what it would add is said first, in figures; this book is
     * kept as a copy before anything is written; then only the rows it lacks
     * come in, in one transaction.
     */
    private fun askMerge(data: BackupCodec.ParsedBackup) {
        lifecycleScope.launch {
            val shop = BusinessProfile.businessName(this@BackupActivity)
            val plan = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.BookMerge.plan(dao, data, shop) } }
            if (isFinishing) return@launch
            val words = com.innovation313.roshankhata.data.AndroidWords(this@BackupActivity)
            val p = plan.getOrElse { e ->
                val msg = if (e is com.innovation313.roshankhata.data.BookMerge.Refused)
                    getString(com.innovation313.roshankhata.data.WordIds.string(com.innovation313.roshankhata.data.BookMerge.refusalWord(e)))
                else e.message ?: getString(R.string.backup_failed)
                MaterialAlertDialogBuilder(this@BackupActivity).setTitle(R.string.merge_title).setMessage(msg).setPositiveButton(android.R.string.ok, null).show()
                return@launch
            }
            if (p.none) {
                MaterialAlertDialogBuilder(this@BackupActivity).setTitle(R.string.merge_title).setMessage(R.string.merge_nothing).setPositiveButton(android.R.string.ok, null).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@BackupActivity)
                .setTitle(R.string.merge_title)
                .setMessage(getString(R.string.merge_help) + "\n\n" + com.innovation313.roshankhata.data.BookMerge.lines(p, words).joinToString("\n") { "• $it" })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.merge_button) { _, _ -> doMerge(data, shop) }
                .show()
        }
    }

    private fun doMerge(data: BackupCodec.ParsedBackup, shop: String?) {
        lifecycleScope.launch {
            // On the app's own scope (21 Oct 2026): turning the phone mid-merge cancelled the screen's
            // scope and rolled the merge back with nothing said. Only the message waits for the screen.
            val result = com.innovation313.roshankhata.data.AppScope.async {
                runCatching {
                    // This book as it stands, kept first (Daily copies), so the merge can be undone;
                    // nothing is merged unless it was.
                    if (!com.innovation313.roshankhata.data.Snapshots.keepNow(this@BackupActivity)) error(getString(R.string.restore_keep_failed))
                    dao.mergeBook(data, shop)
                }
            }.await()
            if (isFinishing) return@launch
            result.onSuccess { done ->
                Toast.makeText(this@BackupActivity, Digits.string(resources, R.string.merge_done, done.rows, done.renumbered), Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                Toast.makeText(this@BackupActivity, e.message ?: getString(R.string.backup_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun doRestore(data: BackupCodec.ParsedBackup, driveAccount: String? = null) {
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            // The restore itself on the app's own scope (21 Oct 2026): a screen turned mid-restore no
            // longer cancels it half way.
            val appContext = applicationContext
            val outcome = com.innovation313.roshankhata.data.AppScope.async {
                runCatching {
                    // This book as it stands, kept first (Daily copies), so the restore can be undone.
                    // No copy, no restore (21 Oct 2026).
                    com.innovation313.roshankhata.data.Snapshots.keepNow(appContext)
                        .also { if (it) Backup.restore(appContext, dao, data) }
                }
            }.await()
            // A restore that failed rolled back whole (22 Oct 2026, the review: it crashed the app and said nothing).
            if (outcome.isFailure) {
                if (!isFinishing) MaterialAlertDialogBuilder(this@BackupActivity)
                    .setTitle(R.string.restore_warning_title)
                    .setMessage(R.string.snapshot_restore_failed)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@launch
            }
            val kept = outcome.getOrDefault(false)
            if (!kept) {
                if (!isFinishing) MaterialAlertDialogBuilder(this@BackupActivity)
                    .setTitle(R.string.restore_warning_title)
                    .setMessage(R.string.restore_keep_failed)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@launch
            }

            // Images, only on the Drive route and only if an archive is there.
            // This runs AFTER the text restore on purpose: the bill-photo path
            // re-map reads and rewrites the very entries the text restore just
            // put back. A file restore (driveAccount == null) never carries
            // images, by the owner's design.
            var imagesFailed = false
            // Silence used to be the answer when no archive came back, and
            // silence reads as "everything came back". It does not: photos
            // simply were not in this backup. Say which of the two happened.
            var noImages = false
            if (driveAccount != null) {
                imagesFailed = withContext(Dispatchers.IO) {
                    val result = DriveBackup.restoreImages(this@BackupActivity, driveAccount)
                    val bytes = result.getOrNull()
                    when {
                        result.isFailure -> true
                        bytes == null -> { noImages = true; false }
                        else -> {
                            BackupImages.restore(this@BackupActivity, dao, bytes)
                            false
                        }
                    }
                }
            } else {
                noImages = true
            }

            if (imagesFailed) {
                Toast.makeText(
                    this@BackupActivity,
                    R.string.drive_images_restore_failed,
                    Toast.LENGTH_LONG
                ).show()
            }

            // A backup preserves EXACTLY what was in the ledger when it was
            // taken — including anyone who was already in the Recycle Bin at
            // that moment. Restored, they go back to the Recycle Bin, not the
            // home list. That is correct, but to the owner it looks like the
            // customer "did not come back" — so if any deleted rows were part of
            // this backup, say so plainly, and point to where they are.
            val deletedParties = data.parties.count { it.isDeleted }

            // A file backup carries records only — that is its design, not a
            // fault, so the wording differs from a Drive backup that simply
            // had no archive beside it.
            val imagesNote = when {
                !noImages -> null
                driveAccount == null -> getString(R.string.restore_file_no_images)
                else -> getString(R.string.restore_no_images)
            }

            if (deletedParties > 0 || imagesNote != null) {
                val body = listOfNotNull(
                    if (deletedParties > 0)
                        Digits.string(resources, R.string.restore_done_with_bin, deletedParties) else null,
                    imagesNote
                ).joinToString("\n\n")
                MaterialAlertDialogBuilder(this@BackupActivity)
                    .setTitle(R.string.restore_done)
                    .setMessage(body)
                    .setPositiveButton(R.string.ok) { _, _ -> goHome() }
                    .setCancelable(false)
                    .show()
            } else {
                Toast.makeText(
                    this@BackupActivity,
                    R.string.restore_done,
                    Toast.LENGTH_LONG
                ).show()
                goHome()
            }
        }
    }

    /**
     * The complete erase, guarded the same way a business delete is: the
     * dialog says exactly what goes and what stays (Drive backups stay —
     * they are the owner's own account and may be their only copy), and the
     * button stays disabled until the keyword is typed. See [EraseAll] for
     * what actually happens.
     */
    private fun confirmEraseAll() {
        val keyword = getString(R.string.erase_all_keyword)
        val view = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_business_delete, null)
        val tvSummary: android.widget.TextView = view.findViewById(R.id.tvDeleteSummary)
        val etConfirm: android.widget.EditText = view.findViewById(R.id.etDeleteConfirmName)
        tvSummary.text = getString(R.string.erase_all_body)
        etConfirm.hint = getString(R.string.business_delete_type_hint, keyword)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.erase_all_title)
            .setView(view)
            .setPositiveButton(R.string.delete_forever, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            positive.isEnabled = false
            positive.setOnClickListener {
                positive.isEnabled = false
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { EraseAll.wipe(this@BackupActivity) }
                    Toast.makeText(this@BackupActivity, R.string.erase_all_done, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    goHome()
                }
            }
            etConfirm.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    positive.isEnabled = EraseAll.confirms(s?.toString(), keyword)
                }
            })
        }
        dialog.show()
        com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
        etConfirm.requestFocus()
    }

    private fun goHome() {
        // CLEAR_TASK, not CLEAR_TOP — every screen dies, and with it every
        // cached DAO and every Flow still collecting on a database file that
        // no longer exists.
        //
        // All three callers get here after the ledger underneath the app has
        // been REPLACED: a file restore, a Drive restore, an erase-all. The
        // Drive route is the one that proved this: restoreAll switches
        // business on every round, and a switch closes one database file and
        // opens another. That is exactly what BusinessSwitchActivity already
        // clears the task for, and its comment says why — but restore is a
        // business switch too, and this path only cleared the activities
        // ABOVE Home. Home survived, still collecting observePartiesWithBalance
        // on the closed file, so it went on showing the zero it had read
        // before the restore while the ledger screen showed the real figures.
        // The owner saw Rs 0 on one screen and Rs 500 on the next.
        //
        // Nothing was wrong with the data; the screen was reading a book that
        // had been shut. Clearing the task means no screen can outlive the
        // database it was opened against.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
        )
        finish()
    }

    /**
     * Two ways back in, because hunting for a file is the step where people
     * give up.
     *
     * The app keeps its own recent backups, so the ordinary case — "restore
     * what I saved last week" — needs no file manager, no permissions, and no
     * searching. Picking a file from storage is still there for a backup that
     * came from another phone.
     */
    private fun showRestoreOptions() {
        val saved = Backup.internalBackups(this)

        if (saved.isEmpty()) {
            pickFromStorage()
            return
        }

        val options = listOf(
            ChoiceSheet.Option(getString(R.string.restore_from_app), icon = R.drawable.ic_tile_backup,
                iconTint = 0, tile = R.color.tile_backup_bg),
            ChoiceSheet.file(getString(R.string.restore_from_storage))
        )
        ChoiceSheet.show(this, getString(R.string.restore_from_file), options) { which ->
            when (which) {
                0 -> showSavedBackups(saved)
                1 -> pickFromStorage()
            }
        }
    }

    private fun showSavedBackups(files: List<File>) {
        val labels = files.map { f ->
            Digits.string(resources, R.string.backup_saved_at,
                Format.dateTime(f.lastModified()),
                (f.length() / 1024).coerceAtLeast(1)
            )
        }

        ChoiceSheet.show(this, getString(R.string.restore_from_app), labels.map { ChoiceSheet.file(it) }) { which ->
            restoreFromFile(files[which])
        }
    }

    private fun pickFromStorage() {
        // Accept anything. A backup is plain text, but file managers report it
        // as octet-stream, text/plain, or nothing at all depending on the
        // phone. Refusing the owner's own backup on a MIME technicality would
        // be maddening, and the parser validates the contents regardless.
        pickBackupFile.launch(arrayOf("*/*"))
    }

    private fun restoreFromFile(file: File) {
        Toast.makeText(this, R.string.reading_backup, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val (result, data) = withContext(Dispatchers.IO) {
                Backup.parseFile(file)
            }
            handleParseResult(result, data)
        }
    }

    // ---------- Google Drive backup ----------

    /**
     * Ask for Drive authorization. AuthorizationClient either already has the
     * grant (older sessions where the owner consented before) or hands back a
     * PendingIntent for the consent screen. The first case finishes here; the
     * second is launched and lands in [driveAuthorize] below.
     */
    private fun connectDrive() {
        // Two steps, in order: first a Sign in with Google to learn which
        // account this is (so the screen can show the email), then the Drive
        // authorization for that account. The sign-in gives the label; the
        // authorize() gives the actual permission. The owner sees the account
        // picker once, then the Drive consent.
        lifecycleScope.launch {
            // The real reason a sign-in fails, in the toast as well as Logcat
            // (7 Oct): "did not complete" alone told the owner nothing when it
            // was Google refusing the package name, and the owner has no
            // Logcat. A sheet the owner simply closed stays a plain toast.
            var reason: String? = null
            var ownerClosed = false
            val email = try {
                DriveAuth.signIn(this@BackupActivity)
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                // A sheet Google closed by itself (a build it does not know)
                // also arrives as a "cancellation"; only the owner's own
                // dismissal is silent (9 Oct).
                android.util.Log.e("DriveSignIn", "signIn cancelled (Backup)", e)
                ownerClosed = com.innovation313.roshankhata.ui.SignInProblem.isOwnerDismissal(e.errorMessage?.toString())
                reason = "${e.type.substringAfterLast('.')}: ${e.errorMessage}"
                null
            } catch (e: Exception) {
                android.util.Log.e("DriveSignIn", "signIn failed (Backup)", e)
                reason = (e as? androidx.credentials.exceptions.GetCredentialException)
                    ?.let { "${it.type.substringAfterLast('.')}: ${it.errorMessage}" }
                    ?: "${e.javaClass.simpleName}: ${e.message}"
                null
            }

            if (email == null) {
                // Dismissed the account sheet, or nothing came back. Nothing is
                // connected; say so plainly rather than proceeding half-way.
                if (ownerClosed || reason == null) {
                    Toast.makeText(this@BackupActivity, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                } else {
                    com.innovation313.roshankhata.ui.SignInProblem.show(this@BackupActivity, reason)
                }
                return@launch
            }

            // Remember the email now, so even if the Drive step is declined the
            // screen can still show which account was chosen.
            DriveAuth.rememberAccount(this@BackupActivity, email)
            authorizeDrive()
        }
    }

    /** Second step: ask for the Drive permission on the signed-in account. */
    private fun authorizeDrive() {
        DriveAuth.authorize(this)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        driveAuthorize.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        Toast.makeText(this, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Access was already granted -- the account is remembered,
                    // so just refresh.
                    refreshDriveUi()
                    announceSignedIn()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DriveSignIn", "authorize failed (Backup)", e)
                com.innovation313.roshankhata.ui.SignInProblem.show(this, "${e.javaClass.simpleName}: ${e.message}")
            }
    }

    private val driveAuthorize = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        // The Drive consent screen returns here. The account email is already
        // remembered from the sign-in step; here we only confirm the Drive
        // grant went through by reading the result (a declined grant still
        // closes the screen).
        try {
            if (activityResult.resultCode != RESULT_OK) throw IllegalStateException("Drive consent not given")
            DriveAuth.resultFromIntent(this, activityResult.data)
            refreshDriveUi()
            announceSignedIn()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.drive_permission_needed, Toast.LENGTH_LONG).show()
            refreshDriveUi()
        }
    }

    /**
     * Offer automatic backup once, at the only moment it makes sense to ask.
     *
     * The switch defaults to off, deliberately — sending a shopkeeper's books
     * to the cloud is his decision, not the app's. But off-by-default plus
     * never-mentioned is not a decision either: the owner connected his
     * account, which is the moment he is thinking about backup and the only
     * moment the question is not an interruption. He found this himself,
     * having to reach for the switch and wondering why nothing had asked.
     *
     * ASKED ONCE, and "Not now" is recorded as a real no so it is never asked
     * again. A question that keeps coming back is how a switch gets turned
     * off in irritation and left off.
     *
     * Nothing is turned on unless he taps it. Dismissing the dialog without
     * choosing leaves the question unanswered and it may be offered again —
     * an accidental tap outside must not count as either answer.
     */
    /** One offer per visit to this screen, however often the UI refreshes. */
    private var autoBackupOfferShown = false

    private fun offerAutoBackup() {
        if (isFinishing || isDestroyed) return
        if (DriveBackup.autoBackupAnswered(this)) return
        // refreshDriveUi runs several times in one visit — after a backup,
        // after a restore, after a switch is flipped. Answering is what stops
        // the question for good; this stops it stacking before he answers,
        // and lets a dialog dismissed by a tap outside come back next visit
        // rather than this instant.
        if (autoBackupOfferShown) return
        autoBackupOfferShown = true

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_backup_offer_title)
            .setMessage(R.string.auto_backup_offer_body)
            .setPositiveButton(R.string.auto_backup_offer_yes) { _, _ ->
                DriveBackup.setAutoBackup(this, true)
                refreshDriveUi()
            }
            .setNegativeButton(R.string.auto_backup_offer_no) { _, _ ->
                // Written down, not just dismissed: this is the answer, and
                // recording it is what stops the question returning.
                DriveBackup.setAutoBackup(this, false)
                refreshDriveUi()
            }
            .show()
    }

    /**
     * Say plainly that the sign-in worked (13 Oct). Google's sheets close on
     * their own and the screen only changed a line of grey text, so the owner
     * was left looking at the screen wondering whether anything had happened.
     */
    private fun announceSignedIn() {
        val name = DriveAuth.accountName(this) ?: return
        if (isFinishing || isDestroyed) return
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.drive_signed_in_ok, name),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).apply {
            setTextMaxLines(4)
            setBackgroundTint(androidx.core.content.ContextCompat.getColor(this@BackupActivity, R.color.brand_green))
            setTextColor(androidx.core.content.ContextCompat.getColor(this@BackupActivity, R.color.white))
        }.show()
    }

    /** Show the right Drive controls for whether someone is signed in. */
    private fun refreshDriveUi() {
        val name = DriveAuth.accountName(this)
        val connected = name != null && DriveAuth.isConnected(this)

        val status = findViewById<android.widget.TextView>(R.id.tvDriveStatus)
        val connect = findViewById<MaterialButton>(R.id.btnDriveConnect)
        val backupNow = findViewById<MaterialButton>(R.id.btnDriveBackupNow)
        val restore = findViewById<MaterialButton>(R.id.btnDriveRestore)
        val signOut = findViewById<MaterialButton>(R.id.btnDriveSignOut)
        val imagesSwitch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(
            R.id.switchBackupImages
        )
        val autoSwitch = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(
            R.id.switchAutoBackup
        )
        val autoHint = findViewById<android.widget.TextView>(R.id.tvAutoBackupHint)

        if (!connected) {
            status.setText(R.string.drive_not_connected)
            connect.visibility = android.view.View.VISIBLE
            backupNow.visibility = android.view.View.GONE
            restore.visibility = android.view.View.GONE
            signOut.visibility = android.view.View.GONE
            imagesSwitch.visibility = android.view.View.GONE
            autoSwitch.visibility = android.view.View.GONE
            autoHint.visibility = android.view.View.GONE
            return
        }

        connect.visibility = android.view.View.GONE
        backupNow.visibility = android.view.View.VISIBLE
        restore.visibility = android.view.View.VISIBLE
        signOut.visibility = android.view.View.VISIBLE
        imagesSwitch.visibility = android.view.View.VISIBLE
        autoSwitch.visibility = android.view.View.VISIBLE
        autoHint.visibility = android.view.View.VISIBLE

        // The switches follow the saved choice every time the screen is
        // redrawn (13 Oct). They were set once, when the screen opened, so a
        // "Turn on" in the automatic-backup offer saved the choice and the
        // worker ran — but the switch beside it still showed off, and the
        // owner turned it on again by hand believing it had not worked.
        // Setting the same value back through the listener writes nothing new.
        if (autoSwitch.isChecked != DriveBackup.autoBackup(this)) autoSwitch.isChecked = DriveBackup.autoBackup(this)
        if (imagesSwitch.isChecked != DriveBackup.includeImages(this)) imagesSwitch.isChecked = DriveBackup.includeImages(this)

        // Asked here, where the account is KNOWN to be connected — not on the
        // connect flow, which was the first attempt and reached almost nobody.
        // The Connect button is gone once an account is remembered, so
        // connectDrive() never runs again for anyone who signed in before
        // today. That is every existing owner, including the one who asked
        // for this and then reported no question appeared. He was right.
        offerAutoBackup()

        // Show the connection itself the instant it happens -- don't make
        // the owner wait on a network call just to see the email confirmed.
        status.text = getString(R.string.drive_connected_never, name!!)

        // Then fetch the last-backup time in the background and refine the
        // same line once it's known.
        lifecycleScope.launch {
            val last = withContext(Dispatchers.IO) {
                DriveBackup.lastBackupTime(this@BackupActivity, name!!)
            }
            status.text = if (last != null) {
                getString(R.string.drive_connected, name, Format.dateTime(last))
            } else {
                getString(R.string.drive_connected_never, name)
            }
        }
    }

    /**
     * @param replaceOtherPhone the owner has just been shown that another
     *   phone backed up this shop last, and chose to replace that backup.
     */
    private fun driveBackup(replaceOtherPhone: Boolean = false) {
        val name = DriveAuth.accountName(this) ?: return
        Toast.makeText(this, R.string.drive_backing_up, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { Backup.exportToCache(this@BackupActivity, dao) }
            val result = DriveBackup.backup(this@BackupActivity, name, file, force = replaceOtherPhone)

            if (result.isFailure) {
                val cause = result.exceptionOrNull()
                if (cause is DriveBackup.OtherPhoneBackup) {
                    // Nothing was uploaded. Two phones writing one backup
                    // file is how a day's entries vanish from Drive, so this
                    // is the owner's decision, made knowing when and where
                    // the backup on Drive came from.
                    confirmReplaceOtherPhone(cause)
                } else {
                    Toast.makeText(this@BackupActivity, R.string.drive_backup_failed, Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Images ride separately, and only when the owner asked for them.
            // The text backup is already safe on Drive at this point; an image
            // upload that fails does not undo it, so it is reported on its own
            // rather than turning the whole backup red.
            if (DriveBackup.includeImages(this@BackupActivity)) {
                val imageResult = withContext(Dispatchers.IO) {
                    val zip = BackupImages.pack(this@BackupActivity, dao)
                    DriveBackup.backupImages(this@BackupActivity, name, zip)
                }
                if (imageResult.isFailure) {
                    Toast.makeText(
                        this@BackupActivity,
                        R.string.drive_images_backup_failed,
                        Toast.LENGTH_LONG
                    ).show()
                    refreshDriveUi()
                    return@launch
                }
            }

            // Record it. This line was missing, and its absence is why the
            // weekly nudge kept firing for an owner who backs up by hand every
            // day: only the file backup and the automatic daily upload ever
            // marked the book as protected, so pressing this button did the
            // work and got no credit for it.
            BackupReminder.recordBackup(this@BackupActivity)

            Toast.makeText(this@BackupActivity, R.string.drive_backup_done, Toast.LENGTH_LONG).show()
            refreshDriveUi()
        }
    }

    private fun confirmReplaceOtherPhone(conflict: DriveBackup.OtherPhoneBackup) {
        val time = if (conflict.at > 0L) Format.dateTime(conflict.at) else getString(R.string.viewer_unknown_time)
        val device = conflict.device
        val body = if (device.isNullOrBlank()) getString(R.string.drive_other_phone_body, time)
        else getString(R.string.drive_other_phone_body_named, device, time)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drive_other_phone_title)
            .setMessage(body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.drive_other_phone_replace) { _, _ ->
                driveBackup(replaceOtherPhone = true)
            }
            .show()
    }

    /**
     * Restore always starts by asking Drive what is there.
     *
     * It used to go straight to "restore this account's backup", which quietly
     * meant the OPEN shop's backup — correct with one shop, and the reason a
     * two-shop owner restored one book and had no way of knowing the other was
     * still waiting. Naming the shops first makes the choice visible, and makes
     * bringing all of them back a single tap.
     */
    private fun confirmDriveRestore() {
        driveDiscover()
    }

    /**
     * Ask Drive what shops it is holding, and offer to bring them all back.
     *
     * This is the answer to a wiped or lost phone. The list of businesses
     * lives in this phone's preferences, so a fresh install knows about one
     * shop and would restore one shop, leaving a second shop's backup sitting
     * on Drive with nothing pointing at it. Drive is asked directly instead.
     */
    private fun driveDiscover() {
        val account = DriveAuth.accountName(this) ?: return
        Toast.makeText(this, R.string.drive_looking, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val found = DriveBackup.discoverBusinesses(this@BackupActivity, account).getOrNull()
            if (found.isNullOrEmpty()) {
                Toast.makeText(this@BackupActivity, R.string.drive_no_backup, Toast.LENGTH_LONG).show()
                return@launch
            }

            // Shop name on the card, its backup date beneath (10 Oct). A tap
            // restores that one shop; the button restores all of them.
            val choices = found.map { biz ->
                val shop = biz.name ?: Digits.string(resources, R.string.business_numbered, biz.id)
                ChoiceSheet.Option(shop, Format.dateOnly(biz.modifiedAt), icon = R.drawable.ic_storefront,
                    iconTint = R.color.brand_green_text, tile = R.color.tile_khata_bg)
            }
            ChoiceSheet.show(
                this@BackupActivity,
                Digits.quantity(resources, R.plurals.drive_found_businesses, found.size, found.size),
                choices,
                secondary = ChoiceSheet.Action(getString(R.string.drive_restore_all)) {
                    confirmRestoreAll(account, found)
                }
            ) { which -> confirmRestoreAll(account, listOf(found[which])) }
        }
    }

    private fun confirmRestoreAll(account: String, businesses: List<DriveBackup.DriveBusiness>) {
        val names = businesses.joinToString("\n") { biz ->
            "• " + (biz.name ?: Digits.string(resources, R.string.business_numbered, biz.id))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_warning_title)
            .setMessage(getString(R.string.drive_restore_all_warning, names))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore_replace) { _, _ -> restoreAll(account, businesses) }
            .show()
    }

    /**
     * Restore each discovered shop into its own book, one after another.
     *
     * Two things here are deliberate and load-bearing. Each shop's registry
     * entry is recreated at the id its Drive file already carries, so it lands
     * back on its own database file rather than a newly numbered one. And the
     * DAO is fetched fresh inside every round, never the screen's cached one:
     * switching business closes and reopens the database, so a handle taken
     * before the switch would write this shop's ledger into the last shop's
     * file. That is the single worst thing this feature could do, so it is
     * done explicitly rather than left to habit.
     */
    private fun restoreAll(account: String, businesses: List<DriveBackup.DriveBusiness>) {
        Toast.makeText(this, R.string.drive_restoring, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val done = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for (biz in businesses) {
                val label = biz.name ?: Digits.string(resources, R.string.business_numbered, biz.id)

                // A shop of this number already here under ANOTHER name is a different shop (21 Oct 2026,
                // the critical review: it was replaced by the Drive copy without a word). The Drive copy
                // then comes in as a shop of its own, beside it.
                val here = Businesses.list(this@BackupActivity).firstOrNull { it.id == biz.id }
                val hereName = here?.let { Businesses.displayName(this@BackupActivity, it) }
                val otherShop = here != null && here.id != 1L && !biz.name.isNullOrBlank() && !hereName.isNullOrBlank() &&
                    !hereName.trim().equals(biz.name.trim(), ignoreCase = true)
                val target = if (otherShop) Businesses.create(this@BackupActivity, biz.name.orEmpty())
                    else Businesses.ensure(this@BackupActivity, biz.id, biz.name)
                Businesses.switchTo(this@BackupActivity, target.id)

                val file = DriveBackup.restoreByIdToFile(this@BackupActivity, account, biz.backupFileId)
                    .getOrNull()
                if (file == null) { failed += label; continue }

                val (result, data) = withContext(Dispatchers.IO) { Backup.parseFile(file).also { file.delete() } }
                if (result !is BackupCodec.ImportResult.Ok || data == null) { failed += label; continue }

                val liveDao = KhataDatabase.get(this@BackupActivity).khataDao()
                val kept = withContext(Dispatchers.IO) {
                    // Each shop's own book kept first, as a single restore does; not replaced unless kept.
                    // A restore that fails rolls back whole and is counted as failed, never a crash (22 Oct 2026).
                    runCatching {
                        com.innovation313.roshankhata.data.Snapshots.keepNow(this@BackupActivity)
                            .also { if (it) Backup.restore(this@BackupActivity, liveDao, data) }
                    }.getOrDefault(false)
                }
                if (!kept) { failed += label; continue }

                // Images for this shop, from this shop's own archive. A shop
                // without one is restored without photos, not failed.
                DriveBackup.restoreImages(this@BackupActivity, account).getOrNull()?.let { bytes ->
                    withContext(Dispatchers.IO) {
                        BackupImages.restore(this@BackupActivity, liveDao, bytes)
                    }
                }

                BackupReminder.recordBackup(this@BackupActivity)
                // This phone now stands on that backup: its next upload is a
                // continuation of it, not a stranger's file to be asked about.
                DriveBackup.rememberSeen(this@BackupActivity, biz.modifiedAt)
                done += label
            }

            val body = buildString {
                if (done.isNotEmpty()) {
                    append(getString(R.string.drive_restored_these, done.joinToString("\n") { "• $it" }))
                }
                if (failed.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append(getString(R.string.drive_restore_missed, failed.joinToString("\n") { "• $it" }))
                }
            }

            MaterialAlertDialogBuilder(this@BackupActivity)
                .setTitle(R.string.restore_done)
                .setMessage(body)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> goHome() }
                .show()
        }
    }

    private fun driveSignOut() {
        DriveAuth.disconnect(this) { refreshDriveUi() }
    }
}
