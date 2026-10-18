package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R

// PaymentMethod's labels: the app's own strings, so they stay with the app
// while the table itself moved to the shared module (KMP Phase 3, 18 Oct).
// Called as before: PaymentMethod.labelOf(context, key).

fun PaymentMethod.labelRes(key: String): Int? = when (key) {
    CASH -> R.string.payment_method_cash
    BANK -> R.string.payment_method_bank
    CHEQUE -> R.string.payment_method_cheque
    ONLINE -> R.string.payment_method_online
    else -> null
}

fun PaymentMethod.labelOf(context: Context, key: String?): String? {
    if (key.isNullOrBlank()) return null
    val res = labelRes(key) ?: return key
    return context.getString(res)
}
