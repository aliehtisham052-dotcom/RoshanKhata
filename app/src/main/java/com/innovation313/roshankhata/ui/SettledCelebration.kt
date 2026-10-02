package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BusinessProfile
import java.io.File
import java.io.FileOutputStream

/**
 * "Aslam ka hisaab saaf ✓" (2 Oct) — the moment a customer's balance comes
 * down to zero from what he owed, by a payment.
 *
 * Who sees it: the SHOP. The customer is reached only through the card the
 * owner may choose to send (WhatsApp or any app), which carries no amount.
 * Celebrations wear thin, so the full dialog is shown the first five times;
 * after that a quiet bar with the same card button. With phone animations
 * off, the tick is shown finished.
 */
object SettledCelebration {

    private const val PREFS = "settled_celebration"
    private const val KEY_SHOWN = "shown"
    private const val FULL_TIMES = 5

    fun show(activity: Activity, partyName: String) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getInt(KEY_SHOWN, 0)
        prefs.edit().putInt(KEY_SHOWN, shown + 1).apply()
        val title = activity.getString(R.string.settled_title, partyName)

        if (shown >= FULL_TIMES) {
            Snackbar.make(activity.findViewById(android.R.id.content), title, 6000)
                .setAction(R.string.settled_send_card) { shareCard(activity, partyName) }
                .show()
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.view_settled, null)
        fill(activity, view, partyName)
        val iv = view.findViewById<ImageView>(R.id.ivSettledCheck)
        if (Motion.enabled(activity)) {
            iv.setImageResource(R.drawable.avd_settled_check)
            (iv.drawable as? Animatable)?.start()
        }
        MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setNegativeButton(R.string.ok, null)
            .setPositiveButton(R.string.settled_send_card) { _, _ -> shareCard(activity, partyName) }
            .show()
    }

    private fun fill(context: Context, view: View, partyName: String) {
        view.findViewById<TextView>(R.id.tvSettledTitle).text = context.getString(R.string.settled_title, partyName)
        view.findViewById<TextView>(R.id.tvSettledSub).text =
            context.getString(R.string.settled_sub, Format.dateOnly(System.currentTimeMillis()))
        val shop = BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() }
        view.findViewById<TextView>(R.id.tvSettledShop).apply {
            text = shop.orEmpty()
            visibility = if (shop == null) View.GONE else View.VISIBLE
        }
    }

    /** The card as a light-theme PNG, whatever the phone's theme, offered to the share sheet. */
    private fun shareCard(activity: Activity, partyName: String) {
        try {
            val light = activity.createConfigurationContext(
                Configuration(activity.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                }
            )
            val themed = android.view.ContextThemeWrapper(light, R.style.Theme_RoshanKhata)
            val card = LayoutInflater.from(themed).inflate(R.layout.view_settled, null)
            fill(themed, card, partyName)
            val width = (360 * light.resources.displayMetrics.density).toInt()
            card.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            val bitmap = Bitmap.createBitmap(card.measuredWidth, card.measuredHeight, Bitmap.Config.ARGB_8888)
            card.draw(Canvas(bitmap))

            val dir = File(activity.cacheDir, "receipts").apply { mkdirs() }
            val file = File(dir, "settled_card.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(share, activity.getString(R.string.share)))
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
