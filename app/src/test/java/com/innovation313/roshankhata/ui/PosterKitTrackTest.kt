package com.innovation313.roshankhata.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which poster texts may be letter-spaced (13 Nov 2026, the poster review). Tracking opens a gap inside a word whose
 * letters join, and in the desktop proofs it split Devanagari and Bengali words: the Hindi kicker read "अ भी-अ भी आ या"
 * and the closed sign "बं द". Only Latin words are tracked (the phone's own text engine was not run for this).
 */
class PosterKitTrackTest {

    @Test
    fun `Latin words are letter spaced`() {
        assertTrue(PosterKit.canTrack("JUST ARRIVED"))
        assertTrue(PosterKit.canTrack("CLOSED"))
        assertTrue(PosterKit.canTrack("Baru datang"))
        assertTrue(PosterKit.canTrack("0300 1234567"))
    }

    @Test
    fun `Arabic script words are never letter spaced`() {
        assertFalse(PosterKit.canTrack("بند"))
        assertFalse(PosterKit.canTrack("نیا مال آ گیا"))
        assertFalse(PosterKit.canTrack("بسته"))
        assertFalse(PosterKit.canTrack("نئون"))
    }

    @Test
    fun `Devanagari and Bengali words are never letter spaced`() {
        assertFalse(PosterKit.canTrack("बंद"))
        assertFalse(PosterKit.canTrack("अभी-अभी आया"))
        assertFalse(PosterKit.canTrack("বন্ধ"))
        assertFalse(PosterKit.canTrack("সদ্য এসেছে"))
    }

    @Test
    fun `one joined letter in a mixed text is enough`() {
        assertFalse(PosterKit.canTrack("Al-Noor النور"))
        assertFalse(PosterKit.canTrack("SALE छूट"))
    }
}
