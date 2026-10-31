package com.innovation313.roshankhata.data

/**
 * How the stock register and the sales & purchase register speak for the shop's trade (31 Oct 2026, the
 * owner: "stock aur sale register bhi tamam shops ke liye hona chahiye… jo bhi business hai uske
 * according"). Both registers are every shop's; only their words and the label columns differ:
 *
 * - [AGRI]: the agriculture department's inspection — "Stock & Compliance Register", the pesticide label's
 *   technical name, formulation and registration number, the label checklist, dealer's and inspector's
 *   signatures.
 * - [MEDICAL]: the drug inspector's — the same sheet with the medicine's generic name.
 * - [PLAIN]: any other shop — "Stock Register" with no label columns or checklist (no inspector reads
 *   them), signed by the owner and whoever checks the count.
 *
 * Decided by the stored trade key (the phone's Trade.name, the computer's TradeKit.key): one rule, so the
 * picker, the product form, the screens and the printed sheets never disagree about the shop.
 */
enum class RegisterStyle {
    AGRI, MEDICAL, PLAIN;

    /** Whether the inspector's label details (columns, checklist, signatures) belong on the sheet. */
    val compliance: Boolean get() = this != PLAIN

    companion object {
        fun of(tradeKey: String?): RegisterStyle = when (tradeKey?.trim()?.uppercase()) {
            "AGRI" -> AGRI
            "MEDICAL" -> MEDICAL
            else -> PLAIN
        }
    }
}
