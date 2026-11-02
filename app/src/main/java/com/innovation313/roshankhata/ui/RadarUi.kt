package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PaymentRadar
import com.innovation313.roshankhata.data.RadarRow
import com.innovation313.roshankhata.data.ViewerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Payment Radar on the phone (2 Nov 2026): the switch, the shop's own normal (read once in a while, not
 * on every entry), and the words. The reading itself is [PaymentRadar] in the shared module.
 *
 * What the customer might see over the counter is kept plain: the customer page carries a coloured dot and,
 * when there is one, the likely days of the next payment — never "pays very late". The judgement and its
 * reasons open only on a tap. A helper's read-only phone shows none of it: how a customer pays is the owner's.
 */
object RadarUi {

    private const val PREFS = "payment_radar"
    private const val KEY_ON = "on"
    /** The shop's normal is read again after this long: it moves slowly, and it reads every customer's entries. */
    private const val SHOP_TTL_MS = 10L * 60 * 1000

    /** On unless the owner turned it off on this phone; never on a helper's phone. */
    fun on(context: Context): Boolean =
        !ViewerMode.isOn(context) && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    private var shopFor: Int = 0
    private var shopAt: Long = 0
    private var shopDays: Int? = null

    /** This shop's normal ([PaymentRadar.shopTypicalDays]), cached for [SHOP_TTL_MS] per open book. */
    suspend fun shopDays(dao: KhataDao): Int? {
        val key = System.identityHashCode(dao)
        val now = System.currentTimeMillis()
        if (key == shopFor && now - shopAt < SHOP_TTL_MS) return shopDays
        val days = withContext(Dispatchers.IO) { runCatching { PaymentRadar.shopTypicalDays(dao.radarRows()) }.getOrNull() }
        shopFor = key; shopAt = now; shopDays = days
        return days
    }

    /** One customer's reading from the entries his screen already holds. */
    suspend fun read(dao: KhataDao, partyId: Long, entries: List<LedgerEntry>, promise: Long?): PaymentRadar.Reading {
        val shop = shopDays(dao)
        return withContext(Dispatchers.Default) {
            PaymentRadar.read(
                entries.filter { !it.isDeleted }.map { RadarRow(partyId, it.isGiven, it.amount, it.timestamp) },
                System.currentTimeMillis(), shop, promise
            )
        }
    }

    /**
     * The dot's colour on the header's gold chip (tile_bills_bg): the ledger's own got/gave text colours and
     * the chip's gold, each repainted at night with the chip; null when there is nothing to say yet.
     */
    fun dot(r: PaymentRadar.Reading): Int? = when (r.light) {
        PaymentRadar.Light.GREEN -> R.color.green_got_text
        PaymentRadar.Light.AMBER -> R.color.fi_gold_b
        PaymentRadar.Light.RED -> R.color.red_gave_text
        PaymentRadar.Light.NONE -> null
    }

    /** The dot's colour on the I-gave form's dark green band. */
    fun dotOnDark(r: PaymentRadar.Reading): Int? = when (r.light) {
        PaymentRadar.Light.GREEN -> R.color.green_got_on_dark
        PaymentRadar.Light.AMBER -> R.color.gold_on_dark
        PaymentRadar.Light.RED -> R.color.red_gave_on_dark
        PaymentRadar.Light.NONE -> null
    }

    private fun short(t: Long) = DateWords.format("d MMM", t)

    /**
     * The customer page's chip: the dot and the likely days of the next payment, else the feature's name.
     * Null when the Radar has nothing to say (too little history and nothing long unpaid).
     */
    fun chip(context: Context, r: PaymentRadar.Reading): CharSequence? {
        val dot = dot(r) ?: return null
        val from = r.nextFrom
        val to = r.nextTo
        val words = if (from != null && to != null) context.getString(R.string.radar_next_short, short(from), short(to))
        else context.getString(R.string.radar_title)
        return SpannableStringBuilder("●  ").apply {
            setSpan(ForegroundColorSpan(ContextCompat.getColor(context, dot)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(words)
        }
    }

    /** The I-gave form's mark: the dot alone and the feature's name — the form is in front of the customer. */
    fun mark(context: Context, r: PaymentRadar.Reading): CharSequence? {
        val dot = dotOnDark(r) ?: return null
        return SpannableStringBuilder("●  ").apply {
            setSpan(ForegroundColorSpan(ContextCompat.getColor(context, dot)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(context.getString(R.string.radar_title))
        }
    }

    private fun days(context: Context, n: Int) = context.resources.getQuantityString(R.plurals.radar_days, n, n)

    /** Every reason, one line each, in the owner's language. */
    fun reasons(context: Context, r: PaymentRadar.Reading, promise: Long?): String = buildList {
        add(context.getString(when (r.light) {
            PaymentRadar.Light.GREEN -> R.string.radar_on_time
            PaymentRadar.Light.AMBER -> R.string.radar_late
            PaymentRadar.Light.RED -> R.string.radar_very_late
            PaymentRadar.Light.NONE -> R.string.radar_new
        }))
        r.typicalDays?.let { add(context.getString(R.string.radar_typical, days(context, it))) }
        r.shopDays?.let { add(context.getString(R.string.radar_shop_typical, days(context, it))) }
        r.oldestOpenDays?.takeIf { it > 0 }?.let { add(context.getString(R.string.radar_oldest, days(context, it))) }
        r.payDay?.let { add(context.getString(R.string.radar_pay_day, it.toString())) }
        val from = r.nextFrom
        val to = r.nextTo
        if (from != null && to != null) add(context.getString(R.string.radar_next, short(from), short(to)))
        if (r.promiseOverdue && promise != null) add(context.getString(R.string.radar_promise_passed, Format.dateOnly(promise)))
    }.joinToString("\n") { "• $it" } + "\n\n" + context.getString(R.string.radar_private)

    /** The reasons in a dialog, with the switch to turn the Radar off on this phone. */
    fun explain(activity: Activity, r: PaymentRadar.Reading, promise: Long?, onTurnedOff: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.radar_title)
            .setMessage(reasons(activity, r, promise))
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.radar_turn_off) { _, _ -> set(activity, false); onTurnedOff() }
            .show()
    }

    /** More → Payment Radar: what it is, and the one switch. */
    fun settings(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val isOn = on(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.radar_title)
            .setMessage(activity.getString(R.string.radar_switch_help) + "\n\n" + activity.getString(R.string.radar_private))
            .setPositiveButton(if (isOn) R.string.radar_turn_off else R.string.radar_turn_on) { _, _ -> set(activity, !isOn) }
            .setNegativeButton(R.string.close, null)
            .show()
    }
}
