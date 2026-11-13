package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Hudhud's chips in the app's own nine languages (12 Nov 2026): each figure's chip, typed or said as it reads, is
 * understood as that very figure. The words are the app's strings.xml (desktopWords reads the same files the phone
 * shows), so a translation changed later into words Hudhud no longer understands fails here, not on a shop's phone.
 * Before this test eight chips were not understood as themselves (Arabic's two totals, Farsi's debt, Indonesian's
 * three, English and Sindhi expiry).
 */
class HudhudWordsTest {

    private val languages = listOf("en", "ur", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "id")

    private val chips = mapOf(
        "vq_profit_month" to Kind.PROFIT_MONTH,
        "vq_expiring" to Kind.EXPIRING,
        "vq_not_paid" to Kind.NOT_PAID,
        "vq_who_owes_most" to Kind.WHO_OWES_MOST,
        "vq_sales_today" to Kind.SALES_TODAY,
        "vq_sales_month" to Kind.SALES_MONTH,
        "vq_to_give" to Kind.TO_GIVE,
        "vq_to_get" to Kind.TO_GET
    )

    @Test fun `every figure's chip is understood as that figure, in all nine languages`() {
        val wrong = ArrayList<String>()
        for (tag in languages) {
            val words = desktopWords(Locale.forLanguageTag(tag))
            for ((name, kind) in chips) {
                val label = words.text(name)
                val got = VoiceQuestion.classify(label)
                if (got != kind) wrong += "$tag $name \"$label\" -> $got"
            }
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    /** Hudhud's own chips (12 Nov 2026): the backup, the drawer, today's summary, who will pay and what he can tell, in all nine. */
    @Test fun `every one of Hudhud's own chips is understood as itself, in all nine languages`() {
        val own = mapOf(
            "hudhud_chip_backup" to Hudhud.Ask.BackupStatus,
            "hudhud_chip_drawer" to Hudhud.Ask.Drawer,
            "hudhud_chip_today" to Hudhud.Ask.Today,
            "hudhud_chip_likely" to Hudhud.Ask.WhenPays(emptyList()),
            // 13 Nov 2026: "what can you tell me", the list of what he knows.
            "hudhud_chip_abilities" to Hudhud.Ask.Abilities
        )
        val wrong = ArrayList<String>()
        for (tag in languages) {
            val words = desktopWords(Locale.forLanguageTag(tag))
            for ((name, ask) in own) {
                val label = words.text(name)
                val got = Hudhud.understand(label, emptyList(), emptyList())
                if (got != ask) wrong += "$tag $name \"$label\" -> $got"
            }
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    // 13 Nov 2026 (Brain v2): the new chips, typed or said as they read, in all nine — and those shown under a
    // customer's or a product's answer carry on from him or from it, with no "uska".

    private val book = listOf(Hudhud.Name(1, "Abid Ali"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Naeem Khan"))
    private val goods = listOf(Hudhud.Name(101, "Urea"), Hudhud.Name(102, "DAP"))

    private fun wrongOf(cases: Map<String, Hudhud.Ask>, before: Hudhud.Ask? = null, names: List<Hudhud.Name> = emptyList()): List<String> {
        val wrong = ArrayList<String>()
        for (tag in languages) {
            val words = desktopWords(Locale.forLanguageTag(tag))
            for ((name, ask) in cases) {
                val label = words.text(name)
                val got = Hudhud.understand(label, names, emptyList(), null, goods, before)
                if (got != ask) wrong += "$tag $name \"$label\" -> $got"
            }
        }
        return wrong
    }

    @Test fun `Brain v2's own chips are understood as themselves, in all nine languages`() {
        val today = VoiceQuestion.Window.TODAY
        val wrong = wrongOf(mapOf(
            "hudhud_chip_date" to Hudhud.Ask.Clock,
            "hudhud_chip_maker" to Hudhud.Ask.About(Hudhud.AboutTopic.MAKER),
            "hudhud_chip_privacy" to Hudhud.Ask.About(Hudhud.AboutTopic.PRIVACY),
            "hudhud_chip_who" to Hudhud.Ask.Chat(Hudhud.Talk.WHO_ARE_YOU),
            "hudhud_chip_my_name" to Hudhud.Ask.About(Hudhud.AboutTopic.OWNER_NAME),
            "hudhud_chip_shop" to Hudhud.Ask.Shop(Hudhud.ShopFacet.ALL),
            "hudhud_chip_shop_phone" to Hudhud.Ask.Shop(Hudhud.ShopFacet.PHONE),
            "hudhud_chip_someone" to Hudhud.Ask.NeedName,
            "hudhud_chip_book" to Hudhud.Ask.Status(Hudhud.StatusOf.BOOK),
            "hudhud_chip_activity" to Hudhud.Ask.Activity(today),
            "hudhud_chip_collected" to Hudhud.Ask.Collected(today),
            // A product's "sold this month" typed alone is the shop's month (naming the product asks the product's).
            "hudhud_chip_sold" to Hudhud.Ask.Figure(Kind.SALES_MONTH)
        ))
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    @Test fun `a customer's chips carry on from him, in all nine languages`() {
        val him = listOf(2L)
        val wrong = wrongOf(mapOf(
            "hudhud_l_phone" to Hudhud.Ask.Person(him, Hudhud.Facet.PHONE),
            "hudhud_chip_details" to Hudhud.Ask.Person(him, Hudhud.Facet.DETAILS),
            "hudhud_l_last_payment" to Hudhud.Ask.Person(him, Hudhud.Facet.LAST_PAYMENT),
            "hudhud_l_last_entry" to Hudhud.Ask.Person(him, Hudhud.Facet.LAST_ENTRY),
            "hudhud_l_promise" to Hudhud.Ask.Person(him, Hudhud.Facet.PROMISE),
            "credit_limit" to Hudhud.Ask.Person(him, Hudhud.Facet.LIMIT),
            "hudhud_l_since" to Hudhud.Ask.Person(him, Hudhud.Facet.SINCE),
            "hudhud_l_entries" to Hudhud.Ask.Person(him, Hudhud.Facet.ENTRIES),
            "hudhud_chip_when_pays" to Hudhud.Ask.WhenPays(him),
            "hudhud_l_balance" to Hudhud.Ask.Parties(him)
        ), before = Hudhud.Ask.Person(him, Hudhud.Facet.PHONE), names = book)
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    @Test fun `a product's chips carry on from it, in all nine languages`() {
        val urea = listOf(101L)
        val wrong = wrongOf(mapOf(
            "hudhud_l_price" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.PRICE),
            "hudhud_l_stock" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.STOCK),
            "hudhud_l_cost" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.COST),
            "hudhud_chip_suppliers" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.SUPPLIERS),
            "hudhud_chip_buyers" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.BUYERS),
            "hudhud_l_last_in" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.LAST_IN),
            "hudhud_chip_details" to Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.OVERVIEW)
        ), before = Hudhud.Ask.Goods(urea, Hudhud.GoodsFacet.PRICE), names = book)
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }
}
