package com.anoop.financetracker.data

import androidx.room.withTransaction
import com.anoop.financetracker.domain.Category
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.Recurrence
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.iso
import com.anoop.financetracker.domain.parseDate
import com.anoop.financetracker.io.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.UUID

/** Single source of truth for finance data. All writes go through here. */
class FinanceRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    val accounts: Flow<List<AccountEntity>> = db.accounts().observeAll()
    val txs: Flow<List<TxEntity>> = db.tx().observeAll()
    val budgets: Flow<List<BudgetEntity>> = db.budgets().observeAll()
    val goals: Flow<List<GoalEntity>> = db.goals().observeAll()
    val bills: Flow<List<BillEntity>> = db.bills().observeAll()
    val recurring: Flow<List<RecurringEntity>> = db.recurring().observeAll()
    val customCategories: Flow<List<Category>> = db.categories().observeAll().map { l -> l.map { Category(it.key, it.name, it.emoji, it.kind, true) } }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    // ----- transactions -----

    /**
     * Saves a transaction. The id is created when the form opens, so a double tap or a retried save
     * writes the same row again instead of creating a duplicate.
     */
    suspend fun saveTx(t: TxEntity) {
        val existing = db.tx().byId(t.id)
        db.tx().upsert(t.copy(createdAt = existing?.createdAt ?: t.createdAt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun tx(id: String) = db.tx().byId(id)
    suspend fun deleteTx(id: String): Boolean = db.tx().deleteById(id) > 0
    suspend fun allTxs() = db.tx().all()

    // ----- accounts -----

    suspend fun saveAccount(a: AccountEntity) = db.accounts().upsert(a.copy(updatedAt = System.currentTimeMillis()))
    suspend fun account(id: String) = db.accounts().byId(id)

    /** Accounts that have transactions can't be deleted (that would corrupt balances); archive them instead. */
    suspend fun deleteAccount(id: String): Boolean {
        if (db.tx().countForAccount(id) > 0) return false
        val a = db.accounts().byId(id) ?: return false
        db.accounts().delete(a)
        return true
    }

    suspend fun createDefaultAccounts(currency: String) {
        if (db.accounts().all().isNotEmpty()) return
        db.accounts().upsertAll(listOf(
            AccountEntity("pa-bank", "Bank account", "bank", currency, 0, sortOrder = 0),
            AccountEntity("pa-cash", "Cash", "cash", currency, 0, sortOrder = 1),
            AccountEntity("pa-card", "Credit card", "card", currency, 0, sortOrder = 2),
        ))
    }

    // ----- budgets & categories -----

    suspend fun setBudget(category: String, limitMils: Long?) {
        if (limitMils == null || limitMils <= 0) db.budgets().delete(category) else db.budgets().upsert(BudgetEntity(category, limitMils))
    }

    suspend fun saveCategory(c: CategoryEntity) = db.categories().upsert(c)
    suspend fun deleteCategory(key: String) = db.categories().delete(key)

    // ----- goals -----

    suspend fun saveGoal(g: GoalEntity) = db.goals().upsert(g.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteGoal(id: String) = db.goals().delete(id)

    /**
     * Adds (or with a negative amount, withdraws) money to a goal. Optionally records it as an expense from an account.
     * Returns the milestones crossed (25/50/75/100).
     */
    suspend fun addToGoal(id: String, mils: Long, date: LocalDate, expenseFrom: String?): List<Int> {
        var crossed = emptyList<Int>()
        db.withTransaction {
            val g = db.goals().byId(id) ?: return@withTransaction
            val after = (g.savedMils + mils).coerceAtLeast(0)
            crossed = FinanceMath.goalMilestonesCrossed(g.targetMils, g.savedMils, after)
            val hist = try { Json.parseToJsonElement(g.historyJson) as JsonArray } catch (e: Exception) { JsonArray(emptyList()) }
            val entry = buildJsonObject { put("date", date.iso()); put("amount", mils / 1000.0) }
            db.goals().upsert(g.copy(savedMils = after, historyJson = JsonArray(hist + entry).toString(), updatedAt = System.currentTimeMillis()))
            if (expenseFrom != null && mils > 0) {
                db.tx().upsert(TxEntity(newId(), date.iso(), "", TxType.EXPENSE, "other", mils, expenseFrom, note = "Saved to goal: ${g.name}"))
            }
        }
        return crossed
    }

    // ----- bills -----

    suspend fun saveBill(b: BillEntity) = db.bills().upsert(b.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteBill(id: String) = db.bills().delete(id)

    /**
     * Records a bill payment as an expense and moves the bill to its next due date (or marks it done).
     * The transaction id is derived from the bill and due date, so paying twice cannot double-count.
     */
    suspend fun payBill(id: String, amountMils: Long, paidOn: LocalDate, fromAccount: String): Boolean {
        var ok = false
        db.withTransaction {
            val b = db.bills().byId(id) ?: return@withTransaction
            val txId = "bill-${b.id}-${b.due}"
            if (db.tx().byId(txId) != null) return@withTransaction
            db.tx().upsert(TxEntity(txId, paidOn.iso(), "", TxType.EXPENSE, b.category, amountMils, fromAccount,
                note = b.title, merchant = b.provider, billId = b.id))
            val paid = try { Json.parseToJsonElement(b.paidJson) as JsonArray } catch (e: Exception) { JsonArray(emptyList()) }
            val next = parseDate(b.due)?.let { Recurrence.next(it, b.repeat) }
            db.bills().upsert(b.copy(
                paidJson = JsonArray(paid + JsonPrimitive(b.due)).toString(),
                due = next?.iso() ?: b.due, done = next == null, updatedAt = System.currentTimeMillis(),
            ))
            ok = true
        }
        return ok
    }

    // ----- recurring -----

    suspend fun saveRecurring(r: RecurringEntity) = db.recurring().upsert(r.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteRecurring(id: String) = db.recurring().delete(id)

    /**
     * Posts every due occurrence of active recurring items up to today. Each posted transaction id is
     * "rec-<item>-<date>", so running this twice (or from two triggers at once) never creates duplicates.
     */
    suspend fun postDueRecurring(): List<TxEntity> {
        val posted = ArrayList<TxEntity>()
        val now = today()
        db.withTransaction {
            val accountIds = db.accounts().all().map { it.id }.toSet()
            for (r in db.recurring().all()) {
                if (!r.active) continue
                val start = parseDate(r.nextDate) ?: continue
                if (r.accountId !in accountIds) continue
                val end = parseDate(r.endDate)
                val dates = Recurrence.occurrences(start, r.repeat, now, end)
                if (dates.isEmpty()) continue
                for (d in dates) {
                    val t = TxEntity("rec-${r.id}-${d.iso()}", d.iso(), "", r.type,
                        if (r.type == TxType.TRANSFER) "transfer" else r.category, r.amountMils, r.accountId,
                        toAccountId = if (r.type == TxType.TRANSFER) r.toAccountId else null, note = r.note, recurringId = r.id)
                    if (db.tx().insertIgnore(t) != -1L) posted += t
                }
                val next = Recurrence.next(dates.last(), r.repeat)
                val finished = next == null || (end != null && next.isAfter(end))
                db.recurring().upsert(r.copy(nextDate = next?.iso() ?: r.nextDate, active = !finished, updatedAt = System.currentTimeMillis()))
            }
        }
        return posted
    }

    // ----- whole-database operations -----

    suspend fun snapshot(currency: String) = Snapshot(
        currency, db.accounts().all(), db.tx().all(), db.budgets().all(), db.goals().all(),
        db.bills().all(), db.recurring().all(), db.categories().all(),
    )

    /** Replaces all finance data in one transaction (used by restore and tracker import). Nothing is lost on failure. */
    suspend fun replaceAll(s: Snapshot) {
        db.withTransaction {
            db.tx().clear(); db.accounts().clear(); db.budgets().clear(); db.goals().clear()
            db.bills().clear(); db.recurring().clear(); db.categories().clear()
            db.accounts().upsertAll(s.accounts); db.tx().upsertAll(s.txs); db.budgets().upsertAll(s.budgets)
            db.goals().upsertAll(s.goals); db.bills().upsertAll(s.bills); db.recurring().upsertAll(s.recurring)
            db.categories().upsertAll(s.categories)
        }
    }

    suspend fun isEmpty() = db.accounts().all().isEmpty() && db.tx().count() == 0
}
