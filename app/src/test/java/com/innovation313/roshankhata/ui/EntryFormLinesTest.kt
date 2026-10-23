package com.innovation313.roshankhata.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.LineDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The add-entry form's goods list survives the screen being rebuilt (R41): every field of every
 * line comes back as it was. Robolectric for the Android framework's own org.json.
 */
@RunWith(AndroidJUnit4::class)
class EntryFormLinesTest {

    @Test
    fun everyFieldOfEveryLineComesBack() {
        val lines = listOf(
            LineDraft(
                itemName = "Urea", quantity = 2.5, unit = "bag", rate = 4300.0,
                productId = 7L, billItemId = 12L, creditPrice = 4300.0, cashPrice = 4200.0,
                productUnit = "bag", rateEdited = false, isBonus = false,
                updateProductRate = true, addToProducts = false,
                crop = "Wheat", pest = "Aphid", dose = "1 per acre"
            ),
            LineDraft(itemName = "Sulphur", quantity = 1.0, unit = null, rate = null, rateEdited = true, addToProducts = true),
            LineDraft(itemName = "Old free line", quantity = 3.0, unit = "kg", rate = null, isBonus = true)
        )
        assertEquals(lines, EntryFormLines.fromJson(EntryFormLines.toJson(lines)))
    }

    @Test
    fun anEmptyListAndUnreadableTextComeBackEmpty() {
        assertTrue(EntryFormLines.fromJson(EntryFormLines.toJson(emptyList())).isEmpty())
        assertTrue(EntryFormLines.fromJson(null).isEmpty())
        assertTrue(EntryFormLines.fromJson("not a list").isEmpty())
    }
}
