package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.ui.CoachMarkController
import com.innovation313.roshankhata.ui.HudhudSheet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hudhud on a real Android (12 Nov 2026): his bar on Home opens his sheet, a turn of the phone with the sheet open
 * closes it without a crash, and a customer asked for by name is answered with that customer's own balance from the
 * book. Silent (speak = false): the emulator needs no voice engine. A made-up customer (lesson 80).
 */
@RunWith(AndroidJUnit4::class)
class HudhudOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun readyToUse() {
        // Past the first run, as EveryScreenOpensTest opens Home: language chosen, setup done, the country and the
        // shop's trade saved, so Home asks nothing over the sheet.
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putBoolean("chosen", true).commit()
        context.getSharedPreferences("setup", Context.MODE_PRIVATE).edit().putBoolean("done", true).commit()
        BusinessProfile.setCountry(context, ShopCountry.PAKISTAN)
        if (BusinessProfile.trade(context) == null) BusinessProfile.setTrade(context, Trade.KIRYANA)
        com.innovation313.roshankhata.ui.HudhudGreet.greeted(context)
    }

    private fun home() = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_UNLOCKED, true)

    /** Asks on the main thread until [check] holds, for at most [ms]. */
    private fun waitFor(ms: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            var ok = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ok = check() }
            if (ok) return true
            SystemClock.sleep(100)
        }
        return false
    }

    @Test
    fun hisFaceOpensTheSheetAndATurnClosesIt() {
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            scenario.onActivity { a -> a.findViewById<View>(R.id.ivHudhudFace).performClick() }
            assertTrue("Hudhud's sheet did not open from his face on Home", waitFor(3000) { HudhudSheet.isOpen() })

            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertFalse("the sheet outlived its screen", HudhudSheet.isOpen())
        }
    }

    @Test
    fun aCustomerAskedByNameIsAnsweredWithHisBalance() {
        val dao = KhataDatabase.get(context).khataDao()
        runBlocking {
            val id = dao.insertParty(Party(name = "Zarnosh Traders", phone = "03000000001"))
            dao.insertEntry(
                LedgerEntry(
                    partyId = id, amount = 4321.0, isGiven = true, entryNumber = "RK-970001",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            var figure: TextView? = null
            var label: TextView? = null
            scenario.onActivity { a ->
                val sheet = HudhudSheet.open(a, ask = "Zarnosh ka kitna baqaya hai", speak = false)
                figure = sheet?.findViewById(R.id.tvHudFigure)
                label = sheet?.findViewById(R.id.tvHudLabel)
            }
            val answered = waitFor(5000) {
                figure?.visibility == View.VISIBLE && figure?.text?.filter { it.isDigit() }?.toString() == "4321"
            }
            assertTrue("no answer of 4,321 for the customer, got: ${figure?.text}", answered)
            var name = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync { name = label?.text?.toString().orEmpty() }
            assertEquals("Zarnosh Traders", name)
        }
    }
    @Test
    fun anEntrySaidAsAFactOffersTheFormFilledIn() {
        val dao = KhataDatabase.get(context).khataDao()
        runBlocking {
            if (dao.partiesWithBalanceOnce().none { it.name == "Qeemat Brothers" }) {
                dao.insertParty(Party(name = "Qeemat Brothers", phone = "03000000002"))
            }
        }
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            var figure: TextView? = null
            var open: TextView? = null
            scenario.onActivity { a ->
                val sheet = HudhudSheet.open(a, ask = "Qeemat Brothers ko 700 diye", speak = false)
                figure = sheet?.findViewById(R.id.tvHudFigure)
                open = sheet?.findViewById(R.id.btnHudOpen)
            }
            val offered = waitFor(5000) {
                figure?.text?.filter { it.isDigit() }?.toString() == "700" && open?.visibility == View.VISIBLE
            }
            assertTrue("no entry offered for 700, got: ${figure?.text}", offered)
            var words = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync { words = open?.text?.toString().orEmpty() }
            assertEquals(context.getString(R.string.hudhud_fill_entry), words)
        }
    }

    @Test
    fun theMorningGreetingShowsOnceAndOpensToday() {
        CoachMarkController.markRun(context)
        com.innovation313.roshankhata.ui.HudhudGreet.forget(context)
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            var bubble: View? = null
            scenario.onActivity { a -> bubble = a.findViewById(R.id.hudhudGreeting) }
            assertTrue("the morning greeting did not show", waitFor(5000) { bubble?.visibility == View.VISIBLE })
            scenario.onActivity { a -> a.findViewById<View>(R.id.hudhudGreetingCard).performClick() }
            assertTrue("today's summary did not open from the greeting", waitFor(3000) { HudhudSheet.isOpen() })
        }
        // Greeted today: Home again shows no bubble.
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            SystemClock.sleep(1500)
            var shown = true
            scenario.onActivity { a -> shown = a.findViewById<View>(R.id.hudhudGreeting).visibility == View.VISIBLE }
            assertFalse("the greeting came twice in one day", shown)
        }
    }
}
