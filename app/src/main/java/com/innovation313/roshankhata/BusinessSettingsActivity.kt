package com.innovation313.roshankhata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.ProfileChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Business Profile: everything the shop prints on its invoices, statements
 * and reports — name, address, STRN, bank and wallet rows, payment QR, stamp,
 * signature, terms.
 *
 * Redesigned 30 Sep 2026 (owner-approved mockup): labelled Material fields
 * whose label stays visible after typing, a live preview of the invoice
 * header, Add/Change buttons that say what they will do, a pinned Save with
 * an "unsaved changes" line, and a question before leaving with unsaved
 * edits. IBAN and wallet numbers are checked as typed; a warning never
 * blocks Save, because an account number that is not an IBAN is legitimate.
 *
 * The QR is picked through the system photo picker, which needs no storage
 * permission — the user grants access to exactly the one image they choose,
 * and nothing else.
 */
class BusinessSettingsActivity : BaseActivity() {

    private lateinit var etBusinessName: EditText
    private lateinit var etBusinessAddress: EditText
    private lateinit var ivQrPreview: ImageView
    private lateinit var tvNoQr: View
    private lateinit var btnRemoveQr: MaterialButton

    private lateinit var ivSignaturePreview: ImageView
    private lateinit var tvNoSignature: View
    private lateinit var btnRemoveSignature: MaterialButton

    private lateinit var ivStampPreview: ImageView
    private lateinit var tvNoStamp: View
    private lateinit var btnRemoveStamp: MaterialButton

    // The small "how this prints" card at the top of the screen. It mirrors
    // the same three things a statement actually shows — name, stamp, QR —
    // so a mistake is caught here, not on a document a customer already has.
    private lateinit var tvPreviewBusinessName: TextView
    private lateinit var tvPreviewInitials: TextView
    private lateinit var tvPreviewDetails: TextView
    private lateinit var ivPreviewStampThumb: ImageView
    private lateinit var tvPreviewStampPlaceholder: View
    private lateinit var ivPreviewQrThumb: ImageView
    private lateinit var tvPreviewQrPlaceholder: View

    private lateinit var etBankName: EditText
    private lateinit var etBankTitle: EditText
    private lateinit var etBankIban: EditText
    private lateinit var etBankJazzCash: EditText
    private lateinit var etInvoiceTerms: EditText
    private lateinit var etStrn: EditText
    private lateinit var tvUnsaved: TextView
    private lateinit var btnPickQr: MaterialButton
    private lateinit var btnPickStamp: MaterialButton
    private lateinit var btnPickSignature: MaterialButton

    /** The text fields as they were loaded, to tell whether anything changed. */
    private var saved: List<String> = emptyList()
    private val fields: List<EditText> get() = listOf(
        etBusinessName, etBusinessAddress, etStrn,
        etBankName, etBankTitle, etBankIban, etBankJazzCash, etInvoiceTerms
    )
    private fun current(): List<String> = fields.map { it.text.toString().trim() }
    private val isDirty: Boolean get() = current() != saved

    /** Asks before a back press throws typed changes away; only armed while something is unsaved. */
    private val leaveGuard = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            MaterialAlertDialogBuilder(this@BusinessSettingsActivity)
                .setTitle(R.string.bp_leave_title)
                .setMessage(R.string.bp_leave_msg)
                .setNegativeButton(R.string.bp_leave_keep, null)
                .setPositiveButton(R.string.bp_leave_discard) { _, _ ->
                    isEnabled = false
                    finish()
                }
                .show()
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) launchCrop(uri, "qr", cropQrResult)
    }

    /**
     * A launcher of its own rather than one shared with a flag: two images are
     * being chosen on this screen, and a payment code saved as a signature —
     * or the reverse — is not a mistake worth risking to save a few lines.
     */
    private val pickSignature = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) launchCrop(uri, "ink", cropSignatureResult)
    }

    private val pickStamp = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) launchCrop(uri, "ink", cropStampResult)
    }

    // Each picker above hands its picked photo to ImageCropActivity first —
    // an auto-detected rectangle to confirm or drag into place, rather than
    // an automatic crop trusted outright. One result launcher per field, for
    // the same reason as the three pickers above: saving the wrong crop into
    // the wrong field is not a risk worth a shared callback.
    private val cropQrResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleCropResult(result) { uri -> saveQr(uri) } }

    private val cropSignatureResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleCropResult(result) { uri -> saveSignature(uri) } }

    private val cropStampResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleCropResult(result) { uri -> saveStamp(uri) } }

    private fun launchCrop(
        sourceUri: Uri,
        mode: String,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        launcher.launch(
            Intent(this, ImageCropActivity::class.java)
                .putExtra(ImageCropActivity.EXTRA_SOURCE_URI, sourceUri.toString())
                .putExtra(ImageCropActivity.EXTRA_MODE, mode)
        )
    }

    private fun handleCropResult(
        result: androidx.activity.result.ActivityResult,
        onCropped: (Uri) -> Unit
    ) {
        if (result.resultCode != RESULT_OK) return
        val path = result.data?.getStringExtra(ImageCropActivity.EXTRA_RESULT_PATH) ?: return
        onCropped(Uri.fromFile(java.io.File(path)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_settings)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        etBusinessName = findViewById(R.id.etBusinessName)
        etBusinessAddress = findViewById(R.id.etBusinessAddress)
        ivQrPreview = findViewById(R.id.ivQrPreview)
        tvNoQr = findViewById(R.id.tvNoQr)
        btnRemoveQr = findViewById(R.id.btnRemoveQr)
        ivSignaturePreview = findViewById(R.id.ivSignaturePreview)
        tvNoSignature = findViewById(R.id.tvNoSignature)
        btnRemoveSignature = findViewById(R.id.btnRemoveSignature)
        ivStampPreview = findViewById(R.id.ivStampPreview)
        tvNoStamp = findViewById(R.id.tvNoStamp)
        btnRemoveStamp = findViewById(R.id.btnRemoveStamp)
        etBankName = findViewById(R.id.etBankName)
        etBankTitle = findViewById(R.id.etBankTitle)
        etBankIban = findViewById(R.id.etBankIban)
        etBankJazzCash = findViewById(R.id.etBankJazzCash)
        etInvoiceTerms = findViewById(R.id.etInvoiceTerms)
        etStrn = findViewById(R.id.etStrn)
        tvUnsaved = findViewById(R.id.tvUnsaved)
        btnPickQr = findViewById(R.id.btnPickQr)
        btnPickStamp = findViewById(R.id.btnPickStamp)
        btnPickSignature = findViewById(R.id.btnPickSignature)
        tvPreviewInitials = findViewById(R.id.tvPreviewInitials)
        tvPreviewDetails = findViewById(R.id.tvPreviewDetails)

        tvPreviewBusinessName = findViewById(R.id.tvPreviewBusinessName)
        ivPreviewStampThumb = findViewById(R.id.ivPreviewStampThumb)
        tvPreviewStampPlaceholder = findViewById(R.id.tvPreviewStampPlaceholder)
        ivPreviewQrThumb = findViewById(R.id.ivPreviewQrThumb)
        tvPreviewQrPlaceholder = findViewById(R.id.tvPreviewQrPlaceholder)

        etBusinessName.setText(BusinessProfile.businessName(this).orEmpty())
        etBusinessAddress.setText(BusinessProfile.businessAddress(this).orEmpty())
        etBankName.setText(BusinessProfile.bankName(this).orEmpty())
        etBankTitle.setText(BusinessProfile.bankAccountTitle(this).orEmpty())
        etBankIban.setText(BusinessProfile.bankIban(this).orEmpty())
        etBankJazzCash.setText(BusinessProfile.bankJazzCash(this).orEmpty())
        etInvoiceTerms.setText(BusinessProfile.termsAndConditions(this).orEmpty())
        etStrn.setText(BusinessProfile.strn(this).orEmpty())

        btnPickQr.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnRemoveQr.setOnClickListener { confirmRemoveQr() }

        btnPickSignature.setOnClickListener {
            pickSignature.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnRemoveSignature.setOnClickListener {
            // The remove button is a small icon now; one tap on it by accident
            // must not throw the signature away, so it asks first, as the QR does.
            confirmRemove(R.string.remove_signature, R.string.signature) {
                BusinessProfile.removeSignature(this)
                refreshSignature()
            }
        }

        btnPickStamp.setOnClickListener {
            pickStamp.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnRemoveStamp.setOnClickListener {
            confirmRemove(R.string.remove_stamp, R.string.stamp) {
                BusinessProfile.removeStamp(this)
                refreshStamp()
            }
        }

        findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            BusinessProfile.setBusinessName(
                this,
                etBusinessName.text.toString().trim().ifEmpty { null }
            )
            BusinessProfile.setBusinessAddress(
                this,
                etBusinessAddress.text.toString().trim().ifEmpty { null }
            )
            BusinessProfile.setBankName(this, etBankName.text.toString().trim().ifEmpty { null })
            BusinessProfile.setBankAccountTitle(this, etBankTitle.text.toString().trim().ifEmpty { null })
            BusinessProfile.setBankIban(this, etBankIban.text.toString().trim().ifEmpty { null })
            BusinessProfile.setBankJazzCash(this, etBankJazzCash.text.toString().trim().ifEmpty { null })
            BusinessProfile.setTermsAndConditions(this, etInvoiceTerms.text.toString().trim().ifEmpty { null })
            BusinessProfile.setStrn(this, etStrn.text.toString().trim().ifEmpty { null })
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
            leaveGuard.isEnabled = false
            finish()
        }

        val swPhoto = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(
            R.id.swPhotoOnStatement
        )
        swPhoto.isChecked = BusinessProfile.photoOnStatement(this)
        swPhoto.setOnCheckedChangeListener { _, on ->
            BusinessProfile.setPhotoOnStatement(this, on)
        }

        // The preview tracks every keystroke, so it always matches the
        // fields below it — a mistake is caught here, not on a document a
        // customer already has.
        saved = current()
        onBackPressedDispatcher.addCallback(this, leaveGuard)
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                refreshPreviewText()
                val dirty = isDirty
                tvUnsaved.visibility = if (dirty) View.VISIBLE else View.GONE
                leaveGuard.isEnabled = dirty
            }
        }
        fields.forEach { it.addTextChangedListener(watcher) }
        refreshPreviewText()

        // IBAN and wallet number: checked when the owner leaves the box, and
        // cleared as soon as it is corrected. A warning, never a block.
        val tilIban = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilBankIban)
        val tilWallet = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilBankJazzCash)
        fun checkIban() {
            tilIban.error = if (ProfileChecks.ibanLooksWrong(etBankIban.text.toString())) getString(R.string.bp_iban_bad) else null
        }
        fun checkWallet() {
            tilWallet.error = if (ProfileChecks.mobileLooksWrong(etBankJazzCash.text.toString())) getString(R.string.bp_mobile_bad) else null
        }
        etBankIban.setOnFocusChangeListener { _, has -> if (!has) checkIban() }
        etBankJazzCash.setOnFocusChangeListener { _, has -> if (!has) checkWallet() }
        etBankIban.addTextChangedListener(afterChange { if (tilIban.error != null) checkIban() })
        etBankJazzCash.addTextChangedListener(afterChange { if (tilWallet.error != null) checkWallet() })
        checkIban()
        checkWallet()

        refreshQr()
        refreshSignature()
        refreshStamp()
    }

    private fun afterChange(block: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = block()
    }

    /**
     * The preview's name, the initials in its logo square, and one detail
     * line — address and STRN — exactly as the invoice header prints them.
     */
    private fun refreshPreviewText() {
        val name = etBusinessName.text.toString().trim()
        tvPreviewBusinessName.text = name.ifEmpty { getString(R.string.app_name) }
        tvPreviewInitials.text = ProfileChecks.initials(name.ifEmpty { getString(R.string.app_name) })
        val details = listOfNotNull(
            etBusinessAddress.text.toString().trim().ifEmpty { null },
            etStrn.text.toString().trim().ifEmpty { null }?.let { "STRN $it" }
        ).joinToString(" \u00B7 ")
        tvPreviewDetails.text = details
        tvPreviewDetails.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmRemove(title: Int, message: Int, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(title) { _, _ -> onYes() }
            .show()
    }

    private fun saveSignature(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BusinessProfile.saveSignature(this@BusinessSettingsActivity, uri)
            }
            Toast.makeText(
                this@BusinessSettingsActivity,
                if (ok) R.string.signature_saved else R.string.signature_save_failed,
                Toast.LENGTH_SHORT
            ).show()
            refreshSignature()
        }
    }

    private fun refreshSignature() {
        lifecycleScope.launch {
            val signature = withContext(Dispatchers.IO) {
                BusinessProfile.loadSignature(this@BusinessSettingsActivity)
            }
            if (signature == null) {
                ivSignaturePreview.visibility = android.view.View.GONE
                tvNoSignature.visibility = android.view.View.VISIBLE
                btnRemoveSignature.visibility = android.view.View.GONE
                btnPickSignature.setText(R.string.bp_add)
            } else {
                ivSignaturePreview.setImageBitmap(signature)
                ivSignaturePreview.visibility = android.view.View.VISIBLE
                tvNoSignature.visibility = android.view.View.GONE
                btnRemoveSignature.visibility = android.view.View.VISIBLE
                btnPickSignature.setText(R.string.bp_change)
            }
        }
    }

    private fun saveStamp(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BusinessProfile.saveStamp(this@BusinessSettingsActivity, uri)
            }
            Toast.makeText(
                this@BusinessSettingsActivity,
                if (ok) R.string.stamp_saved else R.string.stamp_save_failed,
                Toast.LENGTH_SHORT
            ).show()
            refreshStamp()
        }
    }

    /**
     * Decoding happens on IO, not here.
     *
     * These three images are full-resolution — they have to be, since the
     * same files get drawn into invoices and statements at print quality.
     * Decoding all three on the main thread is what made this screen take a
     * visible moment to open; the work moves off, and the views are set when
     * it lands.
     */
    private fun refreshStamp() {
        lifecycleScope.launch {
            val stamp = withContext(Dispatchers.IO) {
                BusinessProfile.loadStamp(this@BusinessSettingsActivity)
            }
            if (stamp == null) {
                ivStampPreview.visibility = View.GONE
                tvNoStamp.visibility = View.VISIBLE
                btnRemoveStamp.visibility = View.GONE
                btnPickStamp.setText(R.string.bp_add)
                ivPreviewStampThumb.visibility = View.GONE
                tvPreviewStampPlaceholder.visibility = View.VISIBLE
            } else {
                ivStampPreview.setImageBitmap(stamp)
                ivStampPreview.visibility = View.VISIBLE
                tvNoStamp.visibility = View.GONE
                btnRemoveStamp.visibility = View.VISIBLE
                btnPickStamp.setText(R.string.bp_change)
                ivPreviewStampThumb.setImageBitmap(stamp)
                ivPreviewStampThumb.visibility = View.VISIBLE
                tvPreviewStampPlaceholder.visibility = View.GONE
            }
        }
    }

    private fun saveQr(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BusinessProfile.saveQr(this@BusinessSettingsActivity, uri)
            }

            if (!ok) {
                Toast.makeText(
                    this@BusinessSettingsActivity,
                    R.string.qr_save_failed,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            refreshQr()

            // Make the owner look at what was actually saved before it starts
            // going out to customers. A wrong code here sends real money to
            // the wrong account, and no amount of care later undoes that.
            MaterialAlertDialogBuilder(this@BusinessSettingsActivity)
                .setTitle(R.string.qr_confirm_title)
                .setMessage(R.string.qr_confirm_message)
                .setNegativeButton(R.string.remove_qr) { _, _ ->
                    BusinessProfile.removeQr(this@BusinessSettingsActivity)
                    refreshQr()
                    Toast.makeText(
                        this@BusinessSettingsActivity,
                        R.string.qr_removed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setPositiveButton(R.string.qr_confirm_yes) { _, _ ->
                    Toast.makeText(
                        this@BusinessSettingsActivity,
                        R.string.qr_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun confirmRemoveQr() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_qr)
            .setMessage(R.string.payment_qr)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove_qr) { _, _ ->
                BusinessProfile.removeQr(this)
                refreshQr()
                Toast.makeText(this, R.string.qr_removed, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun refreshQr() {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                BusinessProfile.loadQr(this@BusinessSettingsActivity)
            }

            if (bitmap != null) {
                ivQrPreview.setImageBitmap(bitmap)
                ivQrPreview.visibility = View.VISIBLE
                tvNoQr.visibility = View.GONE
                btnRemoveQr.visibility = View.VISIBLE
                btnPickQr.setText(R.string.bp_change)
                ivPreviewQrThumb.setImageBitmap(bitmap)
                ivPreviewQrThumb.visibility = View.VISIBLE
                tvPreviewQrPlaceholder.visibility = View.GONE
            } else {
                ivQrPreview.visibility = View.GONE
                tvNoQr.visibility = View.VISIBLE
                btnRemoveQr.visibility = View.GONE
                btnPickQr.setText(R.string.bp_add)
                ivPreviewQrThumb.visibility = View.GONE
                tvPreviewQrPlaceholder.visibility = View.VISIBLE
            }
        }
    }
}
