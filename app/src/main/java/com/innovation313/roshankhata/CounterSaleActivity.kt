package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.string
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.Counter
import com.innovation313.roshankhata.data.CounterDraft
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.SaleReceiptText
import com.innovation313.roshankhata.screens.CounterSaleScreen
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.screens.problemWord
import com.innovation313.roshankhata.words.ProvideWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The counter on the phone (POS Phase 1, 21 Oct 2026): the shared
 * [CounterSaleScreen] over the phone's database, with the phone's own two
 * things — the camera as the barcode scanner (Google's code scanner, every
 * format, so a pack's EAN rings its product up) and the receipt as text to
 * share on WhatsApp. Ringing up is [Counter.post], the one transaction.
 */
class CounterSaleActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }
    private val draft = CounterDraft()
    private var products by mutableStateOf<List<Product>>(emptyList())
    private var parties by mutableStateOf<List<PartyWithBalance>>(emptyList())
    private var onHand by mutableStateOf<Map<Long, Double?>>(emptyMap())
    private var heldCount by mutableStateOf(0)
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter_sale)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            // Recent tickets: a return against one, or its void (POS Phase 3).
            menu.add(R.string.pos_recent_sales).setOnMenuItemClickListener { showRecent(); true }
            // The counter's own settings (schema 36): prices with tax. The owner's to change.
            menu.add(R.string.counter_settings).setOnMenuItemClickListener {
                com.innovation313.roshankhata.ui.AccessUi.allowed(this@CounterSaleActivity, dao, com.innovation313.roshankhata.data.Access.Permission.SETTINGS,
                    getString(R.string.counter_settings)) { counterSettings() }
                true
            }
            // Who is at the counter (POS Phase 5).
            menu.add(R.string.access_who).setOnMenuItemClickListener {
                com.innovation313.roshankhata.ui.AccessUi.signIn(this@CounterSaleActivity, dao) { message = com.innovation313.roshankhata.data.Access.userName ?: getString(R.string.access_owner) }
                true
            }
        }

        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val viewer = KhataDatabase.get(this).viewerCopy
        findViewById<ComposeView>(R.id.counterCompose).setContent {
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    CounterSaleScreen(
                        draft = draft, products = products, parties = parties, onHand = onHand,
                        heldCount = heldCount, message = message,
                        onScan = { scan() },
                        onHold = { hold() },
                        onShowHeld = { showHeld() },
                        onComplete = { if (viewer) toast(R.string.viewer_on_note) else complete() }
                    )
                }
            }
        }
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    dao.productsOnce(), dao.partiesWithBalanceOnce(),
                    runCatching { Counter.onHand(dao) }.getOrDefault(emptyMap()),
                    dao.heldSalesOnce().size,
                    com.innovation313.roshankhata.data.BookSettings.isOn(dao.settingValue(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX))
                )
            }
            products = loaded.products; parties = loaded.parties; onHand = loaded.onHand; heldCount = loaded.held
            draft.pricesIncludeTax = loaded.taxInside
        }
    }

    /** Prices with their tax inside or not (the book's setting, so the computer's counter follows it too). */
    private fun counterSettings() {
        lifecycleScope.launch {
            val on = withContext(Dispatchers.IO) {
                com.innovation313.roshankhata.data.BookSettings.isOn(dao.settingValue(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX))
            }
            val box = android.widget.CheckBox(this@CounterSaleActivity).apply { setText(R.string.counter_prices_include_tax); isChecked = on }
            val help = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.counter_prices_include_tax_help); textSize = 13f }
            val pad = (20 * resources.displayMetrics.density).toInt()
            val view = android.widget.LinearLayout(this@CounterSaleActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0); addView(box); addView(help)
            }
            MaterialAlertDialogBuilder(this@CounterSaleActivity)
                .setTitle(R.string.counter_settings)
                .setView(view)
                .setPositiveButton(R.string.save) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX, if (box.isChecked) "1" else null)
                        }
                        reload()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private class Loaded(val products: List<Product>, val parties: List<PartyWithBalance>, val onHand: Map<Long, Double?>, val held: Int, val taxInside: Boolean)

    /** The camera as a scanner: a pack's barcode rings its product up; an unknown code says so. */
    private fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue?.trim().orEmpty()
                if (code.isEmpty()) return@addOnSuccessListener
                val product = products.firstOrNull { it.barcode == code }
                if (product == null) {
                    message = getString(R.string.pos_barcode_unknown) + " (" + code + ")"
                } else {
                    val line = draft.add(product, onHand[product.id])
                    message = line.shortBy?.let { getString(R.string.pos_stock_low, com.innovation313.roshankhata.ui.Calc.trim(onHand[product.id] ?: 0.0)) }
                }
            }
            .addOnFailureListener { toast(R.string.qr_scan_failed) }
    }

    private fun complete(allowedBy: String? = null) {
        draft.problem?.let { message = getString(com.innovation313.roshankhata.data.WordIds.string(problemWord(it))); return }
        // A cashier's discount past the cap waits for a manager's PIN (POS Phase 5).
        val lineDiscounts = draft.lines.sumOf { it.discountValue }
        if (allowedBy == null && com.innovation313.roshankhata.data.Access.discountNeedsOverride(draft.totals.discount + lineDiscounts, draft.totals.subtotal)) {
            message = getString(R.string.access_discount_cap, com.innovation313.roshankhata.ui.Format.money(draft.totals.subtotal * com.innovation313.roshankhata.data.Access.CASHIER_DISCOUNT_CAP))
            com.innovation313.roshankhata.ui.AccessUi.allowed(this, dao, com.innovation313.roshankhata.data.Access.Permission.DISCOUNT_ANY, getString(R.string.pos_discount)) { by -> complete(by) }
            return
        }
        // Past the customer's credit limit (POS Phase 9): a manager's PIN.
        val party = parties.firstOrNull { it.id == draft.partyId }
        val over = draft.creditOver(party?.balance, party?.creditLimit)
        if (over != null && allowedBy == null && !com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.CREDIT_OVER)) {
            val what = getString(R.string.pos_credit_limit, com.innovation313.roshankhata.ui.Format.money(over.first), com.innovation313.roshankhata.ui.Format.money(over.second))
            message = what
            com.innovation313.roshankhata.ui.AccessUi.allowed(this, dao, com.innovation313.roshankhata.data.Access.Permission.CREDIT_OVER, what) { by -> complete(by) }
            return
        }
        if (over != null && allowedBy == null) message = getString(R.string.pos_credit_limit, com.innovation313.roshankhata.ui.Format.money(over.first), com.innovation313.roshankhata.ui.Format.money(over.second))
        lifecycleScope.launch {
            if (allowedBy != null) withContext(Dispatchers.IO) { dao.log(com.innovation313.roshankhata.data.AuditAction.DISCOUNT_OVERRIDE, allowedBy, detail = com.innovation313.roshankhata.ui.Format.money(draft.totals.discount + lineDiscounts)) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val id = Counter.post(dao, draft)
                    val sale = dao.saleWithLines(id)!!
                    sale to sale.sale.partyId?.let { pid -> dao.partiesWithBalanceOnce().firstOrNull { it.id == pid } }
                }
            }
            result.onSuccess { (sale, party) ->
                val words = AndroidWords(this@CounterSaleActivity)
                val shop = com.innovation313.roshankhata.data.BusinessProfile.businessName(this@CounterSaleActivity) ?: getString(R.string.app_name)
                val text = SaleReceiptText.build(words, shop, sale, party?.balance)
                draft.clear()
                message = null
                reload()
                MaterialAlertDialogBuilder(this@CounterSaleActivity)
                    .setTitle(getString(R.string.pos_posted, sale.sale.saleNumber))
                    .setMessage(text)
                    .setPositiveButton(R.string.share) { _, _ ->
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.pos_receipt)))
                    }
                    // The paired Bluetooth receipt printer (POS Phase 8), the ticket as a picture the roll's width.
                    .setNeutralButton(R.string.print_receipt) { _, _ -> com.innovation313.roshankhata.ui.TextReceipt.print(this@CounterSaleActivity, lifecycleScope, text) }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }.onFailure { e ->
                message = if (e is Counter.ExpiredStock) getString(R.string.pos_expired_stock, e.item, com.innovation313.roshankhata.ui.Calc.trim(e.fresh))
                    else e.message ?: getString(R.string.backup_failed)
            }
        }
    }

    private fun hold() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { Counter.hold(dao, draft) } }
            draft.clear()
            reload()
        }
    }

    private fun showHeld() {
        lifecycleScope.launch {
            val held = withContext(Dispatchers.IO) { dao.heldSalesOnce() }
            if (held.isEmpty()) return@launch
            val labels = held.map { (it.customerName ?: getString(R.string.pos_walk_in)) + " · " + com.innovation313.roshankhata.ui.Format.money(it.grandTotal) }.toTypedArray()
            MaterialAlertDialogBuilder(this@CounterSaleActivity)
                .setTitle(com.innovation313.roshankhata.data.Digits.string(resources, R.string.pos_held_tickets, held.size))
                .setItems(labels) { _, i ->
                    lifecycleScope.launch {
                        val full = withContext(Dispatchers.IO) { dao.saleWithLines(held[i].id) } ?: return@launch
                        draft.load(full, onHandOf = { onHand[it] }, productOf = { id -> products.firstOrNull { it.id == id } })
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** The last twenty tickets; one tapped offers a return (goods back) or a void. */
    private fun showRecent() {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val rows = withContext(Dispatchers.IO) { dao.salesBetween(now - 60L * 24 * 60 * 60 * 1000, now + 60_000).take(20) }
            if (rows.isEmpty()) { toast(R.string.pos_lines_empty); return@launch }
            val labels = rows.map { s ->
                s.saleNumber + " · " + (s.customerName ?: getString(R.string.pos_walk_in)) + " · " + com.innovation313.roshankhata.ui.Format.money(s.grandTotal) +
                    (if (s.status == com.innovation313.roshankhata.data.SaleStatus.VOID) " · " + getString(R.string.pos_status_void) else "")
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@CounterSaleActivity)
                .setTitle(R.string.pos_recent_sales)
                .setItems(labels) { _, i ->
                    val s = rows[i]
                    if (s.status != com.innovation313.roshankhata.data.SaleStatus.DONE) return@setItems
                    MaterialAlertDialogBuilder(this@CounterSaleActivity)
                        .setTitle(s.saleNumber)
                        .setItems(arrayOf(getString(R.string.pos_return), getString(R.string.pos_void))) { _, which ->
                            if (which == 0) com.innovation313.roshankhata.ui.AccessUi.allowed(this@CounterSaleActivity, dao, com.innovation313.roshankhata.data.Access.Permission.RETURN, getString(R.string.pos_return) + " " + s.saleNumber) { startReturn(s.id) }
                            else com.innovation313.roshankhata.ui.AccessUi.allowed(this@CounterSaleActivity, dao, com.innovation313.roshankhata.data.Access.Permission.VOID, getString(R.string.pos_void) + " " + s.saleNumber) { askVoid(s.id, s.saleNumber) }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun askVoid(saleId: Long, number: String) {
        val input = android.widget.EditText(this).apply { hint = getString(R.string.pos_void_reason) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pos_void)
            .setMessage(getString(R.string.pos_void_confirm, number))
            .setView(input)
            .setPositiveButton(R.string.pos_void) { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) { toast(R.string.pos_err_reason); return@setPositiveButton }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.voidSale(saleId, reason) } }
                    message = getString(R.string.pos_voided) + " " + number
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Goods back against a ticket: the shared ReturnForm in a dialog, posted in one transaction. */
    private fun startReturn(saleId: Long) {
        lifecycleScope.launch {
            val full = withContext(Dispatchers.IO) { dao.saleWithLines(saleId) } ?: return@launch
            val back = withContext(Dispatchers.IO) { dao.returnedOfSale(saleId) }.associate { it.saleItemId to it.qty }
            val draft = com.innovation313.roshankhata.screens.ReturnDraft(full, back)
            val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val words = AndroidWords(this@CounterSaleActivity)
            var problem by mutableStateOf<String?>(null)
            val view = ComposeView(this@CounterSaleActivity)
            val dialog = MaterialAlertDialogBuilder(this@CounterSaleActivity).setTitle(R.string.pos_return_title).setView(view).create()
            view.setContent {
                RoshanTheme(dark = dark) {
                    ProvideWords(words) {
                        com.innovation313.roshankhata.screens.ReturnForm(draft, onCancel = { dialog.dismiss() }, message = problem, onPost = {
                            lifecycleScope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { dao.postReturn(draft.toReturn(), draft.toItems()) } }
                                r.onSuccess { id ->
                                    val number = withContext(Dispatchers.IO) { dao.getReturn(id)?.returnNumber }.orEmpty()
                                    message = getString(R.string.pos_return_posted, number)
                                    dialog.dismiss(); reload()
                                }.onFailure { problem = it.message }
                            }
                        })
                    }
                }
            }
            dialog.show()
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
