package com.anoop.financetracker.security

import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.data.BillEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.work.AlertPlanner
import com.anoop.financetracker.work.Notifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PinAndAlertsTest {

    @Test fun TC_SEC_01_pin_hash_verifies_only_the_right_pin() {
        val h = PinHasher.hash("4821", iterations = 2_000)
        assertTrue(PinHasher.verify("4821", h))
        assertFalse(PinHasher.verify("4822", h))
        assertFalse(PinHasher.verify("4821", "garbage"))
        assertFalse(h.contains("4821"))
    }

    @Test fun TC_SEC_02_same_pin_hashes_differently_each_time() {
        assertNotEquals(PinHasher.hash("1234", 1_000), PinHasher.hash("1234", 1_000))
    }

    @Test fun TC_SEC_03_pin_format_rules() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    private val today = LocalDate.of(2026, 10, 15)
    private val all = AppSettings(notifyBills = true, notifyBudget = true, notifyGoals = true, notifyRecurring = true, notifySummary = true)
    private val bills = listOf(
        BillEntity("b1", "Rent", 450_000, "housing", due = "2026-10-17", remindDays = 3),
        BillEntity("b2", "Phone", 12_000, "utilities", due = "2026-10-30", remindDays = 3),
        BillEntity("b3", "Gym", 20_000, "fun", due = "2026-10-10", remindDays = 3),
        BillEntity("b4", "Old", 1_000, "other", due = "2026-10-16", done = true),
    )
    private val txs = listOf(
        TxEntity("1", "2026-10-05", "", TxType.EXPENSE, "groceries", 130_000, "a"),
        TxEntity("2", "2026-10-06", "", TxType.EXPENSE, "dining", 70_000, "a"),
        TxEntity("3", "2026-09-06", "", TxType.INCOME, "salary", 1_000_000, "a"),
    )
    private val budgets = listOf(BudgetEntity("groceries", 150_000), BudgetEntity("dining", 60_000), BudgetEntity("fun", 100_000))
    private val goals = listOf(GoalEntity("g", "Car", targetMils = 1_000_000, savedMils = 520_000))

    @Test fun TC_NOT_01_bill_reminders_due_soon_and_overdue() {
        val a = AlertPlanner.plan(all, today, bills, emptyList(), emptyList(), emptyList(), emptyList()).filter { it.channel == Notifier.CH_BILLS }
        assertEquals(2, a.size)
        assertTrue(a.any { it.title.contains("Rent due in 2 days") })
        assertTrue(a.any { it.title.startsWith("Overdue: Gym") })
    }

    @Test fun TC_NOT_02_nothing_when_switched_off() {
        assertTrue(AlertPlanner.plan(AppSettings(), LocalDate.of(2026, 11, 1), bills, budgets, txs, goals, txs).isEmpty())
    }

    @Test fun TC_NOT_03_budget_alerts_at_80_and_100_percent() {
        val a = AlertPlanner.plan(all, today, emptyList(), budgets, txs, emptyList(), emptyList())
        assertTrue(a.any { it.key == "bud80-groceries-2026-10" })
        assertTrue(a.any { it.key == "bud100-dining-2026-10" })
        assertFalse(a.any { it.key.contains("fun") })
    }

    @Test fun TC_NOT_04_goal_milestone_alert() {
        val a = AlertPlanner.plan(all, today, emptyList(), emptyList(), emptyList(), goals, emptyList())
        assertEquals("goal-g-50", a.single().key)
    }

    @Test fun TC_NOT_05_monthly_summary_only_on_the_first() {
        assertTrue(AlertPlanner.plan(all, today, emptyList(), emptyList(), txs, emptyList(), emptyList()).none { it.channel == Notifier.CH_SUMMARY })
        val first = AlertPlanner.plan(all, LocalDate.of(2026, 10, 1), emptyList(), emptyList(), txs, emptyList(), emptyList()).single { it.channel == Notifier.CH_SUMMARY }
        assertEquals("sum-2026-09", first.key)
        assertTrue(first.text.contains("KWD 1,000.000"))
    }

    @Test fun TC_NOT_06_recurring_notice_lists_posted_items() {
        val a = AlertPlanner.plan(all, today, emptyList(), emptyList(), emptyList(), emptyList(), txs.take(2))
        assertTrue(a.single { it.channel == Notifier.CH_RECURRING }.title.contains("2 recurring"))
    }
}
