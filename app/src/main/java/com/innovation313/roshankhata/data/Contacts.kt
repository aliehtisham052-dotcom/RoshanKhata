package com.innovation313.roshankhata.data

import android.content.Context
import android.provider.ContactsContract

/** A contact read from the phone. Held in memory only, never written to disk. */
data class PhoneContact(
    val name: String,
    val phone: String,
    /** Already on the books — shown as "Added", not offered again. */
    val alreadyAdded: Boolean = false,
    /**
     * On the books, but sitting in the Recycle Bin.
     *
     * Neither "Added" nor importable. Importing would build a second, empty
     * party beside the binned one, and restoring the bin afterwards would
     * leave the owner with the same person twice — once with their history,
     * once without. Saying where they actually are is the honest answer.
     */
    val inRecycleBin: Boolean = false
)

/**
 * Reads the phone's contact list.
 *
 * Nothing here is stored. Contacts are read into memory, shown for the owner
 * to pick from, and dropped when the screen closes. Only the ones actually
 * chosen are saved — and only their name and number, nothing else.
 */
object Contacts {

    /** Digits only, so 0300-123 4567 and +923001234567 match as the same person. */
    private fun normalise(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        // Compare on the last 10 digits: that survives +92 / 0092 / leading-0
        // differences between how the same number is written in two places.
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun load(
        context: Context,
        existingPhones: List<String>,
        binnedPhones: List<String> = emptyList()
    ): List<PhoneContact> {
        val existing = existingPhones.map { normalise(it) }.toSet()
        val binned = binnedPhones.map { normalise(it) }.toSet()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val seen = mutableSetOf<String>()
        val result = mutableListOf<PhoneContact>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx < 0 || numIdx < 0) return@use

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val number = cursor.getString(numIdx)?.trim().orEmpty()
                if (name.isEmpty() || number.isEmpty()) continue

                val key = normalise(number)
                if (key.isEmpty()) continue

                // One phone can list the same number under home/mobile/work.
                // Show it once.
                if (!seen.add(key)) continue

                result += PhoneContact(
                    name = name,
                    phone = number,
                    alreadyAdded = key in existing,
                    inRecycleBin = key in binned
                )
            }
        }

        return result
    }
}
