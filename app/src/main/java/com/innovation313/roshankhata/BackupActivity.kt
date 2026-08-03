package com.innovation313.roshankhata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Backup
import com.innovation313.roshankhata.data.BackupImages
import com.innovation313.roshankhata.data.BackupReminder
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.DriveAuth
import com.innovation313.roshankhata.data.DriveBackup
import com.innovation313.roshankhata.data.DriveFeature
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
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
class BackupActivity : AppCompatActivity() {

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
        }
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
                val json = Backup.export(this@BackupActivity, dao)

                val downloadPath = Backup.saveToDownloads(this@BackupActivity, json)
                Backup.writeInternalCopy(this@BackupActivity, json)
                val shareFile = Backup.writeToCache(this@BackupActivity, json)

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
        result: Backup.ImportResult,
        data: Backup.ParsedBackup?,
        driveAccount: String? = null
    ) {
        when (result) {
            is Backup.ImportResult.Failed -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.restore_from_file)
                    .setMessage(getString(R.string.restore_failed, result.reason))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }

            is Backup.ImportResult.Ok -> {
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
        counts: Backup.ImportResult.Ok,
        data: Backup.ParsedBackup,
        driveAccount: String? = null
    ) {
        val body = getString(
            R.string.restore_warning_message,
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
        val message =
            if (mismatch) getString(R.string.restore_business_mismatch, data.businessName, openShop) +
                "\n\n" + body
            else body

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_warning_title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore_replace) { _, _ -> doRestore(data, driveAccount) }
            .show()
    }

    private fun doRestore(data: Backup.ParsedBackup, driveAccount: String? = null) {
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Backup.restore(this@BackupActivity, dao, data)
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
                        getString(R.string.restore_done_with_bin, deletedParties) else null,
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

    private fun goHome() {
        // Back to a clean home screen — the ledger it was showing no longer
        // exists, and leaving stale rows on screen would be alarming.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
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

        val options = arrayOf(
            getString(R.string.restore_from_app),
            getString(R.string.restore_from_storage)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_from_file)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSavedBackups(saved)
                    1 -> pickFromStorage()
                }
            }
            .show()
    }

    private fun showSavedBackups(files: List<File>) {
        val labels = files.map { f ->
            getString(
                R.string.backup_saved_at,
                Format.dateTime(f.lastModified()),
                (f.length() / 1024).coerceAtLeast(1)
            )
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_from_app)
            .setItems(labels) { _, which ->
                restoreFromFile(files[which])
            }
            .show()
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
            val email = try {
                DriveAuth.signIn(this@BackupActivity)
            } catch (e: Exception) {
                null
            }

            if (email == null) {
                // Dismissed the account sheet, or nothing came back. Nothing is
                // connected; say so plainly rather than proceeding half-way.
                Toast.makeText(
                    this@BackupActivity,
                    R.string.drive_signin_failed,
                    Toast.LENGTH_LONG
                ).show()
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
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
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
            DriveAuth.resultFromIntent(this, activityResult.data)
            refreshDriveUi()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.drive_permission_needed, Toast.LENGTH_LONG).show()
            refreshDriveUi()
        }
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

        // Fetch the last-backup time so the owner knows how current they are.
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

    private fun driveBackup() {
        val name = DriveAuth.accountName(this) ?: return
        Toast.makeText(this, R.string.drive_backing_up, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { Backup.export(this@BackupActivity, dao) }
            val result = DriveBackup.backup(this@BackupActivity, name, json)

            if (result.isFailure) {
                Toast.makeText(this@BackupActivity, R.string.drive_backup_failed, Toast.LENGTH_LONG).show()
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

            Toast.makeText(this@BackupActivity, R.string.drive_backup_done, Toast.LENGTH_LONG).show()
            refreshDriveUi()
        }
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

            val labels = found.map { biz ->
                val shop = biz.name ?: getString(R.string.business_numbered, biz.id)
                "$shop — ${Format.dateOnly(biz.modifiedAt)}"
            }.toTypedArray()

            MaterialAlertDialogBuilder(this@BackupActivity)
                .setTitle(getString(R.string.drive_found_businesses, found.size))
                .setItems(labels) { _, which -> confirmRestoreAll(account, listOf(found[which])) }
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.drive_restore_all) { _, _ ->
                    confirmRestoreAll(account, found)
                }
                .show()
        }
    }

    private fun confirmRestoreAll(account: String, businesses: List<DriveBackup.DriveBusiness>) {
        val names = businesses.joinToString("\n") { biz ->
            "• " + (biz.name ?: getString(R.string.business_numbered, biz.id))
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
                val label = biz.name ?: getString(R.string.business_numbered, biz.id)

                Businesses.ensure(this@BackupActivity, biz.id, biz.name)
                Businesses.switchTo(this@BackupActivity, biz.id)

                val text = DriveBackup.restoreById(this@BackupActivity, account, biz.backupFileId)
                    .getOrNull()
                if (text == null) { failed += label; continue }

                val (result, data) = withContext(Dispatchers.IO) { Backup.parseText(text) }
                if (result !is Backup.ImportResult.Ok || data == null) { failed += label; continue }

                val liveDao = KhataDatabase.get(this@BackupActivity).khataDao()
                withContext(Dispatchers.IO) { Backup.restore(this@BackupActivity, liveDao, data) }

                // Images for this shop, from this shop's own archive. A shop
                // without one is restored without photos, not failed.
                DriveBackup.restoreImages(this@BackupActivity, account).getOrNull()?.let { bytes ->
                    withContext(Dispatchers.IO) {
                        BackupImages.restore(this@BackupActivity, liveDao, bytes)
                    }
                }

                BackupReminder.recordBackup(this@BackupActivity)
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
