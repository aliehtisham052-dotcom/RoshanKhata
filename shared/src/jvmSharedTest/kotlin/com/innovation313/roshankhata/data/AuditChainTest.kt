package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The audit log's seal (schema 36): an edited or removed row is found. */
class AuditChainTest {

    private fun chain(rows: List<AuditLog>): List<AuditLog> {
        var prev: String? = null
        return rows.map { r -> r.copy(hash = AuditChain.hashOf(prev, r)).also { prev = it.hash } }
    }

    private val rows = chain(listOf(
        AuditLog(id = 1, timestamp = 100, user = null, action = AuditAction.SALE_POSTED, refTable = "sales", refId = 1, detail = "S-000001 500.00"),
        AuditLog(id = 2, timestamp = 200, user = "Imran", action = AuditAction.SALE_VOIDED, refTable = "sales", refId = 1, detail = "S-000001 wrong"),
        AuditLog(id = 3, timestamp = 300, user = "Asad", action = AuditAction.MANAGER_OVERRIDE, detail = "void")
    ))

    @Test
    fun anUntouchedLogHolds() {
        val r = AuditChain.verify(rows)
        assertTrue(r.intact); assertEquals(3, r.sealed); assertEquals(0, r.beforeSeal)
    }

    @Test
    fun anEditedDetailOrARemovedRowBreaksTheSealThere() {
        val edited = rows.toMutableList().also { it[1] = it[1].copy(detail = "S-000001 customer asked") }
        assertEquals(2L, AuditChain.verify(edited).brokenAt)
        val removed = rows.filter { it.id != 2L }
        assertEquals(3L, AuditChain.verify(removed).brokenAt)
        val who = rows.toMutableList().also { it[0] = it[0].copy(user = "Asad") }
        assertEquals(1L, AuditChain.verify(who).brokenAt)
    }

    @Test
    fun rowsFromBeforeTheSealMayOnlyLeadTheLog() {
        val old = listOf(AuditLog(id = 1, timestamp = 50, action = AuditAction.SIGN_IN), AuditLog(id = 2, timestamp = 60, action = AuditAction.RESTORE))
        var prev: String? = null
        val newer = listOf(AuditLog(id = 3, timestamp = 70, action = AuditAction.SALE_POSTED), AuditLog(id = 4, timestamp = 80, action = AuditAction.SALE_HELD))
            .map { r -> r.copy(hash = AuditChain.hashOf(prev, r)).also { prev = it.hash } }
        val r = AuditChain.verify(old + newer)
        assertTrue(r.intact); assertEquals(2, r.beforeSeal); assertEquals(2, r.sealed)
        val gap = newer + AuditLog(id = 5, timestamp = 90, action = AuditAction.EXPORT)
        assertEquals(5L, AuditChain.verify(gap).brokenAt)
        assertNull(AuditChain.verify(emptyList()).brokenAt)
    }
}
