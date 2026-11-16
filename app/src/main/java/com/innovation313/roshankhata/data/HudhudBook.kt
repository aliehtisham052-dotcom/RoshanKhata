package com.innovation313.roshankhata.data

import android.content.Context
import android.os.Bundle

/**
 * Hudhud's answers on the phone (12 Nov 2026; made in :shared since 16 Nov 2026, audit T22). What each [Hudhud.Ask] comes
 * to is worked out by [AnswerBook] and its readers (AnswerFacts, AnswerCounterFacts, AnswerRecordFacts) — the one rule the
 * computer's Hudhud reads too — over a [HudhudPhoneEnv] for the given [Context]. This object turns the shared card into the
 * card the sheet has always drawn: a page becomes its screen's class, what it opens on its extras, a word key its resource
 * id, and the card's own words a function of a Context in the voice's language. Nothing is decided here: every figure,
 * line, gate and chip is the shared layer's; HudhudGoldenAnswersTest holds that the phone's cards did not change.
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
        val open: Class<*>? = null,
        /** A row that answers a question when tapped (14 Nov 2026: "which product?" lists products, each its own ask). */
        val ask: Hudhud.Ask? = null,
        /** With [amount]: the moment the form opens on, a day the sentence said (16 Nov 2026); null: now. */
        val time: Long? = null
    )

    /** A verse the card shows: its surah's Arabic name, where it is, and its Arabic text — never said aloud. */
    data class Verse(val surah: String, val ref: String, val arabic: String)

    /** The sheet's card: every field as [AnswerCard] has it, in the phone's own types (see [AnswerCard] for each). */
    data class Answer(
        val mood: Mood,
        val label: String? = null,
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
        val money: Boolean = false,
        /** What to say instead of the card itself, with a context in the voice's language; null: the whole card is said. */
        val spoken: ((Context) -> String)? = null,
        /** What the card's button does instead of opening a screen (the sheet's own: download the language). */
        val doIt: (() -> Unit)? = null,
        val options: List<Hudhud.Ask> = emptyList(),
        val optionLabels: Map<Hudhud.Ask, String> = emptyMap(),
        val voiceSettings: Boolean = false,
        val automations: Boolean = false,
        val ask: Hudhud.Ask? = null,
        val tookAs: String? = null,
        val quiet: Boolean = false,
        val act: Hudhud.Move? = null,
        val secrets: List<String> = emptyList(),
        val voice: Hudhud.VoiceChange? = null,
        val verses: List<Verse> = emptyList(),
        val verseNote: String? = null,
        val reverent: Boolean = false,
        val look: HudhudLook.Made? = null,
        val lookUndo: HudhudLook.Made? = null
    )

    /** What [speech] said of a card: its words, and whether amounts were left out for the hidden balance. */
    class Speech(val text: String, val leftOutMoney: Boolean)

    private fun env(context: Context): AnswerEnv = HudhudPhoneEnv(context)

    /** The shared card as the sheet's card. */
    fun card(c: AnswerCard): Answer = Answer(
        mood = moodOf(c.mood),
        label = c.label,
        figure = c.figure,
        lines = c.lines,
        rows = c.rows.map { r ->
            Row(r.partyId, r.name, r.figure, r.sub, r.amount, r.isGiven, r.open?.let { HudhudPhoneEnv.activity(it) }, r.ask, r.time)
        },
        open = c.open?.let { HudhudPhoneEnv.activity(it) },
        openParty = c.openParty,
        openExtras = c.openWith?.let { HudhudPhoneEnv.extras(it) },
        openLabel = c.openLabel?.let { WordIds.string(it) },
        money = c.money,
        spoken = c.spoken?.let { own -> { w: Context -> own(HudhudPhoneEnv(w)) } },
        options = c.options,
        optionLabels = c.optionLabels,
        voiceSettings = c.voiceSettings,
        automations = c.automations,
        ask = c.ask,
        tookAs = c.tookAs,
        quiet = c.quiet,
        act = c.act,
        secrets = c.secrets,
        voice = c.voice,
        verses = c.verses.map { Verse(it.surah, it.ref, it.arabic) },
        verseNote = c.verseNote,
        reverent = c.reverent,
        look = c.look,
        lookUndo = c.lookUndo
    )

    private fun moodOf(m: AnswerCard.Mood): Mood = when (m) {
        AnswerCard.Mood.CALM -> Mood.CALM
        AnswerCard.Mood.GOOD -> Mood.GOOD
        AnswerCard.Mood.CARE -> Mood.CARE
    }

    /** The help pages Hudhud can point to ([AnswerBook.PAGES], shared). */
    val PAGES: List<AnswerBook.Page> get() = AnswerBook.PAGES

    /** The help pages as Hudhud matches a question against them ([AnswerBook.topics]). */
    fun topics(context: Context): List<Hudhud.Topic> = AnswerBook.topics(env(context))

    /** What Hudhud says when his sheet opens. */
    fun hello(context: Context): Answer = card(AnswerBook.hello(env(context)))

    /** His own introduction when his face is tapped. */
    fun intro(context: Context, greet: Boolean): Answer = card(AnswerBook.intro(env(context), greet))

    /** What he says when the book could not be read. */
    fun failed(context: Context): Answer = card(AnswerBook.failed(env(context)))

    suspend fun answer(context: Context, dao: KhataDao, ask: Hudhud.Ask, parties: List<PartyWithBalance>): Answer =
        card(AnswerBook.answer(env(context), dao, ask, parties))

    /** The morning bubble's line on Home (counts only, never an amount). */
    suspend fun greetingLine(context: Context, dao: KhataDao, bell: Int): String = AnswerBook.greetingLine(env(context), dao, bell)

    /** The greeting by the shop's country and the hour, with what the owner asked to be called. */
    fun greeting(context: Context, now: Long = System.currentTimeMillis()): String = AnswerBook.greeting(env(context), now)

    /** What opens the customer's entry form filled in: voice entry's own extras. */
    fun entryForm(amount: Double, isGiven: Boolean, time: Long? = null): Bundle =
        HudhudPhoneEnv.extras(AnswerBook.entryForm(amount, isGiven, time))

    /** A chip's words for [ask]; null: no chip. */
    fun chipLabel(c: Context, ask: Hudhud.Ask): String? = AnswerBook.chipLabel(env(c), ask)

    /** The Personal tab's questions. */
    fun personalChips(): List<Hudhud.Ask> = AnswerBook.personalChips()

    /** What to ask next after [ask]. */
    fun next(c: Context, ask: Hudhud.Ask): List<Hudhud.Ask> = AnswerBook.next(env(c), ask)

    /** The day's own questions, first among his chips when the sheet opens. */
    suspend fun suggested(c: Context, dao: KhataDao): List<Hudhud.Ask> = AnswerBook.suggested(env(c), dao)

    /**
     * What he says for [a], with [w] a context in the voice's language: the shared rule ([AnswerBook.speechOf]) over the
     * card's parts, so a card the sheet made itself is said by it too.
     */
    fun speech(w: Context, a: Answer, hideMoney: Boolean): Speech? = AnswerBook.speechOf(
        env(w), a.quiet, a.spoken?.let { own -> { own(w) } }, a.label, a.figure, a.money, a.lines,
        a.rows.map { AnswerBook.SpokenRow(it.name, it.figure, it.sub) }, a.secrets, hideMoney
    )?.let { Speech(it.text, it.leftOutMoney) }

    /** A figure as every screen writes it: the minus outside the left-to-right seal, as Insights writes it. */
    fun money(v: Double): String = AnswerBook.money(v)
}
