package com.innovation313.roshankhata

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.innovation313.roshankhata.data.ThemeMode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.Cheque
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.data.TextSize
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every screen in the app opens, survives a rotation, and survives the owner
 * switching to WhatsApp and back — on a real Android, with real data in the
 * ledger.
 *
 * DeviceSmokeTest covers the first-run path (splash, language). This covers
 * everything after it: one test per screen, so a crash names the screen it
 * happened on instead of "the app crashed somewhere".
 *
 * The ledger is seeded once with a small shop's worth of data — a customer
 * with given/got entries and goods, a supplier with a bill whose item expires
 * soon, a pending cheque, a cash-book line, an invoice. An empty ledger only
 * ever shows empty states; most crashes live in the code that draws real rows.
 *
 * What this asserts is deliberately narrow: no crash. A screen that finishes
 * itself (for example the crop screen opened without a picture) is a pass —
 * that is its designed behaviour, not a failure. It does not check what is on
 * screen; the owner's own phone testing covers that, and a test that fails
 * whenever a label moves is a test people learn to ignore.
 *
 * Not here, on purpose:
 *  - GateActivity, LanguageActivity: DeviceSmokeTest.
 *  - LockActivity: only reachable with App Lock on and a fingerprint enrolled;
 *    on an emulator with neither, its biometric prompt errors and it closes
 *    the whole task (finishAffinity) by design, which would end the run.
 */
@RunWith(Parameterized::class)
class EveryScreenOpensTest(
    private val screen: String,
    private val intentFor: (Context, Seed) -> Intent,
    private val textSize: Int,
    private val dark: Boolean
) {

    /** Row ids the screens that open one record need. */
    data class Seed(
        val customerId: Long,
        val supplierId: Long,
        val entryId: Long,
        val invoiceId: Long,
        val productId: Long
    )

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun readyToUse() {
        // Past the first-run language picker, as every returning owner is.
        // Same prefs file and key as LanguageActivity (private there).
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()

        // A runtime-permission dialog is another app's window: the screen
        // underneath never reaches RESUMED and the launch would time out,
        // which reads as a hang in our code when it is not.
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pkg = context.packageName
        ui.grantRuntimePermission(pkg, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) {
            ui.grantRuntimePermission(pkg, Manifest.permission.POST_NOTIFICATIONS)
        }

        TextSize.setLevel(context, textSize)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(context, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @After
    fun normalSize() {
        TextSize.setLevel(context, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(context, ThemeMode.LIGHT)
        }
    }

    @Test
    fun opensRotatesAndComesBack() {
        val seed = seed(context)
        ActivityScenario.launch<Activity>(intentFor(context, seed)).use { scenario ->
            // A crash anywhere below takes the app process down and fails
            // this test with the stack trace; the asserts only guard against
            // a screen stuck in a state it should never be left in.
            if (scenario.state == Lifecycle.State.DESTROYED) return

            // In dark mode the screen must actually BE dark: night resources
            // resolved, and the page colour it draws on a dark one.
            if (dark) scenario.onActivity { activity ->
                val night = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                assertEquals("$screen: not in night mode", Configuration.UI_MODE_NIGHT_YES, night)
                val page = ContextCompat.getColor(activity, R.color.page_bg)
                assertTrue("$screen: page colour is not dark", ColorUtils.calculateLuminance(page) < 0.1)

                // Every visible piece of text must stand out from what is
                // actually behind it. The owner found text that vanished
                // into its own background in dark mode (header subtitles,
                // the Add Party / Add Entry pills); this measures it on
                // every screen instead of waiting to be spotted.
                val faint = mutableListOf<String>()
                checkContrast(activity.findViewById(android.R.id.content), page, faint)
                assertTrue("$screen: text too faint in dark mode: ${faint.take(8).joinToString("; ")}", faint.isEmpty())
            }

            scenario.recreate()                              // rotation / dark mode / font size
            if (scenario.state == Lifecycle.State.DESTROYED) return

            scenario.moveToState(Lifecycle.State.CREATED)    // owner switches app
            scenario.moveToState(Lifecycle.State.RESUMED)    // and comes back
            assertTrue(
                "$screen is ${scenario.state} after coming back to it",
                scenario.state == Lifecycle.State.RESUMED ||
                    scenario.state == Lifecycle.State.DESTROYED
            )
        }
    }


    /**
     * Walks the screen and records every visible, enabled text whose colour
     * is too close to the colour really drawn behind it (contrast below 3:1,
     * the level at which text stops being comfortably readable).
     */
    private fun checkContrast(v: View?, page: Int, out: MutableList<String>) {
        if (v == null || !v.isShown || v.alpha < 0.5f) return
        if (v is TextView && v.isEnabled && v.text.isNotBlank() && v !is EditText) {
            val bg = backgroundBehind(v, page)
            val fg = ColorUtils.compositeColors(v.currentTextColor, bg)
            val ratio = ColorUtils.calculateContrast(fg, bg)
            if (ratio < 3.0) {
                val id = if (v.id != View.NO_ID) v.resources.getResourceEntryName(v.id) else "?"
                out += "'${v.text.toString().take(24)}' ($id) %.1f:1 on #%06X".format(ratio, bg and 0xFFFFFF)
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) checkContrast(v.getChildAt(i), page, out)
    }

    /** The first solid colour found behind [v], walking up its parents. */
    private fun backgroundBehind(v: View, page: Int): Int {
        var p: View? = v
        while (p != null) {
            solidColourOf(p)?.let { c ->
                if (Color.alpha(c) >= 200) return ColorUtils.compositeColors(c, page)
            }
            p = p.parent as? View
        }
        return page
    }

    private fun solidColourOf(v: View): Int? {
        val state = v.drawableState
        if (v is MaterialCardView) return v.cardBackgroundColor.getColorForState(state, v.cardBackgroundColor.defaultColor)
        if (v is MaterialButton) v.backgroundTintList?.let { return it.getColorForState(state, it.defaultColor) }
        val bg = v.background ?: return null
        v.backgroundTintList?.let { return it.getColorForState(state, it.defaultColor) }
        return drawableColour(bg, state)
    }

    private fun drawableColour(d: Drawable, state: IntArray): Int? = when (d) {
        is ColorDrawable -> d.color
        // A gradient header: its average colour is what text sits on.
        is GradientDrawable -> d.color?.let { it.getColorForState(state, it.defaultColor) }
            ?: d.colors?.takeIf { it.isNotEmpty() }?.let { average(it) }
        is MaterialShapeDrawable -> d.fillColor?.let { it.getColorForState(state, it.defaultColor) }
        // A ripple (every tappable row) carries a white MASK layer that is
        // never drawn: it only bounds the ripple. Reading it as the colour
        // behind the text flagged every list row as "on #FFFFFF". Skip the
        // mask; a ripple with no other layer is transparent, so the search
        // carries on up to the parent.
        is RippleDrawable -> (d.numberOfLayers - 1 downTo 0)
            .filter { d.getId(it) != android.R.id.mask }
            .filter { d.getLayerHeight(it) <= 0 && d.getLayerWidth(it) <= 0 }
            .firstNotNullOfOrNull { drawableColour(d.getDrawable(it), state) }
        // Top-most layer that covers the whole area. A layer with its own
        // height or width is decoration (the 2dp gold rule under the Home
        // header), not the colour behind the text.
        is LayerDrawable -> (d.numberOfLayers - 1 downTo 0)
            .filter { d.getLayerHeight(it) <= 0 && d.getLayerWidth(it) <= 0 }
            .firstNotNullOfOrNull { drawableColour(d.getDrawable(it), state) }
        is InsetDrawable -> d.drawable?.let { drawableColour(it, state) }
        else -> null
    }

    private fun average(colours: IntArray): Int = Color.rgb(
        colours.sumOf { Color.red(it) } / colours.size,
        colours.sumOf { Color.green(it) } / colours.size,
        colours.sumOf { Color.blue(it) } / colours.size
    )

    companion object {

        @Volatile
        private var seeded: Seed? = null

        /** Once per run: every screen reads the same shop. */
        @Synchronized
        fun seed(context: Context): Seed {
            seeded?.let { return it }
            val dao = KhataDatabase.get(context).khataDao()
            val day = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()

            val result = runBlocking {
                val customer = dao.insertParty(
                    Party(name = "Test Kisan", phone = "03000000000", village = "Test Village")
                )
                val supplier = dao.insertParty(Party(name = "Test Supplier", isCustomer = false))

                val entry = dao.insertEntry(
                    LedgerEntry(
                        partyId = customer, amount = 5000.0, isGiven = true,
                        entryNumber = "RK-900001", timestamp = now - 3 * day,
                        itemName = "Test Urea", quantity = 2.0, unit = "bag"
                    )
                )
                dao.insertEntry(
                    LedgerEntry(
                        partyId = customer, amount = 1500.0, isGiven = false,
                        entryNumber = "RK-900002", timestamp = now - day
                    )
                )

                dao.insertCheque(
                    Cheque(
                        partyId = customer, amount = 2000.0, isReceived = true,
                        chequeNumber = "000123", dueDate = now + day
                    )
                )

                dao.insertCashEntry(
                    CashEntry(amount = 300.0, isIncome = false, category = "Test kharcha")
                )

                // Goods in from the supplier, one item close to expiry — so
                // the bills, products, stock and expiring screens all have a row.
                dao.insertSupplierBill(
                    entry = null,
                    bill = SupplierBill(partyId = supplier, billNumber = "B-1", totalAmount = 4000.0),
                    items = listOf(
                        BillItem(
                            billId = 0, productName = "Test Spray", quantity = 4.0,
                            unit = "bottle", rate = 1000.0, batchNumber = "T1",
                            expiryDate = now + 10 * day
                        )
                    )
                )
                val product = dao.findOrCreateProduct(name = "Test Spray", defaultUnit = "bottle")

                val invoice = dao.saveInvoiceWithItems(
                    Invoice(customerName = "Test Kisan", customerPhone = "03000000000"),
                    listOf(InvoiceItem(invoiceId = 0, itemName = "Test Spray", quantity = 1.0, rate = 1200.0))
                )

                Seed(customer, supplier, entry, invoice, product.id)
            }
            seeded = result
            return result
        }

        private inline fun <reified T : Activity> open(
            name: String,
            noinline extras: Intent.(Seed) -> Unit = {}
        ): Array<Any> = arrayOf(
            name,
            { c: Context, s: Seed -> Intent(c, T::class.java).apply { extras(s) } }
        )

        /**
         * Every screen twice: at normal size, and at the largest text size
         * this app offers — where fixed-height buttons are relaxed at runtime
         * (TextFit) and the most layout code runs that normal size never does.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = baseScreens().flatMap { row ->
            listOf(
                arrayOf(row[0], row[1], TextSize.NORMAL, false),
                arrayOf("${row[0]} @ largest text", row[1], TextSize.LARGEST, false),
                arrayOf("${row[0]} @ dark", row[1], TextSize.NORMAL, true)
            )
        }

        private fun baseScreens(): List<Array<Any>> = listOf(
            open<WelcomeActivity>("Welcome"),
            open<MainActivity>("Home") { putExtra(MainActivity.EXTRA_UNLOCKED, true) },
            open<KhataActivity>("Khata list"),
            open<PartyDetailActivity>("Customer khata") {
                putExtra(PartyDetailActivity.EXTRA_PARTY_ID, it.customerId)
            },
            open<PartyDetailActivity>("Supplier khata") {
                putExtra(PartyDetailActivity.EXTRA_PARTY_ID, it.supplierId)
            },
            open<EntryDetailActivity>("Entry receipt") {
                putExtra(EntryDetailActivity.EXTRA_ENTRY_ID, it.entryId)
                putExtra(EntryDetailActivity.EXTRA_PARTY_NAME, "Test Kisan")
            },
            open<ReportActivity>("Customer statement") {
                putExtra(ReportActivity.EXTRA_PARTY_ID, it.customerId)
            },
            open<LedgerReportActivity>("Ledger report"),
            open<InspectorReportActivity>("Inspector report"),
            open<RegisterReportActivity>("Register report"),
            open<InsightsActivity>("Insights"),
            open<CashbookActivity>("Cashbook"),
            open<ChequesActivity>("Cheques"),
            open<PlansActivity>("Payment plans"),
            open<BillsActivity>("Supplier bills"),
            open<ProductsActivity>("Products"),
            open<ProductCustomersActivity>("Product customers") {
                putExtra(ProductCustomersActivity.EXTRA_PRODUCT_ID, it.productId)
                putExtra(ProductCustomersActivity.EXTRA_PRODUCT_NAME, "Test Spray")
            },
            open<ExpiringActivity>("Expiring stock"),
            open<InvoicesActivity>("Invoices"),
            open<InvoiceEditorActivity>("New invoice"),
            open<InvoiceEditorActivity>("Edit invoice") {
                putExtra(InvoiceEditorActivity.EXTRA_INVOICE_ID, it.invoiceId)
            },
            open<InvoiceSettingsActivity>("Invoice settings"),
            open<FollowUpActivity>("Follow-up"),
            open<ZakatActivity>("Zakat"),
            open<CalculatorActivity>("Calculator"),
            open<RecycleBinActivity>("Recycle bin"),
            open<DuplicateCustomersActivity>("Duplicate customers"),
            open<ImportContactsActivity>("Import contacts"),
            open<BusinessSettingsActivity>("Business settings"),
            open<BusinessSwitchActivity>("Business switcher"),
            open<BusinessCardActivity>("Business card"),
            open<SnapshotsActivity>("Snapshots"),
            open<BackupActivity>("Backup"),
            open<ImageCropActivity>("Image crop (no picture)"),
            open<AboutActivity>("About"),
            open<HelpActivity>("Help"),
            open<ReportProblemActivity>("Report a problem")
        )
    }
}
