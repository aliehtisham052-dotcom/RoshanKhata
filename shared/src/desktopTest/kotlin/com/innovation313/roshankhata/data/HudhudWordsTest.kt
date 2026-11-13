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
}
