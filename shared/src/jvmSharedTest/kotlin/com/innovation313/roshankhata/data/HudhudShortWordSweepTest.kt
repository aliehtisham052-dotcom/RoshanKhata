package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every word of the bank said alone, against a book with a name for each letter pair (17 Nov 2026, audit L018; lessons 139-140: a
 * short ordinary word was taken for a customer or a product — "par" for Paracetamol, "بات" for Butt). A word of the language said
 * alone is no name, unless it is a whole word of that name; the ones that still are today are frozen below, so a new one fails here
 * and is looked at (a guard to add, or the list to grow on purpose), and one that no longer meets anybody is printed to be taken
 * off the list.
 *
 * The book: for each pair of Latin letters "xy", a customer "Xy<tail> Khan" and a product "Xytol" — a word that begins as the said
 * word begins meets them the way a real name does (a prefix scores 80, one letter apart 50; NameSearch). By default the Latin words
 * are swept, each against the names of its own letter pair (the search scores one name against one word, so the rest of the book
 * cannot change whether this name is met; seconds, not minutes). `-Dhudhud.sweep=full` also sweeps the words of the other scripts
 * against the whole book (about five minutes): the bridge across scripts can meet any name, so no pair can be chosen for them.
 *
 * The bank's words change with a retrain of the bank: re-run, and freeze what is new only after reading why it is met.
 */
class HudhudShortWordSweepTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val tails = listOf("rim", "shid", "lam", "veen", "deem", "jaz", "meen", "had")

    private fun pairIndex(x: Char, y: Char) = (x - 'a') * 26 + (y - 'a')

    private fun party(x: Char, y: Char): Hudhud.Name {
        val i = pairIndex(x, y) + 1L
        return Hudhud.Name(i, "" + x.uppercaseChar() + y + tails[(i % tails.size).toInt()] + " Khan")
    }

    private fun product(x: Char, y: Char) = Hudhud.Name(100_000L + pairIndex(x, y) + 1L, "" + x.uppercaseChar() + y + "tol")

    private val letters = ('a'..'z').toList()

    /** Every word of the bank, lower case, without its punctuation and the bank's stand-ins (qqname, qqitem, qqnum). */
    private fun bankWords(): Set<String> {
        val out = sortedSetOf<String>()
        val bank = File(generateSequence(HudhudModelTest.asset()) { it.parentFile }.elementAt(6), "shared/src/jvmSharedTest/resources/hudhud/bank.tsv")
        bank.forEachLine { l ->
            val p = l.split('\t')
            if (p.size >= 2) p[1].split(Regex("\\s+"))
                .map { it.trim('?', '.', ',', '!', '؟', '،', ';', ':', '"', '\'', '(', ')').lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("qq") }
                .forEach { out += it }
        }
        return out
    }

    /** "P" (a customer) or "G" (a product) when the word alone was read as somebody or something of the book, else null. */
    private fun metKind(a: Ask): String? = when (a) {
        is Ask.Parties -> if (a.ids.isNotEmpty()) "P" else null
        is Ask.Person -> if (a.ids.isNotEmpty()) "P" else null
        is Ask.WhenPays -> if (a.ids.isNotEmpty()) "P" else null
        is Ask.Goods -> if (a.ids.isNotEmpty()) "G" else null
        is Ask.Took -> "P"
        is Ask.Guessed -> metKind(a.ask)
        is Ask.Now -> metKind(a.ask)
        is Ask.DidYouMean -> a.options.firstNotNullOfOrNull { metKind(it) }
        else -> null
    }

    /** Whether the word is a whole word of a name it met (then it is that name said, not an ordinary word taken for it). */
    private fun wholeWordOf(w: String, book: List<Hudhud.Name>) =
        book.any { n -> n.name.lowercase().split(' ').any { it == w } }

    private fun sweep(words: Collection<String>, bookFor: (String) -> Pair<List<Hudhud.Name>, List<Hudhud.Name>>): Map<String, String> {
        val met = sortedMapOf<String, String>()
        for (w in words) {
            val (people, things) = bookFor(w)
            val kind = metKind(Hudhud.understand(w, people, emptyList(), null, things)) ?: continue
            if (!wholeWordOf(w, people + things)) met[w] = kind
        }
        return met
    }

    @Test fun `a Latin word of the bank said alone meets no name of its letter pair beyond the frozen list`() {
        val latin = bankWords().filter { w -> w.length >= 2 && w.all { it in 'a'..'z' } }
        val met = sweep(latin) { w -> listOf(party(w[0], w[1])) to listOf(product(w[0], w[1])) }
        compare(LATIN_FROZEN, met)
    }

    @Test fun `every other word of the bank said alone meets no name of the whole book beyond the frozen list`() {
        if (System.getProperty("hudhud.sweep") != "full") return
        val people = letters.flatMap { x -> letters.map { y -> party(x, y) } }
        val things = letters.flatMap { x -> letters.map { y -> product(x, y) } }
        val other = bankWords().filter { w -> w.any { it.isLetter() } && !w.all { it in 'a'..'z' } }
        compare(OTHER_FROZEN, sweep(other) { people to things })
    }

    private fun compare(frozen: Map<String, String>, met: Map<String, String>) {
        val gone = frozen.keys - met.keys
        if (gone.isNotEmpty()) println("HudhudShortWordSweepTest: no longer met, take off the frozen list: " + gone.sorted())
        val fresh = met.filterKeys { it !in frozen }
        assertEquals("words of the bank said alone that now meet a name (read why before freezing): $fresh", emptyMap<String, String>(), fresh)
    }

    companion object {
        /**
         * Latin words met today (17 Nov 2026), each read: short English and Roman Urdu words that begin a made-up name or product
         * of their pair ("net" Netol, "yet" Yetol, "hoga" a Ho... name by sound). Met only by a one-word sentence; a sentence with
         * an ask in it is read by its ask. Filled from the first run of this test; P a customer, G a product.
         */
        val LATIN_FROZEN: Map<String, String> = mapOf(
            "bit" to "G",
            "bot" to "G",
            "dah" to "P",
            "dat" to "G",
            "haath" to "G",
            "hath" to "G",
            "hoga" to "P",
            "lah" to "P",
            "lot" to "G",
            "net" to "G",
            "oes" to "P",
            "pcs" to "P",
            "put" to "G",
            "rat" to "G",
            "saath" to "G",
            "saj" to "P",
            "yet" to "G"
        )

        /**
         * The other scripts' words met today against the whole book (`-Dhudhud.sweep=full`, 17 Nov 2026: 109 words, about 3.5
         * minutes). Most are met by the bridge across scripts ([HudhudNameBridge]) although the sense knows them as words ("صحبت",
         * "خانه", "साथ", "নষ্ট"): a finding for the bridge's ordinary-word guard, frozen here so it can only shrink.
         */
        val OTHER_FROZEN: Map<String, String> = mapOf(
            "آؤٹ" to "G", "أحد" to "P", "احد" to "P", "ادى" to "P", "افتح" to "G", "ايڊٽ" to "G", "بدم" to "P", "بردیم" to "P",
            "بيته" to "G", "بچت" to "G", "بھلا" to "P", "تمت" to "G", "توانایی‌هات" to "G", "ثبته" to "G", "جاؤں" to "P",
            "جست" to "G", "جون" to "P", "خانه" to "P", "خذت" to "G", "خوشی" to "P", "دری" to "P", "دمت" to "G", "دوں" to "P",
            "رفته" to "G", "ريت" to "G", "ریت" to "G", "زدم" to "P", "زدیم" to "P", "ساتھ" to "G", "سبت" to "G", "سرت" to "G",
            "سیٹ" to "G", "شدم" to "P", "شدی" to "P", "شمسی" to "P", "صبحت" to "G", "صحبت" to "G", "عادت" to "G",
            "عادته" to "G", "لاحد" to "P", "لاٹ" to "G", "لوں" to "P", "ماشي" to "P", "مدي" to "P", "مماطل" to "G",
            "موجز" to "P", "میته" to "G", "می‌دهند" to "P", "نئون" to "P", "نائٹ" to "G", "نرم‌افزار" to "P", "نقطة" to "G",
            "نیٹ" to "G", "هات" to "G", "همین" to "P", "هيه" to "P", "هيون" to "P", "ويون" to "P", "وڳا" to "P", "يكون" to "P",
            "ٹوٹل" to "G", "ٻيون" to "P", "پيون" to "P", "ٿيون" to "P", "ڀيٽ" to "G", "چڑھی" to "P", "چیت" to "G", "ڊيٽ" to "G",
            "ڦاٿل" to "G", "کشیده" to "P", "گفت" to "G", "گلم" to "P", "گوشی" to "P", "ہاتھ" to "G", "आउट" to "G",
            "घड़ी" to "P", "जोड़" to "P", "डिटेल" to "G", "नाइट" to "G", "नेट" to "G", "पक्षी" to "P", "बचत" to "G",
            "भला" to "P", "मक़सद" to "P", "याद" to "P", "वहां" to "P", "साथ" to "G", "सेट" to "G", "होगा" to "P",
            "অ্যাড" to "P", "আউট" to "G", "আসি" to "P", "কতো" to "G", "করি" to "P", "কাজের" to "P", "কাদের" to "P",
            "ক্যা" to "P", "চিত্র" to "G", "দেব" to "P", "নষ্ট" to "G", "নিট" to "G", "নিবেন" to "P", "বলা" to "P",
            "যাদের" to "P", "রশিদ" to "P", "লেট" to "G", "শর্ট" to "G", "শেস" to "P", "সেট" to "G"
        )
    }
}
