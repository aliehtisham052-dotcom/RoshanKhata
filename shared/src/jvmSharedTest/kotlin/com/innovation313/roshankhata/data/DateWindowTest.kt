package com.innovation313.roshankhata.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The date window, one arithmetic on both platforms (29 Oct 2026, the cleanup).
 *
 * The phone's DateRangeFilter (today, yesterday, this week, this month — as Calendar fields set to
 * 00:00:00.000 and 23:59:59.999), the computer's PartyWhen (start..start+day−1, week and month to now)
 * and its ReportWindow (yesterday as dayBefore..todayStart−1, week and month open) are each written
 * out here as they were and held to the shared enums at a fixed moment in a zone without clock changes.
 */
class DateWindowTest {

    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private val day = 24L * 60 * 60 * 1000
    /** Thu 15 Oct 2026 16:45:12.345 PKT. */
    private val now = Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.OCTOBER, 15, 16, 45, 12); set(Calendar.MILLISECOND, 345) }.timeInMillis

    private var countryWas = ShopCountry.current
    @Before fun fix() { countryWas = ShopCountry.current }
    @After fun restore() { ShopCountry.current = countryWas }

    // ---- the phone's DateRangeFilter, line for line (its Calendar.getInstance() in [tz]) ----
    private fun dayStart(y: Int, m: Int, d: Int) = Calendar.getInstance(tz).apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun dayEnd(y: Int, m: Int, d: Int) = Calendar.getInstance(tz).apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
    private fun phoneToday(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        return dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) to dayEnd(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }
    private fun phoneYesterday(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, -1) }
        return dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) to dayEnd(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }
    private fun phoneThisWeek(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply {
            timeInMillis = now
            firstDayOfWeek = ShopCountry.current.weekStart
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        return dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) to now
    }
    private fun phoneThisMonth(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        return dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), 1) to now
    }

    // ---- the computer's PartyWhen.window as KhataControls.kt wrote it ----
    private fun oldWindows(which: PartyWhen, now: Long): LongRange? {
        fun start(c: Calendar): Long = c.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        return when (which) {
            PartyWhen.ALL -> null
            PartyWhen.TODAY -> start(c).let { it until it + day }
            PartyWhen.YESTERDAY -> start(c.apply { add(Calendar.DAY_OF_MONTH, -1) }).let { it until it + day }
            PartyWhen.THIS_WEEK -> start(c.apply { firstDayOfWeek = ShopCountry.current.weekStart; set(Calendar.DAY_OF_WEEK, firstDayOfWeek) })..now
            PartyWhen.THIS_MONTH -> start(c.apply { set(Calendar.DAY_OF_MONTH, 1) })..now
        }
    }

    private fun assertPhone(expected: Pair<Long, Long>, window: LongRange?, what: String) {
        assertEquals("$what from", expected.first, window!!.first)
        assertEquals("$what to", expected.second, window.last)
    }

    @Test
    fun `the phone's four ranges are the shared window, from and to`() {
        for (country in listOf(ShopCountry.PAKISTAN, ShopCountry.IRAN, ShopCountry.QATAR)) {
            ShopCountry.current = country
            assertPhone(phoneToday(now), PartyWhen.TODAY.window(now, tz), "today")
            assertPhone(phoneYesterday(now), PartyWhen.YESTERDAY.window(now, tz), "yesterday")
            assertPhone(phoneThisWeek(now), PartyWhen.THIS_WEEK.window(now, tz), "this week, $country")
            assertPhone(phoneThisMonth(now), PartyWhen.THIS_MONTH.window(now, tz), "this month")
        }
    }

    @Test
    fun `the computer's windows are unchanged, every hour of a fortnight`() {
        for (country in listOf(ShopCountry.PAKISTAN, ShopCountry.IRAN, ShopCountry.QATAR)) {
            ShopCountry.current = country
            var at = now - 7 * day
            while (at <= now + 7 * day) {
                for (w in PartyWhen.entries) assertEquals("$w at $at", oldWindows(w, at), w.window(at, tz))
                at += 3_600_000L + 11_000
            }
        }
    }

    @Test
    fun `the report windows follow the Khata filter, week and month left open`() {
        val today = PartyWhen.TODAY.window(now, tz)!!
        assertEquals(today.first to today.last, ReportWindow.TODAY.window(now, tz))
        // Yesterday as ReportPane.kt wrote it: the day before's start, to the moment before today began.
        assertEquals(dayBefore(today.first, tz) to today.first - 1, ReportWindow.YESTERDAY.window(now, tz))
        assertEquals(PartyWhen.THIS_WEEK.window(now, tz)!!.first to null, ReportWindow.WEEK.window(now, tz))
        assertEquals(PartyWhen.THIS_MONTH.window(now, tz)!!.first to null, ReportWindow.MONTH.window(now, tz))
        assertNull(ReportWindow.ALL.window(now, tz))
        assertNull(ReportWindow.CUSTOM.window(now, tz))
        assertNull(PartyWhen.ALL.window(now, tz))
    }

    @Test
    fun `the week starts where the shop's country starts it`() {
        // 15 Oct 2026 is a Thursday: Monday the 12th in Pakistan, Saturday the 10th in Iran, Sunday the 11th in Qatar.
        fun weekFrom(country: ShopCountry): Int {
            ShopCountry.current = country
            return Calendar.getInstance(tz).apply { timeInMillis = PartyWhen.THIS_WEEK.window(now, tz)!!.first }.get(Calendar.DAY_OF_MONTH)
        }
        assertEquals(Calendar.THURSDAY, Calendar.getInstance(tz).apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK))
        assertEquals(12, weekFrom(ShopCountry.PAKISTAN))
        assertEquals(10, weekFrom(ShopCountry.IRAN))
        assertEquals(11, weekFrom(ShopCountry.QATAR))
    }

    @Test
    fun `a day's edges by the calendar`() {
        val start = PartyWhen.TODAY.window(now, tz)!!.first
        assertEquals(start + day, dayAfter(start, tz))
        assertEquals(start - day, dayBefore(start, tz))
        assertEquals(dayAfter(start, tz) - 1, PartyWhen.TODAY.window(now, tz)!!.last)
    }
}
