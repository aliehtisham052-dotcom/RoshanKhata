package com.innovation313.roshankhata.data

/**
 * Backups in the exact shapes formats 1 to 6 were written in (25 Nov 2026, plan item B2). Each was written by hand from
 * that format's own EXPORT code in the repository's history, app/src/main/java/com/innovation313/roshankhata/data/Backup.kt
 * at the commit named on it: the same top-level keys in the same order, the same fields on every row and nothing the
 * writer of that day did not write. Numbers are written as Android's JSONObject writes a whole double (4500, not 4500.0).
 *
 * One made-up shop in every file, so one set of expectations holds them all ([OldBackupShapesTest]):
 *  - parties 1 Ali Raza and 2 Sana Bibi (customers), 3 Noor Mills (a supplier), 4 Old Customer (in the bin);
 *  - entries 1 (Ali, I gave 4,500, two bags of Urea on the entry itself), 2 (Ali, I got 1,500), 3 (Sana, a Qarz-e-Hasna
 *    loan of 2,000), 4 (Sana, I got 500), 5 (Noor, I got 12,000: ten bags of DAP), 6 (Noor, I gave 7,000), 7 (Ali, 300,
 *    in the bin) and 8 (Old Customer, 800, in the bin) — live balances Ali 3,000, Sana 1,500, Noor -5,000, Old Customer 0;
 *  - one cheque, three cashbook rows (one in the bin); from format 2 a plan with one instalment (paid by entry 2); from 3
 *    a supplier bill with one batch line (its entry is 5); from 4 two products; from 5 an invoice and the profile; from 6
 *    a "not a duplicate" decision.
 * Invented names and numbers only (lesson 80).
 */
object OldBackupShapes {

    /**
     * Format 1. The first backup: parties, entries (one item name, quantity and unit on the entry itself), cheques and
     * the cashbook; pretty-printed with toString(2). In use for minutes before format 2. From Backup.kt at b649cf90
     * (2026-07-14, Feature: full backup & restore).
     */
    val format1 = """
{
  "format": "RoshanKhata",
  "version": 1,
  "exportedAt": 1784235600000,
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ]
}
"""

    /**
     * Format 2. Format 1 plus payment plans and their instalments (format 2 came with c9ccb9be, "Feature: payment
     * plans"). From Backup.kt at 567a234f (2026-07-14, the last commit before 730a4cb2 raised it to 3).
     */
    val format2 = """
{
  "format": "RoshanKhata",
  "version": 2,
  "exportedAt": 1784235600000,
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ],
  "plans": [
    {
      "id": 1,
      "partyId": 1,
      "totalAmount": 3000,
      "installmentAmount": 1000,
      "note": "Three months",
      "nextDueDate": 1785099600000,
      "createdAt": 1784037600000,
      "isClosed": false,
      "closedAt": null,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "installments": [
    {
      "id": 1,
      "planId": 1,
      "amount": 1500,
      "ledgerEntryId": 2,
      "paidAt": 1784041200000,
      "note": null,
      "isDeleted": false
    }
  ]
}
"""

    /**
     * Format 3. Format 2 plus supplier bills and their batch lines (format 3 came with 730a4cb2, "Feature: supplier
     * bills with batch numbers and expiry tracking"). The same writer from 730a4cb2 to 3adf13d9. From Backup.kt at
     * 3adf13d9 (2026-07-28, the last commit before 83634489 raised it to 4).
     */
    val format3 = """
{
  "format": "RoshanKhata",
  "version": 3,
  "exportedAt": 1784235600000,
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ],
  "plans": [
    {
      "id": 1,
      "partyId": 1,
      "totalAmount": 3000,
      "installmentAmount": 1000,
      "note": "Three months",
      "nextDueDate": 1785099600000,
      "createdAt": 1784037600000,
      "isClosed": false,
      "closedAt": null,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "installments": [
    {
      "id": 1,
      "planId": 1,
      "amount": 1500,
      "ledgerEntryId": 2,
      "paidAt": 1784041200000,
      "note": null,
      "isDeleted": false
    }
  ],
  "bills": [
    {
      "id": 1,
      "partyId": 3,
      "billNumber": "B-101",
      "totalAmount": 12000,
      "billDate": 1784052000000,
      "dueDate": 1785459600000,
      "ledgerEntryId": 5,
      "isPaidInFull": false,
      "note": null,
      "createdAt": 1784052000000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "billItems": [
    {
      "id": 1,
      "billId": 1,
      "productName": "DAP",
      "batchNumber": "LOT-7",
      "expiryDate": 1814313600000,
      "quantity": 10,
      "unit": "bag",
      "rate": 1200,
      "note": null,
      "isDeleted": false
    }
  ]
}
"""

    /**
     * Format 4. Format 3 plus products, an entry's productId and billItemId and a bill line's productId (format 4 came
     * with 83634489; 6a2ff7f8 added the entry's billItemId under the same number, so the first format-4 files lack it,
     * as formats 1-3 do). From Backup.kt at c69cd310 (2026-07-30, the last commit before 54319ba4 raised it to 5).
     */
    val format4 = """
{
  "format": "RoshanKhata",
  "version": 4,
  "exportedAt": 1784235600000,
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "productId": 1,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "productId": 2,
      "billItemId": 1,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ],
  "plans": [
    {
      "id": 1,
      "partyId": 1,
      "totalAmount": 3000,
      "installmentAmount": 1000,
      "note": "Three months",
      "nextDueDate": 1785099600000,
      "createdAt": 1784037600000,
      "isClosed": false,
      "closedAt": null,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "installments": [
    {
      "id": 1,
      "planId": 1,
      "amount": 1500,
      "ledgerEntryId": 2,
      "paidAt": 1784041200000,
      "note": null,
      "isDeleted": false
    }
  ],
  "bills": [
    {
      "id": 1,
      "partyId": 3,
      "billNumber": "B-101",
      "totalAmount": 12000,
      "billDate": 1784052000000,
      "dueDate": 1785459600000,
      "ledgerEntryId": 5,
      "isPaidInFull": false,
      "note": null,
      "createdAt": 1784052000000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "billItems": [
    {
      "id": 1,
      "billId": 1,
      "productName": "DAP",
      "batchNumber": "LOT-7",
      "expiryDate": 1814313600000,
      "quantity": 10,
      "unit": "bag",
      "rate": 1200,
      "note": null,
      "productId": 2,
      "isDeleted": false
    }
  ],
  "products": [
    {
      "id": 1,
      "name": "Urea",
      "nameKey": "urea",
      "normalisedName": "urea",
      "category": null,
      "defaultUnit": "bag",
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "DAP",
      "nameKey": "dap",
      "normalisedName": "dap",
      "category": null,
      "defaultUnit": "bag",
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    }
  ]
}
"""

    /**
     * Format 5. Format 4 plus invoices, invoice lines and the Business Profile's nine text fields (format 5 came with
     * 54319ba4). From Backup.kt at 870b8e55 (2026-07-30, the last commit before 75c0a4fc raised it to 6).
     */
    val format5 = """
{
  "format": "RoshanKhata",
  "version": 5,
  "exportedAt": 1784235600000,
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "productId": 1,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "productId": 2,
      "billItemId": 1,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ],
  "plans": [
    {
      "id": 1,
      "partyId": 1,
      "totalAmount": 3000,
      "installmentAmount": 1000,
      "note": "Three months",
      "nextDueDate": 1785099600000,
      "createdAt": 1784037600000,
      "isClosed": false,
      "closedAt": null,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "installments": [
    {
      "id": 1,
      "planId": 1,
      "amount": 1500,
      "ledgerEntryId": 2,
      "paidAt": 1784041200000,
      "note": null,
      "isDeleted": false
    }
  ],
  "bills": [
    {
      "id": 1,
      "partyId": 3,
      "billNumber": "B-101",
      "totalAmount": 12000,
      "billDate": 1784052000000,
      "dueDate": 1785459600000,
      "ledgerEntryId": 5,
      "isPaidInFull": false,
      "note": null,
      "createdAt": 1784052000000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "billItems": [
    {
      "id": 1,
      "billId": 1,
      "productName": "DAP",
      "batchNumber": "LOT-7",
      "expiryDate": 1814313600000,
      "quantity": 10,
      "unit": "bag",
      "rate": 1200,
      "note": null,
      "productId": 2,
      "isDeleted": false
    }
  ],
  "products": [
    {
      "id": 1,
      "name": "Urea",
      "nameKey": "urea",
      "normalisedName": "urea",
      "category": null,
      "defaultUnit": "bag",
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "DAP",
      "nameKey": "dap",
      "normalisedName": "dap",
      "category": null,
      "defaultUnit": "bag",
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "invoices": [
    {
      "id": 1,
      "invoiceNumber": "INV-0001",
      "customerName": "Ali Raza",
      "customerPhone": "0300 1234567",
      "invoiceDate": 1784037600000,
      "dueDate": null,
      "taxPercent": null,
      "discountPercent": null,
      "additionalChargeLabel": null,
      "additionalChargeAmount": null,
      "receivedAmount": 1500,
      "note": null,
      "templateId": 1,
      "createdAt": 1784037600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "invoiceItems": [
    {
      "id": 1,
      "invoiceId": 1,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "rate": 2250,
      "isDeleted": false
    }
  ],
  "businessProfile": {
    "businessName": "Al-Noor Traders",
    "businessAddress": "Main Bazar",
    "bankName": null,
    "bankAccountTitle": null,
    "bankIban": null,
    "bankJazzCash": null,
    "termsAndConditions": "Goods once sold are not returned.",
    "strn": null,
    "photoOnStatement": true
  }
}
"""

    /**
     * Format 6. Format 5 plus the owner's "not a duplicate" decisions, as format 6 was first written. From Backup.kt
     * at 75c0a4fc (2026-07-30, the commit that raised it to 6).
     */
    val format6First = """
{
  "format": "RoshanKhata",
  "version": 6,
  "exportedAt": 1784235600000,
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "productId": 1,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "productId": 2,
      "billItemId": 1,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ],
  "plans": [
    {
      "id": 1,
      "partyId": 1,
      "totalAmount": 3000,
      "installmentAmount": 1000,
      "note": "Three months",
      "nextDueDate": 1785099600000,
      "createdAt": 1784037600000,
      "isClosed": false,
      "closedAt": null,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "installments": [
    {
      "id": 1,
      "planId": 1,
      "amount": 1500,
      "ledgerEntryId": 2,
      "paidAt": 1784041200000,
      "note": null,
      "isDeleted": false
    }
  ],
  "bills": [
    {
      "id": 1,
      "partyId": 3,
      "billNumber": "B-101",
      "totalAmount": 12000,
      "billDate": 1784052000000,
      "dueDate": 1785459600000,
      "ledgerEntryId": 5,
      "isPaidInFull": false,
      "note": null,
      "createdAt": 1784052000000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "billItems": [
    {
      "id": 1,
      "billId": 1,
      "productName": "DAP",
      "batchNumber": "LOT-7",
      "expiryDate": 1814313600000,
      "quantity": 10,
      "unit": "bag",
      "rate": 1200,
      "note": null,
      "productId": 2,
      "isDeleted": false
    }
  ],
  "products": [
    {
      "id": 1,
      "name": "Urea",
      "nameKey": "urea",
      "normalisedName": "urea",
      "category": null,
      "defaultUnit": "bag",
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "DAP",
      "nameKey": "dap",
      "normalisedName": "dap",
      "category": null,
      "defaultUnit": "bag",
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "invoices": [
    {
      "id": 1,
      "invoiceNumber": "INV-0001",
      "customerName": "Ali Raza",
      "customerPhone": "0300 1234567",
      "invoiceDate": 1784037600000,
      "dueDate": null,
      "taxPercent": null,
      "discountPercent": null,
      "additionalChargeLabel": null,
      "additionalChargeAmount": null,
      "receivedAmount": 1500,
      "note": null,
      "templateId": 1,
      "createdAt": 1784037600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "invoiceItems": [
    {
      "id": 1,
      "invoiceId": 1,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "rate": 2250,
      "isDeleted": false
    }
  ],
  "dismissedDuplicates": [
    {
      "partyIdsKey": "1,2",
      "dismissedAt": 1784199600000
    }
  ],
  "businessProfile": {
    "businessName": "Al-Noor Traders",
    "businessAddress": "Main Bazar",
    "bankName": null,
    "bankAccountTitle": null,
    "bankIban": null,
    "bankJazzCash": null,
    "termsAndConditions": "Goods once sold are not returned.",
    "strn": null,
    "photoOnStatement": true
  }
}
"""

    /**
     * Format 6. Format 6 as it was last written, two months later under the same number: the shop's name at the top
     * (f2d07767), the entry's createdBy (77aea4de), a party's father's name and village and a product's company, type,
     * formulation, registration and technical name (27123acc), an entry's paymentMethod and a product's two prices
     * (268d645c). From Backup.kt at 4da5409a (2026-09-26, the last commit before a20da0e8 raised it to 7).
     */
    val format6Last = """
{
  "format": "RoshanKhata",
  "version": 6,
  "exportedAt": 1784235600000,
  "businessName": "Al-Noor Traders",
  "parties": [
    {
      "id": 1,
      "name": "Ali Raza",
      "phone": "0300 1234567",
      "fatherName": "Karim Bakhsh",
      "village": "Chak 12",
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": 10000,
      "createdAt": 1784019600000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "Sana Bibi",
      "phone": "0300 0000001",
      "fatherName": null,
      "village": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "name": "Noor Mills",
      "phone": null,
      "fatherName": null,
      "village": null,
      "isCustomer": false,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784026800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "name": "Old Customer",
      "phone": null,
      "fatherName": null,
      "village": null,
      "isCustomer": true,
      "photoPath": null,
      "creditLimit": null,
      "createdAt": 1784030400000,
      "isDeleted": true,
      "deletedAt": 1784127600000
    }
  ],
  "entries": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 4500,
      "isGiven": true,
      "note": "Urea 2 bags",
      "entryNumber": "RK-000001",
      "timestamp": 1784037600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "productId": 1,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "partyId": 1,
      "amount": 1500,
      "isGiven": false,
      "note": "Plan payment",
      "entryNumber": "RK-000002",
      "timestamp": 1784041200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": "cash",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "partyId": 2,
      "amount": 2000,
      "isGiven": true,
      "note": "Loan",
      "entryNumber": "RK-000003",
      "timestamp": 1784044800000,
      "isQarzeHasna": true,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 4,
      "partyId": 2,
      "amount": 500,
      "isGiven": false,
      "note": null,
      "entryNumber": "RK-000004",
      "timestamp": 1784048400000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": "cash",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 5,
      "partyId": 3,
      "amount": 12000,
      "isGiven": false,
      "note": "Bill B-101",
      "entryNumber": "RK-000005",
      "timestamp": 1784052000000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": "DAP",
      "quantity": 10,
      "unit": "bag",
      "productId": 2,
      "billItemId": 1,
      "createdBy": null,
      "paymentMethod": null,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 6,
      "partyId": 3,
      "amount": 7000,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000006",
      "timestamp": 1784055600000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": "bank",
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 7,
      "partyId": 1,
      "amount": 300,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000007",
      "timestamp": 1784059200000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    },
    {
      "id": 8,
      "partyId": 4,
      "amount": 800,
      "isGiven": true,
      "note": null,
      "entryNumber": "RK-000008",
      "timestamp": 1784062800000,
      "isQarzeHasna": false,
      "recovery": 0,
      "itemName": null,
      "quantity": null,
      "unit": null,
      "productId": null,
      "billItemId": null,
      "createdBy": null,
      "paymentMethod": null,
      "isDeleted": true,
      "deletedAt": 1784163600000
    }
  ],
  "cheques": [
    {
      "id": 1,
      "partyId": 1,
      "amount": 1000,
      "isReceived": true,
      "chequeNumber": "000123",
      "bankName": "Made-up Bank",
      "dueDate": 1784739600000,
      "status": 0,
      "settledAt": null,
      "ledgerEntryId": null,
      "note": null,
      "createdAt": 1784091600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "cashbook": [
    {
      "id": 1,
      "amount": 500,
      "isIncome": true,
      "category": "Sale",
      "note": null,
      "timestamp": 1784095200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "amount": 200,
      "isIncome": false,
      "category": "Tea",
      "note": "Guests",
      "timestamp": 1784098800000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 3,
      "amount": 50,
      "isIncome": false,
      "category": "Other",
      "note": null,
      "timestamp": 1784102400000,
      "isDeleted": true,
      "deletedAt": 1784106000000
    }
  ],
  "plans": [
    {
      "id": 1,
      "partyId": 1,
      "totalAmount": 3000,
      "installmentAmount": 1000,
      "note": "Three months",
      "nextDueDate": 1785099600000,
      "createdAt": 1784037600000,
      "isClosed": false,
      "closedAt": null,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "installments": [
    {
      "id": 1,
      "planId": 1,
      "amount": 1500,
      "ledgerEntryId": 2,
      "paidAt": 1784041200000,
      "note": null,
      "isDeleted": false
    }
  ],
  "bills": [
    {
      "id": 1,
      "partyId": 3,
      "billNumber": "B-101",
      "totalAmount": 12000,
      "billDate": 1784052000000,
      "dueDate": 1785459600000,
      "ledgerEntryId": 5,
      "isPaidInFull": false,
      "note": null,
      "createdAt": 1784052000000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "billItems": [
    {
      "id": 1,
      "billId": 1,
      "productName": "DAP",
      "batchNumber": "LOT-7",
      "expiryDate": 1814313600000,
      "quantity": 10,
      "unit": "bag",
      "rate": 1200,
      "note": null,
      "productId": 2,
      "isDeleted": false
    }
  ],
  "products": [
    {
      "id": 1,
      "name": "Urea",
      "nameKey": "urea",
      "normalisedName": "urea",
      "category": null,
      "defaultUnit": "bag",
      "company": "Made-up Fertilisers",
      "productType": null,
      "formulation": null,
      "registrationNumber": null,
      "technicalName": null,
      "salePrice": 2250,
      "creditPrice": 2400,
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    },
    {
      "id": 2,
      "name": "DAP",
      "nameKey": "dap",
      "normalisedName": "dap",
      "category": null,
      "defaultUnit": "bag",
      "company": null,
      "productType": null,
      "formulation": null,
      "registrationNumber": null,
      "technicalName": null,
      "salePrice": null,
      "creditPrice": null,
      "note": null,
      "createdAt": 1784023200000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "invoices": [
    {
      "id": 1,
      "invoiceNumber": "INV-0001",
      "customerName": "Ali Raza",
      "customerPhone": "0300 1234567",
      "invoiceDate": 1784037600000,
      "dueDate": null,
      "taxPercent": null,
      "discountPercent": null,
      "additionalChargeLabel": null,
      "additionalChargeAmount": null,
      "receivedAmount": 1500,
      "note": null,
      "templateId": 1,
      "createdAt": 1784037600000,
      "isDeleted": false,
      "deletedAt": null
    }
  ],
  "invoiceItems": [
    {
      "id": 1,
      "invoiceId": 1,
      "itemName": "Urea",
      "quantity": 2,
      "unit": "bag",
      "rate": 2250,
      "isDeleted": false
    }
  ],
  "dismissedDuplicates": [
    {
      "partyIdsKey": "1,2",
      "dismissedAt": 1784199600000
    }
  ],
  "businessProfile": {
    "businessName": "Al-Noor Traders",
    "businessAddress": "Main Bazar",
    "bankName": null,
    "bankAccountTitle": null,
    "bankIban": null,
    "bankJazzCash": null,
    "termsAndConditions": "Goods once sold are not returned.",
    "strn": null,
    "photoOnStatement": true
  }
}
"""

    /** Every shape above with the format number it carries. */
    val all: List<Pair<String, String>> = listOf(
        "format1" to format1,
        "format2" to format2,
        "format3" to format3,
        "format4" to format4,
        "format5" to format5,
        "format6First" to format6First,
        "format6Last" to format6Last
    )
}
