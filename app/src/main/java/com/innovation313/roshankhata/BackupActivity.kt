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
import com.innovation313.roshankhata.data.KhataDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            // Accept anything: some file managers hand JSON back as
            // octet-stream, and refusing the user's own backup on a MIME
            // technicality would be maddening. The parser validates it anyway.
            pickBackupFile.launch(arrayOf("*/*"))
        }
    }

    private fun createBackup() {
        Toast.makeText(this, R.string.creating_backup, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val json = Backup.export(dao)
                Backup.writeToCache(this@BackupActivity, json)
            }

            if (file == null) {
                Toast.makeText(this@BackupActivity, R.string.backup_failed, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            val uri = FileProvider.getUriForFile(
                this@BackupActivity,
                "$packageName.fileprovider",
                file
            )

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(share, getString(R.string.save_backup)))
        }
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

            when (result) {
                is Backup.ImportResult.Failed -> {
                    MaterialAlertDialogBuilder(this@BackupActivity)
                        .setTitle(R.string.restore_from_file)
                        .setMessage(getString(R.string.restore_failed, result.reason))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }

                is Backup.ImportResult.Ok -> {
                    if (data == null) return@launch
                    confirmRestore(result, data)
                }
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
                    counts.plans
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

            Toast.makeText(this@BackupActivity, R.string.restore_done, Toast.LENGTH_LONG).show()

            // Back to a clean home screen — the ledger it was showing no longer
            // exists, and leaving stale rows on screen would be alarming.
            startActivity(
                Intent(this@BackupActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_UNLOCKED, true)
            )
            finish()
        }
    }
}
