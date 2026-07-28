package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Stock
import com.innovation313.roshankhata.ui.ProductAdapter
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the shop deals in, and what is left of it.
 *
 * The figures here are only as good as the links behind them. Every entry
 * written before products existed carries a name and no product, and until
 * those are tied nothing has moved as far as this screen can tell. So the
 * tying is offered on the screen whose numbers depend on it, rather than
 * buried in a settings list where it would be found months later.
 *
 * The screen states its own blind spot in the note at the top, the same way
 * the expiry screen does. A stock figure that silently omits half the shop is
 * worse than no figure, because it will be believed.
 */
class ProductsActivity : AppCompatActivity() {

    private lateinit var adapter: ProductAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var btnLink: MaterialButton

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)
        ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.tvNoProducts)
        btnLink = findViewById(R.id.btnLinkGoods)

        adapter = ProductAdapter { stock -> openCustomers(stock) }
        val rv: RecyclerView = findViewById(R.id.rvProducts)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnLink.setOnClickListener { confirmLink() }

        refresh()
    }

    /**
     * Recomputed on every return to this screen.
     *
     * A sale entered in the ledger changes what is on the shelf, and coming
     * back to a figure that has not noticed is how an owner stops trusting the
     * screen. Not a Flow: three separate sums married in Kotlin is a snapshot,
     * and the cost of taking it again is a few queries.
     */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val products = dao.observeProducts().first()
            val stock = Stock.combine(
                products = products,
                bought = dao.boughtPerProduct(),
                sold = dao.soldPerProduct(),
                returned = dao.returnedPerProduct()
            ).sortedBy { it.name.lowercase() }

            adapter.submitList(stock)
            tvEmpty.visibility = if (stock.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openCustomers(stock: Stock.ProductStock) {
        startActivity(
            Intent(this, ProductCustomersActivity::class.java)
                .putExtra(ProductCustomersActivity.EXTRA_PRODUCT_ID, stock.productId)
                .putExtra(ProductCustomersActivity.EXTRA_PRODUCT_NAME, stock.name)
        )
    }

    /**
     * The tying, and an honest account of it before it runs.
     *
     * The dialog says what will be changed and, more importantly, what will
     * not: the names the owner typed are read and never written. This is the
     * one action on this screen that touches existing rows, so it asks first
     * and describes itself in full rather than in a word.
     */
    private fun confirmLink() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.link_goods)
            .setMessage(R.string.link_goods_explain)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.link_goods_go) { _, _ -> runLink() }
            .show()
    }

    private fun runLink() {
        btnLink.isEnabled = false
        lifecycleScope.launch {
            val linked = try {
                dao.linkGoodsToProducts()
            } catch (e: Exception) {
                // Nothing partial can have happened — the whole thing is one
                // transaction. Say it plainly rather than leaving the owner
                // wondering what state their book is in.
                btnLink.isEnabled = true
                Toast.makeText(this@ProductsActivity, R.string.link_goods_failed, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            btnLink.isEnabled = true
            Toast.makeText(
                this@ProductsActivity,
                if (linked == 0) getString(R.string.link_goods_none)
                else getString(R.string.link_goods_done, linked),
                Toast.LENGTH_LONG
            ).show()
            refresh()
        }
    }
}
