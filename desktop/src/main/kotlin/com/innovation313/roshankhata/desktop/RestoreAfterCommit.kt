package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DesktopDatabase
import kotlin.coroutines.cancellation.CancellationException

/**
 * What runs after a restored ledger has COMMITTED, on the computer (BC-258, 24 Nov 2026) — the phone's
 * `data.RestoreAfterCommit` (BC-245) for the daily-copy restore and the corrupt-book rebuild. The ledger is replaced by
 * then, so nothing below may make the restore look "not restored", and no step may stop the steps after it:
 *
 *  - ending live sync (the other device still has the old book);
 *  - the shop's profile (kept in a file beside the book, outside the ledger's transaction, so it can fail on its own:
 *    the old details then stand and the owner opens Business Profile — [Result.profileNotSet]);
 *  - the screen's refresh and the removal of a copy made for the restore.
 *
 * Each step is tried whatever the one before did; a failure is written to errors.log ([noteToLog]) and the run goes on.
 * A coroutine's cancellation is not swallowed (the caller runs this under NonCancellable). Plain code, no Compose,
 * so a test can run every order of it.
 */
object RestoreAfterCommit {

    /** The step of [Step.where] that writes the shop's profile. */
    const val PROFILE = "restore profile"

    class Step(val where: String, val body: suspend () -> Unit)

    /** The steps that failed, by their [Step.where]; every step was tried. The ledger was replaced whatever is in it. */
    class Result(val failed: List<String>) {
        val allDone: Boolean get() = failed.isEmpty()
        /** The ledger is the copy's but the shop's own details are as they were (or partly written). */
        val profileNotSet: Boolean get() = PROFILE in failed
    }

    suspend fun run(steps: List<Step>, note: (String, Throwable) -> Unit = { w, e -> noteToLog(w, e) }): Result {
        val failed = ArrayList<String>()
        for (s in steps) {
            try {
                s.body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed += s.where
                safely(note, s.where, e)
            }
        }
        return Result(failed)
    }

    /** [body] once, its failure noted and answered with false — for a step that is not a coroutine (a callback). */
    fun guarded(where: String, note: (String, Throwable) -> Unit = { w, e -> noteToLog(w, e) }, body: () -> Unit): Boolean =
        try {
            body(); true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            safely(note, where, e); false
        }

    /** errors.log, in this computer's own folder; writing it never makes the failure worse. */
    fun noteToLog(where: String, e: Throwable) {
        try {
            CrashLog.write(DesktopDatabase.home(), "$where (the restore went through; not a crash)", e)
        } catch (t: Throwable) {
            // The log cannot be written: nothing more can be done.
        }
    }

    private fun safely(note: (String, Throwable) -> Unit, where: String, e: Throwable) {
        try { note(where, e) } catch (t: Throwable) { /* a failing note never stops the steps after it */ }
    }
}
