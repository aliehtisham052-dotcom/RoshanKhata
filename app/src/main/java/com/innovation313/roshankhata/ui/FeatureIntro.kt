package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * A feature's first-open sheet (1 Nov 2026). The owner liked a competitor's sheet in front of its
 * QR screen - three numbered pictures, one line on what it does for the shop, one button - and
 * asked where the app needs the same. Home already has its tour (CoachMarkController) and simple
 * screens explain themselves, so it is only the features whose rule is not obvious and costs a
 * mistake when missed, or whose use is easy to miss: [Topic].
 *
 * Once per feature on this phone (preferences, not the book: a second phone's owner has not seen
 * it); a rotation while it is open shows it again rather than counting as seen. Help → Feature
 * guides lists them all to see again. The pictures are the app's own tile drawings, never a photo
 * or anyone's data.
 */
object FeatureIntro {

    private const val PREFS = "feature_intro"

    /** One step: its picture, whether that picture is one colour (tinted) or the two-tone tile (drawn as is), its words. */
    class Step(@DrawableRes val icon: Int, val tint: Boolean, @StringRes val words: Int)

    enum class Topic(
        val key: String,
        @StringRes val title: Int,
        @StringRes val body: Int,
        @ColorRes val tile: Int,
        @DrawableRes val listIcon: Int,
        val steps: List<Step>
    ) {
        COUNTER("counter", R.string.pos_title, R.string.intro_counter_body, R.color.tile_cashbook_bg, R.drawable.ic_tile_invoice, listOf(
            Step(R.drawable.ic_tile_products, false, R.string.intro_counter_s1),
            Step(R.drawable.ic_people, true, R.string.intro_counter_s2),
            Step(R.drawable.ic_tile_cashbook, false, R.string.intro_counter_s3))),
        DOCUMENTS("documents", R.string.documents_title, R.string.intro_documents_body, R.color.tile_invoice_bg, R.drawable.ic_tile_invoice, listOf(
            Step(R.drawable.ic_pdf_doc, true, R.string.intro_documents_s1),
            Step(R.drawable.ic_truck, true, R.string.intro_documents_s2),
            Step(R.drawable.ic_tile_invoice, false, R.string.intro_documents_s3))),
        CASHBOOK("cashbook", R.string.coach_title_nav_cashbook, R.string.intro_cashbook_body, R.color.tile_cashbook_bg, R.drawable.ic_tile_cashbook, listOf(
            Step(R.drawable.ic_swap, true, R.string.intro_cashbook_s1),
            Step(R.drawable.ic_tile_calc, false, R.string.intro_cashbook_s2),
            Step(R.drawable.ic_check_circle, true, R.string.intro_cashbook_s3))),
        CHEQUES("cheques", R.string.coach_title_nav_cheques, R.string.intro_cheques_body, R.color.tile_cheques_bg, R.drawable.ic_tile_cheques, listOf(
            Step(R.drawable.ic_edit, true, R.string.intro_cheques_s1),
            Step(R.drawable.ic_calendar, true, R.string.intro_cheques_s2),
            Step(R.drawable.ic_tile_khata, false, R.string.intro_cheques_s3))),
        CUSTOMER_QR("customer_qr", R.string.customer_qr, R.string.intro_qr_body, R.color.tile_khata_bg, R.drawable.ic_qr_scan, listOf(
            Step(R.drawable.ic_qr_scan, true, R.string.intro_qr_s1),
            Step(R.drawable.ic_share, true, R.string.intro_qr_s2),
            Step(R.drawable.ic_tile_khata, false, R.string.intro_qr_s3))),
        STAFF("staff", R.string.staff_title, R.string.intro_staff_body, R.color.tile_settings_bg, R.drawable.ic_people, listOf(
            Step(R.drawable.ic_person, true, R.string.intro_staff_s1),
            Step(R.drawable.ic_calendar, true, R.string.intro_staff_s2),
            Step(R.drawable.ic_menu_lock, true, R.string.intro_staff_s3))),
        PLANS("plans", R.string.coach_title_nav_plans, R.string.intro_plans_body, R.color.tile_plans_bg, R.drawable.ic_tile_plans, listOf(
            Step(R.drawable.ic_people, true, R.string.intro_plans_s1),
            Step(R.drawable.ic_tile_plans, false, R.string.intro_plans_s2),
            Step(R.drawable.ic_bell, true, R.string.intro_plans_s3))),
        FOLLOWUP("followup", R.string.followup_title, R.string.intro_followup_body, R.color.tile_followup_bg, R.drawable.ic_tile_followup, listOf(
            Step(R.drawable.ic_list_all, true, R.string.intro_followup_s1),
            Step(R.drawable.ic_action_whatsapp, true, R.string.intro_followup_s2),
            Step(R.drawable.ic_check_circle, true, R.string.intro_followup_s3))),
        BACKUP("backup", R.string.coach_title_backup, R.string.intro_backup_body, R.color.tile_backup_bg, R.drawable.ic_tile_backup, listOf(
            Step(R.drawable.ic_tile_backup, false, R.string.intro_backup_s1),
            Step(R.drawable.ic_calendar, true, R.string.intro_backup_s2),
            Step(R.drawable.ic_swap, true, R.string.intro_backup_s3))),
        ORDERS("orders", R.string.po_title, R.string.intro_orders_body, R.color.tile_stock_bg, R.drawable.ic_truck, listOf(
            Step(R.drawable.ic_feature_stock, true, R.string.intro_orders_s1),
            Step(R.drawable.ic_truck, true, R.string.intro_orders_s2),
            Step(R.drawable.ic_tile_bills, false, R.string.intro_orders_s3))),
        EXPIRY("expiry", R.string.coach_title_stock, R.string.intro_expiry_body, R.color.tile_stock_bg, R.drawable.ic_tile_expiring, listOf(
            Step(R.drawable.ic_tile_bills, false, R.string.intro_expiry_s1),
            Step(R.drawable.ic_tile_expiring, false, R.string.intro_expiry_s2),
            Step(R.drawable.ic_undo, true, R.string.intro_expiry_s3))),
        // Payment Radar (2 Nov 2026): shown the first time a customer's page has a reading to show.
        RADAR("radar", R.string.radar_title, R.string.intro_radar_body, R.color.tile_followup_bg, R.drawable.ic_tile_followup, listOf(
            Step(R.drawable.ic_tile_khata, false, R.string.intro_radar_s1),
            Step(R.drawable.ic_calendar, true, R.string.intro_radar_s2),
            Step(R.drawable.ic_menu_lock, true, R.string.intro_radar_s3)));
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seen(context: Context, topic: Topic): Boolean = prefs(context).getBoolean("seen_" + topic.key, false)

    private fun markSeen(context: Context, topic: Topic) {
        prefs(context).edit().putBoolean("seen_" + topic.key, true).apply()
    }

    /** Every sheet counted as seen: for tests whose pictures must show the screen, not the sheet. */
    fun markAllSeen(context: Context) {
        val e = prefs(context).edit()
        Topic.values().forEach { e.putBoolean("seen_" + it.key, true) }
        e.commit()
    }

    /** One sheet as never seen (the screen-proof test shows one). */
    fun forget(context: Context, topic: Topic) {
        prefs(context).edit().remove("seen_" + topic.key).commit()
    }

    /**
     * The sheet the first time [topic]'s screen opens on this phone, once the screen is drawn;
     * then [then] (also at once when it was seen before). [then] does not run when the sheet only
     * closed for a rotation - the rebuilt screen asks again.
     */
    fun showOnce(activity: Activity, topic: Topic, then: (() -> Unit)? = null) {
        if (seen(activity, topic)) { then?.invoke(); return }
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (seen(activity, topic)) { then?.invoke(); return@post }
            show(activity, topic, then)
        }
    }

    /** The sheet itself, seen before or not (Help → Feature guides). */
    fun show(activity: Activity, topic: Topic, then: (() -> Unit)? = null): BottomSheetDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.sheet_feature_intro, null)
        val row = view.findViewById<LinearLayout>(R.id.introSteps)
        val tileTint = ColorStateList.valueOf(ContextCompat.getColor(activity, topic.tile))
        val glyphTint = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.brand_green_text))
        topic.steps.forEachIndexed { i, step ->
            val cell = inflater.inflate(R.layout.item_intro_step, row, false)
            cell.findViewById<View>(R.id.introStepTile).backgroundTintList = tileTint
            cell.findViewById<ImageView>(R.id.ivIntroStep).apply {
                setImageResource(step.icon)
                imageTintList = if (step.tint) glyphTint else null
            }
            cell.findViewById<TextView>(R.id.tvIntroNumber).text = (i + 1).toString()
            cell.findViewById<TextView>(R.id.tvIntroStep).setText(step.words)
            row.addView(cell)
        }
        view.findViewById<TextView>(R.id.tvIntroTitle).setText(topic.title)
        view.findViewById<TextView>(R.id.tvIntroBody).setText(topic.body)

        val sheet = BottomSheetDialog(activity)
        view.findViewById<MaterialButton>(R.id.btnIntroDone).setOnClickListener { sheet.dismiss() }
        sheet.setOnDismissListener {
            // Closed for a rotation: not seen yet, and the rebuilt screen shows it again.
            if (activity.isChangingConfigurations) return@setOnDismissListener
            markSeen(activity, topic)
            if (!activity.isFinishing && !activity.isDestroyed) then?.invoke()
        }
        (activity as? ComponentActivity)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (sheet.isShowing) sheet.dismiss()
            }
        })

        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
        return sheet
    }

    /** Help → Feature guides: every sheet, to see again. */
    fun pick(activity: Activity) {
        val topics = Topic.values().toList()
        ChoiceSheet.show(
            activity,
            title = activity.getString(R.string.help_feature_guides),
            options = topics.map {
                ChoiceSheet.Option(
                    title = activity.getString(it.title),
                    icon = it.listIcon,
                    iconTint = if (it.listIcon.isTwoTone()) 0 else R.color.brand_green_text,
                    tile = it.tile
                )
            }
        ) { i -> show(activity, topics[i]) }
    }

    /** The Home tiles' two-tone drawings carry their own colours (lesson 56). */
    private fun Int.isTwoTone(): Boolean = this in TWO_TONE

    private val TWO_TONE = setOf(
        R.drawable.ic_tile_invoice, R.drawable.ic_tile_cashbook, R.drawable.ic_tile_cheques,
        R.drawable.ic_tile_plans, R.drawable.ic_tile_followup, R.drawable.ic_tile_backup,
        R.drawable.ic_tile_expiring
    )
}
