package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import java.io.File

/**
 * Hands a finished PDF to the owner the way a document should be handed over:
 * SHOW it first, share second. Every PDF the app makes used to jump straight
 * to the system share sheet, so the owner never saw the document before it
 * left their hands — they were choosing where to send a page they had not
 * read. This puts a small step in front: a viewer opens on the PDF, and
 * sharing is one deliberate tap after that, not the only option.
 *
 * Read-only by nature: this neither creates nor changes any record. It is
 * given a file that already exists and only opens or forwards it, so nothing
 * here can touch the ledger.
 *
 * The file lives in a cache folder already exposed through the app's
 * FileProvider (statements/, invoices/, …), so both the viewer and the share
 * sheet can read it by content:// URI without any storage permission.
 */
object PdfShare {

    /**
     * Show [file] to the owner. A dialog offers to OPEN it in whatever PDF
     * viewer the phone has, or to SHARE it (WhatsApp, email, Drive, …). Open is
     * the primary action, so the natural path is see-then-send.
     *
     * @param titleRes the dialog title (e.g. "Report ready").
     */
    fun present(context: Context, file: File, titleRes: Int = R.string.pdf_ready_title) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setMessage(R.string.pdf_ready_body)
            // Primary: open the document so it can be read before anything else.
            .setPositiveButton(R.string.pdf_open) { _, _ ->
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Started directly, not wrapped in createChooser: a chooser
                // over ACTION_VIEW renders as the same sheet the Share button
                // opens, so the two buttons looked and behaved identically.
                // Android still shows its own picker if several viewers are
                // installed, and none if there is a default — which is the
                // behaviour "Open" should have.
                //
                // The fallback is on the actual failure (no viewer on the
                // phone), not on resolveActivity(): that returns null under
                // Android 11 package-visibility unless the manifest declares
                // the query, and a wrong null there is what made Open behave
                // as Share.
                try {
                    context.startActivity(view)
                } catch (e: android.content.ActivityNotFoundException) {
                    share(context, uri, file.name)
                }
            }
            // Secondary: send it on, once they have chosen to.
            .setNeutralButton(R.string.share) { _, _ -> share(context, uri, file.name) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun share(context: Context, uri: android.net.Uri, name: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share))
        )
    }
}
