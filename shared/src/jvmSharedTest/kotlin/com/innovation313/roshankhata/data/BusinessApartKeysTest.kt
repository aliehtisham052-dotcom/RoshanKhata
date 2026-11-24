package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 4 (24 Nov 2026): a shop's own preference keys are told from every other shop's, invented keys only. */
class BusinessApartKeysTest {
    @Test fun businessOneHasNoSuffixAndTheOthersAreNamed() {
        assertEquals("", BusinessKeys.suffixFor(1))
        assertEquals("_b2", BusinessKeys.suffixFor(2))
        assertEquals("order_draft", BusinessKeys.keyOf("order_draft", 1))
        assertEquals("order_draft_b12", BusinessKeys.keyOf("order_draft", 12))
    }

    @Test fun twoShopsNeverShareADraftKey() {
        val keys = (1L..40L).map { BusinessKeys.keyOf("count_draft", it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun aSuffixMatchesOnlyItsOwnShop() {
        assertTrue(BusinessKeys.endsWithBusinessSuffix("said_cheques_b3", 3))
        assertFalse(BusinessKeys.endsWithBusinessSuffix("said_cheques_b13", 3))
        assertFalse(BusinessKeys.endsWithBusinessSuffix("said_cheques_b33", 3))
        assertFalse(BusinessKeys.endsWithBusinessSuffix("said_cheques_b3", 13))
    }

    @Test fun businessOneIsNeverMatchedByAnyRule() {
        assertFalse(BusinessKeys.endsWithBusinessSuffix("said_cheques_b1", 1))
        assertFalse(BusinessKeys.endsWithShopId("fields_1", 1))
        for (f in BusinessKeys.SHARED_FILES) assertFalse(f, BusinessKeys.owns(f, "anything_b1|roshan_khata.db", 1, "roshan_khata.db"))
    }

    @Test fun anIdKeyMatchesItsShopAndNotATenfoldNeighbour() {
        assertTrue(BusinessKeys.endsWithShopId("fields_3", 3))
        assertFalse(BusinessKeys.endsWithShopId("fields_13", 3))
        assertFalse(BusinessKeys.endsWithShopId("fields_3", 13))
    }

    @Test fun aBookFileIsFoundAsAWholeField() {
        assertTrue(BusinessKeys.namesBookFile("sent|roshan_khata_b3.db", "roshan_khata_b3.db"))
        assertTrue(BusinessKeys.namesBookFile("sent|roshan_khata_b3.db|x", "roshan_khata_b3.db"))
        assertFalse(BusinessKeys.namesBookFile("sent|roshan_khata_b13.db", "roshan_khata_b3.db"))
        assertFalse(BusinessKeys.namesBookFile("sent|xroshan_khata_b3.db", "roshan_khata_b3.db"))
    }

    @Test fun aDeletedShopsLeftoversAreFoundInEverySharedFileAndNobodyElsesAre() {
        val id = 3L
        val file = "roshan_khata_b3.db"
        // file -> (its own keys, keys of other shops that must stay)
        val own = mapOf(
            "automations" to listOf("said_cheques_b3", "said_collect_b3"),
            "form_drafts" to listOf("order_draft_b3", "count_draft_b3", "return_draft_b3"),
            "book_seal" to listOf("day_b3"), "seal_offer" to listOf("done_b3"), "snapshots" to listOf("last_b3"),
            "poster" to listOf("fields_3", "lang_3"), "rate_list" to listOf("excluded_3", "company_3", "heading_3"),
            "customer_link" to listOf("sent|$file", "page|$file|7"), "live_sync" to listOf("peer|$file")
        )
        val kept = listOf(
            "said_cheques_b13", "said_cheques_b2", "said_cheques", "order_draft", "order_draft_b33", "fields_13", "fields_1",
            "excluded_30", "sent|roshan_khata_b13.db", "sent|roshan_khata.db", "on_cheques", "hour_cheques"
        )
        assertEquals("every shared file is covered by this test", BusinessKeys.SHARED_FILES, own.keys)
        for ((f, keys) in own) {
            for (k in keys) assertTrue("$f/$k is the deleted shop's", BusinessKeys.owns(f, k, id, file))
            for (k in kept) assertFalse("$f/$k is not the deleted shop's", BusinessKeys.owns(f, k, id, file))
        }
    }

    @Test fun aFileNobodyListedOwnsNothing() {
        assertFalse(BusinessKeys.owns("some_new_prefs", "x_b3", 3, "roshan_khata_b3.db"))
    }
}
