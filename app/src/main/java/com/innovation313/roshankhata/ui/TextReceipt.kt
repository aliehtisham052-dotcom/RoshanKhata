package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
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
import com.innovation313.roshankhata.data.EscPos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A receipt made of text — a counter ticket, the day report — drawn as a
 * picture the roll's width and sent to the paired Bluetooth ESC/POS printer
 * the way the entry receipt is (ReceiptPrinter). The printer choice is the
 * entry screen's: the paired devices, the last one used ticked. (POS Phase 8.)
 *
 * The printer's own settings live on this phone (21 Oct 2026, the counter's
 * settings): an 80 mm roll (576 dots) instead of 58 mm (384), the shop's logo
 * at the top — dithered, so a coloured logo prints as dots — and the cash
 * drawer wired to the printer opened before a cash sale's ticket.
 */
object TextReceipt {

    data class Settings(val wide: Boolean = false, val logo: Boolean = false, val drawer: Boolean = false) {
        val width: Int get() = if (wide) EscPos.WIDTH_80MM else EscPos.WIDTH_58MM
    }

    private fun prefs(context: Context) = context.getSharedPreferences("printer", Activity.MODE_PRIVATE)

    fun settings(context: Context): Settings = prefs(context).let {
        Settings(it.getBoolean(KEY_WIDE, false), it.getBoolean(KEY_LOGO, false), it.getBoolean(KEY_DRAWER, false))
    }

    fun setSettings(context: Context, s: Settings) {
        prefs(context).edit().putBoolean(KEY_WIDE, s.wide).putBoolean(KEY_LOGO, s.logo).putBoolean(KEY_DRAWER, s.drawer).apply()
    }

    private const val KEY_WIDE = "width_80mm"
    private const val KEY_LOGO = "logo"
    private const val KEY_DRAWER = "drawer"

    /** The logo's box at the top of the roll: at most this tall, and 60% of the roll across. */
    private const val LOGO_HEIGHT = 120

    fun picture(text: String, widthPx: Int = EscPos.WIDTH_58MM, logo: Bitmap? = null): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = if (widthPx > EscPos.WIDTH_58MM) 26f else 22f; typeface = Typeface.MONOSPACE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx - 16)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.1f).build()
        val mark = logo?.let { printable(it, (widthPx * 0.6).toInt(), LOGO_HEIGHT) }
        val top = mark?.let { it.height + 12 } ?: 0
        val bmp = Bitmap.createBitmap(widthPx, layout.height + 32 + top, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        mark?.let { canvas.drawBitmap(it, ((widthPx - it.width) / 2).toFloat(), 12f, null); it.recycle() }
        canvas.translate(8f, 16f + top)
        layout.draw(canvas)
        return bmp
    }

    /** The logo scaled into [maxW] x [maxH] (never enlarged), then dithered to black and white dots. */
    private fun printable(logo: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val scale = minOf(1f, maxW.toFloat() / logo.width, maxH.toFloat() / logo.height)
        val w = (logo.width * scale).toInt().coerceAtLeast(1)
        val h = (logo.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(logo, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== logo) scaled.recycle()
        return Bitmap.createBitmap(EscPos.dither(px, w, h), w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * The paired printers to choose from, then the text sent as a picture —
     * with the drawer's pulse first when the sale took [cash] and the drawer
     * setting is on.
     */
    fun print(activity: Activity, scope: LifecycleCoroutineScope, text: String, cash: Boolean = false) {
        val adapter = ReceiptPrinter.adapter(activity)
        if (adapter == null) { Toast.makeText(activity, R.string.print_no_bluetooth, Toast.LENGTH_LONG).show(); return }
        if (!adapter.isEnabled) { Toast.makeText(activity, R.string.print_bluetooth_off, Toast.LENGTH_LONG).show(); return }
        val devices = ReceiptPrinter.paired(activity)
        if (devices.isEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage(R.string.print_no_printer).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val prefs = prefs(activity)
        val settings = settings(activity)
        val last = devices.indexOfFirst { it.address == prefs.getString("address", null) }
        val choices = devices.map { ChoiceSheet.Option(ReceiptPrinter.label(it), icon = R.drawable.ic_print, iconTint = R.color.tile_calc_fg, tile = R.color.tile_calc_bg) }
        ChoiceSheet.show(activity, activity.getString(R.string.print_choose), choices, last) { which ->
            prefs.edit().putString("address", devices[which].address).apply()
            Toast.makeText(activity, R.string.print_sending, Toast.LENGTH_SHORT).show()
            val appContext = activity.applicationContext
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val logo = if (settings.logo) com.innovation313.roshankhata.data.BusinessProfile.loadLogo(appContext) else null
                    val bmp = picture(text, settings.width, logo)
                    try {
                        ReceiptPrinter.send(devices[which], EscPos.withDrawer(ReceiptPrinter.bytes(bmp, settings.width), cash && settings.drawer))
                        true
                    } catch (e: Exception) { false } finally { bmp.recycle(); logo?.recycle() }
                }
                Toast.makeText(activity, if (ok) R.string.print_done else R.string.print_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
