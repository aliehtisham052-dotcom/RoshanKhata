package com.innovation313.roshankhata.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.innovation313.roshankhata.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A receipt made of text — a counter ticket, the day report — drawn as a
 * picture the roll's width (384 px for 58 mm) and sent to the paired
 * Bluetooth ESC/POS printer the way the entry receipt is (ReceiptPrinter).
 * The printer choice is the entry screen's: the paired devices, the last
 * one used ticked. (POS Phase 8.)
 */
object TextReceipt {

    fun picture(text: String, widthPx: Int = 384): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 22f; typeface = Typeface.MONOSPACE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx - 16)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.1f).build()
        val bmp = Bitmap.createBitmap(widthPx, layout.height + 32, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.translate(8f, 16f)
        layout.draw(canvas)
        return bmp
    }

    /** The paired printers to choose from, then the text sent as a picture. */
    fun print(activity: Activity, scope: LifecycleCoroutineScope, text: String) {
        val adapter = ReceiptPrinter.adapter(activity)
        if (adapter == null) { Toast.makeText(activity, R.string.print_no_bluetooth, Toast.LENGTH_LONG).show(); return }
        if (!adapter.isEnabled) { Toast.makeText(activity, R.string.print_bluetooth_off, Toast.LENGTH_LONG).show(); return }
        val devices = ReceiptPrinter.paired(activity)
        if (devices.isEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage(R.string.print_no_printer).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val prefs = activity.getSharedPreferences("printer", Activity.MODE_PRIVATE)
        val last = devices.indexOfFirst { it.address == prefs.getString("address", null) }
        val choices = devices.map { ChoiceSheet.Option(ReceiptPrinter.label(it), icon = R.drawable.ic_print, iconTint = R.color.tile_calc_fg, tile = R.color.tile_calc_bg) }
        ChoiceSheet.show(activity, activity.getString(R.string.print_choose), choices, last) { which ->
            prefs.edit().putString("address", devices[which].address).apply()
            Toast.makeText(activity, R.string.print_sending, Toast.LENGTH_SHORT).show()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val bmp = picture(text)
                    try { ReceiptPrinter.send(devices[which], ReceiptPrinter.bytes(bmp)); true } catch (e: Exception) { false } finally { bmp.recycle() }
                }
                Toast.makeText(activity, if (ok) R.string.print_done else R.string.print_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
