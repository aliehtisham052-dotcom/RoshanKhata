package com.innovation313.roshankhata

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.ThemeMode
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.ui.CoachMarkController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Pictures of the phone's screens to LOOK at (31 Oct 2026, the owner: "aap mujhse behtar screen dekh rahe ho" —
 * he has no time to open every screen again and again). EveryScreenOpensTest proves nothing crashes; it cannot see a
 * figure cut to "Rs", a word broken a letter a line, or text gone dark on dark. These are taken on the owner's own
 * width (screen-proof.sh sets the emulator to 360 dp) in four looks: English at 150% text by day for a kiryana, Urdu
 * at 150% by night for an agri shop, Bengali at normal size by night for a clothes shop, Indonesian at 85% by day
 * for a pharmacy. They are saved to Download/screen-proof/<look>/ and the screen-proof workflow puts them on the
 * `screen-proof` pre-release. The book is the made-up shop EveryScreenOpensTest seeds, plus one large account so the
 * totals show seven digits. No real name or number.
 */
@RunWith(Parameterized::class)
class ScreenProofPhoneTest(
    private val look: String,
    private val language: String,
    private val level: Int,
    private val phoneScale: Float,
    private val dark: Boolean,
    private val trade: Trade
) {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ready() {
        app.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putBoolean("chosen", true).commit()
        app.getSharedPreferences("welcome", Context.MODE_PRIVATE).edit().putBoolean("welcome_seen", true).commit()
        app.getSharedPreferences("home_offers", Context.MODE_PRIVATE).edit().putBoolean("backup_offered", true).commit()
        CoachMarkController.markRun(app)
        BalancePrivacy.setHidden(app, false)
        BusinessProfile.setTrade(app, trade)
        BusinessProfile.setBusinessName(app, "Al-Noor Traders")
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(app.packageName, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) ui.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        // The phone's own text size, under the app's (TextSize multiplies the two, 85%-150%).
        ui.executeShellCommand("settings put system font_scale $phoneScale").close()
        SystemClock.sleep(500)
        TextSize.setLevel(app, level)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(app, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            AppCompatDelegate.setApplicationLocales(
                if (language == "en") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language)
            )
        }
    }

    @After
    fun back() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        TextSize.setLevel(app, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(app, ThemeMode.LIGHT)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun theScreens() {
        val seed = EveryScreenOpensTest.seed(app)
        val big = bigAccount()
        fun open(cls: Class<out Activity>, extras: Intent.() -> Unit = {}) = Intent(app, cls).apply(extras)

        shoot("01-home", open(MainActivity::class.java) { putExtra(MainActivity.EXTRA_UNLOCKED, true) })
        shoot("02-khata", open(KhataActivity::class.java))
        shoot("03-customer", open(PartyDetailActivity::class.java) { putExtra(PartyDetailActivity.EXTRA_PARTY_ID, big) })
        shoot("04-add-entry", open(PartyDetailActivity::class.java) {
            putExtra(PartyDetailActivity.EXTRA_PARTY_ID, big)
            putExtra(PartyDetailActivity.EXTRA_VOICE_AMOUNT, 1234567.0)
            putExtra(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, true)
        }, settleMs = 2500)
        shoot("05-receipt", open(EntryDetailActivity::class.java) {
            putExtra(EntryDetailActivity.EXTRA_ENTRY_ID, seed.entryId)
            putExtra(EntryDetailActivity.EXTRA_PARTY_NAME, "Test Kisan")
        })
        shoot("06-ledger-report", open(LedgerReportActivity::class.java))
        shoot("07-stock-register", open(InspectorReportActivity::class.java))
        shoot("08-sales-register", open(RegisterReportActivity::class.java))
        shoot("09-insights", open(InsightsActivity::class.java), settleMs = 2500)
        shoot("10-cash-flow", open(CashFlowActivity::class.java))
        shoot("11-cashbook", open(CashbookActivity::class.java))
        shoot("12-cheques", open(ChequesActivity::class.java))
        shoot("13-plans", open(PlansActivity::class.java))
        shoot("14-bills", open(BillsActivity::class.java))
        shoot("15-products", open(ProductsActivity::class.java))
        shoot("16-rate-list", open(RateListActivity::class.java))
        shoot("17-counter", open(CounterSaleActivity::class.java), settleMs = 2500)
        shoot("18-orders", open(OrdersActivity::class.java))
        shoot("19-sales-report", open(SalesReportActivity::class.java), settleMs = 2500)
        shoot("20-expiring", open(ExpiringActivity::class.java))
        shoot("21-invoices", open(InvoicesActivity::class.java))
        shoot("22-invoice-editor", open(InvoiceEditorActivity::class.java) { putExtra(InvoiceEditorActivity.EXTRA_INVOICE_ID, seed.invoiceId) })
        shoot("23-follow-up", open(FollowUpActivity::class.java))
        shoot("24-business-profile", open(BusinessSettingsActivity::class.java))
        shoot("25-backup", open(BackupActivity::class.java))
        shoot("26-staff", open(StaffActivity::class.java))
        shoot("27-zakat", open(ZakatActivity::class.java))
        shoot("28-calculator", open(CalculatorActivity::class.java))
    }

    /** A customer with seven-figure dealings, so every total on the way is wide (made-up name). */
    private fun bigAccount(): Long = runBlocking {
        val dao = KhataDatabase.get(app).khataDao()
        dao.partiesWithBalanceOnce().firstOrNull { it.name == BIG_NAME }?.let { return@runBlocking it.id }
        val id = dao.insertParty(Party(name = BIG_NAME, phone = "03000000001", village = "Test Village"))
        val now = System.currentTimeMillis()
        dao.insertEntry(LedgerEntry(partyId = id, amount = 2_345_678.0, isGiven = true, entryNumber = "RK-900101", timestamp = now - 5 * DAY, note = "Test sale"))
        dao.insertEntry(LedgerEntry(partyId = id, amount = 1_111_111.0, isGiven = false, entryNumber = "RK-900102", timestamp = now - DAY))
        id
    }

    private fun shoot(name: String, intent: Intent, settleMs: Long = 1500) {
        try {
            ActivityScenario.launch<Activity>(intent).use {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                SystemClock.sleep(settleMs)
                val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                assertTrue("no picture for $name", bmp != null && bmp.width > 0)
                save("$name.png", bmp)
            }
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
            // One screen that cannot open must not cost the others' pictures; its name says it is missing.
            android.util.Log.e("ScreenProof", "$look/$name failed", e)
        }
    }

    private fun save(name: String, bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/screen-proof/$look")
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val BIG_NAME = "Test Wholesale Customer"
        private const val DAY = 24L * 60 * 60 * 1000

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(
            // 1.15 x LARGEST (1.30) = the app's 150% ceiling.
            arrayOf("en-150-day-kiryana", "en", TextSize.LARGEST, 1.15f, false, Trade.KIRYANA),
            arrayOf("ur-150-night-agri", "ur", TextSize.LARGEST, 1.15f, true, Trade.AGRI),
            arrayOf("bn-100-night-clothing", "bn", TextSize.NORMAL, 1.0f, true, Trade.CLOTHING),
            arrayOf("in-85-day-medical", "id", TextSize.SMALL, 1.0f, false, Trade.MEDICAL)
        )
    }
}
