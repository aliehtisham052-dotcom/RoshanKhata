package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Live sync window called every joined device "Live", in green, even one
 * silent for weeks (25 Oct 2026, the audit). Now by when it was last heard.
 */
class PeerStateTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    @Test
    fun `heard within its daily word is live`() {
        assertEquals(PeerState.LIVE, peerState(now - 5 * 60_000L, now))
        assertEquals(PeerState.LIVE, peerState(now - 25 * hour, now))
    }

    @Test
    fun `quiet for days, then stale past a week, and never heard`() {
        assertEquals(PeerState.QUIET, peerState(now - 3 * 24 * hour, now))
        assertEquals(PeerState.STALE, peerState(now - 21 * 24 * hour, now))
        assertEquals(PeerState.NEVER, peerState(null, now))
    }
}
