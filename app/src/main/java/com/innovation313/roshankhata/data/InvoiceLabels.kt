package com.innovation313.roshankhata.data

// Invoice's discount wording: it formats with the app's Format, so it stays in
// the app while Invoice itself moved to the shared module (KMP Phase 3, 18 Oct).

/**
 * How the discount line reads: "Discount (5%)" for a percent, plain
 * "Discount" for an amount in money, whose figure is printed beside it.
 */
fun Invoice.discountLabel(word: String): String =
    if (discountAmount != null) word else "$word (${com.innovation313.roshankhata.ui.Format.plain(discountPercent ?: 0.0)}%)"
