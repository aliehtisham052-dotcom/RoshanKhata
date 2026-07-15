package com.innovation313.roshankhata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Backup
import com.innovation313.roshankhata.data.BusinessReport
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

        findViewById<MaterialButton>(R.id.btnBackup).setOnClickListener { createBackup() }

        findViewById<MaterialButton>(R.id.btnRestore).setOnClickListener {
            showRestoreOptions()
        }

        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener { makeReport() }
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
                val json = Backup.export(dao)

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
        data: Backup.ParsedBackup?
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
                confirmRestore(result, data)
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
        data: Backup.ParsedBackup
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_warning_title)
            .setMessage(
                getString(
                    R.string.restore_warning_message,
                    counts.parties,
                    counts.entries,
                    counts.cheques,
                    counts.cash,
                    counts.plans,
                    counts.bills
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore_replace) { _, _ -> doRestore(data) }
            .show()
    }

    private fun doRestore(data: Backup.ParsedBackup) {
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Backup.restore(dao, data)
            }

            // A backup preserves EXACTLY what was in the ledger when it was
            // taken — including anyone who was already in the Recycle Bin at
            // that moment. Restored, they go back to the Recycle Bin, not the
            // home list. That is correct, but to the owner it looks like the
            // customer "did not come back" — so if any deleted rows were part of
            // this backup, say so plainly, and point to where they are.
            val deletedParties = data.parties.count { it.isDeleted }

            if (deletedParties > 0) {
                MaterialAlertDialogBuilder(this@BackupActivity)
                    .setTitle(R.string.restore_done)
                    .setMessage(
                        getString(R.string.restore_done_with_bin, deletedParties)
                    )
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

    /**
     * A printable PDF of the whole business.
     *
     * Deliberately kept on the same screen as the backup, and deliberately
     * labelled — on the button, on the screen, and on the document's own first
     * page — as NOT being one. A PDF cannot be read back into the app. If an
     * owner mistook this for their backup, deleted the real file, and then lost
     * the phone, this document would serve only to show them exactly what they
     * had lost.
     */
    private fun makeReport() {
        Toast.makeText(this, R.string.making_report, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                BusinessReport.build(this@BackupActivity, dao)
            }

            if (file == null) {
                Toast.makeText(this@BackupActivity, R.string.report_failed, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            val uri = FileProvider.getUriForFile(
                this@BackupActivity,
                "$packageName.fileprovider",
                file
            )

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(
                Intent.createChooser(share, getString(R.string.share_report_pdf))
            )
        }
    }
}
