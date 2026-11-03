package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.Stock
import com.innovation313.roshankhata.data.UnitWords

/**
 * The price check (3 Nov 2026, the competitor study): "how much is this?" answered from its barcode — the
 * cash and credit price, what is on the shelf and where it is kept — with nothing put on a ticket. The
 * Products screen opens it for a code scanned with the camera or typed into its search by a scanner.
 * Who bought it and its details open from here as from the product's row; a helper's read-only phone is
 * offered no details to change.
 */
object PriceCheckSheet {

    fun show(
        activity: Activity,
        product: Product,
        /** Its line in the stock figures, when the shop has moved it at all. */
        stock: Stock.ProductStock?,
        onBuyers: () -> Unit,
        /** Null on a phone that cannot change products. */
        onEdit: (() -> Unit)?
    ): BottomSheetDialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_price_check, null)
        view.findViewById<TextView>(R.id.tvPriceName).text = product.name
        view.findViewById<TextView>(R.id.tvPriceSub).apply {
            val sub = listOfNotNull(product.company, UnitWords.label(product.defaultUnit).ifEmpty { null }, product.barcode?.let { Format.ltr(it) })
                .filter { it.isNotBlank() }
            if (sub.isNotEmpty()) { text = sub.joinToString("  ·  "); visibility = View.VISIBLE }
        }
        // A price never set is a dash, never a zero: zero is a price (the product form's own rule).
        view.findViewById<TextView>(R.id.tvPriceCash).text = product.salePrice?.let { Format.money(it) } ?: "–"
        view.findViewById<TextView>(R.id.tvPriceCredit).text = product.creditPrice?.let { Format.money(it) } ?: "–"
        view.findViewById<TextView>(R.id.tvPriceStock).text = activity.getString(R.string.inventory_title) + ": " + when {
            stock == null || stock.isUntouched -> activity.getString(R.string.stock_no_movement)
            stock.onHand == null -> activity.getString(R.string.stock_units_differ)
            else -> Format.qty(stock.onHand!!, stock.unit)
        }
        view.findViewById<TextView>(R.id.tvPricePlace).apply {
            val place = product.note?.trim().orEmpty()
            if (place.isNotEmpty()) { text = place; visibility = View.VISIBLE }
        }

        val sheet = BottomSheetDialog(activity)
        view.findViewById<MaterialButton>(R.id.btnPriceBuyers).setOnClickListener { sheet.dismiss(); onBuyers() }
        view.findViewById<MaterialButton>(R.id.btnPriceEdit).apply {
            if (onEdit == null) visibility = View.GONE
            else setOnClickListener { sheet.dismiss(); onEdit() }
        }
        view.findViewById<MaterialButton>(R.id.btnPriceClose).setOnClickListener { sheet.dismiss() }
        DialogLife.bind(activity, sheet)
        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
        return sheet
    }
}
