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
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.KhataDatabase
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
 * The Play Store screenshots (10 Oct): the real screens, on a seeded demo
 * book, saved to Download/store-shots/<language>/ for CI to pull and frame
 * (scripts/frame_screenshots.py). Not part of the regular device run: the
 * screenshots workflow runs this class alone, by hand.
 *
 * The book is DEMO DATA ONLY - invented names, invented figures - because
 * the listing is public and no real customer may appear in it (CLAUDE.md).
 * Every seeded name carries [MARK], and the book is wiped after.
 */
@RunWith(Parameterized::class)
class StoreScreenshotsTest(private val language: String) {

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val dao get() = KhataDatabase.get(app).khataDao()

    @Before
    fun seed() {
        app.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putBoolean("chosen", true).commit()
        app.getSharedPreferences("welcome", Context.MODE_PRIVATE).edit().putBoolean("welcome_seen", true).commit()
        CoachMarkController.markRun(app)
        BalancePrivacy.setHidden(app, false)
        BusinessProfile.setTrade(app, Trade.AGRI)
        BusinessProfile.setBusinessName(app, if (language == "ur") "بلال زرعی سروس" else "Bilal Zarai Service")

        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(app.packageName, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) ui.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)

        TextSize.setLevel(app, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(app, ThemeMode.LIGHT)
            AppCompatDelegate.setApplicationLocales(
                if (language == "en") LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language)
            )
        }
        runBlocking { seedBook() }
    }

    @After
    fun clear() {
        runBlocking {
            dao.purgeSeededEntries("%$MARK")
            dao.purgeSeededParties("%$MARK")
            dao.wipeCash()
        }
    }

    private suspend fun seedBook() {
        dao.purgeSeededEntries("%$MARK"); dao.purgeSeededParties("%$MARK"); dao.wipeCash()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        // (name, phone, lines: amount, given?, days ago, note)
        val ur = language == "ur"
        val book = listOf(
            Triple(if (ur) "محمد اکرم" else "Muhammad Akram", "0300 1234567",
                listOf(Line(18500.0, true, 12, if (ur) "یوریا 5 بوری" else "Urea 5 bori"), Line(8000.0, false, 6, null), Line(6200.0, true, 2, if (ur) "سپرے" else "Spray"))),
            Triple(if (ur) "رانا شفیق" else "Rana Shafiq", "0321 7654321",
                listOf(Line(42000.0, true, 30, if (ur) "گندم بیج 10 بوری" else "Wheat seed 10 bori"), Line(20000.0, false, 14, null))),
            Triple(if (ur) "چوہدری نذیر" else "Chaudhry Nazeer", "0345 1122334",
                listOf(Line(9700.0, true, 9, if (ur) "ڈی اے پی 2 بوری" else "DAP 2 bori"), Line(9700.0, false, 1, null))),
            Triple(if (ur) "ملک عمران" else "Malik Imran", "0333 9988776",
                listOf(Line(15000.0, true, 40, null), Line(5000.0, false, 20, null), Line(3300.0, true, 4, if (ur) "فیڈ 3 بوری" else "Feed 3 bori"))),
            Triple(if (ur) "حاجی اسلم" else "Haji Aslam", "0301 5566778",
                listOf(Line(7500.0, false, 3, if (ur) "ایڈوانس" else "Advance"))),
            Triple(if (ur) "عثمان گجر" else "Usman Gujjar", "0312 4433221",
                listOf(Line(26400.0, true, 18, if (ur) "زنک + پوٹاش" else "Zinc + Potash"), Line(10000.0, false, 7, null)))
        )
        var n = 0
        for ((name, phone, lines) in book) {
            val id = dao.insertParty(Party(name = "$name$MARK", phone = phone, isCustomer = true, createdAt = now - 120 * day))
            for (l in lines) {
                n++
                dao.insertEntry(LedgerEntry(partyId = id, amount = l.amount, isGiven = l.given, note = l.note,
                    entryNumber = "RK-%06d".format(n), timestamp = now - l.daysAgo * day - 3 * 60 * 60 * 1000))
            }
        }
        val cash = listOf(
            Triple(12500.0, true, if (ur) "نقد بکری" else "Cash sales"), Triple(3200.0, false, if (ur) "ڈیزل" else "Diesel"),
            Triple(8400.0, true, if (ur) "نقد بکری" else "Cash sales"), Triple(1500.0, false, if (ur) "چائے پانی" else "Tea"),
            Triple(2000.0, false, if (ur) "مزدوری" else "Labour")
        )
        for ((i, c) in cash.withIndex()) {
            dao.insertCashEntry(CashEntry(amount = c.first, isIncome = c.second, category = c.third, timestamp = now - i * 5 * 60 * 60 * 1000))
        }
    }

    private class Line(val amount: Double, val given: Boolean, val daysAgo: Int, val note: String?)

    private fun shoot(name: String, intent: Intent, settleMs: Long = 1500) {
        ActivityScenario.launch<Activity>(intent).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            SystemClock.sleep(settleMs)
            val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertTrue("no screenshot for $name", bmp != null && bmp.width > 0)
            save("$name.png", bmp)
        }
    }

    @Test
    fun theStoreScreens() {
        val unlocked = Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_UNLOCKED, true)
        shoot("01-home", unlocked)
        shoot("02-khata", Intent(app, KhataActivity::class.java))
        val first = runBlocking { dao.partiesWithBalanceOnce().maxByOrNull { it.balance }?.id } ?: return
        shoot("03-customer", Intent(app, PartyDetailActivity::class.java).putExtra(PartyDetailActivity.EXTRA_PARTY_ID, first))
        shoot("04-cashbook", Intent(app, CashbookActivity::class.java))
        shoot("05-insights", Intent(app, InsightsActivity::class.java), settleMs = 2500)
        shoot("06-backup", Intent(app, BackupActivity::class.java))
    }

    /** Download/store-shots/<lang>/<name>: survives the test app's uninstall, for CI to pull. */
    private fun save(name: String, bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/store-shots/$language")
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        /** A thin space and a tag no owner types; purged by it. */
        private const val MARK = " (demo)"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages() = listOf(arrayOf<Any>("en"), arrayOf<Any>("ur"))
    }
}
