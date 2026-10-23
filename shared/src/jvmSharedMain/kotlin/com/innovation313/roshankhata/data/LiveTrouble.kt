package com.innovation313.roshankhata.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Live sync's failures that a round passes over (23 Oct 2026, the handover:
 * "swallowed sync failures are not written to errors.log"). A round that
 * failed is tried again, so the screens go on; but the reason was lost, and a
 * relay or a book that failed every round for a week left nothing to read.
 *
 * Each platform says where a failure is written ([sink]: the computer's
 * logs/errors.log through its CrashLog); none is written anywhere while it is
 * null. Kept to one line of each kind every [EVERY_MS], and [PER_HOUR] in all,
 * so a relay that fails every few seconds cannot fill the disk or bury a crash.
 */
object LiveTrouble {

    /** Where a failure goes: (where it happened, what was thrown). Set by the platform; null writes nothing. */
    @Volatile var sink: ((String, Throwable) -> Unit)? = null

    const val EVERY_MS = 10 * 60_000L
    const val PER_HOUR = 20

    private val last = ConcurrentHashMap<String, Long>()
    private var hourStart = 0L
    private var inHour = 0

    /**
     * [e], thrown at [where], written down unless the same kind was written in the last [EVERY_MS] or the hour's
     * [PER_HOUR] are spent. A coroutine's cancellation is never a failure. True when it was written.
     */
    fun note(where: String, e: Throwable, now: Long = System.currentTimeMillis()): Boolean {
        if (e is kotlinx.coroutines.CancellationException) return false
        val to = sink ?: return false
        val kind = where + "|" + e.javaClass.name
        synchronized(this) {
            val before = last[kind]
            if (before != null && now - before < EVERY_MS) return false
            if (now - hourStart >= 60 * 60_000L) { hourStart = now; inHour = 0 }
            if (inHour >= PER_HOUR) return false
            inHour++
            last[kind] = now
            if (last.size > 200) last.entries.removeIf { now - it.value >= EVERY_MS }
        }
        // The writer must never make the failure worse.
        try { to("live sync, " + where, e) } catch (t: Throwable) { return false }
        return true
    }

    /** For tests: forgets what was written. */
    fun reset() = synchronized(this) { last.clear(); hourStart = 0L; inHour = 0 }
}
