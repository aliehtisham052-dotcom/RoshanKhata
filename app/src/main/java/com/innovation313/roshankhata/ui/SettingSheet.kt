package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * One personal setting as a sheet (8 Nov 2026, the owner: Automations and Theme look professional, Screen
 * privacy, App Lock and Payment Radar were plain dialogs with walls of text). The setting's icon and name, an
 * On / Off pill, a line or two on what it does, the long text behind "Learn more", and one button that names
 * the result. A button that turns something off or removes protection is in the app's night-aware red
 * (an outline, so its words keep their contrast on both looks). Only the presentation lives here: what a
 * button does is the caller's [onAction], exactly as the dialogs it replaced did it.
 */
object SettingSheet {

    fun show(
        activity: Activity,
        icon: Int,
        tile: Int,
        title: CharSequence,
        on: Boolean,
        summary: CharSequence,
        more: CharSequence?,
        actionLabel: CharSequence,
        danger: Boolean,
        onAction: () -> Unit
    ): BottomSheetDialog {
        val sheet = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_setting, null)
        val dp = activity.resources.displayMetrics.density

        view.findViewById<ImageView>(R.id.ivSettingIcon).setImageResource(icon)
        view.findViewById<FrameLayout>(R.id.settingTile).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(activity, tile))
        view.findViewById<TextView>(R.id.tvSettingTitle).text = title
        view.findViewById<TextView>(R.id.tvSettingSummary).text = summary

        val chip = view.findViewById<TextView>(R.id.tvSettingState)
        chip.setText(if (on) R.string.setting_state_on else R.string.setting_state_off)
        chip.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(activity, if (on) R.color.tile_khata_bg else R.color.page_line)
        )
        chip.setTextColor(ContextCompat.getColor(activity, if (on) R.color.brand_green_text else R.color.ink_soft))

        // The long text, behind a Learn more that opens and closes it. Nothing to expand: no link.
        val moreText = view.findViewById<TextView>(R.id.tvSettingMore)
        val learn = view.findViewById<TextView>(R.id.btnSettingLearn)
        if (more.isNullOrBlank()) {
            learn.visibility = View.GONE
        } else {
            moreText.text = more
            learn.setText(R.string.setting_learn_more)
            learn.setOnClickListener {
                val open = moreText.visibility != View.VISIBLE
                moreText.visibility = if (open) View.VISIBLE else View.GONE
                learn.setText(if (open) R.string.setting_learn_less else R.string.setting_learn_more)
            }
        }

        val action = view.findViewById<MaterialButton>(R.id.btnSettingAction)
        action.text = actionLabel
        if (danger) {
            val red = ContextCompat.getColor(activity, R.color.red_gave_text)
            action.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            action.strokeColor = ColorStateList.valueOf(red)
            action.strokeWidth = (2 * dp).toInt()
            action.setTextColor(red)
        }
        action.setOnClickListener {
            sheet.dismiss()
            onAction()
        }
        view.findViewById<View>(R.id.btnSettingClose).setOnClickListener { sheet.dismiss() }

        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
        return sheet
    }
}
