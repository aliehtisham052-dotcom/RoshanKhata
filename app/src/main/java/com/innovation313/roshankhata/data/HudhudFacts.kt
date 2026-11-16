package com.innovation313.roshankhata.data

import android.content.Context

/**
 * The phone's door to Hudhud's wider knowledge, which lives in :shared since 16 Nov 2026 ([AnswerFacts], audit T22: one rule
 * for the phone and the computer). Only what the phone's tests ask for directly is here; the sheet asks [HudhudBook].
 */
object HudhudFacts {

    /** "Label: value", the value isolated so a name or a figure reads right in a right-to-left line. */
    fun line(label: String, value: String) = AnswerFacts.line(label, value)

    fun line(c: Context, label: Int, value: String) = AnswerFacts.line(c.getString(label), value)

    suspend fun person(
        c: Context, dao: KhataDao, ids: List<Long>, facet: Hudhud.Facet, parties: List<PartyWithBalance>
    ): HudhudBook.Answer = HudhudBook.card(AnswerFacts.person(HudhudPhoneEnv(c), dao, ids, facet, parties))

    fun shop(c: Context, facet: Hudhud.ShopFacet): HudhudBook.Answer = HudhudBook.card(AnswerFacts.shop(HudhudPhoneEnv(c), facet))

    suspend fun qarz(c: Context, dao: KhataDao, parties: List<PartyWithBalance>): HudhudBook.Answer =
        HudhudBook.card(AnswerFacts.qarz(HudhudPhoneEnv(c), dao, parties))
}
