package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A product's details on the computer (KMP Phase 7aa), the phone's
 * ProductDetailsDialog: name, company, unit, cash and credit price (a
 * cleared box is "no rate", never zero), and the inspection details (type,
 * active ingredient, formulation, registration number). A rename onto
 * another product's name is refused, as the phone refuses it: names are
 * unique. The category the form no longer shows is carried through.
 */
@Composable
fun ProductEditDialog(dao: KhataDao, product: Product, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(product.name) }
    var company by remember { mutableStateOf(product.company.orEmpty()) }
    var unit by remember { mutableStateOf(UnitWords.label(product.defaultUnit)) }
    var cash by remember { mutableStateOf(product.salePrice?.let { Calc.trim(it) }.orEmpty()) }
    var credit by remember { mutableStateOf(product.creditPrice?.let { Calc.trim(it) }.orEmpty()) }
    var type by remember { mutableStateOf(product.productType.orEmpty()) }
    var technical by remember { mutableStateOf(product.technicalName.orEmpty()) }
    var formulation by remember { mutableStateOf(product.formulation.orEmpty()) }
    var registration by remember { mutableStateOf(product.registrationNumber.orEmpty()) }
    var barcode by remember { mutableStateOf(product.barcode.orEmpty()) }
    var cost by remember { mutableStateOf(product.costPrice?.let { Calc.trim(it) }.orEmpty()) }
    var lowStock by remember { mutableStateOf(product.lowStockLevel?.let { Calc.trim(it) }.orEmpty()) }
    var packUnit by remember { mutableStateOf(product.packUnit.orEmpty()) }
    var packSize by remember { mutableStateOf(product.packSize?.let { Calc.trim(it) }.orEmpty()) }
    var packPrice by remember { mutableStateOf(product.packPrice?.let { Calc.trim(it) }.orEmpty()) }
    var taxRate by remember { mutableStateOf(product.taxRate?.let { Calc.trim(it) }.orEmpty()) }
    // Variants (21 Oct 2026): this product as one size, colour or flavour of a main product.
    var parentId by remember { mutableStateOf(product.parentId) }
    var variant by remember { mutableStateOf(product.variant.orEmpty()) }
    var parentQuery by remember { mutableStateOf("") }
    var others by remember { mutableStateOf<List<Product>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        others = withContext(Dispatchers.IO) {
            val all = dao.productsOnce()
            val byId = all.associateBy { it.id }
            // Never itself, nor one of its own variants: a loop has no main product.
            all.filter { it.id != product.id && !it.isDeleted && com.innovation313.roshankhata.data.ItemGroups.rootOf(it, byId).id != product.id }
        }
    }
    var problem by remember { mutableStateOf<String?>(null) }
    val needName = word(W.enter_name)
    val words = com.innovation313.roshankhata.words.LocalWords.current
    // Prices are the owner's and a manager's to change (POS Phase 5); a cashier sees them.
    val mayPrice = com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT)

    // A price, count or rate typed but not readable is said and not saved (25 Oct 2026, the audit: it was saved as
    // "no price" in silence); a cleared box is still "no rate".
    val unreadableWords = mapOf(
        W.sale_price_hint to cash, W.credit_price_hint to credit, W.product_cost_hint to cost,
        W.product_low_stock_hint to lowStock, W.product_pack_size to packSize, W.product_pack_price to packPrice
    )
    fun save() {
        val bad = unreadableWords.entries.firstOrNull { (_, v) -> v.isNotBlank() && Calc.evalPad(v.trim()) == null }?.key
            ?: W.product_tax_rate.takeIf { taxRate.isNotBlank() && Calc.evalPercent(taxRate.trim()) == null }
        if (bad != null) { problem = words.text(W.enter_valid_amount) + " — " + words.text(bad); return }
        val newName = name.trim()
        if (newName.isEmpty()) { problem = needName; return }
        fun typed(v: String) = v.trim().ifEmpty { null }
        val updated = product.copy(
            name = newName,
            nameKey = ProductName.key(newName),
            normalisedName = ProductName.normalised(newName),
            company = typed(company),
            defaultUnit = UnitWords.canonical(unit),
            productType = typed(type),
            technicalName = typed(technical),
            formulation = typed(formulation),
            registrationNumber = typed(registration),
            // A cleared box is "no rate for this": null, not zero. Zero is a price.
            salePrice = typed(cash)?.let { Calc.evalPad(it) },
            creditPrice = typed(credit)?.let { Calc.evalPad(it) },
            barcode = typed(barcode),
            costPrice = typed(cost)?.let { Calc.evalPad(it) },
            lowStockLevel = typed(lowStock)?.let { Calc.evalPad(it) },
            // A pack needs both its name and how many are in it; half of one is no pack.
            packUnit = typed(packUnit)?.takeIf { (typed(packSize)?.let { Calc.evalPad(it) } ?: 0.0) > 0.0 },
            packSize = typed(packSize)?.let { Calc.evalPad(it) }?.takeIf { it > 0.0 && typed(packUnit) != null },
            packPrice = typed(packPrice)?.let { Calc.evalPad(it) }?.takeIf { typed(packUnit) != null },
            taxRate = typed(taxRate)?.let { Calc.evalPercent(it) }?.takeIf { it > 0.0 },
            parentId = parentId,
            variant = if (parentId != null) typed(variant) else null
        )
        scope.launch {
            val clash = withContext(Dispatchers.IO) { dao.productByKey(updated.nameKey) }
            val codeClash = withContext(Dispatchers.IO) { updated.barcode?.let { dao.productByBarcode(it) } }
            if (clash != null && clash.id != product.id) {
                problem = words.text(W.product_name_taken, clash.name)
            } else if (codeClash != null && codeClash.id != product.id) {
                problem = words.text(W.product_barcode_taken, codeClash.name)
            } else {
                withContext(Dispatchers.IO) {
                    dao.updateProduct(updated)
                    if (updated.salePrice != product.salePrice || updated.creditPrice != product.creditPrice || updated.costPrice != product.costPrice) {
                        dao.log(com.innovation313.roshankhata.data.AuditAction.PRICE_CHANGED, com.innovation313.roshankhata.data.Access.userName, "products", product.id,
                            updated.name + ": " + listOfNotNull(product.salePrice, product.creditPrice).joinToString("/") + " → " + listOfNotNull(updated.salePrice, updated.creditPrice).joinToString("/"))
                    }
                }
                onClose()
            }
        }
    }
    AppDialog(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = product.name, state = rememberDialogState(width = fitW(560.dp), height = fitH(920.dp))) {
        // The app's own frame (Marhala 4): one title and ✕, the fields, Cancel and Save always in sight at its foot.
        DialogFrame(
            product.name, subtitle = word(W.edit), onClose = onClose,
            footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } },
            footer = {
                FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
                FluentButton(word(W.save), { save() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
            }
        ) {
            Field(word(W.product_name), name) { name = it; problem = null }
            Field(word(W.company_optional), company) { company = it }
            Field(word(W.unit_optional), unit) { unit = it }
            val trade = LocalTrade.current
            SuggestionRow(trade.units(), unit) { unit = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Outlined(cash, { cash = it }, label = { Text(word(W.sale_price_hint)) }, singleLine = true, modifier = Modifier.weight(1f), enabled = mayPrice, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                Outlined(credit, { credit = it }, label = { Text(word(W.credit_price_hint)) }, singleLine = true, modifier = Modifier.weight(1f), enabled = mayPrice, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            }
            Text(word(W.product_price_help), color = c.textMuted, fontSize = 12.sp)
            // The counter (POS Phase 2): the pack's barcode (a USB scanner types it here), the
            // unit's cost to the shop, and the stock level that warns in the bell.
            Field(word(W.product_barcode_hint), barcode) { barcode = it; problem = null }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Outlined(cost, { cost = it }, label = { Text(word(W.product_cost_hint)) }, singleLine = true, modifier = Modifier.weight(1f), enabled = mayPrice, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                Outlined(lowStock, { lowStock = it }, label = { Text(word(W.product_low_stock_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
            }
            // Packs and tax (schema 36): a bigger unit stock may come or go in, and this product's tax.
            Text(word(W.product_pack_help), Modifier.padding(top = 4.dp), color = c.textMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Outlined(packUnit, { packUnit = it }, label = { Text(word(W.product_pack_unit)) }, singleLine = true, modifier = Modifier.weight(1f))
                Outlined(packSize, { packSize = it }, label = { Text(word(W.product_pack_size)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Outlined(packPrice, { packPrice = it }, label = { Text(word(W.product_pack_price)) }, singleLine = true, modifier = Modifier.weight(1f), enabled = mayPrice, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                Outlined(taxRate, { taxRate = it }, label = { Text(word(W.product_tax_rate)) }, singleLine = true, modifier = Modifier.weight(1f), enabled = mayPrice, calc = com.innovation313.roshankhata.screens.CalcKind.PERCENT)
            }
            Text(word(W.product_variant_help), Modifier.padding(top = 4.dp), color = c.textMuted, fontSize = 12.sp)
            val main = others.firstOrNull { it.id == parentId }
            if (main != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(word(W.product_variant_of) + ": " + main.name, Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    IconAction("ic_close", word(W.delete), { parentId = null })
                }
                Field(word(W.product_variant), variant) { variant = it }
            } else {
                Field(word(W.product_variant_of), parentQuery) { parentQuery = it }
                if (parentQuery.isNotBlank()) com.innovation313.roshankhata.data.CounterDraft.search(others, parentQuery, 5).forEach { p ->
                    Row(Modifier.fillMaxWidth().press { parentId = p.id; parentQuery = "" }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(p.name, color = c.ink, fontSize = 14.sp)
                    }
                }
            }
            Field(word(W.product_type_hint), type) { type = it }
            SuggestionRow(trade.types(), type) { type = it }
            // The inspector's details (technical name, formulation, registration) belong to the
            // trades an inspector visits (TradeFeatures.inspectorReports); a value already
            // written stays and is saved as it was.
            if (trade.inspector || technical.isNotBlank() || formulation.isNotBlank() || registration.isNotBlank()) {
                Text(word(W.compliance_section), Modifier.padding(top = 8.dp), color = c.ink, fontWeight = FontWeight.Bold)
                Text(word(W.compliance_explain), color = c.textMuted, fontSize = 12.sp)
                Field(word(W.technical_name_hint), technical) { technical = it }
                Field(word(W.formulation_hint), formulation) { formulation = it }
                Field(word(W.registration_number_hint), registration) { registration = it }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, set: (String) -> Unit) =
    Outlined(value, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
