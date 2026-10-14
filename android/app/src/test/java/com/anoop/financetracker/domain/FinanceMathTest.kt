package com.anoop.financetracker.domain

import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.TxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class FinanceMathTest {
    private val bank = AccountEntity("bank", "Bank", "bank", "KWD", 1_000_000)   // 1,000.000
    private val cash = AccountEntity("cash", "Cash", "cash", "KWD", 50_000)       // 50.000
    private val card = AccountEntity("card", "Card", "card", "KWD", -100_000)     // owes 100.000
    private val accounts = listOf(bank, cash, card)

    private fun tx(id: String, date: String, type: String, cat: String, mils: Long, acct: String, to: String? = null, note: String = "") =
        TxEntity(id, date, "", type, cat, mils, acct, to, note)

    private val txs = listOf(
        tx("1", "2026-10-01", TxType.INCOME, "salary", 1_450_000, "bank"),
        tx("2", "2026-10-02", TxType.EXPENSE, "housing", 450_000, "bank"),
        tx("3", "2026-10-03", TxType.EXPENSE, "groceries", 38_500, "card", note = "Lulu weekly"),
        tx("4", "2026-10-04", TxType.TRANSFER, "transfer", 60_000, "bank", "cash"),
        tx("5", "2026-10-20", TxType.TRANSFER, "transfer", 120_000, "bank", "card"),
        tx("6", "2026-10-21", TxType.EXPENSE, "groceries", 100_000, "cash"),
        tx("7", "2026-09-15", TxType.EXPENSE, "dining", 20_000, "card"),
        tx("8", "2026-09-01", TxType.INCOME, "salary", 1_400_000, "bank"),
    )
    private val oct = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test fun TC_CALC_01_account_balances_follow_every_transaction() {
        val b = FinanceMath.balances(accounts, txs)
        // bank: 1000 + 1450 + 1400 - 450 - 60 - 120
        assertEquals(3_220_000L, b["bank"])
        // cash: 50 + 60 - 100
        assertEquals(10_000L, b["cash"])
        // card: -100 - 38.5 - 20 + 120
        assertEquals(-38_500L, b["card"])
        assertEquals(b["bank"], FinanceMath.accountBalance(bank, txs))
    }

    @Test fun TC_CALC_02_transfers_are_not_income_or_expense_and_do_not_change_net_worth() {
        val s = FinanceMath.summary(txs, oct)
        assertEquals(1_450_000L, s.incomeMils)
        assertEquals(588_500L, s.expenseMils)          // 450 + 38.5 + 100
        assertEquals(180_000L, s.transferMils)
        val without = txs.filter { it.type != TxType.TRANSFER }
        assertEquals(FinanceMath.totalBalance(accounts, without), FinanceMath.totalBalance(accounts, txs))
    }

    @Test fun TC_CALC_03_net_savings_and_savings_rate() {
        val s = FinanceMath.summary(txs, oct)
        assertEquals(861_500L, s.netMils)
        assertEquals(861_500.0 / 1_450_000.0, s.savingsRate!!, 1e-9)
    }

    @Test fun TC_CALC_04_savings_rate_is_empty_without_income() {
        val s = FinanceMath.summary(txs.filter { it.type == TxType.EXPENSE }, oct)
        assertNull(s.savingsRate)
    }

    @Test fun TC_CALC_05_category_totals_sorted_with_shares() {
        val c = FinanceMath.categoryTotals(txs, oct, TxType.EXPENSE)
        assertEquals(listOf("housing", "groceries"), c.map { it.category })
        assertEquals(138_500L, c[1].mils)
        assertEquals(1.0, c.sumOf { it.share }, 1e-9)
    }

    @Test fun TC_CALC_06_budget_levels_at_80_and_100_percent() {
        val st = FinanceMath.budgetStatus(listOf(BudgetEntity("groceries", 150_000), BudgetEntity("housing", 400_000), BudgetEntity("fun", 50_000)), txs, oct)
        val g = st.lines.first { it.category == "groceries" }
        assertEquals(1, g.level)                       // 138.5 of 150 = 92%
        assertEquals(2, st.lines.first { it.category == "housing" }.level) // 450 of 400
        assertEquals(0, st.lines.first { it.category == "fun" }.level)
        assertEquals(600_000L, st.limitMils)
        assertEquals(588_500L, st.spentMils)
        // Multi-month ranges scale the monthly limit.
        assertEquals(1_200_000L, FinanceMath.budgetStatus(listOf(BudgetEntity("housing", 400_000)), txs, oct, 3).limitMils)
    }

    @Test fun TC_CALC_07_monthly_series_covers_each_month() {
        val s = FinanceMath.monthlySeries(txs, YearMonth.of(2026, 10), 3)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), s.map { it.month })
        assertEquals(1_400_000L, s[1].incomeMils)
        assertEquals(20_000L, s[1].expenseMils)
        assertEquals(0L, s[0].incomeMils)
    }

    @Test fun TC_CALC_08_period_ranges() {
        val d = LocalDate.of(2026, 10, 15) // a Thursday
        val w = Periods.range(Period.WEEK, d)
        assertEquals(DayOfWeek.SUNDAY, w.from.dayOfWeek)
        assertEquals(7L, w.days)
        assertTrue(w.contains(d))
        assertEquals(DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), Periods.range(Period.MONTH, d))
        assertEquals(365L, Periods.range(Period.YEAR, d).days)
        assertEquals(LocalDate.of(2026, 9, 15), Periods.shift(Period.MONTH, d, -1))
    }

    @Test fun TC_CALC_09_recurrence_matches_tracker_rules() {
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.next(LocalDate.of(2026, 1, 31), "monthly"))
        assertEquals(LocalDate.of(2028, 2, 29), Recurrence.next(LocalDate.of(2028, 1, 31), "monthly"))
        assertEquals(LocalDate.of(2026, 4, 30), Recurrence.next(LocalDate.of(2026, 1, 30), "quarterly"))
        assertNull(Recurrence.next(LocalDate.of(2026, 1, 1), "none"))
        val occ = Recurrence.occurrences(LocalDate.of(2026, 7, 1), "monthly", LocalDate.of(2026, 10, 15))
        assertEquals(4, occ.size)
        assertEquals(2, Recurrence.occurrences(LocalDate.of(2026, 7, 1), "monthly", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 8, 31)).size)
    }

    @Test fun TC_CALC_10_goal_milestones() {
        assertEquals(listOf(25, 50), FinanceMath.goalMilestonesCrossed(1_000_000, 200_000, 500_000))
        assertEquals(listOf(100), FinanceMath.goalMilestonesCrossed(1_000_000, 990_000, 1_200_000))
        assertTrue(FinanceMath.goalMilestonesCrossed(1_000_000, 600_000, 700_000).isEmpty())
    }

    @Test fun TC_CALC_11_filters_and_search() {
        assertEquals(listOf("3"), txs.filter { FinanceMath.matches(it, TxFilter(query = "lulu")) }.map { it.id })
        // An account filter includes transfers into the account.
        assertEquals(setOf("4", "6"), txs.filter { FinanceMath.matches(it, TxFilter(accountId = "cash")) }.map { it.id }.toSet())
        assertEquals(2, txs.count { FinanceMath.matches(it, TxFilter(category = "groceries")) })
        val s = FinanceMath.summary(txs, oct, TxFilter(accountId = "cash"))
        assertEquals(100_000L, s.expenseMils)
        assertEquals(0L, s.incomeMils)
    }

    @Test fun TC_CALC_12_cash_flow_running_total() {
        val b = FinanceMath.buckets(txs, oct)
        assertEquals(31, b.size)
        val flow = FinanceMath.cashFlow(b)
        assertEquals(861_500L, flow.last())
        assertEquals(1_450_000L, flow[0])
        val year = FinanceMath.buckets(txs, DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        assertEquals(12, year.size)
        assertEquals(1_400_000L, year[8].incomeMils)
    }

    @Test fun TC_CALC_13_months_in_range() {
        assertEquals(1, FinanceMath.monthsIn(oct))
        assertEquals(12, FinanceMath.monthsIn(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
    }
}
