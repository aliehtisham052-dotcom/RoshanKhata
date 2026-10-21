package com.innovation313.roshankhata

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
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

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
                    dao.heldSalesOnce().size
                )
            }
            products = loaded.products; parties = loaded.parties; onHand = loaded.onHand; heldCount = loaded.held
        }
    }

    private class Loaded(val products: List<Product>, val parties: List<PartyWithBalance>, val onHand: Map<Long, Double?>, val held: Int)

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

    private fun complete() {
        draft.problem?.let { message = getString(com.innovation313.roshankhata.data.WordIds.string(problemWord(it))); return }
        lifecycleScope.launch {
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
                    .setNegativeButton(R.string.close, null)
                    .show()
            }.onFailure { message = it.message ?: getString(R.string.backup_failed) }
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
                .setTitle(getString(R.string.pos_held_tickets, held.size))
                .setItems(labels) { _, i ->
                    lifecycleScope.launch {
                        val full = withContext(Dispatchers.IO) { dao.saleWithLines(held[i].id) } ?: return@launch
                        draft.load(full) { onHand[it] }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
