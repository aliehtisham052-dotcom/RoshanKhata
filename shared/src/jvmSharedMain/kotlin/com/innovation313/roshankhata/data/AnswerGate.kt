package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W

/**
 * The one door Hudhud's answers ask "who may see this?" through (14 Nov 2026, the owner: "access poori deni chahiye… jo
 * professional tareeqa hai wohi use karo" — a cashier signed in under counter mode sees only what his own screens show; the
 * owner, and a device with counter mode off, see everything). Shared since 16 Nov 2026 (audit T22): the phone and the computer
 * keep the same [Access], so one gate serves both; the phone's HudhudGate calls this one.
 *
 * Why one door: `scripts/gen_hudhud_catalog.py` holds every fact Hudhud tells to the gate of the screen that shows the same
 * fact (`shared/hudhud/catalog/decisions.tsv`), so a gate must be one the catalog knows by name. An answer file that asked
 * [Access] directly would be a gate nobody checked; `check_gates` in that script fails the build for it.
 *
 * Each function is named after the permission it reads (the script checks that too):
 *  - [reports]  the owner or a manager: every figure of money, the day's takings, purchase rates and costs.
 *  - [staff]    the owner only: the staff, their salaries and what was paid them (Staff is the owner's screen).
 *  - [settings] the owner only: Business Profile's private part — the shop's country, email, tax number, bank account,
 *               IBAN and wallet. (The shop's name, address, phone, currency and trade are printed on what a cashier hands
 *               out or read on every amount he sees, so they are not behind it; no screen of a cashier names the
 *               country, so it is.) What each of these is held to is in `AnswerFacts.shop`'s own note.
 */
object AnswerGate {
    fun reports() = Access.can(Access.Permission.REPORTS)

    fun staff() = Access.can(Access.Permission.STAFF)

    fun settings() = Access.can(Access.Permission.SETTINGS)

    /**
     * The words that say who may open a page whose own gate asks [p] (15 Nov 2026, the owner's "hudood"): null when anyone
     * signed in may open it. The owner's own things (settings, backup, staff) say the owner; the rest, the owner or a manager.
     */
    fun limitWord(p: Access.Permission?): String? {
        p ?: return null
        return if (Access.ownerOnly(p)) W.hudhud_limit_owner else W.hudhud_limit_manager
    }

    /** [limitWord] for [screen], read from the page's own gate ([AnswerEnv.screenNeeds]), never from a list of Hudhud's. */
    fun pageLimit(env: AnswerEnv, screen: ScreenId?): String? {
        screen ?: return null
        return limitWord(env.screenNeeds(screen))
    }
}
