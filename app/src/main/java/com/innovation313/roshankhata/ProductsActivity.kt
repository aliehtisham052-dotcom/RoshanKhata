package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.string
import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.data.quantity

import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.Digits

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Stock
import com.innovation313.roshankhata.ui.ProductAdapter
import com.innovation313.roshankhata.ui.ProductDetailsDialog
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
class ProductsActivity : BaseActivity() {

    private lateinit var adapter: ProductAdapter
    private lateinit var tvEmpty: View
    private lateinit var tvEmptyText: TextView

    /** Every product with its stock, before the search box narrows it. */
    private var allStock: List<Stock.ProductStock> = emptyList()
    private lateinit var btnLink: MaterialButton

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Export: the system's save sheet names the file, the list is written to it. */
    private val saveCsv = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri: android.net.Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = com.innovation313.roshankhata.data.ProductCsv.text(dao.productsOnce(),
                        com.innovation313.roshankhata.data.ProductCsv.headers(com.innovation313.roshankhata.data.AndroidWords(this@ProductsActivity)))
                    contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            Toast.makeText(this@ProductsActivity, if (ok) R.string.products_export else R.string.products_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Import: the rows are read and counted, the owner confirms, then one transaction writes them. */
    private val pickCsv = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val plan = withContext(Dispatchers.IO) {
                runCatching {
                    val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    val rows = com.innovation313.roshankhata.data.ProductCsv.parse(text)
                    if (rows.isEmpty()) null else com.innovation313.roshankhata.data.ProductCsv.plan(rows, dao.productsOnce())
                }.getOrNull()
            }
            if (plan == null) { Toast.makeText(this@ProductsActivity, R.string.products_import_none, Toast.LENGTH_LONG).show(); return@launch }
            val lines = com.innovation313.roshankhata.data.Digits.string(resources, R.string.products_import_plan, plan.toAdd.size, plan.toUpdate.size) +
                (if (plan.barcodeClashes > 0) "\n\n" + com.innovation313.roshankhata.data.Digits.string(resources, R.string.products_import_clash, plan.barcodeClashes) else "")
            MaterialAlertDialogBuilder(this@ProductsActivity)
                .setTitle(R.string.products_import)
                .setMessage(lines)
                .setPositiveButton(R.string.save) { _, _ ->
                    lifecycleScope.launch {
                        val (added, updated) = withContext(Dispatchers.IO) { dao.importProducts(plan) }
                        Toast.makeText(this@ProductsActivity, com.innovation313.roshankhata.data.Digits.string(resources, R.string.products_imported, added, updated), Toast.LENGTH_LONG).show()
                        refresh()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)
        ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.emptyProducts)
        tvEmptyText = findViewById(R.id.tvNoProducts)
        findViewById<android.widget.EditText>(R.id.etProductSearch).addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = show()
            })
        btnLink = findViewById(R.id.btnLinkGoods)

        adapter = ProductAdapter { stock -> openCustomers(stock) }
        val rv: RecyclerView = findViewById(R.id.rvProducts)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnLink.setOnClickListener { confirmLink() }

        // The stock register an inspection asks for. It opens from here, on the
        // screen whose figures it prints, rather than from a settings list
        // where it would be looked for only after the inspector had left.
        findViewById<View>(R.id.btnInspectorReport).setOnClickListener {
            startActivity(Intent(this, InspectorReportActivity::class.java))
        }

        // The other half of what an inspection asks for: what came in and went
        // out over a period, not just what is on the shelf today.
        findViewById<View>(R.id.btnRegisterReport).setOnClickListener {
            startActivity(Intent(this, RegisterReportActivity::class.java))
        }
        // The shop's selling prices as pictures for WhatsApp. Read-only, so it
        // opens on the helper's phone as well; every trade has prices to send.
        findViewById<View>(R.id.btnRateList).setOnClickListener {
            startActivity(Intent(this, RateListActivity::class.java))
        }
        // The list as a spreadsheet and back (POS Phase 2).
        findViewById<View>(R.id.btnProductsMore).setOnClickListener { v ->
            android.widget.PopupMenu(this, v).apply {
                menu.add(0, 1, 0, R.string.products_export)
                menu.add(0, 2, 1, R.string.products_import)
                menu.add(0, 3, 2, R.string.stock_count_title)
                menu.add(0, 4, 3, R.string.stock_valuation)
                menu.add(0, 5, 4, R.string.stock_adjust)
                menu.add(0, 6, 5, R.string.po_title)
                setOnMenuItemClickListener { item ->
                    val ok = when (item.itemId) {
                        1 -> com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.EXPORT)
                        3, 5 -> com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.STOCK_ADJUST)
                        4, 6 -> true
                        else -> com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT)
                    }
                    if (!ok) { Toast.makeText(this@ProductsActivity, R.string.access_denied, Toast.LENGTH_LONG).show(); return@setOnMenuItemClickListener true }
                    when (item.itemId) {
                        1 -> saveCsv.launch("RoshanKhata_Products_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + ".csv")
                        2 -> pickCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/octet-stream", "*/*"))
                        3 -> countStock()
                        4 -> showValuation()
                        5 -> adjustStock()
                        6 -> startActivity(Intent(this@ProductsActivity, OrdersActivity::class.java))
                    }
                    true
                }
            }.show()
        }
        // An inspector visits agri and medical shops; the others keep the list only (4 Oct).
        if (!com.innovation313.roshankhata.data.TradeFeatures.inspectorReports(this)) {
            findViewById<View>(R.id.btnInspectorReport).visibility = View.GONE
            findViewById<View>(R.id.btnRegisterReport).visibility = View.GONE
        }

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

    /** A stock count (POS Phase 6): the shared form in a dialog; only the differences are written. */
    private fun countStock() {
        lifecycleScope.launch {
            val draft = com.innovation313.roshankhata.screens.CountDraft(withContext(Dispatchers.IO) { Stock.of(dao) })
            val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val words = com.innovation313.roshankhata.data.AndroidWords(this@ProductsActivity)
            val view = androidx.compose.ui.platform.ComposeView(this@ProductsActivity)
            val dialog = MaterialAlertDialogBuilder(this@ProductsActivity).setTitle(R.string.stock_count_title).setView(view).create()
            view.setContent {
                com.innovation313.roshankhata.screens.RoshanTheme(dark = dark) {
                    com.innovation313.roshankhata.words.ProvideWords(words) {
                        com.innovation313.roshankhata.screens.StockCountForm(draft, onCancel = { dialog.dismiss() }, onSave = {
                            lifecycleScope.launch {
                                val n = withContext(Dispatchers.IO) { runCatching { dao.recordCount(draft.counts(), draft.note.trim().ifBlank { null }) }.getOrDefault(0) }
                                Toast.makeText(this@ProductsActivity, com.innovation313.roshankhata.data.Digits.string(resources, R.string.stock_count_saved, n), Toast.LENGTH_LONG).show()
                                dialog.dismiss(); refresh()
                            }
                        })
                    }
                }
            }
            dialog.show()
        }
    }

    /** One correction to the shelf (POS Phase 6): the product, the signed quantity, the reason, a note. */
    private fun adjustStock() {
        lifecycleScope.launch {
            val products = com.innovation313.roshankhata.data.NameOrder.sortedBy(withContext(Dispatchers.IO) { dao.productsOnce() }) { it.name }
            if (products.isEmpty()) { Toast.makeText(this@ProductsActivity, R.string.no_products, Toast.LENGTH_LONG).show(); return@launch }
            MaterialAlertDialogBuilder(this@ProductsActivity)
                .setTitle(R.string.stock_adjust)
                .setItems(products.map { it.name }.toTypedArray()) { _, i ->
                    val p = products[i]
                    val qty = android.widget.EditText(this@ProductsActivity).apply { hint = getString(R.string.adjust_qty_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
                    val reasons = listOf(com.innovation313.roshankhata.data.AdjustReason.DAMAGE, com.innovation313.roshankhata.data.AdjustReason.EXPIRED, com.innovation313.roshankhata.data.AdjustReason.THEFT, com.innovation313.roshankhata.data.AdjustReason.OTHER)
                    val labels = listOf(R.string.adjust_reason_damage, R.string.adjust_reason_expired, R.string.adjust_reason_theft, R.string.adjust_reason_other).map { getString(it) }
                    val reason = android.widget.Spinner(this@ProductsActivity).apply { adapter = android.widget.ArrayAdapter(this@ProductsActivity, android.R.layout.simple_spinner_dropdown_item, labels) }
                    val note = android.widget.EditText(this@ProductsActivity).apply { hint = getString(R.string.note_optional) }
                    val box = android.widget.LinearLayout(this@ProductsActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad / 2, pad, 0)
                        addView(qty); addView(reason); addView(note)
                    }
                    MaterialAlertDialogBuilder(this@ProductsActivity)
                        .setTitle(p.name)
                        .setView(box)
                        .setPositiveButton(R.string.save) { _, _ ->
                            // Signed (21 Oct 2026): "+ in, − out", as the box says. Read as an amount, "−5"
                            // for damaged goods came out as +5 and the shelf went UP.
                            val q = com.innovation313.roshankhata.ui.Calc.evalPad(qty.text.toString()) ?: 0.0
                            if (q == 0.0) { Toast.makeText(this@ProductsActivity, R.string.pos_err_quantity, Toast.LENGTH_LONG).show(); return@setPositiveButton }
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { runCatching { dao.adjustStock(p.id, q, reasons[reason.selectedItemPosition], note.text.toString().trim().ifBlank { null }) } }
                                refresh()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** What the shelf is worth: cost price, else the last purchase rate; nothing guessed. */
    private fun showValuation() {
        lifecycleScope.launch {
            val v = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.Valuation.compute(dao) }
            val lines = v.rows.filter { it.value != null }.sortedByDescending { it.value }.take(40).joinToString("\n") {
                it.name + ": " + com.innovation313.roshankhata.ui.Calc.trim(it.onHand ?: 0.0) + (it.unit?.let { u -> " $u" } ?: "") + " × " + com.innovation313.roshankhata.ui.Format.money(it.cost ?: 0.0) + " = " + com.innovation313.roshankhata.ui.Format.money(it.value ?: 0.0)
            }
            MaterialAlertDialogBuilder(this@ProductsActivity)
                .setTitle(getString(R.string.stock_valuation) + " · " + com.innovation313.roshankhata.ui.Format.money(v.total))
                .setMessage(com.innovation313.roshankhata.data.Digits.string(resources, R.string.stock_valuation_known, v.known, v.rows.size) + "\n\n" + lines)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val stock = com.innovation313.roshankhata.data.NameOrder.sortedBy(Stock.of(dao)) { it.name }

            allStock = stock
            show()
        }
    }

    /**
     * The list as the search box narrows it, and the figures above it. The
     * figures always count the whole shop, not the search (13 Oct). "Out of
     * stock" counts only products whose bought and sold sides count in the
     * same unit: one that cannot be subtracted is not called empty.
     */
    private fun show() {
        val q = findViewById<android.widget.EditText>(R.id.etProductSearch).text.toString().trim()
        val shown = if (q.isEmpty()) allStock
        else allStock.filter { com.innovation313.roshankhata.ui.NameSearch.matches(it.name, null, q.lowercase()) }
        adapter.submitList(shown)
        tvEmptyText.setText(if (allStock.isEmpty()) R.string.no_products else R.string.products_no_match)
        tvEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        val figures = com.innovation313.roshankhata.data.Digits.FIGURES
        findViewById<TextView>(R.id.tvStatProducts).text = "%,d".format(figures, allStock.size)
        findViewById<TextView>(R.id.tvStatOut).text =
            "%,d".format(figures, allStock.count { (it.onHand ?: 1.0) <= 0.0 })
    }

    private fun openCustomers(stock: Stock.ProductStock) {
        // Two things an owner wants from a product, so ask which rather than
        // guessing: who bought it, or what it actually is. Same shape as the
        // invoice list's own tap.
        ChoiceSheet.show(
            this, stock.name,
            listOf(
                ChoiceSheet.buyers(getString(R.string.product_who_bought)),
                ChoiceSheet.edit(getString(R.string.product_edit_details))
            )
        ) { which ->
            when (which) {
                0 -> openProductCustomers(stock)
                1 -> editProduct(stock)
            }
        }
    }

    private fun openProductCustomers(stock: Stock.ProductStock) {
        startActivity(
            Intent(this, ProductCustomersActivity::class.java)
                .putExtra(ProductCustomersActivity.EXTRA_PRODUCT_ID, stock.productId)
                .putExtra(ProductCustomersActivity.EXTRA_PRODUCT_NAME, stock.name)
        )
    }

    /**
     * The first way to correct a product since the app shipped.
     *
     * The form itself lives in [ProductDetailsDialog] because the bill screen
     * opens the same one — an owner holding the label while writing a bill is
     * the best moment to answer these questions, and two forms asking them
     * would eventually ask them differently.
     */
    private fun editProduct(stock: Stock.ProductStock) {
        lifecycleScope.launch {
            val product = dao.productById(stock.productId) ?: return@launch
            ProductDetailsDialog.show(
                activity = this@ProductsActivity,
                product = product,
                dao = dao,
                // The row shows the name and the stock figure, both of which a
                // save can change.
                onSaved = { refresh() }
            )
        }
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

    /**
     * Runs on [AppScope] — see AppScope's own comment.
     *
     * This one is a sweep across the entire book rather than a single row,
     * and the owner is told a count when it finishes. Tied to this screen it
     * could be cut off halfway by leaving, and because it is one @Transaction
     * the result would be that NOTHING was tied while the owner had every
     * reason to believe it had run — the worst of both, since they would not
     * think to try again.
     *
     * All three UI outcomes hop back to the main thread and check the screen
     * is still there. The tying happens regardless; only the telling depends
     * on someone being present to be told.
     */
    private fun runLink() {
        btnLink.isEnabled = false
        AppScope.launch {
            val linked = try {
                dao.linkGoodsToProducts()
            } catch (e: Exception) {
                // Nothing partial can have happened — the whole thing is one
                // transaction. Say it plainly rather than leaving the owner
                // wondering what state their book is in.
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        btnLink.isEnabled = true
                        Toast.makeText(
                            this@ProductsActivity,
                            R.string.link_goods_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    btnLink.isEnabled = true
                    Toast.makeText(
                        this@ProductsActivity,
                        if (linked == 0) getString(R.string.link_goods_none)
                        else Digits.quantity(resources, R.plurals.link_goods_done, linked, linked),
                        Toast.LENGTH_LONG
                    ).show()
                    refresh()
                }
            }
        }
    }
}
