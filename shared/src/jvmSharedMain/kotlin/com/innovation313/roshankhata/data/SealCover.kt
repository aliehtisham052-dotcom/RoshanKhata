package com.innovation313.roshankhata.data

/**
 * Which entries a seal covers, from two figures read once (8 Nov 2026, the owner's "Kitab ki Muhar is invisible").
 * Seals chain without gaps: the first seal reaches up to [firstTo] (entries at or below it were written before sealing
 * began and no seal covers them), and every entry with an id in (firstTo, lastTo] is in exactly one seal's range
 * (fromId, toId]. An entry above [lastTo] goes into the next seal.
 */
class SealCover(val firstTo: Long, val lastTo: Long) {
    /** In some seal's range. */
    fun covers(entryId: Long): Boolean = entryId > firstTo && entryId <= lastTo

    /** Written after the newest seal: the next one will hold it. */
    fun waiting(entryId: Long): Boolean = entryId > lastTo

    companion object {
        /** The book's two figures with two cheap reads; null while the book has no seal. */
        suspend fun read(dao: KhataDao): SealCover? {
            val first = dao.firstSealTo() ?: return null
            val last = dao.latestSealHead()?.toId ?: return null
            return SealCover(first, last)
        }
    }
}
