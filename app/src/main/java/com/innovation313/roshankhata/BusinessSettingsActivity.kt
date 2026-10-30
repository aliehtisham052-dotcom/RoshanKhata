package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.set

import com.innovation313.roshankhata.ui.ChoiceSheet
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
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.Trade
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
    private lateinit var etBusinessNameUrdu: EditText
    private lateinit var etBusinessAddress: EditText
    private lateinit var etBusinessPhone: EditText
    private lateinit var etBusinessEmail: EditText
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
    private lateinit var btnCurrency: MaterialButton
    private lateinit var btnCountry: MaterialButton
    private lateinit var btnTrade: MaterialButton

    /** Foldable sections; the flags feed their folded one-line summaries. */
    private val sections = com.innovation313.roshankhata.ui.ProfileSections(this)
    private var hasQr = false
    private var hasStamp = false
    private var hasSignature = false

    /** A shop that counts in rupees gets the Pakistani IBAN/mobile checks; any other, a general shape check. */
    private val pakistaniRules: Boolean get() = BusinessProfile.currency(this) == Currency.DEFAULT
    private lateinit var tvUnsaved: TextView
    private lateinit var btnPickQr: MaterialButton
    private lateinit var btnPickStamp: MaterialButton
    private lateinit var btnPickSignature: MaterialButton
    private lateinit var btnPickLogo: MaterialButton
    private lateinit var btnRemoveLogo: MaterialButton
    private lateinit var ivLogoPreview: ImageView
    private lateinit var ivNoLogo: View
    private lateinit var ivPreviewLogo: ImageView

    /** The text fields as they were loaded, to tell whether anything changed. */
    private var saved: List<String> = emptyList()
    private val fields: List<EditText> get() = listOf(
        etBusinessName, etBusinessNameUrdu, etBusinessAddress, etBusinessPhone, etBusinessEmail, etStrn,
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

    private val pickLogo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) launchCrop(uri, "ink", cropLogoResult)
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

    private val cropLogoResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleCropResult(result) { uri -> saveLogo(uri) } }

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
        etBusinessNameUrdu = findViewById(R.id.etBusinessNameUrdu)
        etBusinessAddress = findViewById(R.id.etBusinessAddress)
        etBusinessPhone = findViewById(R.id.etBusinessPhone)
        etBusinessEmail = findViewById(R.id.etBusinessEmail)
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
        // The wallet hint names the shop's country's services (21 Oct 2026): bKash in Dhaka, not JazzCash.
        // The services are said under the box, not in its one-line label, where they were cut (30 Oct 2026).
        com.innovation313.roshankhata.data.ShopCountry.current.wallets.takeIf { it.isNotBlank() }?.let {
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilBankJazzCash).helperText = it
        }
        etInvoiceTerms = findViewById(R.id.etInvoiceTerms)
        etStrn = findViewById(R.id.etStrn)
        btnCurrency = findViewById(R.id.btnCurrency)
        btnCountry = findViewById(R.id.btnCountry)
        btnCountry.setOnClickListener { chooseCountry() }
        renderCountry()
        btnTrade = findViewById(R.id.btnTrade)
        tvUnsaved = findViewById(R.id.tvUnsaved)
        btnPickQr = findViewById(R.id.btnPickQr)
        btnPickStamp = findViewById(R.id.btnPickStamp)
        btnPickSignature = findViewById(R.id.btnPickSignature)
        btnPickLogo = findViewById(R.id.btnPickLogo)
        btnRemoveLogo = findViewById(R.id.btnRemoveLogo)
        ivLogoPreview = findViewById(R.id.ivLogoPreview)
        ivNoLogo = findViewById(R.id.ivNoLogo)
        ivPreviewLogo = findViewById(R.id.ivPreviewLogo)
        tvPreviewInitials = findViewById(R.id.tvPreviewInitials)
        tvPreviewDetails = findViewById(R.id.tvPreviewDetails)

        tvPreviewBusinessName = findViewById(R.id.tvPreviewBusinessName)
        ivPreviewStampThumb = findViewById(R.id.ivPreviewStampThumb)
        tvPreviewStampPlaceholder = findViewById(R.id.tvPreviewStampPlaceholder)
        ivPreviewQrThumb = findViewById(R.id.ivPreviewQrThumb)
        tvPreviewQrPlaceholder = findViewById(R.id.tvPreviewQrPlaceholder)

        etBusinessName.setText(BusinessProfile.businessName(this).orEmpty())
        etBusinessNameUrdu.setText(BusinessProfile.businessNameUrdu(this).orEmpty())
        etBusinessAddress.setText(BusinessProfile.businessAddress(this).orEmpty())
        etBusinessPhone.setText(BusinessProfile.businessPhone(this).orEmpty())
        etBusinessEmail.setText(BusinessProfile.businessEmail(this).orEmpty())
        etBankName.setText(BusinessProfile.bankName(this).orEmpty())
        etBankTitle.setText(BusinessProfile.bankAccountTitle(this).orEmpty())
        etBankIban.setText(BusinessProfile.bankIban(this).orEmpty())
        etBankJazzCash.setText(BusinessProfile.bankJazzCash(this).orEmpty())
        etInvoiceTerms.setText(BusinessProfile.termsAndConditions(this).orEmpty())
        etStrn.setText(BusinessProfile.strn(this).orEmpty())
        renderCurrency()
        btnCurrency.setOnClickListener { chooseCurrency() }
        renderCalendar()
        findViewById<View>(R.id.btnCalendar).setOnClickListener { chooseCalendar() }
        renderTrade()
        btnTrade.setOnClickListener { chooseTrade() }

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

        btnPickLogo.setOnClickListener {
            pickLogo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnRemoveLogo.setOnClickListener {
            confirmRemove(R.string.bp_remove_logo, R.string.bp_logo) {
                BusinessProfile.removeLogo(this)
                refreshLogo()
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
            BusinessProfile.setBusinessNameUrdu(this, etBusinessNameUrdu.text.toString().trim().ifEmpty { null })
            BusinessProfile.setBusinessAddress(
                this,
                etBusinessAddress.text.toString().trim().ifEmpty { null }
            )
            BusinessProfile.setBusinessPhone(this, etBusinessPhone.text.toString().trim().ifEmpty { null })
            BusinessProfile.setBusinessEmail(this, etBusinessEmail.text.toString().trim().ifEmpty { null })
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
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swAskShare).apply {
            isChecked = BusinessProfile.askShareAfterEntry(this@BusinessSettingsActivity)
            setOnCheckedChangeListener { _, on -> BusinessProfile.setAskShareAfterEntry(this@BusinessSettingsActivity, on) }
        }
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swPayOnReminder).apply {
            isChecked = BusinessProfile.paymentOnReminder(this@BusinessSettingsActivity)
            setOnCheckedChangeListener { _, on -> BusinessProfile.setPaymentOnReminder(this@BusinessSettingsActivity, on) }
        }
        // Own digits on printed sheets, offered only where the language has digits of its own (21 Oct 2026).
        findViewById<android.view.View>(R.id.rowNativeDigits).visibility =
            if (com.innovation313.roshankhata.data.Digits.hasOwnDigits()) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swNativeDigits).apply {
            isChecked = com.innovation313.roshankhata.data.Digits.nativeOnPrint
            setOnCheckedChangeListener { _, on -> com.innovation313.roshankhata.data.PrintDigits.set(this@BusinessSettingsActivity, on) }
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
                sections.refreshAll()
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
            tilIban.error = if (ProfileChecks.ibanLooksWrong(etBankIban.text.toString(), pakistaniRules)) getString(R.string.bp_iban_bad) else null
        }
        fun checkWallet() {
            tilWallet.error = if (ProfileChecks.mobileLooksWrong(etBankJazzCash.text.toString(), pakistaniRules)) getString(R.string.bp_mobile_bad) else null
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
        refreshLogo()

        // Foldable sections (13 Oct): the shop's own details open, the rest
        // folded to one line each that says what they hold.
        sections.add(R.id.secIdentity, R.id.bodyIdentity, R.string.bp_section_identity,
            R.drawable.ic_storefront, startOpen = true) {
            listOf(
                etBusinessName.text.toString().trim(),
                etBusinessPhone.text.toString().trim(),
                Trade.current(this).label(this),
            ).filter { it.isNotEmpty() }.joinToString(" · ")
        }
        sections.add(R.id.secPaid, R.id.bodyPaid, R.string.bp_section_paid,
            R.drawable.ic_qr_scan, startOpen = false) {
            listOf(
                etBankName.text.toString().trim(),
                getString(if (hasQr) R.string.bp_qr else R.string.no_qr_yet),
            ).filter { it.isNotEmpty() }.joinToString(" · ")
        }
        sections.add(R.id.secSeals, R.id.bodySeals, R.string.bp_section_seals,
            R.drawable.ic_seal_stamp, startOpen = false) {
            val have = listOfNotNull(
                if (hasStamp) getString(R.string.stamp) else null,
                if (hasSignature) getString(R.string.signature) else null,
            )
            if (have.isEmpty()) getString(R.string.no_stamp_yet) else have.joinToString(" · ")
        }
        sections.add(R.id.secDocs, R.id.bodyDocs, R.string.bp_section_docs,
            R.drawable.ic_pdf_doc, startOpen = false) {
            etInvoiceTerms.text.toString().trim().lineSequence().firstOrNull().orEmpty()
        }

        // Opened from one row of the shop sheet (11 Oct): bring that part of
        // the profile into view rather than leaving the owner at the top of
        // a long screen to find it again.
        intent.getStringExtra(EXTRA_FOCUS)?.let { focus ->
            val targetId = when (focus) {
                FOCUS_NAME -> R.id.etBusinessName
                FOCUS_PHONE -> R.id.etBusinessPhone
                FOCUS_STAMP -> R.id.ivStampPreview
                FOCUS_PAYMENT -> R.id.etBankName
                FOCUS_TRADE -> R.id.btnTrade
                else -> 0
            }
            val scroll = findViewById<android.widget.ScrollView>(R.id.scroll)
            val target = if (targetId != 0) findViewById<View>(targetId) else null
            target?.let { sections.reveal(it) }
            if (scroll != null && target != null) scroll.post {
                var y = 0
                var v: View? = target
                while (v != null && v !== scroll) {
                    y += v.top
                    v = v.parent as? View
                }
                val margin = (24 * resources.displayMetrics.density).toInt()
                scroll.smoothScrollTo(0, (y - margin).coerceAtLeast(0))
            }
        }

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
            etBusinessPhone.text.toString().trim().ifEmpty { null },
            etStrn.text.toString().trim().ifEmpty { null }?.let { "${BusinessProfile.taxLabel(this)} $it" }
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

    private fun saveLogo(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BusinessProfile.saveLogo(this@BusinessSettingsActivity, uri)
            }
            Toast.makeText(
                this@BusinessSettingsActivity,
                if (ok) R.string.bp_logo_saved else R.string.bp_logo_save_failed,
                Toast.LENGTH_SHORT
            ).show()
            refreshLogo()
        }
    }

    /** The logo row and the preview's square: the logo when there is one, the initials when not. */
    private fun refreshLogo() {
        lifecycleScope.launch {
            val logo = withContext(Dispatchers.IO) {
                BusinessProfile.loadLogo(this@BusinessSettingsActivity)
            }
            if (logo == null) {
                ivLogoPreview.visibility = View.GONE
                ivNoLogo.visibility = View.VISIBLE
                btnRemoveLogo.visibility = View.GONE
                btnPickLogo.setText(R.string.bp_add)
                ivPreviewLogo.visibility = View.GONE
                tvPreviewInitials.visibility = View.VISIBLE
            } else {
                ivLogoPreview.setImageBitmap(logo)
                ivLogoPreview.visibility = View.VISIBLE
                ivNoLogo.visibility = View.GONE
                btnRemoveLogo.visibility = View.VISIBLE
                btnPickLogo.setText(R.string.bp_change)
                ivPreviewLogo.setImageBitmap(logo)
                ivPreviewLogo.visibility = View.VISIBLE
                tvPreviewInitials.visibility = View.GONE
            }
        }
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
                hasSignature = false
            } else {
                ivSignaturePreview.setImageBitmap(signature)
                ivSignaturePreview.visibility = android.view.View.VISIBLE
                tvNoSignature.visibility = android.view.View.GONE
                btnRemoveSignature.visibility = android.view.View.VISIBLE
                btnPickSignature.setText(R.string.bp_change)
                hasSignature = true
            }
            sections.refreshAll()
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
            hasStamp = stamp != null
            sections.refreshAll()
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
                hasQr = true
                ivPreviewQrThumb.setImageBitmap(bitmap)
                ivPreviewQrThumb.visibility = View.VISIBLE
                tvPreviewQrPlaceholder.visibility = View.GONE
            } else {
                ivQrPreview.visibility = View.GONE
                tvNoQr.visibility = View.VISIBLE
                btnRemoveQr.visibility = View.GONE
                btnPickQr.setText(R.string.bp_add)
                hasQr = false
                ivPreviewQrThumb.visibility = View.GONE
                tvPreviewQrPlaceholder.visibility = View.VISIBLE
            }
            sections.refreshAll()
        }
    }

    // ---------- Trade / shoba (4 Oct 2026) ----------

    private fun renderTrade() {
        btnTrade.text = Trade.current(this).label(this)
        sections.refreshAll()
    }

    /** Saved the moment it is chosen, like the currency: a setting, not a text field. */
    private fun chooseTrade() {
        com.innovation313.roshankhata.ui.TradePicker.show(this, Trade.current(this), required = false) {
            BusinessProfile.setTrade(this, it)
            renderTrade()
        }
    }

    // ---------- Currency sign (4 Oct 2026) ----------

    private fun calendarLabel(kind: com.innovation313.roshankhata.data.ShopCalendar.Kind): String = getString(when (kind) {
        com.innovation313.roshankhata.data.ShopCalendar.Kind.GREGORIAN -> R.string.calendar_gregorian
        com.innovation313.roshankhata.data.ShopCalendar.Kind.HIJRI -> R.string.calendar_hijri
        com.innovation313.roshankhata.data.ShopCalendar.Kind.JALALI -> R.string.calendar_jalali
    })

    private fun renderCalendar() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCalendar).text =
            calendarLabel(BusinessProfile.calendar(this))
    }

    private fun chooseCalendar() {
        val kinds = com.innovation313.roshankhata.data.ShopCalendar.Kind.values()
        val current = BusinessProfile.calendar(this)
        val choices = kinds.map {
            ChoiceSheet.Option(calendarLabel(it), icon = R.drawable.ic_calendar,
                iconTint = R.color.tile_insights_fg, tile = R.color.tile_insights_bg)
        }
        ChoiceSheet.show(this, getString(R.string.bp_calendar), choices, kinds.indexOf(current)) { which ->
            com.innovation313.roshankhata.data.ShopCalendar.set(this, kinds[which])
            renderCalendar()
        }
    }

    private fun renderCountry() {
        val c = BusinessProfile.country(this)
        btnCountry.text = if (c == com.innovation313.roshankhata.data.ShopCountry.OTHER) getString(R.string.country_other)
            else c.displayName(resources.configuration.locales[0])
    }

    /**
     * The shop's country (21 Oct 2026). Saved the moment it is chosen, like
     * the currency. Choosing a country also offers its currency and calendar
     * when the shop still has the defaults, so a Saudi shop is not left on
     * rupees; a sign the owner chose himself is kept.
     */
    private fun chooseCountry() {
        val locale = resources.configuration.locales[0]
        val current = BusinessProfile.country(this)
        val all = com.innovation313.roshankhata.data.ShopCountry.choices(locale)
        val choices = all.map { c ->
            if (c == com.innovation313.roshankhata.data.ShopCountry.OTHER)
                ChoiceSheet.Option(getString(R.string.country_other), icon = R.drawable.ic_hash,
                    iconTint = R.color.tile_settings_fg, tile = R.color.tile_settings_bg)
            else ChoiceSheet.Option(c.displayName(locale), glyph = c.code, glyphSp = 13f,
                iconTint = R.color.backup_ink, tile = R.color.tile_bills_bg)
        }
        ChoiceSheet.show(this, getString(R.string.bp_country), choices, all.indexOf(current)) { which ->
            val chosen = all[which]
            val wasDefaultCurrency = BusinessProfile.currency(this) == Currency.DEFAULT
            BusinessProfile.setCountry(this, chosen)
            if (wasDefaultCurrency && chosen != com.innovation313.roshankhata.data.ShopCountry.OTHER && chosen.currencySign != Currency.DEFAULT) {
                Currency.set(this, chosen.currencySign)
                renderCurrency()
            }
            if (BusinessProfile.calendar(this) == com.innovation313.roshankhata.data.ShopCalendar.Kind.GREGORIAN && chosen.calendar != com.innovation313.roshankhata.data.ShopCalendar.Kind.GREGORIAN) {
                com.innovation313.roshankhata.data.ShopCalendar.set(this, chosen.calendar)
                renderCalendar()
            }
            renderCountry()
        }
    }

    private fun renderCurrency() {
        btnCurrency.text = Currency.label(BusinessProfile.currency(this))
    }

    /**
     * Saved the moment it is chosen (like the statement-photo switch), not
     * with the text fields: a sign is a setting, and the owner should see
     * the whole app change at once. Figures are not converted — see [Currency].
     */
    private fun chooseCurrency() {
        val current = BusinessProfile.currency(this)
        // The sign itself sits in the tile (10 Oct), the code beside it; the
        // last card types any other sign.
        val choices = Currency.CHOICES.map { (sign, code) ->
            ChoiceSheet.Option(code, glyph = sign, glyphSp = if (sign.length > 2) 11f else 15f,
                iconTint = R.color.backup_ink, tile = R.color.tile_bills_bg)
        } + ChoiceSheet.Option(getString(R.string.currency_custom), icon = R.drawable.ic_hash,
            iconTint = R.color.tile_settings_fg, tile = R.color.tile_settings_bg)
        val checked = Currency.CHOICES.indexOfFirst { it.first == current }
        ChoiceSheet.show(this, getString(R.string.bp_currency), choices, checked) { which ->
            if (which < Currency.CHOICES.size) {
                Currency.set(this, Currency.CHOICES[which].first)
                renderCurrency()
            } else {
                typeCurrency(current)
            }
        }
    }

    private fun typeCurrency(current: String) {
        val input = EditText(this).apply {
            setText(current)
            hint = getString(R.string.currency_custom_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(Currency.MAX_LENGTH))
            setSingleLine()
            setSelection(text.length)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.currency_custom)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                Currency.set(this, input.text.toString())
                renderCurrency()
            }
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
    }

    companion object {
        /** Which part of the profile to bring into view: one of the FOCUS_ values. */
        const val EXTRA_FOCUS = "focus"
        const val FOCUS_NAME = "name"
        /** The phone field (the poster screen sends the owner here when it is empty). */
        const val FOCUS_PHONE = "phone"
        const val FOCUS_STAMP = "stamp"
        const val FOCUS_PAYMENT = "payment"
        const val FOCUS_TRADE = "trade"
    }
}
