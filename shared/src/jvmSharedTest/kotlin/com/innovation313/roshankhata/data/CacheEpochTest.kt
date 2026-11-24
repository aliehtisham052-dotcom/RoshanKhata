package com.innovation313.roshankhata.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BC-250 (24 Nov 2026): a load that began before a shop switch can never put its answer after the cache was dropped. */
class CacheEpochTest {
    private val cache = HashMap<Long, String>()
    private val epoch = CacheEpoch()

    @Test fun aPutBeforeAnyDropLands() {
        val seen = epoch.now()
        assertTrue(epoch.putIfCurrent(seen) { cache[1] = "shop A's face" })
        assertEquals("shop A's face", cache[1])
    }

    @Test fun aLoadThatBeganBeforeTheDropIsRefusedAfterIt() {
        val seen = epoch.now()                        // the load starts, working on shop A's folder
        epoch.drop { cache.clear() }                  // the owner switches to shop B
        assertFalse(epoch.putIfCurrent(seen) { cache[1] = "shop A's face" })
        assertTrue("nothing of shop A is left for shop B's customer 1", cache.isEmpty())
    }

    @Test fun aLoadThatBeganAfterTheDropIsAccepted() {
        epoch.drop { cache.clear() }
        val seen = epoch.now()
        assertTrue(epoch.putIfCurrent(seen) { cache[1] = "shop B's face" })
        assertEquals("shop B's face", cache[1])
    }

    @Test fun theDropClearsWhatWasThere() {
        epoch.putIfCurrent(epoch.now()) { cache[7] = "x" }
        epoch.drop { cache.clear() }
        assertTrue(cache.isEmpty())
    }

    @Test fun aRefusedPutRunsNothing() {
        val seen = epoch.now()
        epoch.drop { }
        var ran = false
        epoch.putIfCurrent(seen) { ran = true }
        assertFalse(ran)
    }

    /** Many loads race one drop: whatever order they run in, none of the old generation is in the cache once the drop has returned. */
    @Test fun noLoadOfTheOldShopSurvivesTheDropWhateverTheTiming() {
        repeat(200) { round ->
            val shared = HashMap<Long, String>()
            val e = CacheEpoch()
            val pool = Executors.newFixedThreadPool(5)
            val go = CountDownLatch(1)
            val seenBefore = (1..4).map { e.now() }
            val loads = seenBefore.mapIndexed { i, seen ->
                pool.submit { go.await(); e.putIfCurrent(seen) { synchronized(shared) { shared[i.toLong()] = "old" } } }
            }
            val drop = pool.submit { go.await(); e.drop { synchronized(shared) { shared.clear() } } }
            go.countDown()
            loads.forEach { it.get(5, TimeUnit.SECONDS) }
            drop.get(5, TimeUnit.SECONDS)
            pool.shutdown()
            // The drop has returned: every load began before it, so none may have been kept after it.
            assertTrue("round $round: ${shared}", synchronized(shared) { shared.isEmpty() })
        }
    }
}
