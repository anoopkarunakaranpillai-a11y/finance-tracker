package com.anoop.financetracker.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anoop.financetracker.AppContainer
import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.data.BillEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.CategoryEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.RecurringEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.Category
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.io.Backup
import com.anoop.financetracker.io.BackupException
import com.anoop.financetracker.io.ImportResult
import com.anoop.financetracker.security.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Everything the finance screens show, recomputed whenever the database or settings change. */
data class FinanceState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val accounts: List<AccountEntity> = emptyList(),
    val balances: Map<String, Long> = emptyMap(),
    val txs: List<TxEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val custom: List<Category> = emptyList(),
) {
    val currency get() = settings.currency
    val activeAccounts get() = accounts.filter { !it.archived }
    val totalBalance: Long get() = activeAccounts.sumOf { balances[it.id] ?: 0L }
    fun accountName(id: String) = accounts.firstOrNull { it.id == id }?.name ?: "Unknown account"
}

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

private data class Part1(val s: AppSettings, val a: List<AccountEntity>, val t: List<TxEntity>, val b: List<BudgetEntity>)
private data class Part2(val g: List<GoalEntity>, val bi: List<BillEntity>, val r: List<RecurringEntity>, val c: List<Category>)

class AppViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo

    val state: StateFlow<FinanceState> = combine(
        combine(c.settings.settings, repo.accounts, repo.txs, repo.budgets) { s, a, t, b -> Part1(s, a, t, b) },
        combine(repo.goals, repo.bills, repo.recurring, repo.customCategories) { g, bi, r, cc -> Part2(g, bi, r, cc) },
    ) { p1, p2 ->
        FinanceState(true, p1.s, p1.a, FinanceMath.balances(p1.a, p1.t), p1.t, p1.b, p2.g, p2.bi, p2.r, p2.c)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FinanceState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages
    fun say(m: String, actionLabel: String? = null, action: (() -> Unit)? = null) { _messages.tryEmit(UiMessage(m, actionLabel, action)) }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { say("Something went wrong. Nothing was changed.") }
    }

    init {
        // Catch up on recurring items that fell due while the app was closed.
        io { val n = repo.postDueRecurring().size; if (n > 0) say("Recorded $n recurring transaction${if (n == 1) "" else "s"}.") }
    }

    // ----- transactions -----
    fun deleteTx(id: String, undo: Boolean = true) = io {
        val t = repo.tx(id) ?: return@io
        repo.deleteTx(id)
        lastDeleted = if (undo) t else null
        if (undo) say("Transaction deleted.", "Undo") { undoDelete() } else say("Transaction deleted.")
    }
    private var lastDeleted: TxEntity? = null
    fun undoDelete() = io { lastDeleted?.let { repo.saveTx(it); lastDeleted = null; say("Restored.") } }

    // ----- accounts -----
    fun saveAccount(a: AccountEntity) = io { repo.saveAccount(a); say("Account saved.") }
    fun deleteAccount(id: String, onDone: (Boolean) -> Unit) = io {
        val ok = repo.deleteAccount(id)
        say(if (ok) "Account deleted." else "This account has transactions, so it was archived instead.")
        if (!ok) repo.account(id)?.let { repo.saveAccount(it.copy(archived = true)) }
        onDone(ok)
    }
    fun newId() = repo.newId()

    // ----- budgets, goals, bills, recurring, categories -----
    fun setBudget(cat: String, mils: Long?) = io { repo.setBudget(cat, mils) }
    fun saveGoal(g: GoalEntity) = io { repo.saveGoal(g); say("Goal saved.") }
    fun deleteGoal(id: String) = io { repo.deleteGoal(id); say("Goal deleted.") }
    fun addToGoal(id: String, mils: Long, from: String?) = io {
        val crossed = repo.addToGoal(id, mils, LocalDate.now(), from)
        say(if (crossed.contains(100)) "Goal reached! 🎉" else if (crossed.isNotEmpty()) "${crossed.max()}% of the goal saved." else "Goal updated.")
    }
    fun saveBill(b: BillEntity) = io { repo.saveBill(b); say("Bill saved.") }
    fun deleteBill(id: String) = io { repo.deleteBill(id); say("Bill deleted.") }
    fun payBill(id: String, mils: Long, from: String) = io {
        say(if (repo.payBill(id, mils, LocalDate.now(), from)) "Payment recorded." else "This bill is already paid for that due date.")
    }
    fun saveRecurring(r: RecurringEntity) = io {
        repo.saveRecurring(r)
        val n = repo.postDueRecurring().size
        say(if (n > 0) "Saved, and recorded $n due transaction${if (n == 1) "" else "s"}." else "Recurring item saved.")
    }
    fun deleteRecurring(id: String) = io { repo.deleteRecurring(id); say("Recurring item deleted. Past transactions are kept.") }
    fun saveCategory(cat: CategoryEntity) = io { repo.saveCategory(cat); say("Category saved.") }
    fun deleteCategory(key: String) = io { repo.deleteCategory(key) }

    // ----- settings -----
    fun updateSettings(block: (AppSettings) -> AppSettings) = io { c.settings.update(block) }

    fun setPin(pin: String, biometric: Boolean) = io {
        val h = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        c.settings.update { it.copy(lockEnabled = true, pinHash = h, biometric = biometric) }
        say("App lock is on.")
    }
    fun disableLock() = io { c.settings.update { it.copy(lockEnabled = false, pinHash = "", biometric = false) }; say("App lock is off.") }

    fun startFresh(currency: String) = io {
        c.settings.update { it.copy(currency = currency, welcomeDone = true) }
        repo.createDefaultAccounts(currency)
    }

    // ----- backup & restore -----
    suspend fun exportBackup(uri: Uri, password: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = c.settings.current()
            var text = Backup.export(repo.snapshot(s.currency))
            if (!password.isNullOrEmpty()) text = Backup.encrypt(text, password.toCharArray())
            c.context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: return@withContext false
            c.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
            true
        } catch (e: Exception) { false }
    }

    /** Reads and checks a backup without changing anything. */
    suspend fun readBackup(uri: Uri, password: String?): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val raw = c.context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext Result.failure(BackupException("Couldn't open that file."))
            if (raw.length > 50_000_000) return@withContext Result.failure(BackupException("That file is too large to be a backup."))
            val text = if (Backup.isEncrypted(raw)) {
                if (password.isNullOrEmpty()) return@withContext Result.failure(BackupException("This backup is password-protected. Enter its password."))
                Backup.decrypt(raw, password.toCharArray())
            } else raw
            Result.success(Backup.import(text, c.settings.current().currency))
        } catch (e: BackupException) { Result.failure(e) } catch (e: Exception) { Result.failure(BackupException("Couldn't read that backup.")) }
    }

    fun applyImport(r: ImportResult) = io {
        repo.replaceAll(r.snapshot)
        c.settings.update { it.copy(currency = r.snapshot.currency, welcomeDone = true) }
        repo.postDueRecurring()
        say("Imported: ${r.summary}.")
    }
}
