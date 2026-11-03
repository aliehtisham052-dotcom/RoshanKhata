package com.innovation313.roshankhata.ui

import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BillPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A picture the app keeps for a row (schema 43, 4 Nov 2026: a cashbook row's receipt, a cheque), the supplier
 * bill's viewer (BillsActivity.showBillPhoto) for any of them: full width, decoded off the main thread, and a file
 * that is no longer on this phone said in words rather than shown as nothing.
 */
object KeptPhoto {

    fun show(activity: AppCompatActivity, path: String, title: String) {
        activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BillPhoto.load(path) }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (bitmap == null) {
                Toast.makeText(activity, R.string.photo_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val image = ImageView(activity).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                contentDescription = title
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(ScrollView(activity).apply { addView(image) })
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }

    /** A row's line wears a small camera when its row keeps a picture; nothing when it does not. */
    fun mark(line: TextView, kept: Boolean) {
        if (!kept) {
            line.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        val icon = ContextCompat.getDrawable(line.context, R.drawable.ic_camera_small)?.mutate() ?: return
        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        line.setCompoundDrawablesRelative(icon, null, null, null)
        line.compoundDrawablePadding = (4 * line.resources.displayMetrics.density).toInt()
        TextViewCompat.setCompoundDrawableTintList(line, ContextCompat.getColorStateList(line.context, R.color.text_muted))
    }
}
