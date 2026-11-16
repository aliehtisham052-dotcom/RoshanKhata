package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.data.AnswerCard.Row
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.screens.ReportPeriod
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
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
object AnswerBook {

    /** A help page Hudhud can point to: a question from Help, or a feature's first-open sheet. */
    /**
     * [gate] is the screen whose own gate the page tells ([AnswerGate.pageLimit]): the screen it opens, or — for the
     * pages that only say where a screen is (`open = null`) — that screen's class.
     */
    class Page(val key: String, val title: String, val body: String, val open: ScreenId?, val gate: ScreenId? = open)

    val PAGES: List<Page> by lazy {
        listOf(
            Page("faq_backup", W.faq_q_backup, W.faq_a_backup, ScreenId.BACKUP),
            Page("faq_restore", W.faq_q_restore, W.faq_a_restore, ScreenId.BACKUP),
            Page("faq_business", W.faq_q_business, W.faq_a_business, ScreenId.BUSINESS_SWITCH),
            Page("faq_snapshot", W.faq_q_snapshot, W.faq_a_snapshot, ScreenId.SNAPSHOTS),
            Page("faq_lock", W.faq_q_lock, W.faq_a_lock, null),
            Page("faq_hide", W.faq_q_hide, W.faq_a_hide, null),
            Page("faq_percent", W.faq_q_percent, W.faq_a_percent, ScreenId.CALCULATOR),
            Page("faq_delete", W.faq_q_delete, W.faq_a_delete, ScreenId.RECYCLE_BIN),
            Page("faq_language", W.faq_q_language, W.faq_a_language, ScreenId.LANGUAGE),
            Page("faq_data", W.faq_q_data, W.faq_a_data, null)
        ) + FEATURES.map { Page(FEATURE + it.key, it.title, it.body, it.screen) } + listOf(
            // 13 Nov 2026 (Brain v2): every screen the owner may ask for by name, with its own title and words, so
            // "invoice kahan hai" or "rate list kholo" opens it — those without a first-open sheet of their own.
            Page("screen_khata", W.coach_title_nav_khata, W.coach_desc_nav_khata, ScreenId.KHATA),
            Page("screen_bills", W.coach_title_bills, W.coach_desc_bills, ScreenId.BILLS),
            Page("screen_products", W.coach_title_products, W.coach_desc_products, ScreenId.PRODUCTS),
            Page("screen_insights", W.coach_title_insights, W.coach_desc_insights, ScreenId.INSIGHTS),
            Page("screen_card", W.coach_title_bizcard, W.coach_desc_bizcard, ScreenId.BUSINESS_CARD),
            Page("screen_calc", W.coach_title_calc, W.coach_desc_calc, ScreenId.CALCULATOR),
            Page("screen_zakat", W.tile_zakat, W.zakat_subtitle, ScreenId.ZAKAT),
            Page("screen_rates", W.rate_list_title, W.rate_list_subtitle, ScreenId.RATE_LIST),
            Page("screen_purchase_rates", W.sr_title, W.sr_subtitle, ScreenId.SUPPLIER_RATES),
            Page("screen_cashflow", W.cf_title, W.cf_subtitle, ScreenId.CASH_FLOW),
            Page("screen_sales_report", W.sales_report_title, W.sales_report_title, ScreenId.SALES_REPORT),
            Page("screen_posters", W.poster_title, W.poster_subtitle, ScreenId.POSTER),
            Page("screen_seasons", W.tile_seasons, W.season_subtitle, ScreenId.SEASONS),
            Page("screen_schemes", W.schemes_title, W.schemes_subtitle, ScreenId.SCHEMES),
            Page("screen_audit", W.audit_title, W.audit_subtitle, ScreenId.AUDIT),
            Page("screen_lapsed", W.lapsed_title, W.lapsed_title, ScreenId.LAPSED),
            Page("screen_aging", W.aging_title, W.aging_title, ScreenId.AGING),
            Page("screen_bin", W.tile_bin, W.faq_a_delete, ScreenId.RECYCLE_BIN),
            // 14 Nov 2026 (#32): the six screens that had no how-to page, by the owner's tiers. The OPEN tier (Help,
            // Mera Khata, Payment voice) says what the screen is and where it is, and opens it; the screen asks its own
            // gate (AccessUi), so Hudhud only points. The NEVER tier (Duplicates, Helper's phone, Heir Access) only
            // says where it is and what it does — `open = null`, he never opens it: a merge, a copy of the whole book
            // and an heir's key are the owner's own taps. Their words are the screens' own sentences (already in nine
            // languages) put together in hudhud_page_*, so the page says what the screen says. The titles are also
            // names and shop words, so Hudhud.isFeaturePage keeps them out of the feature words.
            Page("screen_duplicates", W.duplicate_customers, W.hudhud_page_duplicates, null, ScreenId.DUPLICATE_CUSTOMERS),
            Page("screen_help", W.help_support, W.hudhud_page_help, ScreenId.HELP),
            Page("screen_mera_khata", W.mera_khata_title, W.mera_khata_help, ScreenId.MERA_KHATA),
            Page("screen_payment_voice", W.paisa_title, W.hudhud_page_paisa, ScreenId.PAYMENT_VOICE),
            Page("screen_viewer", W.viewer_menu, W.hudhud_page_viewer, null, ScreenId.VIEWER),
            Page("screen_waris", W.waris_title, W.hudhud_page_waris, null, ScreenId.WARIS),
            // The shop's side of the khata link (the customer's side is screen_mera_khata): where to send it and what the
            // customer gets. It opens the Khata list, where the customer is picked, as the customer QR's page does. A
            // "how do I send a link" question was otherwise taken to Mera Khata, the page for receiving one.
            Page("faq_khata_link", W.clink_menu, W.hudhud_page_khata_link, ScreenId.KHATA)
        )
    }

    /**
     * The feature sheets' pages (the phone's first-open sheets, FeatureIntro.Topic, in its order): each sheet's key, title
     * and body words, and the page Hudhud opens for it. Shared, so the computer's Hudhud knows the same pages; the phone's
     * HudhudFeaturePagesTest holds this list to FeatureIntro.Topic (the same keys, words and order), so a sheet added
     * there fails the build's tests until it is added here with its page.
     */
    class Feature(val key: String, val title: String, val body: String, val screen: ScreenId)

    val FEATURES: List<Feature> = listOf(
        Feature("counter", W.pos_title, W.intro_counter_body, ScreenId.COUNTER_SALE),
        Feature("documents", W.documents_title, W.intro_documents_body, ScreenId.INVOICES),
        Feature("cashbook", W.coach_title_nav_cashbook, W.intro_cashbook_body, ScreenId.CASHBOOK),
        Feature("cheques", W.coach_title_nav_cheques, W.intro_cheques_body, ScreenId.CHEQUES),
        Feature("customer_qr", W.customer_qr, W.intro_qr_body, ScreenId.KHATA),
        Feature("staff", W.staff_title, W.intro_staff_body, ScreenId.STAFF),
        Feature("plans", W.coach_title_nav_plans, W.intro_plans_body, ScreenId.PLANS),
        Feature("followup", W.followup_title, W.intro_followup_body, ScreenId.FOLLOW_UP),
        Feature("backup", W.coach_title_backup, W.intro_backup_body, ScreenId.BACKUP),
        Feature("orders", W.po_title, W.intro_orders_body, ScreenId.ORDERS),
        Feature("expiry", W.coach_title_stock, W.intro_expiry_body, ScreenId.EXPIRING),
        Feature("radar", W.radar_title, W.intro_radar_body, ScreenId.FOLLOW_UP)
    )

    /**
     * The help pages as Hudhud matches a question against them: each in the app's language and in English, and in
     * both Urdu scripts when the app speaks Urdu (people type Roman Urdu on an Urdu phone, and the other way round).
     */
    fun topics(context: AnswerEnv): List<Hudhud.Topic> {
        val app = context.locale
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
                // A feature's or a screen's title is never a customer's name; a question's may be (محفوظ, "keep"), and so
                // may the title of a screen called what a person is (Waris, Madad): one rule, shared with the tests.
                feature = Hudhud.isFeaturePage(p.key)
            )
        }
    }

    private fun localized(context: AnswerEnv, locale: Locale): AnswerEnv = context.inLanguage(locale)

    /** What Hudhud says when his sheet opens. */
    fun hello(context: AnswerEnv) = AnswerCard(
        Mood.CALM,
        label = greeting(context),
        lines = listOf(context.getString(W.hudhud_hello)),
        spoken = { w -> greeting(w) + " " + w.getString(W.hudhud_hello) }
    )

    /**
     * His own introduction when his face is tapped (13 Nov 2026, the owner: "tap karein to wo bole, main Hudhud hoon,
     * aap mujh se poochhein"): who he is and to ask; the day's first time with the greeting and the owner's name.
     */
    fun intro(context: AnswerEnv, greet: Boolean): AnswerCard {
        // The first time he meets the owner (14 Nov 2026, the owner: "data mehfooz hai, ye bhi bataye — professionally,
        // har baat pe nahi"): once, that the book stays on this phone; after that only when asked ("data mehfooz hai?").
        val privacy = context.privacyDue()
        if (privacy) context.privacyTold()
        return AnswerCard(
            Mood.CALM,
            label = greeting(context),
            lines = listOfNotNull(context.getString(W.hudhud_intro), if (privacy) context.getString(W.hudhud_privacy_once) else null),
            spoken = { w ->
                (if (greet) greeting(w) + " " else "") + w.getString(W.hudhud_intro) +
                    (if (privacy) " " + w.getString(W.hudhud_privacy_once) else "")
            }
        )
    }

    /** What he says when the book could not be read. */
    fun failed(context: AnswerEnv) = AnswerCard(Mood.CARE, lines = listOf(context.getString(W.hudhud_unknown)))

    suspend fun answer(context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): AnswerCard {
        // The one door every ask passes before the book is read (15 Nov 2026): a span he cannot give is never read as
        // another, however the ask came (a chip, an answer repeated, a question carried on).
        val within = Hudhud.keepWithinReach(ask)
        return answerOf(context, dao, within, parties).let { if (it.ask == null) it.copy(ask = within) else it }
    }

    private suspend fun answerOf(context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): AnswerCard =
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
            Hudhud.Ask.Salaam -> AnswerCard(
                Mood.GOOD,
                lines = listOf(addressed(context, W.hudhud_salaam_reply), context.getString(W.hudhud_hello))
            )
            Hudhud.Ask.Hello -> AnswerCard(Mood.CALM, lines = listOf(context.getString(W.hudhud_hello)))
            Hudhud.Ask.Abilities -> abilities(context)
            is Hudhud.Ask.Chat -> chat(context, ask)
            Hudhud.Ask.Unknown -> unknown(context)
            is Hudhud.Ask.OffTopic -> offTopic(context, ask)
            // Brain v2 (13 Nov 2026): the wider knowledge, each by its screen's own rule (AnswerFacts).
            is Hudhud.Ask.Person -> AnswerFacts.person(context, dao, ask.ids, ask.facet, parties)
            is Hudhud.Ask.Goods -> AnswerFacts.goods(context, dao, ask.ids, ask.facet, ask.window)
            is Hudhud.Ask.Shop -> AnswerFacts.shop(context, ask.facet)
            is Hudhud.Ask.About -> AnswerFacts.about(context, ask.topic)
            Hudhud.Ask.Clock -> AnswerFacts.clock(context)
            is Hudhud.Ask.Count -> AnswerFacts.count(context, dao, ask.thing, ask.window, parties)
            is Hudhud.Ask.Status -> AnswerFacts.status(context, dao, ask.of, parties, ask.cheques)
            is Hudhud.Ask.Deliveries -> AnswerFacts.deliveries(context, dao, ask.window)
            is Hudhud.Ask.Activity -> AnswerFacts.activity(context, dao, ask.window)
            is Hudhud.Ask.Collected -> AnswerFacts.collected(context, dao, ask.window)
            is Hudhud.Ask.NeedName -> AnswerFacts.needName(context, dao, ask.facet, parties)
            is Hudhud.Ask.Cashbook -> AnswerFacts.cashbook(context, dao, ask.window)
            is Hudhud.Ask.Returns -> AnswerFacts.returns(context, dao, ask.window)
            is Hudhud.Ask.Counter -> AnswerCounterFacts.answer(context, dao, ask)
            is Hudhud.Ask.Act -> act(context, ask.move)
            is Hudhud.Ask.Voice -> voiceChange(context, ask.change)
            is Hudhud.Ask.TopGoods -> AnswerFacts.topGoods(context, dao, ask.window)
            Hudhud.Ask.Qarz -> AnswerFacts.qarz(context, dao, parties)
            Hudhud.Ask.Automations -> AnswerFacts.automations(context)
            is Hudhud.Ask.NeedGoods -> AnswerFacts.needGoods(context, dao, ask.facet, ask.code)
            is Hudhud.Ask.Paper -> AnswerFacts.paper(context, dao, ask.of, ask.ids, parties)
            // Said, the name is only offered (16 Nov 2026, audit S-05): it is kept once the card's Save is tapped.
            is Hudhud.Ask.CallMe -> if (ask.save) callMe(context, ask.name) else callMeOffer(context, ask.name)
            is Hudhud.Ask.OnDays -> onDays(context, dao, ask, parties)
            is Hudhud.Ask.Both -> both(context, dao, ask, parties)
            is Hudhud.Ask.Compare -> compare(context, dao, ask)
            is Hudhud.Ask.Look -> look(context, ask.request)
            is Hudhud.Ask.LookDone -> lookDone(context, ask.made)
            is Hudhud.Ask.Record -> AnswerRecordFacts.answer(context, dao, ask, parties)
            // A span he cannot give: the card says so and offers the spans he can. A question about how the book stands
            // now that came with a span is answered as today, the card saying so first (15 Nov 2026).
            is Hudhud.Ask.SpanLimit -> spanLimit(context, ask)
            is Hudhud.Ask.Now -> now(context, dao, ask.ask, parties)
            // Tell, never ask (13 Nov 2026): a guess is answered, with what it was taken as over the card; "did you
            // mean" answers its first meaning the same way and offers the others as chips.
            is Hudhud.Ask.Guessed -> guessed(context, dao, ask.ask, parties, emptyList())
            is Hudhud.Ask.DidYouMean -> ask.options.firstOrNull()?.let { guessed(context, dao, it, parties, ask.options.drop(1)) }
                ?: unknown(context)
        }

    /**
     * "Call me Ali bhai" / "mera naam Ali hai" (14 Nov 2026, the owner's check log): what he is told to call the owner is
     * kept on this phone only (HudhudPrefs, as his voice settings keep it — never in the book, a backup or a sync), and
     * the card says it back, so a wrong hearing is seen at once and mended by saying it again.
     */
    private fun callMe(context: AnswerEnv, name: String): AnswerCard {
        context.setCallMe(name)
        val kept = context.callMe() ?: return unknown(context)
        return AnswerCard(
            Mood.GOOD, label = kept,
            lines = listOf(context.getString(W.hudhud_owner_named, Format.isolate(kept))),
            openLabel = W.hudhud_settings, voiceSettings = true
        )
    }

    /**
     * "Mera naam Ehtisham hai" (16 Nov 2026, audit T15 / S-05): a name heard is never kept at once — a misheard word would be
     * what he calls the owner from then on. The card says what it would keep, and its Save chip ([Hudhud.Ask.CallMe] with
     * save) keeps it; the card that follows is [callMe]'s, so the owner sees it was kept.
     */
    private fun callMeOffer(context: AnswerEnv, name: String): AnswerCard {
        val keep = Hudhud.Ask.CallMe(name, save = true)
        return AnswerCard(
            Mood.CALM, label = name,
            lines = listOf(context.getString(W.hudhud_call_me_confirm, Format.isolate(name))),
            options = listOf(keep), optionLabels = mapOf(keep to context.getString(W.save))
        )
    }

    // ---------------------------------------------------------------------------- a day named, two questions, two spans

    /** The words of a day or a month named outright: "16 Nov 2026", or "November 2026" for a whole month. */
    private fun daysWords(d: VoiceQuestion.Dated, from: Long): String =
        DateWords.format(if (oneDay(d)) "d MMM yyyy" else "MMMM yyyy", from)

    /** A day, not a whole month: a day of the month, the day before yesterday, the last such weekday. */
    private fun oneDay(d: VoiceQuestion.Dated): Boolean = d.month == null || d.day != null

    /**
     * A sale, a profit, money in, the day's dealings or one customer's paid or taken over a day or a month named outright
     * (16 Nov 2026, audit T31): the same calls as the span asks over [Hudhud.span] of the day — the sale by
     * [KhataDao.salesTotalBetween] (opened on the sales report over those days), the profit by Insights' rule (this month's and
     * last month's exactly as Insights says them), money in and the day's dealings by [AnswerFacts]'s own readers, a customer's
     * lines by [moved]'s rule — each behind its own gate. A day not yet come is said so, never answered with a figure.
     */
    private suspend fun onDays(context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.OnDays, parties: List<PartyWithBalance>): AnswerCard {
        val span = Hudhud.span(ask.days)
            ?: return AnswerCard(Mood.CALM, lines = listOf(context.getString(W.hudhud_date_ahead)))
        val (from, to) = span
        val words = daysWords(ask.days, from)
        return when (ask.of) {
            Hudhud.DatedOf.SALES -> {
                val label = context.getString(W.hudhud_sales_on, words)
                if (!AnswerGate.reports()) return AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.hudhud_owner_only)))
                sum(W.hudhud_sales_on, label, dao.salesTotalBetween(from, to), ScreenId.SALES_REPORT)
                    .copy(openWith = OpenWith.SalesDays(from, DayMath.startOfDay(to - 1)))
            }
            Hudhud.DatedOf.PROFIT -> {
                val label = context.getString(W.hudhud_profit_on, words)
                if (!AnswerGate.reports()) return AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.hudhud_owner_only)))
                when (span) {
                    Hudhud.span(VoiceQuestion.Window.THIS_MONTH) -> profit(context, W.hudhud_profit_on, label, Profit.thisMonth(dao), ScreenId.INSIGHTS)
                    Hudhud.span(VoiceQuestion.Window.LAST_MONTH) -> profit(context, W.hudhud_profit_on, label, Profit.lastMonth(dao), ScreenId.INSIGHTS)
                    else -> profit(context, W.hudhud_profit_on, label, Profit.between(dao, from, to), null)
                }
            }
            Hudhud.DatedOf.COLLECTED -> AnswerFacts.collected(context, dao, null,
                AnswerFacts.Over(context.getString(W.hudhud_t_collected) + " · " + words, span, oneDay(ask.days)))
            Hudhud.DatedOf.ACTIVITY -> AnswerFacts.activity(context, dao, null,
                AnswerFacts.Over(context.getString(W.hudhud_t_activity) + " · " + words, span, oneDay(ask.days)))
            Hudhud.DatedOf.PAID, Hudhud.DatedOf.TOOK -> movedOver(context, dao, ask.ids, from, to, words, ask.of == Hudhud.DatedOf.PAID, parties)
        }
    }

    /**
     * Two questions in one sentence (16 Nov 2026, audit T32): each half answered by its own reader behind its own gate, the two
     * put on one card — each heading with its figure, then its lines; the button is the first half's that has one.
     */
    private suspend fun both(context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Both, parties: List<PartyWithBalance>): AnswerCard {
        val halves = listOf(answer(context, dao, ask.first, parties), answer(context, dao, ask.second, parties))
        val lines = ArrayList<String>()
        for (h in halves) {
            listOfNotNull(h.label, h.figure).joinToString(": ").takeIf { it.isNotBlank() }?.let { lines += it }
            lines += h.lines.take(MAX_NEXT)
        }
        val opener = halves.firstOrNull { it.open != null || it.openParty != null }
        return AnswerCard(
            Mood.CALM, lines = lines, rows = halves.flatMap { it.rows }.take(TOP),
            open = opener?.open, openParty = opener?.openParty, openWith = opener?.openWith, openLabel = opener?.openLabel,
            secrets = halves.flatMap { it.secrets }, quiet = halves.any { it.quiet }, ask = ask
        )
    }

    /**
     * A sale or a profit over two spans and the difference, first less second, with its sign (16 Nov 2026, audit T32): each
     * figure by the very rule its own card uses ([spanFigure]); a figure not known (a profit with no cost) gives no difference —
     * no third figure is ever made up. Only the owner or a manager sees it, as each figure alone.
     */
    private suspend fun compare(context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Compare): AnswerCard {
        val title = context.getString(W.hudhud_compare_title)
        if (!AnswerGate.reports()) return AnswerCard(Mood.CALM, label = title, lines = listOf(context.getString(W.hudhud_owner_only)))
        val one = spanFigure(context, dao, ask.first) ?: return unknown(context)
        val two = spanFigure(context, dao, ask.second) ?: return unknown(context)
        val lines = listOf(one, two).map { (label, v) -> label + ": " + (v?.let { money(it) } ?: "—") }
        val a = one.second
        val b = two.second
        if (a == null || b == null) {
            return AnswerCard(Mood.CALM, label = title, lines = lines + context.getString(W.hudhud_compare_unknown), money = true)
        }
        val diff = a - b
        val signed = (if (Money.isPositive(diff)) "+" else "") + money(diff)
        return AnswerCard(
            if (diff < 0 && !Money.isZero(diff)) Mood.CARE else Mood.CALM, label = title, figure = signed,
            lines = lines + (context.getString(W.z_variance) + ": " + signed), money = true
        )
    }

    /** One side of a comparison: its card's heading and its figure, or null figure when it is not known (a profit with no cost). */
    private suspend fun spanFigure(context: AnswerEnv, dao: KhataDao, a: Hudhud.Ask): Pair<String, Double?>? = when (a) {
        is Hudhud.Ask.Period -> {
            val label = context.getString(periodLabel(a.measure, a.window))
            val (from, to) = Hudhud.span(a.window)
            label to when (a.measure) {
                Hudhud.Measure.SALES -> dao.salesTotalBetween(from, to)
                Hudhud.Measure.PROFIT -> when (a.window) {
                    VoiceQuestion.Window.THIS_MONTH -> Profit.thisMonth(dao)
                    VoiceQuestion.Window.LAST_MONTH -> Profit.lastMonth(dao)
                    else -> Profit.between(dao, from, to)
                }.let { r -> if (r.isEmpty) null else r.grossProfit }
            }
        }
        is Hudhud.Ask.OnDays -> {
            val sales = a.of == Hudhud.DatedOf.SALES
            val span = Hudhud.span(a.days)
            if (span == null) {
                context.getString(W.hudhud_date_ahead) to null
            } else {
                val (from, to) = span
                val label = context.getString(if (sales) W.hudhud_sales_on else W.hudhud_profit_on, daysWords(a.days, from))
                label to if (sales) dao.salesTotalBetween(from, to) else Profit.between(dao, from, to).let { r -> if (r.isEmpty) null else r.grossProfit }
            }
        }
        else -> null
    }

    /** [ask] answered as a guess: the card says what it was taken as, the [others] are chips, and nothing is said aloud. */
    private suspend fun guessed(
        context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>, others: List<Hudhud.Ask>
    ): AnswerCard {
        val inner = answer(context, dao, ask, parties)
        val took = chipLabel(context, ask) ?: inner.label ?: ""
        return inner.copy(
            tookAs = context.getString(W.hudhud_took_as, Format.isolate(took)),
            quiet = true,
            options = others,
            ask = ask
        )
    }

    // ------------------------------------------------------------------------------------------ spans he cannot give

    /**
     * A question about how the book stands now that came with a span ("pichle saal kitne customers", "Ali ka hisaab last
     * month") (15 Nov 2026): the book keeps no earlier standing of these, so [ask] is answered as it stands today and the
     * card says so first — the span left out in silence read as that span's own figure.
     */
    private suspend fun now(context: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): AnswerCard {
        val inner = answerOf(context, dao, ask, parties)
        return inner.copy(lines = listOf(context.getString(W.hudhud_span_now)) + inner.lines)
    }

    /**
     * A span of days he cannot give for this question ([Hudhud.Ask.SpanLimit]): the card says so and offers the same
     * question over the spans he can give, each a tap with its span in its words ([AnswerCard.optionLabels]). A question only
     * the owner or a manager may see is offered to nobody else, and says so as the figure itself would.
     */
    private fun spanLimit(context: AnswerEnv, ask: Hudhud.Ask.SpanLimit): AnswerCard {
        val title = context.getString(W.hudhud_span_title)
        if (!AnswerGate.reports() && ask.options.any { needsReports(it) }) {
            return AnswerCard(Mood.CALM, label = title, lines = listOf(context.getString(W.hudhud_owner_only)))
        }
        val words = if (ask.long) W.hudhud_span_sums else W.hudhud_span_rows
        val labels = LinkedHashMap<Hudhud.Ask, String>()
        for (a in ask.options) spanChipLabel(context, a)?.let { labels[a] = it }
        return AnswerCard(
            Mood.CALM, label = title, lines = listOf(context.getString(words)),
            options = ask.options.filter { it in labels }, optionLabels = labels,
            spoken = { w -> w.getString(words) }
        )
    }

    /** The question's own name without any span, for the chips that offer it over several (15 Nov 2026). */
    private fun spanQuestion(c: AnswerEnv, a: Hudhud.Ask): String? = when (a) {
        is Hudhud.Ask.Deliveries -> c.getString(W.hudhud_t_deliveries)
        is Hudhud.Ask.Activity -> c.getString(W.hudhud_t_activity)
        is Hudhud.Ask.Collected -> c.getString(W.hudhud_t_collected)
        is Hudhud.Ask.Cashbook -> c.getString(W.hudhud_t_cashbook)
        is Hudhud.Ask.Returns -> c.getString(W.hudhud_t_returns)
        is Hudhud.Ask.TopGoods -> c.getString(W.hudhud_t_top_goods)
        is Hudhud.Ask.Counter -> AnswerCounterFacts.titleOf(c, a.facet)
        is Hudhud.Ask.Moved -> c.getString(if (a.got) W.i_got else W.i_gave)
        is Hudhud.Ask.Goods -> if (a.facet == Hudhud.GoodsFacet.SOLD) c.getString(W.hudhud_l_sold) else null
        else -> null
    }

    /** A chip's words for [a] over its span: the sales, profit and counts already say theirs, the rest get it after their name. */
    private fun spanChipLabel(c: AnswerEnv, a: Hudhud.Ask): String? {
        if (a is Hudhud.Ask.Figure || a is Hudhud.Ask.Period || a is Hudhud.Ask.Count) return chipLabel(c, a)
        val name = spanQuestion(c, a) ?: return null
        val w = Hudhud.spanOf(a) ?: return name
        return name + " \u00B7 " + c.getString(windowWord(w))
    }

    // ------------------------------------------------------------------------------------------ the look

    /** Where a look setting stands now: the text size's level or the theme's mode (reading only). */
    private fun lookValue(context: AnswerEnv, setting: HudhudLook.Setting): Int = when (setting) {
        HudhudLook.Setting.SIZE -> context.lookValue(setting)
        HudhudLook.Setting.THEME -> context.lookValue(setting)
    }

    /** The setting's own name, as the More list writes it. */
    private fun lookName(setting: HudhudLook.Setting): String =
        if (setting == HudhudLook.Setting.SIZE) W.text_size else W.theme

    /** One value's own word, as the More list writes it (the numbers are held equal to TextSize's and ThemeMode's). */
    private fun lookWord(setting: HudhudLook.Setting, value: Int): String = when (setting) {
        HudhudLook.Setting.SIZE -> when (value) {
            HudhudLook.SIZE_SMALL -> W.text_size_small
            HudhudLook.SIZE_NORMAL -> W.text_size_normal
            HudhudLook.SIZE_LARGE -> W.text_size_large
            else -> W.text_size_largest
        }
        HudhudLook.Setting.THEME -> when (value) {
            HudhudLook.THEME_LIGHT -> W.theme_light
            HudhudLook.THEME_DARK -> W.theme_dark
            else -> W.theme_system
        }
    }

    /** What an order for the look comes to, by where the setting stands: a change is handed to the sheet, the rest are told. */
    private fun look(context: AnswerEnv, request: HudhudLook.Request): AnswerCard {
        val setting = request.setting
        val name = context.getString(lookName(setting))
        return when (val plan = HudhudLook.plan(request, lookValue(context, setting))) {
            // Not shown here: the screen is rebuilt by it, and the new screen's sheet shows the card (lookDone).
            is HudhudLook.Plan.Change -> AnswerCard(Mood.CALM, label = name, look = plan.made, quiet = true)
            is HudhudLook.Plan.Same -> AnswerCard(
                Mood.CALM, label = name, figure = context.getString(lookWord(setting, plan.value)),
                lines = listOf(context.getString(W.hudhud_look_already))
            )
            is HudhudLook.Plan.Limit -> AnswerCard(
                Mood.CALM, label = name, figure = context.getString(lookWord(setting, plan.value)),
                lines = listOf(context.getString(if (plan.bigger) W.hudhud_look_largest else W.hudhud_look_smallest))
            )
            is HudhudLook.Plan.Show -> AnswerCard(Mood.CALM, label = name, figure = context.getString(lookWord(setting, plan.value)))
        }
    }

    /**
     * The card of a change that was made, shown by the sheet the rebuilt screen opens (or by the one that made it, when the
     * screen did not rebuild): what it is now, what it was, and an Undo — unless this is the Undo, which is told as taken back.
     */
    private fun lookDone(context: AnswerEnv, made: HudhudLook.Made): AnswerCard {
        val setting = made.setting
        return AnswerCard(
            Mood.CALM,
            label = context.getString(lookName(setting)),
            // What the phone says now, not what the order said: the card never claims a value the phone is not at.
            figure = context.getString(lookWord(setting, lookValue(context, setting))),
            lines = listOf(
                if (made.undone) context.getString(W.hudhud_look_taken_back)
                else context.getString(W.hudhud_look_was, context.getString(lookWord(setting, made.from)))
            ),
            lookUndo = if (made.undone) null else made.reversed()
        )
    }

    /** One of his acts: the sheet plays it; the card says it in a word. */
    /**
     * A change to how he speaks (14 Nov 2026): the sheet makes it ([AnswerCard.voice]) and the card says it in one line;
     * "again" is answered by the sheet with the last answer (this line only when there is none yet).
     */
    private fun voiceChange(context: AnswerEnv, change: Hudhud.VoiceChange): AnswerCard = AnswerCard(
        Mood.CALM,
        lines = listOf(context.getString(
            when (change) {
                Hudhud.VoiceChange.AGAIN -> W.hudhud_v_again_none
                Hudhud.VoiceChange.SLOWER -> W.hudhud_v_slower
                Hudhud.VoiceChange.FASTER -> W.hudhud_v_faster
                Hudhud.VoiceChange.QUIET -> W.hudhud_v_quiet
                Hudhud.VoiceChange.OFF -> W.hudhud_v_off
                Hudhud.VoiceChange.ON -> W.hudhud_v_on
                Hudhud.VoiceChange.VOLUME -> W.hudhud_v_volume
            }
        )),
        quiet = change == Hudhud.VoiceChange.QUIET || change == Hudhud.VoiceChange.OFF || change == Hudhud.VoiceChange.VOLUME,
        voice = change
    )

    private fun act(context: AnswerEnv, move: Hudhud.Move): AnswerCard = AnswerCard(
        Mood.GOOD,
        lines = listOf(context.getString(
            when (move) {
                Hudhud.Move.FLY -> W.hudhud_act_fly
                Hudhud.Move.DANCE -> W.hudhud_act_dance
                Hudhud.Move.JUMP -> W.hudhud_act_jump
            }
        )),
        act = move
    )

    /**
     * What he can tell (13 Nov 2026, the owner: "aap kya bata sakte ho" — the list): what this person may see of it,
     * as lines; the chips under the card ask each of them in a tap. Said aloud, one sentence pointing at the list.
     */
    private fun abilities(context: AnswerEnv): AnswerCard {
        val reports = AnswerGate.reports()
        val lines = ArrayList<String>()
        lines += context.getString(W.hudhud_can_intro)
        if (reports) {
            lines += "\u2022 " + context.getString(W.hudhud_can_dues)
            lines += "\u2022 " + context.getString(W.hudhud_can_sales)
            lines += "\u2022 " + context.getString(W.hudhud_can_drawer)
            if (context.radarOn()) lines += "\u2022 " + context.getString(W.hudhud_can_pay)
        }
        if (!context.viewer()) lines += "\u2022 " + context.getString(W.hudhud_can_entry)
        // 13 Nov 2026 (Brain v2): a customer's facts, goods, what happened, the shop and the day.
        lines += "\u2022 " + context.getString(W.hudhud_can_people)
        lines += "\u2022 " + context.getString(W.hudhud_can_goods)
        if (reports) lines += "\u2022 " + context.getString(W.hudhud_can_book)
        lines += "\u2022 " + context.getString(W.hudhud_can_shop)
        lines += "\u2022 " + context.getString(W.hudhud_can_look)
        lines += "\u2022 " + context.getString(W.hudhud_can_more)
        lines += "\u2022 " + context.getString(W.hudhud_can_chat)
        return AnswerCard(Mood.GOOD, lines = lines)
    }

    /**
     * A little talk (13 Nov 2026, the owner: haal chaal, "bade pyaar se"): a warm short reply that turns back to the
     * shop. A salaam with it is answered first.
     */
    private fun chat(context: AnswerEnv, ask: Hudhud.Ask.Chat): AnswerCard {
        fun words(w: AnswerEnv): String {
            val reply = when (ask.talk) {
                Hudhud.Talk.HOW_ARE_YOU -> w.getString(W.hudhud_chat_how)
                Hudhud.Talk.WHO_ARE_YOU -> w.getString(W.hudhud_chat_who)
                Hudhud.Talk.THANKS -> w.getString(W.hudhud_chat_thanks)
                Hudhud.Talk.BYE -> w.getString(W.hudhud_chat_bye)
                Hudhud.Talk.PRAISE -> w.getString(W.hudhud_chat_praise)
                Hudhud.Talk.FINE -> w.getString(W.hudhud_chat_fine)
                Hudhud.Talk.CAN_TALK -> w.getString(W.hudhud_chat_can_talk)
                // Only called ("bhai jaan", "Hudhud"): he answers to it, with the owner's name when he gave one.
                Hudhud.Talk.ADDRESS -> addressed(w, W.hudhud_chat_address)
            }
            return (if (ask.salaam) addressed(w, W.hudhud_salaam_reply) + " " else "") + reply
        }
        val mood = when (ask.talk) {
            Hudhud.Talk.WHO_ARE_YOU, Hudhud.Talk.BYE, Hudhud.Talk.ADDRESS, Hudhud.Talk.CAN_TALK -> Mood.CALM
            else -> Mood.GOOD
        }
        return AnswerCard(mood, lines = listOf(words(context)), spoken = { w -> words(w) })
    }

    /**
     * A reply that turns to the owner by the name he gave ("Ji, Ali bhai. Farmaiye.", 13 Nov 2026); the words alone
     * when he gave none. [words] has the name's place as its first argument, and a form of its own without it.
     */
    private fun addressed(w: AnswerEnv, words: String): String {
        val name = w.callMe() ?: return w.getString(words)
        val named = when (words) {
            W.hudhud_chat_address -> W.hudhud_chat_address_named
            W.hudhud_salaam_reply -> W.hudhud_salaam_reply_named
            else -> return w.getString(words)
        }
        return w.getString(named, Format.isolate(name))
    }

    /**
     * What he does not know: one line, said whole, that the nearest questions he can answer are the chips under it
     * ([Hudhud.suggest], from the words he was given).
     */
    internal fun unknown(context: AnswerEnv) = AnswerCard(Mood.CALM, lines = listOf(context.getString(W.hudhud_unknown)))

    /**
     * Off the book (16 Nov 2026, audit T7): a sum opens the calculator (with the calculator's own answer when it was written
     * with signs: "2+2 = 4"); any other topic is refused plainly, with what he can tell offered as a chip. Never an entry.
     */
    private fun offTopic(context: AnswerEnv, ask: Hudhud.Ask.OffTopic): AnswerCard = when (ask.why) {
        Hudhud.OffTopicWhy.SUM -> AnswerCard(
            Mood.CALM,
            label = ask.sum?.let { Format.ltr(it) },
            figure = ask.result?.let { Format.ltr(com.innovation313.roshankhata.ui.Calc.trim(it)) },
            lines = listOf(context.getString(W.hudhud_sum_here)),
            open = ScreenId.CALCULATOR
        )
        Hudhud.OffTopicWhy.GENERAL -> AnswerCard(
            Mood.CALM,
            lines = listOf(context.getString(W.hudhud_off_topic)),
            options = listOf(Hudhud.Ask.Abilities)
        )
    }

    /** The words each of VoiceQuestion's figures goes by. */
    fun labelOf(kind: VoiceQuestion.Kind): String = when (kind) {
        VoiceQuestion.Kind.PROFIT_MONTH -> W.vq_profit_month
        VoiceQuestion.Kind.EXPIRING -> W.vq_expiring
        VoiceQuestion.Kind.NOT_PAID -> W.vq_not_paid
        VoiceQuestion.Kind.WHO_OWES_MOST -> W.vq_who_owes_most
        VoiceQuestion.Kind.WHO_OWES_LEAST -> W.vq_who_owes_least
        VoiceQuestion.Kind.SALES_TODAY -> W.vq_sales_today
        VoiceQuestion.Kind.SALES_MONTH -> W.vq_sales_month
        VoiceQuestion.Kind.TO_GIVE -> W.vq_to_give
        VoiceQuestion.Kind.TO_GET -> W.vq_to_get
    }

    // ---------------------------------------------------------------------------------------------- the figures

    private suspend fun figure(
        context: AnswerEnv, dao: KhataDao, kind: VoiceQuestion.Kind, parties: List<PartyWithBalance>
    ): AnswerCard {
        val labelRes = labelOf(kind)
        val label = context.getString(labelRes)
        if (!AnswerGate.reports()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.hudhud_owner_only)))
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
            VoiceQuestion.Kind.SALES_TODAY -> sum(labelRes, label, dao.salesTotalBetween(dayStart, dayEnd), ScreenId.INSIGHTS)
            VoiceQuestion.Kind.SALES_MONTH -> sum(labelRes, label, dao.salesTotalBetween(monthStart, monthEnd), ScreenId.INSIGHTS)
            VoiceQuestion.Kind.TO_GET -> sum(labelRes, label, PartyTotals.of(parties.map { it.balance }).get, ScreenId.KHATA, calm = true)
            VoiceQuestion.Kind.TO_GIVE -> sum(labelRes, label, PartyTotals.of(parties.map { it.balance }).give, ScreenId.KHATA, calm = true)
            VoiceQuestion.Kind.PROFIT_MONTH -> profit(context, labelRes, label, Profit.thisMonth(dao), ScreenId.INSIGHTS)
            VoiceQuestion.Kind.WHO_OWES_MOST -> {
                val top = parties.filter { Money.isPositive(it.balance) }.sortedByDescending { it.balance }.take(TOP)
                if (top.isEmpty()) {
                    AnswerCard(Mood.GOOD, label = label, lines = listOf(context.getString(W.vq_nobody)))
                } else {
                    val first = top.first()
                    AnswerCard(
                        Mood.CALM, label = label,
                        rows = top.map { Row(it.id, it.name, money(it.balance), null) },
                        open = ScreenId.KHATA, money = true
                    )
                }
            }
            // The other end of the same list (14 Nov 2026, the owner's check logs: "kisi ne ... sab se kam dene hain"): the
            // smallest balance still owed to the shop, the same accounts and the same rule as who owes the most.
            VoiceQuestion.Kind.WHO_OWES_LEAST -> {
                val low = parties.filter { Money.isPositive(it.balance) }.sortedBy { it.balance }.take(TOP)
                if (low.isEmpty()) {
                    AnswerCard(Mood.GOOD, label = label, lines = listOf(context.getString(W.vq_nobody)))
                } else {
                    AnswerCard(
                        Mood.CALM, label = label,
                        rows = low.map { Row(it.id, it.name, money(it.balance), null) },
                        open = ScreenId.KHATA, money = true
                    )
                }
            }
            VoiceQuestion.Kind.NOT_PAID -> AnswerCard(Mood.CALM, label = label,
                lines = listOf(context.getString(W.vq_not_paid_body)), open = ScreenId.FOLLOW_UP)
            VoiceQuestion.Kind.EXPIRING -> AnswerCard(Mood.CALM, label = label,
                lines = listOf(context.getString(W.vq_expiring_body)), open = ScreenId.EXPIRING)
        }
    }

    /** A profit with its heading, as Insights says it: its share of the sales it stands on, or that none is known. */
    private fun profit(context: AnswerEnv, labelRes: String, label: String, r: Profit.ProfitReport, open: ScreenId?): AnswerCard =
        if (r.isEmpty) {
            AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.insights_profit_none)), open = open)
        } else {
            AnswerCard(
                if (r.grossProfit < 0) Mood.CARE else Mood.GOOD,
                label = label,
                figure = money(r.grossProfit),
                lines = listOfNotNull(r.coveragePercent?.let { context.getString(W.insights_profit_coverage, it) }),
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
    private suspend fun period(context: AnswerEnv, dao: KhataDao, measure: Hudhud.Measure, window: VoiceQuestion.Window): AnswerCard {
        val labelRes = periodLabel(measure, window)
        val label = context.getString(labelRes)
        if (!AnswerGate.reports()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.hudhud_owner_only)))
        }
        val (from, to) = Hudhud.span(window)
        return when (measure) {
            Hudhud.Measure.SALES -> sum(labelRes, label, dao.salesTotalBetween(from, to), ScreenId.SALES_REPORT)
                .copy(openWith = salesReportDays(window, from, to, if (window == VoiceQuestion.Window.ALL_TIME) dao.firstSaleAt() else null))
            Hudhud.Measure.PROFIT -> when (window) {
                VoiceQuestion.Window.THIS_MONTH -> profit(context, labelRes, label, Profit.thisMonth(dao), ScreenId.INSIGHTS)
                VoiceQuestion.Window.LAST_MONTH -> profit(context, labelRes, label, Profit.lastMonth(dao), ScreenId.INSIGHTS)
                else -> profit(context, labelRes, label, Profit.between(dao, from, to), null)
            }
        }
    }

    /** The sales report's own period for a span, or its first and last day as the range picker gives them. */
    private fun salesReportDays(window: VoiceQuestion.Window, from: Long, to: Long, firstSale: Long? = null): OpenWith = when (window) {
        VoiceQuestion.Window.TODAY -> OpenWith.SalesPeriod(ReportPeriod.TODAY.name)
        VoiceQuestion.Window.THIS_WEEK -> OpenWith.SalesPeriod(ReportPeriod.WEEK.name)
        VoiceQuestion.Window.THIS_MONTH -> OpenWith.SalesPeriod(ReportPeriod.MONTH.name)
        else -> OpenWith.SalesDays(firstSale?.let { DayMath.startOfDay(it) } ?: from, DayMath.startOfDay(to - 1))
    }

    /** The heading of a sale or a profit over a span. */
    internal fun periodLabel(measure: Hudhud.Measure, window: VoiceQuestion.Window): String = when (measure) {
        Hudhud.Measure.SALES -> when (window) {
            VoiceQuestion.Window.TODAY -> W.vq_sales_today
            VoiceQuestion.Window.YESTERDAY -> W.hudhud_sales_yesterday
            VoiceQuestion.Window.THIS_WEEK -> W.hudhud_sales_week
            VoiceQuestion.Window.LAST_WEEK -> W.hudhud_sales_last_week
            VoiceQuestion.Window.THIS_MONTH -> W.vq_sales_month
            VoiceQuestion.Window.LAST_MONTH -> W.hudhud_sales_last_month
            VoiceQuestion.Window.LAST_6_MONTHS -> W.hudhud_sales_6_months
            VoiceQuestion.Window.THIS_YEAR -> W.hudhud_sales_this_year
            VoiceQuestion.Window.LAST_YEAR -> W.hudhud_sales_last_year
            VoiceQuestion.Window.ALL_TIME -> W.hudhud_sales_all_time
        }
        Hudhud.Measure.PROFIT -> when (window) {
            VoiceQuestion.Window.TODAY -> W.hudhud_profit_today
            VoiceQuestion.Window.YESTERDAY -> W.hudhud_profit_yesterday
            VoiceQuestion.Window.THIS_WEEK -> W.hudhud_profit_week
            VoiceQuestion.Window.LAST_WEEK -> W.hudhud_profit_last_week
            VoiceQuestion.Window.THIS_MONTH -> W.vq_profit_month
            VoiceQuestion.Window.LAST_MONTH -> W.hudhud_profit_last_month
            VoiceQuestion.Window.LAST_6_MONTHS -> W.hudhud_profit_6_months
            VoiceQuestion.Window.THIS_YEAR -> W.hudhud_profit_this_year
            VoiceQuestion.Window.LAST_YEAR -> W.hudhud_profit_last_year
            VoiceQuestion.Window.ALL_TIME -> W.hudhud_profit_all_time
        }
    }

    /** A span of days in the date filter's own words. */
    internal fun windowWord(window: VoiceQuestion.Window): String = when (window) {
        VoiceQuestion.Window.TODAY -> W.range_today
        VoiceQuestion.Window.YESTERDAY -> W.range_yesterday
        VoiceQuestion.Window.THIS_WEEK -> W.range_this_week
        VoiceQuestion.Window.LAST_WEEK -> W.range_week
        VoiceQuestion.Window.THIS_MONTH -> W.range_this_month
        VoiceQuestion.Window.LAST_MONTH -> W.range_month
        VoiceQuestion.Window.LAST_6_MONTHS -> W.hudhud_win_6_months
        VoiceQuestion.Window.THIS_YEAR -> W.hudhud_win_this_year
        VoiceQuestion.Window.LAST_YEAR -> W.hudhud_win_last_year
        VoiceQuestion.Window.ALL_TIME -> W.hudhud_win_all_time
    }

    /**
     * What one customer paid ([got]) or took over a span ("Ali ne kal kitna diya"): his own live lines in it, I got or
     * I gave, every one as his page shows it (a paid-now sale's payment and a loan included: money that moved).
     */
    private suspend fun moved(
        context: AnswerEnv, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window, got: Boolean,
        parties: List<PartyWithBalance>
    ): AnswerCard {
        val (from, to) = Hudhud.span(window)
        return movedOver(context, dao, ids, from, to, context.getString(windowWord(window)), got, parties)
    }

    /** [moved] over [from, to), the span said in [words] (16 Nov 2026: also a day or a month named outright, audit T31). */
    private suspend fun movedOver(
        context: AnswerEnv, dao: KhataDao, ids: List<Long>, from: Long, to: Long, words: String, got: Boolean,
        parties: List<PartyWithBalance>
    ): AnswerCard {
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return unknown(context)
        if (found.size > 1) return pick(context, found)
        val p = found.first()
        val total = LedgerLines.of(dao, listOf(p.id))
            .filter { it.timestamp >= from && it.timestamp < to && it.isGiven == !got }
            .sumOf { it.amount }
        val way = if (got) W.i_got else W.i_gave
        return AnswerCard(
            Mood.CALM,
            label = p.name,
            figure = money(total),
            lines = listOf(context.getString(way) + " · " + words),
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
    private fun entry(context: AnswerEnv, ask: Hudhud.Ask.Entry, parties: List<PartyWithBalance>): AnswerCard {
        val way = context.getString(if (ask.isGiven) W.i_gave else W.i_got)
        if (context.viewer()) {
            return AnswerCard(Mood.CALM, label = way, figure = money(ask.amount), lines = listOf(context.getString(W.viewer_refused)))
        }
        if (parties.isEmpty()) {
            return AnswerCard(Mood.CALM, label = way, figure = money(ask.amount),
                lines = listOf(context.getString(W.voice_no_party)), open = ScreenId.KHATA)
        }
        val found = ask.ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.size == 1) {
            val p = found.first()
            return AnswerCard(
                Mood.CALM,
                label = p.name,
                figure = money(ask.amount),
                lines = listOf(way, context.getString(W.hudhud_entry_check)),
                openParty = p.id,
                openWith = entryForm(ask.amount, ask.isGiven),
                openLabel = W.hudhud_fill_entry,
                money = true
            )
        }
        // As voice entry's own picker: the names the words recognise, else the whole book nearest the words first
        // (recent dealings first among equals); one tap opens the form filled in for the one chosen.
        val choices = found.ifEmpty {
            NameSearch.rankSpoken(parties.sortedByDescending { it.lastActivity }, ask.nameWords) { it.name }.take(PICK)
        }
        return AnswerCard(
            Mood.CALM,
            label = context.getString(W.voice_which_party, Format.moneyLtr(ask.amount)),
            lines = listOf(way),
            rows = choices.map {
                Row(it.id, it.name, money(abs(it.balance)), side(context, it.balance), amount = ask.amount, isGiven = ask.isGiven)
            },
            money = true, quiet = true
        )
    }

    /** What opens the customer's entry form filled in: voice entry's own extras. */
    fun entryForm(amount: Double, isGiven: Boolean): OpenWith = OpenWith.EntryForm(amount, isGiven)

    // ------------------------------------------------------------------------------------- when he will pay

    /**
     * "Ali kab dega?" by the Payment Radar, by its own rules: only for the owner and a manager (REPORTS), only where
     * the Radar is on (never on a helper's phone), only for customers; the reading from his own lines, his promised day
     * and the promises he was given (PromiseLog), against the shop's normal — the reasons RadarUi gives on his page. He
     * says aloud only the likely days, never the judgement: a customer may be standing at the counter. With no name,
     * who is in his usual paying days today (the morning list's rule).
     */
    private suspend fun whenPays(context: AnswerEnv, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): AnswerCard {
        val label = context.getString(W.radar_title)
        if (!AnswerGate.reports()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.hudhud_owner_only)))
        }
        if (!context.radarOn()) return AnswerCard(Mood.CALM, label = label, lines = listOf(context.getString(W.radar_state_off)))
        val now = System.currentTimeMillis()
        if (ids.isEmpty()) {
            val promises = dao.observePromises().first().associate { it.partyId to it.due }
            val likely = PaymentRadar.likelyNow(LedgerLines.owingOf(dao, parties), context.radarShopDays(dao), promises, now)
            val rows = parties.filter { it.id in likely }.sortedByDescending { it.balance }.take(TOP)
            val heading = context.getString(W.radar_likely_now)
            if (rows.isEmpty()) {
                return AnswerCard(Mood.CALM, label = heading, lines = listOf(context.getString(W.hudhud_likely_none)),
                    open = ScreenId.FOLLOW_UP)
            }
            return AnswerCard(
                Mood.GOOD, label = heading,
                rows = rows.map { Row(it.id, it.name, money(it.balance), null) },
                open = ScreenId.FOLLOW_UP
            )
        }
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return unknown(context)
        if (found.size > 1) return pick(context, found)
        val p = found.first()
        if (!p.isCustomer) {
            return AnswerCard(Mood.CALM, label = p.name, figure = money(abs(p.balance)),
                lines = listOf(side(context, p.balance), context.getString(W.hudhud_radar_customers)),
                openParty = p.id, money = true)
        }
        val promise = dao.getParty(p.id)?.harvestPromise
        val promises = runCatching { PromiseLog.of(dao.promiseLog(p.id)) }.getOrDefault(emptyList())
        val r = PaymentRadar.read(LedgerLines.of(dao, listOf(p.id)), now, context.radarShopDays(dao), promise, promises)
        val from = r.nextFrom
        val to = r.nextTo
        fun likely(w: AnswerEnv): String? =
            if (from != null && to != null) w.getString(W.radar_next_short, shortDay(from), shortDay(to)) else null
        val mood = when (r.light) {
            PaymentRadar.Light.GREEN -> Mood.GOOD
            PaymentRadar.Light.AMBER, PaymentRadar.Light.RED -> Mood.CARE
            PaymentRadar.Light.NONE -> Mood.CALM
        }
        return AnswerCard(
            mood,
            label = p.name,
            lines = listOfNotNull(likely(context), context.radarReasons(r, promise, context.toneThatWorked(p.id, r.balance))),
            openParty = p.id,
            spoken = { w ->
                p.name + ": " + (likely(w) ?: w.getString(if (r.light == PaymentRadar.Light.NONE) W.radar_new else W.hudhud_radar_see))
            }
        )
    }

    private fun shortDay(t: Long) = DateWords.format("d MMM", t)

    /**
     * Several customers the words could be: each offered, his page a tap away. For the private note (14 Nov 2026) a tap
     * asks for that one's note instead — the note is what was asked, and Hudhud tells it, he does not open a page.
     */
    internal fun pick(context: AnswerEnv, found: List<PartyWithBalance>, facet: Hudhud.Facet? = null) = AnswerCard(
        Mood.CALM,
        label = context.getString(W.hudhud_pick_party),
        rows = found.map {
            Row(it.id, it.name, money(abs(it.balance)), side(context, it.balance),
                ask = if (facet == Hudhud.Facet.NOTE) Hudhud.Ask.Person(listOf(it.id), Hudhud.Facet.NOTE) else null)
        },
        quiet = true
    )

    // ------------------------------------------------------------------------------------------------ today

    /**
     * What needs the owner today, under the morning's greeting: whom to remind (Follow-up's own rule, the 9 o'clock
     * notice's: [Automations.collect]; the first three named, with who is in his usual paying days where the Radar is
     * the owner's to see) and the bell's own rows (HomeAlerts, less "the app was updated"). Said aloud, it is the
     * greeting and one line of counts — never an amount.
     */
    private suspend fun today(context: AnswerEnv, dao: KhataDao): AnswerCard {
        val now = System.currentTimeMillis()
        val radar = AnswerGate.reports() && context.radarOn()
        val collect = if (context.viewer()) null
        else runCatching { Automations.collect(dao, context.remindedToday(now), now, radar = radar) }.getOrNull()
        val alerts = runCatching { context.alerts(dao) }.getOrDefault(emptyList())
            .filter { it.kind != AnswerAlert.Kind.UPDATED }
        val remind = collect?.count ?: 0
        val lines = ArrayList<String>()
        if (remind > 0) lines += context.quantity(WP.hudhud_greet_remind, remind, remind)
        alerts.forEach { lines += "• " + it.title }
        if (lines.isEmpty()) lines += context.getString(W.hudhud_greet_clear)
        val likely = collect?.likelyIds.orEmpty()
        val rows = collect?.top.orEmpty().map {
            Row(it.id, it.name, money(it.balance), if (it.id in likely) context.getString(W.radar_likely_now) else null)
        }
        return AnswerCard(
            if (remind == 0 && alerts.isEmpty()) Mood.GOOD else Mood.CALM,
            label = greeting(context, now),
            lines = lines,
            rows = rows,
            open = when {
                remind > 0 -> ScreenId.FOLLOW_UP
                alerts.isNotEmpty() -> ScreenId.ALERTS
                else -> null
            },
            spoken = { w -> greeting(w, now) + " " + headline(w, remind, alerts.size) }
        )
    }

    /**
     * The morning bubble's line on Home (counts only, never an amount: balances may be hidden): whom to remind today,
     * else how many rows the bell has, else that nothing is waiting. [bell] is the bell's own count.
     */
    suspend fun greetingLine(context: AnswerEnv, dao: KhataDao, bell: Int): String {
        val now = System.currentTimeMillis()
        val remind = if (context.viewer()) 0
        else runCatching { Automations.collect(dao, context.remindedToday(now), now).count }.getOrDefault(0)
        return headline(context, remind, bell)
    }

    private fun headline(context: AnswerEnv, remind: Int, bell: Int): String = when {
        remind > 0 -> context.quantity(WP.hudhud_greet_remind, remind, remind)
        bell > 0 -> context.quantity(WP.hudhud_greet_alerts, bell, bell)
        else -> context.getString(W.hudhud_greet_clear)
    }

    /**
     * The greeting by the shop's country and the hour ([Hudhud.greeting], the owner's rule), with what the owner asked
     * to be called when he gave it ("Assalam-o-Alaikum, Ali bhai!", 13 Nov 2026).
     */
    fun greeting(context: AnswerEnv, now: Long = System.currentTimeMillis()): String {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val word = context.getString(
            when (Hudhud.greeting(context.profile.country(), hour)) {
                Hudhud.Greeting.SALAAM -> W.hudhud_greet_salaam
                Hudhud.Greeting.MORNING -> W.hudhud_greet_morning
                Hudhud.Greeting.AFTERNOON -> W.hudhud_greet_afternoon
                Hudhud.Greeting.EVENING -> W.hudhud_greet_evening
            }
        )
        val name = context.callMe() ?: return word
        return context.getString(W.hudhud_greet_named, word.trimEnd('!', '\uFF01', ' '), Format.isolate(name))
    }

    /** An amount with its heading: glad of more than nothing unless it is what the shop owes ([calm]). */
    private fun sum(labelRes: String, label: String, value: Double, open: ScreenId, calm: Boolean = false) = AnswerCard(
        if (!calm && Money.isPositive(value)) Mood.GOOD else Mood.CALM,
        label = label,
        figure = money(value),
        open = open,
        money = true
    )

    // -------------------------------------------------------------------------------------- customers by name

    private fun named(context: AnswerEnv, ids: List<Long>, parties: List<PartyWithBalance>): AnswerCard {
        val byId = parties.associateBy { it.id }
        val found = ids.mapNotNull { byId[it] }
        if (found.isEmpty()) return unknown(context)
        if (found.size == 1) {
            val p = found.first()
            return AnswerCard(
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
    internal fun side(context: AnswerEnv, balance: Double): String = context.getString(
        when {
            Money.isPositive(balance) -> W.you_will_get
            Money.isNegative(balance) -> W.you_will_give
            else -> W.settled
        }
    )

    // ------------------------------------------------------------------------------------- backup and drawer

    /** The backup as the bell judges it: another phone's, a failed one, none, a week old, or in order. */
    private fun backup(context: AnswerEnv): AnswerCard {
        val label = context.getString(W.coach_title_backup)
        val last = context.lastBackupAt()
        val other = context.otherPhoneAt()
        val failed = context.backupFailedAt()
        fun care(title: String, sub: String) = AnswerCard(
            Mood.CARE, label = label, lines = listOf(title, sub), open = ScreenId.BACKUP
        )
        return when {
            other > last -> care(context.getString(W.notif_backup_other_phone_title), context.getString(W.na_backup_other_phone_sub))
            failed > last -> care(context.getString(W.notif_backup_failed_title), context.getString(W.na_backup_failed_sub))
            last == 0L -> care(context.getString(W.na_backup_never), context.getString(W.na_backup_sub))
            else -> {
                val days = ((System.currentTimeMillis() - last) / DAY_MS).toInt()
                if (days >= OLD_BACKUP_DAYS) {
                    care(context.getString(W.na_backup, days.toString()), context.getString(W.na_backup_sub))
                } else {
                    val line = context.getString(W.business_last_backup, Format.dateOnly(last))
                    AnswerCard(Mood.GOOD, label = label, lines = listOf(line), open = ScreenId.BACKUP)
                }
            }
        }
    }

    /**
     * What should be in the drawer now, by Close the day's own rule, and since when it is counted. Behind REPORTS like
     * every figure: told to a cashier before he counts, the expected cash says how much could go missing unseen.
     */
    private suspend fun drawer(context: AnswerEnv, dao: KhataDao): AnswerCard {
        if (!AnswerGate.reports()) {
            return AnswerCard(Mood.CALM, label = context.getString(W.z_expected),
                lines = listOf(context.getString(W.hudhud_owner_only)))
        }
        val f = DayCloseMath.drawerByTheBooks(dao)
        val last = f.last
        val since = if (last == null) context.getString(W.dayclose_since_first)
        else context.getString(W.dayclose_since_last, Format.dateTime(last.closedAt))
        val expected = f.expected
        return AnswerCard(
            Mood.CALM,
            label = context.getString(W.z_expected),
            figure = money(expected),
            lines = listOf(since),
            open = ScreenId.CASHBOOK,
            money = true
        )
    }

    // ---------------------------------------------------------------------------------------------- help

    private fun help(context: AnswerEnv, key: String): AnswerCard {
        val page = PAGES.firstOrNull { it.key == key } ?: return unknown(context)
        val limit = AnswerGate.pageLimit(context, page.gate)
        return AnswerCard(
            Mood.CALM,
            label = context.getString(page.title),
            lines = listOf(context.getString(page.body)) + listOfNotNull(limit?.let { context.getString(it) }),
            open = page.open,
            // Said in full when short, else its first sentences and that the rest is on the screen (13 Nov 2026); the
            // limit is said after it either way (15 Nov 2026).
            spoken = { w ->
                HudhudSpeech.upTo(w.getString(page.body), SAY_HELP, w.getString(W.hudhud_more_on_screen)) +
                    (limit?.let { " " + w.getString(it) } ?: "")
            }
        )
    }

    // ------------------------------------------------------------------------------------------ chips

    /** A chip's words for [ask] (13 Nov 2026: next steps, "did you mean", the day's own questions); null: no chip. */
    fun chipLabel(c: AnswerEnv, ask: Hudhud.Ask): String? = when (ask) {
        is Hudhud.Ask.Figure -> c.getString(labelOf(ask.kind))
        is Hudhud.Ask.Period -> c.getString(periodLabel(ask.measure, ask.window))
        Hudhud.Ask.Abilities -> c.getString(W.hudhud_chip_abilities)
        Hudhud.Ask.Today -> c.getString(W.hudhud_chip_today)
        Hudhud.Ask.BackupStatus -> c.getString(W.hudhud_chip_backup)
        Hudhud.Ask.Drawer -> c.getString(W.hudhud_chip_drawer)
        is Hudhud.Ask.WhenPays -> c.getString(if (ask.ids.isEmpty()) W.hudhud_chip_likely else W.hudhud_chip_when_pays)
        is Hudhud.Ask.Chat -> if (ask.talk == Hudhud.Talk.WHO_ARE_YOU) c.getString(W.hudhud_chip_who) else null
        is Hudhud.Ask.About -> when (ask.topic) {
            Hudhud.AboutTopic.OWNER_NAME -> c.getString(W.hudhud_chip_my_name)
            Hudhud.AboutTopic.MAKER -> c.getString(W.hudhud_chip_maker)
            Hudhud.AboutTopic.PRIVACY -> c.getString(W.hudhud_chip_privacy)
            Hudhud.AboutTopic.TAWHEED -> c.getString(W.hudhud_chip_tawheed)
            Hudhud.AboutTopic.HAMD -> c.getString(W.hudhud_chip_hamd)
            Hudhud.AboutTopic.RISALAT -> c.getString(W.hudhud_chip_risalat)
            // The app's own history and his story (14 Nov 2026). The first day of the work has no chip of its own: the
            // journey's card says it.
            Hudhud.AboutTopic.HOOPOE_STORY -> c.getString(W.hudhud_chip_story)
            Hudhud.AboutTopic.APP_PUBLISHED -> c.getString(W.hudhud_chip_published)
            Hudhud.AboutTopic.HUDHUD_ADDED -> c.getString(W.hudhud_chip_hudhud_added)
            Hudhud.AboutTopic.APP_JOURNEY -> c.getString(W.hudhud_chip_history)
            else -> null
        }
        is Hudhud.Ask.Shop -> when (ask.facet) {
            Hudhud.ShopFacet.NAME -> c.getString(W.business_name)
            Hudhud.ShopFacet.PHONE -> c.getString(W.hudhud_chip_shop_phone)
            Hudhud.ShopFacet.ALL -> c.getString(W.hudhud_chip_shop)
            else -> null
        }
        is Hudhud.Ask.NeedName -> c.getString(W.hudhud_chip_someone)
        is Hudhud.Ask.Cashbook -> c.getString(W.hudhud_chip_cashbook)
        is Hudhud.Ask.Returns -> c.getString(W.hudhud_chip_returns)
        is Hudhud.Ask.Guessed -> chipLabel(c, ask.ask)
        is Hudhud.Ask.Now -> chipLabel(c, ask.ask)
        // A span he cannot give, taken as the question it was asked of ("Taken as: Goods that came in").
        is Hudhud.Ask.SpanLimit -> ask.options.firstOrNull()?.let { spanQuestion(c, it) }
        Hudhud.Ask.Clock -> c.getString(W.hudhud_chip_date)
        is Hudhud.Ask.Parties -> if (ask.ids.size == 1) c.getString(W.hudhud_l_balance) else null
        is Hudhud.Ask.Person -> c.getString(
            when (ask.facet) {
                Hudhud.Facet.PHONE -> W.hudhud_l_phone
                Hudhud.Facet.DETAILS -> W.hudhud_chip_details
                Hudhud.Facet.LAST_PAYMENT -> W.hudhud_l_last_payment
                Hudhud.Facet.LAST_ENTRY -> W.hudhud_l_last_entry
                Hudhud.Facet.SINCE -> W.hudhud_l_since
                Hudhud.Facet.LIMIT -> W.credit_limit
                Hudhud.Facet.PROMISE -> W.hudhud_l_promise
                Hudhud.Facet.ENTRIES -> W.hudhud_l_entries
                Hudhud.Facet.NOTE -> W.party_note_label
            }
        )
        is Hudhud.Ask.Goods -> c.getString(
            when (ask.facet) {
                Hudhud.GoodsFacet.OVERVIEW -> W.hudhud_chip_details
                Hudhud.GoodsFacet.PRICE -> W.hudhud_l_price
                Hudhud.GoodsFacet.STOCK -> W.hudhud_l_stock
                Hudhud.GoodsFacet.COST -> W.hudhud_l_cost
                Hudhud.GoodsFacet.SUPPLIERS -> W.hudhud_chip_suppliers
                Hudhud.GoodsFacet.LAST_IN -> W.hudhud_l_last_in
                Hudhud.GoodsFacet.BUYERS -> W.hudhud_chip_buyers
                Hudhud.GoodsFacet.SOLD -> W.hudhud_chip_sold
                Hudhud.GoodsFacet.COVER -> W.hudhud_chip_cover
                Hudhud.GoodsFacet.LABEL -> W.hudhud_l_reg
                Hudhud.GoodsFacet.BARCODE -> W.csv_col_barcode
                Hudhud.GoodsFacet.PACK -> W.hudhud_l_pack
                Hudhud.GoodsFacet.TAX -> W.product_tax_rate
                Hudhud.GoodsFacet.SHELF -> W.hudhud_l_shelf
                Hudhud.GoodsFacet.ADDED -> W.hudhud_l_added
                Hudhud.GoodsFacet.BATCHES -> W.inspector_count_batches
                Hudhud.GoodsFacet.SCHEME -> W.schemes_title
            }
        )
        is Hudhud.Ask.Count -> {
            val title = when (ask.thing) {
                Hudhud.Thing.CUSTOMERS -> W.hudhud_t_customers
                Hudhud.Thing.SUPPLIERS -> W.hudhud_t_suppliers
                Hudhud.Thing.PRODUCTS -> W.tile_products
                Hudhud.Thing.STAFF -> W.hudhud_t_staff
                Hudhud.Thing.ENTRIES -> W.hudhud_l_entries
                Hudhud.Thing.INVOICES -> W.invoices
                Hudhud.Thing.BILLS -> W.supplier_bills
                Hudhud.Thing.TICKETS -> W.hudhud_t_tickets
                Hudhud.Thing.CALCULATIONS -> W.hudhud_t_calculations
                Hudhud.Thing.APP_OPENS -> W.hudhud_t_app_opens
            }
            val w = ask.window
            if (w == null) c.getString(title) else c.getString(title) + " \u00B7 " + c.getString(windowWord(w))
        }
        is Hudhud.Ask.Status -> c.getString(
            when (ask.of) {
                Hudhud.StatusOf.CHEQUES -> W.cheques
                Hudhud.StatusOf.PLANS -> W.nav_plans
                Hudhud.StatusOf.BILLS -> W.supplier_bills
                Hudhud.StatusOf.LOW_STOCK -> W.hudhud_t_low
                Hudhud.StatusOf.BOOK -> W.hudhud_chip_book
                Hudhud.StatusOf.STAFF -> W.hudhud_t_salary
                Hudhud.StatusOf.INVOICES -> W.hudhud_t_invoices_due
            }
        )
        is Hudhud.Ask.Paper -> c.getString(
            when (ask.of) {
                Hudhud.PaperOf.BILL -> W.hudhud_t_last_bill
                Hudhud.PaperOf.ORDER -> W.hudhud_t_last_order
                Hudhud.PaperOf.INVOICE -> W.hudhud_t_last_invoice
                Hudhud.PaperOf.SCHEMES -> W.schemes_title
            }
        )
        is Hudhud.Ask.Deliveries -> c.getString(W.hudhud_t_deliveries)
        is Hudhud.Ask.Activity -> c.getString(W.hudhud_chip_activity)
        is Hudhud.Ask.Collected -> c.getString(W.hudhud_chip_collected)
        is Hudhud.Ask.TopGoods -> c.getString(W.hudhud_t_top_goods)
        Hudhud.Ask.Qarz -> c.getString(W.hudhud_t_qarz)
        Hudhud.Ask.Automations -> c.getString(W.auto_title)
        is Hudhud.Ask.OffTopic -> null
        // A value of the look as a chip: "Text size Large", "Theme Dark" (the others, a step and "show", have none).
        is Hudhud.Ask.Look -> when (val r = ask.request) {
            is HudhudLook.Request.SizeTo -> lookChip(c, r.setting, r.level)
            is HudhudLook.Request.ThemeTo -> lookChip(c, r.setting, r.mode)
            else -> null
        }
        else -> null
    }

    private fun lookChip(c: AnswerEnv, setting: HudhudLook.Setting, value: Int): String =
        c.getString(lookName(setting)) + " " + c.getString(lookWord(setting, value))

    /** The look's other values as questions, one tap each: asked after a card about the setting. */
    private fun lookChips(c: AnswerEnv, setting: HudhudLook.Setting): List<Hudhud.Ask> =
        HudhudLook.others(setting, lookValue(c, setting)).map {
            Hudhud.Ask.Look(if (setting == HudhudLook.Setting.SIZE) HudhudLook.Request.SizeTo(it) else HudhudLook.Request.ThemeTo(it))
        }

    /**
     * The Personal tab's questions (14 Nov 2026, the owner: "personal tab ho, highlight na ho; personal cheezein personal hi
     * rahein"): Hudhud's own story and the three of faith. They are on that quiet tab only: no main chip, no card, no
     * notification and no screen leads to them, and the shop's figures are never beside them.
     */
    fun personalChips(): List<Hudhud.Ask> = listOf(
        Hudhud.Ask.About(Hudhud.AboutTopic.HOOPOE_STORY), Hudhud.Ask.About(Hudhud.AboutTopic.TAWHEED),
        Hudhud.Ask.About(Hudhud.AboutTopic.HAMD), Hudhud.Ask.About(Hudhud.AboutTopic.RISALAT)
    )

    /**
     * What to ask next after [ask] (13 Nov 2026, the owner: "recommendation automatically pick kare"): the questions
     * that follow from this answer, each one tap — about the same customer or product, the same figure on another day,
     * or the page beside it. Figures behind REPORTS are offered only to those who may see them.
     */
    fun next(c: AnswerEnv, ask: Hudhud.Ask): List<Hudhud.Ask> {
        val reports = AnswerGate.reports()
        val radar = reports && c.radarOn()
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
                listOf(Hudhud.GoodsFacet.PRICE, Hudhud.GoodsFacet.STOCK, Hudhud.GoodsFacet.COVER, Hudhud.GoodsFacet.SUPPLIERS, Hudhud.GoodsFacet.BUYERS, Hudhud.GoodsFacet.LAST_IN)
                    .filter { it != ask.facet }.map { Hudhud.Ask.Goods(listOf(id), it) }
            }.orEmpty()
            is Hudhud.Ask.Figure -> when (ask.kind) {
                VoiceQuestion.Kind.SALES_TODAY -> listOf(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH), Hudhud.Ask.Collected(today), Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH))
                VoiceQuestion.Kind.SALES_MONTH -> listOf(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.LAST_MONTH),
                    Hudhud.Ask.TopGoods(VoiceQuestion.Window.THIS_MONTH), Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
                VoiceQuestion.Kind.PROFIT_MONTH -> listOf(Hudhud.Ask.Period(Hudhud.Measure.PROFIT, VoiceQuestion.Window.LAST_MONTH),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))
                VoiceQuestion.Kind.TO_GET -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), Hudhud.Ask.WhenPays(emptyList()),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID))
                VoiceQuestion.Kind.TO_GIVE -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.BILLS), Hudhud.Ask.Deliveries())
                VoiceQuestion.Kind.WHO_OWES_MOST -> listOf(Hudhud.Ask.WhenPays(emptyList()), Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID),
                    Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
                VoiceQuestion.Kind.NOT_PAID -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), Hudhud.Ask.WhenPays(emptyList()))
                VoiceQuestion.Kind.WHO_OWES_LEAST -> listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
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
                Hudhud.StatusOf.INVOICES -> listOf(Hudhud.Ask.Count(Hudhud.Thing.INVOICES), Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
            }
            is Hudhud.Ask.Deliveries -> listOf(Hudhud.Ask.Status(Hudhud.StatusOf.BILLS), Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK))
            is Hudhud.Ask.Activity -> listOf(Hudhud.Ask.Collected(today), Hudhud.Ask.Deliveries(today), Hudhud.Ask.Count(Hudhud.Thing.INVOICES, today))
            is Hudhud.Ask.Collected -> listOf(Hudhud.Ask.Activity(today), Hudhud.Ask.Drawer, Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))
            Hudhud.Ask.Clock -> listOf(Hudhud.Ask.Today, Hudhud.Ask.Activity(today))
            is Hudhud.Ask.Chat -> if (ask.talk == Hudhud.Talk.WHO_ARE_YOU) {
                listOf(Hudhud.Ask.Abilities, Hudhud.Ask.About(Hudhud.AboutTopic.MAKER), Hudhud.Ask.About(Hudhud.AboutTopic.HUDHUD_ADDED),
                    Hudhud.Ask.About(Hudhud.AboutTopic.PRIVACY))
            } else {
                listOf(Hudhud.Ask.Abilities, Hudhud.Ask.Today)
            }
            is Hudhud.Ask.About -> when (ask.topic) {
                // Faith and the hoopoe's story lead nowhere (14 Nov 2026, the owner: "personal cheezein personal hi rahein"):
                // they are the Personal tab's, and the tab's own chips are what the sheet shows while one of them is up. They
                // never bring the shop's figures into it, and no main chip leads to them.
                Hudhud.AboutTopic.TAWHEED, Hudhud.AboutTopic.HAMD, Hudhud.AboutTopic.RISALAT, Hudhud.AboutTopic.HOOPOE_STORY -> emptyList()
                // The app's own dates lead to each other (the card asked for is taken out by the filter below).
                Hudhud.AboutTopic.APP_STARTED, Hudhud.AboutTopic.APP_PUBLISHED, Hudhud.AboutTopic.HUDHUD_ADDED, Hudhud.AboutTopic.APP_JOURNEY ->
                    listOf(Hudhud.Ask.About(Hudhud.AboutTopic.APP_JOURNEY), Hudhud.Ask.About(Hudhud.AboutTopic.APP_PUBLISHED),
                        Hudhud.Ask.About(Hudhud.AboutTopic.HUDHUD_ADDED), Hudhud.Ask.Abilities)
                else -> listOf(Hudhud.Ask.Abilities, Hudhud.Ask.Chat(Hudhud.Talk.WHO_ARE_YOU), Hudhud.Ask.About(Hudhud.AboutTopic.PRIVACY))
            }
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
            is Hudhud.Ask.Now -> return next(c, ask.ask)
            is Hudhud.Ask.Look -> lookChips(c, ask.request.setting)
            is Hudhud.Ask.LookDone -> lookChips(c, ask.made.setting)
            else -> emptyList()
        }
        val owner = AnswerGate.staff()
        return out.filter { a -> a != ask && (reports || !needsReports(a)) && (owner || !needsOwner(a)) }.distinct().take(MAX_NEXT)
    }

    /** A question whose answer only the owner may see: who works here and what each is owed (Staff is the owner's screen). */
    private fun needsOwner(a: Hudhud.Ask): Boolean = when (a) {
        is Hudhud.Ask.Status -> a.of == Hudhud.StatusOf.STAFF
        is Hudhud.Ask.Count -> a.thing == Hudhud.Thing.STAFF
        is Hudhud.Ask.Record -> a.facet in STAFF_FACETS
        else -> false
    }

    /** The records that are the staff's own: pay, what was paid, who was away, who they are (Staff is the owner's screen). */
    private val STAFF_FACETS = setOf(Hudhud.RecordFacet.STAFF_PAY, Hudhud.RecordFacet.STAFF_PAID, Hudhud.RecordFacet.ATTENDANCE, Hudhud.RecordFacet.STAFF_PERSON)

    /** A question whose answer is a figure only the owner or a manager may see. */
    private fun needsReports(a: Hudhud.Ask): Boolean = when (a) {
        is Hudhud.Ask.Figure, is Hudhud.Ask.Period, is Hudhud.Ask.Collected, is Hudhud.Ask.Activity, Hudhud.Ask.Drawer,
        is Hudhud.Ask.WhenPays, is Hudhud.Ask.Cashbook, is Hudhud.Ask.TopGoods, Hudhud.Ask.Qarz, is Hudhud.Ask.Counter -> true
        is Hudhud.Ask.Status -> a.of == Hudhud.StatusOf.STAFF
        is Hudhud.Ask.Record -> a.facet in STAFF_FACETS
        is Hudhud.Ask.Goods -> a.facet == Hudhud.GoodsFacet.COST || a.facet == Hudhud.GoodsFacet.SUPPLIERS || a.facet == Hudhud.GoodsFacet.SOLD || a.facet == Hudhud.GoodsFacet.COVER
        is Hudhud.Ask.Now -> needsReports(a.ask)
        is Hudhud.Ask.OnDays -> a.of != Hudhud.DatedOf.PAID && a.of != Hudhud.DatedOf.TOOK
        is Hudhud.Ask.Compare -> true
        is Hudhud.Ask.Both -> needsReports(a.first) || needsReports(a.second)
        else -> false
    }

    /**
     * The day's own questions, first among his chips when the sheet opens (13 Nov 2026): what the bell has now — a
     * cheque or a plan due, a bill, low stock, the drawer, the backup, a late payer — asked in one tap.
     */
    suspend fun suggested(c: AnswerEnv, dao: KhataDao): List<Hudhud.Ask> {
        val reports = AnswerGate.reports()
        val alerts = runCatching { c.alerts(dao) }.getOrDefault(emptyList())
        return alerts.mapNotNull { a ->
            when (a.kind) {
                AnswerAlert.Kind.CHEQUE -> Hudhud.Ask.Status(Hudhud.StatusOf.CHEQUES)
                AnswerAlert.Kind.PLAN -> Hudhud.Ask.Status(Hudhud.StatusOf.PLANS)
                AnswerAlert.Kind.BILL -> Hudhud.Ask.Status(Hudhud.StatusOf.BILLS)
                AnswerAlert.Kind.STOCK -> Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK)
                AnswerAlert.Kind.EXPIRING -> if (reports) Hudhud.Ask.Figure(VoiceQuestion.Kind.EXPIRING) else null
                AnswerAlert.Kind.DRAWER -> if (reports) Hudhud.Ask.Drawer else null
                AnswerAlert.Kind.BACKUP, AnswerAlert.Kind.BACKUP_FAILED, AnswerAlert.Kind.BACKUP_OTHER_PHONE -> Hudhud.Ask.BackupStatus
                AnswerAlert.Kind.LATE -> if (reports && c.radarOn()) Hudhud.Ask.WhenPays(emptyList()) else null
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
     * ([AnswerCard.spoken]); else the whole card — the heading with its figure, every line, every row (the name, its
     * figure, what it is) — less what is never said aloud: the [AnswerCard.secrets] (said to be on the screen), and every
     * amount while Home's balance is hidden ([hideMoney]: a customer may be standing at the counter). Null for a quiet
     * answer (a guess, a choice) and when nothing but a heading would be left.
     */
    fun speech(w: AnswerEnv, a: AnswerCard, hideMoney: Boolean): Speech? = speechOf(
        w, a.quiet, a.spoken?.let { own -> { own(w) } }, a.label, a.figure, a.money, a.lines,
        a.rows.map { SpokenRow(it.name, it.figure, it.sub) }, a.secrets, hideMoney
    )

    /** A row as [speechOf] reads it: the name, its figure and what it is. */
    class SpokenRow(val name: String, val figure: String, val sub: String?)

    /**
     * [speech] over a card's parts, so a platform that keeps its own card (the phone's sheet makes a few cards of its own)
     * says it by the one rule: [spoken] is the card's own words already made in the voice's language.
     */
    fun speechOf(
        w: AnswerEnv, quiet: Boolean, spoken: (() -> String)?, label: String?, figure: String?, money: Boolean,
        lines: List<String>, rows: List<SpokenRow>, secrets: List<String>, hideMoney: Boolean
    ): Speech? {
        if (quiet) return null
        spoken?.let { own ->
            val text = runCatching { own() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return Speech(text, false)
        }
        val onScreen = w.getString(W.hudhud_on_screen).trimEnd('.', '\u06D4', '\u0964', '!')
        var leftOut = false
        fun said(text: String?): String? {
            if (text.isNullOrBlank()) return null
            var t = HudhudSpeech.clean(text)
            for (secret in secrets) {
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
        val saidLabel = said(label)
        val saidFigure = if (money && hideMoney && figure != null) { leftOut = true; null } else said(figure)
        when {
            saidLabel != null && saidFigure != null -> parts += "$saidLabel: $saidFigure"
            saidLabel != null -> parts += saidLabel
            saidFigure != null -> parts += saidFigure
        }
        lines.forEach { line -> said(line)?.let { parts += it } }
        for (r in rows) {
            val bits = listOfNotNull(HudhudSpeech.clean(r.name).takeIf { it.isNotBlank() }, said(r.figure), said(r.sub))
            if (bits.isNotEmpty()) parts += bits.joinToString(", ")
        }
        // Only the heading is left (its figure and lines were amounts): nothing worth saying.
        if (parts.isEmpty() || (leftOut && parts.size == 1 && saidLabel != null && saidFigure == null)) return null
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
    private fun sentenceEnd(w: AnswerEnv): String {
        val locale = w.locale
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
