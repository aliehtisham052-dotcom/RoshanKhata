package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.innovation313.roshankhata.AndroidAutomationSettings
import com.innovation313.roshankhata.AutomationWorker
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.Automations
import com.innovation313.roshankhata.data.DateWords
import java.util.Calendar

/**
 * The automations' switches and hours (31 Oct 2026, More → Automations): one block per automation — its
 * switch with its name, and under it the hour it speaks at (a button that offers the 24 hours). The hour is
 * under the name, never beside it: at 150% text on a 360 dp phone a row of name and button left the name a
 * few letters wide (lesson 58). Kept on this phone ([AndroidAutomationSettings]).
 */
object AutomationsDialog {

    fun show(activity: Activity) {
        val settings = AndroidAutomationSettings(activity)
        val words = AndroidWords(activity)
        val d = activity.resources.displayMetrics.density
        val help = TextView(activity).apply {
            text = activity.getString(R.string.auto_help)
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
        }
        val blocks = Automations.Kind.values().map { kind ->
            val time = MaterialButton(activity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                isAllCaps = false
                letterSpacing = 0f
                text = activity.getString(R.string.auto_at_time, hourWord(settings.hour(kind)))
                setTextColor(ContextCompat.getColor(activity, R.color.brand_green_text))
                isEnabled = settings.isOn(kind)
                setOnClickListener { pickHour(activity, settings.hour(kind)) { h -> settings.setHour(kind, h); text = activity.getString(R.string.auto_at_time, hourWord(h)) } }
            }
            val switch = SwitchMaterial(activity).apply {
                text = words.text(kind.word)
                textSize = 16f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                isChecked = settings.isOn(kind)
                setOnCheckedChangeListener { _, on -> settings.setOn(kind, on); time.isEnabled = on }
            }
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(switch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(time, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        // Notifications off for the app (3 Nov 2026 audit, M24): every switch read ON while nothing could be said.
        // Said first, with the way to turn them on in the phone's own settings.
        val off = !androidx.core.app.NotificationManagerCompat.from(activity).areNotificationsEnabled()
        val warning: List<View> = if (!off) emptyList() else listOf<View>(
            TextView(activity).apply {
                text = activity.getString(R.string.auto_notifications_off)
                textSize = 14f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setTextColor(ContextCompat.getColor(activity, R.color.red_gave_text))
            },
            MaterialButton(activity).apply {
                isAllCaps = false
                text = activity.getString(R.string.auto_notifications_turn_on)
                setOnClickListener { openNotificationSettings(activity) }
            }
        )
        val views = ArrayList<View>().apply { addAll(warning); add(help); addAll(blocks) }
        val body = FormBox.column(activity, *views.toTypedArray())
        body.setPadding(body.paddingLeft, body.paddingTop, body.paddingRight, (8 * d).toInt())
        val scroll = ScrollView(activity).apply { addView(body) }
        TextFit.relax(scroll)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.auto_title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ -> }
            .setOnDismissListener { AutomationWorker.schedule(activity) }
            .show()
    }

    /** The phone's notification settings for this app (Android 8+), else its app page. */
    fun openNotificationSettings(activity: Activity) {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 26)
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.packageName)
        else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", activity.packageName, null))
        runCatching { activity.startActivity(intent) }
    }

    /** An hour in the shop's clock ("9:00 AM", or "09:00" where the country writes 24 hours). */
    private fun hourWord(hour: Int): String {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0) }
        return DateWords.format("h:mm a", c.timeInMillis)
    }

    private fun pickHour(activity: Activity, current: Int, onPicked: (Int) -> Unit) {
        val labels = (0..23).map { hourWord(it) }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.auto_pick_hour)
            .setSingleChoiceItems(labels, current.coerceIn(0, 23)) { dialog, which -> onPicked(which); dialog.dismiss() }
            .show()
    }
}
