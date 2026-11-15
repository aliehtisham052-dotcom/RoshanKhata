package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.BackupActivity
import com.innovation313.roshankhata.CashbookActivity
import com.innovation313.roshankhata.DuplicateCustomersActivity
import com.innovation313.roshankhata.InsightsActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.StaffActivity
import com.innovation313.roshankhata.WarisActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The limits line of a help page (15 Nov 2026) is read from the screen's own gate (AccessUi.needs) and from who the
 * book keeps for the owner alone (Access.ownerOnly): the owner's things say the owner, the rest the owner or a manager,
 * a screen anyone may open says nothing, and a page that only says where a screen is takes that screen's gate.
 */
class HudhudPageLimitTest {

    @Test fun `the owner's own screens say the owner`() {
        assertEquals(R.string.hudhud_limit_owner, HudhudGate.pageLimit(BackupActivity::class.java))
        assertEquals(R.string.hudhud_limit_owner, HudhudGate.pageLimit(StaffActivity::class.java))
        assertEquals(R.string.hudhud_limit_owner, HudhudGate.pageLimit(WarisActivity::class.java))
    }

    @Test fun `reports and edits say the owner or a manager`() {
        assertEquals(R.string.hudhud_limit_manager, HudhudGate.pageLimit(InsightsActivity::class.java))
        assertEquals(R.string.hudhud_limit_manager, HudhudGate.pageLimit(DuplicateCustomersActivity::class.java))
    }

    @Test fun `a screen anyone may open says nothing`() {
        assertNull(HudhudGate.pageLimit(CashbookActivity::class.java))
        assertNull(HudhudGate.pageLimit(null))
    }
}
