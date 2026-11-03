package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * A counter sale is written (3 Nov 2026, the owner's chosen mockup "Counter — sale saved"): the sheet rises,
 * the gold-ringed tick pops in and draws itself, the sale's number comes up under it, the receipt rises into
 * place, and the change to hand back arrives last on its own gold chip — the one figure the cashier must act
 * on, no longer a few words at the end of a dialog's title. Print and Share keep the sheet open (one may want
 * both); Close ends it. The whole arrival is over in about half a second; with animations off it is all there.
 */
object SaleSavedSheet {

    fun show(
        activity: Activity,
        title: String,
        change: Double,
        receipt: String,
        onPrint: () -> Unit,
        onShare: () -> Unit
    ): BottomSheetDialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_sale_saved, null)
        val tick = view.findViewById<View>(R.id.saleTick)
        val titleView = view.findViewById<TextView>(R.id.tvSaleTitle).apply { text = title }
        val receiptView = view.findViewById<TextView>(R.id.tvSaleReceipt).apply { text = receipt }
        val chip = view.findViewById<View>(R.id.saleChange)
        val giveBack = change > 0.005
        if (giveBack) {
            chip.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvSaleChange).text = Format.ltr(Format.money(change))
        }

        val sheet = BottomSheetDialog(activity)
        view.findViewById<MaterialButton>(R.id.btnSalePrint).setOnClickListener { onPrint() }
        view.findViewById<MaterialButton>(R.id.btnSaleShare).setOnClickListener { onShare() }
        view.findViewById<MaterialButton>(R.id.btnSaleClose).setOnClickListener { sheet.dismiss() }
        // Closed with the screen, and let go of once closed: a counter shows one per sale all day.
        DialogLife.bind(activity, sheet)

        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()

        // The arrival, in the mockup's order: tick, number, receipt, then the change.
        MotionKit.popIn(tick, delay = 60, duration = 240)
        MotionKit.drawCheck(view.findViewById(R.id.ivSaleTick), R.drawable.avd_save_check, R.drawable.ic_check_plain, R.color.white)
        MotionKit.riseIn(titleView, delay = 100, duration = 180)
        MotionKit.riseIn(receiptView, delay = 160, duration = 200)
        if (giveBack) MotionKit.popIn(chip, delay = 300, duration = 220, from = 0.96f)
        activity.findViewById<View>(android.R.id.content)?.let { Motion.confirm(it) }
        return sheet
    }
}
