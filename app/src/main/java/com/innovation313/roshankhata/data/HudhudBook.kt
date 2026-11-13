package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.innovation313.roshankhata.AlertsActivity
import com.innovation313.roshankhata.BackupActivity
import com.innovation313.roshankhata.BusinessSwitchActivity
import com.innovation313.roshankhata.CalculatorActivity
import com.innovation313.roshankhata.CashbookActivity
import com.innovation313.roshankhata.ChequesActivity
import com.innovation313.roshankhata.CounterSaleActivity
import com.innovation313.roshankhata.ExpiringActivity
import com.innovation313.roshankhata.FollowUpActivity
import com.innovation313.roshankhata.InsightsActivity
import com.innovation313.roshankhata.InvoicesActivity
import com.innovation313.roshankhata.KhataActivity
import com.innovation313.roshankhata.LanguageActivity
import com.innovation313.roshankhata.OrdersActivity
import com.innovation313.roshankhata.PartyDetailActivity
import com.innovation313.roshankhata.PlansActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.RecycleBinActivity
import com.innovation313.roshankhata.SalesReportActivity
import com.innovation313.roshankhata.SnapshotsActivity
import com.innovation313.roshankhata.StaffActivity
import com.innovation313.roshankhata.screens.ReportPeriod
import com.innovation313.roshankhata.ui.FeatureIntro
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.HudhudPrefs
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.ui.RadarUi
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Hudhud's answers (12 Nov 2026): what each [Hudhud.Ask] comes to, read from the open shop's book by the very rule
 * the screen it names reads it with — the sale by [KhataDao.salesTotalBetween] over the day and the month Insights
 * uses, the profit by [Profit.thisMonth], what is to get and to give by [PartyTotals] as Home adds it up, the drawer
 * by [DayCloseMath.drawerByTheBooks] as Close the day, the backup by the bell's own notes — so Hudhud and the screen
 * he names can never disagree. Every answer only READS. A figure behind the REPORTS right is not shown to a cashier;
 * he is told who can see it instead.
 */
object HudhudBook {

    /** How an answer sits with the shop: [GOOD] news Hudhud is glad of, [CARE] something to see to, else [CALM]. */
    enum class Mood { CALM, GOOD, CARE }

    /**
     * A customer or supplier on the answer card: tapped, his page opens — with the entry form filled in, when the row
     * offers him for an entry ([amount], [isGiven]: "Ali ko 500 diye" with two Alis in the book).
     */
    data class Row(
        val partyId: Long,
        val name: String,
        val figure: String,
        val sub: String?,
        val amount: Double? = null,
        val isGiven: Boolean = true,
        /** A row that is not a customer (a product, a staff member): tapped, this screen opens instead. */
        val open: Class<*>? = null
    )

    data class Answer(
        val mood: Mood,
        /** The small heading over the figure. */
        val label: String? = null,
        /** The one big figure. */
        val figure: String? = null,
        val lines: List<String> = emptyList(),
        val rows: List<Row> = emptyList(),
        /** The screen the answer comes from, opened by the card's button. */
        val open: Class<*>? = null,
        /** A customer's own page instead, by id. */
        val openParty: Long? = null,
        /** What the screen opens on: the sales report's days, the entry form filled in. */
        val openExtras: Bundle? = null,
        /** The button's own words, when "Open" would not say what it does. */
        val openLabel: Int? = null,
        /** The big figure is an amount of money: not said aloud while Home's balance is hidden. */
        val money: Boolean = false,
        /**
         * What to say instead of the card itself, with a context in the voice's language (a help page's first sentences,
         * a greeting, a customer's likely days without the Radar's judgement); null: the whole card is said ([speech]).
         */
        val spoken: ((Context) -> String)? = null,
        /** What the card's button does instead of opening a screen (the sheet's own: download the language). */
        val doIt: (() -> Unit)? = null,
        /** "Did you mean": the questions offered as chips under the card (13 Nov 2026). */
        val options: List<Hudhud.Ask> = emptyList(),
        /** The button opens Hudhud's voice settings (what he calls the owner). */
        val voiceSettings: Boolean = false,
        /** The question this answers: the sheet asks it again in the voice's language when that is another (Roman Urdu). */
        val ask: Hudhud.Ask? = null,
        /**
         * What a sentence nothing fitted exactly was taken as (13 Nov 2026, the owner: "aap ka kaam batana hai, poochhna
         * nahi"): written over the card, so a wrong reading is seen; such an answer is not said aloud.
         */
        val tookAs: String? = null,
        /** Not said aloud: a choice between names, a guess (the owner: "jo doubtful hai us par chup rahe"). */
        val quiet: Boolean = false,
        /** One of his little acts, played on the sheet (13 Nov 2026, the owner: "urr ke dikhao"). */
        val act: Hudhud.Move? = null,
        /** Values the card shows and the voice never says (a phone number, an account number, an email, a tax number). */
        val secrets: List<String> = emptyList()
    )

    /** A help page Hudhud can point to: a question from Help, or a feature's first-open sheet. */
    class Page(val key: String, val title: Int, val body: Int, val open: Class<*>?)

    val PAGES: List<Page> by lazy {
        listOf(
            Page("faq_backup", R.string.faq_q_backup, R.string.faq_a_backup, BackupActivity::class.java),
            Page("faq_restore", R.string.faq_q_restore, R.string.faq_a_restore, BackupActivity::class.java),
            Page("faq_business", R.string.faq_q_business, R.string.faq_a_business, BusinessSwitchActivity::class.java),
            Page("faq_snapshot", R.string.faq_q_snapshot, R.string.faq_a_snapshot, SnapshotsActivity::class.java),
            Page("faq_lock", R.string.faq_q_lock, R.string.faq_a_lock, null),
            Page("faq_hide", R.string.faq_q_hide, R.string.faq_a_hide, null),
            Page("faq_percent", R.string.faq_q_percent, R.string.faq_a_percent, CalculatorActivity::class.java),
            Page("faq_delete", R.string.faq_q_delete, R.string.faq_a_delete, RecycleBinActivity::class.java),
            Page("faq_language", R.string.faq_q_language, R.string.faq_a_language, LanguageActivity::class.java),
            Page("faq_data", R.string.faq_q_data, R.string.faq_a_data, null)
        ) + FeatureIntro.Topic.values().map { Page(FEATURE + it.key, it.title, it.body, screenOf(it)) } + listOf(
            // 13 Nov 2026 (Brain v2): every screen the owner may ask for by name, with its own title and words, so
            // "invoice kahan hai" or "rate list kholo" opens it — those without a first-open sheet of their own.
            Page("screen_khata", R.string.coach_title_nav_khata, R.string.coach_desc_nav_khata, KhataActivity::class.java),
            Page("screen_bills", R.string.coach_title_bills, R.string.coach_desc_bills, com.innovation313.roshankhata.BillsActivity::class.java),
            Page("screen_products", R.string.coach_title_products, R.string.coach_desc_products, com.innovation313.roshankhata.ProductsActivity::class.java),
            Page("screen_insights", R.string.coach_title_insights, R.string.coach_desc_insights, InsightsActivity::class.java),
            Page("screen_card", R.string.coach_title_bizcard, R.string.coach_desc_bizcard, com.innovation313.roshankhata.BusinessCardActivity::class.java),
            Page("screen_calc", R.string.coach_title_calc, R.string.coach_desc_calc, CalculatorActivity::class.java),
            Page("screen_zakat", R.string.tile_zakat, R.string.zakat_subtitle, com.innovation313.roshankhata.ZakatActivity::class.java),
            Page("screen_rates", R.string.rate_list_title, R.string.rate_list_subtitle, com.innovation313.roshankhata.RateListActivity::class.java),
            Page("screen_purchase_rates", R.string.sr_title, R.string.sr_subtitle, com.innovation313.roshankhata.SupplierRatesActivity::class.java),
            Page("screen_cashflow", R.string.cf_title, R.string.cf_subtitle, com.innovation313.roshankhata.CashFlowActivity::class.java),
            Page("screen_sales_report", R.string.sales_report_title, R.string.sales_report_title, SalesReportActivity::class.java),
            Page("screen_posters", R.string.poster_title, R.string.poster_subtitle, com.innovation313.roshankhata.PosterActivity::class.java),
            Page("screen_seasons", R.string.tile_seasons, R.string.season_subtitle, com.innovation313.roshankhata.SeasonsActivity::class.java),
            Page("screen_schemes", R.string.schemes_title, R.string.schemes_subtitle, com.innovation313.roshankhata.SchemesActivity::class.java),
            Page("screen_audit", R.string.audit_title, R.string.audit_subtitle, com.innovation313.roshankhata.AuditActivity::class.java),
            Page("screen_lapsed", R.string.lapsed_title, R.string.lapsed_title, com.innovation313.roshankhata.LapsedActivity::class.java),
            Page("screen_aging", R.string.aging_title, R.string.aging_title, com.innovation313.roshankhata.AgingActivity::class.java),
            Page("screen_bin", R.string.tile_bin, R.string.faq_a_delete, RecycleBinActivity::class.java)
        )
    }

    /** No else: a feature sheet added later does not build until it says which screen Hudhud opens for it. */
    private fun screenOf(topic: FeatureIntro.Topic): Class<*>? = when (topic) {
        FeatureIntro.Topic.COUNTER -> CounterSaleActivity::class.java
        FeatureIntro.Topic.DOCUMENTS -> InvoicesActivity::class.java
        FeatureIntro.Topic.CASHBOOK -> CashbookActivity::class.java
        FeatureIntro.Topic.CHEQUES -> ChequesActivity::class.java
        FeatureIntro.Topic.CUSTOMER_QR -> KhataActivity::class.java
        FeatureIntro.Topic.STAFF -> StaffActivity::class.java
        FeatureIntro.Topic.PLANS -> PlansActivity::class.java
        FeatureIntro.Topic.FOLLOWUP -> FollowUpActivity::class.java
        FeatureIntro.Topic.BACKUP -> BackupActivity::class.java
        FeatureIntro.Topic.ORDERS -> OrdersActivity::class.java
        FeatureIntro.Topic.EXPIRY -> ExpiringActivity::class.java
        FeatureIntro.Topic.RADAR -> FollowUpActivity::class.java
    }

    /**
     * The help pages as Hudhud matches a question against them: each in the app's language and in English, and in
     * both Urdu scripts when the app speaks Urdu (people type Roman Urdu on an Urdu phone, and the other way round).
     */
    fun topics(context: Context): List<Hudhud.Topic> {
        val app = context.resources.configuration.locales[0]
        val tongues = buildList {
            add(context)
            add(localized(context, Locale.ENGLISH))
            if (app.language == "ur") {
                add(localized(context, Locale("ur")))
                add(localized(context, Locale.forLanguageTag("ur-Latn")))
            }
        }
        return PAGES.map { p ->
            Hudhud.Topic(
                p.key,
                tongues.joinToString(" ") { it.getString(p.title) },
                tongues.joinToString(" ") { it.getString(p.body) },
                // A feature's or a screen's title is never a customer's name; a question's may be (محفوظ, "keep").
                feature = !p.key.startsWith("faq_")
            )
        }
    }

    private fun localized(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /** What Hudhud says when his sheet opens. */
    fun hello(context: Context) = Answer(
        Mood.CALM,
        label = greeting(context),
        lines = listOf(context.getString(R.string.hudhud_hello)),
        spoken = { w -> greeting(w) + " " + w.getString(R.string.hudhud_hello) }
    )

    /**
     * His own introduction when his face is tapped (13 Nov 2026, the owner: "tap karein to wo bole, main Hudhud hoon,
     * aap mujh se poochhein"): who he is and to ask; the day's first time with the greeting and the owner's name.
     */
    fun intro(context: Context, greet: Boolean) = Answer(
        Mood.CALM,
        label = greeting(context),
        lines = listOf(context.getString(R.string.hudhud_intro)),
        spoken = { w -> (if (greet) greeting(w) + " " else "") + w.getString(R.string.hudhud_intro) }
    )

    /** What he says when the book could not be read. */
    fun failed(context: Context) = Answer(Mood.CARE, lines = listOf(context.getString(R.string.hudhud_unknown)))

    suspend fun answer(context: Context, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): Answer =
        answerOf(context, dao, ask, parties).let { if (it.ask == null) it.copy(ask = ask) else it }

    private suspend fun answerOf(context: Context, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): Answer =
        when (ask) {
            is Hudhud.Ask.Figure -> figure(context, dao, ask.kind, parties)
            is Hudhud.Ask.Period -> period(context, dao, ask.measure, ask.window)
            is Hudhud.Ask.Moved -> moved(context, dao, ask.ids, ask.window, ask.got, parties)
            is Hudhud.Ask.Entry -> entry(context, ask, parties)
            is Hudhud.Ask.WhenPays -> whenPays(context, dao, ask.ids, parties)
            Hudhud.Ask.Today -> today(context, dao)
            is Hudhud.Ask.Parties -> named(context, ask.ids, parties)
            is Hudhud.Ask.Help -> help(context, ask.key)
            Hudhud.Ask.BackupStatus -> backup(context)
            Hudhud.Ask.Drawer -> drawer(context, dao)
            Hudhud.Ask.Salaam -> Answer(
                Mood.GOOD,
                lines = listOf(addressed(context, R.string.hudhud_salaam_reply), context.getString(R.string.hudhud_hello))
            )
            Hudhud.Ask.Hello -> Answer(Mood.CALM, lines = listOf(context.getString(R.string.hudhud_hello)))
            Hudhud.Ask.Abilities -> abilities(context)
            is Hudhud.Ask.Chat -> chat(context, ask)
            Hudhud.Ask.Unknown -> unknown(context)
            // Brain v2 (13 Nov 2026): the wider knowledge, each by its screen's own rule (HudhudFacts).
            is Hudhud.Ask.Person -> HudhudFacts.person(context, dao, ask.ids, ask.facet, parties)
            is Hudhud.Ask.Goods -> HudhudFacts.goods(context, dao, ask.ids, ask.facet, ask.window)
            is Hudhud.Ask.Shop -> HudhudFacts.shop(context, ask.facet)
            is Hudhud.Ask.About -> HudhudFacts.about(context, ask.topic)
            Hudhud.Ask.Clock -> HudhudFacts.clock(context)
            is Hudhud.Ask.Count -> HudhudFacts.count(context, dao, ask.thing, ask.window, parties)
            is Hudhud.Ask.Status -> HudhudFacts.status(context, dao, ask.of, parties)
            is Hudhud.Ask.Deliveries -> HudhudFacts.deliveries(context, dao, ask.window)
            is Hudhud.Ask.Activity -> HudhudFacts.activity(context, dao, ask.window)
            is Hudhud.Ask.Collected -> HudhudFacts.collected(context, dao, ask.window)
            is Hudhud.Ask.NeedName -> HudhudFacts.needName(context, dao, ask.facet, parties)
            is Hudhud.Ask.Cashbook -> HudhudFacts.cashbook(context, dao, ask.window)
            is Hudhud.Ask.Returns -> HudhudFacts.returns(context, dao, ask.window)
            is Hudhud.Ask.Act -> act(context, ask.move)
            // Tell, never ask (13 Nov 2026): a guess is answered, with what it was taken as over the card; "did you
            // mean" answers its first meaning the same way and offers the others as chips.
            is Hudhud.Ask.Guessed -> guessed(context, dao, ask.ask, parties, emptyList())
            is Hudhud.Ask.DidYouMean -> ask.options.firstOrNull()?.let { guessed(context, dao, it, parties, ask.options.drop(1)) }
                ?: unknown(context)
        }

    /** [ask] answered as a guess: the card says what it was taken as, the [others] are chips, and nothing is said aloud. */
    private suspend fun guessed(
        context: Context, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>, others: List<Hudhud.Ask>
    ): Answer {
        val inner = answer(context, dao, ask, parties)
        val took = chipLabel(context, ask) ?: inner.label ?: ""
        return inner.copy(
            tookAs = context.getString(R.string.hudhud_took_as, Format.isolate(took)),
            quiet = true,
            options = others,
            ask = ask
        )
    }

    /** One of his acts: the sheet plays it; the card says it in a word. */
    private fun act(context: Context, move: Hudhud.Move): Answer = Answer(
        Mood.GOOD,
        lines = listOf(context.getString(
            when (move) {
                Hudhud.Move.FLY -> R.string.hudhud_act_fly
                Hudhud.Move.DANCE -> R.string.hudhud_act_dance
                Hudhud.Move.JUMP -> R.string.hudhud_act_jump
            }
        )),
        act = move
    )

    /**
     * What he can tell (13 Nov 2026, the owner: "aap kya bata sakte ho" — the list): what this person may see of it,
     * as lines; the chips under the card ask each of them in a tap. Said aloud, one sentence pointing at the list.
     */
    private fun abilities(context: Context): Answer {
        val reports = Access.can(Access.Permission.REPORTS)
        val lines = ArrayList<String>()
        lines += context.getString(R.string.hudhud_can_intro)
        if (reports) {
            lines += "\u2022 " + context.getString(R.string.hudhud_can_dues)
            lines += "\u2022 " + context.getString(R.string.hudhud_can_sales)
            lines += "\u2022 " + context.getString(R.string.hudhud_can_drawer)
            if (RadarUi.on(context)) lines += "\u2022 " + context.getString(R.string.hudhud_can_pay)
        }
        if (!ViewerMode.isOn(context)) lines += "\u2022 " + context.getString(R.string.hudhud_can_entry)
        // 13 Nov 2026 (Brain v2): a customer's facts, goods, what happened, the shop and the day.
        lines += "\u2022 " + context.getString(R.string.hudhud_can_people)
        lines += "\u2022 " + context.getString(R.string.hudhud_can_goods)
        if (reports) lines += "\u2022 " + context.getString(R.string.hudhud_can_book)
        lines += "\u2022 " + context.getString(R.string.hudhud_can_shop)
        lines += "\u2022 " + context.getString(R.string.hudhud_can_more)
        lines += "\u2022 " + context.getString(R.string.hudhud_can_chat)
        return Answer(Mood.GOOD, lines = lines)
    }

    /**
     * A little talk (13 Nov 2026, the owner: haal chaal, "bade pyaar se"): a warm short reply that turns back to the
     * shop. A salaam with it is answered first.
     */
    private fun chat(context: Context, ask: Hudhud.Ask.Chat): Answer {
        fun words(w: Context): String {
            val reply = when (ask.talk) {
                Hudhud.Talk.HOW_ARE_YOU -> w.getString(R.string.hudhud_chat_how)
                Hudhud.Talk.WHO_ARE_YOU -> w.getString(R.string.hudhud_chat_who)
                Hudhud.Talk.THANKS -> w.getString(R.string.hudhud_chat_thanks)
                Hudhud.Talk.BYE -> w.getString(R.string.hudhud_chat_bye)
                Hudhud.Talk.PRAISE -> w.getString(R.string.hudhud_chat_praise)
                Hudhud.Talk.FINE -> w.getString(R.string.hudhud_chat_fine)
                Hudhud.Talk.CAN_TALK -> w.getString(R.string.hudhud_chat_can_talk)
                // Only called ("bhai jaan", "Hudhud"): he answers to it, with the owner's name when he gave one.
                Hudhud.Talk.ADDRESS -> addressed(w, R.string.hudhud_chat_address)
            }
            return (if (ask.salaam) addressed(w, R.string.hudhud_salaam_reply) + " " else "") + reply
        }
        val mood = when (ask.talk) {
            Hudhud.Talk.WHO_ARE_YOU, Hudhud.Talk.BYE, Hudhud.Talk.ADDRESS, Hudhud.Talk.CAN_TALK -> Mood.CALM
            else -> Mood.GOOD
        }
        return Answer(mood, lines = listOf(words(context)), spoken = { w -> words(w) })
    }

    /**
     * A reply that turns to the owner by the name he gave ("Ji, Ali bhai. Farmaiye.", 13 Nov 2026); the words alone
     * when he gave none. [words] has the name's place as its first argument, and a form of its own without it.
     */
    private fun addressed(w: Context, words: Int): String {
        val name = HudhudPrefs.callMe(w) ?: return w.getString(words)
        val named = when (words) {
            R.string.hudhud_chat_address -> R.string.hudhud_chat_address_named
            R.string.hudhud_salaam_reply -> R.string.hudhud_salaam_reply_named
            else -> return w.getString(words)
        }
        return w.getString(named, Format.isolate(name))
    }

    /**
     * What he does not know: one line, said whole, that the nearest questions he can answer are the chips under it
     * ([Hudhud.suggest], from the words he was given).
     */
    internal fun unknown(context: Context) = Answer(Mood.CALM, lines = listOf(context.getString(R.string.hudhud_unknown)))

    /** The words each of VoiceQuestion's figures goes by. */
    fun labelOf(kind: VoiceQuestion.Kind): Int = when (kind) {
        VoiceQuestion.Kind.PROFIT_MONTH -> R.string.vq_profit_month
        VoiceQuestion.Kind.EXPIRING -> R.string.vq_expiring
        VoiceQuestion.Kind.NOT_PAID -> R.string.vq_not_paid
        VoiceQuestion.Kind.WHO_OWES_MOST -> R.string.vq_who_owes_most
        VoiceQuestion.Kind.SALES_TODAY -> R.string.vq_sales_today
        VoiceQuestion.Kind.SALES_MONTH -> R.string.vq_sales_month
        VoiceQuestion.Kind.TO_GIVE -> R.string.vq_to_give
        VoiceQuestion.Kind.TO_GET -> R.string.vq_to_get
    }

    // ---------------------------------------------------------------------------------------------- the figures

    private suspend fun figure(
        context: Context, dao: KhataDao, kind: VoiceQuestion.Kind, parties: List<PartyWithBalance>
    ): Answer {
        val labelRes = labelOf(kind)
        val label = context.getString(labelRes)
        if (!Access.can(Access.Permission.REPORTS)) {
            return Answer(Mood.CALM, label = label, lines = listOf(context.getString(R.string.hudhud_owner_only)))
        }
        // The day and the month exactly as Insights' own voice answers took them.
        val c = Calendar.getInstance().apply { timeInMillis = DayMath.startOfDay(System.currentTimeMillis()) }
        val dayStart = c.timeInMillis
        val dayEnd = (c.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        c.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = c.timeInMillis
        val monthEnd = (c.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
        return when (kind) {
            // Opened on Insights, where today's sale and this month's stand by the same sum and windows; the sales
            // report opens on a period of its own and could show another figure.
            VoiceQuestion.Kind.SALES_TODAY -> sum(labelRes, label, dao.salesTotalBetween(dayStart, dayEnd), InsightsActivity::class.java)
            VoiceQuestion.Kind.SALES_MONTH -> sum(labelRes, label, dao.salesTotalBetween(monthStart, monthEnd), InsightsActivity::class.java)
            VoiceQuestion.Kind.TO_GET -> sum(labelRes, label, PartyTotals.of(parties.map { it.balance }).get, KhataActivity::class.java, calm = true)
            VoiceQuestion.Kind.TO_GIVE -> sum(labelRes, label, PartyTotals.of(parties.map { it.balance }).give, KhataActivity::class.java, calm = true)
            VoiceQuestion.Kind.PROFIT_MONTH -> profit(context, labelRes, label, Profit.thisMonth(dao), InsightsActivity::class.java)
            VoiceQuestion.Kind.WHO_OWES_MOST -> {
                val top = parties.filter { Money.isPositive(it.balance) }.sortedByDescending { it.balance }.take(TOP)
                if (top.isEmpty()) {
                    Answer(Mood.GOOD, label = label, lines = listOf(context.getString(R.string.vq_nobody)))
                } else {
                    val first = top.first()
                    Answer(
                        Mood.CALM, label = label,
                        rows = top.map { Row(it.id, it.name, money(it.balance), null) },
                        open = KhataActivity::class.java, money = true
                    )
                }
            }
            VoiceQuestion.Kind.NOT_PAID -> Answer(Mood.CALM, label = label,
                lines = listOf(context.getString(R.string.vq_not_paid_body)), open = FollowUpActivity::class.java)
            VoiceQuestion.Kind.EXPIRING -> Answer(Mood.CALM, label = label,
                lines = listOf(context.getString(R.string.vq_expiring_body)), open = ExpiringActivity::class.java)
        }
    }

    /** A profit with its heading, as Insights says it: its share of the sales it stands on, or that none is known. */
    private fun profit(context: Context, labelRes: Int, label: String, r: Profit.ProfitReport, open: Class<*>?): Answer =
        if (r.isEmpty) {
            Answer(Mood.CALM, label = label, lines = listOf(context.getString(R.string.insights_profit_none)), open = open)
        } else {
            Answer(
                if (r.grossProfit < 0) Mood.CARE else Mood.GOOD,
                label = label,
                figure = money(r.grossProfit),
                lines = listOfNotNull(r.coveragePercent?.let { Digits.string(context.resources, R.string.insights_profit_coverage, it) }),
                open = open,
                money = true
            )
        }

    // ------------------------------------------------------------------------------------------ spans of days

    /**
     * A sale or a profit over a span of days (12 Nov 2026): the sale by [KhataDao.salesTotalBetween], opened on the
     * sales report over the very same days (its revenue is that sum: SalesRuleTest); the profit by Insights' own rule
     * ([Profit.between]), opened on Insights where it shows the figure (this month's and last month's) — a day's or a
     * week's profit is on no screen, so that card has no button.
     */
    private suspend fun period(context: Context, dao: KhataDao, measure: Hudhud.Measure, window: VoiceQuestion.Window): Answer {
        val labelRes = periodLabel(measure, window)
        val label = context.getString(labelRes)
        if (!Access.can(Access.Permission.REPORTS)) {
            return Answer(Mood.CALM, label = label, lines = listOf(context.getString(R.string.hudhud_owner_only)))
        }
        val (from, to) = Hudhud.span(window)
        return when (measure) {
            Hudhud.Measure.SALES -> sum(labelRes, label, dao.salesTotalBetween(from, to), SalesReportActivity::class.java)
                .copy(openExtras = salesReportDays(window, from, to))
            Hudhud.Measure.PROFIT -> when (window) {
                VoiceQuestion.Window.THIS_MONTH -> profit(context, labelRes, label, Profit.thisMonth(dao), InsightsActivity::class.java)
                VoiceQuestion.Window.LAST_MONTH -> profit(context, labelRes, label, Profit.lastMonth(dao), InsightsActivity::class.java)
                else -> profit(context, labelRes, label, Profit.between(dao, from, to), null)
            }
        }
    }

    /** The sales report's own period for a span, or its first and last day as the range picker gives them. */
    private fun salesReportDays(window: VoiceQuestion.Window, from: Long, to: Long): Bundle = Bundle().apply {
        when (window) {
            VoiceQuestion.Window.TODAY -> putString(SalesReportActivity.EXTRA_PERIOD, ReportPeriod.TODAY.name)
            VoiceQuestion.Window.THIS_WEEK -> putString(SalesReportActivity.EXTRA_PERIOD, ReportPeriod.WEEK.name)
            VoiceQuestion.Window.THIS_MONTH -> putString(SalesReportActivity.EXTRA_PERIOD, ReportPeriod.MONTH.name)
            else -> {
                putLong(SalesReportActivity.EXTRA_FIRST_DAY, from)
                putLong(SalesReportActivity.EXTRA_LAST_DAY, DayMath.startOfDay(to - 1))
            }
        }
    }

    /** The heading of a sale or a profit over a span. */
    internal fun periodLabel(measure: Hudhud.Measure, window: VoiceQuestion.Window): Int = when (measure) {
        Hudhud.Measure.SALES -> when (window) {
            VoiceQuestion.Window.TODAY -> R.string.vq_sales_today
            VoiceQuestion.Window.YESTERDAY -> R.string.hudhud_sales_yesterday
            VoiceQuestion.Window.THIS_WEEK -> R.string.hudhud_sales_week
            VoiceQuestion.Window.LAST_WEEK -> R.string.hudhud_sales_last_week
            VoiceQuestion.Window.THIS_MONTH -> R.string.vq_sales_month
            VoiceQuestion.Window.LAST_MONTH -> R.string.hudhud_sales_last_month
        }
        Hudhud.Measure.PROFIT -> when (window) {
            VoiceQuestion.Window.TODAY -> R.string.hudhud_profit_today
            VoiceQuestion.Window.YESTERDAY -> R.string.hudhud_profit_yesterday
            VoiceQuestion.Window.THIS_WEEK -> R.string.hudhud_profit_week
            VoiceQuestion.Window.LAST_WEEK -> R.string.hudhud_profit_last_week
            VoiceQuestion.Window.THIS_MONTH -> R.string.vq_profit_month
            VoiceQuestion.Window.LAST_MONTH -> R.string.hudhud_profit_last_month
        }
    }

    /** A span of days in the date filter's own words. */
    internal fun windowWord(window: VoiceQuestion.Window): Int = when (window) {
        VoiceQuestion.Window.TODAY -> R.string.range_today
        VoiceQuestion.Window.YESTERDAY -> R.string.range_yesterday
        VoiceQuestion.Window.THIS_WEEK -> R.string.range_this_week
        VoiceQuestion.Window.LAST_WEEK -> R.string.range_week
        VoiceQuestion.Window.THIS_MONTH -> R.string.range_this_month
        VoiceQuestion.Window.LAST_MONTH -> R.string.range_month
    }

    /**
     * What one customer paid ([got]) or took over a span ("Ali ne kal kitna diya"): his own live lines in it, I got or
     * I gave, every one as his page shows it (a paid-now sale's payment and a loan included: money that moved).
     */
    private suspend fun moved(
        context: Context, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window, got: Boolean,
        parties: List<PartyWithBalance>
    ): Answer {
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return unknown(context)
        if (found.size > 1) return pick(context, found)
        val p = found.first()
        val (from, to) = Hudhud.span(window)
        val total = LedgerLines.of(dao, listOf(p.id))
            .filter { it.timestamp >= from && it.timestamp < to && it.isGiven == !got }
            .sumOf { it.amount }
        val way = if (got) R.string.i_got else R.string.i_gave
        return Answer(
            Mood.CALM,
            label = p.name,
            figure = money(total),
            lines = listOf(context.getString(way) + " · " + context.getString(windowWord(window))),
            openParty = p.id,
            money = true
        )
    }

    // ---------------------------------------------------------------------------------------------- an entry

    /**
     * An entry said as a fact ("Ali ko 500 diye"): nothing is written here. The card says what was understood — who,
     * how much, which way — and its button opens his page with the form filled in, where every check the form makes
     * (the credit limit above all) still runs and the owner presses Save, as voice entry does. When the words could be
     * several customers, or none, each is offered nearest first; a read-only phone is told it cannot write.
     */
    private fun entry(context: Context, ask: Hudhud.Ask.Entry, parties: List<PartyWithBalance>): Answer {
        val way = context.getString(if (ask.isGiven) R.string.i_gave else R.string.i_got)
        if (ViewerMode.isOn(context)) {
            return Answer(Mood.CALM, label = way, figure = money(ask.amount), lines = listOf(context.getString(R.string.viewer_refused)))
        }
        if (parties.isEmpty()) {
            return Answer(Mood.CALM, label = way, figure = money(ask.amount),
                lines = listOf(context.getString(R.string.voice_no_party)), open = KhataActivity::class.java)
        }
        val found = ask.ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.size == 1) {
            val p = found.first()
            return Answer(
                Mood.CALM,
                label = p.name,
                figure = money(ask.amount),
                lines = listOf(way, context.getString(R.string.hudhud_entry_check)),
                openParty = p.id,
                openExtras = entryForm(ask.amount, ask.isGiven),
                openLabel = R.string.hudhud_fill_entry,
                money = true
            )
        }
        // As voice entry's own picker: the names the words recognise, else the whole book nearest the words first
        // (recent dealings first among equals); one tap opens the form filled in for the one chosen.
        val choices = found.ifEmpty {
            NameSearch.rankSpoken(parties.sortedByDescending { it.lastActivity }, ask.nameWords) { it.name }.take(PICK)
        }
        return Answer(
            Mood.CALM,
            label = context.getString(R.string.voice_which_party, Format.moneyLtr(ask.amount)),
            lines = listOf(way),
            rows = choices.map {
                Row(it.id, it.name, money(abs(it.balance)), side(context, it.balance), amount = ask.amount, isGiven = ask.isGiven)
            },
            money = true, quiet = true
        )
    }

    /** What opens the customer's entry form filled in: voice entry's own extras. */
    fun entryForm(amount: Double, isGiven: Boolean): Bundle = Bundle().apply {
        putDouble(PartyDetailActivity.EXTRA_VOICE_AMOUNT, amount)
        putBoolean(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, isGiven)
    }

    // ------------------------------------------------------------------------------------- when he will pay

    /**
     * "Ali kab dega?" by the Payment Radar, by its own rules: only for the owner and a manager (REPORTS), only where
     * the Radar is on (never on a helper's phone), only for customers; the reading from his own lines, his promised day
     * and the promises he was given (PromiseLog), against the shop's normal — the reasons RadarUi gives on his page. He
     * says aloud only the likely days, never the judgement: a customer may be standing at the counter. With no name,
     * who is in his usual paying days today (the morning list's rule).
     */
    private suspend fun whenPays(context: Context, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): Answer {
        val label = context.getString(R.string.radar_title)
        if (!Access.can(Access.Permission.REPORTS)) {
            return Answer(Mood.CALM, label = label, lines = listOf(context.getString(R.string.hudhud_owner_only)))
        }
        if (!RadarUi.on(context)) return Answer(Mood.CALM, label = label, lines = listOf(context.getString(R.string.radar_state_off)))
        val now = System.currentTimeMillis()
        if (ids.isEmpty()) {
            val promises = dao.observePromises().first().associate { it.partyId to it.due }
            val likely = PaymentRadar.likelyNow(LedgerLines.owingOf(dao, parties), RadarUi.shopDays(dao), promises, now)
            val rows = parties.filter { it.id in likely }.sortedByDescending { it.balance }.take(TOP)
            val heading = context.getString(R.string.radar_likely_now)
            if (rows.isEmpty()) {
                return Answer(Mood.CALM, label = heading, lines = listOf(context.getString(R.string.hudhud_likely_none)),
                    open = FollowUpActivity::class.java)
            }
            return Answer(
                Mood.GOOD, label = heading,
                rows = rows.map { Row(it.id, it.name, money(it.balance), null) },
                open = FollowUpActivity::class.java
            )
        }
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return unknown(context)
        if (found.size > 1) return pick(context, found)
        val p = found.first()
        if (!p.isCustomer) {
            return Answer(Mood.CALM, label = p.name, figure = money(abs(p.balance)),
                lines = listOf(side(context, p.balance), context.getString(R.string.hudhud_radar_customers)),
                openParty = p.id, money = true)
        }
        val promise = dao.getParty(p.id)?.harvestPromise
        val promises = runCatching { PromiseLog.of(dao.promiseLog(p.id)) }.getOrDefault(emptyList())
        val r = PaymentRadar.read(LedgerLines.of(dao, listOf(p.id)), now, RadarUi.shopDays(dao), promise, promises)
        val from = r.nextFrom
        val to = r.nextTo
        fun likely(w: Context): String? =
            if (from != null && to != null) w.getString(R.string.radar_next_short, shortDay(from), shortDay(to)) else null
        val mood = when (r.light) {
            PaymentRadar.Light.GREEN -> Mood.GOOD
            PaymentRadar.Light.AMBER, PaymentRadar.Light.RED -> Mood.CARE
            PaymentRadar.Light.NONE -> Mood.CALM
        }
        return Answer(
            mood,
            label = p.name,
            lines = listOfNotNull(likely(context), RadarUi.reasons(context, r, promise, RadarUi.toneThatWorked(context, p.id, r.balance))),
            openParty = p.id,
            spoken = { w ->
                p.name + ": " + (likely(w) ?: w.getString(if (r.light == PaymentRadar.Light.NONE) R.string.radar_new else R.string.hudhud_radar_see))
            }
        )
    }

    private fun shortDay(t: Long) = DateWords.format("d MMM", t)

    /** Several customers the words could be: each offered, his page a tap away. */
    internal fun pick(context: Context, found: List<PartyWithBalance>) = Answer(
        Mood.CALM,
        label = context.getString(R.string.hudhud_pick_party),
        rows = found.map { Row(it.id, it.name, money(abs(it.balance)), side(context, it.balance)) }, quiet = true
    )

    // ------------------------------------------------------------------------------------------------ today

    /**
     * What needs the owner today, under the morning's greeting: whom to remind (Follow-up's own rule, the 9 o'clock
     * notice's: [Automations.collect]; the first three named, with who is in his usual paying days where the Radar is
     * the owner's to see) and the bell's own rows (HomeAlerts, less "the app was updated"). Said aloud, it is the
     * greeting and one line of counts — never an amount.
     */
    private suspend fun today(context: Context, dao: KhataDao): Answer {
        val now = System.currentTimeMillis()
        val radar = Access.can(Access.Permission.REPORTS) && RadarUi.on(context)
        val collect = if (ViewerMode.isOn(context)) null
        else runCatching { Automations.collect(dao, ReminderLog.openedToday(context, now), now, radar = radar) }.getOrNull()
        val alerts = runCatching { HomeAlerts.compute(context, dao) { Format.money(it) } }.getOrDefault(emptyList())
            .filter { it.kind != HomeAlerts.Kind.UPDATED }
        val remind = collect?.count ?: 0
        val lines = ArrayList<String>()
        if (remind > 0) lines += Digits.quantity(context.resources, R.plurals.hudhud_greet_remind, remind, remind)
        alerts.forEach { lines += "• " + it.title }
        if (lines.isEmpty()) lines += context.getString(R.string.hudhud_greet_clear)
        val likely = collect?.likelyIds.orEmpty()
        val rows = collect?.top.orEmpty().map {
            Row(it.id, it.name, money(it.balance), if (it.id in likely) context.getString(R.string.radar_likely_now) else null)
        }
        return Answer(
            if (remind == 0 && alerts.isEmpty()) Mood.GOOD else Mood.CALM,
            label = greeting(context, now),
            lines = lines,
            rows = rows,
            open = when {
                remind > 0 -> FollowUpActivity::class.java
                alerts.isNotEmpty() -> AlertsActivity::class.java
                else -> null
            },
            spoken = { w -> greeting(w, now) + " " + headline(w, remind, alerts.size) }
        )
    }

    /**
     * The morning bubble's line on Home (counts only, never an amount: balances may be hidden): whom to remind today,
     * else how many rows the bell has, else that nothing is waiting. [bell] is the bell's own count.
     */
    suspend fun greetingLine(context: Context, dao: KhataDao, bell: Int): String {
        val now = System.currentTimeMillis()
        val remind = if (ViewerMode.isOn(context)) 0
        else runCatching { Automations.collect(dao, ReminderLog.openedToday(context, now), now).count }.getOrDefault(0)
        return headline(context, remind, bell)
    }

    private fun headline(context: Context, remind: Int, bell: Int): String = when {
        remind > 0 -> Digits.quantity(context.resources, R.plurals.hudhud_greet_remind, remind, remind)
        bell > 0 -> Digits.quantity(context.resources, R.plurals.hudhud_greet_alerts, bell, bell)
        else -> context.getString(R.string.hudhud_greet_clear)
    }

    /**
     * The greeting by the shop's country and the hour ([Hudhud.greeting], the owner's rule), with what the owner asked
     * to be called when he gave it ("Assalam-o-Alaikum, Ali bhai!", 13 Nov 2026).
     */
    fun greeting(context: Context, now: Long = System.currentTimeMillis()): String {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val word = context.getString(
            when (Hudhud.greeting(BusinessProfile.country(context), hour)) {
                Hudhud.Greeting.SALAAM -> R.string.hudhud_greet_salaam
                Hudhud.Greeting.MORNING -> R.string.hudhud_greet_morning
                Hudhud.Greeting.AFTERNOON -> R.string.hudhud_greet_afternoon
                Hudhud.Greeting.EVENING -> R.string.hudhud_greet_evening
            }
        )
        val name = HudhudPrefs.callMe(context) ?: return word
        return context.getString(R.string.hudhud_greet_named, word.trimEnd('!', '\uFF01', ' '), Format.isolate(name))
    }

    /** An amount with its heading: glad of more than nothing unless it is what the shop owes ([calm]). */
    private fun sum(labelRes: Int, label: String, value: Double, open: Class<*>, calm: Boolean = false) = Answer(
        if (!calm && Money.isPositive(value)) Mood.GOOD else Mood.CALM,
        label = label,
        figure = money(value),
        open = open,
        money = true
    )

    // -------------------------------------------------------------------------------------- customers by name

    private fun named(context: Context, ids: List<Long>, parties: List<PartyWithBalance>): Answer {
        val byId = parties.associateBy { it.id }
        val found = ids.mapNotNull { byId[it] }
        if (found.isEmpty()) return unknown(context)
        if (found.size == 1) {
            val p = found.first()
            return Answer(
                Mood.CALM,
                label = p.name,
                figure = money(abs(p.balance)),
                lines = listOf(side(context, p.balance)),
                openParty = p.id,
                money = true
            )
        }
        return pick(context, found)
    }

    /** Which way a balance runs, in the Khata list's own words. */
    internal fun side(context: Context, balance: Double): String = context.getString(
        when {
            Money.isPositive(balance) -> R.string.you_will_get
            Money.isNegative(balance) -> R.string.you_will_give
            else -> R.string.settled
        }
    )

    // ------------------------------------------------------------------------------------- backup and drawer

    /** The backup as the bell judges it: another phone's, a failed one, none, a week old, or in order. */
    private fun backup(context: Context): Answer {
        val label = context.getString(R.string.coach_title_backup)
        val last = BackupReminder.lastBackupAt(context)
        val other = AlertNotes.otherPhoneAt(context)
        val failed = AlertNotes.backupFailedAt(context)
        fun care(title: String, sub: String) = Answer(
            Mood.CARE, label = label, lines = listOf(title, sub), open = BackupActivity::class.java
        )
        return when {
            other > last -> care(context.getString(R.string.notif_backup_other_phone_title), context.getString(R.string.na_backup_other_phone_sub))
            failed > last -> care(context.getString(R.string.notif_backup_failed_title), context.getString(R.string.na_backup_failed_sub))
            last == 0L -> care(context.getString(R.string.na_backup_never), context.getString(R.string.na_backup_sub))
            else -> {
                val days = ((System.currentTimeMillis() - last) / DAY_MS).toInt()
                if (days >= OLD_BACKUP_DAYS) {
                    care(context.getString(R.string.na_backup, days.toString()), context.getString(R.string.na_backup_sub))
                } else {
                    val line = context.getString(R.string.business_last_backup, Format.dateOnly(last))
                    Answer(Mood.GOOD, label = label, lines = listOf(line), open = BackupActivity::class.java)
                }
            }
        }
    }

    /**
     * What should be in the drawer now, by Close the day's own rule, and since when it is counted. Behind REPORTS like
     * every figure: told to a cashier before he counts, the expected cash says how much could go missing unseen.
     */
    private suspend fun drawer(context: Context, dao: KhataDao): Answer {
        if (!Access.can(Access.Permission.REPORTS)) {
            return Answer(Mood.CALM, label = context.getString(R.string.z_expected),
                lines = listOf(context.getString(R.string.hudhud_owner_only)))
        }
        val f = DayCloseMath.drawerByTheBooks(dao)
        val last = f.last
        val since = if (last == null) context.getString(R.string.dayclose_since_first)
        else context.getString(R.string.dayclose_since_last, Format.dateTime(last.closedAt))
        val expected = f.expected
        return Answer(
            Mood.CALM,
            label = context.getString(R.string.z_expected),
            figure = money(expected),
            lines = listOf(since),
            open = CashbookActivity::class.java,
            money = true
        )
    }

    // ---------------------------------------------------------------------------------------------- help

    private fun help(context: Context, key: String): Answer {
        val page = PAGES.firstOrNull { it.key == key } ?: return unknown(context)
        return Answer(
            Mood.CALM,
            label = context.getString(page.title),
            lines = listOf(context.getString(page.body)),
            open = page.open,
            // Said in full when short, else its first sentences and that the rest is on the screen (13 Nov 2026).
            spoken = { w -> HudhudSpeech.upTo(w.getString(page.body), SAY_HELP, w.getString(R.string.hudhud_more_on_screen)) }
        )
    }

    // ------------------------------------------------------------------------------------------ chips

    /** A chip's words for [ask] (13 Nov 2026: next steps, "did you mean", the day's own questions); null: no chip. */
    fun chipLabel(c: Context, ask: Hudhud.Ask): String? = when (ask) {
        is Hudhud.Ask.Figure -> c.getString(labelOf(ask.kind))
        is Hudhud.Ask.Period -> c.getString(periodLabel(ask.measure, ask.window))
        Hudhud.Ask.Abilities -> c.getString(R.string.hudhud_chip_abilities)
        Hudhud.Ask.Today -> c.getString(R.string.hudhud_chip_today)
        Hudhud.Ask.BackupStatus -> c.getString(R.string.hudhud_chip_backup)
        Hudhud.Ask.Drawer -> c.getString(R.string.hudhud_chip_drawer)
        is Hudhud.Ask.WhenPays -> c.getString(if (ask.ids.isEmpty()) R.string.hudhud_chip_likely else R.string.hudhud_chip_when_pays)
        is Hudhud.Ask.Chat -> if (ask.talk == Hudhud.Talk.WHO_ARE_YOU) c.getString(R.string.hudhud_chip_who) else null
        is Hudhud.Ask.About -> when (ask.topic) {
            Hudhud.AboutTopic.OWNER_NAME -> c.getString(R.string.hudhud_chip_my_name)
            Hudhud.AboutTopic.MAKER -> c.getString(R.string.hudhud_chip_maker)
            Hudhud.AboutTopic.PRIVACY -> c.getString(R.string.hudhud_chip_privacy)
            else -> null
        }
        is Hudhud.Ask.Shop -> when (ask.facet) {
            Hudhud.ShopFacet.NAME -> c.getString(R.string.business_name)
            Hudhud.ShopFacet.PHONE -> c.getString(R.string.hudhud_chip_shop_phone)
            Hudhud.ShopFacet.ALL -> c.getString(R.string.hudhud_chip_shop)
            else -> null
        }
        is Hudhud.Ask.NeedName -> c.getString(R.string.hudhud_chip_someone)
        is Hudhud.Ask.Cashbook -> c.getString(R.string.hudhud_chip_cashbook)
        is Hudhud.Ask.Returns -> c.getString(R.string.hudhud_chip_returns)
        is Hudhud.Ask.Guessed -> chipLabel(c, ask.ask)
        Hudhud.Ask.Clock -> c.getString(R.string.hudhud_chip_date)
        is Hudhud.Ask.Parties -> if (ask.ids.size == 1) c.getString(R.string.hudhud_l_balance) else null
        is Hudhud.Ask.Person -> c.getString(
            when (ask.facet) {
                Hudhud.Facet.PHONE -> R.string.hudhud_l_phone
                Hudhud.Facet.DETAILS -> R.string.hudhud_chip_details
                Hudhud.Facet.LAST_PAYMENT -> R.string.hudhud_l_last_payment
                Hudhud.Facet.LAST_ENTRY -> R.string.hudhud_l_last_entry
                Hudhud.Facet.SINCE -> R.string.hudhud_l_since
                Hudhud.Facet.LIMIT -> R.string.credit_limit
                Hudhud.Facet.PROMISE -> R.string.hudhud_l_promise
                Hudhud.Facet.ENTRIES -> R.string.hudhud_l_entries
            }
        )
        is Hudhud.Ask.Goods -> c.getString(
            when (ask.facet) {
                Hudhud.GoodsFacet.OVERVIEW -> R.string.hudhud_chip_details
                Hudhud.GoodsFacet.PRICE -> R.string.hudhud_l_price
                Hudhud.GoodsFacet.STOCK -> R.string.hudhud_l_stock
                Hudhud.GoodsFacet.COST -> R.string.hudhud_l_cost
                Hudhud.GoodsFacet.SUPPLIERS -> R.string.hudhud_chip_suppliers
                Hudhud.GoodsFacet.LAST_IN -> R.string.hudhud_l_last_in
                Hudhud.GoodsFacet.BUYERS -> R.string.hudhud_chip_buyers
                Hudhud.GoodsFacet.SOLD -> R.string.hudhud_chip_sold
            }
        )
        is Hudhud.Ask.Count -> {
            val title = when (ask.thing) {
                Hudhud.Thing.CUSTOMERS -> R.string.hudhud_t_customers
                Hudhud.Thing.SUPPLIERS -> R.string.hudhud_t_suppliers
                Hudhud.Thing.PRODUCTS -> R.string.tile_products
                Hudhud.Thing.STAFF -> R.string.hudhud_t_staff
                Hudhud.Thing.ENTRIES -> R.string.hudhud_l_entries
                Hudhud.Thing.INVOICES -> R.string.invoices
                Hudhud.Thing.BILLS -> R.string.supplier_bills
                Hudhud.Thing.TICKETS -> R.string.hudhud_t_tickets
                Hudhud.Thing.CALCULATIONS -> R.string.hudhud_t_calculations
                Hudhud.Thing.APP_OPENS -> R.string.hudhud_t_app_opens
            }
            val w = ask.window
            if (w == null) c.getString(title) else c.getString(title) + " \u00B7 " + c.getString(windowWord(w))
        }
        is Hudhud.Ask.Status -> c.getString(
            when (ask.of) {
                Hudhud.StatusOf.CHEQUES -> R.string.cheques
                Hudhud.StatusOf.PLANS -> R.string.nav_plans
                Hudhud.StatusOf.BILLS -> R.string.supplier_bills
                Hudhud.StatusOf.LOW_STOCK -> R.string.hudhud_t_low
                Hudhud.StatusOf.BOOK -> R.string.hudhud_chip_book
                Hudhud.StatusOf.STAFF -> R.string.hudhud_t_salary
            }
        )
        is Hudhud.Ask.Deliveries -> c.getString(R.string.hudhud_t_deliveries)
        is Hudhud.Ask.Activity -> c.getString(R.string.hudhud_chip_activity)
        is Hudhud.Ask.Collected -> c.getString(R.string.hudhud_chip_collected)
        else -> null
    }

    /**
     * What to ask next after [ask] (13 Nov 2026, the owner: "recommendation automatically pick kare"): the questions
     * that follow from this answer, each one tap — about the same customer or product, the same figure on another day,
     * or the page beside it. Figures behind REPORTS are offered only to those who may see them.
     */
    fun next(c: Context, ask: Hudhud.Ask): List<Hudhud.Ask> {
        val reports = Access.can(Access.Permission.REPORTS)
        val radar = reports && RadarUi.on(c)
        val today = VoiceQuestion.Window.TODAY
        fun person(id: Long, f: Hudhud.Facet) = Hudhud.Ask.Person(listOf(id), f)
        val out: List<Hudhud.Ask> = when (ask) {
            is Hudhud.Ask.Parties -> ask.ids.singleOrNull()?.let { id ->
                listOfNotNull(person(id, Hudhud.Facet.PHONE), person(id, Hudhud.Facet.LAST_PAYMENT),
                    if (radar) Hudhud.Ask.WhenPays(listOf(id)) else null, person(id, Hudhud.Facet.DETAILS))
            }.orEmpty()
            is Hudhud.Ask.Person -> ask.ids.singleOrNull()?.let { id ->
                listOf(Hudhud.Ask.Parties(listOf(id))) + listOf(
                    Hudhud.Facet.PHONE, Hudhud.Facet.LAST_PAYMENT, Hudhud.Facet.LIMIT, Hudhud.Facet.PROMISE, Hudhud.Facet.DETAILS
                ).filter { it != ask.facet }.map { person(id, it) }
            }.orEmpty()
            is Hudhud.Ask.WhenPays -> ask.ids.singleOrNull()?.let { id ->
                listOf(Hudhud.Ask.Parties(listOf(id)), person(id, Hudhud.Facet.LAST_PAYMENT), person(id, Hudhud.Facet.PROMISE))
            } ?: listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID))
            is Hudhud.Ask.Moved -> ask.ids.singleOrNull()?.let { id -> listOf(Hudhud.Ask.Parties(listOf(id)), person(id, Hudhud.Facet.LAST_PAYMENT)) }.orEmpty()
            is Hudhud.Ask.Goods -> ask.ids.singleOrNull()?.let { id ->
                listOf(Hudhud.GoodsFacet.PRICE, Hudhud.GoodsFacet.STOCK, Hudhud.GoodsFacet.SUPPLIERS, Hudhud.GoodsFacet.BUYERS, Hudhud.GoodsFacet.LAST_IN)
                    .filter { it != ask.facet }.map { Hudhud.Ask.Goods(listOf(id), it) }
            }.orEmpty()
            is Hudhud.Ask.Figure -> when (ask.kind) {
                VoiceQuestion.Kind.SALES_TODAY -> listOf(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH), Hudhud.Ask.Collected(today), Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH))
                VoiceQuestion.Kind.SALES_MONTH -> listOf(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.LAST_MONTH),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH), Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
                VoiceQuestion.Kind.PROFIT_MONTH -> listOf(Hudhud.Ask.Period(Hudhud.Measure.PROFIT, VoiceQuestion.Window.LAST_MONTH),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))
                VoiceQuestion.Kind.TO_GET -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), Hudhud.Ask.WhenPays(emptyList()),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID))
                VoiceQuestion.Kind.TO_GIVE -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.BILLS), Hudhud.Ask.Deliveries())
                VoiceQuestion.Kind.WHO_OWES_MOST -> listOf(Hudhud.Ask.WhenPays(emptyList()), Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
                VoiceQuestion.Kind.NOT_PAID -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), Hudhud.Ask.WhenPays(emptyList()))
                VoiceQuestion.Kind.EXPIRING -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK), Hudhud.Ask.Deliveries())
            }
            is Hudhud.Ask.Period -> if (ask.measure == Hudhud.Measure.SALES) {
                listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))
            } else {
                listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH), Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))
            }
            is Hudhud.Ask.Count -> when (ask.thing) {
                Hudhud.Thing.INVOICES -> listOf(Hudhud.Ask.Count(Hudhud.Thing.INVOICES, VoiceQuestion.Window.YESTERDAY),
                    Hudhud.Ask.Count(Hudhud.Thing.INVOICES, VoiceQuestion.Window.THIS_MONTH), Hudhud.Ask.Activity(today))
                Hudhud.Thing.BILLS -> listOf(Hudhud.Ask.Deliveries(), Hudhud.Ask.Status(Hudhud.StatusOf.BILLS))
                Hudhud.Thing.APP_OPENS -> listOf(Hudhud.Ask.Count(Hudhud.Thing.CALCULATIONS, today), Hudhud.Ask.Activity(today))
                Hudhud.Thing.CALCULATIONS -> listOf(Hudhud.Ask.Count(Hudhud.Thing.APP_OPENS, today), Hudhud.Ask.Activity(today))
                Hudhud.Thing.CUSTOMERS -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST),
                    Hudhud.Ask.Count(Hudhud.Thing.SUPPLIERS))
                Hudhud.Thing.SUPPLIERS -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE), Hudhud.Ask.Status(Hudhud.StatusOf.BILLS),
                    Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS))
                Hudhud.Thing.PRODUCTS -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK), Hudhud.Ask.Figure(VoiceQuestion.Kind.EXPIRING))
                Hudhud.Thing.STAFF -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.STAFF))
                Hudhud.Thing.ENTRIES -> listOf(Hudhud.Ask.Activity(today), Hudhud.Ask.Collected(today))
                Hudhud.Thing.TICKETS -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), Hudhud.Ask.Collected(today))
            }
            is Hudhud.Ask.Status -> when (ask.of) {
                Hudhud.StatusOf.CHEQUES -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.PLANS), Hudhud.Ask.Collected(today))
                Hudhud.StatusOf.PLANS -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.CHEQUES), Hudhud.Ask.WhenPays(emptyList()))
                Hudhud.StatusOf.BILLS -> listOf(Hudhud.Ask.Deliveries(), Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE))
                Hudhud.StatusOf.LOW_STOCK -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.EXPIRING), Hudhud.Ask.Deliveries())
                Hudhud.StatusOf.BOOK -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Activity(today), Hudhud.Ask.BackupStatus)
                Hudhud.StatusOf.STAFF -> listOf(Hudhud.Ask.Count(Hudhud.Thing.STAFF), Hudhud.Ask.Activity(today))
            }
            is Hudhud.Ask.Deliveries -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.BILLS), Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK))
            is Hudhud.Ask.Activity -> listOf(Hudhud.Ask.Collected(today), Hudhud.Ask.Deliveries(today), Hudhud.Ask.Count(Hudhud.Thing.INVOICES, today))
            is Hudhud.Ask.Collected -> listOf(Hudhud.Ask.Activity(today), Hudhud.Ask.Drawer, Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
            Hudhud.Ask.Clock -> listOf(Hudhud.Ask.Today, Hudhud.Ask.Activity(today))
            is Hudhud.Ask.Chat -> if (ask.talk == Hudhud.Talk.WHO_ARE_YOU) {
                listOf(Hudhud.Ask.Abilities, Hudhud.Ask.About(Hudhud.AboutTopic.MAKER), Hudhud.Ask.About(Hudhud.AboutTopic.PRIVACY))
            } else {
                listOf(Hudhud.Ask.Abilities, Hudhud.Ask.Today)
            }
            is Hudhud.Ask.About -> listOf(Hudhud.Ask.Abilities, Hudhud.Ask.Chat(Hudhud.Talk.WHO_ARE_YOU), Hudhud.Ask.About(Hudhud.AboutTopic.PRIVACY))
            is Hudhud.Ask.Shop -> listOf(Hudhud.Ask.Shop(Hudhud.ShopFacet.ALL), Hudhud.Ask.Status(Hudhud.StatusOf.BOOK))
            Hudhud.Ask.Today -> listOf(Hudhud.Ask.Activity(today), Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), Hudhud.Ask.Collected(today))
            Hudhud.Ask.BackupStatus -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.BOOK))
            Hudhud.Ask.Drawer -> listOf(Hudhud.Ask.Collected(today), Hudhud.Ask.Activity(today))
            Hudhud.Ask.Abilities -> listOf(Hudhud.Ask.Today, Hudhud.Ask.Clock, Hudhud.Ask.Status(Hudhud.StatusOf.BOOK))
            is Hudhud.Ask.Cashbook -> listOf(Hudhud.Ask.Drawer, Hudhud.Ask.Collected(today), Hudhud.Ask.Activity(today))
            is Hudhud.Ask.Returns -> listOf(Hudhud.Ask.Count(Hudhud.Thing.TICKETS, today), Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
            is Hudhud.Ask.NeedName -> listOf(Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS), Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST))
            is Hudhud.Ask.Act -> listOf(Hudhud.Ask.Abilities, Hudhud.Ask.Today)
            is Hudhud.Ask.Guessed -> return next(c, ask.ask)
            else -> emptyList()
        }
        return out.filter { a -> a != ask && (reports || !needsReports(a)) }.distinct().take(MAX_NEXT)
    }

    /** A question whose answer is a figure only the owner or a manager may see. */
    private fun needsReports(a: Hudhud.Ask): Boolean = when (a) {
        is Hudhud.Ask.Figure, is Hudhud.Ask.Period, is Hudhud.Ask.Collected, is Hudhud.Ask.Activity, Hudhud.Ask.Drawer,
        is Hudhud.Ask.WhenPays, is Hudhud.Ask.Cashbook -> true
        is Hudhud.Ask.Status -> a.of == Hudhud.StatusOf.STAFF
        is Hudhud.Ask.Goods -> a.facet == Hudhud.GoodsFacet.COST || a.facet == Hudhud.GoodsFacet.SUPPLIERS || a.facet == Hudhud.GoodsFacet.SOLD
        else -> false
    }

    /**
     * The day's own questions, first among his chips when the sheet opens (13 Nov 2026): what the bell has now — a
     * cheque or a plan due, a bill, low stock, the drawer, the backup, a late payer — asked in one tap.
     */
    suspend fun suggested(c: Context, dao: KhataDao): List<Hudhud.Ask> {
        val reports = Access.can(Access.Permission.REPORTS)
        val alerts = runCatching { HomeAlerts.compute(c, dao) { Format.money(it) } }.getOrDefault(emptyList())
        return alerts.mapNotNull { a ->
            when (a.kind) {
                HomeAlerts.Kind.CHEQUE -> Hudhud.Ask.Status(Hudhud.StatusOf.CHEQUES)
                HomeAlerts.Kind.PLAN -> Hudhud.Ask.Status(Hudhud.StatusOf.PLANS)
                HomeAlerts.Kind.BILL -> Hudhud.Ask.Status(Hudhud.StatusOf.BILLS)
                HomeAlerts.Kind.STOCK -> Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK)
                HomeAlerts.Kind.EXPIRING -> if (reports) Hudhud.Ask.Figure(VoiceQuestion.Kind.EXPIRING) else null
                HomeAlerts.Kind.DRAWER -> if (reports) Hudhud.Ask.Drawer else null
                HomeAlerts.Kind.BACKUP, HomeAlerts.Kind.BACKUP_FAILED, HomeAlerts.Kind.BACKUP_OTHER_PHONE -> Hudhud.Ask.BackupStatus
                HomeAlerts.Kind.LATE -> if (reports && RadarUi.on(c)) Hudhud.Ask.WhenPays(emptyList()) else null
                else -> null
            }
        }.distinct().take(MAX_NEXT - 1)
    }

    // ------------------------------------------------------------------------------------------ the voice

    /** What [speech] said of a card: its words, and whether amounts were left out for the hidden balance. */
    class Speech(val text: String, val leftOutMoney: Boolean)

    /**
     * What he says for [a], with [w] a context in the voice's language (13 Nov 2026, the owner: "jo cheez confirm hai
     * usko complete bolna chahiye… jo doubtful hai us par chup rahe"): its own words when it has them
     * ([Answer.spoken]); else the whole card — the heading with its figure, every line, every row (the name, its
     * figure, what it is) — less what is never said aloud: the [Answer.secrets] (said to be on the screen), and every
     * amount while Home's balance is hidden ([hideMoney]: a customer may be standing at the counter). Null for a quiet
     * answer (a guess, a choice) and when nothing but a heading would be left.
     */
    fun speech(w: Context, a: Answer, hideMoney: Boolean): Speech? {
        if (a.quiet) return null
        a.spoken?.let { own ->
            val text = runCatching { own(w) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return Speech(text, false)
        }
        val onScreen = w.getString(R.string.hudhud_on_screen).trimEnd('.', '\u06D4', '\u0964', '!')
        var leftOut = false
        fun said(text: String?): String? {
            if (text.isNullOrBlank()) return null
            var t = HudhudSpeech.clean(text)
            for (secret in a.secrets) {
                val v = HudhudSpeech.clean(secret)
                if (v.isNotEmpty()) t = t.replace(v, onScreen)
            }
            if (hideMoney && hasMoney(t)) {
                leftOut = true
                return null
            }
            return t.takeIf { it.isNotBlank() }
        }
        val parts = ArrayList<String>()
        val label = said(a.label)
        val figure = if (a.money && hideMoney && a.figure != null) { leftOut = true; null } else said(a.figure)
        when {
            label != null && figure != null -> parts += "$label: $figure"
            label != null -> parts += label
            figure != null -> parts += figure
        }
        a.lines.forEach { line -> said(line)?.let { parts += it } }
        for (r in a.rows) {
            val bits = listOfNotNull(HudhudSpeech.clean(r.name).takeIf { it.isNotBlank() }, said(r.figure), said(r.sub))
            if (bits.isNotEmpty()) parts += bits.joinToString(", ")
        }
        // Only the heading is left (its figure and lines were amounts): nothing worth saying.
        if (parts.isEmpty() || (leftOut && parts.size == 1 && label != null && figure == null)) return null
        val stop = sentenceEnd(w)
        return Speech(parts.joinToString(" ") { endWith(it, stop) }, leftOut)
    }

    /** An amount written as the screens write money: the book's sign with a figure. */
    private fun hasMoney(text: String): Boolean {
        val sign = Currency.symbol.trim()
        return sign.isNotEmpty() && text.contains(sign) && text.any { it.isDigit() }
    }

    /**
     * The full stop of the voice's language: Urdu's, Hindi's and Bengali's own; the others' the Latin one — and Urdu
     * written in Latin letters (Roman Urdu) the Latin one too.
     */
    private fun sentenceEnd(w: Context): String {
        val locale = w.resources.configuration.locales[0]
        if (locale.script == "Latn") return "."
        return when (locale.language) {
            "ur" -> "\u06D4"
            "hi", "bn" -> "\u0964"
            else -> "."
        }
    }

    private fun endWith(part: String, stop: String): String {
        val t = part.trim().trimEnd(',', ';', ':')
        return if (t.isEmpty() || t.last() in ENDS) t else t + stop
    }

    private val ENDS = setOf('.', '!', '?', '\u061F', '\u06D4', '\u0964', '\u2026')

    // ------------------------------------------------------------------------------------------ the words

    /** A figure as every screen writes it: the minus outside the left-to-right seal, as Insights writes it. */
    fun money(v: Double): String = (if (v < 0) "\u2212" else "") + Format.moneyLtr(v)

    /** A feature sheet's page key begins so; a question-and-answer page's with "faq_". */
    private const val FEATURE = "intro_"
    private const val TOP = 5
    /** Next-step chips under an answer. */
    private const val MAX_NEXT = 4
    /** Customers offered for an entry whose name was not made out: voice entry's picker, cut to a card's length. */
    private const val PICK = 6
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val OLD_BACKUP_DAYS = 7
    /** How much of a help page is said aloud before "the rest is on the screen". */
    private const val SAY_HELP = 320
}
