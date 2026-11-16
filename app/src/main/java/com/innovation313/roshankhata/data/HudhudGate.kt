package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.AccessUi

/**
 * The phone's door to the one gate Hudhud's answers ask "who may see this?" through ([AnswerGate], :shared since 16 Nov 2026,
 * audit T22; its note says what each gate covers). The sheet asks the same three as the answers, and a page's own limit is
 * read from the screen's own gate ([AccessUi.needs]) by AnswerGate's one rule.
 */
internal object HudhudGate {
    fun reports() = AnswerGate.reports()

    fun staff() = AnswerGate.staff()

    fun settings() = AnswerGate.settings()

    /** The words (a resource) that say who may open [screen] on a counter phone; null: anyone signed in may open it. */
    fun pageLimit(screen: Class<*>?): Int? {
        val p = AccessUi.needs(screen ?: return null) ?: return null
        return AnswerGate.limitWord(p)?.let { WordIds.string(it) }
    }
}
