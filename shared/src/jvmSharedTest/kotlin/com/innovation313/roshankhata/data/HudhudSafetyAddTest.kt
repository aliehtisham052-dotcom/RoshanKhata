package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 14 Nov 2026, the owner's 1,000 sentences, by class: keeping the book safe opens the lock's or the hiding's help page,
 * something new to put in the book opens that feature's page, and the staff named as a feature is the staff's own
 * answer. An everyday verb is never a concept by its sound ("karna" sounds like Indonesian "karyawan", staff).
 */
class HudhudSafetyAddTest {

    private val topics = listOf("faq_lock", "faq_hide", "intro_staff", "screen_bills", "intro_cheques", "screen_products")
        .map { Hudhud.Topic(it, it, it) }

    private fun read(s: String) = Hudhud.byRules(s, emptyList(), topics)

    @Test fun `keeping the book safe is the lock's or the hiding's page`() {
        assertEquals(Hudhud.Ask.Help("faq_lock"), read("PIN bhool gaya"))
        assertEquals(Hudhud.Ask.Help("faq_lock"), read("Fingerprint lagao"))
        assertEquals(Hudhud.Ask.Help("faq_lock"), read("Naya PIN banana hai"))
        assertEquals(Hudhud.Ask.Help("faq_lock"), read("پاس ورڈ کیسے لگاؤں"))
        assertEquals(Hudhud.Ask.Help("faq_hide"), read("Khata chhupa kar rakhna hai"))
        assertEquals(Hudhud.Ask.Help("faq_hide"), read("Screenshot band karna hai"))
    }

    @Test fun `something new opens that feature's page`() {
        assertEquals(Hudhud.Ask.Help("intro_staff"), read("Naya mulazim add karna hai"))
        assertEquals(Hudhud.Ask.Help("screen_bills"), read("Naya bill add karna hai"))
        assertEquals(Hudhud.Ask.Help("intro_cheques"), read("naya cheque kahan add karna hai"))
        assertEquals(Hudhud.Ask.Help("screen_products"), read("naya maal add karna hai"))
    }

    @Test fun `the staff's own account is the staff's answer`() {
        assertEquals(Hudhud.Ask.Status(Hudhud.StatusOf.STAFF), read("Mulazim ka hisaab dikhao"))
    }

    @Test fun `an everyday verb is not the staff`() {
        assertEquals(Hudhud.Ask.Help("screen_bills"), read("naya bill add karna hai"))
    }
}
