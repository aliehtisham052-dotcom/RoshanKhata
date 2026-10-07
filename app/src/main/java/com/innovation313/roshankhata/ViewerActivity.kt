package com.innovation313.roshankhata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Backup
import com.innovation313.roshankhata.data.BackupImages
import com.innovation313.roshankhata.data.DriveAuth
import com.innovation313.roshankhata.data.DriveBackup
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.ViewerMode
import com.innovation313.roshankhata.data.ViewerSync
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * The helper's phone (3 Oct 2026). See [ViewerMode] for why it is safe.
 *
 * On the OWNER's phone this screen does two things: sends today's copy to a
 * helper as a file (WhatsApp is how a shop passes things around), and — on a
 * helper's phone that is still a normal phone — turns it into a read-only one.
 *
 * On a READ-ONLY phone it says whose book this is, how old the copy is, takes
 * a fresh copy, and can turn the phone back into a normal one.
 *
 * The file route comes first on purpose: it needs no Google account at all.
 * The Drive route needs the OWNER's Google account on the helper's phone,
 * which also opens the owner's mail and photos to whoever holds it — the
 * screen says so before it starts, and the owner decides.
 */
class ViewerActivity : BaseActivity() {

    /** Drive account picked in this visit, waiting for the consent screen. */
    private var pendingAccount: String? = null

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) loadFromFile(uri) }

    private val driveAuthorize = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val account = pendingAccount
        try {
            DriveAuth.resultFromIntent(this, result.data)
            if (account != null) chooseDriveBook(account) else setBusy(false)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.drive_permission_needed, Toast.LENGTH_LONG).show()
            setBusy(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        ScreenInsets.on(this)

        findViewById<MaterialButton>(R.id.btnViewerSendFile).setOnClickListener { sendCopy() }
        findViewById<MaterialButton>(R.id.btnViewerFromFile).setOnClickListener {
            confirmBecomeViewer(getString(R.string.viewer_confirm_body)) {
                pickFile.launch(arrayOf("*/*"))
            }
        }
        findViewById<MaterialButton>(R.id.btnViewerFromDrive).setOnClickListener {
            confirmBecomeViewer(
                getString(R.string.viewer_confirm_body) + "\n\n" +
                    getString(R.string.viewer_drive_privacy)
            ) { connectDrive() }
        }
        findViewById<MaterialButton>(R.id.btnViewerRefresh).setOnClickListener { refresh() }
        findViewById<MaterialButton>(R.id.btnViewerLeave).setOnClickListener { confirmLeave() }

        render()
    }

    private fun render() {
        val viewer = ViewerMode.isOn(this)
        findViewById<View>(R.id.viewerStatus).visibility = if (viewer) View.VISIBLE else View.GONE
        findViewById<View>(R.id.viewerSetup).visibility = if (viewer) View.GONE else View.VISIBLE
        if (!viewer) return

        val shop = ViewerMode.shopName(this)
        findViewById<TextView>(R.id.tvViewerShop).apply {
            text = shop?.let { getString(R.string.viewer_shop, it) }.orEmpty()
            visibility = if (shop.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val dataAt = ViewerMode.dataAt(this)
        findViewById<TextView>(R.id.tvViewerDataAt).text =
            if (dataAt > 0L) getString(R.string.viewer_data_at, Format.dateTime(dataAt))
            else getString(R.string.viewer_data_at_unknown)
        val refreshed = ViewerMode.refreshedAt(this)
        findViewById<TextView>(R.id.tvViewerRefreshedAt).text =
            getString(R.string.viewer_refreshed_at, Format.dateTime(refreshed))
        val fromDrive = ViewerMode.source(this) == ViewerMode.SOURCE_DRIVE
        findViewById<TextView>(R.id.tvViewerSource).text =
            if (fromDrive) getString(R.string.viewer_source_drive, ViewerMode.account(this).orEmpty())
            else getString(R.string.viewer_source_file)
        findViewById<MaterialButton>(R.id.btnViewerRefresh).setText(
            if (fromDrive) R.string.viewer_refresh else R.string.viewer_refresh_file
        )
    }

    private fun setBusy(busy: Boolean) {
        listOf(
            R.id.btnViewerSendFile, R.id.btnViewerFromFile, R.id.btnViewerFromDrive,
            R.id.btnViewerRefresh, R.id.btnViewerLeave
        ).forEach { findViewById<View>(it)?.isEnabled = !busy }
        if (busy) Toast.makeText(this, R.string.viewer_loading, Toast.LENGTH_SHORT).show()
    }

    // ---------- Owner's side: send today's copy ----------

    /**
     * With customer photos in the book the owner chooses: the copy with its
     * pictures (heavier, and the size is said), or the balances alone (the
     * small file this button always sent). With no photos there is nothing
     * to choose, and it sends at once as before.
     */
    private fun sendCopy() {
        setBusy(true)
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    BackupImages.copyImageBytes(
                        this@ViewerActivity, KhataDatabase.get(this@ViewerActivity).khataDao()
                    )
                }.getOrDefault(0L)
            }
            setBusy(false)
            if (bytes <= 0L) {
                shareCopy(withPhotos = false)
                return@launch
            }
            val size = android.text.format.Formatter.formatShortFileSize(this@ViewerActivity, bytes)
            val choices = arrayOf(
                getString(R.string.viewer_send_with_photos, size),
                getString(R.string.viewer_send_without_photos)
            )
            MaterialAlertDialogBuilder(this@ViewerActivity)
                .setTitle(R.string.viewer_send_file)
                .setItems(choices) { _, which -> shareCopy(withPhotos = which == 0) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun shareCopy(withPhotos: Boolean) {
        setBusy(true)
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dao = KhataDatabase.get(this@ViewerActivity).khataDao()
                    val json = Backup.exportToCache(this@ViewerActivity, dao)
                    if (withPhotos) BackupImages.packCopy(this@ViewerActivity, dao, json)
                    else json
                }.getOrNull()
            }
            setBusy(false)
            if (file == null) {
                Toast.makeText(this@ViewerActivity, R.string.backup_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(this@ViewerActivity, "$packageName.fileprovider", file)
            // The text alone goes as text/plain: WhatsApp refuses an unknown
            // type (see BackupActivity). With photos it is a zip, a type every
            // messenger sends as a document.
            val share = Intent(Intent.ACTION_SEND).apply {
                type = if (withPhotos) "application/zip" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.viewer_send_file)))
        }
    }

    // ---------- Becoming a read-only phone ----------

    private fun confirmBecomeViewer(message: String, go: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.viewer_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ -> go() }
            .show()
    }

    /**
     * The owner's file is either the text alone or the zip that also carries
     * photos ([BackupImages.packCopy]); both open here. Only the text is read
     * into memory. The photos are streamed out of the file afterwards.
     */
    private fun loadFromFile(uri: Uri) {
        setBusy(true)
        lifecycleScope.launch {
            val open: () -> InputStream? = { contentResolver.openInputStream(uri) }
            val copy = withContext(Dispatchers.IO) {
                runCatching { BackupImages.readCopy(open) }.getOrNull()
            }
            if (copy == null) {
                setBusy(false)
                Toast.makeText(this@ViewerActivity, R.string.viewer_load_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            finishLoad(
                ViewerSync.load(
                    this@ViewerActivity, copy.text, ViewerMode.SOURCE_FILE, null, 1L,
                    images = if (copy.hasImages) open else null
                )
            )
        }
    }

    /**
     * Same two steps as the Backup screen — sign in to learn the account,
     * then ask for the Drive permission — but the account is NOT remembered
     * in [DriveAuth]. That is where this phone's OWN backups would go, and
     * the owner's account must never become this phone's backup target.
     */
    private fun connectDrive() {
        setBusy(true)
        lifecycleScope.launch {
            val email = try {
                DriveAuth.signIn(this@ViewerActivity)
            } catch (e: Exception) {
                android.util.Log.e("DriveSignIn", "signIn failed (Viewer)", e)
                null
            }
            if (email == null) {
                setBusy(false)
                Toast.makeText(this@ViewerActivity, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingAccount = email
            DriveAuth.authorize(this@ViewerActivity)
                .addOnSuccessListener { result ->
                    val pending = result.pendingIntent
                    if (result.hasResolution() && pending != null) {
                        driveAuthorize.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else if (result.hasResolution()) {
                        setBusy(false)
                        Toast.makeText(this@ViewerActivity, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                    } else {
                        chooseDriveBook(email)
                    }
                }
                .addOnFailureListener {
                    setBusy(false)
                    Toast.makeText(this@ViewerActivity, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                }
        }
    }

    /** One shop on Drive: load it. Several: ask which one this helper works at. */
    private fun chooseDriveBook(account: String) {
        lifecycleScope.launch {
            val found = DriveBackup.discoverBusinesses(this@ViewerActivity, account).getOrNull()
            when {
                found == null -> {
                    setBusy(false)
                    Toast.makeText(this@ViewerActivity, R.string.viewer_load_failed, Toast.LENGTH_LONG).show()
                }
                found.isEmpty() -> {
                    setBusy(false)
                    Toast.makeText(this@ViewerActivity, R.string.viewer_no_backup, Toast.LENGTH_LONG).show()
                }
                found.size == 1 -> loadFromDrive(account, found[0].id)
                else -> {
                    val labels = found.map {
                        it.name ?: getString(R.string.viewer_business_number, it.id)
                    }.toTypedArray()
                    MaterialAlertDialogBuilder(this@ViewerActivity)
                        .setTitle(R.string.viewer_pick_business)
                        .setItems(labels) { _, which -> loadFromDrive(account, found[which].id) }
                        .setOnCancelListener { setBusy(false) }
                        .show()
                }
            }
        }
    }

    private fun loadFromDrive(account: String, businessId: Long) {
        lifecycleScope.launch {
            val read = DriveBackup.readForBusiness(this@ViewerActivity, account, businessId)
            val pair = read.getOrNull()
            if (read.isFailure || pair == null) {
                setBusy(false)
                Toast.makeText(
                    this@ViewerActivity,
                    if (read.isFailure) R.string.viewer_load_failed else R.string.viewer_no_backup,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            // The owner's photos, when his Drive backup carries them (his own
            // "include images" switch). None there, or a failed download, is
            // not a failed load: the book still opens, without pictures.
            val photos: ByteArray? =
                DriveBackup.readImagesForBusiness(this@ViewerActivity, account, businessId).getOrNull()
            val photoStream: (() -> InputStream?)? =
                if (photos != null) { { photos.inputStream() } } else null
            finishLoad(
                ViewerSync.load(
                    this@ViewerActivity, pair.first, ViewerMode.SOURCE_DRIVE, account, businessId,
                    images = photoStream
                )
            )
        }
    }

    // ---------- A read-only phone ----------

    private fun refresh() {
        if (ViewerMode.source(this) == ViewerMode.SOURCE_DRIVE) {
            val account = ViewerMode.account(this) ?: return connectDrive()
            setBusy(true)
            loadFromDrive(account, ViewerMode.driveBusinessId(this))
        } else {
            pickFile.launch(arrayOf("*/*"))
        }
    }

    private fun finishLoad(outcome: ViewerSync.Outcome) {
        setBusy(false)
        when (outcome) {
            is ViewerSync.Outcome.Loaded -> {
                Toast.makeText(this, R.string.viewer_loaded, Toast.LENGTH_LONG).show()
                restart()
            }
            is ViewerSync.Outcome.LoadedWithoutPhotos -> {
                Toast.makeText(this, R.string.viewer_loaded_no_photos, Toast.LENGTH_LONG).show()
                restart()
            }
            is ViewerSync.Outcome.Rejected ->
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.viewer_rejected, outcome.reason))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            is ViewerSync.Outcome.Failed -> {
                android.util.Log.e("Viewer", "load failed", outcome.cause)
                Toast.makeText(this, R.string.viewer_load_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.viewer_leave_title)
            .setMessage(R.string.viewer_leave_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.viewer_leave) { _, _ ->
                ViewerMode.leave(this)
                restart()
            }
            .show()
    }

    /**
     * The book that is open just changed (copy loaded, or back to this
     * phone's own). Every screen still holding the old one goes, exactly as
     * after a business switch, and the app starts again through the gate so
     * App Lock still applies.
     */
    private fun restart() {
        startActivity(
            Intent(this, GateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
