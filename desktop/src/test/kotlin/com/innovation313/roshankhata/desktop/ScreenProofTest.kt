package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.innovation313.roshankhata.data.LedgerAlerts
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.words.ProvideWords
import com.innovation313.roshankhata.words.desktopWords
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The Windows window itself as a picture (20 Oct redesign): the header, the
 * rail and Home, in English and Urdu, light and dark, with a made-up shop and
 * made-up customers. Kept in build/screen-proof and put on the desktop-test
 * pre-release by desktop.yml, so the design is looked at without a laptop
 * (lesson 16). Never real names or numbers.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenProofTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private val parties = listOf(
        PartyWithBalance(1, "Muhammad Aslam", "03000000001", true, null, 36200.0, now - 2 * 60 * 60 * 1000),
        PartyWithBalance(2, "Ghulam Rasool", "03000000002", true, null, 18900.0, now - 5 * 60 * 60 * 1000),
        PartyWithBalance(3, "Haji Bashir Ahmad", "03000000003", true, null, 0.0, now - day),
        PartyWithBalance(4, "Sample Seeds Co.", "03000000004", false, null, -48000.0, now - 2 * day),
        PartyWithBalance(5, "Allah Ditta", null, true, null, 9450.0, now - 3 * day),
        PartyWithBalance(6, "Khalid Javed", "03000000006", true, null, 12400.0, now - 6 * day)
    )

    /** Made-up cash sums and today's entries, so the table and the third card are drawn. */
    private fun figures() = HomeFigures(
        cashIn = 184500.0, cashOut = 61200.0,
        today = listOf(
            com.innovation313.roshankhata.data.EntryWithParty(1, 1, "Muhammad Aslam", 12500.0, false, "Cash received", "E-1", now - 20 * 60 * 1000),
            com.innovation313.roshankhata.data.EntryWithParty(2, 2, "Ghulam Rasool", 2400.0, true, "Maize seed 10 kg", "E-2", now - 90 * 60 * 1000),
            com.innovation313.roshankhata.data.EntryWithParty(3, 4, "Sample Seeds Co.", 20000.0, false, "Payment by cheque", "E-3", now - 3 * 60 * 60 * 1000),
            com.innovation313.roshankhata.data.EntryWithParty(4, 5, "Allah Ditta", 3200.0, true, "Wheat seed 2 bags", "E-4", now - 5 * 60 * 60 * 1000)
        )
    )

    private fun alerts() = listOf(
        LedgerAlerts.Alert(LedgerAlerts.Kind.CHEQUE, "Cheque", "Ghulam Rasool"),
        LedgerAlerts.Alert(LedgerAlerts.Kind.PLAN, "Plan", "Allah Ditta"),
        LedgerAlerts.Alert(LedgerAlerts.Kind.EXPIRING, "Expiring", "")
    )

    private fun shoot(
        name: String, ur: Boolean, dark: Boolean, dir: File,
        width: Int = 1440, height: Int = 900, scale: Float = 1f,
        page: @androidx.compose.runtime.Composable () -> Unit = {
            HomePane(
                parties, late = 2, alerts = alerts(), extra = emptyList(),
                figuresOf = { figures() }, reload = 0, shopName = "Al-Noor Agri Store",
                onOpen = {}, onAction = {}, onCustomer = {}, onAddParty = {}, onGot = {}
            )
        }
    ): File {
        // As Main does on choosing a language: the default locale is what Format.ltr and the
        // dates read their direction from. Without it the Urdu proof showed "Oct 2026 20" even
        // where the app itself is right (20 Oct): the picture must be drawn as the app draws it.
        val before = Locale.getDefault()
        val locale = Locale.forLanguageTag(if (ur) "ur" else "en").also { Locale.setDefault(it) }
        val words = desktopWords(locale)
        val scene = ImageComposeScene(width = width, height = height, density = Density(scale)) {
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (ur) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        Column(Modifier.fillMaxSize().background(LocalRoshanColors.current.pageBg)) {
                            AppHeader(
                                shopName = if (ur) "النور ایگری اسٹور" else "Al-Noor Agri Store",
                                logo = null, onShop = {},
                                backupLabel = words.text(com.innovation313.roshankhata.words.W.backup_now),
                                onBackup = {}, onPhone = {}, alerts = 3, onAlerts = {}, menu = emptyList(), onAddParty = {}
                            )
                            Row(Modifier.fillMaxSize()) {
                                SectionRail(Section.HOME, {}, badges = mapOf(Section.FOLLOWUP to 2))
                                Box(Modifier.weight(1f)) { page() }
                            }
                        }
                    }
                }
            }
        }
        try {
            // The first frame lays out, the next loads the pictures; then a second on, so Home's
            // rise and every eased colour (Motion.kt, 1.8.3) have finished before the picture.
            scene.render(0)
            scene.render(16_000_000)
            scene.render(500_000_000)
            val image = scene.render(1_000_000_000)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            return File(dir, "$name.png").also { it.writeBytes(png) }
        } finally {
            scene.close()
            Locale.setDefault(before)
        }
    }

    @Test
    fun `home draws in English and Urdu, light and dark`() {
        // Windows only: the pictures are for the Windows app and desktop.yml (Windows) publishes
        // them. On the Ubuntu runner Skia's native renderer crashed the test JVM with a SIGSEGV
        // in libstdc++ (Android CI, 20 Oct), taking every other desktop test down with it.
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val small = ArrayList<String>()
        for (ur in listOf(false, true)) for (dark in listOf(false, true)) {
            val name = "screen-home-${if (ur) "ur" else "en"}-${if (dark) "dark" else "light"}"
            val f = shoot(name, ur, dark, dir)
            // A blank window compresses to a few kilobytes; a drawn Home to far more.
            if (f.length() < 15_000) small += "$name=${f.length()}"
        }
        assertTrue("screens that look empty: $small", small.isEmpty())
    }

    /**
     * The owner's own laptop (20 Oct photos): 1366 x 768 at Windows' 125%, about
     * 1093 dp wide. Home, the Khata controls and an invoice's design stepper must
     * fit there without a word broken across lines ("Filte r by date").
     */
    @Test
    fun `laptop size draws without wrapping`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val home = shoot("screen-laptop-home-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f)
        val khata = shoot("screen-laptop-khata-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth(0.42f)) {
                    KhataControls(
                        type = PartyType.ALL, onType = {}, side = PartySide.ALL, onSide = {},
                        sort = PartySort.RECENT, onSort = {}, whenFilter = PartyWhen.ALL, onWhen = {},
                        selecting = false, onSelecting = {}
                    )
                }
                Box(Modifier.fillMaxWidth(0.5f)) { DesignStepper(shown = 3, saved = 1, onShow = {}, onApply = {}) }
            }
        }
        val khataDark = shoot("screen-laptop-khata-en-dark", ur = false, dark = true, dir = dir, width = 1366, height = 768, scale = 1.25f) {
            Box(Modifier.fillMaxWidth(0.42f)) {
                KhataControls(
                    type = PartyType.CUSTOMERS, onType = {}, side = PartySide.TO_GET, onSide = {},
                    sort = PartySort.RECENT, onSort = {}, whenFilter = PartyWhen.ALL, onWhen = {},
                    selecting = false, onSelecting = {}
                )
            }
        }
        for (f in listOf(home, khata, khataDark)) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 15_000)
    }

    /**
     * The pages rebuilt on the Figma rhythm (1.10.0 Phase 2), drawn from a small
     * book in a temporary folder: Cashbook, Cheques and the Khata Report, so the
     * tables, the figure cards and the pills are looked at before the owner installs.
     */
    @Test
    fun `cashbook, cheques, the report and the counter draw on the rhythm`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val home = java.nio.file.Files.createTempDirectory("rk-proof").toFile()
        val db = com.innovation313.roshankhata.data.DesktopDatabase.open(home.resolve("khata.db"))
        val dao = db.khataDao()
        kotlinx.coroutines.runBlocking {
            val a = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Muhammad Aslam", phone = "03000000001", isCustomer = true))
            val b = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Ghulam Rasool", phone = "03000000002", isCustomer = true))
            val sup = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Sample Seeds Co.", phone = "03000000004", isCustomer = false))
            dao.insertEntry(com.innovation313.roshankhata.data.LedgerEntry(partyId = a, amount = 12500.0, isGiven = true, note = "3 bags DAP", entryNumber = "E-1", timestamp = now - 2 * day))
            dao.insertEntry(com.innovation313.roshankhata.data.LedgerEntry(partyId = a, amount = 5000.0, isGiven = false, note = "Cash received", entryNumber = "E-2", timestamp = now - day))
            dao.insertEntry(com.innovation313.roshankhata.data.LedgerEntry(partyId = b, amount = 2400.0, isGiven = true, note = "Maize seed 10 kg", entryNumber = "E-3", timestamp = now - 3 * 60 * 60 * 1000))
            dao.insertCheque(com.innovation313.roshankhata.data.Cheque(partyId = a, amount = 20000.0, isReceived = true, chequeNumber = "0048213", bankName = "HBL", dueDate = now + day))
            dao.insertCheque(com.innovation313.roshankhata.data.Cheque(partyId = b, amount = 7500.0, isReceived = true, chequeNumber = "118822", bankName = "MCB", dueDate = now - 2 * day))
            dao.insertCheque(com.innovation313.roshankhata.data.Cheque(partyId = sup, amount = 48000.0, isReceived = false, chequeNumber = "772201", bankName = "UBL", dueDate = now + 5 * day))
            dao.insertCashEntry(com.innovation313.roshankhata.data.CashEntry(amount = 12500.0, isIncome = true, category = "Counter sale", note = "Walk-in", timestamp = now - 60 * 60 * 1000))
            dao.insertCashEntry(com.innovation313.roshankhata.data.CashEntry(amount = 3200.0, isIncome = false, category = "Rent", note = null, timestamp = now - 5 * 60 * 60 * 1000))
            dao.insertCashEntry(com.innovation313.roshankhata.data.CashEntry(amount = 1850.0, isIncome = false, category = "Wages", note = "Helper", timestamp = now - day))
            // The counter (POS Phase 1): products with prices and a barcode, one ticket already rung up.
            val dap = dao.insertProduct(com.innovation313.roshankhata.data.Product(name = "DAP", nameKey = "dap", normalisedName = "dap", category = "Fertilizer", company = "Engro", defaultUnit = "bag", salePrice = 3500.0, creditPrice = 3700.0, barcode = "8901234567890"))
            val conf = dao.insertProduct(com.innovation313.roshankhata.data.Product(name = "Confidor", nameKey = "confidor", normalisedName = "confidor", category = "Pesticide", company = "Bayer", defaultUnit = "bottle", salePrice = 875.0))
            dao.postSale(
                com.innovation313.roshankhata.data.Sale(partyId = a, customerName = "Muhammad Aslam", timestamp = now - 40 * 60 * 1000, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(
                    com.innovation313.roshankhata.data.SaleItem(saleId = 0, productId = dap, itemName = "DAP", quantity = 3.0, unit = "bag", rate = 3700.0),
                    com.innovation313.roshankhata.data.SaleItem(saleId = 0, productId = conf, itemName = "Confidor", quantity = 2.0, unit = "bottle", rate = 875.0)
                ),
                listOf(com.innovation313.roshankhata.data.SalePayment(saleId = 0, method = com.innovation313.roshankhata.data.PaymentMethod.CASH, amount = 10000.0))
            )
        }
        val words = desktopWords(Locale.ENGLISH)
        val partiesNow = kotlinx.coroutines.runBlocking { dao.partiesWithBalanceOnce() }
        try {
            shoot("screen-laptop-cashbook-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) { CashbookPane(dao) }
            shoot("screen-laptop-cheques-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) { ChequesPane(dao, partiesNow, words) {} }
            shoot("screen-laptop-report-en-dark", ur = false, dark = true, dir = dir, width = 1366, height = 768, scale = 1.25f) { ReportPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0) }
            shoot("screen-laptop-counter-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                CounterSalePane(dao, partiesNow, words, rtl = false, shopName = "Al-Noor Agri Store", currency = "Rs", reload = 0, onChanged = {}, onReceipt = {}, keys = KeyActions())
            }
            shoot("screen-laptop-inventory-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                InventoryPane(dao, words, reload = 0) {}
            }
            shoot("screen-counter-en-dark", ur = false, dark = true, dir = dir, width = 1600, height = 900, scale = 1f) {
                CounterSalePane(dao, partiesNow, words, rtl = false, shopName = "Al-Noor Agri Store", currency = "Rs", reload = 0, onChanged = {}, onReceipt = {}, keys = KeyActions())
            }
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }

    /**
     * The shared forms in their Windows dialogs (21 Oct 2026): a return, a stock
     * count and the shift screen each scroll themselves, so the dialog's frame
     * must not scroll around them — a scroll inside a scroll is measured with no
     * height limit, which throws for a verticalScroll and leaves a weighted list
     * with no height. Drawing them is the test: a throw fails it.
     */
    @Test
    fun `the shared forms draw inside a dialog frame`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val home = java.nio.file.Files.createTempDirectory("rk-proof-forms").toFile()
        val db = com.innovation313.roshankhata.data.DesktopDatabase.open(home.resolve("khata.db"))
        val dao = db.khataDao()
        val (sale, stock) = kotlinx.coroutines.runBlocking {
            val a = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Muhammad Aslam", phone = "03000000001", isCustomer = true))
            val dap = dao.insertProduct(com.innovation313.roshankhata.data.Product(name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", salePrice = 3500.0))
            val id = dao.postSale(
                com.innovation313.roshankhata.data.Sale(partyId = a, customerName = "Muhammad Aslam", subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(com.innovation313.roshankhata.data.SaleItem(saleId = 0, productId = dap, itemName = "DAP", quantity = 3.0, unit = "bag", rate = 3500.0)),
                listOf(com.innovation313.roshankhata.data.SalePayment(saleId = 0, method = com.innovation313.roshankhata.data.PaymentMethod.CASH, amount = 10500.0))
            )
            dao.openShift(com.innovation313.roshankhata.data.Shift(user = null, openingFloat = 5000.0, openedAt = now - 3 * 60 * 60 * 1000))
            dao.saleWithLines(id)!! to com.innovation313.roshankhata.data.Stock.of(dao)
        }
        val words = desktopWords(Locale.ENGLISH)
        fun frame(name: String, scroll: Boolean = false, body: @androidx.compose.runtime.Composable () -> Unit): File {
            val scene = ImageComposeScene(width = 620, height = 720, density = Density(1f)) {
                RoshanTheme(dark = false) { ProvideWords(words) { DialogFrame(name, onClose = {}, scroll = scroll, footer = {}) { body() } } }
            }
            try {
                scene.render(0); scene.render(16_000_000)
                val png = scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                return File(dir, "$name.png").also { it.writeBytes(png) }
            } finally { scene.close() }
        }
        try {
            val shots = listOf(
                frame("dialog-return-en") { com.innovation313.roshankhata.screens.ReturnForm(com.innovation313.roshankhata.screens.ReturnDraft(sale, emptyMap()), onPost = {}, onCancel = {}) },
                frame("dialog-count-en") { com.innovation313.roshankhata.screens.StockCountForm(com.innovation313.roshankhata.screens.CountDraft(stock), onSave = {}, onCancel = {}, modifier = Modifier.fillMaxSize()) },
                frame("dialog-shift-en") { com.innovation313.roshankhata.screens.ShiftScreen(dao, Modifier.fillMaxSize()) }
            )
            for (f in shots) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 6_000)
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }

    /**
     * The counter's extras and the new reports (21 Oct 2026), drawn from a small
     * made-up book: the item grid with a product's variants, the customer's
     * screen, dues by age and the sales report's chart.
     */
    @Test
    fun `the item grid, the customer screen, dues by age and the report chart draw`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val home = java.nio.file.Files.createTempDirectory("rk-proof-extras").toFile()
        val db = com.innovation313.roshankhata.data.DesktopDatabase.open(home.resolve("khata.db"))
        val dao = db.khataDao()
        val draft = com.innovation313.roshankhata.data.CounterDraft()
        val products = kotlinx.coroutines.runBlocking {
            fun p(name: String, cat: String?, price: Double?, parent: Long? = null, variant: String? = null) =
                com.innovation313.roshankhata.data.Product(name = name, nameKey = name.lowercase(), normalisedName = name.lowercase().replace(" ", ""),
                    category = cat, defaultUnit = "piece", salePrice = price, parentId = parent, variant = variant)
            val soap = dao.insertProduct(p("Soap", "Bath", null))
            dao.insertProduct(p("Soap Rose", "Bath", 90.0, soap, "Rose"))
            dao.insertProduct(p("Soap Lime", "Bath", 95.0, soap, "Lime"))
            dao.insertProduct(p("Shampoo", "Bath", 450.0))
            dao.insertProduct(p("Rice 5 kg", "Grocery", 1_650.0))
            dao.insertProduct(p("Cooking Oil", "Grocery", 2_900.0))
            dao.insertProduct(p("Tea 250 g", "Grocery", 780.0))
            dao.insertProduct(p("Biscuits", "Snacks", 60.0))
            // Dues of different ages, and today's sales for the chart.
            val a = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Muhammad Aslam", phone = "03000000001"))
            val b = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Ghulam Rasool", phone = "03000000002"))
            val c = dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Allah Ditta", phone = "03000000005"))
            dao.insertEntryNumbered(com.innovation313.roshankhata.data.LedgerEntry(partyId = a, amount = 12_000.0, isGiven = true, entryNumber = "", timestamp = now - 100 * day))
            dao.insertEntryNumbered(com.innovation313.roshankhata.data.LedgerEntry(partyId = a, amount = 6_500.0, isGiven = true, entryNumber = "", timestamp = now - 12 * day))
            dao.insertEntryNumbered(com.innovation313.roshankhata.data.LedgerEntry(partyId = b, amount = 18_900.0, isGiven = true, entryNumber = "", timestamp = now - 45 * day))
            dao.insertEntryNumbered(com.innovation313.roshankhata.data.LedgerEntry(partyId = b, amount = 4_000.0, isGiven = false, entryNumber = "", timestamp = now - 20 * day))
            dao.insertEntryNumbered(com.innovation313.roshankhata.data.LedgerEntry(partyId = c, amount = 3_200.0, isGiven = true, entryNumber = "", timestamp = now - 70 * day))
            val start = com.innovation313.roshankhata.data.DayCloseMath.startOf(com.innovation313.roshankhata.data.DayCloseMath.dayOf(now))
            listOf(9 to 1_650.0, 9 to 450.0, 11 to 2_900.0, 13 to 780.0, 16 to 5_800.0, 17 to 120.0).forEach { (h, amount) ->
                dao.postSale(
                    com.innovation313.roshankhata.data.Sale(timestamp = minOf(now - 1, start + h * 3_600_000L), subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                    listOf(com.innovation313.roshankhata.data.SaleItem(saleId = 0, itemName = "Goods", quantity = 1.0, rate = amount)),
                    listOf(com.innovation313.roshankhata.data.SalePayment(saleId = 0, method = com.innovation313.roshankhata.data.PaymentMethod.CASH, amount = amount))
                )
            }
            dao.productsOnce()
        }
        draft.add(products.first { it.name == "Rice 5 kg" }, qty = 2.0)
        draft.add(products.first { it.name == "Soap Rose" }, qty = 3.0)
        draft.add(products.first { it.name == "Tea 250 g" })
        draft.addPayment(com.innovation313.roshankhata.data.PaymentMethod.CASH, 5_000.0)
        val qr = java.awt.image.BufferedImage(240, 240, java.awt.image.BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics(); g.color = java.awt.Color.WHITE; g.fillRect(0, 0, 240, 240); g.color = java.awt.Color.BLACK
            for (y in 0 until 24) for (x in 0 until 24) if ((x * 7 + y * 13 + x * y) % 3 == 0) g.fillRect(x * 10, y * 10, 10, 10)
            g.dispose()
        }.toComposeImageBitmap()
        val words = desktopWords(Locale.ENGLISH)
        try {
            val shots = listOf(
                shoot("screen-counter-grid-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                    Box(Modifier.fillMaxSize()) { ItemGridPane(products, emptyMap(), draft, onPick = {}, modifier = Modifier.fillMaxSize()) }
                },
                shoot("screen-aging-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                    AgingPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0) {}
                },
                shoot("screen-aging-ur-dark", ur = true, dark = true, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                    AgingPane(dao, desktopWords(Locale.forLanguageTag("ur")), rtl = true, shopName = "النور ایگری اسٹور", reload = 0) {}
                },
                shoot("screen-sales-chart-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                    SalesReportPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0)
                }
            )
            val scene = ImageComposeScene(width = 1280, height = 720, density = Density(1f)) {
                RoshanTheme(dark = false) { ProvideWords(words) { CustomerScreen(draft, "Al-Noor Agri Store", qr) } }
            }
            val customer = try {
                scene.render(0); scene.render(16_000_000)
                File(dir, "screen-customer-display-en.png").also { it.writeBytes(scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            } finally { scene.close() }
            for (f in shots + customer) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 15_000)
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }
}
