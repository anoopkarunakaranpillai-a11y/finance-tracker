package com.anoop.financetracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anoop.financetracker.TestEnv
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.io.Backup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private var today = LocalDate.of(2026, 10, 15)
    private lateinit var repo: FinanceRepository

    @Before fun setUp() = runBlocking {
        db = TestEnv.memoryDb()
        repo = FinanceRepository(db) { today }
        repo.createDefaultAccounts("KWD")
    }

    @After fun tearDown() { db.close() }

    private fun expense(id: String, mils: Long, acct: String = "pa-bank", date: String = "2026-10-10") =
        TxEntity(id, date, "", TxType.EXPENSE, "groceries", mils, acct, note = "test")

    @Test fun TC_TX_01_add_transaction_persists_and_updates_balance() = runBlocking {
        repo.saveTx(expense("t1", 12_500))
        assertEquals(1, repo.allTxs().size)
        val bal = FinanceMath.balances(db.accounts().all(), repo.allTxs())
        assertEquals(-12_500L, bal["pa-bank"])
    }

    @Test fun TC_TX_02_edit_keeps_created_time_and_changes_values() = runBlocking {
        val t = expense("t1", 12_500).copy(createdAt = 111)
        repo.saveTx(t)
        repo.saveTx(t.copy(amountMils = 20_000, createdAt = 999, note = "edited"))
        val got = repo.tx("t1")!!
        assertEquals(20_000L, got.amountMils)
        assertEquals("edited", got.note)
        assertEquals(111L, got.createdAt)
        assertEquals(1, repo.allTxs().size)
    }

    @Test fun TC_TX_03_delete_transaction() = runBlocking {
        repo.saveTx(expense("t1", 1_000))
        assertTrue(repo.deleteTx("t1"))
        assertFalse(repo.deleteTx("t1"))
        assertTrue(repo.allTxs().isEmpty())
    }

    @Test fun TC_TX_04_saving_the_same_entry_twice_does_not_duplicate() = runBlocking {
        val t = expense("same-id", 5_000)
        repo.saveTx(t); repo.saveTx(t); repo.saveTx(t)
        assertEquals(1, repo.allTxs().size)
    }

    @Test fun TC_ACC_01_account_with_transactions_cannot_be_deleted() = runBlocking {
        repo.saveTx(expense("t1", 1_000, "pa-cash"))
        assertFalse(repo.deleteAccount("pa-cash"))
        assertNotNull(repo.account("pa-cash"))
    }

    @Test fun TC_ACC_02_unused_account_can_be_deleted() = runBlocking {
        assertTrue(repo.deleteAccount("pa-card"))
        assertNull(repo.account("pa-card"))
    }

    @Test fun TC_ACC_03_transfer_moves_money_without_changing_net_worth() = runBlocking {
        repo.saveTx(TxEntity("x", "2026-10-10", "", TxType.TRANSFER, "transfer", 60_000, "pa-bank", "pa-cash"))
        val a = db.accounts().all(); val t = repo.allTxs()
        val b = FinanceMath.balances(a, t)
        assertEquals(-60_000L, b["pa-bank"]); assertEquals(60_000L, b["pa-cash"])
        assertEquals(0L, FinanceMath.totalBalance(a, t))
    }

    @Test fun TC_BILL_01_paying_a_bill_records_expense_and_moves_due_date() = runBlocking {
        repo.saveBill(BillEntity("b1", "Rent", 450_000, "housing", due = "2026-10-01", repeat = "monthly", accountId = "pa-bank"))
        assertTrue(repo.payBill("b1", 450_000, today, "pa-bank"))
        val tx = repo.allTxs().single()
        assertEquals("b1", tx.billId); assertEquals(TxType.EXPENSE, tx.type); assertEquals("housing", tx.category)
        val b = db.bills().byId("b1")!!
        assertEquals("2026-11-01", b.due)
        assertTrue(b.paidJson.contains("2026-10-01"))
        assertFalse(b.done)
    }

    @Test fun TC_BILL_02_one_time_bill_is_done_and_cannot_be_paid_twice_for_same_due() = runBlocking {
        repo.saveBill(BillEntity("b2", "Visa fee", 10_000, "other", due = "2026-10-20", repeat = "none"))
        assertTrue(repo.payBill("b2", 10_000, today, "pa-bank"))
        assertTrue(db.bills().byId("b2")!!.done)
        assertFalse(repo.payBill("b2", 10_000, today, "pa-bank"))
        assertEquals(1, repo.allTxs().size)
    }

    @Test fun TC_REC_01_recurring_posts_missed_occurrences() = runBlocking {
        repo.saveRecurring(RecurringEntity("sal", TxType.INCOME, "salary", 1_450_000, "pa-bank", null, "Salary", "2026-08-01", "monthly"))
        val posted = repo.postDueRecurring()
        assertEquals(listOf("2026-08-01", "2026-09-01", "2026-10-01"), posted.map { it.date })
        assertEquals("2026-11-01", db.recurring().byId("sal")!!.nextDate)
    }

    @Test fun TC_REC_02_running_recurring_twice_creates_no_duplicates() = runBlocking {
        repo.saveRecurring(RecurringEntity("rent", TxType.EXPENSE, "housing", 450_000, "pa-bank", null, "Rent", "2026-10-01", "monthly"))
        repo.postDueRecurring()
        // Simulate a crash before nextDate was saved: put the old date back and run again.
        db.recurring().upsert(db.recurring().byId("rent")!!.copy(nextDate = "2026-10-01"))
        assertTrue(repo.postDueRecurring().isEmpty())
        assertEquals(1, repo.allTxs().size)
    }

    @Test fun TC_REC_03_end_date_stops_recurring() = runBlocking {
        repo.saveRecurring(RecurringEntity("sub", TxType.EXPENSE, "subs", 6_500, "pa-card", null, "Netflix", "2026-08-09", "monthly", endDate = "2026-09-30"))
        assertEquals(2, repo.postDueRecurring().size)
        assertFalse(db.recurring().byId("sub")!!.active)
        today = LocalDate.of(2027, 1, 1)
        assertTrue(repo.postDueRecurring().isEmpty())
    }

    @Test fun TC_REC_04_recurring_transfer_keeps_both_accounts() = runBlocking {
        repo.saveRecurring(RecurringEntity("sav", TxType.TRANSFER, "transfer", 100_000, "pa-bank", "pa-cash", "Move to cash", "2026-10-01", "monthly"))
        val t = repo.postDueRecurring().single()
        assertEquals("pa-cash", t.toAccountId)
        assertEquals("transfer", t.category)
    }

    @Test fun TC_GOAL_01_adding_to_goal_reports_milestones_and_optional_expense() = runBlocking {
        repo.saveGoal(GoalEntity("g", "Car", targetMils = 1_000_000, savedMils = 200_000))
        assertEquals(listOf(25, 50), repo.addToGoal("g", 300_000, today, "pa-bank"))
        assertEquals(500_000L, db.goals().byId("g")!!.savedMils)
        assertEquals(300_000L, repo.allTxs().single().amountMils)
        repo.addToGoal("g", -900_000, today, null)
        assertEquals(0L, db.goals().byId("g")!!.savedMils) // never below zero
    }

    @Test fun TC_DATA_01_import_replaces_everything_in_one_step() = runBlocking {
        repo.saveTx(expense("old", 1_000))
        val text = javaClass.classLoader!!.getResource("tracker-backup.json")!!.readText()
        repo.replaceAll(Backup.import(text).snapshot)
        assertNull(repo.tx("old"))
        assertEquals(7, repo.allTxs().size)
        assertEquals(4, db.accounts().all().size)
        assertEquals(3_361_750L, FinanceMath.totalBalance(db.accounts().all(), repo.allTxs()))
    }

    @Test fun TC_DATA_02_flows_emit_updates_after_changes() = runBlocking {
        assertEquals(0, repo.txs.first().size)
        repo.saveTx(expense("t", 1_000))
        assertEquals(1, repo.txs.first().size)
    }

    @Test fun TC_PERSIST_01_data_survives_closing_and_reopening_the_database() = runBlocking {
        val ctx = TestEnv.context()
        ctx.deleteDatabase("persist-test.db")
        val first = AppDatabase.plain(ctx, "persist-test.db")
        val r1 = FinanceRepository(first)
        r1.createDefaultAccounts("KWD")
        r1.saveTx(expense("keep-me", 7_000))
        first.close()
        val second = AppDatabase.plain(ctx, "persist-test.db")
        val got = FinanceRepository(second).tx("keep-me")
        second.close()
        ctx.deleteDatabase("persist-test.db")
        assertEquals(7_000L, got!!.amountMils)
    }
}
