package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.innovation313.roshankhata.R

/**
 * The Home drawer (6 Oct): the three-line menu. Every feature of the grid,
 * in the grid's own sections, with the open business's card in its header.
 * The settings (lock, privacy, language, text size, theme, help, about)
 * lived at its foot until 7 Oct; they are the ⋮ menu beside the bell now
 * (MoreSheet.showMenu), so the drawer is features and nothing else. Opens
 * from the reading side: the right in Urdu, Sindhi, Persian and Arabic.
 */
object HomeDrawer {

    class Entry(val icon: Int, val tint: Int, val label: Int, val open: () -> Unit)

    private fun heading(activity: Activity, content: LinearLayout, label: Int) {
        content.addView(TextView(activity).apply {
            setText(label)
            setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
            textSize = 12f
            letterSpacing = 0.08f
            val dp = activity.resources.displayMetrics.density
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt())
        })
    }

    private fun row(activity: Activity, content: LinearLayout, drawer: DrawerLayout, e: Entry) {
        val v = LayoutInflater.from(activity).inflate(R.layout.item_drawer_row, content, false)
        TextFit.relax(v)
        v.findViewById<ImageView>(R.id.ivDrawerIcon).setImageResource(e.icon)
        v.findViewById<FrameLayout>(R.id.drawerIconDisc).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(activity, e.tint))
        v.findViewById<TextView>(R.id.tvDrawerLabel).setText(e.label)
        v.setOnClickListener { drawer.closeDrawers(); e.open() }
        content.addView(v)
    }

    fun fill(activity: Activity, drawer: DrawerLayout, content: LinearLayout, daily: List<Entry>, business: List<Entry>, tools: List<Entry>) {
        content.removeAllViews()
        heading(activity, content, R.string.home_sec_daily)
        daily.forEach { row(activity, content, drawer, it) }
        heading(activity, content, R.string.home_sec_business)
        business.forEach { row(activity, content, drawer, it) }
        // The slider's four doors (cash flow, rate list, supplier rates,
        // poster), so each has a fixed place as well as a turning card.
        heading(activity, content, R.string.home_section_tools)
        tools.forEach { row(activity, content, drawer, it) }
        content.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(1, (24 * activity.resources.displayMetrics.density).toInt())
        })
    }
}
