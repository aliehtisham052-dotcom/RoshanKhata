package com.innovation313.roshankhata.data

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A coroutine scope that lives as long as the app does, not as long as
 * whichever screen happened to start the work.
 *
 * THE BUG THIS FIXES. Saving a ledger entry, a supplier bill, a cheque, or a
 * payment plan used to run inside `lifecycleScope.launch { ... }` on the
 * screen that took the input. That scope is tied to the Activity: the moment
 * the Activity is destroyed — which `finish()` does immediately, and which a
 * back-press does the instant it pops that screen off the stack — every
 * coroutine still running in it is cancelled mid-flight.
 *
 * None of these save functions show a spinner or a "saving…" state; the
 * dialog closes the instant Save is tapped, while the write is still going in
 * the background. An owner who taps Save and immediately presses Back — which
 * is the ordinary, fast way to use a ledger app at a counter — can therefore
 * have the screen close, the money vanish, and nothing on screen ever say so.
 * The entry looks saved because the dialog is gone; it was never written.
 *
 * This is exactly the shape of a real, reported symptom: entries added,
 * confirmed by eye, and missing from a backup taken minutes later — with nothing
 * else wrong, because nothing else was. A cancelled write leaves no error, no
 * partial row, no trace at all.
 *
 * THE FIX. A write that must complete regardless of what the owner does next
 * cannot be tied to a screen the owner is free to leave. [AppScope] is backed
 * by a [SupervisorJob], not the lifecycle of any Activity, so it is never
 * cancelled by navigation, by `finish()`, or by one save failing before another
 * on the same scope. It ends only if the whole process does — and a save that
 * cannot outlive the process it runs in was never going to survive a crash
 * either way, which is a different problem, not made worse by this one.
 *
 * WHAT DOES NOT BELONG HERE. Anything that reads the current screen back —
 * observing a Flow to update a list, or touching a View — must stay on the
 * screen's own `lifecycleScope`, precisely so it IS cancelled when the screen
 * goes away; running that kind of work here would be the opposite mistake,
 * updating a View that no longer exists. A save that must show a toast after
 * writing hops back to `Dispatchers.Main` and checks the Activity is still
 * alive before touching it — the write happens either way, and the toast is
 * offered only if there is still a screen to show it on.
 *
 * A WRITE THAT FAILS (23 Oct 2026, the review). An exception thrown inside
 * [AppScope]'s `launch` used to reach the thread's default handler, and the
 * whole app died — on a phone whose storage is full (SQLite cannot grow its
 * file) every save of a sale or a payment did, with the dialog already gone
 * as if saved. Now it goes to [AppFailures.report]: the app writes it down
 * and tells the owner it was NOT saved. The transaction rolled back, so the
 * book holds nothing half-written.
 */
object AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> AppFailures.report(e) }
)

/**
 * Where a failed [AppScope] job is told (23 Oct 2026). The phone sets [report]
 * once, in its Application (the crash record, then "not saved" on screen).
 * Unset, it hands the failure to the thread's own uncaught handler, as before.
 */
object AppFailures {
    @Volatile
    var report: (Throwable) -> Unit = { e ->
        val t = Thread.currentThread()
        t.uncaughtExceptionHandler?.uncaughtException(t, e) ?: throw e
    }
}
