package com.innovation313.roshankhata.data

import android.content.res.Resources

// The Android half of Digits (KMP Phase 2, 18 Oct): Resources exist only on
// Android, so these two live here as extensions and read exactly as before,
// Digits.quantity(res, ...). Files outside the data package import them.

/**
 * A counted string ("%d customers") with its figure in 0-9.
 *
 * Resources.getQuantityString formats with the resources' own locale,
 * and on Bengali, Sindhi and Persian that locale writes its own digits:
 * the owner's Bengali header read "১২০৫ জন গ্রাহক" over amounts that read
 * "Rs 4,598" — two numbering systems a thumb apart. The quantity still
 * picks the plural form; only the formatting is done here, with
 * [FIGURES], so a count reads in the same digits as the money beside it.
 * Takes the same arguments as getQuantityString(id, quantity, args...).
 */
fun Digits.quantity(res: Resources, id: Int, quantity: Int, vararg args: Any): String =
    res.getQuantityText(id, quantity).toString().format(FIGURES, *args)

/** [quantity]'s twin for a plain string with a figure in it. */
fun Digits.string(res: Resources, id: Int, vararg args: Any): String =
    res.getText(id).toString().format(FIGURES, *args)
