package com.innovation313.roshankhata.data

/**
 * A cache that must not outlive the shop it was filled for (24 Nov 2026, BC-250).
 *
 * A load reads [now] BEFORE it looks at the shop's folder, and puts its answer through [putIfCurrent]; a shop switch
 * drops the cache through [drop]. [drop] bumps the epoch and clears under the same lock a put takes, so a load that
 * started before the drop can never put the old shop's picture after it (and one that started after it, for the new
 * shop, is never refused). A refused put only costs a cache miss: the next ask loads again from the right folder.
 * Pure, so the rule is run by the shared tests.
 */
class CacheEpoch {
    private val lock = Any()
    private var epoch = 0L

    /** The epoch a load works under: read it first, before the shop's folder is looked at. */
    fun now(): Long = synchronized(lock) { epoch }

    /** Bumps the epoch and runs [clear], so nothing begun before it can land after it. */
    fun drop(clear: () -> Unit) = synchronized(lock) { epoch++; clear() }

    /** Runs [put] only if no [drop] happened since [seen] was read; returns whether it ran. */
    fun putIfCurrent(seen: Long, put: () -> Unit): Boolean = synchronized(lock) {
        if (seen != epoch) false else { put(); true }
    }
}
