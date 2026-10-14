package com.anoop.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anoop.financetracker.AppContainer
import com.anoop.financetracker.domain.AccountTypes
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.iso
import com.anoop.financetracker.domain.parseDate
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.TxEditorViewModel
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.ConfirmDialog
import com.anoop.financetracker.ui.components.SubPage
import com.anoop.financetracker.ui.theme.Ft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxEditorScreen(container: AppContainer, st: FinanceState, appVm: AppViewModel, id: String?, type: String?, acct: String?, onDone: () -> Unit) {
    val vm: TxEditorViewModel = viewModel(key = "tx-${id ?: "new"}-$type",
        factory = viewModelFactory { initializer { TxEditorViewModel(container.repo, container.settings, id, type, acct) } })
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    LaunchedEffect(s.saved) { if (s.saved) { appVm.say(if (s.isNew) "Saved." else "Changes saved."); onDone() } }

    val d = s.draft
    val accounts = st.activeAccounts.ifEmpty { st.accounts }
    SubPage(if (s.isNew) "New ${TxType.label(d.type).lowercase()}" else "Edit ${TxType.label(d.type).lowercase()}", onBack = onDone, actions = {
        if (!s.isNew) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("tx-delete")) { Icon(Icons.Filled.Delete, contentDescription = "Delete transaction") }
    }) {
        if (s.loading) { CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally)); return@SubPage }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxType.ALL.forEachIndexed { i, t ->
                    SegmentedButton(selected = d.type == t, onClick = { vm.edit { it.copy(type = t) } },
                        shape = SegmentedButtonDefaults.itemShape(i, TxType.ALL.size), modifier = Modifier.testTag("type-$t")) { Text(TxType.label(t)) }
                }
            }
            OutlinedTextField(
                value = d.amountText, onValueChange = { v -> vm.edit { it.copy(amountText = v.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' }.take(16)) } },
                label = { Text("Amount (${s.currency})") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                isError = s.errors["amount"] != null, supportingText = s.errors["amount"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("field-amount"),
            )
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field-date")) {
                Text("📅  ${d.date}" + if (d.date == LocalDate.now().iso()) " (today)" else "")
            }
            s.errors["date"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val acctChoices = accounts.map { Choice(it.id, "${AccountTypes.of(it.type).emoji} ${it.name}") }
            if (d.type == TxType.TRANSFER) {
                ChoiceField("From account", d.accountId, acctChoices, { v -> vm.edit { it.copy(accountId = v) } }, error = s.errors["account"], tag = "from")
                ChoiceField("To account", d.toAccountId, acctChoices, { v -> vm.edit { it.copy(toAccountId = v) } }, error = s.errors["toAccount"], tag = "to")
                Text("Transfers move money between your accounts. They are not counted as income or expenses.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            } else {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Categories.forKind(d.type, st.custom).forEach { c ->
                        FilterChip(selected = d.category == c.key, onClick = { vm.edit { it.copy(category = c.key) } },
                            label = { Text("${c.emoji} ${c.name}") }, modifier = Modifier.testTag("cat-${c.key}"))
                    }
                }
                s.errors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                ChoiceField(if (d.type == TxType.INCOME) "Received into" else "Paid from", d.accountId, acctChoices, { v -> vm.edit { it.copy(accountId = v) } }, error = s.errors["account"], tag = "account")
                if (d.type == TxType.EXPENSE) ChoiceField("Payment method (optional)", d.method,
                    listOf(Choice("", "Not set")) + Categories.METHODS.map { Choice(it, it) }, { v -> vm.edit { it.copy(method = v) } }, tag = "method")
                OutlinedTextField(d.merchant, { v -> vm.edit { it.copy(merchant = v.take(60)) } }, Modifier.fillMaxWidth().testTag("field-merchant"),
                    label = { Text(if (d.type == TxType.INCOME) "From (optional)" else "Payee / shop (optional)") }, singleLine = true,
                    isError = s.errors["merchant"] != null, supportingText = s.errors["merchant"]?.let { { Text(it) } })
            }
            OutlinedTextField(d.note, { v -> vm.edit { it.copy(note = v.take(200)) } }, Modifier.fillMaxWidth().testTag("field-note"),
                label = { Text("Description / notes (optional)") }, minLines = 2,
                isError = s.errors["note"] != null, supportingText = s.errors["note"]?.let { { Text(it) } })
            OutlinedTextField(d.tags, { v -> vm.edit { it.copy(tags = v.take(80)) } }, Modifier.fillMaxWidth(),
                label = { Text("Tags (optional, comma separated)") }, singleLine = true)
            s.errors["form"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (accounts.isEmpty()) Text("Add an account first (Dashboard → Accounts → Manage).", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.size(4.dp))
            Button(onClick = { vm.save() }, enabled = !s.saving && !s.saved, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tx-save")) {
                if (s.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (s.isNew) "Save" else "Save changes")
            }
            if (!s.isNew && s.extra?.billId != null) Text("Recorded from a bill payment.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            if (!s.isNew && s.extra?.recurringId != null) Text("Posted automatically by a recurring item.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
        }
    }

    if (pickDate) {
        val init = parseDate(d.date) ?: LocalDate.now()
        val dp = rememberDatePickerState(initialSelectedDateMillis = init.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                dp.selectedDateMillis?.let { ms -> val nd = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate(); vm.edit { it.copy(date = nd.iso()) } }
                pickDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }) { DatePicker(dp) }
    }
    if (confirmDelete) ConfirmDialog("Delete this transaction?", "It will be removed and balances will update. You can undo right after.", "Delete",
        onConfirm = { confirmDelete = false; appVm.deleteTx(s.id); onDone() }, onDismiss = { confirmDelete = false })
}
