package com.innovation313.roshankhata.ui

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * The "Customer's signature" row (item_signature_row) on the new-entry form
 * and in Edit (18 Oct). It holds the signature only in memory — [current] —
 * and the screen decides when to file it: a new entry has no number until
 * it is saved, and Edit files it only on Save, so Cancel leaves the entry's
 * signature exactly as it was.
 *
 * [current] is the picture to keep (a new one, or the existing one), and
 * [changed] says whether it differs from what the entry had: a new picture,
 * or removed (current == null after having one).
 */
class SignatureRow(
    private val activity: Activity,
    row: View,
    private val about: () -> SignatureSheet.About,
    existing: Bitmap? = null
) {
    var current: Bitmap? = existing
        private set
    var changed: Boolean = false
        private set

    private val status = row.findViewById<TextView>(R.id.tvSigRowStatus)
    private val thumb = row.findViewById<ImageView>(R.id.ivSigRowThumb)
    private val action = row.findViewById<MaterialButton>(R.id.btnSigRowAction)
    private val remove = row.findViewById<View>(R.id.btnSigRowRemove)

    init {
        val open = View.OnClickListener {
            SignatureSheet.show(activity, about()) { bmp ->
                current = bmp
                changed = true
                paint()
            }
        }
        row.setOnClickListener(open)
        action.setOnClickListener(open)
        remove.setOnClickListener {
            current = null
            changed = true
            paint()
        }
        paint()
    }

    /** The signature the entry already has, once read; ignored if the owner already changed it. */
    fun showExisting(bitmap: Bitmap?) {
        if (changed) return
        current = bitmap
        paint()
    }

    /** Back to "not signed" for the next entry on the same form. */
    fun reset() {
        current = null
        changed = false
        paint()
    }

    private fun paint() {
        val sig = current
        thumb.setImageBitmap(sig)
        thumb.visibility = if (sig != null) View.VISIBLE else View.GONE
        remove.visibility = if (sig != null) View.VISIBLE else View.GONE
        action.setText(if (sig != null) R.string.sig_change else R.string.sig_sign)
        status.setText(if (sig != null) R.string.sig_done else R.string.sig_none)
        status.setTextColor(
            ContextCompat.getColor(activity, if (sig != null) R.color.brand_green_text else R.color.text_muted)
        )
    }
}
