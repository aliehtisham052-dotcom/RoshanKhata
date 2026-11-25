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
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.ThemeMode
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.ui.CoachMarkController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

/**
 * What the two screen-proof tests share (24 Nov 2026): ScreenProofPhoneTest (four looks, every screen) and
 * ScreenProofLangTest (every language at 85/100/150% text, six layout-heavy screens). The seeding, the "returning owner"
 * prefs, the language / text size / theme switching, the made-up large account, the screenshot and its saving live here
 * once, so a change to how a look is set up is made in one place (lesson 128). Moved from ScreenProofPhoneTest unchanged.
 */
object ScreenProofKit {

    const val BIG_NAME = "Test Wholesale Customer"
    const val DAY = 24L * 60 * 60 * 1000

    /** The old @Before of ScreenProofPhoneTest: a returning owner's prefs, the made-up shop, then the look. */
    fun apply(app: Context, language: String, level: Int, phoneScale: Float, dark: Boolean, trade: Trade) {
        app.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putBoolean("chosen", true).commit()
        // A returning owner: the setup finished (5 Nov 2026, SetupProgress) and the shop's country saved, so
        // Home does not ask an older book's country over the pictures (this emulator's SIM says US).
        app.getSharedPreferences("setup", Context.MODE_PRIVATE).edit().putBoolean("done", true).commit()
        BusinessProfile.setCountry(app, com.innovation313.roshankhata.data.ShopCountry.PAKISTAN)
        app.getSharedPreferences("welcome", Context.MODE_PRIVATE).edit().putBoolean("welcome_seen", true).commit()
        app.getSharedPreferences("home_offers", Context.MODE_PRIVATE).edit().putBoolean("backup_offered", true).commit()
        CoachMarkController.markRun(app)
        // The pictures are of the screens, not of their first-open sheets (one sheet has its own picture).
        com.innovation313.roshankhata.ui.FeatureIntro.markAllSeen(app)
        // Nor of Hudhud's morning greeting, which has its own picture (52).
        com.innovation313.roshankhata.ui.HudhudGreet.greeted(app)
        BalancePrivacy.setHidden(app, false)
        BusinessProfile.setTrade(app, trade)
        BusinessProfile.setBusinessName(app, "Al-Noor Traders")
        // Made-up shop details, so the statement's band and How to pay show (no real number).
        BusinessProfile.setBusinessPhone(app, "0300 0000000")
        BusinessProfile.setBusinessAddress(app, "Main Bazar")
        BusinessProfile.setBankName(app, "Test Bank")
        BusinessProfile.setBankIban(app, "PK00 TEST 0000 0000 0000")
        BusinessProfile.setBankJazzCash(app, "0300 0000001")
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

    /** The old @After: the phone's size, theme and language back to the plain ones. */
    fun reset(app: Context) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        TextSize.setLevel(app, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(app, ThemeMode.LIGHT)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    /** A customer with seven-figure dealings, so every total on the way is wide (made-up name). */
    fun bigAccount(app: Context): Long = runBlocking {
        val dao = KhataDatabase.get(app).khataDao()
        dao.partiesWithBalanceOnce().firstOrNull { it.name == BIG_NAME }?.let { return@runBlocking it.id }
        // A private note (schema 43): its chip under the name on 03-customer.
        val id = dao.insertParty(Party(name = BIG_NAME, phone = "03000000001", village = "Test Village", note = "Test note: pays on the 10th"))
        val now = System.currentTimeMillis()
        dao.insertEntry(LedgerEntry(partyId = id, amount = 2_345_678.0, isGiven = true, entryNumber = "RK-900101", timestamp = now - 5 * DAY, note = "Test sale"))
        dao.insertEntry(LedgerEntry(partyId = id, amount = 1_111_111.0, isGiven = false, entryNumber = "RK-900102", timestamp = now - DAY))
        id
    }

    fun shoot(look: String, app: Context, name: String, intent: Intent, settleMs: Long = 1500, then: ((Activity) -> Unit)? = null) {
        try {
            ActivityScenario.launch<Activity>(intent).use {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                SystemClock.sleep(settleMs)
                if (then != null) {
                    it.onActivity { a -> then(a) }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    SystemClock.sleep(1500)
                }
                val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                assertTrue("no picture for $name", bmp != null && bmp.width > 0)
                save(app, look, "$name.png", bmp)
            }
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
            // One screen that cannot open must not cost the others' pictures; its name says it is missing.
            android.util.Log.e("ScreenProof", "$look/$name failed", e)
        }
    }

    /** Saved to Download/screen-proof/<look>/<name>; screen-proof.sh pulls the folder and the workflow publishes it. */
    fun save(app: Context, look: String, name: String, bmp: Bitmap) {
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
}
