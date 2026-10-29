package com.innovation313.roshankhata.data

import android.content.Context

// PaymentMethod's labels on the phone, as the call has always read:
// PaymentMethod.labelOf(context, key). The table itself is the shared
// PaymentMethod.label (29 Oct 2026: one list of methods for both platforms,
// read through the phone's own resources by AndroidWords), so a method added
// there — WALLET — is labelled here without a second list to forget.

fun PaymentMethod.labelOf(context: Context, key: String?): String? {
    if (key.isNullOrBlank()) return null
    return label(AndroidWords(context), key)
}

/** The method with its reference, "Bank transfer · Ref: TID 778…" — the shared PaymentMethod.said, on the phone. */
fun PaymentMethod.saidOn(context: Context, method: String?, ref: String?): String? =
    said(AndroidWords(context), method, ref)
