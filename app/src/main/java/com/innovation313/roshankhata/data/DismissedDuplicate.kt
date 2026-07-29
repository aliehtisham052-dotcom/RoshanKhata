package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A group the owner has already looked at on the "Duplicate customers"
 * screen and confirmed are different people, not the same person twice.
 *
 * Keyed by [DuplicateDetector.groupKey] — the sorted, comma-joined party
 * ids that made up the group at the moment it was dismissed, not by name.
 * That is what lets this expire itself correctly with no timer and no
 * "book changed" flag to maintain: if a phone number is added to one of
 * the two parties and they no longer share a signal, or a third party now
 * joins them, the id set is different and the group is a new one — it is
 * shown again on its own, because it genuinely is not the group that was
 * dismissed.
 */
@Entity(tableName = "dismissed_duplicates")
data class DismissedDuplicate(
    @PrimaryKey val partyIdsKey: String,
    val dismissedAt: Long
)
