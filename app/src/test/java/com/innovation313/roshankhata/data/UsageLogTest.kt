package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's own counts (17 Nov 2026, BC-210): "since counting began" is the kept total, not a walk of days from 1970
 * that always said 0, and it survives the 40-day sweep; a window of days is still read day by day.
 */
@RunWith(RobolectricTestRunner::class)
class UsageLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun clean() {
        context.getSharedPreferences("usage_log", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun sinceCountingBeganIsTheTotalAndOutlivesTheSweep() {
        val now = System.currentTimeMillis()
        val start = now - 60 * day
        UsageLog.add(context, UsageLog.What.CALCULATIONS, start)
        UsageLog.add(context, UsageLog.What.CALCULATIONS, start + day)
        UsageLog.add(context, UsageLog.What.CALCULATIONS, now)
        // The two old days are past the 40 kept days and were swept; the total since counting still holds all three.
        assertEquals(3, UsageLog.count(context, UsageLog.What.CALCULATIONS, 0L, Long.MAX_VALUE, now))
        // Today's window, day by day.
        val today = DayMath.startOfDay(now)
        assertEquals(1, UsageLog.count(context, UsageLog.What.CALCULATIONS, today, today + day, now))
        // App opens were never counted: nothing.
        assertEquals(0, UsageLog.count(context, UsageLog.What.APP_OPENS, 0L, Long.MAX_VALUE, now))
    }
}
