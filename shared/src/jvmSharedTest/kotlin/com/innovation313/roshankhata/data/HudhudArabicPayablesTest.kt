package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two Arabic readings of the audit's BC-256 (24 Nov 2026), through the whole pipeline with the shipped sense (lesson 145):
 *
 *  - "أي الموردين لم أدفع لهم" (which suppliers have I not paid) read as the goods that came in (Deliveries): it is what the shop owes
 *    its suppliers, as English "which suppliers have I not paid" is (TO_GIVE);
 *  - "من يدين لي بأكبر مبلغ" (who owes me the biggest sum) read as the shop's total to get (TO_GET): it is who owes the shop most.
 *
 * Names are invented. Arabic wording was written by Claude and wants a native speaker's eye.
 */
class HudhudArabicPayablesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val pages = HudhudRealPages.forApp("ar")

    private fun read(said: String): Hudhud.Ask {
        val a = Hudhud.understand(said, names, pages, "ar", emptyList(), null)
        return if (a is Hudhud.Ask.Guessed) a.ask else a
    }

    private fun figure(said: String): VoiceQuestion.Kind? = (read(said) as? Hudhud.Ask.Figure)?.kind

    @Test fun `which suppliers the shop has not paid is what it owes them`() {
        for (s in listOf(
            "أي الموردين لم أدفع لهم",
            "أي الموردين لم أدفع لهم بعد",
            "من لم أدفع له من الموردين",
            "أي مورد لم أسدد له",
            "الموردين الذين لم أسدد لهم"
        )) assertEquals(s, VoiceQuestion.Kind.TO_GIVE, figure(s))
    }

    @Test fun `who owes the shop the biggest sum is who owes most and not the total`() {
        for (s in listOf(
            "من يدين لي بأكبر مبلغ",
            "من مدين لي بأكثر",
            "من هو أكبر مدين لي",
            "من يدين لي بأعلى مبلغ"
        )) assertEquals(s, VoiceQuestion.Kind.WHO_OWES_MOST, figure(s))
    }

    @Test fun `the neighbours stay where they were`() {
        // The shop's own biggest debt: whom it owes most.
        assertEquals(VoiceQuestion.Kind.TO_GIVE, figure("لمن أدين بأكبر مبلغ"))
        // Customers who have not paid the shop are never the shop's payables.
        assertNotEquals(VoiceQuestion.Kind.TO_GIVE, figure("أي العملاء لم يدفعوا لي"))
        // "Not paid" without a supplier is no supplier question.
        assertNotEquals(VoiceQuestion.Kind.TO_GIVE, figure("من لم يدفع لي"))
        // "I have not paid" with no supplier in it is no supplier question (an electricity bill is not a payable to a supplier).
        assertNotEquals(VoiceQuestion.Kind.TO_GIVE, figure("لم أدفع فاتورة الكهرباء بعد"))
        // Goods that came from a supplier today are still the deliveries.
        assertTrue(read("أي الموردين جاءت منهم بضاعة اليوم").toString(), read("أي الموردين جاءت منهم بضاعة اليوم") is Hudhud.Ask.Deliveries)
    }

    @Test fun `the rules alone say both readings (the sense is not what holds them)`() {
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE), Hudhud.byRules("أي الموردين لم أدفع لهم", names, pages, "ar"))
        assertEquals(VoiceQuestion.Kind.WHO_OWES_MOST, VoiceQuestion.classify("من يدين لي بأكبر مبلغ"))
        assertEquals(VoiceQuestion.Kind.WHO_OWES_MOST, VoiceQuestion.classify("من مدين لي بأكثر"))
    }
}
