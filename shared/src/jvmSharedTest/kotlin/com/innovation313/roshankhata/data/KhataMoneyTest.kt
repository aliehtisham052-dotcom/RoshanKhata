package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule both entry forms save a khata entry's way of paying by, and the drawer counts by (17 Nov 2026, T43,
 * [KhataMoney]). Each guard is held alone (lesson 129): KhataMoneyBookTest holds the DAO's SQL to [KhataMoney.countsInDrawer].
 */
class KhataMoneyTest {

    @Test
    fun `goods carry no way of paying, whoever and whichever way`() {
        for (given in listOf(true, false)) for (customer in listOf(true, false)) for (money in listOf(true, false))
            assertFalse("$given $customer $money", KhataMoney.carriesMethod(given, customer, hasGoods = true, supplierMoney = money))
    }

    @Test
    fun `money paid to a supplier and money from a customer carry one`() {
        assertTrue("M40: paid to a supplier", KhataMoney.carriesMethod(isGiven = true, isCustomer = false, hasGoods = false, supplierMoney = false))
        assertTrue("received from a customer", KhataMoney.carriesMethod(isGiven = false, isCustomer = true, hasGoods = false, supplierMoney = false))
        assertFalse("a customer's sale is not decided here", KhataMoney.carriesMethod(isGiven = true, isCustomer = true, hasGoods = false, supplierMoney = true))
    }

    @Test
    fun `a supplier's I got carries one only when it was money`() {
        assertFalse("C2: goods on credit", KhataMoney.carriesMethod(isGiven = false, isCustomer = false, hasGoods = false, supplierMoney = false))
        assertTrue("T43: money handed back", KhataMoney.carriesMethod(isGiven = false, isCustomer = false, hasGoods = false, supplierMoney = true))
    }

    @Test
    fun `only a supplier's I got of money with no goods is written as money`() {
        assertTrue(KhataMoney.isSupplierMoney(isGiven = false, isCustomer = false, hasGoods = false, supplierMoney = true))
        assertFalse("goods chosen", KhataMoney.isSupplierMoney(isGiven = false, isCustomer = false, hasGoods = false, supplierMoney = false))
        assertFalse("goods on the entry", KhataMoney.isSupplierMoney(isGiven = false, isCustomer = false, hasGoods = true, supplierMoney = true))
        assertFalse("a customer's", KhataMoney.isSupplierMoney(isGiven = false, isCustomer = true, hasGoods = false, supplierMoney = true))
        assertFalse("an I gave", KhataMoney.isSupplierMoney(isGiven = true, isCustomer = false, hasGoods = false, supplierMoney = true))
    }

    @Test
    fun `the drawer counts cash out, a customer's cash and a supplier's cash money, nothing else`() {
        val c = KhataMoney.CASH
        assertTrue("cash paid out to anyone", KhataMoney.countsInDrawer(isGiven = true, isCustomer = false, method = c, kind = "NORMAL"))
        assertTrue("cash paid out to a customer", KhataMoney.countsInDrawer(isGiven = true, isCustomer = true, method = c, kind = "NORMAL"))
        assertTrue("a customer's cash", KhataMoney.countsInDrawer(isGiven = false, isCustomer = true, method = c, kind = "NORMAL"))
        assertTrue("a supplier's cash handed back", KhataMoney.countsInDrawer(isGiven = false, isCustomer = false, method = c, kind = KhataMoney.MONEY))
        assertFalse("the old forms' pre-ticked Cash on goods", KhataMoney.countsInDrawer(isGiven = false, isCustomer = false, method = c, kind = "NORMAL"))
        assertFalse("an opening balance owed to a supplier", KhataMoney.countsInDrawer(isGiven = false, isCustomer = false, method = c, kind = "OPENING"))
        assertFalse("money handed back by bank", KhataMoney.countsInDrawer(isGiven = false, isCustomer = false, method = "bank", kind = KhataMoney.MONEY))
        assertFalse("no way said", KhataMoney.countsInDrawer(isGiven = false, isCustomer = true, method = null, kind = "NORMAL"))
        assertFalse("no way said, paid out", KhataMoney.countsInDrawer(isGiven = true, isCustomer = false, method = null, kind = "NORMAL"))
    }

    @Test
    fun `the stored words never change`() {
        // A pushed word is frozen like a schema version (lesson 20): rows carry it in books, backups and copies.
        assertEquals("MONEY", KhataMoney.MONEY)
        assertEquals("cash", KhataMoney.CASH)
    }
}
