package com.innovation313.roshankhata

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.ImageAutoCrop
import com.innovation313.roshankhata.data.PhotoDecode
import com.innovation313.roshankhata.ui.CropOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Confirm or fix a crop before it is saved, instead of trusting an
 * automatic guess outright.
 *
 * Automatic detection (finding a QR's finder patterns, or the ink on a
 * signature/stamp photo) is best-effort — it can mistake another dark
 * thing in the frame, like a table surface or a shadow, for the real
 * content. Rather than keep tuning the guess, it becomes the STARTING
 * rectangle here, and the person is the one who confirms it is right —
 * the same pattern a profile-photo or ID-scan cropper uses, which works
 * regardless of what the background contains because a person is
 * deciding, not a pixel threshold.
 */
class ImageCropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE_URI = "source_uri"
        /** "qr" or "ink" — which auto-detector suggests the starting rectangle. */
        const val EXTRA_MODE = "mode"
        const val EXTRA_RESULT_PATH = "result_path"
    }

    private lateinit var ivSource: ImageView
    private lateinit var overlay: CropOverlayView
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_crop)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        setSupportActionBar(findViewById<Toolbar>(R.id.cropToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ivSource = findViewById(R.id.ivCropSource)
        overlay = findViewById(R.id.cropOverlay)

        findViewById<MaterialButton>(R.id.btnCropCancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        findViewById<MaterialButton>(R.id.btnCropUse).setOnClickListener { confirmCrop() }

        val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)?.let { Uri.parse(it) }
        val mode = intent.getStringExtra(EXTRA_MODE).orEmpty()

        if (sourceUri == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            // Generous edge: this bitmap is what actually gets cropped, and
            // BusinessProfile's own downscale runs again afterward — more
            // detail here means a more precise drag, not a bigger saved file.
            val bmp = withContext(Dispatchers.IO) {
                PhotoDecode.read(this@ImageCropActivity, sourceUri, 1200, keepShortEdge = false)
            }
            if (bmp == null) {
                Toast.makeText(this@ImageCropActivity, R.string.crop_load_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            bitmap = bmp
            ivSource.setImageBitmap(bmp)

            val suggestion = withContext(Dispatchers.Default) {
                if (mode == "qr") ImageAutoCrop.suggestQrRect(bmp) else ImageAutoCrop.suggestInkRect(bmp)
            }

            // Laid out AFTER the image is set and the frame has measured —
            // only then are the ImageView's actual displayed bounds known,
            // which is what the suggested rectangle needs to be placed in.
            ivSource.post { placeInitialRect(bmp, suggestion) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_CANCELED)
        finish()
        return true
    }

    /**
     * Where the bitmap actually lands inside the ImageView under
     * fitCenter — the ImageView is usually wider or taller than the image's
     * own aspect ratio, so there is letterboxing on two sides that a crop
     * rectangle must never be placed into.
     */
    private fun displayedImageRect(bmp: Bitmap): RectF {
        val ivW = ivSource.width.toFloat()
        val ivH = ivSource.height.toFloat()
        val scale = minOf(ivW / bmp.width, ivH / bmp.height)
        val dispW = bmp.width * scale
        val dispH = bmp.height * scale
        val offsetX = (ivW - dispW) / 2f
        val offsetY = (ivH - dispH) / 2f
        return RectF(offsetX, offsetY, offsetX + dispW, offsetY + dispH)
    }

    private fun placeInitialRect(bmp: Bitmap, suggestion: Rect?) {
        val disp = displayedImageRect(bmp)
        val scale = disp.width() / bmp.width

        val initial = if (suggestion != null) {
            RectF(
                disp.left + suggestion.left * scale,
                disp.top + suggestion.top * scale,
                disp.left + suggestion.right * scale,
                disp.top + suggestion.bottom * scale
            )
        } else {
            // No confident suggestion — an 80%-of-image centred box is a
            // reasonable starting point to drag from, rather than the
            // overlay simply not appearing at all.
            val margin = 0.10f
            RectF(
                disp.left + disp.width() * margin,
                disp.top + disp.height() * margin,
                disp.right - disp.width() * margin,
                disp.bottom - disp.height() * margin
            )
        }
        overlay.rect = initial
    }

    private fun confirmCrop() {
        val bmp = bitmap ?: return
        val disp = displayedImageRect(bmp)
        val scale = bmp.width / disp.width()
        val r = overlay.rect

        // View coordinates back to bitmap coordinates — the inverse of
        // placeInitialRect's mapping, clamped so a drag that reached the
        // letterboxed edge cannot ask for pixels outside the real bitmap.
        val left = ((r.left - disp.left) * scale).toInt().coerceIn(0, bmp.width - 1)
        val top = ((r.top - disp.top) * scale).toInt().coerceIn(0, bmp.height - 1)
        val right = ((r.right - disp.left) * scale).toInt().coerceIn(left + 1, bmp.width)
        val bottom = ((r.bottom - disp.top) * scale).toInt().coerceIn(top + 1, bmp.height)

        val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)

        val outFile = File(cacheDir, "crop_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(outFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.crop_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val result = Intent().putExtra(EXTRA_RESULT_PATH, outFile.absolutePath)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
