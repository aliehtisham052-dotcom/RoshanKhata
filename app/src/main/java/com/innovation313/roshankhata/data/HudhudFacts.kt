package com.innovation313.roshankhata.data

import android.content.Context
import android.os.Bundle
import com.innovation313.roshankhata.AboutActivity
import com.innovation313.roshankhata.AuditActivity
import com.innovation313.roshankhata.BillsActivity
import com.innovation313.roshankhata.BusinessSettingsActivity
import com.innovation313.roshankhata.CalculatorActivity
import com.innovation313.roshankhata.ChequesActivity
import com.innovation313.roshankhata.CounterSaleActivity
import com.innovation313.roshankhata.DayBookActivity
import com.innovation313.roshankhata.InvoicesActivity
import com.innovation313.roshankhata.InsightsActivity
import com.innovation313.roshankhata.ZakatActivity
import com.innovation313.roshankhata.AndroidAutomationSettings
import com.innovation313.roshankhata.KhataActivity
import com.innovation313.roshankhata.LanguageActivity
import com.innovation313.roshankhata.PlansActivity
import com.innovation313.roshankhata.ProductCustomersActivity
import com.innovation313.roshankhata.ProductsActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.StaffActivity
import com.innovation313.roshankhata.SupplierRatesActivity
import com.innovation313.roshankhata.data.HudhudBook.Answer
import com.innovation313.roshankhata.data.HudhudBook.Mood
import com.innovation313.roshankhata.data.HudhudBook.Row
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.HudhudPrefs
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Hudhud's wider knowledge (13 Nov 2026, the owner: "Hudhud ko har cheez ka pata ho… kya kab aaya, kya kab gaya…
 * lekin accuracy 100%, fake data nahi"): a customer's number, last payment and limit; a product's price, stock,
 * purchase rate and which supplier is cheaper; the shop's profile; how many of anything were made today; where
 * cheques, plans, bills, stock and salaries stand; what came in and what happened today and who did it; the date.
 *
 * Every figure is read from the open shop's book by the rule of the screen the card opens — the stock by [Valuation]
 * (Products and the bell), money in by the day book's rows ([DayBook]), salaries by [Payroll] (Staff), suppliers' rates
 * by the bill lines Purchase rates reads — so Hudhud and the screen never disagree. Nothing is estimated: what the
 * book does not hold he says it does not hold ("No price is set for this item"), and the app's own counts that began
 * with this build say since when they count. Every answer only READS. Money and other businesses' secrets (purchase
 * rates, salaries, the day's takings) are behind the REPORTS right like every figure; numbers (phones, IBAN, tax) are
 * shown and never said aloud — a customer may be standing at the counter.
 */
internal object HudhudFacts {

    private fun now() = System.currentTimeMillis()

    private fun reports() = Access.can(Access.Permission.REPORTS)

    private fun ownerOnly(c: Context, label: String) =
        Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_owner_only)))

    /** "Label: value", the value isolated so a name or a figure reads right in a right-to-left line. */
    private fun line(label: String, value: String) = label + ": " + Format.isolate(value)

    private fun line(c: Context, label: Int, value: String) = line(c.getString(label), value)

    private fun whenOf(c: Context, t: Long) = WhenWords.whenWords(t, now(), AndroidWords(c))

    private fun dayOf(c: Context, t: Long) = WhenWords.dayWord(t, now(), AndroidWords(c))

    /** A count as a figure. */
    private fun n(count: Int): String = count.toString()

    /** The span asked, or today when none was. */
    private fun spanOf(window: VoiceQuestion.Window?): Pair<Long, Long> = Hudhud.span(window ?: VoiceQuestion.Window.TODAY)

    private fun heading(c: Context, title: Int, window: VoiceQuestion.Window?): String =
        if (window == null) c.getString(title) else c.getString(title) + " · " + c.getString(HudhudBook.windowWord(window))

    // ------------------------------------------------------------------------------------------------ people

    /**
     * A named customer's or supplier's fact. A payment is what he paid (a customer's "I got") or what the shop paid
     * him (a supplier's "I gave"), as his page shows it; his private note is never read out or shown here.
     */
    suspend fun person(
        c: Context, dao: KhataDao, ids: List<Long>, facet: Hudhud.Facet, parties: List<PartyWithBalance>
    ): Answer {
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return HudhudBook.unknown(c)
        if (found.size > 1) return HudhudBook.pick(c, found)
        val p = found.first()
        val party = dao.getParty(p.id)
        val name = p.name
        val balance = line(c.getString(R.string.hudhud_l_balance), HudhudBook.money(abs(p.balance)) + " · " + HudhudBook.side(c, p.balance))
        return when (facet) {
            Hudhud.Facet.PHONE -> {
                val phone = party?.phone?.takeIf { it.isNotBlank() }
                if (phone == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_no_phone, name)), openParty = p.id)
                } else {
                    Answer(Mood.CALM, label = name, figure = Format.ltr(phone), lines = listOf(c.getString(R.string.hudhud_l_phone)),
                        openParty = p.id, spoken = { w -> name + ": " + w.getString(R.string.hudhud_on_screen) })
                }
            }
            Hudhud.Facet.DETAILS -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                val first = points.minOfOrNull { it.timestamp }
                val lines = listOfNotNull(
                    c.getString(if (p.isCustomer) R.string.customer else R.string.supplier),
                    party?.fatherName?.takeIf { it.isNotBlank() }?.let { line(c, R.string.hudhud_l_father, it) },
                    party?.village?.takeIf { it.isNotBlank() }?.let { line(c, R.string.hudhud_l_village, it) },
                    party?.phone?.takeIf { it.isNotBlank() }?.let { line(c.getString(R.string.hudhud_l_phone), Format.ltr(it)) },
                    first?.let { line(c, R.string.hudhud_l_since, Format.dateOnly(it)) },
                    if (points.isEmpty()) c.getString(R.string.party_no_entries) else balance
                )
                Answer(Mood.CALM, label = name, lines = lines, openParty = p.id,
                    secrets = listOfNotNull(party?.phone?.takeIf { it.isNotBlank() }))
            }
            Hudhud.Facet.LAST_PAYMENT -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                // A customer pays with an "I got"; the shop pays a supplier with an "I gave".
                val last = points.filter { it.isGiven != p.isCustomer }.maxByOrNull { it.timestamp }
                if (last == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_no_payment), balance), openParty = p.id)
                } else {
                    val at = whenOf(c, last.timestamp)
                    Answer(Mood.CALM, label = name, figure = HudhudBook.money(last.amount),
                        lines = listOf(line(c, R.string.hudhud_l_last_payment, at), balance), openParty = p.id, money = true)
                }
            }
            Hudhud.Facet.LAST_ENTRY -> {
                val last = LedgerLines.of(dao, listOf(p.id)).maxByOrNull { it.timestamp }
                if (last == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.party_no_entries)), openParty = p.id)
                } else {
                    val way = c.getString(if (last.isGiven) R.string.i_gave else R.string.i_got)
                    Answer(Mood.CALM, label = name, figure = HudhudBook.money(last.amount),
                        lines = listOf(line(c, R.string.hudhud_l_last_entry, whenOf(c, last.timestamp) + " · " + way), balance),
                        openParty = p.id, money = true)
                }
            }
            Hudhud.Facet.SINCE -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                val first = points.minOfOrNull { it.timestamp }
                if (first == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.party_no_entries)), openParty = p.id)
                } else {
                    Answer(Mood.CALM, label = name, figure = Format.dateOnly(first),
                        lines = listOf(c.getString(R.string.hudhud_l_since), line(c, R.string.hudhud_l_entries, n(points.size))),
                        openParty = p.id)
                }
            }
            Hudhud.Facet.ENTRIES -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                val gave = points.filter { it.isGiven }
                val got = points.filter { !it.isGiven }
                Answer(Mood.CALM, label = name, figure = n(points.size),
                    lines = listOf(
                        c.getString(R.string.hudhud_l_entries),
                        line(c.getString(R.string.i_gave), n(gave.size) + " · " + HudhudBook.money(gave.sumOf { it.amount })),
                        line(c.getString(R.string.i_got), n(got.size) + " · " + HudhudBook.money(got.sumOf { it.amount }))
                    ),
                    openParty = p.id)
            }
            Hudhud.Facet.LIMIT -> {
                val limit = party?.creditLimit?.takeIf { it > 0.0 }
                if (limit == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_limit_none), balance), openParty = p.id)
                } else {
                    val used = if (p.balance > 0) Format.percent(p.balance / limit * 100.0) + "%" else "0%"
                    val over = p.balance > limit
                    Answer(if (over) Mood.CARE else Mood.CALM, label = name, figure = HudhudBook.money(limit),
                        lines = listOf(c.getString(R.string.credit_limit), balance, line(c, R.string.hudhud_l_limit_used, used)),
                        openParty = p.id, money = true)
                }
            }
            Hudhud.Facet.PROMISE -> {
                val day = party?.harvestPromise
                if (day == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_promise_none), balance), openParty = p.id)
                } else {
                    val passed = DayMath.startOfDay(day) < DayMath.startOfDay(now()) && Money.isPositive(p.balance)
                    Answer(if (passed) Mood.CARE else Mood.CALM, label = name, figure = Format.dateOnly(day),
                        lines = listOfNotNull(c.getString(R.string.hudhud_l_promise), if (passed) c.getString(R.string.hudhud_promise_passed) else null, balance),
                        openParty = p.id)
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ goods

    /**
     * A product's facts: price and stock for everyone at the counter; what it cost, which supplier charged what and how
     * much sold, behind REPORTS. Suppliers' rates are their last rate each, from the bill lines (Purchase rates' own
     * reading), cheapest first, with the day each was charged — an old rate stands beside its date, never as today's.
     */
    suspend fun goods(c: Context, dao: KhataDao, ids: List<Long>, facet: Hudhud.GoodsFacet, window: VoiceQuestion.Window?): Answer {
        val products = dao.productsOnce()
        val found = ids.mapNotNull { id -> products.firstOrNull { it.id == id } }
        if (found.isEmpty()) return HudhudBook.unknown(c)
        if (found.size > 1) {
            return Answer(Mood.CALM, label = c.getString(R.string.hudhud_pick_party),
                rows = found.map { Row(0, it.name, it.salePrice?.let { v -> HudhudBook.money(v) } ?: "", it.company, open = ProductsActivity::class.java) }, quiet = true)
        }
        val p = found.first()
        val name = p.name
        val unit = p.defaultUnit?.takeIf { it.isNotBlank() }
        fun perUnit(v: Double) = HudhudBook.money(v) + (unit?.let { " / " + UnitWords.label(it) } ?: "")
        val stock = runCatching { Valuation.compute(dao).rows.firstOrNull { it.productId == p.id } }.getOrNull()
        val lines = dao.supplierRateLinesOnce().filter { l ->
            l.rate > 0.0 && (l.productId == p.id || (l.productId == null && l.productName.trim().equals(name.trim(), ignoreCase = true)))
        }.sortedByDescending { it.billDate }
        fun priceLines(): List<String> = listOfNotNull(
            p.salePrice?.let { line(c, R.string.hudhud_l_cash_price, perUnit(it)) },
            p.creditPrice?.let { line(c, R.string.credit_price_hint, perUnit(it)) }
        )
        // Read once into locals: the shared module's properties do not smart-cast here.
        val onHand: Double? = stock?.onHand
        val stockUnit: String? = stock?.unit
        val isLow = stock?.isLow == true
        fun stockLine(): String? = if (stock == null) null else line(c, R.string.hudhud_l_stock,
            if (onHand == null) c.getString(R.string.stock_no_movement) else Format.qty(onHand, stockUnit))
        val open = ProductsActivity::class.java
        return when (facet) {
            Hudhud.GoodsFacet.PRICE -> {
                val pl = priceLines()
                if (pl.isEmpty()) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_price_none)), open = open)
                } else {
                    val v = p.salePrice ?: p.creditPrice ?: 0.0
                    Answer(Mood.CALM, label = name, figure = perUnit(v), lines = pl, open = open, money = true)
                }
            }
            Hudhud.GoodsFacet.STOCK -> {
                if (onHand == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.stock_no_movement)), open = open)
                } else {
                    val left = Format.qty(onHand, stockUnit)
                    Answer(if (isLow) Mood.CARE else Mood.CALM, label = name, figure = left,
                        lines = listOfNotNull(c.getString(R.string.hudhud_l_stock), if (isLow) c.getString(R.string.hudhud_t_low) else null),
                        open = open)
                }
            }
            Hudhud.GoodsFacet.COST -> {
                if (!reports()) return ownerOnly(c, name)
                val cost = p.costPrice ?: stock?.cost
                val last = lines.firstOrNull()
                if (cost == null && last == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_cost_none)), open = SupplierRatesActivity::class.java)
                } else {
                    val shown = cost ?: last!!.rate
                    Answer(Mood.CALM, label = name, figure = perUnit(shown),
                        lines = listOfNotNull(c.getString(R.string.hudhud_l_cost),
                            last?.let { line(c, R.string.hudhud_l_last_in, it.supplierName + " · " + Format.dateOnly(it.billDate) + " · " + HudhudBook.money(it.rate)) }),
                        open = SupplierRatesActivity::class.java, money = true)
                }
            }
            Hudhud.GoodsFacet.SUPPLIERS -> {
                if (!reports()) return ownerOnly(c, name)
                // Each supplier's own last rate, cheapest first.
                val each = lines.groupBy { it.supplierId }.values.map { it.first() }.sortedBy { it.rate }
                if (each.isEmpty()) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_rates_none)), open = SupplierRatesActivity::class.java)
                } else {
                    val cheap = each.first()
                    val dear = each.last()
                    Answer(Mood.CALM, label = name,
                        lines = listOfNotNull(
                            line(c, R.string.hudhud_l_cheapest, cheap.supplierName + " · " + HudhudBook.money(cheap.rate)),
                            if (each.size > 1) line(c, R.string.hudhud_l_dearest, dear.supplierName + " · " + HudhudBook.money(dear.rate)) else null
                        ),
                        rows = each.take(TOP).map { Row(it.supplierId, it.supplierName, HudhudBook.money(it.rate), Format.dateOnly(it.billDate)) },
                        open = SupplierRatesActivity::class.java, money = true)
                }
            }
            Hudhud.GoodsFacet.LAST_IN -> {
                val last = lines.firstOrNull()
                if (last == null) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_rates_none)), open = BillsActivity::class.java)
                } else {
                    val rate = if (reports()) " · " + HudhudBook.money(last.rate) else ""
                    Answer(Mood.CALM, label = name, figure = Format.dateOnly(last.billDate),
                        lines = listOf(c.getString(R.string.hudhud_l_last_in), last.supplierName + rate),
                        rows = listOf(Row(last.supplierId, last.supplierName, if (reports()) HudhudBook.money(last.rate) else "", Format.dateOnly(last.billDate))),
                        open = BillsActivity::class.java)
                }
            }
            Hudhud.GoodsFacet.BUYERS -> {
                val who = dao.customersWhoBought(p.id, 0L, Long.MAX_VALUE)
                val extras = Bundle().apply {
                    putLong(ProductCustomersActivity.EXTRA_PRODUCT_ID, p.id)
                    putString(ProductCustomersActivity.EXTRA_PRODUCT_NAME, p.name)
                }
                if (who.isEmpty()) {
                    Answer(Mood.CALM, label = name, lines = listOf(c.getString(R.string.hudhud_buyers_none)))
                } else {
                    Answer(Mood.CALM, label = name, figure = n(who.size), lines = listOf(c.getString(R.string.hudhud_t_customers)),
                        rows = who.take(TOP).map { Row(it.id, it.name, HudhudBook.money(abs(it.balance)), HudhudBook.side(c, it.balance)) },
                        open = ProductCustomersActivity::class.java, openExtras = extras)
                }
            }
            Hudhud.GoodsFacet.SOLD -> {
                val w = window ?: VoiceQuestion.Window.THIS_MONTH
                val label = name + " · " + c.getString(HudhudBook.windowWord(w))
                if (!reports()) return ownerOnly(c, label)
                val (from, to) = Hudhud.span(w)
                val sold = dao.topProductsBetween(from, to, ALL_PRODUCTS).firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                if (sold == null || sold.qty <= 0.0) {
                    Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_sold_none)))
                } else {
                    val q = Format.qty(sold.qty, sold.unit)
                    Answer(Mood.GOOD, label = label, figure = q, lines = listOf(c.getString(R.string.hudhud_l_sold)),
                        open = com.innovation313.roshankhata.SalesReportActivity::class.java)
                }
            }
            Hudhud.GoodsFacet.OVERVIEW -> {
                val all = priceLines() + listOfNotNull(stockLine()) +
                    if (reports()) listOfNotNull(lines.firstOrNull()?.let {
                        line(c, R.string.hudhud_l_last_in, it.supplierName + " · " + Format.dateOnly(it.billDate) + " · " + HudhudBook.money(it.rate))
                    }) else emptyList()
                val price = p.salePrice ?: p.creditPrice
                Answer(Mood.CALM, label = name, lines = all.ifEmpty { listOf(c.getString(R.string.hudhud_price_none)) }, open = open, money = true)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the shop

    /** The shop's profile as Business Profile holds it; a field never filled in is said to be empty, not invented. */
    fun shop(c: Context, facet: Hudhud.ShopFacet): Answer {
        val name = BusinessProfile.businessName(c)
        val address = BusinessProfile.businessAddress(c)
        val phone = BusinessProfile.businessPhone(c)
        val email = BusinessProfile.businessEmail(c)
        val tax = BusinessProfile.strn(c)
        val country = BusinessProfile.country(c)
        val countryName = Locale("", country.code).getDisplayCountry(c.resources.configuration.locales[0]).ifBlank { country.code }
        val trade = BusinessProfile.trade(c)?.let { Trade.of(it).label(c) }
        val open = BusinessSettingsActivity::class.java
        /** One field: written on the card; said aloud only when it is words, never a number (a phone, a tax number). */
        fun one(label: (Context) -> String, value: String?, sayIt: Boolean): Answer =
            if (value.isNullOrBlank()) {
                Answer(Mood.CALM, label = label(c), lines = listOf(c.getString(R.string.hudhud_not_set)), open = open)
            } else {
                Answer(Mood.CALM, label = label(c), figure = if (sayIt) value else Format.ltr(value), open = open,
                    secrets = if (sayIt) emptyList() else listOf(value))
            }
        fun res(id: Int): (Context) -> String = { it.getString(id) }
        return when (facet) {
            Hudhud.ShopFacet.NAME -> one(res(R.string.business_name), name, true)
            Hudhud.ShopFacet.ADDRESS -> one(res(R.string.business_address), address, true)
            Hudhud.ShopFacet.PHONE -> one(res(R.string.hudhud_l_phone), phone, false)
            Hudhud.ShopFacet.EMAIL -> one(res(R.string.hudhud_l_email), email, false)
            Hudhud.ShopFacet.TAX -> one({ BusinessProfile.taxLabel(it) }, tax, false)
            Hudhud.ShopFacet.CURRENCY -> one(res(R.string.hudhud_l_currency), BusinessProfile.currency(c), true)
            Hudhud.ShopFacet.COUNTRY -> one(res(R.string.hudhud_l_country), countryName, true)
            Hudhud.ShopFacet.TRADE -> one(res(R.string.hudhud_l_trade), trade, true)
            Hudhud.ShopFacet.BANK -> {
                val bank = listOfNotNull(
                    BusinessProfile.bankName(c)?.takeIf { it.isNotBlank() },
                    BusinessProfile.bankAccountTitle(c)?.takeIf { it.isNotBlank() },
                    BusinessProfile.bankIban(c)?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) },
                    BusinessProfile.bankJazzCash(c)?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }
                )
                if (bank.isEmpty()) one(res(R.string.hudhud_l_bank), null, false)
                else Answer(Mood.CALM, label = c.getString(R.string.hudhud_l_bank), lines = bank, open = open,
                    secrets = listOfNotNull(BusinessProfile.bankIban(c), BusinessProfile.bankJazzCash(c)).filter { it.isNotBlank() })
            }
            Hudhud.ShopFacet.ALL -> {
                val lines = listOfNotNull(
                    address?.takeIf { it.isNotBlank() }?.let { line(c, R.string.business_address, it) },
                    phone?.takeIf { it.isNotBlank() }?.let { line(c.getString(R.string.hudhud_l_phone), Format.ltr(it)) },
                    email?.takeIf { it.isNotBlank() }?.let { line(c.getString(R.string.hudhud_l_email), Format.ltr(it)) },
                    tax?.takeIf { it.isNotBlank() }?.let { line(BusinessProfile.taxLabel(c), Format.ltr(it)) },
                    trade?.let { line(c, R.string.hudhud_l_trade, it) },
                    line(c, R.string.hudhud_l_country, countryName),
                    line(c, R.string.hudhud_l_currency, BusinessProfile.currency(c))
                )
                Answer(Mood.CALM, label = name?.takeIf { it.isNotBlank() } ?: c.getString(R.string.hudhud_chip_shop),
                    lines = if (name.isNullOrBlank()) listOf(c.getString(R.string.hudhud_not_set)) + lines else lines, open = open,
                    secrets = listOfNotNull(phone, email, tax).filter { it.isNotBlank() })
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ about

    fun about(c: Context, topic: Hudhud.AboutTopic): Answer = when (topic) {
        Hudhud.AboutTopic.APP -> said(c, R.string.hudhud_about_app, AboutActivity::class.java)
        Hudhud.AboutTopic.MAKER -> said(c, R.string.hudhud_about_maker, AboutActivity::class.java)
        Hudhud.AboutTopic.PRIVACY -> said(c, R.string.hudhud_about_privacy, AboutActivity::class.java)
        Hudhud.AboutTopic.LANGUAGES -> said(c, R.string.hudhud_about_languages, LanguageActivity::class.java)
        Hudhud.AboutTopic.NAME_STORY -> Answer(Mood.GOOD, lines = listOf(c.getString(R.string.hudhud_about_name_story)))
        Hudhud.AboutTopic.VERSION -> {
            val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "?"
            Answer(Mood.CALM, lines = listOf(c.getString(R.string.hudhud_about_version, version)), open = AboutActivity::class.java)
        }
        Hudhud.AboutTopic.OWNER_NAME -> {
            val me = HudhudPrefs.callMe(c)
            if (me == null) {
                Answer(Mood.CALM, lines = listOf(c.getString(R.string.hudhud_owner_unnamed)), openLabel = R.string.hudhud_settings,
                    voiceSettings = true)
            } else {
                Answer(Mood.GOOD, label = me, lines = listOf(c.getString(R.string.hudhud_owner_named, Format.isolate(me))),
                    openLabel = R.string.hudhud_settings, voiceSettings = true)
            }
        }
    }

    private fun said(c: Context, words: Int, open: Class<*>?) =
        Answer(Mood.CALM, lines = listOf(c.getString(words)), open = open)

    /**
     * Today's date in the calendar everyone uses, the Islamic date worked out by the Umm al-Qura calendar (said to be
     * worked out: by the moon it may be a day off), the Persian date where the shop keeps that calendar, and the time.
     */
    fun clock(c: Context): Answer {
        val t = now()
        val date = DateWords.format("EEEE, d MMMM yyyy", t)
        val key = monthKey(c)
        val (hy, hm, hd) = ShopCalendar.ymd(ShopCalendar.Kind.HIJRI, t)
        val hijri = "$hd ${ShopCalendar.monthNames(ShopCalendar.Kind.HIJRI, key).getOrElse(hm - 1) { "" }} $hy"
        val time = DateWords.format("h:mm a", t)
        val jalali = if (BusinessProfile.calendar(c) == ShopCalendar.Kind.JALALI) {
            val (jy, jm, jd) = ShopCalendar.ymd(ShopCalendar.Kind.JALALI, t)
            "$jd ${ShopCalendar.monthNames(ShopCalendar.Kind.JALALI, key, BusinessProfile.country(c)).getOrElse(jm - 1) { "" }} $jy"
        } else null
        return Answer(Mood.CALM, label = c.getString(R.string.range_today), figure = date,
            lines = listOfNotNull(
                line(c, R.string.hudhud_l_islamic, hijri), jalali, line(c, R.string.hudhud_l_time, time),
                c.getString(R.string.hudhud_hijri_note)
            ),
            spoken = { w ->
                val l = w.resources.configuration.locales[0]
                val hm2 = ShopCalendar.monthNames(ShopCalendar.Kind.HIJRI, monthKey(w)).getOrElse(hm - 1) { "" }
                w.getString(R.string.range_today) + ": " + DateWords.format("EEEE, d MMMM yyyy", t, l) + ". " +
                    w.getString(R.string.hudhud_l_islamic) + ": " + "$hd $hm2 $hy"
            })
    }

    private fun monthKey(c: Context): String {
        val l = c.resources.configuration.locales[0]
        return when {
            l.language == "ur" && l.script.equals("Latn", ignoreCase = true) -> "en"
            l.language == "in" -> "id"
            else -> l.language
        }
    }

    // ------------------------------------------------------------------------------------------------ counts

    /**
     * How many of something: the people and goods the book holds now; what was made over a span of days (today's
     * when none was said, with the whole book's count beside it); the app's own counts, with the day they began.
     */
    suspend fun count(c: Context, dao: KhataDao, thing: Hudhud.Thing, window: VoiceQuestion.Window?, parties: List<PartyWithBalance>): Answer {
        val (from, to) = spanOf(window)
        val w = window ?: VoiceQuestion.Window.TODAY
        return when (thing) {
            Hudhud.Thing.CUSTOMERS -> {
                val all = parties.filter { it.isCustomer }
                val owe = all.count { Money.isPositive(it.balance) }
                counted(c, R.string.hudhud_t_customers, null, all.size, listOf(line(c.getString(R.string.you_will_get), n(owe))), KhataActivity::class.java)
            }
            Hudhud.Thing.SUPPLIERS -> {
                val all = parties.filter { !it.isCustomer }
                val owed = all.count { Money.isNegative(it.balance) }
                counted(c, R.string.hudhud_t_suppliers, null, all.size, listOf(line(c.getString(R.string.you_will_give), n(owed))), KhataActivity::class.java)
            }
            Hudhud.Thing.PRODUCTS -> {
                val v = runCatching { Valuation.compute(dao) }.getOrNull()
                val count = dao.productsOnce().size
                counted(c, R.string.tile_products, null, count, listOfNotNull(v?.low?.size?.takeIf { it > 0 }?.let { line(c, R.string.hudhud_t_low, n(it)) }),
                    ProductsActivity::class.java)
            }
            Hudhud.Thing.STAFF -> counted(c, R.string.hudhud_t_staff, null, dao.observeStaff().first().size, emptyList(), StaffActivity::class.java)
            Hudhud.Thing.ENTRIES -> {
                val shown = dao.entryCountBetween(from, to)
                val total = if (window == null) dao.entryCountBetween(0L, Long.MAX_VALUE) else null
                // The newest of them as rows, each who, which way and how much (the day book's own entries); the
                // amounts only for those who may see figures, as the other counts.
                val money = reports()
                val rows = if (shown == 0) emptyList() else dao.dayBookEntries(from, to).sortedByDescending { it.timestamp }.take(TOP).map { e ->
                    Row(0, e.partyName, if (money) HudhudBook.money(e.amount) else "",
                        c.getString(if (e.isGiven) R.string.i_gave else R.string.i_got) + " \u00B7 " + whenOf(c, e.timestamp),
                        open = KhataActivity::class.java)
                }
                counted(c, R.string.hudhud_l_entries, w, shown, listOfNotNull(total?.let { line(c, R.string.range_all, n(it)) }), KhataActivity::class.java, rows)
            }
            Hudhud.Thing.INVOICES -> {
                val made = dao.observeInvoicesFrom(from).first().filter { it.invoiceDate < to && it.kind == DocKind.INVOICE }
                    .sortedByDescending { it.invoiceDate }
                val total = if (window == null) dao.observeDocCount(DocKind.INVOICE).first() else null
                val money = reports()
                val sum = if (money && made.isNotEmpty()) line(c, R.string.hudhud_l_total, HudhudBook.money(made.sumOf { it.grandTotal })) else null
                val rows = made.take(TOP).map { i ->
                    Row(0, i.customerName, if (money) HudhudBook.money(i.grandTotal) else "", i.invoiceNumber + " \u00B7 " + dayOf(c, i.invoiceDate),
                        open = InvoicesActivity::class.java)
                }
                counted(c, R.string.invoices, w, made.size, listOfNotNull(sum, total?.let { line(c, R.string.range_all, n(it)) }), InvoicesActivity::class.java, rows)
            }
            Hudhud.Thing.BILLS -> {
                // Bills by their own date, as the Bills page lists them: who sent them, what they came to, the first items.
                val bills = dao.observeBills().first()
                val came = bills.filter { it.billDate >= from && it.billDate < to }.sortedByDescending { it.billDate }
                val money = reports()
                val sum = if (money && came.isNotEmpty()) line(c, R.string.hudhud_l_total, HudhudBook.money(came.sumOf { it.totalAmount })) else null
                val rows = came.take(TOP).map { b ->
                    val items = runCatching { dao.billItems(b.id) }.getOrDefault(emptyList()).filter { !it.isDeleted }
                    val what = items.take(2).joinToString(", ") { it.productName } + if (items.size > 2) "\u2026" else ""
                    Row(b.partyId, b.partyName, if (money) HudhudBook.money(b.totalAmount) else "",
                        listOf(dayOf(c, b.billDate), what).filter { it.isNotBlank() }.joinToString(" \u00B7 "))
                }
                counted(c, R.string.supplier_bills, w, came.size,
                    listOfNotNull(sum, if (window == null) line(c, R.string.range_all, n(bills.size)) else null), BillsActivity::class.java, rows)
            }
            Hudhud.Thing.TICKETS -> {
                val sales = dao.salesBetween(from, to).filter { it.status == "done" }
                val money = reports()
                val sum = if (money && sales.isNotEmpty()) line(c, R.string.hudhud_l_total, HudhudBook.money(sales.sumOf { it.grandTotal })) else null
                val rows = sales.take(TOP).map { t ->
                    Row(0, t.customerName?.takeIf { it.isNotBlank() } ?: c.getString(R.string.pos_walk_in),
                        if (money) HudhudBook.money(t.grandTotal) else "", t.saleNumber + " \u00B7 " + whenOf(c, t.timestamp),
                        open = CounterSaleActivity::class.java)
                }
                counted(c, R.string.hudhud_t_tickets, w, sales.size, listOfNotNull(sum), CounterSaleActivity::class.java, rows)
            }
            Hudhud.Thing.CALCULATIONS, Hudhud.Thing.APP_OPENS -> {
                val what = if (thing == Hudhud.Thing.CALCULATIONS) UsageLog.What.CALCULATIONS else UsageLog.What.APP_OPENS
                val shown = UsageLog.count(c, what, from, to)
                val since = UsageLog.since(c)
                val title = if (thing == Hudhud.Thing.CALCULATIONS) R.string.hudhud_t_calculations else R.string.hudhud_t_app_opens
                // Asked with no day: today's, and every one since this phone began counting.
                val all = if (window == null) UsageLog.count(c, what, 0L, Long.MAX_VALUE) else null
                counted(c, title, w, shown,
                    listOfNotNull(
                        all?.let { line(c, R.string.hudhud_l_since_counting, n(it)) },
                        since?.let { c.getString(R.string.hudhud_counted_since, Format.dateOnly(it)) }
                    ),
                    if (thing == Hudhud.Thing.CALCULATIONS) CalculatorActivity::class.java else null)
            }
        }
    }

    /** A count with its heading; none said plainly ("None in this time"), not as a bare nought. */
    private fun counted(
        c: Context, title: Int, window: VoiceQuestion.Window?, count: Int, lines: List<String>, open: Class<*>?, rows: List<Row> = emptyList()
    ): Answer {
        val label = heading(c, title, window)
        if (count == 0) {
            val none = c.getString(if (window != null) R.string.hudhud_count_none else R.string.hudhud_none_yet)
            return Answer(Mood.CALM, label = label, lines = listOf(none) + lines, open = open)
        }
        return Answer(Mood.CALM, label = label, figure = n(count), lines = lines, rows = rows, open = open)
    }

    // ------------------------------------------------------------------------------------------------ the pages

    suspend fun status(c: Context, dao: KhataDao, of: Hudhud.StatusOf, parties: List<PartyWithBalance>): Answer = when (of) {
        Hudhud.StatusOf.CHEQUES -> cheques(c, dao)
        Hudhud.StatusOf.PLANS -> plans(c, dao)
        Hudhud.StatusOf.BILLS -> bills(c, dao, parties)
        Hudhud.StatusOf.LOW_STOCK -> lowStock(c, dao)
        Hudhud.StatusOf.BOOK -> book(c, dao, parties)
        Hudhud.StatusOf.STAFF -> salaries(c, dao)
    }

    /** Cheques not yet cleared, bounced or cancelled: those to bank and those to be cashed, and how many are due. */
    private suspend fun cheques(c: Context, dao: KhataDao): Answer {
        val label = c.getString(R.string.cheques)
        val pending = dao.pendingChequesOnce()
        if (pending.isEmpty()) {
            return Answer(Mood.GOOD, label = label, lines = listOf(c.getString(R.string.hudhud_cheques_none)), open = ChequesActivity::class.java)
        }
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY).second
        val due = pending.count { it.dueDate < endOfToday }
        val got = pending.filter { it.isReceived }
        val gave = pending.filter { !it.isReceived }
        val money = reports()
        fun part(title: Int, list: List<Cheque>) =
            line(c, title, n(list.size) + if (money && list.isNotEmpty()) " · " + HudhudBook.money(list.sumOf { it.amount }) else "")
        return Answer(if (due > 0) Mood.CARE else Mood.CALM, label = label, figure = n(pending.size),
            lines = listOfNotNull(part(R.string.hudhud_l_cheques_in, got), part(R.string.hudhud_l_cheques_out, gave),
                if (due > 0) line(c, R.string.hudhud_l_due, n(due)) else null),
            open = ChequesActivity::class.java)
    }

    /** Open payment plans: how many, how many are due today or late, and the soonest few. */
    private suspend fun plans(c: Context, dao: KhataDao): Answer {
        val label = c.getString(R.string.nav_plans)
        val open = dao.openPlansOnce()
        if (open.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_plans_none)), open = PlansActivity::class.java)
        }
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY).second
        val due = open.filter { (it.nextDueDate ?: Long.MAX_VALUE) < endOfToday }
        return Answer(if (due.isNotEmpty()) Mood.CARE else Mood.CALM, label = label, figure = n(open.size),
            lines = listOfNotNull(
                if (due.isNotEmpty()) line(c, R.string.hudhud_l_due, n(due.size)) else null,
                if (reports()) line(c, R.string.hudhud_l_remaining, HudhudBook.money(open.sumOf { it.remaining })) else null
            ),
            rows = open.take(TOP).map { Row(it.partyId, it.partyName, if (reports()) HudhudBook.money(it.remaining) else "", it.nextDueDate?.let { d -> Format.dateOnly(d) }) },
            open = PlansActivity::class.java)
    }

    /** Supplier bills not paid in full, what is owed to suppliers (Home's sum over them), and the next due. */
    private suspend fun bills(c: Context, dao: KhataDao, parties: List<PartyWithBalance>): Answer {
        val label = c.getString(R.string.supplier_bills)
        val unpaid = dao.observeBills().first().filter { !it.isPaidInFull }.sortedBy { it.dueDate ?: Long.MAX_VALUE }
        if (unpaid.isEmpty()) {
            return Answer(Mood.GOOD, label = label, lines = listOf(c.getString(R.string.hudhud_bills_none)), open = BillsActivity::class.java)
        }
        val owed = PartyTotals.of(parties.filter { !it.isCustomer }.map { it.balance }).give
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY).second
        val due = unpaid.count { (it.dueDate ?: Long.MAX_VALUE) < endOfToday }
        return Answer(if (due > 0) Mood.CARE else Mood.CALM, label = label, figure = n(unpaid.size),
            lines = listOfNotNull(
                if (reports()) line(c, R.string.hudhud_l_owed_suppliers, HudhudBook.money(owed)) else null,
                if (due > 0) line(c, R.string.hudhud_l_due, n(due)) else null
            ),
            rows = unpaid.take(TOP).map { Row(it.partyId, it.partyName, if (reports()) HudhudBook.money(it.totalAmount) else "", it.dueDate?.let { d -> Format.dateOnly(d) }) },
            open = BillsActivity::class.java)
    }

    /** Goods at or under the level the owner set: the bell's own list ([Valuation.low]). */
    private suspend fun lowStock(c: Context, dao: KhataDao): Answer {
        val label = c.getString(R.string.hudhud_t_low)
        val v = Valuation.compute(dao)
        val low = v.low
        if (low.isEmpty()) {
            return Answer(Mood.GOOD, label = label, lines = listOf(c.getString(R.string.hudhud_low_none), line(c, R.string.tile_products, n(v.rows.size))),
                open = ProductsActivity::class.java)
        }
        return Answer(Mood.CARE, label = label, figure = n(low.size),
            rows = low.take(TOP + 1).map { Row(0, it.name, it.onHand?.let { h -> Format.qty(h, it.unit) } ?: "", null, open = ProductsActivity::class.java) },
            open = ProductsActivity::class.java)
    }

    /**
     * The whole book in a few lines ("jitna data hai uski information"): its people, goods, entries and documents, its
     * last entry and last backup, and — for the owner — what is to get and to give.
     */
    private suspend fun book(c: Context, dao: KhataDao, parties: List<PartyWithBalance>): Answer {
        val customers = parties.count { it.isCustomer }
        val suppliers = parties.size - customers
        val entries = dao.entryCountBetween(0L, Long.MAX_VALUE)
        val products = dao.productsOnce().size
        val invoices = dao.observeDocCount(DocKind.INVOICE).first()
        val last = parties.maxOfOrNull { it.lastActivity }?.takeIf { it > 0L }
        val backup = BackupReminder.lastBackupAt(c).takeIf { it > 0L }
        val totals = PartyTotals.of(parties.map { it.balance })
        val lines = listOfNotNull(
            line(c, R.string.hudhud_t_customers, n(customers)),
            line(c, R.string.hudhud_t_suppliers, n(suppliers)),
            line(c, R.string.hudhud_l_entries, n(entries)),
            line(c, R.string.tile_products, n(products)),
            line(c, R.string.invoices, n(invoices)),
            last?.let { line(c, R.string.hudhud_l_last_entry, whenOf(c, it)) },
            line(c, R.string.hudhud_l_last_backup, backup?.let { whenOf(c, it) } ?: c.getString(R.string.na_backup_never)),
            if (reports()) line(c, R.string.you_will_get, HudhudBook.money(totals.get)) else null,
            if (reports()) line(c, R.string.you_will_give, HudhudBook.money(totals.give)) else null
        )
        return Answer(Mood.CALM, label = c.getString(R.string.hudhud_t_book), lines = lines, open = KhataActivity::class.java)
    }

    /** This month's salaries still to pay, by Staff's own month rule ([Payroll.month]). */
    private suspend fun salaries(c: Context, dao: KhataDao): Answer {
        val label = c.getString(R.string.hudhud_t_salary)
        if (!reports()) return ownerOnly(c, label)
        val (from, to, days) = Payroll.monthRange(now())
        val staff = dao.observeStaff().first()
        val marks = dao.observeAttendanceBetween(from, to).first()
        val pays = dao.observeStaffPaymentsBetween(from, to).first()
        val due = staff.map { person ->
            person to Payroll.month(person.monthlySalary, days, marks.filter { it.staffId == person.id }.map { it.status },
                pays.filter { it.staffId == person.id }).due
        }.filter { it.second > 0.004 }
        if (due.isEmpty()) {
            return Answer(Mood.GOOD, label = label, lines = listOf(c.getString(R.string.hudhud_salary_none)), open = StaffActivity::class.java)
        }
        val total = due.sumOf { it.second }
        return Answer(Mood.CALM, label = label, figure = HudhudBook.money(total),
            rows = due.take(TOP + 1).map { (s, d) -> Row(0, s.name, HudhudBook.money(d), null, open = StaffActivity::class.java) },
            open = StaffActivity::class.java, money = true)
    }

    // ------------------------------------------------------------------------------------- what came, what happened

    /**
     * Goods that came in on supplier bills over a span (the last few bills when none was said): who sent them, when,
     * the first items of each, and — for the owner — what each bill came to.
     */
    suspend fun deliveries(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val label = heading(c, R.string.hudhud_t_deliveries, window)
        val all = dao.observeBills().first().sortedByDescending { it.billDate }
        val bills = if (window == null) all.take(TOP) else Hudhud.span(window).let { (from, to) -> all.filter { it.billDate >= from && it.billDate < to } }
        if (bills.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_deliveries_none)), open = BillsActivity::class.java)
        }
        val rows = bills.take(TOP).map { b ->
            val items = runCatching { dao.billItems(b.id) }.getOrDefault(emptyList()).filter { !it.isDeleted }
            val what = items.take(2).joinToString(", ") { it.productName } + if (items.size > 2) "…" else ""
            Row(b.partyId, b.partyName, if (reports()) HudhudBook.money(b.totalAmount) else "",
                listOf(dayOf(c, b.billDate), what).filter { it.isNotBlank() }.joinToString(" · "))
        }
        return Answer(Mood.CALM, label = label, figure = if (window != null) n(bills.size) else null, rows = rows, open = BillsActivity::class.java)
    }

    /**
     * What happened in the book over a span (today when none was said) and who did it — the owner's check and balance:
     * entries written, money in and out (the day book's rows), invoices, bills, counter tickets, what was edited or
     * deleted and by whom (the audit log), the calculator's sums and how often the app came to the front.
     */
    suspend fun activity(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = heading(c, R.string.hudhud_t_activity, w)
        if (!reports()) return ownerOnly(c, label)
        val (from, to) = Hudhud.span(w)
        val entries = dao.entryCountBetween(from, to)
        val rows = DayBook.rows(dao, from, to)
        val moneyIn = rows.filter { it.kind == DayBook.Kind.GOT && isCustomerRow(it) || it.kind == DayBook.Kind.CASH_IN }.sumOf { it.amount }
        val moneyOut = rows.filter { it.kind == DayBook.Kind.CASH_OUT || it.kind == DayBook.Kind.BILL_PAID || it.kind == DayBook.Kind.STAFF_PAID }.sumOf { it.amount }
        val invoices = dao.observeInvoicesFrom(from).first().count { it.invoiceDate < to && it.kind == DocKind.INVOICE }
        val bills = dao.observeBills().first().count { it.billDate >= from && it.billDate < to }
        val tickets = dao.doneSaleCount(from, to)
        val audit = dao.latestAudit(AUDIT_READ).filter { it.timestamp >= from && it.timestamp < to }
        val edited = audit.count { it.action in EDITS }
        val deleted = audit.count { it.action in DELETES }
        val voided = audit.count { it.action == AuditAction.SALE_VOIDED || it.action == AuditAction.RETURN_VOIDED }
        val by = audit.mapNotNull { it.user?.takeIf { u -> u.isNotBlank() } }.distinct()
        val sums = UsageLog.count(c, UsageLog.What.CALCULATIONS, from, to)
        val opens = UsageLog.count(c, UsageLog.What.APP_OPENS, from, to)
        val lines = listOfNotNull(
            line(c, R.string.hudhud_l_entries, n(entries)),
            if (moneyIn > 0.004) line(c, R.string.hudhud_l_money_in, HudhudBook.money(moneyIn)) else null,
            if (moneyOut > 0.004) line(c, R.string.hudhud_l_money_out, HudhudBook.money(moneyOut)) else null,
            if (invoices > 0) line(c, R.string.invoices, n(invoices)) else null,
            if (bills > 0) line(c, R.string.supplier_bills, n(bills)) else null,
            if (tickets > 0) line(c, R.string.hudhud_t_tickets, n(tickets)) else null,
            if (edited > 0 || deleted > 0) line(c.getString(R.string.hudhud_l_edited), n(edited)) + " · " + line(c.getString(R.string.hudhud_l_deleted), n(deleted)) else null,
            if (voided > 0) line(c, R.string.hudhud_l_voided, n(voided)) else null,
            if (by.isNotEmpty()) line(c, R.string.hudhud_l_by, by.joinToString(", ")) else null,
            if (sums > 0) line(c, R.string.hudhud_t_calculations, n(sums)) else null,
            if (opens > 0) line(c, R.string.hudhud_t_app_opens, n(opens)) else null
        )
        val nothing = entries == 0 && rows.isEmpty() && invoices == 0 && bills == 0 && tickets == 0 && audit.isEmpty()
        val open = if (w == VoiceQuestion.Window.TODAY || w == VoiceQuestion.Window.YESTERDAY) DayBookActivity::class.java else AuditActivity::class.java
        val extras = if (open == DayBookActivity::class.java) Bundle().apply { putLong(DayBookActivity.EXTRA_DAY, from) } else null
        return Answer(Mood.CALM, label = label,
            lines = if (nothing) listOf(c.getString(R.string.hudhud_activity_none)) + lines.drop(1).filter { it.isNotEmpty() } else lines,
            open = open, openExtras = extras)
    }

    /** A day book row's party is a customer: a supplier's "I got" is goods that came, not money. */
    private fun isCustomerRow(r: DayBook.Row): Boolean = r.customer

    /**
     * Money that came in over a span (today when none was said): customers' payments (their "I got", any way of
     * paying) and the cashbook's income — the day book's own rows — with how it came (cash, bank, wallet…).
     */
    suspend fun collected(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = heading(c, R.string.hudhud_t_collected, w)
        if (!reports()) return ownerOnly(c, label)
        val (from, to) = Hudhud.span(w)
        val rows = DayBook.rows(dao, from, to).filter { it.kind == DayBook.Kind.GOT && isCustomerRow(it) || it.kind == DayBook.Kind.CASH_IN }
        val total = rows.sumOf { it.amount }
        val khata = rows.filter { it.kind == DayBook.Kind.GOT }.sumOf { it.amount }
        val cash = rows.filter { it.kind == DayBook.Kind.CASH_IN }.sumOf { it.amount }
        val words = AndroidWords(c)
        val ways = rows.groupBy { it.method ?: "" }.filterKeys { it.isNotEmpty() }
            .map { (m, list) -> line(PaymentMethod.label(words, m), HudhudBook.money(list.sumOf { it.amount })) }
        val open = if (w == VoiceQuestion.Window.TODAY || w == VoiceQuestion.Window.YESTERDAY) DayBookActivity::class.java else null
        val extras = if (open != null) Bundle().apply { putLong(DayBookActivity.EXTRA_DAY, from) } else null
        return Answer(if (Money.isPositive(total)) Mood.GOOD else Mood.CALM, label = label, figure = HudhudBook.money(total),
            lines = listOf(line(c, R.string.hudhud_l_from_customers, HudhudBook.money(khata)), line(c, R.string.hudhud_l_cashbook_in, HudhudBook.money(cash))) + ways,
            open = open, openExtras = extras, money = true)
    }

    // ------------------------------------------------------------------------------------------------ told, not asked

    /**
     * A question about somebody that names nobody ("uska number", "limit kitni hai"), told rather than asked back (13 Nov
     * 2026, the owner: "aap ka kaam batana hai, poochhna nahi"): the customers dealt with last, each with that fact —
     * his number, his last payment, his credit limit, his promised day — or else his balance. A name in the question
     * gives the one man.
     */
    suspend fun needName(c: Context, dao: KhataDao, facet: Hudhud.Facet?, parties: List<PartyWithBalance>): Answer {
        val title = when (facet) {
            Hudhud.Facet.PHONE -> R.string.hudhud_need_phone
            Hudhud.Facet.LAST_PAYMENT -> R.string.hudhud_need_payment
            Hudhud.Facet.LIMIT -> R.string.hudhud_need_limit
            Hudhud.Facet.PROMISE -> R.string.hudhud_need_promise
            else -> R.string.hudhud_need_recent
        }
        val recent = parties.sortedByDescending { it.lastActivity }.take(TOP)
        if (recent.isEmpty()) {
            return Answer(Mood.CALM, label = c.getString(title), lines = listOf(c.getString(R.string.voice_no_party)), open = KhataActivity::class.java)
        }
        val secrets = ArrayList<String>()
        val rows = recent.map { p ->
            when (facet) {
                Hudhud.Facet.PHONE -> {
                    val phone = p.phone?.takeIf { it.isNotBlank() }
                    phone?.let { secrets += it }
                    Row(p.id, p.name, phone?.let { Format.ltr(it) } ?: c.getString(R.string.hudhud_no_phone_short), null)
                }
                Hudhud.Facet.LAST_PAYMENT -> {
                    // A customer pays with an "I got"; the shop pays a supplier with an "I gave".
                    val last = LedgerLines.of(dao, listOf(p.id)).filter { it.isGiven != p.isCustomer }.maxByOrNull { it.timestamp }
                    if (last == null) Row(p.id, p.name, "", c.getString(R.string.hudhud_no_payment))
                    else Row(p.id, p.name, HudhudBook.money(last.amount), whenOf(c, last.timestamp))
                }
                Hudhud.Facet.LIMIT -> {
                    val limit = dao.getParty(p.id)?.creditLimit?.takeIf { it > 0.0 }
                    if (limit == null) Row(p.id, p.name, "", c.getString(R.string.hudhud_limit_none))
                    else Row(p.id, p.name, HudhudBook.money(limit), HudhudBook.money(abs(p.balance)) + " \u00B7 " + HudhudBook.side(c, p.balance))
                }
                Hudhud.Facet.PROMISE -> {
                    val day = dao.getParty(p.id)?.harvestPromise
                    if (day == null) Row(p.id, p.name, "", c.getString(R.string.hudhud_promise_none))
                    else Row(p.id, p.name, Format.dateOnly(day), null)
                }
                else -> Row(p.id, p.name, HudhudBook.money(abs(p.balance)), HudhudBook.side(c, p.balance))
            }
        }
        return Answer(Mood.CALM, label = c.getString(title), lines = listOf(c.getString(R.string.hudhud_need_hint)), rows = rows,
            open = KhataActivity::class.java, secrets = secrets)
    }

    /**
     * What the cashbook took in and paid out over a span (today when none was said): the two sums, the difference, and
     * its own rows, newest first (13 Nov 2026, the owner: "cashbook mein kya aaya, kya gaya"). Money, behind REPORTS.
     */
    suspend fun cashbook(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = heading(c, R.string.hudhud_t_cashbook, w)
        if (!reports()) return ownerOnly(c, label)
        val (from, to) = Hudhud.span(w)
        val rows = dao.cashbookRowsBetween(from, to)
        val open = com.innovation313.roshankhata.CashbookActivity::class.java
        if (rows.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_cashbook_none)), open = open)
        }
        val moneyIn = rows.filter { it.isIncome }.sumOf { it.amount }
        val moneyOut = rows.filter { !it.isIncome }.sumOf { it.amount }
        return Answer(Mood.CALM, label = label, figure = HudhudBook.money(moneyIn - moneyOut),
            lines = listOf(
                c.getString(R.string.hudhud_l_net),
                line(c, R.string.hudhud_l_money_in, HudhudBook.money(moneyIn)),
                line(c, R.string.hudhud_l_money_out, HudhudBook.money(moneyOut))
            ),
            rows = rows.sortedByDescending { it.timestamp }.take(TOP).map { r ->
                Row(0, r.category.ifBlank { c.getString(if (r.isIncome) R.string.hudhud_l_money_in else R.string.hudhud_l_money_out) },
                    if (r.isIncome) "+" + HudhudBook.money(r.amount) else HudhudBook.money(-r.amount),
                    listOfNotNull(whenOf(c, r.timestamp), r.note?.takeIf { it.isNotBlank() }).joinToString(" \u00B7 "), open = open)
            },
            open = open, money = true)
    }

    /** Goods that came back at the counter over a span (today when none was said): how many returns, and what for. */
    suspend fun returns(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = heading(c, R.string.hudhud_t_returns, w)
        val (from, to) = Hudhud.span(w)
        val all = dao.returnsBetween(from, to).filter { it.status == "done" }
        val open = CounterSaleActivity::class.java
        if (all.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_returns_none)), open = open)
        }
        val money = reports()
        return Answer(Mood.CALM, label = label, figure = n(all.size),
            lines = if (money) listOf(
                line(c, R.string.hudhud_l_returned_worth, HudhudBook.money(all.sumOf { it.total })),
                line(c, R.string.hudhud_l_refunded, HudhudBook.money(all.sumOf { it.refundAmount }))
            ) else emptyList(),
            rows = all.take(TOP).map { r ->
                Row(0, r.customerName?.takeIf { it.isNotBlank() } ?: c.getString(R.string.pos_walk_in), if (money) HudhudBook.money(r.total) else "",
                    listOf(r.returnNumber, whenOf(c, r.timestamp)).joinToString(" \u00B7 "), open = open)
            },
            open = open)
    }

    private val EDITS = setOf(AuditAction.ENTRY_EDITED, AuditAction.CASH_EDITED, AuditAction.PRICE_CHANGED, AuditAction.STOCK_ADJUSTED)
    private val DELETES = setOf(AuditAction.ENTRY_DELETED, AuditAction.CASH_DELETED, AuditAction.PARTY_DELETED)

    /**
     * What sold most over [window] (this month when none is said; 14 Nov 2026, the owner's list: "sab se zyada kya bik
     * raha hai"): Sale Insights' own count ([KhataDao.topProductsBetween]), quantities as sold, never a guess at money.
     */
    suspend fun topGoods(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.THIS_MONTH
        val (from, to) = Hudhud.span(w)
        val top = dao.topProductsBetween(from, to, TOP)
        val label = heading(c, R.string.hudhud_t_top_goods, w)
        if (!reports()) return ownerOnly(c, label)
        if (top.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_top_goods_none)),
                open = InsightsActivity::class.java)
        }
        val rows = top.map { s ->
            Row(0, s.name, Format.qty(s.qty, s.unit), null, open = InsightsActivity::class.java)
        }
        return Answer(Mood.GOOD, label = label, figure = top.first().name, rows = rows, open = InsightsActivity::class.java)
    }

    /**
     * The Qarz-e-Hasna in the book (14 Nov 2026): lent without profit and still to come back, by Zakat's own split
     * ([KhataDao.observeZakatBalancesByParty]), the five largest by name, and what it is in one plain line.
     */
    suspend fun qarz(c: Context, dao: KhataDao, parties: List<PartyWithBalance>): Answer {
        val label = c.getString(R.string.hudhud_t_qarz)
        val note = c.getString(R.string.hudhud_qarz_note)
        if (!reports()) return ownerOnly(c, label)
        val owed = dao.observeZakatBalancesByParty().first().filter { it.qarz > 0.005 }.sortedByDescending { it.qarz }
        if (owed.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_qarz_none), note),
                open = KhataActivity::class.java)
        }
        val byId = parties.associateBy { it.id }
        val rows = owed.take(TOP).mapNotNull { q -> byId[q.partyId]?.let { p -> Row(p.id, p.name, HudhudBook.money(q.qarz), null) } }
        return Answer(Mood.CALM, label = label, figure = HudhudBook.money(owed.sumOf { it.qarz }), rows = rows,
            lines = listOf(c.resources.getQuantityString(R.plurals.hudhud_qarz_people, owed.size, owed.size), note),
            open = ZakatActivity::class.java, money = true)
    }

    /**
     * The automations (14 Nov 2026, the owner: "automations ka bhi Hudhud se link"): which are on and at what hour, on
     * this phone. He changes none of them: "Open" shows their own window, where the owner does.
     */
    fun automations(c: Context): Answer {
        val settings = AndroidAutomationSettings(c)
        val words = AndroidWords(c)
        val rows = Automations.Kind.values().map { k ->
            val hour = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, settings.hour(k)); set(java.util.Calendar.MINUTE, 0)
            }.timeInMillis
            Row(0, words.text(k.word), c.getString(if (settings.isOn(k)) R.string.hudhud_auto_on else R.string.hudhud_auto_off),
                if (settings.isOn(k)) c.getString(R.string.auto_at_time, DateWords.format("h:mm a", hour)) else null)
        }
        return Answer(Mood.CALM, label = c.getString(R.string.auto_title), rows = rows,
            lines = listOf(c.getString(R.string.hudhud_auto_line)), automations = true)
    }

    /**
     * A product's fact asked with no product named (14 Nov 2026: "is cheez ka stock kitna hai", "khad kis kis ne li"):
     * he tells which products he can answer for — the month's best sellers first, then the rest — each a tap away.
     */
    suspend fun needGoods(c: Context, dao: KhataDao, facet: Hudhud.GoodsFacet): Answer {
        val products = dao.productsOnce()
        val label = c.getString(R.string.hudhud_need_goods)
        if (products.isEmpty()) {
            return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.no_products)), open = ProductsActivity::class.java)
        }
        val (from, to) = Hudhud.span(VoiceQuestion.Window.THIS_MONTH)
        val best = dao.topProductsBetween(from, to, TOP).map { it.name.lowercase(Locale.ROOT) }
        val ordered = products.sortedWith(compareBy({ best.indexOf(it.name.lowercase(Locale.ROOT)).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.name }))
        val rows = ordered.take(TOP).map { p -> Row(0, p.name, "", null, ask = Hudhud.Ask.Goods(listOf(p.id), facet)) }
        return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_need_goods_line)), rows = rows)
    }

    private const val TOP = 5
    /** Every product's sales in the span (the product asked for is found among them by its name). */
    private const val ALL_PRODUCTS = 100_000
    /** The newest audit rows read for a span's edits and deletes: far more than any shop writes in a day. */
    private const val AUDIT_READ = 5_000
}
