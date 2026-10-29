package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.innovation313.roshankhata.data.LedgerAlerts
import com.innovation313.roshankhata.data.PartyWhen
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
import kotlinx.coroutines.flow.first

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

    /** Made-up cash sums, today's and the last entries and a promise, so every card of Home is drawn. */
    private fun figures(): HomeFigures {
        val today = listOf(
            com.innovation313.roshankhata.data.EntryWithParty(1, 1, "Muhammad Aslam", 12500.0, false, "Cash received", "E-1", now - 20 * 60 * 1000),
            com.innovation313.roshankhata.data.EntryWithParty(2, 2, "Ghulam Rasool", 2400.0, true, "Maize seed 10 kg", "E-2", now - 90 * 60 * 1000)
        )
        return HomeFigures(
            cashIn = 184500.0, cashOut = 61200.0, drawer = 23400.0,
            today = today,
            recent = today + listOf(
                com.innovation313.roshankhata.data.EntryWithParty(3, 4, "Sample Seeds Co.", 20000.0, false, "Payment by cheque", "E-3", now - day - 3 * 60 * 60 * 1000),
                com.innovation313.roshankhata.data.EntryWithParty(4, 5, "Allah Ditta", 3200.0, true, "Wheat seed 2 bags", "E-4", now - 2 * day),
                com.innovation313.roshankhata.data.EntryWithParty(5, 6, "Khalid Javed", 6400.0, true, "DAP 2 bags", "E-5", now - 4 * day),
                com.innovation313.roshankhata.data.EntryWithParty(6, 3, "Haji Bashir Ahmad", 9000.0, false, "Cash", "E-6", now - 9 * day)
            ),
            promisedToday = setOf(5L)
        )
    }

    private fun alerts() = listOf(
        LedgerAlerts.Alert(LedgerAlerts.Kind.CHEQUE, "Cheque", "Ghulam Rasool"),
        LedgerAlerts.Alert(LedgerAlerts.Kind.PLAN, "Plan", "Allah Ditta"),
        LedgerAlerts.Alert(LedgerAlerts.Kind.EXPIRING, "Expiring", "")
    )

    private fun shoot(
        name: String, ur: Boolean, dark: Boolean, dir: File,
        width: Int = 1440, height: Int = 900, scale: Float = 1f,
        /** The language drawn, when not simply English or Urdu (21 Oct 2026: Arabic, Bengali, Indonesian proofs). */
        lang: String = if (ur) "ur" else "en",
        /** The place open in the rail; a merged page's tabs show above it as in the window (Marhala 3). */
        section: Section = Section.HOME,
        /** More real time for a page that draws off the window's thread in steps (an invoice: the book, then its pages). */
        waits: Int = 1,
        page: @androidx.compose.runtime.Composable () -> Unit = {
            HomePane(
                parties, late = 2, alerts = alerts(), extra = emptyList(),
                figuresOf = { figures() }, reload = 0,
                onOpen = {}, onCustomer = {}, onGave = {}, onGot = {}, onStatement = {}, onNewBill = {}
            )
        }
    ): File {
        // As Main does on choosing a language: the default locale is what Format.ltr and the
        // dates read their direction from. Without it the Urdu proof showed "Oct 2026 20" even
        // where the app itself is right (20 Oct): the picture must be drawn as the app draws it.
        val before = Locale.getDefault()
        val locale = Locale.forLanguageTag(lang).also { Locale.setDefault(it) }
        val words = desktopWords(locale)
        val scene = ImageComposeScene(width = width, height = height, density = Density(scale)) {
            DeskTheme(dark = dark) {
                ProvideWords(words) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (ur) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        // The window as Main draws it (Marhala 2): the rail with the shop on top, then the top row
                        // carrying the page's own head, then the page.
                        val head = androidx.compose.runtime.remember { PageHead() }
                        Row(Modifier.fillMaxSize().background(LocalRoshanColors.current.pageBg)) {
                            SectionRail(section, {}, badges = mapOf(Section.FOLLOWUP to 2), top = {
                                ShopCard(if (ur) "النور ایگری اسٹور" else "Al-Noor Agri Store", null, null, onProfile = {}, onSwitch = {})
                            })
                            Column(Modifier.weight(1f)) {
                                AppHeader(
                                    head = head,
                                    backupAt = System.currentTimeMillis() - 3 * 60 * 60 * 1000L, backupOld = false,
                                    onBackup = {}, onPhone = {}, alerts = 3, onAlerts = {}, menu = emptyList(), onAddParty = {}
                                )
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    androidx.compose.runtime.CompositionLocalProvider(LocalPageHead provides head) {
                                        Column(Modifier.fillMaxSize()) {
                                            PageTabRow(section) {}
                                            Box(Modifier.weight(1f).fillMaxWidth()) { page() }
                                        }
                                    }
                                }
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
            // A page that reads the book off the window's thread (Expiry, the register, the sales report) gets
            // real time to answer before the frames that are kept.
            repeat(waits) { n ->
                Thread.sleep(400)
                scene.render(250_000_000L + n * 100_000_000L)
            }
            scene.render(500_000_000L + waits * 100_000_000L)
            val image = scene.render(1_000_000_000L + waits * 100_000_000L)
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
     * Home in the languages the proofs never showed (21 Oct 2026, the nine-language
     * review): Arabic (right to left, Naskh), Bengali and Hindi (tall glyphs that
     * Windows draws with Nirmala UI), Indonesian (the longest translations, 24-hour
     * clock, Rp 1.250.000). Looked at, not only measured: a clipped Bengali line or a
     * chevron pointing the wrong way in Arabic shows here first.
     */
    @Test
    fun `home draws in Arabic, Hindi, Bengali and Indonesian`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val small = ArrayList<String>()
        for (lang in listOf("ar", "hi", "bn", "in")) {
            val rtl = lang == "ar"
            val name = "screen-home-$lang-light"
            val f = shoot(name, ur = rtl, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, lang = lang)
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
        // The owner's two real windows (the plan, 25 Oct 2026): taskbar along the bottom, 1093 x 535 dp, and
        // along the side, 1043 x 590 dp — at Windows' 125%, so 1366 x 669 and 1304 x 738 pixels.
        val home = shoot("screen-laptop-home-en", ur = false, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f)
        val homeSide = shoot("screen-laptop-side-home-en", ur = false, dark = false, dir = dir, width = 1304, height = 738, scale = 1.25f)
        val homeUr = shoot("screen-laptop-home-ur-dark", ur = true, dark = true, dir = dir, width = 1366, height = 669, scale = 1.25f)
        // The Khata page as the mockup draws it (Marhala 4): the list and the chosen account, at both windows.
        val khata = shoot("screen-laptop-khata-ur", ur = true, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.KHATA) {
            KhataProof(ur = true)
        }
        val khataDark = shoot("screen-laptop-khata-en-dark", ur = false, dark = true, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.KHATA) {
            KhataProof(ur = false)
        }
        val khataSide = shoot("screen-laptop-side-khata-en", ur = false, dark = false, dir = dir, width = 1304, height = 738, scale = 1.25f, section = Section.KHATA) {
            KhataProof(ur = false)
        }
        // Several entries being chosen to delete (the phone's select mode): the bar and the ticks, in Urdu.
        val khataPick = shoot("screen-laptop-khata-pick-ur", ur = true, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.KHATA) {
            KhataProof(ur = true, picked = setOf(4L, 2L))
        }
        val stepper = shoot("screen-laptop-stepper-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
            Box(Modifier.fillMaxWidth(0.5f)) { DesignStepper(shown = 3, saved = 1, onShow = {}, onApply = {}) }
        }
        for (f in listOf(home, homeSide, homeUr, khata, khataDark, khataSide, khataPick)) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 15_000)
        assertTrue("${stepper.name} looks empty", stepper.length() > 2_000)
    }

    /**
     * The Khata page from made-up parties and entries, as the window lays it out: the list's pieces in the
     * mockup's 340 dp and the chosen account beside it (the page itself lives in Main's Book, with its state).
     */
    @androidx.compose.runtime.Composable
    private fun KhataProof(ur: Boolean, picked: Set<Long>? = null) {
        val day = 24L * 60 * 60 * 1000
        val t = now - 2 * 60 * 60 * 1000
        val parties = listOf(
            PartyWithBalance(1, if (ur) "اسلم کریم" else "Aslam Kareem", "03000000101", true, null, 8_450.0, lastActivity = t),
            PartyWithBalance(2, if (ur) "بلال احمد" else "Bilal Ahmed", "03330000202", true, null, 3_900.0, lastActivity = t - day),
            PartyWithBalance(3, if (ur) "حاجی یوسف" else "Haji Yousaf", "03450000303", true, null, 1_900.0, lastActivity = t - 3 * day),
            PartyWithBalance(4, "Sample Seeds Co.", null, false, null, 0.0, lastActivity = t - 4 * day),
            PartyWithBalance(5, if (ur) "رانا ساجد" else "Rana Sajid", "03010000505", true, null, -2_000.0, lastActivity = t - 6 * day)
        )
        val entries = listOf(
            com.innovation313.roshankhata.data.EntryWithItems(
                com.innovation313.roshankhata.data.LedgerEntry(id = 4, partyId = 1, amount = 4_550.0, isGiven = true, entryNumber = "RK-000214", timestamp = t),
                listOf(com.innovation313.roshankhata.data.EntryItem(id = 1, entryId = 4, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 4_550.0))
            ),
            com.innovation313.roshankhata.data.EntryWithItems(
                com.innovation313.roshankhata.data.LedgerEntry(id = 3, partyId = 1, amount = 8_800.0, isGiven = false, entryNumber = "RK-000213",
                    timestamp = t - 30 * 60 * 1000, paymentMethod = com.innovation313.roshankhata.data.PaymentMethod.CASH), emptyList()
            ),
            com.innovation313.roshankhata.data.EntryWithItems(
                com.innovation313.roshankhata.data.LedgerEntry(id = 2, partyId = 1, amount = 12_100.0, isGiven = true, entryNumber = "RK-000188", timestamp = t - 3 * day),
                listOf(com.innovation313.roshankhata.data.EntryItem(id = 2, entryId = 2, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 12_100.0))
            ),
            com.innovation313.roshankhata.data.EntryWithItems(
                com.innovation313.roshankhata.data.LedgerEntry(id = 1, partyId = 1, amount = 600.0, isGiven = true, note = "Spray pump repair", entryNumber = "RK-000151", timestamp = t - 9 * day),
                emptyList()
            )
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(start = androidx.compose.ui.unit.Dp(16f), end = androidx.compose.ui.unit.Dp(16f), top = androidx.compose.ui.unit.Dp(4f), bottom = androidx.compose.ui.unit.Dp(14f))) {
            val listWidth = (maxWidth * 0.38f).coerceIn(androidx.compose.ui.unit.Dp(300f), androidx.compose.ui.unit.Dp(400f))
            Row(Modifier.fillMaxSize(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(androidx.compose.ui.unit.Dp(12f))) {
                Column(Modifier.width(listWidth), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(androidx.compose.ui.unit.Dp(10f))) {
                    PartyTabs(PartyType.ALL, mapOf(PartyType.ALL to 5, PartyType.CUSTOMERS to 4, PartyType.SUPPLIERS to 1), {})
                    KhataTotals(get = 14_250.0, give = 2_000.0, hidden = false, side = PartySide.ALL, onSide = {})
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(androidx.compose.ui.unit.Dp(8f))) {
                        FluentField(value = "", onValueChange = {}, placeholder = com.innovation313.roshankhata.words.word(com.innovation313.roshankhata.words.W.search_parties),
                            leadingIcon = "ic_search", trailing = { KeyCap("Ctrl F") }, modifier = Modifier.weight(1f))
                        KhataFindTools(PartySide.ALL, {}, PartyWhen.ALL, {}, PartySort.RECENT, {})
                    }
                    KhataInfoRow(parties, PartySort.RECENT, canStatements = true, canExport = true, onStatements = {}, onExcel = {}, onImport = {}, onChoose = {})
                    parties.forEach { p -> PartyCard(p, null, open = p.id == 1L, selecting = false, ticked = false, onClick = {}, onTick = {}) }
                }
                CustomerPane(
                    parties.first(), null, entries,
                    identity = com.innovation313.roshankhata.words.word(com.innovation313.roshankhata.words.W.son_of_prefix, if (ur) "محمد کریم" else "Muhammad Kareem") +
                        "  \u00b7  " + (if (ur) "چک 12" else "Chak 12"),
                    promise = com.innovation313.roshankhata.words.word(com.innovation313.roshankhata.words.W.harvest_promise_header, "15 Nov"),
                    seasons = listOf("Rabi 2025"),
                    onGave = {}, onGot = {}, onRemind = {}, onStatement = {}, onEdit = {}, onDelete = {}, onEntry = {},
                    onPromise = {}, promiseInMenu = true, onQr = {},
                    pick = picked?.let { EntryPick(it, onToggle = {}, onAll = {}, onDelete = {}, onCancel = {}) }, onPickEntries = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
            // Batches for Stock & expiry (Marhala 3): the ticket above took 2 Confidor with no batch, so the lot that
            // expires first shows 22 of its 24 — the one rule the counter, Expiry and the register share.
            dao.insertSupplierBill(
                null, com.innovation313.roshankhata.data.SupplierBill(partyId = sup, billNumber = "SB-1042", billDate = now - 20 * day, totalAmount = 0.0),
                listOf(
                    com.innovation313.roshankhata.data.BillItem(billId = 0, productName = "Confidor", batchNumber = "CF-2410", expiryDate = now + 18 * day, quantity = 24.0, unit = "bottle", rate = 700.0, productId = conf),
                    com.innovation313.roshankhata.data.BillItem(billId = 0, productName = "Confidor", batchNumber = "CF-2502", expiryDate = now + 200 * day, quantity = 36.0, unit = "bottle", rate = 720.0, productId = conf),
                    com.innovation313.roshankhata.data.BillItem(billId = 0, productName = "DAP", batchNumber = "DP-77", quantity = 40.0, unit = "bag", rate = 3300.0, productId = dap)
                )
            )
        }
        val words = desktopWords(Locale.ENGLISH)
        val partiesNow = kotlinx.coroutines.runBlocking { dao.partiesWithBalanceOnce() }
        try {
            shoot("screen-laptop-cashbook-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.CASHBOOK) { CashbookPane(dao) }
            // The owner's shortest window, in Urdu: the head keeps its title, the four figures and the two buttons fit.
            shoot("screen-laptop-cashbook-short-ur", ur = true, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.CASHBOOK) { CashbookPane(dao) }
            shoot("screen-laptop-cheques-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.CHEQUES) { ChequesPane(dao, partiesNow, words) {} }
            // Marhala 4: the bills' three figures (the phone's), and products with their own empty and search words.
            shoot("screen-laptop-bills-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.BILLS) { BillsPane(dao, partiesNow, words, home) {} }
            shoot("screen-laptop-products-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.PRODUCTS) { ProductsPane(dao, words) }
            // Marhala 4, part 7: the phone's 24 Oct report — the period chips, the summary card, the days.
            shoot("screen-laptop-report-en-dark", ur = false, dark = true, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.REPORT, waits = 3) { ReportPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0, parties = partiesNow) }
            shoot("screen-laptop-report-short-ur", ur = true, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.REPORT, waits = 3) { ReportPane(dao, words, rtl = true, shopName = "Al-Noor Agri Store", reload = 0, parties = partiesNow) }
            // The sales and purchase register in the page frame: chips, the two figures, each line with what it was.
            shoot("screen-laptop-salesregister-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.REGISTER, waits = 3) { RegisterPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0) }
            shoot("screen-laptop-counter-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.COUNTER) {
                CounterSalePane(dao, partiesNow, words, rtl = false, shopName = "Al-Noor Agri Store", currency = "Rs", reload = 0, onChanged = {}, onReceipt = {}, keys = KeyActions())
            }
            // The owner's shortest window (1093 x 535 dp, the taskbar at the foot) with a ticket being rung up
            // (Marhala 4): its lines, the discount in money, cash handed over and the change, the foot in sight.
            val products = kotlinx.coroutines.runBlocking { dao.productsOnce() }
            val ticket = com.innovation313.roshankhata.data.CounterDraft().apply {
                products.firstOrNull { it.name == "DAP" }?.let { add(it, 40.0, qty = 2.0) }
                products.firstOrNull { it.name == "Confidor" }?.let { add(it, 58.0, qty = 3.0) }
                discount = "500"
                addPayment(com.innovation313.roshankhata.data.PaymentMethod.CASH).amount = "10000"
            }
            shoot("screen-laptop-counter-short-en", ur = false, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.COUNTER) {
                CounterSalePane(dao, partiesNow, words, rtl = false, shopName = "Al-Noor Agri Store", currency = "Rs", reload = 0, onChanged = {}, onReceipt = {}, keys = KeyActions(), draft = ticket)
            }
            shoot("screen-laptop-inventory-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
                InventoryPane(dao, words, reload = 0) {}
            }
            shoot("screen-counter-en-dark", ur = false, dark = true, dir = dir, width = 1600, height = 900, scale = 1f) {
                CounterSalePane(dao, partiesNow, words, rtl = false, shopName = "Al-Noor Agri Store", currency = "Rs", reload = 0, onChanged = {}, onReceipt = {}, keys = KeyActions())
            }
            // Merged pages (Marhala 3): the tabs above the page, the rail marking the page that holds them.
            shoot("screen-laptop-expiry-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.EXPIRY) {
                ExpiryPane(dao)
            }
            shoot("screen-laptop-register-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.STOCK) {
                StockPane(dao, desktopWords(Locale.forLanguageTag("ur")), rtl = true, shopName = "Al-Noor Agri Store", reload = 0)
            }
            shoot("screen-laptop-sales-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.SALES) {
                SalesReportPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0)
            }
            // Marhala 4, part 7: the report and tool pages in the page frame.
            shoot("screen-laptop-insights-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.INSIGHTS, waits = 3) {
                InsightsPane(dao, reload = 0)
            }
            shoot("screen-laptop-cashflow-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.CASHFLOW, waits = 3) {
                CashFlowPane(dao, reload = 0)
            }
            shoot("screen-laptop-followup-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.FOLLOWUP, waits = 2) {
                FollowUpPane(dao, partiesNow, words, "Al-Noor Agri Store", com.innovation313.roshankhata.data.FileShopSettings(home.resolve("shop.properties")), ReminderStore.forBusiness(home, 1L))
            }
            shoot("screen-laptop-aging-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.AGING, waits = 3) {
                AgingPane(dao, words, rtl = false, shopName = "Al-Noor Agri Store", reload = 0) {}
            }
            shoot("screen-laptop-zakat-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.ZAKAT, waits = 2) {
                ZakatPane(dao)
            }
            CalcMemory.text = "12500+3700*2"
            shoot("screen-laptop-calculator-short-ur", ur = true, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.CALC) {
                CalculatorPane(onKhata = {})
            }
            CalcMemory.text = ""
            // Marhala 4, part 9: staff in the frame, who did what in the phone's words, daily copies with the folder's own.
            kotlinx.coroutines.runBlocking {
                dao.insertStaff(com.innovation313.roshankhata.data.Staff(name = "Nadeem Akhtar", monthlySalary = 25000.0, role = com.innovation313.roshankhata.data.Role.CASHIER))
                dao.insertStaff(com.innovation313.roshankhata.data.Staff(name = "Bilal", monthlySalary = 18000.0))
            }
            shoot("screen-laptop-staff-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.STAFF, waits = 2) {
                StaffPane(dao)
            }
            shoot("screen-laptop-audit-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.AUDIT, waits = 3) {
                AuditPane(dao)
            }
            val daily = home.resolve("daily-proof").apply { mkdirs() }
            val there = home.resolve("outside-proof").apply { mkdirs() }
            listOf("2026-10-24.txt", "2026-10-23.txt", "2026-10-23-before-restore-1761200000000.txt").forEach { daily.resolve(it).writeText("{}") }
            listOf("2026-10-22.txt").forEach { there.resolve(it).writeText("{}") }
            shoot("screen-laptop-daily-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.DAILY, waits = 2) {
                DailyCopiesPane(dao, com.innovation313.roshankhata.data.FileShopSettings(home.resolve("shop-proof.properties")), daily, there, there, onFolder = {}) {}
            }
            // Invoices (Marhala 4, part 5): the list narrow, the open invoice's head and actions in one block, its
            // pages in the height left — at the owner's shortest window, and in Urdu; the form beside its picture.
            val lines = listOf(
                com.innovation313.roshankhata.data.InvoiceItem(invoiceId = 0, itemName = "DAP", quantity = 4.0, unit = "bag", rate = 3700.0),
                com.innovation313.roshankhata.data.InvoiceItem(invoiceId = 0, itemName = "Confidor", quantity = 3.0, unit = "bottle", rate = 875.0),
                com.innovation313.roshankhata.data.InvoiceItem(invoiceId = 0, itemName = "Wheat seed", quantity = 50.0, unit = "kg", rate = 190.0)
            )
            kotlinx.coroutines.runBlocking {
                dao.saveInvoiceWithItems(com.innovation313.roshankhata.data.Invoice(customerName = "Ghulam Rasool", customerPhone = "03000000002", invoiceDate = now - 3 * day, templateId = 4), lines.take(1))
                dao.saveInvoiceWithItems(com.innovation313.roshankhata.data.Invoice(customerName = "Muhammad Aslam", customerPhone = "03000000001", invoiceDate = now - 2 * 60 * 60 * 1000, templateId = 1, receivedAmount = 10000.0), lines)
            }
            fun designs(tag: String): com.innovation313.roshankhata.desktop.invoice.Context {
                val shop = home.resolve("invoice-shop-$tag").apply { mkdirs() }
                val settings = com.innovation313.roshankhata.data.FileShopSettings(shop.resolve("shop.properties")).apply {
                    setBusinessName("Al-Noor Agri Store"); setBusinessAddress("Main Bazaar, Sample Town"); setBusinessPhone("0300 0000000")
                }
                val locale = Locale.forLanguageTag(tag)
                return com.innovation313.roshankhata.desktop.invoice.Context(desktopWords(locale), settings, com.innovation313.roshankhata.desktop.invoice.ProfileImages(shop.resolve("pictures")),
                    rtl = com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(locale), locale = locale)
            }
            val enDesigns = designs("en")
            val urDesigns = designs("ur")
            val urWords = desktopWords(Locale.forLanguageTag("ur"))
            val invoiceShots = listOf(
                shoot("screen-laptop-invoices-short-en", ur = false, dark = false, dir = dir, width = 1366, height = 669, scale = 1.25f, section = Section.INVOICES, waits = 4) {
                    InvoicesPane(dao, partiesNow, words, rtl = false, shopName = "Al-Noor Agri Store", shopLine = "Main Bazaar, Sample Town", designs = { enDesigns })
                },
                shoot("screen-laptop-invoices-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.INVOICES, waits = 4) {
                    InvoicesPane(dao, partiesNow, urWords, rtl = true, shopName = "النور ایگری اسٹور", shopLine = null, designs = { urDesigns })
                }
            )
            for (f in invoiceShots) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 20_000)
            // The form as the laptop's dialog holds it (1093 dp window less its margins), wide enough for the picture beside.
            val invoice = kotlinx.coroutines.runBlocking { dao.observeInvoicePage(5).first().first { it.customerName == "Muhammad Aslam" }.let { dao.getInvoice(it.id)!! to dao.invoiceItems(it.id) } }
            val shelf = kotlinx.coroutines.runBlocking { ProductShelf(dao.productsOnce(), emptyList(), "") }
            val form = ImageComposeScene(width = 1326, height = 629, density = Density(1.25f)) {
                DeskTheme(dark = false) {
                    ProvideWords(words) {
                        InvoiceForm(
                            parties = partiesNow, onSave = { _, _ -> }, onCancel = {},
                            existing = invoice.first, existingLines = invoice.second,
                            preview = { inv, l -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(enDesigns, inv, l)?.picture(0, 2f) },
                            fields = InvoiceFields(received = true, dueDate = true),
                            products = shelf
                        )
                    }
                }
            }
            try {
                form.render(0); form.render(16_000_000)
                // The picture is drawn 0.4 s after the last change (the form's own wait), off the window's thread.
                repeat(4) { n -> Thread.sleep(400); form.render(250_000_000L + n * 100_000_000L) }
                val png = form.render(1_500_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                val f = File(dir, "dialog-laptop-invoice-en.png").also { it.writeBytes(png) }
                assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 20_000)
            } finally { form.close() }
            // The entry form with the phone's list of goods (Marhala 4, part 6): an edited sale of two items, its
            // amount at a discount said in words, as the laptop's dialog holds it (600 x 503 dp).
            val aslam = partiesNow.first { it.name == "Muhammad Aslam" }
            val sale = kotlinx.coroutines.runBlocking {
                val goods = listOf(
                    com.innovation313.roshankhata.data.EntryItem(entryId = 0, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 3700.0),
                    com.innovation313.roshankhata.data.EntryItem(entryId = 0, itemName = "Confidor", quantity = 3.0, unit = "bottle", rate = 875.0, crop = "Cotton", pest = "Whitefly", dose = "250 ml per acre")
                )
                val id = dao.insertEntryWithItems(com.innovation313.roshankhata.data.LedgerEntry(partyId = aslam.id, amount = 9900.0, isGiven = true, entryNumber = "", timestamp = now - 30 * 60 * 1000, rateType = com.innovation313.roshankhata.data.RateType.CREDIT), goods)
                dao.getEntry(id)!! to dao.itemsOfEntry(id).sortedBy { it.lineNo }
            }
            val entryForm = ImageComposeScene(width = 750, height = 629, density = Density(1.25f)) {
                DeskTheme(dark = false) {
                    ProvideWords(words) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                            EntryEditor(
                                partyId = aslam.id, partyName = aslam.name, isGiven = true,
                                existing = sale.first, existingItems = sale.second,
                                onSave = {}, onCancel = {}, isCustomer = true, dao = dao
                            )
                        }
                    }
                }
            }
            try {
                entryForm.render(0); entryForm.render(16_000_000)
                repeat(3) { n -> Thread.sleep(400); entryForm.render(250_000_000L + n * 100_000_000L) }
                val png = entryForm.render(1_500_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                val f = File(dir, "dialog-laptop-entry-en.png").also { it.writeBytes(png) }
                assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 20_000)
            } finally { entryForm.close() }
            // The same sale as its receipt (Marhala 4, part 6b, the phone's entry screen): goods priced, the discount
            // said, the advice under its item, the balance it left in the customer's words, an agri shop's season.
            val receiptCard = ImageComposeScene(width = 700, height = 900, density = Density(1.25f)) {
                DeskTheme(dark = false) {
                    ProvideWords(words) {
                        EntryReceiptCard(
                            dao, sale.first.id, aslam.name, home, 1L, reload = 0,
                            onClose = {}, onEdit = {}, onDelete = {}, onPrint = { _, _ -> }, onInvoice = { _, _ -> }, onChanged = {}
                        )
                    }
                }
            }
            try {
                receiptCard.render(0); receiptCard.render(16_000_000)
                repeat(3) { n -> Thread.sleep(400); receiptCard.render(250_000_000L + n * 100_000_000L) }
                val png = receiptCard.render(1_500_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                val f = File(dir, "dialog-laptop-receipt-en.png").also { it.writeBytes(png) }
                assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 20_000)
            } finally { receiptCard.close() }
            // Marhala 4, part 10: Help in the page frame (the shortcuts with Ctrl+F, Report a problem as one window, the
            // support address, the version); Schemes, Seasons and Duplicates on the frame with their cards. Drawn last,
            // so the proofs above keep their book.
            val partTen = mutableListOf<File>()
            partTen += shoot("screen-laptop-help-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.HELP) {
                HelpPane(words) {}
            }
            kotlinx.coroutines.runBlocking {
                dao.saveScheme(com.innovation313.roshankhata.data.Scheme(
                    name = "Rabi DAP target", startDate = now - 30 * day, endDate = now + 60 * day,
                    measure = com.innovation313.roshankhata.data.SchemeMath.BY_QTY, unit = "bag",
                    rewardKind = com.innovation313.roshankhata.data.SchemeMath.AMOUNT, target1 = 100.0, reward1 = 5000.0, target2 = 200.0, reward2 = 12000.0
                ))
            }
            partTen += shoot("screen-laptop-schemes-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.SCHEMES, waits = 2) {
                SchemesPane(dao, 0)
            }
            partTen += shoot("screen-laptop-seasons-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.SEASONS, waits = 2) {
                SeasonsPane(dao, partiesNow, 0)
            }
            // The same customer written twice (one name, one number): the duplicates page's group.
            val twice = kotlinx.coroutines.runBlocking {
                dao.insertParty(com.innovation313.roshankhata.data.Party(name = "Muhammad Aslam", phone = "03000000001", isCustomer = true))
                dao.partiesWithBalanceOnce()
            }
            partTen += shoot("screen-laptop-duplicates-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f, section = Section.DUPLICATES, waits = 2) {
                DuplicatesPane(dao, twice) {}
            }
            for (f in partTen) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 20_000)
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
                DeskTheme(dark = false) { ProvideWords(words) { DialogFrame(name, onClose = {}, scroll = scroll, footer = {}) { body() } } }
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
            // Today's sales spread over the hours gone by so far (the runner's clock may be early in its day).
            val start = com.innovation313.roshankhata.data.DayCloseMath.startOf(com.innovation313.roshankhata.data.DayCloseMath.dayOf(now))
            val span = (now - start).coerceAtLeast(60_000L)
            listOf(0.08 to 1_650.0, 0.2 to 450.0, 0.35 to 2_900.0, 0.5 to 780.0, 0.7 to 5_800.0, 0.9 to 120.0, 0.9 to 2_400.0).forEach { (at, amount) ->
                dao.postSale(
                    com.innovation313.roshankhata.data.Sale(timestamp = start + (span * at).toLong(), subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
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
                DeskTheme(dark = false) { ProvideWords(words) { CustomerScreen(draft, "Al-Noor Agri Store", qr) } }
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

    /**
     * A dialog window keeps the app's direction and text size (R13, 23 Oct
     * 2026): a window of its own gets both from Swing, which said left to right
     * for Sindhi and right to left for Roman Urdu. The scene here plays that
     * wrong window (the opposite direction, scale 1); AppDialog's body
     * (KeepAppLook) must still read the app's own, and is drawn as a proof.
     */
    @Test
    fun `a dialog body keeps the app direction and text size in Sindhi and Roman Urdu`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val before = Locale.getDefault()
        try {
            for (tag in listOf("sd", "ur-Latn")) {
                val locale = Locale.forLanguageTag(tag).also { Locale.setDefault(it) }
                val rtl = com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(locale)
                val app = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                val swings = if (rtl) LayoutDirection.Ltr else LayoutDirection.Rtl
                val words = desktopWords(locale)
                var seenDir: LayoutDirection? = null
                var seenScale = 0f
                val scene = ImageComposeScene(width = 620, height = 420, density = Density(1f)) {
                    CompositionLocalProvider(LocalLayoutDirection provides swings) {
                        DeskTheme(dark = false) {
                            ProvideWords(words) {
                                KeepAppLook(app, 1.3f) {
                                    seenDir = LocalLayoutDirection.current
                                    seenScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                                    DialogFrame(words.text(com.innovation313.roshankhata.words.W.language), onClose = {}, footer = {}) {
                                        androidx.compose.material3.Text(words.text(com.innovation313.roshankhata.words.W.businesses_title))
                                    }
                                }
                            }
                        }
                    }
                }
                try {
                    scene.render(0); scene.render(16_000_000)
                    val png = scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                    File(dir, "dialog-direction-$tag.png").writeBytes(png)
                } finally { scene.close() }
                org.junit.Assert.assertEquals("$tag: the dialog's direction", app, seenDir)
                org.junit.Assert.assertEquals("$tag: the dialog's text size", 1.3f, seenScale, 0.0001f)
            }
            org.junit.Assert.assertTrue("Sindhi is right to left", com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(Locale.forLanguageTag("sd")))
            org.junit.Assert.assertFalse("Roman Urdu is left to right", com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(Locale.forLanguageTag("ur-Latn")))
        } finally {
            Locale.setDefault(before)
        }
    }
}
