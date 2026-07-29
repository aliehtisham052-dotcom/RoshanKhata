package com.innovation313.roshankhata

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.BusinessProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The owner's own details: business name and payment QR.
 *
 * The QR is picked through the system photo picker, which needs no storage
 * permission — the user grants access to exactly the one image they choose,
 * and nothing else.
 */
class BusinessSettingsActivity : AppCompatActivity() {

    private lateinit var etBusinessName: EditText
    private lateinit var etBusinessAddress: EditText
    private lateinit var ivQrPreview: ImageView
    private lateinit var tvNoQr: TextView
    private lateinit var btnRemoveQr: MaterialButton

    private lateinit var ivSignaturePreview: ImageView
    private lateinit var tvNoSignature: TextView
    private lateinit var btnRemoveSignature: MaterialButton

    private lateinit var ivStampPreview: ImageView
    private lateinit var tvNoStamp: TextView
    private lateinit var btnRemoveStamp: MaterialButton

    private lateinit var etBankName: EditText
    private lateinit var etBankTitle: EditText
    private lateinit var etBankIban: EditText
    private lateinit var etBankJazzCash: EditText
    private lateinit var etInvoiceTerms: EditText

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) saveQr(uri)
    }

    /**
     * A launcher of its own rather than one shared with a flag: two images are
     * being chosen on this screen, and a payment code saved as a signature —
     * or the reverse — is not a mistake worth risking to save a few lines.
     */
    private val pickSignature = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) saveSignature(uri)
    }

    private val pickStamp = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) saveStamp(uri)
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

        etBusinessName.setText(BusinessProfile.businessName(this).orEmpty())
        etBusinessAddress.setText(BusinessProfile.businessAddress(this).orEmpty())
        etBankName.setText(BusinessProfile.bankName(this).orEmpty())
        etBankTitle.setText(BusinessProfile.bankAccountTitle(this).orEmpty())
        etBankIban.setText(BusinessProfile.bankIban(this).orEmpty())
        etBankJazzCash.setText(BusinessProfile.bankJazzCash(this).orEmpty())
        etInvoiceTerms.setText(BusinessProfile.termsAndConditions(this).orEmpty())

        findViewById<MaterialButton>(R.id.btnPickQr).setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnRemoveQr.setOnClickListener { confirmRemoveQr() }

        findViewById<MaterialButton>(R.id.btnPickSignature).setOnClickListener {
            pickSignature.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnRemoveSignature.setOnClickListener {
            BusinessProfile.removeSignature(this)
            refreshSignature()
        }

        findViewById<MaterialButton>(R.id.btnPickStamp).setOnClickListener {
            pickStamp.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        btnRemoveStamp.setOnClickListener {
            BusinessProfile.removeStamp(this)
            refreshStamp()
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
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        val swPhoto = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(
            R.id.swPhotoOnStatement
        )
        swPhoto.isChecked = BusinessProfile.photoOnStatement(this)
        swPhoto.setOnCheckedChangeListener { _, on ->
            BusinessProfile.setPhotoOnStatement(this, on)
        }

        refreshQr()
        refreshSignature()
        refreshStamp()
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
        val signature = BusinessProfile.loadSignature(this)
        if (signature == null) {
            ivSignaturePreview.visibility = android.view.View.GONE
            tvNoSignature.visibility = android.view.View.VISIBLE
            btnRemoveSignature.visibility = android.view.View.GONE
        } else {
            ivSignaturePreview.setImageBitmap(signature)
            ivSignaturePreview.visibility = android.view.View.VISIBLE
            tvNoSignature.visibility = android.view.View.GONE
            btnRemoveSignature.visibility = android.view.View.VISIBLE
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

    private fun refreshStamp() {
        val stamp = BusinessProfile.loadStamp(this)
        if (stamp == null) {
            ivStampPreview.visibility = View.GONE
            tvNoStamp.visibility = View.VISIBLE
            btnRemoveStamp.visibility = View.GONE
        } else {
            ivStampPreview.setImageBitmap(stamp)
            ivStampPreview.visibility = View.VISIBLE
            tvNoStamp.visibility = View.GONE
            btnRemoveStamp.visibility = View.VISIBLE
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
        val bitmap = BusinessProfile.loadQr(this)

        if (bitmap != null) {
            ivQrPreview.setImageBitmap(bitmap)
            ivQrPreview.visibility = View.VISIBLE
            tvNoQr.visibility = View.GONE
            btnRemoveQr.visibility = View.VISIBLE
        } else {
            ivQrPreview.visibility = View.GONE
            tvNoQr.visibility = View.VISIBLE
            btnRemoveQr.visibility = View.GONE
        }
    }
}
