package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

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
import com.innovation313.roshankhata.data.set
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
        // Home's one-time backup offer (MainActivity.maybeOfferBackup) sat
        // over the first English screenshot; the listing shows the screen.
        app.getSharedPreferences("home_offers", Context.MODE_PRIVATE).edit().putBoolean("backup_offered", true).commit()
        BalancePrivacy.setHidden(app, false)
        BusinessProfile.setTrade(app, Trade.AGRI)
        BusinessProfile.setBusinessName(app, demo.shop)
        // The shop's country for the listing's market (21 Oct 2026): the currency sign and the
        // WhatsApp code a buyer there expects on the screenshots.
        BusinessProfile.setCountry(app, demo.country)
        com.innovation313.roshankhata.data.Currency.set(app, demo.country.currencySign)

        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(app.packageName, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) ui.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)

        TextSize.setLevel(app, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(app, ThemeMode.LIGHT)
            AppCompatDelegate.setApplicationLocales(
                if (language == "en") LocaleListCompat.getEmptyLocaleList()
                // Indonesian is asked for as BCP-47 "id" (the picker's tag); its strings live in values-in.
                else LocaleListCompat.forLanguageTags(if (language == "in") "id" else language)
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
        // (name, phone, lines: amount, given?, days ago, note), in the listing's language.
        val n0 = demo.names
        val g = demo.goods
        val book = listOf(
            Triple(n0[0], demo.phones[0], listOf(Line(18500.0, true, 12, g[0]), Line(8000.0, false, 6, null), Line(6200.0, true, 0, g[1]))),
            Triple(n0[1], demo.phones[1], listOf(Line(42000.0, true, 30, g[2]), Line(20000.0, false, 14, null))),
            Triple(n0[2], demo.phones[2], listOf(Line(9700.0, true, 9, g[3]), Line(9700.0, false, 0, null))),
            Triple(n0[3], demo.phones[3], listOf(Line(15000.0, true, 40, null), Line(5000.0, false, 20, null), Line(3300.0, true, 4, g[4]))),
            Triple(n0[4], demo.phones[4], listOf(Line(7500.0, false, 0, g[5]))),
            Triple(n0[5], demo.phones[5], listOf(Line(26400.0, true, 18, g[6]), Line(10000.0, false, 7, null)))
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
        val c = demo.cash
        val cash = listOf(
            Triple(12500.0, true, c[0]), Triple(3200.0, false, c[1]),
            Triple(8400.0, true, c[0]), Triple(1500.0, false, c[2]),
            Triple(2000.0, false, c[3])
        )
        for ((i, c) in cash.withIndex()) {
            dao.insertCashEntry(CashEntry(amount = c.first, isIncome = c.second, category = c.third, timestamp = now - i * 5 * 60 * 60 * 1000))
        }
    }

    private class Line(val amount: Double, val given: Boolean, val daysAgo: Int, val note: String?)

    /** The invented shop of each listing's language: no real customer, every name made up (CLAUDE.md). */
    private class Demo(
        val shop: String, val country: com.innovation313.roshankhata.data.ShopCountry,
        val names: List<String>, val phones: List<String>, val goods: List<String>, val cash: List<String>
    )

    private val demo: Demo get() = DEMOS.getValue(language)

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
        /** A zero-width space no owner types: invisible on screen, purged by it. */
        private const val MARK = "\u200B"

        /** Every language Play lists the app in (store/listing): the screenshots for each market (21 Oct 2026). */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages() = listOf("en", "ur", "ar", "fa", "hi", "bn", "in").map { arrayOf<Any>(it) }

        private val PK_PHONES = listOf("0300 1234567", "0321 7654321", "0345 1122334", "0333 9988776", "0301 5566778", "0312 4433221")
        private val DEMOS: Map<String, Demo> = mapOf(
            "en" to Demo("Bilal Zarai Service", com.innovation313.roshankhata.data.ShopCountry.PAKISTAN,
                listOf("Muhammad Akram", "Rana Shafiq", "Chaudhry Nazeer", "Malik Imran", "Haji Aslam", "Usman Gujjar"), PK_PHONES,
                listOf("Urea 5 bori", "Spray", "Wheat seed 10 bori", "DAP 2 bori", "Feed 3 bori", "Advance", "Zinc + Potash"),
                listOf("Cash sales", "Diesel", "Tea", "Labour")),
            "ur" to Demo("بلال زرعی سروس", com.innovation313.roshankhata.data.ShopCountry.PAKISTAN,
                listOf("محمد اکرم", "رانا شفیق", "چوہدری نذیر", "ملک عمران", "حاجی اسلم", "عثمان گجر"), PK_PHONES,
                listOf("یوریا 5 بوری", "سپرے", "گندم بیج 10 بوری", "ڈی اے پی 2 بوری", "فیڈ 3 بوری", "ایڈوانس", "زنک + پوٹاش"),
                listOf("نقد بکری", "ڈیزل", "چائے پانی", "مزدوری")),
            "ar" to Demo("متجر النور الزراعي", com.innovation313.roshankhata.data.ShopCountry.SAUDI_ARABIA,
                listOf("محمد العمري", "خالد الحربي", "سعد القحطاني", "فهد الدوسري", "عبدالله الشهري", "ناصر الغامدي"),
                listOf("050 123 4567", "055 765 4321", "053 112 2334", "054 998 8776", "056 556 6778", "058 443 3221"),
                listOf("يوريا 5 أكياس", "رش", "بذور قمح 10 أكياس", "داب 2 كيس", "علف 3 أكياس", "دفعة مقدمة", "زنك + بوتاس"),
                listOf("مبيعات نقدية", "ديزل", "شاي", "عمالة")),
            "fa" to Demo("فروشگاه کشاورزی نور", com.innovation313.roshankhata.data.ShopCountry.IRAN,
                listOf("محمد رضایی", "علی احمدی", "حسین کریمی", "رضا موسوی", "مهدی حسینی", "امیر نظری"),
                listOf("0912 123 4567", "0935 765 4321", "0919 112 2334", "0921 998 8776", "0913 556 6778", "0936 443 3221"),
                listOf("اوره ۵ کیسه", "سم‌پاشی", "بذر گندم ۱۰ کیسه", "دی‌آمونیوم ۲ کیسه", "خوراک دام ۳ کیسه", "پیش‌پرداخت", "روی + پتاس"),
                listOf("فروش نقدی", "گازوئیل", "چای", "کارگر")),
            "hi" to Demo("बिलाल कृषि सेवा", com.innovation313.roshankhata.data.ShopCountry.INDIA,
                listOf("मोहम्मद अकरम", "राणा शफ़ीक़", "चौधरी नज़ीर", "मलिक इमरान", "हाजी असलम", "उस्मान गुज्जर"),
                listOf("98765 43210", "91234 56789", "99887 76655", "97654 32109", "93456 78901", "96543 21098"),
                listOf("यूरिया 5 बोरी", "स्प्रे", "गेहूँ बीज 10 बोरी", "डीएपी 2 बोरी", "चारा 3 बोरी", "एडवांस", "ज़िंक + पोटाश"),
                listOf("नक़द बिक्री", "डीज़ल", "चाय", "मज़दूरी")),
            "bn" to Demo("বিলাল কৃষি সেবা", com.innovation313.roshankhata.data.ShopCountry.BANGLADESH,
                listOf("মোহাম্মদ আকরাম", "রানা শফিক", "চৌধুরী নজির", "মালিক ইমরান", "হাজী আসলাম", "ওসমান গুজ্জর"),
                listOf("01712 345678", "01812 345678", "01912 345678", "01612 345678", "01512 345678", "01312 345678"),
                listOf("ইউরিয়া ৫ বস্তা", "স্প্রে", "গমের বীজ ১০ বস্তা", "ডিএপি ২ বস্তা", "খাদ্য ৩ বস্তা", "অগ্রিম", "জিঙ্ক + পটাশ"),
                listOf("নগদ বিক্রি", "ডিজেল", "চা", "মজুরি")),
            "in" to Demo("Toko Tani Bilal", com.innovation313.roshankhata.data.ShopCountry.INDONESIA,
                listOf("Budi Santoso", "Agus Wijaya", "Dedi Kurniawan", "Eko Prasetyo", "Haji Slamet", "Wawan Setiawan"),
                listOf("0812 3456 7890", "0813 7654 3210", "0815 1122 3344", "0817 9988 7766", "0819 5566 7788", "0821 4433 2211"),
                listOf("Urea 5 karung", "Semprot", "Benih gandum 10 karung", "DAP 2 karung", "Pakan 3 karung", "Uang muka", "Zinc + Kalium"),
                listOf("Penjualan tunai", "Solar", "Teh", "Upah"))
        )
    }
}
