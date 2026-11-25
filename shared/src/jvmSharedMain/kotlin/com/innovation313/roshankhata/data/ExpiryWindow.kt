package com.innovation313.roshankhata.data

object ExpiryWindow {
    /**
     * How far ahead to warn.
     *
     * Sixty days is chosen to be useful rather than merely truthful: a warning
     * a week before expiry tells the dealer they have already lost the stock.
     * Two months is long enough to discount it, move it, or get it back to the
     * supplier while they will still accept a return.
     */
    const val WARN_DAYS = 60
    const val WARN_MS = WARN_DAYS * 24L * 60 * 60 * 1000

    /**
     * THE ONE END OF THE WINDOW (25 Nov 2026, BC-281): a batch is "expiring" when it holds stock and its day is on or before
     * the LAST MOMENT of the calendar day [WARN_DAYS] after today's (`expiryDate <= cutoff`, [KhataDao.expiringBatchesOnce]).
     * The bell and the daily sweep used midnight + 60 x 24 h, the Expiry and Bills pages, the computer's badges and
     * the automation `now + 60 x 24 h`: a batch dated mid-day on day 60 was on the pages in the morning and not in the
     * bell, and one dated before midnight appeared in the bell as the day wore on. Counted by the calendar, never by
     * 24-hour blocks (a day the clocks move is 23 or 25 hours). Every screen passes THIS to [Fefo.expiring].
     */
    fun cutoff(now: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long =
        DaySpans.dayStartAfter(now, WARN_DAYS + 1, tz) - 1
}
