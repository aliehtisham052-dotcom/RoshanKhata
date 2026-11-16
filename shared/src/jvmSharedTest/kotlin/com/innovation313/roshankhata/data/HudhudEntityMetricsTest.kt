package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hudhud scored by what he understood INSIDE a reading (16 Nov 2026, audit T10): the other set tests ask only "did he pick
 * the right kind of ask"; a reading can have the right meaning and still the wrong side of the money, the wrong amount,
 * person, product or days — the errors that cost a shopkeeper. Each entity is compared alone, per set and per language:
 *
 * - **correct**: the reading carries the entity the row names;
 * - **wrong**: it carries another (the other side of the money, another amount, person, product or span);
 * - **choice** (people, goods): the right one among others, offered to pick;
 * - **missing**: it carries none, and the reading is not one the row accepts;
 * - **otherwise**: it carries none, but the reading is one the row accepts (a help page, the list of people, a
 *   figure whose card uses its own default days) — not an error.
 *
 * **Where an entity comes from, and nothing else is labelled** (no row of the owner's or the reviewers' is hand-labelled):
 * - *side*: a meaning that names it — held-out entry_gave / entry_got / to_get / to_give / collected / who_owes_most /
 *   not_paid; in the owner's lists and the OK rows of the review sets, a named reading Figure:TO_GET, TO_GIVE,
 *   WHO_OWES_MOST/LEAST, NOT_PAID, Collected, PaidOut ([HudhudEntities.sideOfShown]); a row naming both sides of one
 *   family is not labelled. "Entry" and "Moved" carry a side but their spelling in a set does not, so the review sets'
 *   entries are NOT measured for side.
 * - *amount*: the one figure written in digits in a row whose meaning is an entry alone (held-out entry_*; review
 *   rows held as "Entry"). The owner's lists accept "Entry" beside other readings and some of their sentences carry a
 *   quantity ("5 kilo"), so they are not measured; spelled amounts ("paanch sau") are not read.
 * - *person / product*: the held-out set's own {NAME} / {ITEM} slots; elsewhere the book's name written whole in the
 *   sentence ([HudhudEntities.written]; the review sets' Urdu-, Arabic-, Hindi- and Bengali-script spellings of Ali Raza
 *   and Urea are those the held-out test fills in). A sentence naming two people is not labelled.
 * - *days*: a named "Count:X:WINDOW" reading in the owner's lists and the OK rows of the review sets; no other reading
 *   spells its days. The held-out meanings named "…_today" (entries_today, bills_today, …) are NOT a label of the days:
 *   their sentences ask this week or last month too ("is hafte kitni entries hui hain" is entries_today), so the
 *   held-out set is not measured for days.
 *
 * The floors hold only what is measured today: no reading puts the money on the wrong side where none does now, and the
 * wrong amounts do not rise. People, goods and days are printed, not held (the meaning tests already hold most rows).
 * Names and goods are the invented ones of the other set tests (lesson 80).
 */
class HudhudEntityMetricsTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    // ---------------------------------------------------------------------------------------------------------------
    // The tally.

    enum class Ent(val title: String) { SIDE("side"), AMOUNT("amount"), PERSON("person"), PRODUCT("product"), DAYS("days") }
    enum class Out { CORRECT, WRONG, CHOICE, MISSING, OTHERWISE }

    /** What a row names: any of [people] is the person (several spellings of one), any of [goods] the product. */
    private class Expect(
        val sides: Set<HudhudEntities.Side> = emptySet(),
        val amount: Double? = null,
        val people: Set<Long> = emptySet(),
        val goods: Set<Long> = emptySet(),
        val window: VoiceQuestion.Window? = null,
    )

    private class Tally {
        val counts = LinkedHashMap<Pair<String, Ent>, IntArray>()
        val wrongs = ArrayList<String>()
        fun add(lang: String, e: Ent, o: Out) {
            counts.getOrPut(lang to e) { IntArray(Out.values().size) }[o.ordinal]++
        }
        fun total(e: Ent, o: Out): Int = counts.filterKeys { it.second == e }.values.sumOf { it[o.ordinal] }
    }

    private fun score(t: Tally, lang: String, said: String, got: Hudhud.Ask, accepted: Boolean, x: Expect) {
        val c = HudhudEntities.of(got)
        val shown = HudhudShown.of(got)
        fun none() = if (accepted) Out.OTHERWISE else Out.MISSING
        fun wrong(e: Ent, what: String) { t.wrongs += "  WRONG ${e.title} [$lang] $said  =>  $shown  ($what)" }

        if (x.sides.isNotEmpty()) {
            val o = when {
                x.sides.any { it.opposite in c.sides } -> Out.WRONG
                x.sides.any { it in c.sides } -> Out.CORRECT
                else -> none()
            }
            if (o == Out.WRONG) wrong(Ent.SIDE, "named ${x.sides}, read ${c.sides}")
            t.add(lang, Ent.SIDE, o)
        }
        x.amount?.let { want ->
            val o = when {
                c.amounts.isEmpty() -> none()
                c.amounts.any { Math.abs(it - want) < 0.005 } -> Out.CORRECT
                else -> Out.WRONG
            }
            if (o == Out.WRONG) wrong(Ent.AMOUNT, "written $want, read ${c.amounts}")
            t.add(lang, Ent.AMOUNT, o)
        }
        fun ids(e: Ent, want: Set<Long>, have: Set<Long>) {
            if (want.isEmpty()) return
            val o = when {
                have.isEmpty() -> none()
                want.containsAll(have) -> Out.CORRECT
                have.any { it in want } -> Out.CHOICE
                else -> Out.WRONG
            }
            if (o == Out.WRONG) wrong(e, "named $want, read $have")
            t.add(lang, e, o)
        }
        ids(Ent.PERSON, x.people, c.people)
        ids(Ent.PRODUCT, x.goods, c.goods)
        x.window?.let { want ->
            val o = when {
                c.windows.isEmpty() -> none()
                want in c.windows -> Out.CORRECT
                else -> Out.WRONG
            }
            if (o == Out.WRONG) wrong(Ent.DAYS, "named $want, read ${c.windows}")
            t.add(lang, Ent.DAYS, o)
        }
    }

    private fun print(title: String, t: Tally) {
        println("== Entities in $title  (correct / wrong / choice / missing / otherwise; - = not labelled)")
        val langs = t.counts.keys.map { it.first }.distinct()
        println(String.format("%-8s", "lang") + Ent.values().joinToString("") { String.format(" | %-22s", it.title) })
        fun cell(a: IntArray?) = if (a == null) "-" else a.joinToString("/")
        for (l in langs + "ALL") {
            val row = Ent.values().joinToString("") { e ->
                val a = if (l == "ALL") t.counts.filterKeys { it.second == e }.values.takeIf { it.isNotEmpty() }
                    ?.fold(IntArray(Out.values().size)) { s, v -> IntArray(s.size) { s[it] + v[it] } }
                else t.counts[l to e]
                String.format(" | %-22s", cell(a))
            }
            println(String.format("%-8s", l) + row)
        }
        t.wrongs.forEach(::println)
    }

    private fun hold(set: String, t: Tally) {
        val side = t.total(Ent.SIDE, Out.WRONG)
        val amount = t.total(Ent.AMOUNT, Out.WRONG)
        val (maxSide, maxAmount) = FLOORS.getValue(set)
        assertTrue("$set: $side readings put the money on the wrong side (at most $maxSide)", side <= maxSide)
        assertTrue("$set: $amount readings took the wrong amount (at most $maxAmount)", amount <= maxAmount)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The books the set tests use.

    /** The owner's lists and the review sets: one Latin spelling each (HudhudOwnerSetTest, HudhudReviewSetTest). */
    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    /** The spellings a sentence may write them in: the Latin ones, and the held-out test's own fillings of the first. */
    private val peopleWritten: Map<String, Long> = names.associate { it.name to it.id } +
        listOf("علی رضا", "علي رضا", "अली रज़ा", "আলী রাজা").associateWith { 1L }
    private val goodsWritten: Map<String, Long> = goods.associate { it.name to it.id } +
        listOf("یوریا", "يوريا", "यूरिया", "ইউরিয়া").associateWith { 100L }

    private fun expectWritten(said: String, sides: Set<HudhudEntities.Side>, amount: Double?, window: VoiceQuestion.Window?) =
        Expect(
            sides = sides, amount = amount, window = window,
            people = HudhudEntities.written(said, peopleWritten).let { if (it.size == 1) it else emptySet() },
            goods = HudhudEntities.written(said, goodsWritten).let { if (it.size == 1) it else emptySet() },
        )

    /** The sides a list of named readings stands for; none when it names both sides of one family. */
    private fun sidesOf(readings: List<String>): Set<HudhudEntities.Side> {
        val s = readings.mapNotNull { HudhudEntities.sideOfShown(it) }.toSet()
        return if (s.any { it.opposite in s }) emptySet() else s
    }

    private fun windowOf(readings: List<String>): VoiceQuestion.Window? =
        readings.mapNotNull { HudhudEntities.windowOfShown(it) }.toSet().singleOrNull()

    private fun lines(resource: String): List<String> =
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }

    // ---------------------------------------------------------------------------------------------------------------
    // The sets, read exactly as their own tests read them.

    private fun owner(resource: String, title: String, set: String) {
        val rows = lines(resource).map { it.split('\t') }
        if (rows.isEmpty()) return
        val topics = HudhudRealPages.topics(listOf("values-b+ur+Latn", "values", "values-ur"))
        val t = Tally()
        for (r in rows) {
            val said = r[1]
            val ok = r.last().split('|')
            val got = Hudhud.understand(said, names, topics, null, goods)
            val shown = HudhudShown.of(got)
            // The owner's lists have no language column: they are his own Roman Urdu and English.
            score(t, "owner", said, got, ok.any { shown.startsWith(it) }, expectWritten(said, sidesOf(ok), null, windowOf(ok)))
        }
        print(title, t)
        hold(set, t)
    }

    private fun review(resource: String, title: String, set: String) {
        val rows = lines(resource).map { it.split('\t') }
        if (rows.isEmpty()) return
        val pages = HashMap<String, List<Hudhud.Topic>>()
        val t = Tally()
        for (r in rows) {
            val (lang, status, said) = r
            val prev = r.getOrNull(3)?.takeIf { it.isNotBlank() }
            val held = r[4]
            val topics = pages.getOrPut(lang) { HudhudRealPages.forApp(lang) }
            val before = prev?.let { Hudhud.understand(it, names, topics, lang, goods, null) }
            val got = Hudhud.understand(said, names, topics, lang, goods, before)
            // Only an OK row's reading was judged right; a MISS row's reading is the wrong one it had, so it names nothing.
            val ok = status == "OK"
            val x = expectWritten(
                said,
                sides = if (ok) sidesOf(listOf(held)) else emptySet(),
                amount = if (ok && held == "Entry") HudhudEntities.digitAmount(said) else null,
                window = if (ok) windowOf(listOf(held)) else null,
            )
            score(t, lang, said, got, ok && HudhudShown.of(got) == held, x)
        }
        print(title, t)
        hold(set, t)
    }

    /** The held-out test's book and fillings (HudhudSenseTest): the same person and product in each script. */
    private val heldNames = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(3, "علي رضا"), Hudhud.Name(4, "अली रज़ा"),
        Hudhud.Name(5, "আলী রাজা"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Tariq Butt"), Hudhud.Name(8, "Naeem Khan"),
        Hudhud.Name(9, "Saleem Bhai")
    )
    private val heldGoods = listOf(
        Hudhud.Name(101, "Urea"), Hudhud.Name(102, "یوریا"), Hudhud.Name(103, "يوريا"), Hudhud.Name(104, "यूरिया"),
        Hudhud.Name(105, "ইউরিয়া"), Hudhud.Name(106, "DAP"), Hudhud.Name(107, "Sugar")
    )
    private val heldTopics = listOf(
        Hudhud.Topic("faq_backup", "Backup بیک اپ بیک‌اپ बैकअप ব্যাকআপ نسخة احتياطية پشتیبان cadangan", "Take a backup of the book"),
        Hudhud.Topic("faq_invoice", "Invoice انوائس اِنوائس इनवॉइस বিল ইনভয়েস فاتورة فاکتور faktur bill", "Make an invoice"),
        Hudhud.Topic("faq_customer", "Customer گاہک گراهڪ ग्राहक গ্রাহক عميل مشتری pelanggan", "Add a customer")
    )

    private fun heldFilled(lang: String, text: String): String {
        val (name, item) = when (lang) {
            "ur", "sd", "fa" -> "علی رضا" to "یوریا"
            "ar" -> "علي رضا" to "يوريا"
            "hi" -> "अली रज़ा" to "यूरिया"
            "bn" -> "আলী রাজা" to "ইউরিয়া"
            else -> "Ali Raza" to "Urea"
        }
        return text.replace("{NAME}", name).replace("{ITEM}", item)
    }

    private val heldSide = mapOf(
        "entry_gave" to HudhudEntities.Side.OUT, "entry_got" to HudhudEntities.Side.IN,
        "collected" to HudhudEntities.Side.IN,
        "to_get" to HudhudEntities.Side.RECEIVE, "who_owes_most" to HudhudEntities.Side.RECEIVE,
        "not_paid" to HudhudEntities.Side.RECEIVE, "to_give" to HudhudEntities.Side.PAY,
    )

    private fun heldLabel(a: Hudhud.Ask): String? = when (a) {
        is Hudhud.Ask.Guessed -> heldLabel(a.ask)
        is Hudhud.Ask.NeedName -> "needs_name"
        else -> Hudhud.labelOf(a)
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Test fun `entities in the held-out sentences`() {
        val rows = javaClass.getResourceAsStream("/hudhud/heldout.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() }
        if (rows.isEmpty()) return
        val t = Tally()
        for (line in rows) {
            val (lang, label, text) = line.split('\t', limit = 3)
            val said = heldFilled(lang, text)
            val got = Hudhud.understand(said, heldNames, heldTopics, lang, heldGoods)
            val x = Expect(
                sides = setOfNotNull(heldSide[label]),
                amount = if (label.startsWith("entry_")) HudhudEntities.digitAmount(said) else null,
                people = if ("{NAME}" in text) (1L..5L).toSet() else emptySet(),
                goods = if ("{ITEM}" in text) (101L..105L).toSet() else emptySet(),
            )
            // Accepted is the label alone; HudhudSenseTest's few near readings are not granted here.
            score(t, lang, said, got, heldLabel(got) == label, x)
        }
        print("the held-out set", t)
        hold("heldout", t)
    }

    @Test fun `entities in the owner's short lists`() = owner("/hudhud/owner_set.tsv", "the owner's short lists", "owner_set")

    @Test fun `entities in the owner's 1000 sentences`() = owner("/hudhud/owner_1000.tsv", "the owner's 1,000 sentences", "owner_1000")

    @Test fun `entities in review set D`() = review("/hudhud/review_d.tsv", "review set D", "review_d")

    @Test fun `entities in review set O`() = review("/hudhud/review_o.tsv", "review set O", "review_o")

    private companion object {
        /**
         * Per set: the most readings that may put the money on the wrong side, and the most that may take the wrong amount —
         * what this build reads (16 Nov 2026). Lower them with every fix, never raise them.
         */
        val FLOORS = mapOf(
            // 16 Nov 2026, the payable class fixed (BC-097): supplier and "kis ko … nahi diya" questions, "mere paas", "dene
            // wala kaun", the Farsi and Bengali debtor. What is left reads both ways and is not turned: "Kis ki payment late ho
            // gayi" (customers' late payments too), "Kitna qarz baqi hai", "Kis ka maal ka paisa baqi hai".
            "heldout" to (0 to 0),
            "owner_set" to (1 to 0),
            "owner_1000" to (3 to 0),
            "review_d" to (0 to 0),
            "review_o" to (0 to 0),
        )
    }
}
