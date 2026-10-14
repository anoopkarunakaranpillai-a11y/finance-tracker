package com.anoop.financetracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.DateRange
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.Period
import com.anoop.financetracker.domain.Periods
import com.anoop.financetracker.domain.TxFilter
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.iso
import com.anoop.financetracker.domain.parseDate
import com.anoop.financetracker.io.CsvExport
import com.anoop.financetracker.io.PdfReport
import com.anoop.financetracker.io.ReportData
import com.anoop.financetracker.io.ShareFiles
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.DateField
import com.anoop.financetracker.ui.components.DonutChart
import com.anoop.financetracker.ui.components.KeyValue
import com.anoop.financetracker.ui.components.LineChart
import com.anoop.financetracker.ui.components.Page
import com.anoop.financetracker.ui.components.PairBarChart
import com.anoop.financetracker.ui.components.PairPoint
import com.anoop.financetracker.ui.components.ProgressBar
import com.anoop.financetracker.ui.components.SectionCard
import com.anoop.financetracker.ui.components.Slice
import com.anoop.financetracker.ui.components.TxRow
import com.anoop.financetracker.ui.theme.Ft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReportsScreen(st: FinanceState, vm: AppViewModel) {
    val ctx = LocalContext.current
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now().iso()) }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29).iso()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().iso()) }
    var acct by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    val cur = st.currency
    val a = parseDate(anchor) ?: LocalDate.now()
    val custom = DateRange(parseDate(from) ?: LocalDate.now(), parseDate(to) ?: LocalDate.now()).let { if (it.from.isAfter(it.to)) DateRange(it.to, it.from) else it }
    val range = Periods.range(period, a, custom)
    val filter = TxFilter(acct.ifBlank { null }, cat.ifBlank { null })

    val sum = remember(st.txs, range, filter) { FinanceMath.summary(st.txs, range, filter) }
    val buckets = remember(st.txs, range, filter) { FinanceMath.buckets(st.txs, range, filter) }
    val flow = remember(buckets) { FinanceMath.cashFlow(buckets) }
    val exp = remember(st.txs, range, filter) { FinanceMath.categoryTotals(st.txs, range, TxType.EXPENSE, filter) }
    val inc = remember(st.txs, range, filter) { FinanceMath.categoryTotals(st.txs, range, TxType.INCOME, filter) }
    val months = FinanceMath.monthsIn(range)
    val budget = remember(st.budgets, st.txs, range) { if (period == Period.DAY || period == Period.WEEK) null else FinanceMath.budgetStatus(st.budgets, st.txs, range, months) }
    val list = remember(st.txs, range, filter) { st.txs.filter { range.contains(it.date) && FinanceMath.matches(it, filter) } }
    val m = { v: Long -> Money.format(v, cur) }
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val label = when (period) {
        Period.DAY -> range.from.format(fmt)
        Period.MONTH -> range.from.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        Period.YEAR -> range.from.year.toString()
        else -> "${range.from.format(fmt)} – ${range.to.format(fmt)}"
    }
    val filterText = listOfNotNull(acct.ifBlank { null }?.let { st.accountName(it) }, cat.ifBlank { null }?.let { Categories.find(it, st.custom).name }).joinToString(" · ")
    val fileBase = "finance-report-${range.from.iso()}_${range.to.iso()}"
    fun report() = ReportData("Finance report", range, filterText, cur, sum, exp, inc, budget, list, st.accounts, st.custom)

    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.say(if (ShareFiles.writeTo(ctx, uri) { it.write(CsvExport.build(list, st.accounts, cur, st.custom).toByteArray()) }) "CSV saved." else "Couldn't save the CSV.")
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.say(if (ShareFiles.writeTo(ctx, uri) { PdfReport.write(report(), it) }) "PDF saved." else "Couldn't save the PDF.")
    }

    Page("Reports", label) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) }, modifier = Modifier.testTag("period-${p.name}")) }
            }
        }
        item {
            if (period == Period.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", from, { from = it }, Modifier.weight(1f)); DateField("To", to, { to = it }, Modifier.weight(1f))
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { anchor = Periods.shift(period, a, -1).iso() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${period.label.lowercase()}") }
                Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { anchor = Periods.shift(period, a, 1).iso() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${period.label.lowercase()}") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField("Account", acct, listOf(Choice("", "All accounts")) + st.accounts.map { Choice(it.id, it.name) }, { acct = it }, Modifier.weight(1f), tag = "rep-account")
                ChoiceField("Category", cat, listOf(Choice("", "All")) + (Categories.forKind(TxType.EXPENSE, st.custom) + Categories.forKind(TxType.INCOME, st.custom)).map { Choice(it.key, "${it.emoji} ${it.name}") },
                    { cat = it }, Modifier.weight(1f), tag = "rep-category")
            }
        }
        item {
            SectionCard("Summary") {
                KeyValue("Income", m(sum.incomeMils), Ft.c.income, bold = true)
                KeyValue("Expenses", m(sum.expenseMils), Ft.c.expense, bold = true)
                KeyValue("Net savings", Money.format(sum.netMils, cur, true), if (sum.netMils >= 0) Ft.c.income else Ft.c.expense, bold = true)
                KeyValue("Savings rate", sum.savingsRate?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "— (no income)")
                KeyValue("Transfers between accounts", m(sum.transferMils))
                KeyValue("Transactions", sum.count.toString())
            }
        }
        item {
            SectionCard("Income vs expenses") {
                if (sum.incomeMils == 0L && sum.expenseMils == 0L) Text("Nothing recorded in this period.", color = Ft.c.muted)
                else PairBarChart(buckets.map { PairPoint(it.label, it.incomeMils, it.expenseMils) }, Ft.c.income, Ft.c.expense, "Income", "Expenses", { Money.compact(it, cur) })
            }
        }
        if (buckets.size > 1) item {
            SectionCard("Cash flow", action = { Text("running total", style = MaterialTheme.typography.labelMedium, color = Ft.c.muted) }) {
                LineChart(flow, buckets.map { it.label }, MaterialTheme.colorScheme.primary, { Money.format(it, cur, true) })
            }
        }
        if (exp.isNotEmpty()) item {
            SectionCard("Spending by category") {
                DonutChart(exp.mapIndexed { i, c -> Slice(Categories.find(c.category, st.custom).name, c.mils, Ft.c.chart[i % Ft.c.chart.size]) }, { m(it) }, "spent")
            }
        }
        if (inc.isNotEmpty()) item {
            SectionCard("Income by source") {
                inc.forEach { c -> KeyValue(Categories.find(c.category, st.custom).let { "${it.emoji} ${it.name}" }, "${m(c.mils)} · ${(c.share * 100).toInt()}%") }
            }
        }
        budget?.takeIf { it.lines.isNotEmpty() }?.let { b ->
            item {
                SectionCard("Budget performance", action = { Text(if (months > 1) "$months months" else "1 month", style = MaterialTheme.typography.labelMedium, color = Ft.c.muted) }) {
                    b.lines.forEach { l ->
                        KeyValue(Categories.find(l.category, st.custom).name, "${m(l.spentMils)} of ${m(l.limitMils)}", if (l.ratio >= 1) Ft.c.expense else androidx.compose.ui.graphics.Color.Unspecified)
                        ProgressBar(l.ratio, Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
        item {
            SectionCard("Export") {
                Text("Exports use the period and filters above (${list.size} transactions).", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { savePdf.launch("$fileBase.pdf") }, modifier = Modifier.testTag("export-pdf")) { Text("Save PDF") }
                    OutlinedButton(onClick = { saveCsv.launch("$fileBase.csv") }, modifier = Modifier.testTag("export-csv")) { Text("Save CSV") }
                    OutlinedButton(onClick = { runCatching { ShareFiles.share(ctx, "$fileBase.pdf", "application/pdf") { PdfReport.write(report(), it) } }.onFailure { vm.say("Couldn't share the PDF.") } }) { Text("Share PDF") }
                    OutlinedButton(onClick = { runCatching { ShareFiles.share(ctx, "$fileBase.csv", "text/csv") { it.write(CsvExport.build(list, st.accounts, cur, st.custom).toByteArray()) } }.onFailure { vm.say("Couldn't share the CSV.") } }) { Text("Share CSV") }
                }
            }
        }
        item {
            SectionCard("Transactions (${list.size})") {
                if (list.isEmpty()) Text("No transactions in this period.", color = Ft.c.muted)
                list.take(50).forEachIndexed { i, t -> if (i > 0) HorizontalDivider(color = Ft.c.line); TxRow(t, st::accountName, cur, st.custom) {} }
                if (list.size > 50) Text("Showing 50 of ${list.size}. The exports include all of them.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}
