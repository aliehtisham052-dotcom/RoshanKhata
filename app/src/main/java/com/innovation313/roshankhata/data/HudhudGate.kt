package com.innovation313.roshankhata.data

/**
 * The one door Hudhud's files ask "who may see this?" through (14 Nov 2026, the owner: "access poori deni chahiye… jo
 * professional tareeqa hai wohi use karo" — a cashier signed in under counter mode sees only what his own screens
 * show; the owner, and a phone with counter mode off, see everything).
 *
 * Why one door: `scripts/gen_hudhud_catalog.py` holds every fact Hudhud tells to the gate of the screen that shows
 * the same fact (`shared/hudhud/catalog/decisions.tsv`), so a gate must be one the catalog knows by name. A Hudhud
 * file that asked [Access] directly would be a gate nobody checked; `check_gates` in that script fails the build for it.
 *
 * Each function is named after the permission it reads (the script checks that too):
 *  - [reports]  the owner or a manager: every figure of money, the day's takings, purchase rates and costs.
 *  - [staff]    the owner only: the staff, their salaries and what was paid them (Staff is the owner's screen).
 *  - [settings] the owner only: Business Profile's private part — the shop's country, email, tax number, bank account,
 *               IBAN and wallet. (The shop's name, address, phone, currency and trade are printed on what a cashier hands
 *               out or read on every amount he sees, so they are not behind it; no screen of a cashier names the
 *               country, so it is.) What each of these is held to is in `HudhudFacts.shop`'s own note.
 */
internal object HudhudGate {
    fun reports() = Access.can(Access.Permission.REPORTS)

    fun staff() = Access.can(Access.Permission.STAFF)

    fun settings() = Access.can(Access.Permission.SETTINGS)
}
