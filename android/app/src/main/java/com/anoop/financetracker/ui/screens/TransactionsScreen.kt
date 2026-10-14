package com.anoop.financetracker.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxFilter
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.Routes
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.EmptyState
import com.anoop.financetracker.ui.components.Page
import com.anoop.financetracker.ui.components.TxRow
import com.anoop.financetracker.ui.theme.Ft

@Composable
fun TransactionsScreen(st: FinanceState, vm: AppViewModel, nav: NavHostController) {
    var q by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var acct by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("new") }
    val cur = st.currency

    val filter = TxFilter(acct.ifBlank { null }, cat.ifBlank { null }, type.ifBlank { null }, q)
    val list = remember(st.txs, filter, sort) {
        val l = st.txs.filter { FinanceMath.matches(it, filter) }
        when (sort) {
            "old" -> l.sortedWith(compareBy({ it.date }, { it.time }, { it.createdAt }))
            "big" -> l.sortedByDescending { it.amountMils }
            "small" -> l.sortedBy { it.amountMils }
            else -> l
        }
    }
    val inc = list.filter { it.type == TxType.INCOME }.sumOf { it.amountMils }
    val exp = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMils }

    Page("Transactions", "${list.size} shown · in ${Money.format(inc, cur)} · out ${Money.format(exp, cur)}") {
        item {
            OutlinedTextField(q, { q = it.take(60) }, Modifier.fillMaxWidth().testTag("tx-search"), singleLine = true,
                placeholder = { Text("Search notes, payee, tags or amount") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = RoundedCornerShape(14.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "All", TxType.EXPENSE to "Expenses", TxType.INCOME to "Income", TxType.TRANSFER to "Transfers").forEach { (k, l) ->
                    FilterChip(selected = type == k, onClick = { type = k; cat = "" }, label = { Text(l) }, modifier = Modifier.testTag("chip-$l"))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField("Account", acct, listOf(Choice("", "All accounts")) + st.accounts.map { Choice(it.id, it.name) }, { acct = it }, Modifier.weight(1f))
                val cats = when (type) {
                    TxType.INCOME -> Categories.forKind(TxType.INCOME, st.custom)
                    TxType.EXPENSE -> Categories.forKind(TxType.EXPENSE, st.custom)
                    TxType.TRANSFER -> emptyList()
                    else -> Categories.forKind(TxType.EXPENSE, st.custom) + Categories.forKind(TxType.INCOME, st.custom)
                }
                ChoiceField("Category", cat, listOf(Choice("", "All")) + cats.map { Choice(it.key, "${it.emoji} ${it.name}") }, { cat = it }, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("new" to "Newest", "old" to "Oldest", "big" to "Largest", "small" to "Smallest").forEach { (k, l) ->
                    FilterChip(selected = sort == k, onClick = { sort = k }, label = { Text(l) })
                }
                if (q.isNotEmpty() || type.isNotEmpty() || acct.isNotEmpty() || cat.isNotEmpty())
                    TextButton(onClick = { q = ""; type = ""; acct = ""; cat = "" }) { Text("Clear filters") }
            }
        }
        if (list.isEmpty()) item {
            if (st.txs.isEmpty()) EmptyState("🧾", "No transactions yet", "Tap Add to record income, an expense or a transfer.")
            else EmptyState("🔍", "Nothing matches", "Try a different search or clear the filters.")
        } else item {
            Surface(shape = RoundedCornerShape(18.dp), color = Ft.c.card) {
                androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    var lastDate = ""
                    list.take(500).forEachIndexed { i, t ->
                        if (t.date != lastDate && sort in listOf("new", "old")) {
                            lastDate = t.date
                            Text(t.date, style = MaterialTheme.typography.labelMedium, color = Ft.c.muted, modifier = Modifier.padding(top = 10.dp))
                        } else if (i > 0) HorizontalDivider(color = Ft.c.line)
                        TxRow(t, st::accountName, cur, st.custom) { nav.navigate(Routes.editTx(id = t.id)) }
                    }
                    if (list.size > 500) Text("Showing the first 500. Narrow the search to see more.", color = Ft.c.muted, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}
