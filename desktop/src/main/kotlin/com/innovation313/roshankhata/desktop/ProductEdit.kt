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
    var unit by remember { mutableStateOf(product.defaultUnit.orEmpty()) }
    var cash by remember { mutableStateOf(product.salePrice?.let { Calc.trim(it) }.orEmpty()) }
    var credit by remember { mutableStateOf(product.creditPrice?.let { Calc.trim(it) }.orEmpty()) }
    var type by remember { mutableStateOf(product.productType.orEmpty()) }
    var technical by remember { mutableStateOf(product.technicalName.orEmpty()) }
    var formulation by remember { mutableStateOf(product.formulation.orEmpty()) }
    var registration by remember { mutableStateOf(product.registrationNumber.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needName = word(W.enter_name)
    val words = com.innovation313.roshankhata.words.LocalWords.current

    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = product.name, state = rememberDialogState(width = 560.dp, height = 820.dp)) {
        Column(
            Modifier.fillMaxSize().background(LocalRoshanColors.current.surface).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Field(word(W.product_name), name) { name = it; problem = null }
            Field(word(W.company_optional), company) { company = it }
            Field(word(W.unit_optional), unit) { unit = it }
            val trade = LocalTrade.current
            SuggestionRow(trade.units(), unit) { unit = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cash, { cash = it }, label = { Text(word(W.sale_price_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(credit, { credit = it }, label = { Text(word(W.credit_price_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text(word(W.product_price_help), color = c.textMuted, fontSize = 12.sp)
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
            problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClose) { Text(word(W.cancel)) }
                FillButton(word(W.save), c.brandGreen) {
                    val newName = name.trim()
                    if (newName.isEmpty()) { problem = needName; return@FillButton }
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
                        creditPrice = typed(credit)?.let { Calc.evalPad(it) }
                    )
                    scope.launch {
                        val clash = withContext(Dispatchers.IO) { dao.productByKey(updated.nameKey) }
                        if (clash != null && clash.id != product.id) {
                            problem = words.text(W.product_name_taken, clash.name)
                        } else {
                            withContext(Dispatchers.IO) { dao.updateProduct(updated) }
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, set: (String) -> Unit) =
    OutlinedTextField(value, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
