package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * A computer's book whose units were saved as labels ("بوری", "bori") is
 * repaired to the keys when it opens, as the phone's is (23 Oct review, R02).
 */
class UnitRepairDesktopTest {
    @Test
    fun `labels saved on the computer become keys on the next open`() = runBlocking {
        val dir = Files.createTempDirectory("rk-unit-repair").toFile()
        try {
            val file = dir.resolve("khata.db")
            var db = DesktopDatabase.open(file)
            val urea = db.khataDao().insertProduct(
                Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"), defaultUnit = "بوری")
            )
            val tin = db.khataDao().insertProduct(
                Product(name = "Ghee", nameKey = ProductName.key("Ghee"), normalisedName = ProductName.normalised("Ghee"), defaultUnit = "tin")
            )
            db.close()
            db = DesktopDatabase.open(file)
            assertEquals("bag", db.khataDao().productById(urea)?.defaultUnit)
            // The owner's own word is left as typed.
            assertEquals("tin", db.khataDao().productById(tin)?.defaultUnit)
            db.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
