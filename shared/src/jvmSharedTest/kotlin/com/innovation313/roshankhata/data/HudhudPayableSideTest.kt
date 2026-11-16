package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The shop's to pay is never read as the shop's to get (16 Nov 2026, audit T10, register BC-097). The entity metrics found
 * supplier questions, "whom have I not paid" and "whose money is with me" read as who owes the shop, and the sense turned
 * "sab se zyada kis ko dena hai" from what the shop owes into its biggest debtor. Through the whole pipeline (lesson 145).
 */
class HudhudPayableSideTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }

    private fun read(lang: String, said: String): Hudhud.Ask {
        val a = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, emptyList(), null)
        return if (a is Hudhud.Ask.Guessed) a.ask else a
    }

    private val toGive = Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)

    @Test fun `what the shop owes stays the shop's to pay`() {
        for (said in listOf(
            "Sab se zyada kis ko dena hai",
            "Kis supplier ka qarz sab se zyada hai",
            "Kis supplier ko abhi tak kuch nahi diya",
            "Kis supplier ko payment late ho gayi",
            "Kis supplier ko sab se zyada diya",
            "Kis ko aaj tak nahi diya",
            "Kis kis ka paisa mere paas hai",
            "Dene wali raqam batao",
        )) assertEquals(said, toGive, read("ur-Latn", said))
    }

    @Test fun `who owes the shop stays the shop's to get`() {
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID), read("ur-Latn", "paise na dene wale kaun hain"))
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID), read("ur-Latn", "Kis ne abhi tak paise nahi diye"))
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), read("fa", "بیشترین بدهکار کیست"))
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), read("bn", "কে আমার কাছে সবচেয়ে বেশি দেনা"))
        assertNotEquals(toGive, read("ur-Latn", "Sab se achha payment dene wala kaun hai"))
    }

    /** Credit given TO somebody is his to pay back: "kis ko udhaar diya" is never the shop's debt. */
    @Test fun `credit given to whom is not the shop's debt`() {
        assertNotEquals(toGive, read("ur-Latn", "Kis ko udhaar diya aur abhi tak nahi diya"))
    }

    /** A named customer stays the customer's question, whatever the side words. */
    @Test fun `a name keeps its own reading`() {
        assertNotEquals(toGive, read("ur-Latn", "Ali Raza ko payment late ho gayi"))
        for (said in listOf("Bashir Ahmad supplier ka qarz kitna hai", "Bashir Ahmad ko abhi tak supplier ka kuch nahi diya"))
            assertNotEquals(said, toGive, read("ur-Latn", said))
    }

    /** The rules alone (lesson 129): each of these is read as the shop's to pay before the sense is asked. */
    @Test fun `the rules read the payable side themselves`() {
        for (said in listOf("Kis ko aaj tak nahi diya", "Kis supplier ka qarz sab se zyada hai", "Kis kis ka paisa mere paas hai",
            "Kis supplier ko abhi tak kuch nahi diya"))
            assertEquals(said, VoiceQuestion.Kind.TO_GIVE, VoiceQuestion.classify(said))
        assertEquals(VoiceQuestion.Kind.NOT_PAID, VoiceQuestion.classify("Kis ne abhi tak paise nahi diye"))
    }
}
