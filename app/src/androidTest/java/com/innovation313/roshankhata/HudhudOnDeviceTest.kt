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
import com.innovation313.roshankhata.ui.HudhudStyle
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

    /**
     * 13 Nov 2026: his first chip's own words bring the list of what he can do, and "kaise ho" a word of his own —
     * small talk, never a customer's name or a figure. Whatever the app's language, the chip's label is understood
     * (HudhudWordsTest holds all nine).
     */
    @Test
    fun heSaysWhatHeCanDoAndHowHeIs() {
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            var lines: TextView? = null
            var intro = ""
            scenario.onActivity { a ->
                intro = a.getString(R.string.hudhud_can_intro)
                val sheet = HudhudSheet.open(a, ask = a.getString(R.string.hudhud_chip_abilities), speak = false)
                lines = sheet?.findViewById(R.id.tvHudLines)
            }
            assertTrue(
                "no list of what he can do, got: ${lines?.text}",
                waitFor(5000) { lines?.text?.toString()?.startsWith(intro) == true }
            )
            var how = ""
            scenario.onActivity { a ->
                how = a.getString(R.string.hudhud_chat_how)
                HudhudSheet.open(a)?.dismiss()
            }
            assertTrue("the sheet did not close", waitFor(3000) { !HudhudSheet.isOpen() })
            scenario.onActivity { a ->
                lines = HudhudSheet.open(a, ask = "kaise ho", speak = false)?.findViewById(R.id.tvHudLines)
            }
            assertTrue("no word of his own for kaise ho, got: ${lines?.text}", waitFor(5000) { lines?.text?.toString() == how })
        }
    }

    /**
     * His voice settings open with one style chosen out of all of them, the pace on a slider inside its range, and say
     * how he listens on this phone (no microphone asked here). 14 Nov 2026: the choices are chips (they were radio
     * buttons) and the pace is a slider; a style is a chip the owner can tap and hear at once.
     */
    @Test
    fun hisVoiceSettingsOpen() {
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            var style: com.google.android.material.chip.ChipGroup? = null
            var pace: com.google.android.material.slider.Slider? = null
            var listen: TextView? = null
            var sheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
            scenario.onActivity { a ->
                sheet = com.innovation313.roshankhata.ui.HudhudVoiceSheet.show(a)
                style = sheet?.findViewById(R.id.cgHudStyle)
                pace = sheet?.findViewById(R.id.slHudPace)
                listen = sheet?.findViewById(R.id.tvHudListen)
            }
            assertTrue("no style chosen", waitFor(3000) { (style?.checkedChipId ?: View.NO_ID) != View.NO_ID })
            assertEquals(
                "every style is offered, one chip each",
                HudhudStyle.values().size, style?.childCount
            )
            val speed = pace?.value ?: 0f
            assertTrue("the pace slider is outside its range: $speed", speed in 0.6f..1.5f)
            assertTrue("how he listens was not said", waitFor(5000) { !listen?.text.isNullOrBlank() })
            scenario.onActivity { sheet?.dismiss() }
        }
    }

    /**
     * 13 Nov 2026 (Brain v2): what he now reads from a real book — a customer's number, a product's price, how many
     * customers, today's date — each by the screen's own rule; and his face says who he is. Made-up names (lesson 80).
     */
    @Test
    fun heReadsTheBookBeyondBalances() {
        val dao = KhataDatabase.get(context).khataDao()
        runBlocking {
            if (dao.partiesWithBalanceOnce().none { it.name == "Gulnaz Kiryana" }) {
                dao.insertParty(Party(name = "Gulnaz Kiryana", phone = "03000000003"))
            }
            val p = dao.findOrCreateProduct(name = "Tarnab Powder")
            dao.updateProduct(p.copy(salePrice = 1234.0))
        }
        ActivityScenario.launch<Activity>(home()).use { scenario ->
            /** The figure he answers [question] with, on a sheet of its own. */
            fun figureOf(question: String): String {
                scenario.onActivity { a -> HudhudSheet.open(a)?.dismiss() }
                assertTrue("the last sheet did not close", waitFor(3000) { !HudhudSheet.isOpen() })
                var figure: TextView? = null
                scenario.onActivity { a ->
                    figure = HudhudSheet.open(a, ask = question, speak = false)?.findViewById(R.id.tvHudFigure)
                }
                waitFor(5000) { figure?.visibility == View.VISIBLE && !figure?.text.isNullOrBlank() }
                var text = ""
                InstrumentationRegistry.getInstrumentation().runOnMainSync { text = figure?.text?.toString().orEmpty() }
                return text
            }
            val number = figureOf("Gulnaz Kiryana ka number")
            assertEquals("her number, got: $number", "03000000003", number.filter { it.isDigit() })
            val price = figureOf("Tarnab Powder ka rate")
            assertEquals("its price, got: $price", "1234", price.filter { it.isDigit() })
            val customers = figureOf("kitne customer hain").filter { it.isDigit() }
            assertTrue("no count of customers, got: $customers", (customers.toIntOrNull() ?: 0) >= 1)
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
            val date = figureOf("aaj ki tareekh kya hai")
            assertTrue("today's date without the year, got: $date", date.contains(year))

            // His face: who he is, every time.
            scenario.onActivity { a -> HudhudSheet.open(a)?.dismiss() }
            assertTrue(waitFor(3000) { !HudhudSheet.isOpen() })
            var intro = ""
            var privacy = ""
            var lines: TextView? = null
            scenario.onActivity { a ->
                intro = a.getString(R.string.hudhud_intro)
                privacy = a.getString(R.string.hudhud_privacy_once)
                a.findViewById<View>(R.id.ivHudhudFace).performClick()
                // The face opened the sheet; open() hands back the one on show.
                lines = HudhudSheet.open(a)?.findViewById(R.id.tvHudLines)
            }
            // The first introduction on a phone also says, once, that the book stays on it (14 Nov 2026).
            assertTrue("his face did not say who he is, got: ${lines?.text}", waitFor(5000) {
                lines?.text?.toString().let { it == intro || it == intro + "\n" + privacy }
            })
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
