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
    private lateinit var ivQrPreview: ImageView
    private lateinit var tvNoQr: TextView
    private lateinit var btnRemoveQr: MaterialButton

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) saveQr(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_settings)

        etBusinessName = findViewById(R.id.etBusinessName)
        ivQrPreview = findViewById(R.id.ivQrPreview)
        tvNoQr = findViewById(R.id.tvNoQr)
        btnRemoveQr = findViewById(R.id.btnRemoveQr)

        etBusinessName.setText(BusinessProfile.businessName(this).orEmpty())

        findViewById<MaterialButton>(R.id.btnPickQr).setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnRemoveQr.setOnClickListener { confirmRemoveQr() }

        findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            BusinessProfile.setBusinessName(
                this,
                etBusinessName.text.toString().trim().ifEmpty { null }
            )
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        refreshQr()
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
