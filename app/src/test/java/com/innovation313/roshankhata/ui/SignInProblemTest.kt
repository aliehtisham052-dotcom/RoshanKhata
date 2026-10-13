package com.innovation313.roshankhata.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which sign-in "cancellations" are the owner's own, and so get no technical dialog. */
class SignInProblemTest {

    @Test
    fun theOwnersOwnDismissalIsRecognisedHoweverGoogleWordsIt() {
        // Exactly as it came on the owner's phone, 13 Oct.
        assertTrue(SignInProblem.isOwnerDismissal("[16] Cancelled by user."))
        assertTrue(SignInProblem.isOwnerDismissal("activity is cancelled by the user."))
        assertTrue(SignInProblem.isOwnerDismissal("Canceled by user"))
        assertTrue(SignInProblem.isOwnerDismissal("[16] "))
    }

    @Test
    fun anythingElseStillGetsTheDialog() {
        assertFalse(SignInProblem.isOwnerDismissal(null))
        assertFalse(SignInProblem.isOwnerDismissal(""))
        assertFalse(SignInProblem.isOwnerDismissal("[10] Developer console is not set up correctly."))
        assertFalse(SignInProblem.isOwnerDismissal("During begin sign in, failure response from one tap: 16: [28433]"))
        assertFalse(SignInProblem.isOwnerDismissal("No credentials available"))
    }
}
