package com.innovation313.roshankhata.data

/**
 * What runs after a restored ledger has COMMITTED (BC-245, 24 Nov 2026). The ledger is replaced by then, so nothing
 * below may make the restore look "not restored", and nothing may stop the steps after it from running:
 *
 *  1. [endLiveSync]: the other device still has the old book, so live sync ends here. A failure leaves the book
 *     paused ("paused" = 1), which the next opening turns off as restored (LiveSync.check) — never resumed on a
 *     replaced book;
 *  2. [reattachPictures]: pictures kept by row id follow their rows; a failure leaves them unattached, not the book unrestored;
 *  3. [writeProfile]: the shop's profile lives in SharedPreferences and cannot ride in the Room transaction, so it can
 *     fail on its own: the outcome then says so ([Outcome.RESTORED_PROFILE_NOT_SET]) and the shop's old details stay
 *     as they were (or partly written; the owner opens Business Profile). Written AFTER the ledger on purpose: before
 *     it, a ledger that then failed would have changed the profile of a book that was never replaced.
 *
 * Each step is tried whatever the one before did; a coroutine's cancellation is not swallowed here (the caller runs
 * this under NonCancellable).
 */
object RestoreAfterCommit {

    enum class Outcome {
        /** The ledger and the profile are the copy's. */
        RESTORED,
        /** The ledger is replaced; the shop profile could not be written (the old details stand). */
        RESTORED_PROFILE_NOT_SET
    }

    suspend fun run(
        endLiveSync: (suspend () -> Unit)?,
        reattachPictures: (suspend () -> Unit)?,
        writeProfile: (suspend () -> Unit)?,
        note: (String, Throwable) -> Unit = { where, e -> LiveTrouble.note(where, e) }
    ): Outcome {
        suspend fun step(where: String, body: (suspend () -> Unit)?): Boolean {
            if (body == null) return true
            return try {
                body(); true
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                note(where, e)
                false
            }
        }
        step("restore end live sync", endLiveSync)
        step("restore reattach pictures", reattachPictures)
        return if (step("restore profile", writeProfile)) Outcome.RESTORED else Outcome.RESTORED_PROFILE_NOT_SET
    }
}
