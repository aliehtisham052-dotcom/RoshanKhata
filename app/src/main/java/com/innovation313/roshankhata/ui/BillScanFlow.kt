package com.innovation313.roshankhata.ui

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BillOcr
import com.innovation313.roshankhata.data.BillPhoto
import com.innovation313.roshankhata.data.BillScan
import com.innovation313.roshankhata.data.PaymentScan
import com.innovation313.roshankhata.data.ScannedBill
import com.innovation313.roshankhata.data.ScannedPayment
import com.innovation313.roshankhata.data.ScannedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One way of getting a printed bill's picture into the app, shared by every
 * screen that reads bills (1 Oct: supplier bills, then a customer's entry).
 *
 * Google's document scanner first (edges found, page flattened, shadows
 * gone); the plain camera wherever it cannot run (under 1.7 GB of RAM, no
 * Play services); or a picture from the gallery.
 *
 * Construct it as a property of the activity, never later: the three
 * launchers must be registered before the screen starts.
 *
 * [onPhoto] gets the picture. Its second argument is the camera's own
 * working file when the plain camera took it (the caller deletes it once
 * read), and null when the picture belongs to the scanner or the owner's
 * gallery. [onNothing] runs whenever the owner backs out with no picture.
 */
class BillScanFlow(
    private val activity: AppCompatActivity,
    private val onPhoto: (Uri, java.io.File?) -> Unit,
    private val onNothing: () -> Unit
) {
    /** The plain camera's file while it is open; kept across a screen restart. */
    private var cameraPath: String? = null

    private val docScanner = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = if (result.resultCode == AppCompatActivity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
        } else null
        if (uri == null) onNothing() else onPhoto(uri, null)
    }

    private val takePhoto = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { written: Boolean ->
        val file = cameraPath?.let { java.io.File(it) }
        cameraPath = null
        val uri = if (written && file != null) {
            runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
            }.getOrNull()
        } else null
        if (uri == null) {
            file?.delete()
            onNothing()
        } else {
            onPhoto(uri, file)
        }
    }

    private val pickPhoto = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // The gallery's picture is the owner's own, never ours to delete.
        if (uri == null) onNothing() else onPhoto(uri, null)
    }

    /**
     * Camera or gallery. Cancel, Back or a tap outside counts as nothing.
     * [titleRes]: a payment slip is not a bill, and should not be called one.
     */
    fun chooseSource(titleRes: Int = R.string.bill_scan) {
        var chosen = false
        MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setItems(
                arrayOf(activity.getString(R.string.bill_scan_camera), activity.getString(R.string.bill_scan_gallery))
            ) { _, which ->
                chosen = true
                if (which == 0) launchDocScanner()
                else pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { if (!chosen) onNothing() }
            .show()
    }

    private fun launchDocScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                try {
                    docScanner.launch(IntentSenderRequest.Builder(sender).build())
                } catch (e: Exception) {
                    launchCamera()
                }
            }
            // Unsupported phone, no Play services, or the scanner could not
            // start: the plain camera still reads the bill.
            .addOnFailureListener { launchCamera() }
    }

    private fun launchCamera() {
        val file = runCatching {
            val dir = java.io.File(activity.cacheDir, "camera").apply { mkdirs() }
            java.io.File(dir, "bill_scan_${System.currentTimeMillis()}.jpg")
        }.getOrNull()
        val uri = file?.let {
            runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", it
                )
            }.getOrNull()
        }
        if (file == null || uri == null) {
            Toast.makeText(activity, R.string.photo_save_failed, Toast.LENGTH_LONG).show()
            onNothing()
            return
        }
        cameraPath = file.absolutePath
        try {
            takePhoto.launch(uri)
        } catch (e: android.content.ActivityNotFoundException) {
            cameraPath = null
            file.delete()
            Toast.makeText(activity, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
            onNothing()
        }
    }

    fun saveState(out: Bundle, key: String) {
        cameraPath?.let { out.putString(key, it) }
    }

    fun restoreState(state: Bundle?, key: String) {
        cameraPath = state?.getString(key)
    }

    // ------------------------------------------------------------ reading

    /** What came of reading one picture. */
    sealed class Outcome {
        /** The phone's text reader is not installed yet (Play services). */
        object Unavailable : Outcome()
        /** Read, but nothing a bill would carry was found on it. */
        object Nothing : Outcome()
        /**
         * Something was found. [keptPhoto] is a private, scaled copy of the
         * picture (BillPhoto), or null if the copy failed; whoever does not
         * use it must delete it.
         */
        class Found(val bill: ScannedBill, val keptPhoto: String?) : Outcome()
        /** A payment slip or screenshot with something on it; [keptPhoto] as above. */
        class PaymentFound(val payment: ScannedPayment, val keptPhoto: String?) : Outcome()
    }

    companion object {

        /**
         * Read [photo] and, if anything was found, keep a private copy.
         * [temp] (the camera's working file) is deleted either way.
         * Shows "Reading the bill…" while it works.
         */
        suspend fun read(
            activity: AppCompatActivity,
            photo: Uri,
            temp: java.io.File?,
            knownProducts: List<String>,
            knownSuppliers: List<String>
        ): Outcome {
            val reading = MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.bill_scan_reading)
                .setCancelable(false)
                .show()
            try {
                val result = BillOcr.read(activity, photo)
                val bill = (result as? BillOcr.Result.Read)?.let { read ->
                    withContext(Dispatchers.Default) {
                        BillScan.parse(read.lines, knownProducts, knownSuppliers = knownSuppliers)
                    }
                }
                val kept = if (bill != null && !bill.isEmpty) {
                    withContext(Dispatchers.IO) { BillPhoto.save(activity, photo) }
                } else null
                return when {
                    result is BillOcr.Result.Unavailable -> Outcome.Unavailable
                    bill == null || bill.isEmpty -> Outcome.Nothing
                    else -> Outcome.Found(bill, kept)
                }
            } finally {
                // The camera's working file is done with once read and copied.
                runCatching { temp?.delete() }
                reading.dismiss()
            }
        }

        /**
         * Read [photo] as money received: a payment app's screenshot or a
         * bank slip (PaymentScan). Same shape as [read]: a private copy kept
         * only when something was found, [temp] deleted either way.
         */
        suspend fun readPayment(
            activity: AppCompatActivity,
            photo: Uri,
            temp: java.io.File?
        ): Outcome {
            val reading = MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.payment_scan_reading)
                .setCancelable(false)
                .show()
            try {
                val result = BillOcr.read(activity, photo)
                val payment = (result as? BillOcr.Result.Read)?.let { read ->
                    withContext(Dispatchers.Default) { PaymentScan.parse(BillScan.rows(read.lines)) }
                }
                val kept = if (payment != null && !payment.isEmpty) {
                    withContext(Dispatchers.IO) { BillPhoto.save(activity, photo) }
                } else null
                return when {
                    result is BillOcr.Result.Unavailable -> Outcome.Unavailable
                    payment == null || payment.isEmpty -> Outcome.Nothing
                    else -> Outcome.PaymentFound(payment, kept)
                }
            } finally {
                runCatching { temp?.delete() }
                reading.dismiss()
            }
        }

        /**
         * What was read off a payment slip, for the owner to check before it
         * reaches the form. An amount the reader could not be sure of is
         * shown as missing, never guessed. [onUse] gets the reading;
         * [onCancel] runs otherwise, after the kept photo has been deleted.
         */
        fun reviewPayment(
            activity: AppCompatActivity,
            payment: ScannedPayment,
            keptPhoto: String?,
            onUse: (ScannedPayment) -> Unit,
            onCancel: () -> Unit
        ) {
            val dp = activity.resources.displayMetrics.density
            val pad = (24 * dp).toInt()
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, (8 * dp).toInt(), pad, 0)
            }
            fun line(text: String, color: Int = R.color.ink, bold: Boolean = false) {
                column.addView(TextView(activity).apply {
                    this.text = text
                    textSize = 14f
                    setTextColor(activity.getColor(color))
                    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
                })
            }
            if (payment.amount != null) {
                line(activity.getString(R.string.payment_scan_amount, Format.money(payment.amount)), bold = true)
            } else {
                line(activity.getString(R.string.payment_scan_no_amount), R.color.gold_accent, bold = true)
            }
            payment.reference?.let { line(activity.getString(R.string.payment_scan_reference, it)) }
            payment.date?.let { line(activity.getString(R.string.bill_scan_date, Format.dateOnly(it))) }
            line(activity.getString(R.string.bill_scan_check), R.color.text_muted)

            var used = false
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.payment_scan_found_title)
                .setView(column)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.payment_scan_use) { _, _ ->
                    used = true
                    onUse(payment)
                }
                .setOnDismissListener {
                    if (!used) {
                        keptPhoto?.let { path -> AppScope.launch { runCatching { java.io.File(path).delete() } } }
                        onCancel()
                    }
                }
                .show()
        }

        /**
         * The plain message for a reading that found nothing usable.
         * [forPayment]: the slip's own wording instead of the bill's.
         */
        fun showProblem(activity: AppCompatActivity, outcome: Outcome, forPayment: Boolean = false, then: () -> Unit) {
            val message = when {
                outcome is Outcome.Unavailable -> R.string.bill_scan_unavailable
                forPayment -> R.string.payment_scan_nothing
                else -> R.string.bill_scan_nothing
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(if (forPayment) R.string.payment_scan else R.string.bill_scan)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener { then() }
                .show()
        }

        /**
         * What was read, for the owner to check before any of it reaches a
         * form: the bill's number, date and total, every product line with a
         * tick (all ticked), and whether the lines add up to the bill's own
         * total. [onUse] gets the ticked lines; [onCancel] runs otherwise,
         * after the kept photo has been deleted.
         *
         * [showSupplier]: the company's name is the point of a supplier's
         * bill; on a customer's account it is only noise.
         */
        fun review(
            activity: AppCompatActivity,
            bill: ScannedBill,
            keptPhoto: String?,
            showSupplier: Boolean,
            onUse: (List<ScannedItem>) -> Unit,
            onCancel: () -> Unit
        ) {
            val dp = activity.resources.displayMetrics.density
            val pad = (24 * dp).toInt()
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, (8 * dp).toInt(), pad, 0)
            }
            fun line(text: String, color: Int = R.color.ink, bold: Boolean = false) {
                column.addView(TextView(activity).apply {
                    this.text = text
                    textSize = 14f
                    setTextColor(activity.getColor(color))
                    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
                })
            }

            if (showSupplier) bill.supplierName?.let { line(activity.getString(R.string.bill_scan_supplier, it), bold = true) }
            bill.billNumber?.let { line(activity.getString(R.string.bill_scan_number, it)) }
            bill.billDate?.let { line(activity.getString(R.string.bill_scan_date, Format.dateOnly(it))) }
            bill.total?.let { line(activity.getString(R.string.bill_scan_total, Format.money(it)), bold = true) }

            val boxes = bill.items.map { item ->
                CheckBox(activity).apply {
                    isChecked = true
                    text = buildString {
                        append(activity.getString(
                            R.string.bill_scan_item_line, item.name, Format.plain(item.quantity),
                            Format.money(item.rate), Format.money(item.amount)
                        ))
                        item.batch?.let { append("\n").append(activity.getString(R.string.batch_label, it)) }
                        item.expiry?.let { append("\n").append(activity.getString(R.string.bill_scan_expiry, Format.dateOnly(it))) }
                    }
                    setTextColor(activity.getColor(R.color.ink))
                    setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
                }.also { column.addView(it) }
            }

            if (bill.items.isNotEmpty() && bill.total != null) {
                val sum = bill.itemsTotal
                if (kotlin.math.abs(sum - bill.total) < 1.0) {
                    line(activity.getString(R.string.bill_scan_sum_matches), R.color.green_got_text, bold = true)
                } else {
                    line(activity.getString(R.string.bill_scan_sum_differs, Format.money(sum), Format.money(bill.total)), R.color.gold_accent, bold = true)
                }
            }
            if (bill.items.isEmpty()) line(activity.getString(R.string.bill_scan_no_items), R.color.text_muted)
            line(activity.getString(R.string.bill_scan_check), R.color.text_muted)

            val scroll = ScrollView(activity).apply {
                addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            var used = false
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.bill_scan_found_title)
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.bill_scan_use) { _, _ ->
                    used = true
                    onUse(bill.items.filterIndexed { i, _ -> boxes[i].isChecked })
                }
                .setOnDismissListener {
                    if (!used) {
                        keptPhoto?.let { path -> AppScope.launch { runCatching { java.io.File(path).delete() } } }
                        onCancel()
                    }
                }
                .show()
        }
    }
}
