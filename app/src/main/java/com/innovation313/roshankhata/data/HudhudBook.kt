package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
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
import com.innovation313.roshankhata.PlansActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.RecycleBinActivity
import com.innovation313.roshankhata.SnapshotsActivity
import com.innovation313.roshankhata.StaffActivity
import com.innovation313.roshankhata.ui.FeatureIntro
import com.innovation313.roshankhata.ui.Format
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

    /** A customer or supplier on the answer card: tapped, his page opens. */
    data class Row(val partyId: Long, val name: String, val figure: String, val sub: String?)

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
        /** An amount of money: not said aloud while Home's balance is hidden. */
        val money: Boolean = false,
        /** The words to say, written with a context in the voice's language; null says nothing. */
        val spoken: ((Context) -> String)? = null
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
        ) + FeatureIntro.Topic.values().map { Page(FEATURE + it.key, it.title, it.body, screenOf(it)) }
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
                feature = p.key.startsWith(FEATURE)
            )
        }
    }

    private fun localized(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /** What Hudhud says when his sheet opens. */
    fun hello(context: Context) = Answer(Mood.CALM, lines = listOf(context.getString(R.string.hudhud_hello)))

    /** What he says when the book could not be read. */
    fun failed(context: Context) = Answer(Mood.CARE, lines = listOf(context.getString(R.string.hudhud_unknown)))

    suspend fun answer(context: Context, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): Answer =
        when (ask) {
            is Hudhud.Ask.Figure -> figure(context, dao, ask.kind, parties)
            is Hudhud.Ask.Parties -> named(context, ask.ids, parties)
            is Hudhud.Ask.Help -> help(context, ask.key)
            Hudhud.Ask.BackupStatus -> backup(context)
            Hudhud.Ask.Drawer -> drawer(context, dao)
            Hudhud.Ask.Salaam -> Answer(
                Mood.GOOD,
                lines = listOf(context.getString(R.string.hudhud_salaam_reply), context.getString(R.string.hudhud_hello)),
                spoken = { it.getString(R.string.hudhud_salaam_reply) }
            )
            Hudhud.Ask.Hello -> Answer(Mood.CALM, lines = listOf(context.getString(R.string.hudhud_hello)),
                spoken = { it.getString(R.string.hudhud_hello) })
            Hudhud.Ask.Unknown -> unknown(context)
        }

    /** What he does not know: the card points at the chips under it ("You can ask:"); he says only the first sentence. */
    private fun unknown(context: Context) = Answer(
        Mood.CALM, lines = listOf(context.getString(R.string.hudhud_unknown)),
        spoken = { firstSentence(it.getString(R.string.hudhud_unknown)) }
    )

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
            VoiceQuestion.Kind.PROFIT_MONTH -> {
                val r = Profit.thisMonth(dao)
                if (r.isEmpty) {
                    Answer(Mood.CALM, label = label, lines = listOf(context.getString(R.string.insights_profit_none)),
                        open = InsightsActivity::class.java)
                } else {
                    val coverage = r.coveragePercent
                    Answer(
                        if (r.grossProfit < 0) Mood.CARE else Mood.GOOD,
                        label = label,
                        figure = money(r.grossProfit),
                        lines = listOfNotNull(coverage?.let { Digits.string(context.resources, R.string.insights_profit_coverage, it) }),
                        open = InsightsActivity::class.java,
                        money = true,
                        spoken = { w -> w.getString(labelRes) + ": " + said(w, r.grossProfit) }
                    )
                }
            }
            VoiceQuestion.Kind.WHO_OWES_MOST -> {
                val top = parties.filter { Money.isPositive(it.balance) }.sortedByDescending { it.balance }.take(TOP)
                if (top.isEmpty()) {
                    Answer(Mood.GOOD, label = label, lines = listOf(context.getString(R.string.vq_nobody)),
                        spoken = { it.getString(R.string.vq_nobody) })
                } else {
                    val first = top.first()
                    Answer(
                        Mood.CALM, label = label,
                        rows = top.map { Row(it.id, it.name, money(it.balance), null) },
                        open = KhataActivity::class.java, money = true,
                        spoken = { w -> w.getString(labelRes) + ": " + first.name + ", " + said(w, first.balance) }
                    )
                }
            }
            VoiceQuestion.Kind.NOT_PAID -> Answer(Mood.CALM, label = label,
                lines = listOf(context.getString(R.string.vq_not_paid_body)), open = FollowUpActivity::class.java,
                spoken = { it.getString(R.string.vq_not_paid_body) })
            VoiceQuestion.Kind.EXPIRING -> Answer(Mood.CALM, label = label,
                lines = listOf(context.getString(R.string.vq_expiring_body)), open = ExpiringActivity::class.java,
                spoken = { it.getString(R.string.vq_expiring_body) })
        }
    }

    /** An amount with its heading: glad of more than nothing unless it is what the shop owes ([calm]). */
    private fun sum(labelRes: Int, label: String, value: Double, open: Class<*>, calm: Boolean = false) = Answer(
        if (!calm && Money.isPositive(value)) Mood.GOOD else Mood.CALM,
        label = label,
        figure = money(value),
        open = open,
        money = true,
        spoken = { w -> w.getString(labelRes) + ": " + said(w, value) }
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
                money = true,
                spoken = { w -> p.name + ": " + said(w, abs(p.balance)) + ", " + side(w, p.balance) }
            )
        }
        return Answer(
            Mood.CALM,
            label = context.getString(R.string.hudhud_pick_party),
            rows = found.map { Row(it.id, it.name, money(abs(it.balance)), side(context, it.balance)) },
            spoken = { it.getString(R.string.hudhud_pick_party) }
        )
    }

    /** Which way a balance runs, in the Khata list's own words. */
    private fun side(context: Context, balance: Double): String = context.getString(
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
            Mood.CARE, label = label, lines = listOf(title, sub), open = BackupActivity::class.java, spoken = { title }
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
                    Answer(Mood.GOOD, label = label, lines = listOf(line), open = BackupActivity::class.java, spoken = { line })
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
            money = true,
            spoken = { w -> w.getString(R.string.z_expected) + ": " + said(w, expected) }
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
            spoken = { w -> firstSentence(w.getString(page.body)) }
        )
    }

    /** A help page is said up to its first full stop: the rest is on the card. */
    internal fun firstSentence(text: String): String {
        val end = text.indexOfAny(charArrayOf('.', '\u06D4', '\u0964', '?', '\u061F', '!'))
        return if (end in 1 until SAY_AT_MOST) text.substring(0, end + 1) else text.take(SAY_AT_MOST)
    }

    // ------------------------------------------------------------------------------------------ the words

    /** A figure as every screen writes it: the minus outside the left-to-right seal, as Insights writes it. */
    fun money(v: Double): String = (if (v < 0) "\u2212" else "") + Format.moneyLtr(v)

    /** A figure as it is said: "12,400 rupees" for the rupee, else the figure and the book's sign. */
    private fun said(words: Context, v: Double): String {
        val digits = Format.plain(abs(v))
        val amount = if (Currency.symbol == Currency.DEFAULT) words.getString(R.string.paisa_rupees, digits)
        else "$digits ${Currency.symbol}"
        return if (v < 0) "- $amount" else amount
    }

    /** A feature sheet's page key begins so; a question-and-answer page's with "faq_". */
    private const val FEATURE = "intro_"
    private const val TOP = 5
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val OLD_BACKUP_DAYS = 7
    private const val SAY_AT_MOST = 220
}
