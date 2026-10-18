package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R

/**
 * Why a file was refused, in the app's language, for the dialog that says so.
 * [BackupCodec.ImportResult.Failed] keeps the kind; the words are the phone's
 * string resources. [detail] is the exception's own text, kept for diagnosis.
 */
fun BackupCodec.ImportResult.Failed.reason(context: Context): String {
    val text = when (why) {
        BackupCodec.ImportResult.Why.UNREADABLE -> context.getString(R.string.backup_err_unreadable)
        BackupCodec.ImportResult.Why.NOT_OURS -> context.getString(R.string.backup_err_not_ours)
        BackupCodec.ImportResult.Why.TOO_NEW -> context.getString(R.string.backup_err_too_new)
        BackupCodec.ImportResult.Why.INCOMPLETE -> Digits.string(context.resources, R.string.backup_err_incomplete, count)
    }
    return if (detail.isNullOrBlank()) text else "$text\n($detail)"
}
