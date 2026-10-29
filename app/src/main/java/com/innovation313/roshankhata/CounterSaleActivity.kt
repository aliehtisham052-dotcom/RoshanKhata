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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
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
    // The ticket outlives the screen being rebuilt (21 Oct 2026, the critical review: turning the phone
    // or switching dark mode emptied a half-rung ticket). Held by the screen's ViewModel.
    class TicketHolder : androidx.lifecycle.ViewModel() { val draft = CounterDraft() }
    private val draft by lazy { androidx.lifecycle.ViewModelProvider(this)[TicketHolder::class.java].draft }
    private var products by mutableStateOf<List<Product>>(emptyList())
    private var parties by mutableStateOf<List<PartyWithBalance>>(emptyList())
    private var onHand by mutableStateOf<Map<Long, Double?>>(emptyMap())
    private var heldCount by mutableStateOf(0)
    /** Raised once a ticket is written: the payment step closes then (23 Oct 2026, the review). */
    private var postedTick by mutableStateOf(0)
    private var message by mutableStateOf<String?>(null)
    private var scaleRule by mutableStateOf(com.innovation313.roshankhata.data.ScaleCode.Rule())
    /** The big-shop tools (BookSettings.counterAdvanced, 24 Oct 2026): shifts, who is at the counter, the tax box. */
    private var advanced by mutableStateOf(false)
    private var shiftItem: android.view.MenuItem? = null
    private var whoItem: android.view.MenuItem? = null

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
            // Shifts (21 Oct 2026): the float in, the cash counted out, per person. A big-shop tool (24 Oct 2026).
            shiftItem = menu.add(R.string.shift_title).setOnMenuItemClickListener { startActivity(Intent(this@CounterSaleActivity, ShiftActivity::class.java)); true }
            // Who is at the counter (POS Phase 5): with the big-shop tools, or wherever counter mode is on.
            whoItem = menu.add(R.string.access_who).setOnMenuItemClickListener {
                com.innovation313.roshankhata.ui.AccessUi.signIn(this@CounterSaleActivity, dao) { message = com.innovation313.roshankhata.data.Access.userName ?: getString(R.string.access_owner) }
                true
            }
        }
        showTools()

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
                        onComplete = { if (viewer) toast(R.string.viewer_on_note) else complete() },
                        postedTick = postedTick,
                        scaleRule = scaleRule,
                        loyaltyBalanceOf = { id -> withContext(Dispatchers.IO) { dao.loyaltyBalance(id) } },
                        advanced = advanced,
                        // The promised day for what goes to the account: the khata entry's own question (DuePicker).
                        onChooseDue = { draft.partyName?.let { name -> com.innovation313.roshankhata.ui.DuePicker.choose(this@CounterSaleActivity, com.innovation313.roshankhata.ui.Format.name(name), draft.due) { at -> draft.due = at } } },
                        onShowRecent = { showRecent() }
                    )
                }
            }
        }
        reload()
    }

    /**
     * After a ticket (23 Oct 2026, the review): the shelf, the held count and
     * the one customer's balance, read alone — not every customer's balance
     * again. Products and the book's settings do not change with a sale.
     */
    private fun refreshAfterSale(partyId: Long?, balance: Double?) {
        lifecycleScope.launch {
            val stock = withContext(Dispatchers.IO) { runCatching { Counter.onHand(dao) }.getOrDefault(emptyMap()) }
            val heldNow = withContext(Dispatchers.IO) { dao.heldSalesOnce().size }
            onHand = stock; heldCount = heldNow
            if (partyId != null && balance != null) parties = parties.map { if (it.id == partyId) it.copy(balance = balance) else it }
        }
    }

    private fun reload() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    dao.productsOnce(), dao.partiesWithBalanceOnce(),
                    runCatching { Counter.onHand(dao) }.getOrDefault(emptyMap()),
                    dao.heldSalesOnce().size,
                    com.innovation313.roshankhata.data.BookSettings.isOn(dao.settingValue(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX)),
                    com.innovation313.roshankhata.data.Loyalty.rule(dao),
                    com.innovation313.roshankhata.data.ScaleCode.rule(dao),
                    com.innovation313.roshankhata.data.BookSettings.counterAdvanced(dao)
                )
            }
            products = loaded.products; parties = loaded.parties; onHand = loaded.onHand; heldCount = loaded.held
            draft.pricesIncludeTax = loaded.taxInside
            draft.loyalty = loaded.loyalty
            scaleRule = loaded.scale
            advanced = loaded.advanced
            showTools()
        }
    }

    /** The top bar's big-shop items follow the switch; who is at the counter also shows wherever counter mode is on. */
    private fun showTools() {
        shiftItem?.isVisible = advanced
        whoItem?.isVisible = advanced || com.innovation313.roshankhata.data.Access.counterMode || com.innovation313.roshankhata.data.Access.current != null
    }

    /**
     * The counter's settings, two kinds said apart: the book's (prices with
     * their tax inside or not — the computer's counter follows it too) and
     * this phone's receipt printer (an 80 mm roll, the shop's logo at the top,
     * the cash drawer opened on a cash sale; TextReceipt reads them).
     */
    private fun counterSettings() {
        lifecycleScope.launch {
            val on = withContext(Dispatchers.IO) {
                com.innovation313.roshankhata.data.BookSettings.isOn(dao.settingValue(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX))
            }
            val book = withContext(Dispatchers.IO) {
                listOf(
                    com.innovation313.roshankhata.data.BookSettings.LOYALTY_POINTS_PER_100, com.innovation313.roshankhata.data.BookSettings.LOYALTY_POINT_VALUE,
                    com.innovation313.roshankhata.data.BookSettings.SCALE_PREFIX, com.innovation313.roshankhata.data.BookSettings.SCALE_PRICE,
                    com.innovation313.roshankhata.data.BookSettings.SCALE_PRICE_DECIMALS
                ).associateWith { dao.settingValue(it) }
            }
            val wasAdvanced = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.BookSettings.counterAdvanced(dao) }
            val wasBlockShort = withContext(Dispatchers.IO) {
                com.innovation313.roshankhata.data.BookSettings.isOn(dao.settingValue(com.innovation313.roshankhata.data.BookSettings.BLOCK_NEGATIVE_STOCK))
            }
            val printer = com.innovation313.roshankhata.ui.TextReceipt.settings(this@CounterSaleActivity)
            val hasLogo = com.innovation313.roshankhata.data.BusinessProfile.hasLogo(this@CounterSaleActivity)
            val box = android.widget.CheckBox(this@CounterSaleActivity).apply { setText(R.string.counter_prices_include_tax); isChecked = on }
            val help = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.counter_prices_include_tax_help); textSize = 13f }
            // The stock rule (29 Oct 2026): refuse a ticket that takes a product below zero, or only warn (the default).
            val blockShort = android.widget.CheckBox(this@CounterSaleActivity).apply { setText(R.string.pos_block_negative); isChecked = wasBlockShort }
            val blockShortHelp = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.pos_block_negative_help); textSize = 13f }
            val head = android.widget.TextView(this@CounterSaleActivity).apply {
                setText(R.string.printer_section); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            val wide = android.widget.CheckBox(this@CounterSaleActivity).apply { setText(R.string.printer_width_80); isChecked = printer.wide }
            val logo = android.widget.CheckBox(this@CounterSaleActivity).apply {
                setText(if (hasLogo) R.string.printer_logo else R.string.biz_card_logo_none); isChecked = printer.logo && hasLogo; isEnabled = hasLogo
            }
            val drawer = android.widget.CheckBox(this@CounterSaleActivity).apply { setText(R.string.printer_drawer); isChecked = printer.drawer }
            val drawerHelp = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.printer_drawer_help); textSize = 13f }
            // Tickets print on the printer chosen last without asking (23 Oct 2026, the review); it is changed here.
            val choosePrinter = com.google.android.material.button.MaterialButton(this@CounterSaleActivity).apply {
                setText(R.string.print_choose)
                setOnClickListener { com.innovation313.roshankhata.ui.TextReceipt.choosePrinter(this@CounterSaleActivity, lifecycleScope) }
            }
            fun heading(res: Int) = android.widget.TextView(this@CounterSaleActivity).apply {
                setText(res); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            fun number(hint: Int, value: String?) = android.widget.EditText(this@CounterSaleActivity).apply {
                setHint(hint); setText(value.orEmpty()); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                textDirection = android.view.View.TEXT_DIRECTION_LTR
            }
            // Loyalty (the book's): points per 100 spent and a point's worth; an empty box keeps it off.
            val perHundred = number(R.string.loyalty_per_100, book[com.innovation313.roshankhata.data.BookSettings.LOYALTY_POINTS_PER_100])
            val pointValue = number(R.string.loyalty_value, book[com.innovation313.roshankhata.data.BookSettings.LOYALTY_POINT_VALUE])
            val loyaltyHelp = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.loyalty_help); textSize = 13f }
            // The weighing scale's labels (the book's): their prefix, and whether they carry the price.
            val prefix = number(R.string.scale_prefix, book[com.innovation313.roshankhata.data.BookSettings.SCALE_PREFIX] ?: com.innovation313.roshankhata.data.ScaleCode.DEFAULT_PREFIX).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER; filters = arrayOf(android.text.InputFilter.LengthFilter(2))
            }
            val pricedLabel = android.widget.CheckBox(this@CounterSaleActivity).apply {
                setText(R.string.scale_price); isChecked = com.innovation313.roshankhata.data.BookSettings.isOn(book[com.innovation313.roshankhata.data.BookSettings.SCALE_PRICE])
            }
            // The label's price in the shop's own currency digits (21 Oct 2026): 0 to 3 after the point.
            var decimals = com.innovation313.roshankhata.data.ScaleCode.decimalsOf(book[com.innovation313.roshankhata.data.BookSettings.SCALE_PRICE_DECIMALS])
            val decimalsLabel = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.scale_price_decimals); textSize = 14f }
            val example = android.widget.TextView(this@CounterSaleActivity).apply { textSize = 13f }
            fun showExample() { example.text = getString(R.string.scale_price_example, com.innovation313.roshankhata.ui.Format.money(com.innovation313.roshankhata.data.ScaleCode.example(decimals))) }
            val decimalsGroup = android.widget.RadioGroup(this@CounterSaleActivity).apply {
                orientation = android.widget.RadioGroup.HORIZONTAL
                (0..3).forEach { d ->
                    addView(android.widget.RadioButton(this@CounterSaleActivity).apply {
                        id = android.view.View.generateViewId(); text = d.toString(); tag = d; isChecked = d == decimals
                    })
                }
                setOnCheckedChangeListener { g, checked -> decimals = g.findViewById<android.view.View>(checked)?.tag as? Int ?: decimals; showExample() }
            }
            showExample()
            val scaleHelp = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.scale_help); textSize = 13f }
            val pad = (20 * resources.displayMetrics.density).toInt()
            // The big-shop tools' switch first (24 Oct 2026); tax, loyalty and the scale are shown only while it is on.
            val tools = android.widget.CheckBox(this@CounterSaleActivity).apply { setText(R.string.counter_advanced); isChecked = wasAdvanced }
            val toolsHelp = android.widget.TextView(this@CounterSaleActivity).apply { setText(R.string.counter_advanced_help); textSize = 13f }
            val loyaltyHead = heading(R.string.loyalty_section)
            val scaleHead = heading(R.string.scale_section)
            val bigShop = listOf(box, help, loyaltyHead, perHundred, pointValue, loyaltyHelp, scaleHead, prefix, pricedLabel, scaleHelp)
            fun showBigShop() {
                bigShop.forEach { it.visibility = if (tools.isChecked) android.view.View.VISIBLE else android.view.View.GONE }
                listOf(decimalsLabel, decimalsGroup, example).forEach {
                    it.visibility = if (tools.isChecked && pricedLabel.isChecked) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
            val view = android.widget.LinearLayout(this@CounterSaleActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, pad / 2)
                listOf(tools, toolsHelp, box, help, blockShort, blockShortHelp, head, wide, logo, drawer, drawerHelp, choosePrinter,
                    loyaltyHead, perHundred, pointValue, loyaltyHelp,
                    scaleHead, prefix, pricedLabel, decimalsLabel, decimalsGroup, example, scaleHelp).forEach { addView(it) }
            }
            showBigShop()
            tools.setOnCheckedChangeListener { _, _ -> showBigShop() }
            pricedLabel.setOnCheckedChangeListener { _, _ -> showBigShop() }
            val scroll = android.widget.ScrollView(this@CounterSaleActivity).apply { addView(view) }
            MaterialAlertDialogBuilder(this@CounterSaleActivity)
                .setTitle(R.string.counter_settings)
                .setView(scroll)
                .setPositiveButton(R.string.save) { _, _ ->
                    com.innovation313.roshankhata.ui.TextReceipt.setSettings(this@CounterSaleActivity,
                        com.innovation313.roshankhata.ui.TextReceipt.Settings(wide.isChecked, logo.isChecked, drawer.isChecked))
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            // The owner's word on the big-shop tools, kept either way so the book's own guess no longer decides.
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.COUNTER_ADVANCED, if (tools.isChecked) "1" else "0")
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX, if (box.isChecked) "1" else null)
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.BLOCK_NEGATIVE_STOCK, if (blockShort.isChecked) "1" else null)
                            fun typed(e: android.widget.EditText) = e.text.toString().trim().takeIf { t -> (com.innovation313.roshankhata.data.Digits.parse(t) ?: 0.0) > 0.0 }
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.LOYALTY_POINTS_PER_100, typed(perHundred))
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.LOYALTY_POINT_VALUE, typed(pointValue))
                            val p = com.innovation313.roshankhata.data.Digits.toLatin(prefix.text.toString().trim())
                            // The default is kept as nothing (25 Oct 2026): a stored "21" read as a scale in use.
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.SCALE_PREFIX, p.takeIf { it.length == 2 && it.startsWith("2") && it.all(Char::isDigit) && it != com.innovation313.roshankhata.data.ScaleCode.DEFAULT_PREFIX })
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.SCALE_PRICE, if (pricedLabel.isChecked) "1" else null)
                            dao.setSetting(com.innovation313.roshankhata.data.BookSettings.SCALE_PRICE_DECIMALS,
                                decimals.takeIf { it != com.innovation313.roshankhata.data.ScaleCode.DEFAULT_DECIMALS }?.toString())
                        }
                        reload()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private class Loaded(
        val products: List<Product>, val parties: List<PartyWithBalance>, val onHand: Map<Long, Double?>, val held: Int, val taxInside: Boolean,
        val loyalty: com.innovation313.roshankhata.data.LoyaltyRule?, val scale: com.innovation313.roshankhata.data.ScaleCode.Rule,
        val advanced: Boolean
    )

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
                // A weighing scale's label (21 Oct 2026): the item and its weight in one code.
                val weighed = if (product == null) com.innovation313.roshankhata.data.ScaleCode.line(code, scaleRule, products) { draft.unitRate(it) } else null
                if (weighed != null) {
                    val line = draft.addWeighed(weighed, onHand[weighed.product.id])
                    message = line.shortBy?.let { getString(R.string.pos_stock_low, com.innovation313.roshankhata.ui.Calc.trim(onHand[weighed.product.id] ?: 0.0)) }
                } else if (product == null) {
                    message = getString(R.string.pos_barcode_unknown) + " (" + code + ")"
                } else {
                    val line = draft.add(product, onHand[product.id])
                    message = line.shortBy?.let { getString(R.string.pos_stock_low, com.innovation313.roshankhata.ui.Calc.trim(onHand[product.id] ?: 0.0)) }
                }
            }
            .addOnFailureListener { toast(R.string.qr_scan_failed) }
    }

    /**
     * Ring the ticket up. Each check a cashier may not pass alone waits for its
     * own manager's PIN (21 Oct 2026: one PIN given for a discount also let the
     * ticket past the credit limit): a price changed on the ticket or an item
     * typed in by hand, a discount past the cap (on the goods before any
     * discount), the credit limit. A second tap while the ticket is being
     * written does nothing.
     */
    private fun complete(priceBy: String? = null, discountBy: String? = null, creditBy: String? = null) {
        if (draft.posting) return
        draft.problem?.let { message = getString(com.innovation313.roshankhata.data.WordIds.string(problemWord(it))); return }
        val access = com.innovation313.roshankhata.data.Access
        val repriced = draft.repriced
        if (priceBy == null && repriced.isNotEmpty() && !access.can(com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT)) {
            message = getString(R.string.pos_price_needs_pin)
            com.innovation313.roshankhata.ui.AccessUi.allowed(this, dao, com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT, getString(R.string.pos_price_needs_pin)) { by -> complete(by ?: com.innovation313.roshankhata.data.Access.OWNER, discountBy, creditBy) }
            return
        }
        // A cashier's discount past the cap waits for a manager's PIN (POS Phase 5).
        val discounts = draft.allDiscounts
        if (discountBy == null && access.discountNeedsOverride(discounts, draft.grossTotal)) {
            message = getString(R.string.access_discount_cap, com.innovation313.roshankhata.ui.Format.money(draft.grossTotal * com.innovation313.roshankhata.data.Access.CASHIER_DISCOUNT_CAP))
            com.innovation313.roshankhata.ui.AccessUi.allowed(this, dao, com.innovation313.roshankhata.data.Access.Permission.DISCOUNT_ANY, getString(R.string.pos_discount)) { by -> complete(priceBy, by ?: com.innovation313.roshankhata.data.Access.OWNER, creditBy) }
            return
        }
        // Past the customer's credit limit (POS Phase 9): a manager's PIN.
        val party = parties.firstOrNull { it.id == draft.partyId }
        val over = draft.creditOver(party?.balance, party?.creditLimit)
        if (over != null && creditBy == null && !access.can(com.innovation313.roshankhata.data.Access.Permission.CREDIT_OVER)) {
            val what = getString(R.string.pos_credit_limit, com.innovation313.roshankhata.ui.Format.money(over.first), com.innovation313.roshankhata.ui.Format.money(over.second))
            message = what
            com.innovation313.roshankhata.ui.AccessUi.allowed(this, dao, com.innovation313.roshankhata.data.Access.Permission.CREDIT_OVER, what) { by -> complete(priceBy, discountBy, by ?: com.innovation313.roshankhata.data.Access.OWNER) }
            return
        }
        if (over != null && creditBy == null) message = getString(R.string.pos_credit_limit, com.innovation313.roshankhata.ui.Format.money(over.first), com.innovation313.roshankhata.ui.Format.money(over.second))
        val change = draft.change
        val priceDetail = if (repriced.isNotEmpty()) draft.repricedDetail() else null
        // A manager's discount and every price that is not the product's own are written down with the sale,
        // in its transaction (22 Oct 2026, the review: written before, they named no sale and stayed when the
        // ticket was then refused). The ticket is taken here, once: what is scanned meanwhile is the next one's.
        val notes = listOfNotNull(
            discountBy?.let { com.innovation313.roshankhata.data.AuditLog(user = it, action = com.innovation313.roshankhata.data.AuditAction.DISCOUNT_OVERRIDE, detail = com.innovation313.roshankhata.ui.Format.money(discounts)) },
            priceDetail?.let { com.innovation313.roshankhata.data.AuditLog(user = priceBy ?: access.userName, action = com.innovation313.roshankhata.data.AuditAction.PRICE_CHANGED, detail = it) }
        )
        val ticket = draft.snapshot(notes = notes)
        draft.posting = true
        // The post runs where a rebuilt screen cannot cancel it, and the ticket comes off the till the moment
        // the sale is written (23 Oct 2026, the review: turned, or a Bluetooth scanner connecting, between the
        // commit and the clearing, the screen came back with the same lines and Complete posted them again).
        // The draft is the screen's ViewModel's: the rebuilt screen sees it posting, then cleared.
        val held = draft
        val book = dao
        val app = applicationContext
        val work = com.innovation313.roshankhata.data.AppScope.async {
            try {
                val posted = runCatching { Counter.post(book, ticket) }
                posted.onSuccess {
                    withContext(Dispatchers.Main) { held.clearAfter(ticket) }
                    // Today's copy of the book follows the sales (a minute after the last one).
                    com.innovation313.roshankhata.data.Snapshots.afterSale(app)
                }
                // The receipt's figures: a sale posted whose receipt cannot be read is still posted.
                posted.map { id ->
                    runCatching {
                        val sale = book.saleWithLines(id)!!
                        // The customer's balance alone (23 Oct 2026, the review: the whole book's balances were summed for it).
                        Triple(sale, sale.sale.partyId?.let { pid -> book.liveBalance(pid) },
                            com.innovation313.roshankhata.data.Loyalty.onReceipt(book, sale.sale))
                    }.getOrNull()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { held.posting = false }
            }
        }
        lifecycleScope.launch {
            val result = work.await()
            // A screen rebuilt meanwhile shows nothing of it: the sale is in Recent sales.
            if (isFinishing || isDestroyed) return@launch
            result.onSuccess { receipt ->
                    message = null
                    postedTick++
                    if (receipt == null) { reload(); return@onSuccess }
                    val (sale, balance, points) = receipt
                    refreshAfterSale(sale.sale.partyId, balance)
                    val words = AndroidWords(this@CounterSaleActivity)
                    val shop = com.innovation313.roshankhata.data.BusinessProfile.businessName(this@CounterSaleActivity) ?: getString(R.string.app_name)
                    val text = SaleReceiptText.build(words, shop, sale, balance, points)
                    // The change to hand back, in the title (21 Oct 2026).
                    val title = getString(R.string.pos_posted, sale.sale.saleNumber) +
                        (if (change > 0.005) "  ·  " + getString(R.string.pos_change) + " " + com.innovation313.roshankhata.ui.Format.money(change) else "")
                    MaterialAlertDialogBuilder(this@CounterSaleActivity)
                        .setTitle(title)
                        .setMessage(text)
                        .setPositiveButton(R.string.share) { _, _ ->
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.pos_receipt)))
                        }
                        // The paired Bluetooth receipt printer (POS Phase 8), the ticket as a picture the roll's width.
                        .setNeutralButton(R.string.print_receipt) { _, _ ->
                            // Paid at least partly in cash: the drawer opens as the ticket prints (when it is set to).
                            val cash = sale.payments.any { it.method == com.innovation313.roshankhata.data.PaymentMethod.CASH && it.amount > 0.0 }
                            com.innovation313.roshankhata.ui.TextReceipt.print(this@CounterSaleActivity, lifecycleScope, text, cash = cash, direct = true)
                        }
                        .setNegativeButton(R.string.close, null)
                        .show()
                }.onFailure { e ->
                    android.util.Log.w("RoshanKhata", "sale not posted", e)
                    message = when (e) {
                        is Counter.ExpiredStock -> getString(R.string.pos_expired_stock, e.item, com.innovation313.roshankhata.ui.Calc.trim(e.fresh))
                        is Counter.ShortStock -> getString(R.string.pos_short_stock, e.item, com.innovation313.roshankhata.ui.Calc.trim(e.onHand))
                        else -> failWord(e, R.string.pos_post_failed)
                    }
                    // A held ticket another counter rang up meanwhile: this one is no longer held.
                    if (e is com.innovation313.roshankhata.data.CounterRefused && e.why == com.innovation313.roshankhata.data.CounterRefused.Why.HELD_GONE) draft.heldId = null
                }
        }
    }

    /**
     * A failed counter write in the till's own words ([counterFailWord]), else [fallback]; the English
     * reason only in the log. The fallback is the phone's own resource: WordIds holds only the words
     * the shared sources name.
     */
    private fun failWord(e: Throwable, fallback: Int): String {
        val name = com.innovation313.roshankhata.screens.counterFailWord(e, "")
        return if (name.isEmpty()) getString(fallback) else getString(com.innovation313.roshankhata.data.WordIds.string(name))
    }

    /** Park the ticket; cleared only once it is safely held (21 Oct 2026: a failed hold lost it). */
    private fun hold() {
        if (draft.posting || draft.lines.isEmpty()) return
        draft.posting = true
        // As complete(): written where a rebuilt screen cannot cancel it, the till cleared once it is held.
        val till = draft
        // The ticket is taken here, on the main thread, before anything is written (23 Oct 2026, the review:
        // the draft was read on the IO thread and then wiped, so a line scanned meanwhile was lost).
        val ticket = Counter.holdTicket(till)
        val book = dao
        val work = com.innovation313.roshankhata.data.AppScope.async {
            try {
                runCatching { Counter.hold(book, ticket) }.also { r ->
                    if (r.isSuccess) withContext(Dispatchers.Main) { till.clearAfter(ticket) }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { till.posting = false }
            }
        }
        lifecycleScope.launch {
            val held = work.await()
            if (isFinishing || isDestroyed) return@launch
            held.onSuccess { reload() }
                .onFailure { e ->
                    android.util.Log.w("RoshanKhata", "ticket not held", e)
                    message = failWord(e, R.string.pos_hold_failed)
                }
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
                        .setItems(arrayOf(getString(R.string.pos_make_invoice), getString(R.string.pos_return), getString(R.string.pos_void))) { _, which ->
                            when (which) {
                                // The ticket as an A4 invoice (21 Oct 2026): made once, then opened to print or share.
                                0 -> openInvoice(s.id)
                                // Who allowed it goes to the DAO, which refuses a void or return nobody allowed (23 Oct 2026).
                                1 -> com.innovation313.roshankhata.ui.AccessUi.allowed(this@CounterSaleActivity, dao, com.innovation313.roshankhata.data.Access.Permission.RETURN, getString(R.string.pos_return) + " " + s.saleNumber) { by -> startReturn(s.id, by) }
                                else -> com.innovation313.roshankhata.ui.AccessUi.allowed(this@CounterSaleActivity, dao, com.innovation313.roshankhata.data.Access.Permission.VOID, getString(R.string.pos_void) + " " + s.saleNumber) { by -> askVoid(s.id, s.saleNumber, by) }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    /** The sale's A4 invoice ([com.innovation313.roshankhata.data.SaleInvoice]), opened in the invoice editor to print or share. */
    private fun openInvoice(saleId: Long) {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { com.innovation313.roshankhata.data.SaleInvoice.of(dao, saleId, AndroidWords(this@CounterSaleActivity)) }.getOrNull()
            } ?: return@launch
            startActivity(Intent(this@CounterSaleActivity, InvoiceEditorActivity::class.java).putExtra(InvoiceEditorActivity.EXTRA_INVOICE_ID, id))
        }
    }

    private fun askVoid(saleId: Long, number: String, allowedBy: String?) {
        val input = android.widget.EditText(this).apply { hint = getString(R.string.pos_void_reason) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pos_void)
            .setMessage(getString(R.string.pos_void_confirm, number))
            .setView(input)
            .setPositiveButton(R.string.pos_void) { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) { toast(R.string.pos_err_reason); return@setPositiveButton }
                lifecycleScope.launch {
                    // A ticket with goods already back, or from a day that is closed, is not voided:
                    // a return is the way (21 Oct 2026), and the screen says which.
                    val refusal = withContext(Dispatchers.IO) { runCatching { dao.voidRefusal(saleId) }.getOrNull() }
                    val voided = if (refusal != null) null else withContext(Dispatchers.IO) { runCatching { dao.voidSale(saleId, reason, allowedBy = allowedBy) } }
                    val ok = voided?.getOrNull() == true
                    if (ok) com.innovation313.roshankhata.data.Snapshots.afterSale(this@CounterSaleActivity)
                    voided?.exceptionOrNull()?.let { android.util.Log.w("RoshanKhata", "sale not voided", it) }
                    message = when {
                        ok -> getString(R.string.pos_voided) + " " + number
                        refusal != null -> com.innovation313.roshankhata.screens.voidRefusalWord(refusal, "")
                            .let { w -> if (w.isEmpty()) getString(R.string.pos_void_failed) else getString(com.innovation313.roshankhata.data.WordIds.string(w)) }
                        else -> voided?.exceptionOrNull()?.let { failWord(it, R.string.pos_void_failed) }
                            ?: getString(R.string.pos_void_failed)
                    }
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Goods back against a ticket: the shared ReturnForm in a dialog, posted in one transaction. */
    private fun startReturn(saleId: Long, allowedBy: String?) {
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
                            // The form calls this only while nothing is being posted (ReturnDraft.posting).
                            draft.posting = true
                            lifecycleScope.launch {
                                try {
                                    val r = withContext(Dispatchers.IO) { runCatching { dao.postReturn(draft.toReturn(), draft.toItems(), allowedBy = allowedBy) } }
                                    com.innovation313.roshankhata.data.Snapshots.afterSale(this@CounterSaleActivity)
                                    r.onSuccess { id ->
                                        val number = withContext(Dispatchers.IO) { dao.getReturn(id)?.returnNumber }.orEmpty()
                                        message = getString(R.string.pos_return_posted, number)
                                        dialog.dismiss(); reload()
                                    }.onFailure {
                                        android.util.Log.w("RoshanKhata", "return not posted", it)
                                        problem = failWord(it, R.string.pos_return_failed)
                                    }
                                } finally { draft.posting = false }
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
