package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import kotlinx.coroutines.CancellationException

/**
 * A book that could not be read is told as that, never as "none", "nothing waiting" or "all clear" (24 Nov 2026, stage 7 item F2).
 *
 * Until then the answer layer wrapped a handful of reads in `runCatching { … }.getOrDefault(emptyList())`: a bell that failed to
 * compute read as "nothing waiting for you today", a failed bill-item read as a bill with no items, a failed stock valuation as a
 * product with no low-stock line. Each of those is a reassuring answer made out of a failure — the costliest kind (lesson 151:
 * a wrong answer is worse than a miss). Two further harms of `runCatching`: it also swallows [CancellationException], so a
 * cancelled coroutine kept running as if its read had come back empty.
 *
 * The rule now: **a read in the answer layer is not caught.** The exception goes up to the platform's one catch (the phone's
 * sheet, the computer's panel), which shows [AnswerBook.failed] — the words below. The few places that really are best-effort
 * (a chip row that may stay empty, the voice's own words, the app's version name) go through [bestEffort], which names itself in
 * the code, never catches cancellation, and is held to a short list by `AnswerFailsLoudSourceTest`.
 */
object HudhudReadFailure {

    /**
     * The word key of what he says when the book could not be read ("I could not read your book right now. Nothing is lost —
     * please try again in a moment."). It is named as text, not as `W.hudhud_read_failed`, until the key is in strings.xml in all
     * nine languages (stage 7 report): [words] falls back to the old words when the name is not there yet. Then write
     * `W.hudhud_read_failed` in [AnswerBook.failed] and delete this fallback (and its test).
     */
    const val KEY = W.hudhud_read_failed

    /** The words used before [KEY] exists: "I could not find this in your khata…" (honest about not answering, not about why). */
    const val FALLBACK_KEY = "hudhud_unknown"

    /**
     * The failure words. [text] looks a word up by its name and throws for a name that is not in the app's strings (both the
     * phone's `WordIds` and the computer's `XmlWords` do); a blank answer counts as not there.
     */
    fun words(text: (String) -> String): String {
        val own = try {
            text(KEY)
        } catch (e: Exception) {
            null
        }
        return own?.takeIf { it.isNotBlank() } ?: text(FALLBACK_KEY)
    }

    /**
     * [block]'s answer, or [whenFailed] when it throws — except cancellation, which always goes on up (a cancelled read is no
     * empty read). Only for what says nothing when it is empty: a row of chips, a spoken sentence, a version name. Never for a
     * figure, a list of what is owed, what is waiting or what is in stock.
     */
    inline fun <T> bestEffort(whenFailed: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        whenFailed
    }
}
