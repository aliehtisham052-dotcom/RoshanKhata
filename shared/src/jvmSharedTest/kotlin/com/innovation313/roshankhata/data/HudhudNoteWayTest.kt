package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The private note through the whole of Hudhud (14 Nov 2026): the rules, the learnt sense and the how-to pages, not only the strict
 * reader ([HudhudNoteTest]). A note asked with a name is that person's note; asked with nobody named it is "whose?" (the list of who
 * has one), or the note of the person the talk was about; and a sentence that only holds the word "note" — a banknote, a notice, a
 * note to write — is never taken for it. A customer whose own name holds the word ("Note House") is a customer, not the ask.
 * Names are made up (lesson 80).
 */
class HudhudNoteWayTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Malik Store"),
        Hudhud.Name(8, "আলী"), Hudhud.Name(10, "Imran Khan")
    )

    /** A book with customers whose own names hold the word. */
    private val noted = names + listOf(Hudhud.Name(12, "Note House"), Hudhud.Name(13, "Noor Note Store"))

    private val topics = listOf(
        Hudhud.Topic("faq_invoice", "Invoice انوائس bill", "Make an invoice for a customer and share it"),
        Hudhud.Topic("intro_app", "App ایپ application", "What the app is and how it began, how to use it and how old it is"),
        Hudhud.Topic("intro_staff", "Staff and salary سٹاف تنخواہ", "Salary, advances and attendance of the staff")
    )

    private fun read(s: String, who: List<Hudhud.Name> = names, before: Ask? = null) =
        Hudhud.understand(s, who, topics, null, before = before)

    private fun person(id: Long, facet: Facet) = Ask.Person(listOf(id), facet)

    private fun isNote(a: Ask) = (a is Ask.Person && a.facet == Facet.NOTE) || (a is Ask.NeedName && a.facet == Facet.NOTE)

    @Test fun `a note asked with a name is that person's note`() {
        for (q in listOf(
            "Bashir ka note", "Bashir Ahmad ka note", "Bashir ka niji note dikhao", "Bashir's private note", "private note of Bashir",
            "what is the note for Bashir", "بشیر کا نجی نوٹ", "Hudhud Bashir ka note batao", "bhai jaan Bashir ka note bata do"
        )) assertEquals(q, person(6, Facet.NOTE), read(q))
        assertEquals(person(10, Facet.NOTE), read("Imran Khan ka note"))
        assertEquals(person(7, Facet.NOTE), read("Malik Store ka note"))
        assertEquals(person(1, Facet.NOTE), read("Ali Raza ka note"))
        assertEquals(person(2, Facet.NOTE), read("علی رضا کا نوٹ"))
    }

    @Test fun `a name that is several people is a choice, never a guess`() {
        for (q in listOf("Ali ka note", "Ali ka niji note", "علی کا نوٹ", "আলীর নোট")) {
            val a = read(q)
            assertTrue("\"$q\" -> $a", a is Ask.Person && a.facet == Facet.NOTE && a.ids.toSet() == setOf(1L, 2L, 8L))
        }
    }

    @Test fun `a note asked with nobody named is whose, the list is the answer`() {
        for (q in listOf(
            "note", "private note", "note dikhao", "my notes", "mere notes dikhao", "kis kis ka note hai", "who has a note",
            "which customers have notes", "sab ke notes", "uska note", "uska niji note kya hai", "his note", "میرے نوٹ دکھاؤ", "کس کس کا نوٹ ہے"
        )) assertEquals("\"$q\"", Ask.NeedName(Facet.NOTE), read(q))
    }

    @Test fun `with a person before, his own note follows`() {
        val before = person(6, Facet.PHONE)
        for (q in listOf("uska note", "note", "private note", "his note", "uska niji note kya hai"))
            assertEquals("\"$q\"", person(6, Facet.NOTE), read(q, before = before))
        // Everybody's, or the owner's own, is not about him.
        for (q in listOf("my notes", "who has a note", "kis kis ka note hai", "sab ke notes"))
            assertEquals("\"$q\"", Ask.NeedName(Facet.NOTE), read(q, before = before))
        // A name in the sentence is stronger than the talk before it.
        assertEquals(person(10, Facet.NOTE), read("Imran ka note", before = before))
    }

    @Test fun `the other facts of a person keep their answers`() {
        assertEquals(person(6, Facet.PHONE), read("Bashir ka number"))
        assertEquals(person(6, Facet.DETAILS), read("Bashir ka details"))
        assertEquals(Ask.Parties(listOf(6)), read("Bashir ka hisaab"))
    }

    @Test fun `a sentence that only holds the word note is not the note`() {
        for (q in listOf(
            "Bashir ko note do", "Bashir ka note likho", "note likho", "Bashir ko 500 ka note do", "banknote", "notice", "Bashir ne note diya",
            "credit note banao", "100 ke note"
        )) {
            val a = read(q)
            assertTrue("\"$q\" -> $a", !isNote(a))
        }
    }

    /** The word is the ask's, never a name's: a customer called "Note House" is found by his name and not by the word. */
    @Test fun `a customer whose own name holds the word is not found by the ask`() {
        for (q in listOf("note", "uska note", "private note", "my notes", "who has a note", "kis kis ka note hai", "sab ke notes"))
            assertEquals("\"$q\"", Ask.NeedName(Facet.NOTE), read(q, who = noted))
        assertEquals(person(6, Facet.NOTE), read("Bashir ka note", who = noted))
        assertEquals(person(6, Facet.NOTE), read("uska note", who = noted, before = person(6, Facet.PHONE)))
    }

    @Test fun `his own name said is the customer, his name and the ask is his note`() {
        assertEquals(Ask.Parties(listOf(12)), read("Note House ka hisaab", who = noted))
        assertEquals(person(12, Facet.PHONE), read("Note House ka number", who = noted))
        assertTrue(!isNote(read("Note House", who = noted)))
        assertEquals(person(12, Facet.NOTE), read("Note House ka note", who = noted))
        assertEquals(person(12, Facet.NOTE), read("Note House ka niji note", who = noted))
        assertEquals(person(12, Facet.NOTE), read("Note House's private note", who = noted))
    }

    /**
     * A customer named by one word of his name ("house" of "Note House"), with the one note word left in the sentence: with a marker
     * that says whose note is wanted ("niji", "private") the word is the ask; with none it is only his name and he is the answer.
     * (Lesson 100: a mutant that dropped the marker's part survived the tests before this one.)
     */
    @Test fun `a name said by its other word, with a marker, makes the one note word the ask`() {
        for (q in listOf("house ka niji note", "house ka private note", "house's private note", "private note of house"))
            assertEquals("\"$q\"", person(12, Facet.NOTE), read(q, who = noted))
        for (q in listOf("house ka note", "house note"))
            assertTrue("\"$q\" -> ${read(q, who = noted)}", !isNote(read(q, who = noted)))
    }

    @Test fun `the note is no label of the bank`() {
        assertNull(Hudhud.labelOf(person(1, Facet.NOTE)))
        assertNull(Hudhud.labelOf(Ask.NeedName(Facet.NOTE)))
    }

    @Test fun `the note word is told apart in every language`() {
        for (w in listOf("note", "notes", "Note", "نوٹ", "نوٽ", "नोट", "নোট", "ملاحظة", "یادداشت", "yaaddasht", "ملاحظته", "ملاحظتي", "catatan"))
            assertTrue(w, HudhudNote.isNoteWord(VoiceQuestion.normalise(w)))
        for (w in listOf("nota", "notice", "نوٹس", "banknote", "house", "ali", "number")) assertTrue(w, !HudhudNote.isNoteWord(VoiceQuestion.normalise(w)))
    }
}
