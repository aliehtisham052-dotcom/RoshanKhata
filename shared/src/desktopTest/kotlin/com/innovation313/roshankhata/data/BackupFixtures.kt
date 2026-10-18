package com.innovation313.roshankhata.data

/** A phone's backup (format 7) for the computer's tests. */
object BackupFixtures {
    val v7 = """
        {
          "format": "RoshanKhata",
          "version": 7,
          "exportedAt": 1760770000000,
          "businessName": "Bhatti Traders",
          "cheques": [],
          "cashbook": [{"id": 1, "amount": 500.0, "isIncome": true, "category": "Sale", "note": null, "timestamp": 1760770000000}],
          "businessProfile": {
            "businessName": "Bhatti Traders", "businessNameUrdu": null, "businessAddress": "Lappay Wali",
            "businessPhone": null, "businessEmail": null, "bankName": null, "bankAccountTitle": null,
            "bankIban": null, "bankJazzCash": null, "termsAndConditions": null, "strn": null,
            "currency": "Rs", "calendar": "hijri", "trade": null, "tradeOtherName": null,
            "tradeOtherItems": null, "photoOnStatement": true
          },
          "parties": [
            {"id": 7, "name": "Ali (علی)", "phone": "0300 1234567", "fatherName": null, "village": null,
             "isCustomer": true, "photoPath": null, "creditLimit": null, "createdAt": 1760000000000,
             "isDeleted": false, "deletedAt": null}
          ],
          "entries": [
            {"id": 11, "partyId": 7, "amount": 4598, "isGiven": true, "note": null, "entryNumber": "E-1",
             "timestamp": 1760700000000, "isQarzeHasna": false, "recovery": 0, "itemName": null,
             "quantity": null, "unit": null, "productId": null, "billItemId": null, "createdBy": null,
             "paymentMethod": null, "rateType": null, "pairedEntryId": null, "season": null,
             "isDeleted": false, "deletedAt": null}
          ],
          "entryItems": [
            {"id": 21, "entryId": 11, "lineNo": 0, "itemName": "Urea", "quantity": 2.5, "unit": "bag",
             "rate": 1839.2, "isBonus": false, "productId": null, "billItemId": null}
          ]
        }
    """.trimIndent()
}
