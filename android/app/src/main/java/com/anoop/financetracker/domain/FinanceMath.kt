package com.anoop.financetracker.domain

import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.TxEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Inclusive date range. */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
    fun contains(iso: String) = parseDate(iso)?.let { contains(it) } ?: false
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
}

fun parseDate(iso: String): LocalDate? = try { LocalDate.parse(iso) } catch (e: Exception) { null }
fun LocalDate.iso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)
fun latest(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b
fun earliest(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b

enum class Period(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), CUSTOM("Custom") }

object Periods {
    /** Weeks start on Sunday (the Kuwaiti working week). */
    val WEEK_START: DayOfWeek = DayOfWeek.SUNDAY

    fun range(p: Period, anchor: LocalDate, custom: DateRange? = null): DateRange = when (p) {
        Period.DAY -> DateRange(anchor, anchor)
        Period.WEEK -> {
            val s = anchor.with(TemporalAdjusters.previousOrSame(WEEK_START))
            DateRange(s, s.plusDays(6))
        }
        Period.MONTH -> YearMonth.from(anchor).let { DateRange(it.atDay(1), it.atEndOfMonth()) }
        Period.YEAR -> DateRange(anchor.withDayOfYear(1), anchor.withDayOfYear(anchor.lengthOfYear()))
        Period.CUSTOM -> custom ?: DateRange(anchor.minusDays(29), anchor)
    }

    fun shift(p: Period, anchor: LocalDate, steps: Long): LocalDate = when (p) {
        Period.DAY -> anchor.plusDays(steps)
        Period.WEEK -> anchor.plusWeeks(steps)
        Period.MONTH -> anchor.plusMonths(steps)
        Period.YEAR -> anchor.plusYears(steps)
        Period.CUSTOM -> anchor
    }
}

object Recurrence {
    /** Next date after [date] for a repeat rule, matching the web tracker's addPeriod(). Null when it does not repeat. */
    fun next(date: LocalDate, repeat: String): LocalDate? = when (repeat) {
        "daily" -> date.plusDays(1)
        "weekly" -> date.plusWeeks(1)
        "monthly" -> date.plusMonths(1)
        "quarterly" -> date.plusMonths(3)
        "yearly" -> date.plusYears(1)
        else -> null
    }

    /** All occurrence dates from [start] up to and including [until] (bounded, to protect against bad data). */
    fun occurrences(start: LocalDate, repeat: String, until: LocalDate, end: LocalDate? = null, max: Int = 400): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d: LocalDate? = start
        while (d != null && !d.isAfter(until) && (end == null || !d.isAfter(end)) && out.size < max) {
            out += d
            d = next(d, repeat)
        }
        return out
    }
}

data class Summary(val incomeMils: Long, val expenseMils: Long, val transferMils: Long, val count: Int) {
    val netMils: Long get() = incomeMils - expenseMils
    /** Share of income kept, or null when there was no income. */
    val savingsRate: Double? get() = if (incomeMils > 0) netMils.toDouble() / incomeMils else null
}

data class CategoryTotal(val category: String, val mils: Long, val share: Double)
data class MonthPoint(val month: YearMonth, val incomeMils: Long, val expenseMils: Long) {
    val netMils get() = incomeMils - expenseMils
}
data class BudgetLine(val category: String, val limitMils: Long, val spentMils: Long) {
    val ratio: Double get() = if (limitMils > 0) spentMils.toDouble() / limitMils else 0.0
    val remainingMils: Long get() = limitMils - spentMils
    val level: Int get() = when { ratio >= 1.0 -> 2; ratio >= 0.8 -> 1; else -> 0 }
}
data class BudgetStatus(val lines: List<BudgetLine>) {
    val limitMils = lines.sumOf { it.limitMils }
    val spentMils = lines.sumOf { it.spentMils }
    val ratio: Double get() = if (limitMils > 0) spentMils.toDouble() / limitMils else 0.0
}
data class Bucket(val label: String, val range: DateRange, val incomeMils: Long, val expenseMils: Long)

data class TxFilter(
    val accountId: String? = null,
    val category: String? = null,
    val type: String? = null,
    val query: String = "",
)

object FinanceMath {

    /** Balance = opening + income − expense − transfers out + transfers in. */
    fun accountBalance(account: AccountEntity, txs: List<TxEntity>): Long {
        var b = account.openingMils
        for (t in txs) {
            when (t.type) {
                TxType.INCOME -> if (t.accountId == account.id) b += t.amountMils
                TxType.EXPENSE -> if (t.accountId == account.id) b -= t.amountMils
                TxType.TRANSFER -> {
                    if (t.accountId == account.id) b -= t.amountMils
                    if (t.toAccountId == account.id) b += t.amountMils
                }
            }
        }
        return b
    }

    fun balances(accounts: List<AccountEntity>, txs: List<TxEntity>): Map<String, Long> {
        val m = HashMap<String, Long>()
        accounts.forEach { m[it.id] = it.openingMils }
        for (t in txs) {
            when (t.type) {
                TxType.INCOME -> m.computeIfPresent(t.accountId) { _, v -> v + t.amountMils }
                TxType.EXPENSE -> m.computeIfPresent(t.accountId) { _, v -> v - t.amountMils }
                TxType.TRANSFER -> {
                    m.computeIfPresent(t.accountId) { _, v -> v - t.amountMils }
                    t.toAccountId?.let { to -> m.computeIfPresent(to) { _, v -> v + t.amountMils } }
                }
            }
        }
        return m
    }

    /** Net worth across active accounts (liabilities are negative balances). Transfers net to zero. */
    fun totalBalance(accounts: List<AccountEntity>, txs: List<TxEntity>): Long {
        val b = balances(accounts, txs)
        return accounts.filter { !it.archived }.sumOf { b[it.id] ?: 0L }
    }

    fun matches(t: TxEntity, f: TxFilter): Boolean {
        if (f.accountId != null && t.accountId != f.accountId && t.toAccountId != f.accountId) return false
        if (f.category != null && t.category != f.category) return false
        if (f.type != null && t.type != f.type) return false
        val q = f.query.trim().lowercase()
        if (q.isNotEmpty()) {
            val hay = "${t.note} ${t.merchant} ${t.category} ${t.tags} ${t.method}".lowercase()
            if (!hay.contains(q) && !Money.toDouble(t.amountMils).toString().contains(q)) return false
        }
        return true
    }

    /** Income and expense exclude transfers, so moving money between accounts never inflates either. */
    fun summary(txs: List<TxEntity>, range: DateRange, filter: TxFilter = TxFilter()): Summary {
        var inc = 0L; var exp = 0L; var tr = 0L; var n = 0
        for (t in txs) {
            if (!range.contains(t.date) || !matches(t, filter)) continue
            n++
            when (t.type) {
                TxType.INCOME -> inc += t.amountMils
                TxType.EXPENSE -> exp += t.amountMils
                TxType.TRANSFER -> tr += t.amountMils
            }
        }
        return Summary(inc, exp, tr, n)
    }

    fun categoryTotals(txs: List<TxEntity>, range: DateRange, type: String, filter: TxFilter = TxFilter()): List<CategoryTotal> {
        val m = LinkedHashMap<String, Long>()
        for (t in txs) if (t.type == type && range.contains(t.date) && matches(t, filter)) m[t.category] = (m[t.category] ?: 0L) + t.amountMils
        val total = m.values.sum()
        return m.entries.map { CategoryTotal(it.key, it.value, if (total > 0) it.value.toDouble() / total else 0.0) }
            .sortedByDescending { it.mils }
    }

    fun monthlySeries(txs: List<TxEntity>, endMonth: YearMonth, months: Int, filter: TxFilter = TxFilter()): List<MonthPoint> =
        (months - 1 downTo 0).map { back ->
            val ym = endMonth.minusMonths(back.toLong())
            val s = summary(txs, DateRange(ym.atDay(1), ym.atEndOfMonth()), filter)
            MonthPoint(ym, s.incomeMils, s.expenseMils)
        }

    /** Splits a range into chart buckets: days for up to a month, months for longer ranges. */
    fun buckets(txs: List<TxEntity>, range: DateRange, filter: TxFilter = TxFilter()): List<Bucket> {
        val out = ArrayList<Bucket>()
        if (range.days <= 62) {
            var d = range.from
            while (!d.isAfter(range.to)) {
                val r = DateRange(d, d)
                val s = summary(txs, r, filter)
                out += Bucket(d.dayOfMonth.toString(), r, s.incomeMils, s.expenseMils)
                d = d.plusDays(1)
            }
        } else {
            var ym = YearMonth.from(range.from)
            val last = YearMonth.from(range.to)
            while (!ym.isAfter(last)) {
                val from = latest(ym.atDay(1), range.from); val to = earliest(ym.atEndOfMonth(), range.to)
                val s = summary(txs, DateRange(from, to), filter)
                out += Bucket(ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH), DateRange(from, to), s.incomeMils, s.expenseMils)
                ym = ym.plusMonths(1)
            }
        }
        return out
    }

    /** Running total of income − expense across the range (cash flow). */
    fun cashFlow(buckets: List<Bucket>): List<Long> {
        var acc = 0L
        return buckets.map { acc += it.incomeMils - it.expenseMils; acc }
    }

    /** Monthly budgets apply to every month; for longer ranges the limit is multiplied by the number of months. */
    fun budgetStatus(budgets: List<BudgetEntity>, txs: List<TxEntity>, range: DateRange, months: Int = 1): BudgetStatus {
        val spent = HashMap<String, Long>()
        for (t in txs) if (t.type == TxType.EXPENSE && range.contains(t.date)) spent[t.category] = (spent[t.category] ?: 0L) + t.amountMils
        return BudgetStatus(budgets.filter { it.limitMils > 0 }.map { BudgetLine(it.category, it.limitMils * months, spent[it.category] ?: 0L) }
            .sortedByDescending { it.ratio })
    }

    fun goalRatio(g: GoalEntity): Double = if (g.targetMils > 0) (g.savedMils.toDouble() / g.targetMils).coerceIn(0.0, 1.0) else 0.0

    /** Milestones crossed (25/50/75/100%) between two saved amounts. */
    fun goalMilestonesCrossed(targetMils: Long, before: Long, after: Long): List<Int> {
        if (targetMils <= 0) return emptyList()
        return listOf(25, 50, 75, 100).filter { m -> val line = targetMils * m / 100; before < line && after >= line }
    }

    fun monthsIn(range: DateRange): Int {
        val a = YearMonth.from(range.from); val b = YearMonth.from(range.to)
        return ((b.year - a.year) * 12 + (b.monthValue - a.monthValue) + 1).coerceAtLeast(1)
    }
}
