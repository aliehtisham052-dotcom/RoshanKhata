package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.TradeVocab
import com.innovation313.roshankhata.data.UnitWords
import android.app.Activity
import android.widget.EditText
import android.widget.TextView
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import kotlinx.coroutines.launch
import com.innovation313.roshankhata.data.Digits

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
        onDismissed: () -> Unit = {},
        /**
         * What the supplier's bill already says (LabelGuess): put into EMPTY
         * boxes only, never over a value the product already has, and saved
         * only if the owner presses Save.
         */
        guess: com.innovation313.roshankhata.data.LabelGuess.Guess? = null,
        /**
         * Set when this form is one of several in a row (the bill screen's
         * label queue): the title says where the owner is, the buttons read
         * Skip / Save & next, and Back returns to the previous product.
         */
        step: Step? = null
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_edit_product, null)
        fun field(id: Int) = view.findViewById<EditText>(id)

        val etName = field(R.id.etProductName).apply { setText(product.name) }
        val etCompany = field(R.id.etProductCompany).apply { setText(product.company ?: guess?.company) }
        val etUnit = field(R.id.etProductUnit).apply { setText(UnitWords.label(product.defaultUnit ?: guess?.unit)) }
        // The guessed kind in the app's language, like the field's own hint.
        // LabelGuess keeps its three kinds in English for its own reasoning.
        val guessedType = when (guess?.type) {
            "Pesticide" -> activity.getString(R.string.product_type_pesticide)
            "Fertilizer" -> activity.getString(R.string.product_type_fertilizer)
            "Seed" -> activity.getString(R.string.product_type_seed)
            else -> guess?.type
        }
        val etType = view.findViewById<android.widget.AutoCompleteTextView>(R.id.etProductType).apply {
            setText(product.productType ?: guessedType)
            // The trade's own types (9 Oct): a dairy is offered Milk, Packaged,
            // Feed; a pharmacy Tablet, Syrup, Injection. Typed words still save.
            setAdapter(android.widget.ArrayAdapter(activity, R.layout.item_dropdown_row, TradeVocab.types(activity)))
            setOnClickListener { if (text.isNullOrBlank()) showDropDown() }
        }
        view.findViewById<android.widget.TextView>(R.id.tvProductTypeLabel).text = TradeVocab.typeHint(activity)
        etUnit.hint = TradeVocab.unitOptionalHint(activity)
        // The inspection details belong to the trades an inspector visits
        // (9 Oct): hidden for any other, in the medical trade's own words for
        // a pharmacy. Hidden fields still hold what was saved, so a trade
        // switch never erases them.
        val trade = com.innovation313.roshankhata.data.Trade.current(activity)
        view.findViewById<View>(R.id.complianceGroup).visibility =
            if (com.innovation313.roshankhata.data.TradeFeatures.inspectorReports(activity)) View.VISIBLE else View.GONE
        if (trade == com.innovation313.roshankhata.data.Trade.MEDICAL) {
            view.findViewById<android.widget.TextView>(R.id.tvComplianceExplain).setText(R.string.compliance_explain_medical)
            view.findViewById<android.widget.TextView>(R.id.tvTechnicalLabel).setText(R.string.technical_name_hint_medical)
            view.findViewById<android.widget.TextView>(R.id.tvFormulationLabel).setText(R.string.formulation_hint_medical)
            view.findViewById<android.widget.TextView>(R.id.tvRegistrationLabel).setText(R.string.registration_number_hint_medical)
        }
        val etTechnical = field(R.id.etTechnicalName).apply { setText(product.technicalName) }
        val etFormulation = field(R.id.etFormulation).apply { setText(product.formulation ?: guess?.formulation) }
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

        // Said when anything came from the bill, so a guessed box is never
        // mistaken for one the owner filled himself.
        val guessed = guess != null && (
            (product.company == null && guess.company != null) ||
                (product.defaultUnit == null && guess.unit != null) ||
                (product.productType == null && guess.type != null) ||
                (product.formulation == null && guess.formulation != null)
            )

        view.findViewById<TextView>(R.id.tvGuessNote).visibility =
            if (guessed) View.VISIBLE else View.GONE

        val lastStep = step == null || step.position == step.total - 1
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(
                if (step == null) activity.getString(R.string.product_edit_details)
                else Digits.string(activity.resources, R.string.label_step_title, step.position + 1, step.total)
            )
            .setView(view)
            // Skip still advances a queue — an owner who skips one product
            // should reach the next, not be dropped out of the run.
            .setNegativeButton(if (step != null) R.string.label_step_skip else R.string.cancel) { _, _ -> onDismissed() }
            .setOnCancelListener { onDismissed() }
            .apply {
                step?.onBack?.let { back -> setNeutralButton(R.string.back) { _, _ -> back() } }
            }
            .setPositiveButton(if (lastStep) R.string.save else R.string.label_step_save_next) { _, _ ->
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
                    // Carried through untouched: the box is gone from the
                    // form, the column is not. Writing null here would erase
                    // a value the owner typed before today and never asked
                    // to lose.
                    category = product.category,
                    defaultUnit = UnitWords.canonical(etUnit.text.toString()),
                    productType = typed(etType),
                    technicalName = typed(etTechnical),
                    formulation = typed(etFormulation),
                    registrationNumber = typed(etRegistration),
                    // A cleared box means "I do not quote a rate for this",
                    // which is a null and not a zero. Zero is a price.
                    salePrice = Digits.parse(typed(etSalePrice)),
                    creditPrice = Digits.parse(typed(etCreditPrice))
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
            .create()
        // A tap beside the form used to close it — and in the queue that
        // silently skipped the product with everything typed lost. Only the
        // buttons (or Back) close it now.
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    /** Where this form sits in a run of several; see [show]. */
    class Step(val position: Int, val total: Int, val onBack: (() -> Unit)?)
}
