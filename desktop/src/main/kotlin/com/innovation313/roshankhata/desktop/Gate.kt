package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The computer's way to ask for a manager's or the owner's PIN (21 Oct 2026,
 * the critical review): any click handler calls [need] — or [edit] for a
 * change to what is already written — and what it guards runs at once when
 * the signed-in user may, or after the PIN when not. [GateHost] shows the
 * PIN window; with no book open nothing is allowed that is not already.
 */
object Gates {
    internal var ask: ((Access.Permission, (String?) -> Unit) -> Unit)? = null

    fun need(p: Access.Permission, then: () -> Unit) = needBy(p) { then() }

    /**
     * As [need], handing [then] who gave their PIN (null when the signed-in user's own right was
     * enough): the DAO's gated writes take it as `allowedBy` (23 Oct 2026, the review).
     */
    fun needBy(p: Access.Permission, then: (String?) -> Unit) {
        if (Access.can(p)) then(null) else ask?.invoke(p, then)
    }

    /** A change to what is already written: an edit, a delete, the bin. */
    fun edit(then: () -> Unit) = need(Access.Permission.EDIT_BOOK, then)
}

/** Hosts [Gates] for [content]: the PIN window for whatever was asked. */
@Composable
fun GateHost(dao: KhataDao, content: @Composable () -> Unit) {
    var pending by remember { mutableStateOf<Pair<Access.Permission, (String?) -> Unit>?>(null) }
    DisposableEffect(dao) {
        Gates.ask = { p, then -> pending = p to then }
        onDispose { Gates.ask = null }
    }
    content()
    pending?.let { (p, then) ->
        OverrideDialog(dao, whatWord(p), ownerOnly = Access.ownerOnly(p), onClose = { pending = null }) { by -> then(by) }
    }
}

/** What a permission is, in words, for the PIN window. */
@Composable
fun whatWord(p: Access.Permission): String = word(
    when (p) {
        Access.Permission.BACKUP -> W.access_need_backup
        Access.Permission.SETTINGS -> W.access_need_settings
        Access.Permission.STAFF -> W.access_need_staff
        Access.Permission.REPORTS -> W.access_need_reports
        Access.Permission.EXPORT -> W.audit_export
        else -> W.access_need_edit
    }
)

/** What a page needs before a cashier may open it alone; null for the pages anyone signed in may use. */
fun Section.need(): Access.Permission? = when (this) {
    Section.STAFF -> Access.Permission.STAFF
    Section.SHOP -> Access.Permission.SETTINGS
    Section.DAILY -> Access.Permission.BACKUP
    Section.DUPLICATES -> Access.Permission.EDIT_BOOK
    // The register too (25 Oct 2026, the audit): every sale and purchase with its money, as the Khata Report.
    Section.SALES, Section.INSIGHTS, Section.CASHFLOW, Section.ZAKAT, Section.REPORT, Section.REGISTER,
    Section.BUY_RATES, Section.AUDIT, Section.AGING -> Access.Permission.REPORTS
    else -> null
}

/**
 * In place of a page the signed-in user may not open alone: what it needs,
 * and the button that asks a manager or the owner.
 */
@Composable
fun LockedPage(p: Access.Permission, onAllowed: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        EmptyState("ic_tile_settings", word(W.access_page_locked), whatWord(p)) {
            FluentButton(word(if (Access.ownerOnly(p)) W.access_owner_needed_title else W.access_override_title), { Gates.need(p, onAllowed) }, kind = ButtonKind.ACCENT)
        }
    }
}
