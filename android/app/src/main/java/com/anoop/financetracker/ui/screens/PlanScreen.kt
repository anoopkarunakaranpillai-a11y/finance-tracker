package com.anoop.financetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anoop.financetracker.data.BillEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.RecurringEntity
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.DateRange
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.Repeat
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.Validation
import com.anoop.financetracker.domain.iso
import com.anoop.financetracker.domain.parseDate
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.components.AmountField
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.ConfirmDialog
import com.anoop.financetracker.ui.components.DateField
import com.anoop.financetracker.ui.components.EmptyState
import com.anoop.financetracker.ui.components.KeyValue
import com.anoop.financetracker.ui.components.Page
import com.anoop.financetracker.ui.components.ProgressBar
import com.anoop.financetracker.ui.components.SectionCard
import com.anoop.financetracker.ui.theme.Ft
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun PlanScreen(st: FinanceState, vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Budgets", "Goals", "Bills", "Recurring")
    Column {
        PrimaryTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) }, modifier = Modifier.testTag("plan-$t")) }
        }
        when (tab) {
            0 -> BudgetsTab(st, vm)
            1 -> GoalsTab(st, vm)
            2 -> BillsTab(st, vm)
            else -> RecurringTab(st, vm)
        }
    }
}

// ---------------- Budgets ----------------

@Composable
private fun BudgetsTab(st: FinanceState, vm: AppViewModel) {
    val cur = st.currency
    val ym = YearMonth.now()
    val status = remember(st.budgets, st.txs, ym) { FinanceMath.budgetStatus(st.budgets, st.txs, DateRange(ym.atDay(1), ym.atEndOfMonth())) }
    val spent = remember(st.txs, ym) { FinanceMath.categoryTotals(st.txs, DateRange(ym.atDay(1), ym.atEndOfMonth()), TxType.EXPENSE).associate { it.category to it.mils } }
    var edit by remember { mutableStateOf<String?>(null) }
    val m = { v: Long -> Money.format(v, cur) }
    Page("Budgets", "Monthly limits per category · they apply to every month") {
        item {
            SectionCard("This month") {
                if (status.limitMils == 0L) Text("No budgets yet. Tap a category below to set a monthly limit.", color = Ft.c.muted)
                else {
                    KeyValue("Spent in budgeted categories", "${m(status.spentMils)} of ${m(status.limitMils)}", bold = true)
                    ProgressBar(status.ratio, Modifier.padding(top = 6.dp))
                    val over = status.lines.filter { it.level == 2 }
                    val near = status.lines.filter { it.level == 1 }
                    if (over.isNotEmpty()) Text("Over budget: " + over.joinToString { Categories.find(it.category, st.custom).name }, color = Ft.c.expense, modifier = Modifier.padding(top = 8.dp))
                    if (near.isNotEmpty()) Text("Close to the limit: " + near.joinToString { Categories.find(it.category, st.custom).name }, color = Ft.c.warn, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            SectionCard("Categories") {
                Categories.forKind(TxType.EXPENSE, st.custom).forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = Ft.c.line)
                    val limit = st.budgets.firstOrNull { it.category == c.key }?.limitMils
                    val sp = spent[c.key] ?: 0L
                    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { edit = c.key }.padding(vertical = 8.dp).testTag("budget-${c.key}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${c.emoji}  ${c.name}", modifier = Modifier.weight(1f))
                            Text(if (limit != null) "${m(sp)} / ${m(limit)}" else if (sp > 0) "${m(sp)} · no limit" else "Set limit",
                                style = MaterialTheme.typography.bodySmall, color = if (limit != null && sp > limit) Ft.c.expense else Ft.c.muted)
                        }
                        if (limit != null) ProgressBar(sp.toDouble() / limit, Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
    edit?.let { key ->
        val c = Categories.find(key, st.custom)
        var text by remember(key) { mutableStateOf(st.budgets.firstOrNull { it.category == key }?.let { Money.toInput(it.limitMils, cur) } ?: "") }
        var err by remember(key) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("${c.emoji} ${c.name}") },
            text = { Column { Text("Monthly limit in $cur. Leave empty for no limit.", color = Ft.c.muted); Spacer(Modifier.size(8.dp)); AmountField(text, { text = it; err = null }, "Monthly limit", err, tag = "budget") } },
            confirmButton = { TextButton(onClick = {
                if (text.isBlank()) { vm.setBudget(key, null); edit = null }
                else when (val p = Money.parsePositive(text, cur)) {
                    is Money.Parse.Ok -> { vm.setBudget(key, p.mils); edit = null }
                    is Money.Parse.Error -> err = p.message
                }
            }, modifier = Modifier.testTag("budget-save")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } })
    }
}

// ---------------- Goals ----------------

@Composable
private fun GoalsTab(st: FinanceState, vm: AppViewModel) {
    val cur = st.currency
    var edit by remember { mutableStateOf<GoalEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var addMoney by remember { mutableStateOf<GoalEntity?>(null) }
    val m = { v: Long -> Money.format(v, cur) }
    Page("Savings goals", "${st.goals.size} goals · ${m(st.goals.sumOf { it.savedMils })} saved", actions = {
        Button(onClick = { creating = true }, modifier = Modifier.testTag("goal-add")) { Text("+ Goal") }
    }) {
        if (st.goals.isEmpty()) item { EmptyState("🎯", "No goals yet", "Set a target, like an emergency fund or a new car, and track it here.") }
        st.goals.forEach { g ->
            item(g.id) {
                val r = FinanceMath.goalRatio(g)
                SectionCard(g.name, action = { TextButton(onClick = { edit = g }) { Text("Edit") } }) {
                    KeyValue("Saved", "${m(g.savedMils)} of ${m(g.targetMils)}", bold = true)
                    ProgressBar(r, Modifier.padding(vertical = 6.dp), if (r >= 1) Ft.c.income else null)
                    val left = (g.targetMils - g.savedMils).coerceAtLeast(0)
                    val dl = parseDate(g.deadline)
                    val extra = if (dl != null && left > 0) {
                        val months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(dl)).coerceAtLeast(1)
                        " · ${m(left / months)} a month until ${g.deadline}"
                    } else ""
                    Text(if (left == 0L) "Goal reached 🎉" else "${m(left)} to go$extra", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                    OutlinedButton(onClick = { addMoney = g }, modifier = Modifier.padding(top = 8.dp)) { Text("Add or withdraw") }
                }
            }
        }
    }
    if (creating || edit != null) GoalDialog(st, edit, onSave = { vm.saveGoal(it); creating = false; edit = null },
        onDelete = { vm.deleteGoal(it); edit = null }, onDismiss = { creating = false; edit = null }, newId = vm::newId)
    addMoney?.let { g ->
        var text by remember(g.id) { mutableStateOf("") }
        var err by remember(g.id) { mutableStateOf<String?>(null) }
        var asExpense by remember(g.id) { mutableStateOf(false) }
        var from by remember(g.id) { mutableStateOf(st.activeAccounts.firstOrNull()?.id ?: "") }
        AlertDialog(onDismissRequest = { addMoney = null }, title = { Text(g.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(text, { text = it; err = null }, "Amount (use − to withdraw)", err, allowMinus = true, tag = "goal-money")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(asExpense, { asExpense = it }); Text("Also record it as money leaving an account", style = MaterialTheme.typography.bodySmall)
                }
                if (asExpense) ChoiceField("From account", from, st.activeAccounts.map { Choice(it.id, it.name) }, { from = it })
            } },
            confirmButton = { TextButton(onClick = {
                val neg = text.trim().startsWith("-")
                when (val p = Money.parsePositive(text.trim().removePrefix("-"), cur)) {
                    is Money.Parse.Ok -> { vm.addToGoal(g.id, if (neg) -p.mils else p.mils, if (asExpense && !neg) from else null); addMoney = null }
                    is Money.Parse.Error -> err = p.message
                }
            }) { Text("Update goal") } },
            dismissButton = { TextButton(onClick = { addMoney = null }) { Text("Cancel") } })
    }
}

@Composable
private fun GoalDialog(st: FinanceState, g: GoalEntity?, onSave: (GoalEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit, newId: () -> String) {
    val cur = st.currency
    var name by remember { mutableStateOf(g?.name ?: "") }
    var target by remember { mutableStateOf(g?.let { Money.toInput(it.targetMils, cur) } ?: "") }
    var saved by remember { mutableStateOf(g?.let { Money.toInput(it.savedMils, cur) } ?: "") }
    var deadline by remember { mutableStateOf(g?.deadline ?: "") }
    var errs by remember { mutableStateOf(mapOf<String, String>()) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (g == null) "New goal" else "Edit goal") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Goal name") }, singleLine = true, isError = errs["name"] != null,
                supportingText = errs["name"]?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("field-goal-name"))
            AmountField(target, { target = it }, "Target ($cur)", errs["target"], tag = "goal-target")
            AmountField(saved, { saved = it }, "Saved so far", errs["saved"], tag = "goal-saved")
            DateField("Target date", deadline, { deadline = it }, allowEmpty = true)
            if (g != null) TextButton(onClick = { confirmDel = true }) { Text("Delete goal", color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val e = HashMap<String, String>()
            Validation.validateName(name, "goal")?.let { e["name"] = it }
            val t = Money.parsePositive(target, cur)
            if (t is Money.Parse.Error) e["target"] = t.message
            val sv = if (saved.isBlank()) Money.Parse.Ok(0) else Money.parsePositive(saved, cur).let { if (it is Money.Parse.Error && saved.trim().trimStart('0', '.').isEmpty()) Money.Parse.Ok(0) else it }
            if (sv is Money.Parse.Error) e["saved"] = sv.message
            errs = e
            if (e.isEmpty()) onSave(GoalEntity(g?.id ?: newId(), name.trim(), g?.type ?: "savings", (t as Money.Parse.Ok).mils, (sv as Money.Parse.Ok).mils,
                deadline, g?.color ?: "--c-teal", g?.historyJson ?: "[]"))
        }, modifier = Modifier.testTag("goal-save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (confirmDel && g != null) ConfirmDialog("Delete ${g.name}?", "The goal and its history will be removed. Transactions are kept.", "Delete",
        { confirmDel = false; onDelete(g.id) }, { confirmDel = false })
}

// ---------------- Bills ----------------

@Composable
private fun BillsTab(st: FinanceState, vm: AppViewModel) {
    val cur = st.currency
    val now = LocalDate.now()
    var edit by remember { mutableStateOf<BillEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf<BillEntity?>(null) }
    val m = { v: Long -> Money.format(v, cur) }
    val open = st.bills.filter { !it.done }
    val monthly = open.filter { it.repeat == "monthly" }.sumOf { it.amountMils }
    Page("Bills", "${open.size} upcoming · ${m(monthly)} a month in repeating bills", actions = {
        Button(onClick = { creating = true }, modifier = Modifier.testTag("bill-add")) { Text("+ Bill") }
    }) {
        if (st.bills.isEmpty()) item { EmptyState("🧾", "No bills yet", "Add rent, phone, loans or cards to get reminders before they are due.") }
        open.forEach { b ->
            item(b.id) {
                val days = parseDate(b.due)?.let { ChronoUnit.DAYS.between(now, it) } ?: 0
                SectionCard(b.title, action = { TextButton(onClick = { edit = b }) { Text("Edit") } }) {
                    KeyValue("Amount", m(b.amountMils), bold = true)
                    KeyValue("Due", b.due + when { days < 0 -> " · overdue"; days == 0L -> " · today"; else -> " · in $days days" },
                        if (days < 0) Ft.c.expense else if (days <= b.remindDays) Ft.c.warn else androidx.compose.ui.graphics.Color.Unspecified)
                    KeyValue("Repeats", Repeat.label(b.repeat))
                    if (b.provider.isNotBlank()) KeyValue("Provider", b.provider)
                    Button(onClick = { pay = b }, modifier = Modifier.padding(top = 8.dp).testTag("bill-pay-${b.id}")) { Text("Mark paid") }
                }
            }
        }
        val done = st.bills.filter { it.done }
        if (done.isNotEmpty()) item {
            SectionCard("Finished") { done.forEach { b -> Text("✓ ${b.title} · ${m(b.amountMils)}", color = Ft.c.muted, modifier = Modifier.clickable { edit = b }.padding(vertical = 6.dp)) } }
        }
    }
    if (creating || edit != null) BillDialog(st, edit, vm::newId, onSave = { vm.saveBill(it); creating = false; edit = null },
        onDelete = { vm.deleteBill(it); edit = null }, onDismiss = { creating = false; edit = null })
    pay?.let { b ->
        var text by remember(b.id) { mutableStateOf(Money.toInput(b.amountMils, cur)) }
        var from by remember(b.id) { mutableStateOf(b.accountId.takeIf { id -> st.accounts.any { it.id == id } } ?: st.activeAccounts.firstOrNull()?.id ?: "") }
        var err by remember(b.id) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { pay = null }, title = { Text("Pay ${b.title}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(text, { text = it; err = null }, "Amount paid ($cur)", err, tag = "pay")
                ChoiceField("Paid from", from, st.activeAccounts.map { Choice(it.id, it.name) }, { from = it })
                Text(if (b.repeat != Repeat.NONE) "This records the expense and moves the bill to its next due date." else "This records the expense and marks the bill as done.",
                    style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            } },
            confirmButton = { TextButton(onClick = {
                when (val p = Money.parsePositive(text, cur)) {
                    is Money.Parse.Ok -> if (from.isBlank()) err = "Choose an account." else { vm.payBill(b.id, p.mils, from); pay = null }
                    is Money.Parse.Error -> err = p.message
                }
            }, modifier = Modifier.testTag("pay-confirm")) { Text("Record payment") } },
            dismissButton = { TextButton(onClick = { pay = null }) { Text("Cancel") } })
    }
}

@Composable
private fun BillDialog(st: FinanceState, b: BillEntity?, newId: () -> String, onSave: (BillEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    val cur = st.currency
    var title by remember { mutableStateOf(b?.title ?: "") }
    var amount by remember { mutableStateOf(b?.let { Money.toInput(it.amountMils, cur) } ?: "") }
    var cat by remember { mutableStateOf(b?.category ?: "utilities") }
    var provider by remember { mutableStateOf(b?.provider ?: "") }
    var due by remember { mutableStateOf(b?.due ?: LocalDate.now().iso()) }
    var repeat by remember { mutableStateOf(b?.repeat ?: "monthly") }
    var remind by remember { mutableStateOf((b?.remindDays ?: 3).toString()) }
    var acct by remember { mutableStateOf(b?.accountId ?: st.activeAccounts.firstOrNull()?.id ?: "") }
    var errs by remember { mutableStateOf(mapOf<String, String>()) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (b == null) "New bill" else "Edit bill") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it.take(60) }, label = { Text("Name, e.g. Rent or Zain mobile") }, singleLine = true,
                isError = errs["title"] != null, supportingText = errs["title"]?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("field-bill-title"))
            AmountField(amount, { amount = it }, "Amount ($cur)", errs["amount"], tag = "bill-amount")
            ChoiceField("Category", cat, Categories.forKind(TxType.EXPENSE, st.custom).map { Choice(it.key, "${it.emoji} ${it.name}") }, { cat = it })
            OutlinedTextField(provider, { provider = it.take(60) }, label = { Text("Provider (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("Next due", due, { due = it }, error = errs["due"])
            ChoiceField("Repeats", repeat, Repeat.ALL.map { Choice(it.first, it.second) }, { repeat = it })
            ChoiceField("Remind me", remind, listOf("0", "1", "2", "3", "5", "7").map { Choice(it, if (it == "0") "On the due date" else "$it days before") }, { remind = it })
            ChoiceField("Usually paid from", acct, st.activeAccounts.map { Choice(it.id, it.name) }, { acct = it })
            if (b != null) TextButton(onClick = { confirmDel = true }) { Text("Delete bill", color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val e = HashMap<String, String>()
            Validation.validateName(title, "bill")?.let { e["title"] = it }
            val p = Money.parsePositive(amount, cur)
            if (p is Money.Parse.Error) e["amount"] = p.message
            if (parseDate(due) == null) e["due"] = "Pick the due date."
            errs = e
            if (e.isEmpty()) onSave(BillEntity(b?.id ?: newId(), title.trim(), (p as Money.Parse.Ok).mils, cat, provider.trim(), due, repeat,
                remind.toIntOrNull() ?: 3, acct, b?.note ?: "", b?.number ?: "", b?.paidJson ?: "[]", false, b?.loanAccountId))
        }, modifier = Modifier.testTag("bill-save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (confirmDel && b != null) ConfirmDialog("Delete ${b.title}?", "Reminders for it stop. Payments already recorded stay in your transactions.", "Delete",
        { confirmDel = false; onDelete(b.id) }, { confirmDel = false })
}

// ---------------- Recurring ----------------

@Composable
private fun RecurringTab(st: FinanceState, vm: AppViewModel) {
    val cur = st.currency
    var edit by remember { mutableStateOf<RecurringEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    Page("Recurring", "Income and expenses the app records for you on their dates", actions = {
        Button(onClick = { creating = true }, modifier = Modifier.testTag("rec-add")) { Text("+ Recurring") }
    }) {
        if (st.recurring.isEmpty()) item { EmptyState("🔁", "Nothing recurring yet", "Add your salary, rent or subscriptions and they are recorded automatically each time they fall due.") }
        st.recurring.forEach { r ->
            item(r.id) {
                val c = Categories.find(r.category, st.custom)
                SectionCard(r.note.ifBlank { c.name }, action = { TextButton(onClick = { edit = r }) { Text("Edit") } }) {
                    KeyValue(TxType.label(r.type), Money.format(r.amountMils, cur), bold = true)
                    KeyValue("Repeats", Repeat.label(r.repeat))
                    KeyValue(if (r.active) "Next on" else "Status", if (r.active) r.nextDate else "Finished")
                    KeyValue("Account", st.accountName(r.accountId) + (r.toAccountId?.let { " → " + st.accountName(it) } ?: ""))
                }
            }
        }
    }
    if (creating || edit != null) RecurringDialog(st, edit, vm::newId, onSave = { vm.saveRecurring(it); creating = false; edit = null },
        onDelete = { vm.deleteRecurring(it); edit = null }, onDismiss = { creating = false; edit = null })
}

@Composable
private fun RecurringDialog(st: FinanceState, r: RecurringEntity?, newId: () -> String, onSave: (RecurringEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    val cur = st.currency
    var type by remember { mutableStateOf(r?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(r?.let { Money.toInput(it.amountMils, cur) } ?: "") }
    var cat by remember { mutableStateOf(r?.category ?: "") }
    var acct by remember { mutableStateOf(r?.accountId ?: st.activeAccounts.firstOrNull()?.id ?: "") }
    var to by remember { mutableStateOf(r?.toAccountId ?: "") }
    var note by remember { mutableStateOf(r?.note ?: "") }
    var next by remember { mutableStateOf(r?.nextDate ?: LocalDate.now().iso()) }
    var repeat by remember { mutableStateOf(r?.repeat ?: "monthly") }
    var end by remember { mutableStateOf(r?.endDate ?: "") }
    var errs by remember { mutableStateOf(mapOf<String, String>()) }
    var confirmDel by remember { mutableStateOf(false) }
    val accts = st.activeAccounts.map { Choice(it.id, it.name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (r == null) "New recurring item" else "Edit recurring item") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceField("Type", type, TxType.ALL.map { Choice(it, TxType.label(it)) }, { type = it; cat = "" })
            AmountField(amount, { amount = it }, "Amount ($cur)", errs["amount"], tag = "rec-amount")
            if (type != TxType.TRANSFER) ChoiceField("Category", cat, Categories.forKind(type, st.custom).map { Choice(it.key, "${it.emoji} ${it.name}") }, { cat = it }, error = errs["category"], tag = "rec-cat")
            ChoiceField(if (type == TxType.TRANSFER) "From account" else "Account", acct, accts, { acct = it }, error = errs["account"])
            if (type == TxType.TRANSFER) ChoiceField("To account", to, accts, { to = it }, error = errs["to"])
            OutlinedTextField(note, { note = it.take(80) }, label = { Text("Name, e.g. Salary or Netflix") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("field-rec-note"))
            DateField("First / next date", next, { next = it }, error = errs["next"])
            ChoiceField("Repeats", repeat, Repeat.ALL.filter { it.first != Repeat.NONE }.map { Choice(it.first, it.second) }, { repeat = it })
            DateField("End date", end, { end = it }, allowEmpty = true)
            if (r != null) TextButton(onClick = { confirmDel = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val e = HashMap<String, String>()
            val p = Money.parsePositive(amount, cur)
            if (p is Money.Parse.Error) e["amount"] = p.message
            if (type != TxType.TRANSFER && cat.isBlank()) e["category"] = "Choose a category."
            if (acct.isBlank()) e["account"] = "Choose an account."
            if (type == TxType.TRANSFER && (to.isBlank() || to == acct)) e["to"] = "Pick a different account."
            if (parseDate(next) == null) e["next"] = "Pick a date."
            errs = e
            if (e.isEmpty()) onSave(RecurringEntity(r?.id ?: newId(), type, if (type == TxType.TRANSFER) "transfer" else cat, (p as Money.Parse.Ok).mils,
                acct, if (type == TxType.TRANSFER) to else null, note.trim(), next, repeat, end, true))
        }, modifier = Modifier.testTag("rec-save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (confirmDel && r != null) ConfirmDialog("Delete this recurring item?", "Future dates stop. Transactions already recorded are kept.", "Delete",
        { confirmDel = false; onDelete(r.id) }, { confirmDel = false })
}

