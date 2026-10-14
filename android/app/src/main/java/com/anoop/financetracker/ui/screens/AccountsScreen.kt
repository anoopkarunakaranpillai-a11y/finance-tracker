package com.anoop.financetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.domain.AccountTypes
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.Validation
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.Routes
import com.anoop.financetracker.ui.components.AmountField
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.ConfirmDialog
import com.anoop.financetracker.ui.components.EmptyState
import com.anoop.financetracker.ui.components.KeyValue
import com.anoop.financetracker.ui.components.SectionCard
import com.anoop.financetracker.ui.components.SubPage
import com.anoop.financetracker.ui.components.TxRow
import com.anoop.financetracker.ui.theme.Ft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun AccountsScreen(st: FinanceState, nav: NavHostController) {
    val cur = st.currency
    SubPage("Accounts", onBack = { nav.popBackStack() }, actions = {
        TextButton(onClick = { nav.navigate(Routes.editAccount()) }, modifier = Modifier.testTag("account-add")) { Text("+ Account") }
    }) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { KeyValue("Net worth", Money.format(st.totalBalance, cur), bold = true) }
            AccountTypes.GROUPS.forEach { (g, label) ->
                val list = st.accounts.filter { AccountTypes.of(it.type).group == g && !it.archived }
                if (list.isNotEmpty()) item(g) {
                    SectionCard("$label · ${Money.format(list.sumOf { st.balances[it.id] ?: 0L }, cur)}") {
                        list.forEachIndexed { i, a -> if (i > 0) HorizontalDivider(color = Ft.c.line); AccountRow(a, st.balances[a.id] ?: 0L, cur) { nav.navigate(Routes.account(a.id)) } }
                    }
                }
            }
            val archived = st.accounts.filter { it.archived }
            if (archived.isNotEmpty()) item {
                SectionCard("Archived") { archived.forEach { a -> AccountRow(a, st.balances[a.id] ?: 0L, cur) { nav.navigate(Routes.account(a.id)) } } }
            }
            if (st.accounts.isEmpty()) item { EmptyState("🏦", "No accounts", "Add a bank account, cash or card to start.") }
            item { Spacer(Modifier.heightIn(min = 40.dp)) }
        }
    }
}

@Composable
private fun AccountRow(a: AccountEntity, bal: Long, cur: String, onClick: () -> Unit) {
    val t = AccountTypes.of(a.type)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp).testTag("account-${a.id}"), verticalAlignment = Alignment.CenterVertically) {
        Text(t.emoji, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge)
            Text(t.name + detailLine(a), style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
        }
        Text(Money.format(bal, cur), fontWeight = FontWeight.SemiBold, color = if (bal < 0) Ft.c.expense else MaterialTheme.colorScheme.onSurface)
    }
}

private fun details(a: AccountEntity): Map<String, String> = try {
    (Json.parseToJsonElement(a.detailsJson) as JsonObject).mapValues { (it.value as? JsonPrimitive)?.content ?: "" }
} catch (e: Exception) { emptyMap() }

private fun detailLine(a: AccountEntity): String {
    val d = details(a)
    return listOfNotNull(d["inst"]?.takeIf { it.isNotBlank() }, d["last4"]?.takeIf { it.isNotBlank() }?.let { "•••• $it" }).joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
}

@Composable
fun AccountDetailScreen(st: FinanceState, vm: AppViewModel, nav: NavHostController, id: String) {
    val a = st.accounts.firstOrNull { it.id == id }
    if (a == null) { SubPage("Account", { nav.popBackStack() }) { EmptyState("🏦", "Account not found", "It may have been deleted.") }; return }
    val cur = st.currency
    val txs = remember(st.txs, id) { st.txs.filter { it.accountId == id || it.toAccountId == id } }
    val bal = st.balances[id] ?: 0L
    var confirmDel by remember { mutableStateOf(false) }
    SubPage(a.name, onBack = { nav.popBackStack() }, actions = {
        TextButton(onClick = { nav.navigate(Routes.editAccount(id)) }) { Text("Edit") }
    }) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Text("Balance", color = Ft.c.muted)
                    Text(Money.format(bal, cur), style = MaterialTheme.typography.headlineMedium, color = if (bal < 0) Ft.c.expense else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("account-balance"))
                    KeyValue("Type", AccountTypes.of(a.type).name)
                    KeyValue("Opening balance", Money.format(a.openingMils, cur))
                    val inc = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMils }
                    val exp = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMils }
                    val tin = txs.filter { it.type == TxType.TRANSFER && it.toAccountId == id }.sumOf { it.amountMils }
                    val tout = txs.filter { it.type == TxType.TRANSFER && it.accountId == id }.sumOf { it.amountMils }
                    KeyValue("Income", Money.format(inc, cur), Ft.c.income)
                    KeyValue("Expenses", Money.format(exp, cur), Ft.c.expense)
                    KeyValue("Transfers in / out", "${Money.format(tin, cur)} / ${Money.format(tout, cur)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { nav.navigate(Routes.editTx(type = TxType.EXPENSE, acct = id)) }) { Text("+ Entry") }
                        OutlinedButton(onClick = { nav.navigate(Routes.editTx(type = TxType.TRANSFER, acct = id)) }) { Text("⇄ Transfer") }
                    }
                }
            }
            item {
                SectionCard("History (${txs.size})") {
                    if (txs.isEmpty()) Text("No transactions in this account yet.", color = Ft.c.muted)
                    txs.take(300).forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = Ft.c.line)
                        TxRow(t, st::accountName, cur, st.custom) { nav.navigate(Routes.editTx(id = t.id)) }
                    }
                }
            }
            item {
                TextButton(onClick = { confirmDel = true }, modifier = Modifier.testTag("account-delete")) {
                    Text(if (txs.isEmpty()) "Delete account" else "Archive account", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.heightIn(min = 40.dp))
            }
        }
    }
    if (confirmDel) ConfirmDialog(if (txs.isEmpty()) "Delete ${a.name}?" else "Archive ${a.name}?",
        if (txs.isEmpty()) "This account has no transactions and will be removed." else "It has ${txs.size} transactions, so it will be hidden from lists but kept for your history and reports.",
        if (txs.isEmpty()) "Delete" else "Archive",
        onConfirm = { confirmDel = false; vm.deleteAccount(id) {}; nav.popBackStack() }, onDismiss = { confirmDel = false })
}

@Composable
fun AccountEditScreen(st: FinanceState, vm: AppViewModel, id: String?, onDone: () -> Unit) {
    val ex = id?.let { i -> st.accounts.firstOrNull { it.id == i } }
    val cur = st.currency
    val d0 = ex?.let { details(it) } ?: emptyMap()
    val bal0 = ex?.let { st.balances[it.id] } ?: 0L
    var name by remember { mutableStateOf(ex?.name ?: "") }
    var type by remember { mutableStateOf(ex?.type ?: "bank") }
    var balance by remember { mutableStateOf(if (ex != null) Money.toInput(kotlin.math.abs(bal0), cur) else "") }
    var inst by remember { mutableStateOf(d0["inst"] ?: "") }
    var last4 by remember { mutableStateOf(d0["last4"] ?: "") }
    var limit by remember { mutableStateOf(d0["limit"] ?: "") }
    var archived by remember { mutableStateOf(ex?.archived ?: false) }
    var errs by remember { mutableStateOf(mapOf<String, String>()) }
    SubPage(if (ex == null) "New account" else "Edit account", onBack = onDone) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("field-account-name"),
                isError = errs["name"] != null, supportingText = errs["name"]?.let { { Text(it) } })
            ChoiceField("Type", type, AccountTypes.ALL.map { Choice(it.key, "${it.emoji} ${it.name}") }, { type = it }, tag = "account-type")
            val liab = AccountTypes.isLiability(type, d0["dir"])
            AmountField(balance, { balance = it }, if (liab) "Amount you owe now ($cur)" else "Current balance ($cur)", errs["balance"], tag = "account-balance")
            Text(if (liab) "Cards and loans are counted as debt, so this lowers your net worth." else "Leave empty for zero. Every income, expense and transfer updates this balance.",
                style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            OutlinedTextField(inst, { inst = it.take(60) }, label = { Text("Bank or provider (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(last4, { last4 = it.filter(Char::isDigit).take(4) }, label = { Text("Last 4 digits (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = errs["last4"] != null, supportingText = errs["last4"]?.let { { Text(it) } })
            if (AccountTypes.of(type).group == "credit") AmountField(limit, { limit = it }, "Credit limit (optional)", null, tag = "limit")
            if (ex != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Archived (hidden from lists)", modifier = Modifier.weight(1f)); Switch(archived, { archived = it })
            }
            Button(onClick = {
                val e = HashMap<String, String>()
                Validation.validateName(name, "account")?.let { e["name"] = it }
                val amt: Long = if (balance.isBlank()) 0 else when (val p = Money.parsePositive(balance, cur)) {
                    is Money.Parse.Ok -> p.mils
                    is Money.Parse.Error -> if (balance.trim().trimStart('0', '.').isEmpty()) 0 else { e["balance"] = p.message; 0 }
                }
                if (last4.isNotEmpty() && last4.length != 4) e["last4"] = "Last 4 digits must be 4 numbers."
                errs = e
                if (e.isEmpty()) {
                    val target = if (liab) -amt else amt
                    val txNet = if (ex != null) bal0 - ex.openingMils else 0L
                    val det = HashMap(d0)
                    fun putOrDrop(k: String, v: String) { if (v.isBlank()) det.remove(k) else det[k] = v.trim() }
                    putOrDrop("inst", inst); putOrDrop("last4", last4); putOrDrop("limit", limit)
                    val json = JsonObject(det.mapValues { JsonPrimitive(it.value) }).toString()
                    vm.saveAccount(AccountEntity(ex?.id ?: vm.newId(), name.trim(), type, ex?.currency ?: cur, target - txNet, json, archived,
                        ex?.sortOrder ?: st.accounts.size))
                    onDone()
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account-save")) { Text(if (ex == null) "Add account" else "Save changes") }
        }
    }
}

