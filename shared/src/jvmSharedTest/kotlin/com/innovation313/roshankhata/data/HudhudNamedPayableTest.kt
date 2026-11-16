package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A NAMED customer or supplier with the words of the shop's own debt stays his account (16 Nov 2026, lesson 129).
 *
 * `Hudhud.decide` turns a sentence that says the money is the shop's to pay ([VoiceQuestion.payableSaid]) into the shop's
 * whole to-give when the sense read it as a receivable — but not when the rules found somebody named (`namedBy(ruled)`):
 * "sab se zyada Ali Raza ka mujh par" asks about HIM, not the shop's total to every supplier. A deliberate breakage of
 * that one condition survived every test before this class, and a probe of every sentence of the bank, the held-out set,
 * the owner's lists and both review sets (about 17,000) found none where it decides: with a name in the sentence the sense
 * reads him, not a receivable. It decides for "most" said beside a name and "mujh par" — the sense's first reading is then
 * "who_owes_most" (a receivable) and the sentence states the shop's debt, so without the condition the answer would be the
 * shop's whole to-give. Those sentences are the first test; the second holds the rest of the class (a named person the shop
 * owes stays his account), which other guards also hold.
 */
class HudhudNamedPayableTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Imran Traders", "Malik Store", "Saleem Bhai").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }

    private fun read(said: String): Hudhud.Ask =
        Hudhud.understand(said, names, emptyList(), null).let { (it as? Hudhud.Ask.Guessed)?.ask ?: it }

    @Test fun `most said beside a name and the shop's debt is that person's account, in any script he is written in`() {
        val cases = listOf(
            "sab se zyada Ali Raza ka mujh par" to 1L,
            "sab se zyada علی رضا ka mujh par" to 1L,
            "sab se zyada Malik Store ka mujh par" to 5L,
            "sab se zyada Saleem Bhai ka mujh par" to 6L
        )
        for ((said, id) in cases) {
            assertTrue("'$said' states the shop's debt", VoiceQuestion.payableSaid(said))
            assertEquals(said, Hudhud.Ask.Parties(listOf(id)), read(said))
        }
    }

    @Test fun `a named person the shop owes stays his account, never the shop's whole to-give`() {
        val cases = listOf(
            "Ali Raza kitna lena hai mujh par" to 1L,
            "Ali Raza ka kitna lena baqi hai mujh par" to 1L,
            "how much is pending to supplier Imran Traders" to 4L
        )
        for ((said, id) in cases) {
            assertTrue("'$said' states the shop's debt", VoiceQuestion.payableSaid(said))
            assertEquals(said, Hudhud.Ask.Parties(listOf(id)), read(said))
        }
    }
}
