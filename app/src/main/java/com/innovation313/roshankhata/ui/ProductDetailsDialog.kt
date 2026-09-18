package com.innovation313.roshankhata.ui

import android.app.Activity
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import kotlinx.coroutines.launch

/**
 * The one form that edits a product.
 *
 * It lived inside ProductsActivity until the bill screen needed it too: an
 * owner writing a supplier bill has the label in their hand at that exact
 * moment, which is the only moment the company and the registration number
 * are easy to answer. Two screens asking the same four questions with two
 * separate forms is how they end up disagreeing — one gaining a field, the
 * other keeping an old hint — so there is one form and both screens open it.
 *
 * The compliance fields are the reason this exists: an inspector reads the
 * registration number and the active ingredient off the bottle, and the
 * ledger had nowhere to keep either. Everything below the name is optional
 * and stays optional. A feed or seed shop must never be made to answer
 * pesticide questions in order to save a product.
 */
object ProductDetailsDialog {

    /**
     * Is this product missing anything an inspection asks for?
     *
     * Deliberately the SAME four fields, in the same order, as
     * InspectorReport's own incomplete-products rule. A second definition of
     * "incomplete" would let this prompt nag about a product the register
     * calls complete, or stay quiet about one it flags — and the owner would
     * rightly stop believing both.
     */
    fun needsLabelDetails(p: Product): Boolean =
        p.company.isNullOrBlank() ||
            p.registrationNumber.isNullOrBlank() ||
            p.technicalName.isNullOrBlank() ||
            p.formulation.isNullOrBlank()

    /**
     * @param onSaved run on the main thread after a successful save, so the
     *   calling screen can refresh its own figures — or open the next product
     *   in a queue, which is what the bill screen does.
     */
    fun show(
        activity: Activity,
        product: Product,
        dao: KhataDao,
        onSaved: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_edit_product, null)
        fun field(id: Int) = view.findViewById<EditText>(id)

        val etName = field(R.id.etProductName).apply { setText(product.name) }
        val etCompany = field(R.id.etProductCompany).apply { setText(product.company) }
        val etCategory = field(R.id.etProductCategory).apply { setText(product.category) }
        val etUnit = field(R.id.etProductUnit).apply { setText(product.defaultUnit) }
        val etType = field(R.id.etProductType).apply { setText(product.productType) }
        val etTechnical = field(R.id.etTechnicalName).apply { setText(product.technicalName) }
        val etFormulation = field(R.id.etFormulation).apply { setText(product.formulation) }
        val etRegistration =
            field(R.id.etRegistrationNumber).apply { setText(product.registrationNumber) }

        // Shown trimmed rather than as a raw double: a rate of 1850 should
        // read "1850", not "1850.0", and the owner should get back exactly
        // what they typed.
        val etSalePrice = field(R.id.etSalePrice).apply {
            setText(product.salePrice?.let { Format.plain(it) } ?: "")
        }
        val etCreditPrice = field(R.id.etCreditPrice).apply {
            setText(product.creditPrice?.let { Format.plain(it) } ?: "")
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.product_edit_details)
            .setView(view)
            // Cancel still advances a queue — an owner who skips one product
            // should reach the next, not be dropped out of the run.
            .setNegativeButton(R.string.cancel) { _, _ -> onDismissed() }
            .setOnCancelListener { onDismissed() }
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(activity, R.string.enter_name, Toast.LENGTH_SHORT).show()
                    // The dialog is already closing; treat an empty name as a
                    // skip rather than silently saving a nameless product.
                    onDismissed()
                    return@setPositiveButton
                }

                fun typed(e: EditText) = e.text.toString().trim().ifEmpty { null }

                val updated = product.copy(
                    name = newName,
                    nameKey = ProductName.key(newName),
                    normalisedName = ProductName.normalised(newName),
                    company = typed(etCompany),
                    category = typed(etCategory),
                    defaultUnit = typed(etUnit),
                    productType = typed(etType),
                    technicalName = typed(etTechnical),
                    formulation = typed(etFormulation),
                    registrationNumber = typed(etRegistration),
                    // A cleared box means "I do not quote a rate for this",
                    // which is a null and not a zero. Zero is a price.
                    salePrice = typed(etSalePrice)?.toDoubleOrNull(),
                    creditPrice = typed(etCreditPrice)?.toDoubleOrNull()
                )

                // AppScope for the write itself: leaving the screen right
                // after Save must not cancel it. The clash check runs in the
                // same block so the read and the write cannot be separated by
                // the owner walking away.
                AppScope.launch {
                    // Only a rename can collide; keeping the same name finds
                    // this very product and is no clash at all. nameKey is a
                    // UNIQUE index, so without this check the write would be
                    // refused inside a coroutine with the owner believing it
                    // saved.
                    val clash = dao.productByKey(updated.nameKey)
                    if (clash != null && clash.id != product.id) {
                        activity.runOnUiThread {
                            if (!activity.isFinishing) {
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.product_name_taken, clash.name),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            onDismissed()
                        }
                        return@launch
                    }

                    dao.updateProduct(updated)
                    activity.runOnUiThread { if (!activity.isFinishing) onSaved() }
                }
            }
            .show()
    }
}
