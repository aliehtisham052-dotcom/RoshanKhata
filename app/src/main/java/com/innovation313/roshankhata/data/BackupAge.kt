package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R

/**
 * The one line that says how old the last backup is — "Backed up 10:42",
 * "Backed up yesterday", "Backed up 3 days ago", "Never backed up".
 *
 * It was built inside KhataActivity.paintSummary; the Home header shows the
 * same line now (7 Oct), and two copies of this wording would drift apart.
 *
 * TODAY SHOWS THE CLOCK TIME, not the word "today". Between a morning backup
 * and an evening one, "today" does not settle the question being asked — is
 * what I wrote an hour ago safe? A time answers that. Older than today the
 * reverse holds; nobody needs the minute of last week's backup.
 *
 * [stale] is true at a week or never: the line turns gold and bold so a
 * warning does not look like every other day's quiet note.
 */
object BackupAge {
    data class Line(val text: String, val stale: Boolean, val at: Long)

    fun line(context: Context): Line {
        val last = BackupReminder.lastBackupAt(context)
        val ageDays = if (last == 0L) -1L
        else (System.currentTimeMillis() - last) / (24L * 60 * 60 * 1000)
        val text = when {
            ageDays < 0 -> context.getString(R.string.summary_backup_never)
            ageDays == 0L -> context.getString(
                R.string.summary_backup_time,
                // The phone's own 12/24-hour pattern, with 0-9 digits.
                android.text.format.DateFormat.getTimeFormat(context).let { f ->
                    (f as? java.text.SimpleDateFormat)
                        ?.let { java.text.SimpleDateFormat(it.toPattern(), Digits.latinIn()) } ?: f
                }.format(java.util.Date(last))
            )
            ageDays == 1L -> context.getString(R.string.summary_backup_yesterday)
            else -> Digits.quantity(context.resources, 
                R.plurals.summary_backup_days, ageDays.toInt(), ageDays.toInt()
            )
        }
        return Line(text, stale = ageDays < 0 || ageDays >= 7, at = last)
    }
}
