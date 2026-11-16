package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AnswerAlert
import com.innovation313.roshankhata.data.AnswerBook
import com.innovation313.roshankhata.data.AnswerCard
import com.innovation313.roshankhata.data.AnswerEnv
import com.innovation313.roshankhata.data.AnswerGate
import com.innovation313.roshankhata.data.AnswerProfile
import com.innovation313.roshankhata.data.Automations
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudLook
import com.innovation313.roshankhata.data.HudhudSense
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerAlerts
import com.innovation313.roshankhata.data.OpenWith
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentRadar
import com.innovation313.roshankhata.data.ProductFind
import com.innovation313.roshankhata.data.ReminderLadder
import com.innovation313.roshankhata.data.ScreenId
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.data.taxLabelFor
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.desktopWords
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Hudhud on the computer (16 Nov 2026, audit T23): the shop's assistant, typed. What a question means is read by the same
 * shared reader as on the phone ([Hudhud.understand]) and answered by the same shared answers ([AnswerBook]) behind the same
 * gate ([AnswerGate] over [Access]: a cashier signed in sees no money figure, as on the phone). Only the panel is the
 * computer's own: a box to type in, the card, its rows and button, and the chips under it.
 *
 * No voice here (decided 16 Nov 2026, the audit's T24: Windows has no free offline voice for Urdu, Sindhi, Farsi or
 * Bengali, and no free offline recogniser for them): the first card says once that the answer is written. A card that asks
 * him to change how he speaks, to change the look or to fly is answered with that same line, never acted on here.
 *
 * Nothing is written to the book from here (scripts/check_hudhud_readonly.py holds this file too): a row opens the
 * customer's page, a button opens a page, the entry form is only filled in — the owner saves on the page that has its checks.
 */

/** The computer's [AnswerEnv]: its words, this computer's settings file and the open business's own files. */
class HudhudDeskEnv(
    override val words: Words,
    override val locale: Locale,
    private val settings: FileShopSettings,
    private val prefs: AppPrefs,
    private val business: Long,
    private val reminders: ReminderStore
) : AnswerEnv {

    override fun inLanguage(locale: Locale): AnswerEnv = HudhudDeskEnv(desktopWords(locale), locale, settings, prefs, business, reminders)

    override fun callMe(): String? = prefs.prop(KEY_CALL_ME)?.trim()?.takeIf { it.isNotEmpty() }

    override fun setCallMe(name: String) {
        // As the phone keeps it (HudhudPrefs.CALL_ME_MAX): what he is told to call the owner, on this computer only.
        val kept = name.trim().take(CALL_ME_MAX)
        if (kept.isNotEmpty()) prefs.setProp(KEY_CALL_ME, kept)
    }

    override fun privacyDue(): Boolean = prefs.prop(KEY_PRIVACY) != "true"
    override fun privacyTold() = prefs.setProp(KEY_PRIVACY, "true")
    override fun radarOn(): Boolean = RadarDesk.on
    /** The computer never holds a helper's read-only copy. */
    override fun viewer(): Boolean = false

    override fun lookValue(setting: HudhudLook.Setting): Int = when (setting) {
        // The computer's four steps are the phone's four (TEXT_FACTORS), the same order as HudhudLook's levels.
        HudhudLook.Setting.SIZE -> prefs.textSize()
        HudhudLook.Setting.THEME -> when (prefs.theme()) {
            ThemeChoice.LIGHT -> HudhudLook.THEME_LIGHT
            ThemeChoice.DARK -> HudhudLook.THEME_DARK
            ThemeChoice.SYSTEM -> HudhudLook.THEME_SYSTEM
        }
    }

    override val profile: AnswerProfile = object : AnswerProfile {
        // Each as the phone's BusinessProfile returns it: a blank field is no field.
        private fun String?.kept() = this?.takeIf { it.isNotBlank() }
        override fun businessName() = settings.businessName().kept()
        override fun businessNameUrdu() = settings.businessNameUrdu().kept()
        override fun businessAddress() = settings.businessAddress().kept()
        override fun businessPhone() = settings.businessPhone().kept()
        override fun businessEmail() = settings.businessEmail().kept()
        override fun strn() = settings.strn().kept()
        // The phone's BusinessProfile.taxLabel, as the computer's invoices word it (invoice/Context.kt).
        override fun taxLabel(): String = when (val country = settings.country()) {
            ShopCountry.PAKISTAN -> "STRN"
            ShopCountry.OTHER -> words.text(W.tax_number_label)
            else -> country.taxLabelFor(words.text(W.tax_number_label))
        }
        override fun termsAndConditions() = settings.termsAndConditions().kept()
        override fun currency() = settings.currency()
        override fun country() = settings.country()
        override fun calendar(): ShopCalendar.Kind = settings.calendar()
        override fun trade() = settings.tradeKey().kept()
        override fun bankName() = settings.bankName().kept()
        override fun bankAccountTitle() = settings.bankAccountTitle().kept()
        override fun bankIban() = settings.bankIban().kept()
        override fun bankJazzCash() = settings.bankJazzCash().kept()
    }

    /** The phone's Trade.label: the trade's own word, an Other shop's own name. */
    override fun tradeLabel(key: String): String {
        val k = Trades.of(key) ?: "AGRI"
        if (k == "OTHER") settings.otherTradeName()?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching { words.text("trade_" + k.lowercase()) }.getOrDefault(k)
    }

    /** TradeKit.seasons: an agri shop in a country that has crop seasons (a book with no trade reads as agri, as on the phone). */
    override fun seasons(): Boolean = (Trades.of(settings.tradeKey()) ?: "AGRI") == "AGRI" && ShopCountry.current.seasons

    /** SeasonsPane.seasonName's words. */
    override fun seasonName(season: SeasonBook.Season): String =
        if (season.crop == SeasonBook.Crop.RABI) words.text(W.season_rabi, season.year, (season.year + 1) % 100)
        else words.text(W.season_kharif, season.year)

    override fun appVersion(): String? = UpdateCheck.installed()
    /** Not claimed: the computer's build has no store to be another of. */
    override fun testBuild(): Boolean = false
    /** The computer keeps no counts of its own opens and sums: none is said, never a made-up figure. */
    override fun usageCount(thing: Hudhud.Thing, from: Long, to: Long): Int = 0
    override fun usageSince(): Long? = null
    override fun lastBackupAt(): Long = prefs.lastSavedBackup(business) ?: 0L
    override fun otherPhoneAt(): Long = 0L
    override fun backupFailedAt(): Long = 0L

    /** The bell's rows by the shared rule (LedgerAlerts), as the computer's bell lists them. */
    override suspend fun alerts(dao: KhataDao): List<AnswerAlert> =
        LedgerAlerts.compute(words, dao, { Format.money(it) }).map { AnswerAlert(kindOf(it.kind), it.title) }

    override fun remindedToday(now: Long): Set<Long> = reminders.openedToday(now)
    override suspend fun radarShopDays(dao: KhataDao): Int? = RadarDesk.shopDays(dao)
    override fun radarReasons(reading: PaymentRadar.Reading, promise: Long?, tone: Int): String = reasonsText(words, reading, promise, tone)
    override fun toneThatWorked(partyId: Long, balance: Double): Int =
        ReminderLadder.learned(reminders.state(partyId), balance, System.currentTimeMillis())
    override fun automationSettings(): Automations.Settings = DesktopAutomationSettings(prefs, business)
    override fun screenNeeds(screen: ScreenId): Access.Permission? = sectionOf(screen)?.need()

    companion object {
        private const val KEY_CALL_ME = "hudhud_call_me"
        private const val KEY_PRIVACY = "hudhud_privacy_told"
        private const val CALL_ME_MAX = 30

        /** The bell's kinds by name. No else: a kind added to the bell does not build until Hudhud names it. */
        fun kindOf(k: LedgerAlerts.Kind): AnswerAlert.Kind = when (k) {
            LedgerAlerts.Kind.CHEQUE -> AnswerAlert.Kind.CHEQUE
            LedgerAlerts.Kind.PLAN -> AnswerAlert.Kind.PLAN
            LedgerAlerts.Kind.BILL -> AnswerAlert.Kind.BILL
            LedgerAlerts.Kind.EXPIRING -> AnswerAlert.Kind.EXPIRING
            LedgerAlerts.Kind.STOCK -> AnswerAlert.Kind.STOCK
            LedgerAlerts.Kind.DRAWER -> AnswerAlert.Kind.DRAWER
            LedgerAlerts.Kind.CASHFLOW -> AnswerAlert.Kind.CASHFLOW
            LedgerAlerts.Kind.SYNC -> AnswerAlert.Kind.SYNC
        }

        /**
         * The computer's page for an answer's page; null where the computer has none (the bell, Mera Khata, Payment voice,
         * Waris, the helper's phone…): the card then has no button. No else: a page added to ScreenId does not build here
         * until the computer says where it is.
         */
        fun sectionOf(id: ScreenId): Section? = when (id) {
            ScreenId.ABOUT -> Section.HELP
            ScreenId.AGING -> Section.AGING
            ScreenId.ALERTS -> null
            ScreenId.AUDIT -> Section.AUDIT
            ScreenId.BACKUP -> Section.DAILY
            ScreenId.BILLS -> Section.BILLS
            ScreenId.BUSINESS_CARD -> Section.CARD
            ScreenId.BUSINESS_SETTINGS -> Section.SHOP
            ScreenId.BUSINESS_SWITCH -> null
            ScreenId.CALCULATOR -> Section.CALC
            ScreenId.CASH_FLOW -> Section.CASHFLOW
            ScreenId.CASHBOOK -> Section.CASHBOOK
            ScreenId.CHEQUES -> Section.CHEQUES
            ScreenId.COUNTER_SALE -> Section.COUNTER
            ScreenId.DAY_BOOK -> Section.CASHBOOK
            ScreenId.DUPLICATE_CUSTOMERS -> Section.DUPLICATES
            ScreenId.EXPIRING -> Section.EXPIRY
            ScreenId.FOLLOW_UP -> Section.FOLLOWUP
            ScreenId.HELP -> Section.HELP
            ScreenId.INSIGHTS -> Section.INSIGHTS
            ScreenId.INVOICES -> Section.INVOICES
            ScreenId.KHATA -> Section.KHATA
            ScreenId.LANGUAGE -> null
            ScreenId.LAPSED -> Section.INSIGHTS
            ScreenId.MERA_KHATA -> null
            ScreenId.ORDERS -> Section.ORDERS
            ScreenId.PAYMENT_VOICE -> null
            ScreenId.PLANS -> Section.PLANS
            ScreenId.POSTER -> Section.POSTER
            ScreenId.PRODUCT_CUSTOMERS -> Section.PRODUCTS
            ScreenId.PRODUCTS -> Section.PRODUCTS
            ScreenId.RATE_LIST -> Section.RATES
            ScreenId.RECYCLE_BIN -> null
            ScreenId.SALES_REPORT -> Section.SALES
            ScreenId.SCHEMES -> Section.SCHEMES
            ScreenId.SEASONS -> Section.SEASONS
            ScreenId.SHIFT -> Section.COUNTER
            ScreenId.SNAPSHOTS -> Section.DAILY
            ScreenId.STAFF -> Section.STAFF
            ScreenId.SUPPLIER_RATES -> Section.BUY_RATES
            ScreenId.VIEWER -> null
            ScreenId.WARIS -> null
            ScreenId.ZAKAT -> Section.ZAKAT
        }

        /**
         * The Radar's reasons in words (the computer's RadarDialog says the same lines through [radarReasons], which is
         * @Composable; this is its rule for an answer made off the window's thread).
         */
        fun reasonsText(words: Words, r: PaymentRadar.Reading, promise: Long?, tone: Int): String {
            fun days(n: Int) = words.plural(WP.radar_days, n, n)
            fun short(t: Long) = DateWords.format("d MMM", t)
            val lines = mutableListOf<String>()
            lines += words.text(when (r.light) {
                PaymentRadar.Light.GREEN -> W.radar_on_time
                PaymentRadar.Light.AMBER -> W.radar_late
                PaymentRadar.Light.RED -> W.radar_very_late
                PaymentRadar.Light.NONE -> W.radar_new
            })
            r.typicalDays?.let { lines += words.text(W.radar_typical, days(it)) }
            r.shopDays?.let { lines += words.text(W.radar_shop_typical, days(it)) }
            r.oldestOpenDays?.takeIf { it > 0 }?.let { lines += words.text(W.radar_oldest, days(it)) }
            r.payDay?.let { lines += words.text(W.radar_pay_day, it.toString()) }
            val from = r.nextFrom
            val to = r.nextTo
            if (from != null && to != null) lines += words.text(W.radar_next, short(from), short(to))
            if (r.promiseOverdue && promise != null) lines += words.text(W.radar_promise_passed, Format.dateOnly(promise))
            if (r.behindHabit) lines += words.text(W.radar_behind)
            val promised = r.promisesKept + r.promisesBroken
            if (promised > 0) lines += words.text(W.radar_promises, r.promisesKept.toString(), promised.toString())
            when (tone) {
                ReminderLadder.GENTLE -> W.reminder_step_gentle
                ReminderLadder.PLAIN -> W.reminder_step_plain
                ReminderLadder.SERIOUS -> W.reminder_step_serious
                else -> null
            }?.let { lines += words.text(W.radar_tone, words.text(it)) }
            return lines.joinToString("\n") { "• $it" } + "\n\n" + words.text(W.radar_private)
        }

        @Volatile
        private var senseSet = false

        /**
         * Hudhud's learnt sense on the computer: the phone's model when the build carries it as a resource
         * (/hudhud/sense.bin); without it he reads by his rules alone, as the shared tests do without a model.
         */
        fun useSense() {
            if (senseSet) return
            senseSet = true
            HudhudSense.useModel {
                runCatching { HudhudDeskEnv::class.java.getResourceAsStream("/hudhud/sense.bin")?.use { it.readBytes() } }.getOrNull()
            }
        }

        /**
         * A card made for the phone's voice, look or acts, on the computer: the written line instead (he neither speaks, nor
         * changes the window's look, nor flies here). Any other card as it is.
         */
        fun forDesk(card: AnswerCard, words: Words): AnswerCard =
            if (card.voice != null || card.look != null || card.lookUndo != null || card.act != null) {
                AnswerCard(AnswerCard.Mood.CALM, lines = listOf(words.text(W.hudhud_intro_pc)), ask = card.ask)
            } else card

        /** The first card: the greeting, and once that the answers are written here. */
        fun opening(env: AnswerEnv): AnswerCard =
            AnswerCard(AnswerCard.Mood.CALM, label = AnswerBook.greeting(env), lines = listOf(env.getString(W.hudhud_intro_pc)))

        /** A list of chips with their words: each once, each in its own words or the card's ([AnswerCard.optionLabels]). */
        fun labelled(env: AnswerEnv, asks: List<Hudhud.Ask>, card: AnswerCard?): List<Pair<Hudhud.Ask, String>> {
            val out = LinkedHashMap<Hudhud.Ask, String>()
            for (a in asks) {
                if (a in out) continue
                (card?.optionLabels?.get(a) ?: AnswerBook.chipLabel(env, a))?.let { out[a] = it }
            }
            return out.toList()
        }
    }
}

/**
 * Hudhud's panel: the card, the chips under it, and the box to type in. [onOpen] opens a page, [onParty] a customer's page
 * (with his entry form filled in when the card offers that: [OpenWith.EntryForm]), [onAutomations] the automations' window.
 */
@Composable
fun HudhudPanel(
    dao: KhataDao,
    parties: List<PartyWithBalance>,
    env: HudhudDeskEnv,
    onOpen: (Section) -> Unit,
    onParty: (Long, OpenWith.EntryForm?) -> Unit,
    onAutomations: () -> Unit,
    onClose: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf<String?>(null) }
    var card by remember(env) { mutableStateOf(HudhudDeskEnv.opening(env)) }
    var chips by remember { mutableStateOf<List<Pair<Hudhud.Ask, String>>>(emptyList()) }
    var before by remember { mutableStateOf<Hudhud.Ask?>(null) }
    var busy by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf<List<Hudhud.Name>>(emptyList()) }
    var staff by remember { mutableStateOf<List<Hudhud.Name>>(emptyList()) }
    val names = remember(parties) { parties.map { Hudhud.Name(it.id, it.name) } }
    val topics = remember(env) { AnswerBook.topics(env) }
    val language = env.locale.toLanguageTag()

    LaunchedEffect(dao) {
        HudhudDeskEnv.useSense()
        products = withContext(Dispatchers.IO) {
            runCatching { dao.productsOnce().flatMap { p -> listOf(Hudhud.Name(p.id, p.name)) + ProductFind.otherNamesOf(p.otherNames).map { Hudhud.Name(p.id, it) } } }
                .getOrDefault(emptyList())
        }
        staff = withContext(Dispatchers.IO) {
            runCatching { if (AnswerGate.staff()) dao.staffCardsOnce().filter { it.isActive }.map { Hudhud.Name(it.id, it.name) } else emptyList() }
                .getOrDefault(emptyList())
        }
        // The day's own questions first (what the bell has), then the ones he always knows.
        val day = withContext(Dispatchers.IO) { runCatching { AnswerBook.suggested(env, dao) }.getOrDefault(emptyList()) }
        chips = HudhudDeskEnv.labelled(env, day + AnswerBook.usualChips(env), null)
    }

    /** One question: its words, and how it was read (typed: read now; a chip or a row: known already). */
    fun ask(words: String, known: Hudhud.Ask?) {
        if (busy || words.isBlank()) return
        busy = true
        asked = words
        val talk = before
        scope.launch {
            val result: Pair<AnswerCard, List<Hudhud.Ask>>? = try {
                val q = known ?: withContext(Dispatchers.Default) {
                    Hudhud.understand(listOf(words), names, topics, language, products, talk, staff).second
                }
                val answer = withContext(Dispatchers.IO) { AnswerBook.answer(env, dao, q, parties) }
                val next = withContext(Dispatchers.Default) {
                    AnswerBook.chipsFor(env, words, q, answer.options, answer.tookAs != null, names, products, topics, language)
                }
                // What the talk is about now, so "uska number?" carries on (the phone's own rule).
                val taken = (q as? Hudhud.Ask.Guessed)?.ask ?: q
                if (AnswerBook.carriesOn(taken, answer.reverent)) before = taken
                HudhudDeskEnv.forDesk(answer, env.words) to next
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result == null) {
                card = AnswerBook.failed(env)
                chips = HudhudDeskEnv.labelled(env, AnswerBook.usualChips(env), null)
            } else {
                card = result.first
                chips = HudhudDeskEnv.labelled(env, result.second + AnswerBook.usualChips(env), result.first)
            }
            busy = false
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty()) return
        input = ""
        ask(t, null)
    }

    val title = word(W.hudhud_name)
    DialogFrame(
        title,
        onClose = onClose,
        footerStart = {
            FluentField(
                input, { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = word(W.hudhud_ask),
                singleLine = true,
                keyboardActions = KeyboardActions(onDone = { send() })
            )
        },
        footer = { FluentButton(word(W.hudhud_send), { send() }, kind = ButtonKind.ACCENT, enabled = !busy && input.isNotBlank()) }
    ) {
        asked?.let { Text(word(W.hudhud_you_asked, Format.isolate(it)), color = c.inkSoft, fontSize = 13.sp) }
        AnswerCardView(card, onOpen, onParty, onAutomations) { a, words -> ask(words, a) }
        if (chips.isNotEmpty()) {
            PillRow(Modifier.fillMaxWidth()) {
                chips.forEach { (a, label) -> FluentPill(label, false) { ask(label, a) } }
            }
        }
    }
}

/** The card: what it was taken as, the heading and figure, its lines, verses and rows, and its one button. */
@Composable
private fun AnswerCardView(
    card: AnswerCard,
    onOpen: (Section) -> Unit,
    onParty: (Long, OpenWith.EntryForm?) -> Unit,
    onAutomations: () -> Unit,
    onAsk: (Hudhud.Ask, String) -> Unit
) {
    val c = LocalRoshanColors.current
    val figureColour = when (card.mood) {
        AnswerCard.Mood.GOOD -> c.brandGreenText
        AnswerCard.Mood.CARE -> Look.warn
        AnswerCard.Mood.CALM -> c.ink
    }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        card.tookAs?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
        card.label?.let { Text(it, color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        card.figure?.let { Text(it, color = figureColour, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        card.lines.forEach { Text(it, color = c.ink, fontSize = 14.sp) }
        if (card.verses.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(Look.ctl).background(Look.subtle).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                card.verses.forEach { v ->
                    // The Quran's Arabic always reads right to left, whatever the window's language.
                    Text(
                        v.arabic, Modifier.fillMaxWidth(), color = c.ink, fontSize = 18.sp,
                        style = TextStyle(textDirection = TextDirection.Rtl), textAlign = TextAlign.Start
                    )
                    Text(v.surah + " " + v.ref, color = c.textMuted, fontSize = 12.sp)
                }
            }
            card.verseNote?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
        }
        card.rows.forEach { r ->
            // Read into locals: properties of a class from another module are not smart-cast.
            val rowAsk = r.ask
            val screen = r.open?.let { HudhudDeskEnv.sectionOf(it) }
            val entry = r.amount?.let { OpenWith.EntryForm(it, r.isGiven, r.time) }
            val go: (() -> Unit)? = when {
                rowAsk != null -> { -> onAsk(rowAsk, r.name) }
                r.partyId > 0 -> { -> onParty(r.partyId, entry) }
                screen != null -> { -> onOpen(screen) }
                else -> null
            }
            Row(
                Modifier.fillMaxWidth().clip(Look.ctl).then(if (go != null) Modifier.press(Look.ctl, onClick = go) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    r.sub?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 2) }
                }
                if (r.figure.isNotEmpty()) Text(r.figure, color = c.ink, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
        }
        // The card's one button: the customer's page (with the entry form filled in when offered), the page it comes from,
        // or the automations' window. None for a page the computer does not have, nor for his voice settings (no voice here).
        val open = card.open?.let { HudhudDeskEnv.sectionOf(it) }
        val party = card.openParty
        val form = card.openWith as? OpenWith.EntryForm
        val action: (() -> Unit)? = when {
            party != null -> { -> onParty(party, form) }
            card.automations -> onAutomations
            open != null -> { -> onOpen(open) }
            else -> null
        }
        if (action != null && !card.voiceSettings) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                FluentButton(word(card.openLabel ?: W.vq_open), action, kind = ButtonKind.STANDARD)
            }
        }
    }
}
