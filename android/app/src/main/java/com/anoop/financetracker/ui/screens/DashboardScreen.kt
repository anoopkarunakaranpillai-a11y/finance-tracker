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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anoop.financetracker.domain.AccountTypes
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.DateRange
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.parseDate
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.Routes
import com.anoop.financetracker.ui.components.DonutChart
import com.anoop.financetracker.ui.components.EmptyState
import com.anoop.financetracker.ui.components.Page
import com.anoop.financetracker.ui.components.PairBarChart
import com.anoop.financetracker.ui.components.PairPoint
import com.anoop.financetracker.ui.components.ProgressBar
import com.anoop.financetracker.ui.components.SectionCard
import com.anoop.financetracker.ui.components.Slice
import com.anoop.financetracker.ui.components.StatTile
import com.anoop.financetracker.ui.components.TxRow
import com.anoop.financetracker.ui.theme.AmountStyle
import com.anoop.financetracker.ui.theme.Ft
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun DashboardScreen(st: FinanceState, nav: NavHostController) {
    val cur = st.currency
    val now = LocalDate.now()
    val ym = YearMonth.from(now)
    val month = DateRange(ym.atDay(1), ym.atEndOfMonth())
    val sum = remember(st.txs, ym) { FinanceMath.summary(st.txs, month) }
    val series = remember(st.txs, ym) { FinanceMath.monthlySeries(st.txs, ym, 6) }
    val cats = remember(st.txs, ym) { FinanceMath.categoryTotals(st.txs, month, TxType.EXPENSE) }
    val budget = remember(st.budgets, st.txs, ym) { FinanceMath.budgetStatus(st.budgets, st.txs, month) }
    val m = { v: Long -> Money.format(v, cur) }
    val chart = Ft.c.chart

    Page("Dashboard", "${ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${ym.year} · saved on this device", actions = {
        TextButton(onClick = { nav.navigate("study") }, modifier = Modifier.testTag("open-study")) { Text("🎓 Study") }
    }) {
        item {
            // Hero: total balance across all active accounts.
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Text(m(st.totalBalance), style = AmountStyle, color = Color.White, modifier = Modifier.testTag("total-balance"))
                    Text("${st.activeAccounts.size} accounts · net worth after cards and loans", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction("+ Income", Modifier.weight(1f).testTag("qa-income")) { nav.navigate(Routes.editTx(type = TxType.INCOME)) }
                        QuickAction("− Expense", Modifier.weight(1f).testTag("qa-expense")) { nav.navigate(Routes.editTx(type = TxType.EXPENSE)) }
                        QuickAction("⇄ Transfer", Modifier.weight(1f).testTag("qa-transfer")) { nav.navigate(Routes.editTx(type = TxType.TRANSFER)) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Income", m(sum.incomeMils), Ft.c.income, Modifier.weight(1f).testTag("kpi-income"))
                StatTile("Expenses", m(sum.expenseMils), Ft.c.expense, Modifier.weight(1f).testTag("kpi-expense"))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Net savings", Money.format(sum.netMils, cur, true), if (sum.netMils >= 0) Ft.c.income else Ft.c.expense,
                    Modifier.weight(1f).testTag("kpi-net"), sub = sum.savingsRate?.let { "${(it * 100).toInt()}% of income" } ?: "No income yet")
                StatTile("Budget used", if (budget.limitMils > 0) "${(budget.ratio * 100).toInt()}%" else "—",
                    when { budget.ratio >= 1 -> Ft.c.expense; budget.ratio >= .8 -> Ft.c.warn; else -> MaterialTheme.colorScheme.onSurface },
                    Modifier.weight(1f).testTag("kpi-budget"),
                    sub = if (budget.limitMils > 0) "${m(budget.spentMils)} of ${m(budget.limitMils)}" else "No budgets set")
            }
        }
        item {
            SectionCard("Income vs expenses", action = { Text("6 months", style = MaterialTheme.typography.labelMedium, color = Ft.c.muted) }) {
                if (series.all { it.incomeMils == 0L && it.expenseMils == 0L }) Text("Add income and expenses to see your trend.", color = Ft.c.muted)
                else PairBarChart(series.map { PairPoint(it.month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), it.incomeMils, it.expenseMils) },
                    Ft.c.income, Ft.c.expense, "Income", "Expenses", { Money.compact(it, cur) })
            }
        }
        item {
            SectionCard("Where the money went") {
                if (cats.isEmpty()) Text("No expenses this month.", color = Ft.c.muted)
                else DonutChart(cats.mapIndexed { i, c -> Slice(Categories.find(c.category, st.custom).name, c.mils, chart[i % chart.size]) }, { m(it) }, "this month")
            }
        }
        if (budget.lines.isNotEmpty()) item {
            SectionCard("Budget this month", action = { TextButton(onClick = { nav.navigate("plan") }) { Text("Budgets") } }) {
                budget.lines.take(5).forEach { l ->
                    val c = Categories.find(l.category, st.custom)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("${c.emoji} ${c.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${m(l.spentMils)} / ${m(l.limitMils)}", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                    }
                    ProgressBar(l.ratio, Modifier.padding(top = 4.dp))
                }
            }
        }
        if (st.goals.isNotEmpty()) item {
            SectionCard("Savings progress", action = { TextButton(onClick = { nav.navigate("plan") }) { Text("Goals") } }) {
                st.goals.take(4).forEach { g ->
                    val r = FinanceMath.goalRatio(g)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(g.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${(r * 100).toInt()}% · ${m(g.savedMils)}", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                    }
                    ProgressBar(r, Modifier.padding(top = 4.dp), Ft.c.income)
                }
            }
        }
        val dueSoon = st.bills.filter { !it.done && (parseDate(it.due)?.let { d -> ChronoUnit.DAYS.between(now, d) <= 7 } ?: false) }
        if (dueSoon.isNotEmpty()) item {
            SectionCard("Bills due soon", action = { TextButton(onClick = { nav.navigate("plan") }) { Text("Bills") } }) {
                dueSoon.take(4).forEach { b ->
                    val days = ChronoUnit.DAYS.between(now, parseDate(b.due)!!)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(b.title, modifier = Modifier.weight(1f))
                        Text(when { days < 0 -> "overdue"; days == 0L -> "today"; else -> "in $days d" } + " · " + m(b.amountMils),
                            color = if (days < 0) Ft.c.expense else Ft.c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            SectionCard("Accounts", action = { TextButton(onClick = { nav.navigate("accounts") }, modifier = Modifier.testTag("manage-accounts")) { Text("Manage") } }) {
                st.activeAccounts.forEach { a ->
                    val t = AccountTypes.of(a.type)
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { nav.navigate(Routes.account(a.id)) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(t.emoji); Spacer(Modifier.width(10.dp))
                        Text(a.name, modifier = Modifier.weight(1f))
                        val b = st.balances[a.id] ?: 0L
                        Text(m(b), fontWeight = FontWeight.SemiBold, color = if (b < 0) Ft.c.expense else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            SectionCard("Recent transactions", action = { TextButton(onClick = { nav.navigate("transactions") { launchSingleTop = true } }) { Text("See all") } }) {
                if (st.txs.isEmpty()) EmptyState("🧾", "No transactions yet", "Tap Add to record your first income or expense.")
                st.txs.take(6).forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Ft.c.line)
                    TxRow(t, st::accountName, cur, st.custom) { nav.navigate(Routes.editTx(id = t.id)) }
                }
            }
        }
        item {
            SectionCard {
                Row(Modifier.fillMaxWidth().clickable { nav.navigate("study") }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎓", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Study corner", style = MaterialTheme.typography.titleMedium)
                        Text("Navamika's workbook & games · Arya's mock tests. Kept separate from your money.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

