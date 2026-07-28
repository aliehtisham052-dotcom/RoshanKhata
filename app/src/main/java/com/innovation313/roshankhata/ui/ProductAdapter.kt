package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Stock

/**
 * The product list, with what is left of each on the shelf.
 *
 * A ListAdapter rather than notifyDataSetChanged, for the same reason the
 * customer list is one: this grows with the shop, and a full rebind of every
 * row on every change is a stutter the owner feels while scrolling.
 *
 * The row has to show three different situations without letting any of them
 * be mistaken for another:
 *
 *  - a number, when it can be trusted
 *  - the two sides apart, when the units cannot be subtracted
 *  - nothing has moved, which is not the same as none left
 */
class ProductAdapter(
    private val onClick: (Stock.ProductStock) -> Unit
) : ListAdapter<Stock.ProductStock, ProductAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvProdName)
        val tvStock: TextView = view.findViewById(R.id.tvProdStock)
        val tvMoved: TextView = view.findViewById(R.id.tvProdMoved)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        val ctx = holder.itemView.context

        holder.tvName.text = s.name

        val onHand = s.onHand
        when {
            s.isUntouched -> {
                // Never bought, never sold. "0" would be a claim about stock;
                // this is a product nobody has traded yet.
                holder.tvStock.text = ctx.getString(R.string.stock_no_movement)
                holder.tvStock.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            }

            onHand == null -> {
                // The units do not agree, so there is no figure to give. Say so
                // plainly rather than showing a number that cannot be right.
                holder.tvStock.text = ctx.getString(R.string.stock_units_differ)
                holder.tvStock.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            }

            else -> {
                holder.tvStock.text = Format.qty(onHand, s.unit)
                // Below zero means stock left that never arrived — a bill never
                // entered, or a quantity typed wrong. Worth catching the eye.
                holder.tvStock.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (onHand < 0) R.color.red_gave else R.color.green_got
                    )
                )
            }
        }

        // Both sides always, whatever happened above — they are the evidence
        // for the figure, and the only thing on offer when there is no figure.
        holder.tvMoved.text = ctx.getString(
            R.string.stock_in_out,
            Format.qty(s.boughtQty + s.returnedQty, s.boughtUnit),
            Format.qty(s.soldQty, s.soldUnit)
        )

        holder.itemView.setOnClickListener { onClick(s) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Stock.ProductStock>() {
            override fun areItemsTheSame(a: Stock.ProductStock, b: Stock.ProductStock) =
                a.productId == b.productId

            override fun areContentsTheSame(a: Stock.ProductStock, b: Stock.ProductStock) =
                a == b
        }
    }
}
