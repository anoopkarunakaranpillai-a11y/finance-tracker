package com.anoop.financetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anoop.financetracker.data.FinanceRepository
import com.anoop.financetracker.data.SettingsSource
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxDraft
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loading: Boolean = true,
    val id: String = "",
    val isNew: Boolean = true,
    val draft: TxDraft = TxDraft(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val currency: String = "KWD",
    val createdAt: Long = System.currentTimeMillis(),
    val extra: TxEntity? = null,
)

/**
 * Form state for adding or editing a transaction.
 * The row id is fixed when the form opens and saving is blocked while a save is running,
 * so repeated taps can never create duplicate transactions.
 */
class TxEditorViewModel(
    private val repo: FinanceRepository,
    private val settings: SettingsSource,
    private val editId: String?,
    private val presetType: String?,
    private val presetAccount: String? = null,
) : ViewModel() {
    private val _s = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _s

    init {
        viewModelScope.launch {
            val cur = settings.current().currency
            val accounts = repo.accounts.first().filter { !it.archived }
            val existing = editId?.let { repo.tx(it) }
            if (existing != null) {
                _s.value = EditorState(
                    loading = false, id = existing.id, isNew = false, currency = cur, createdAt = existing.createdAt, extra = existing,
                    draft = TxDraft(existing.type, Money.toInput(existing.amountMils, cur), existing.date, existing.category,
                        existing.accountId, existing.toAccountId ?: "", existing.note, existing.merchant, existing.method, existing.tags),
                )
            } else {
                val type = presetType?.takeIf { it in TxType.ALL } ?: TxType.EXPENSE
                val first = presetAccount ?: accounts.firstOrNull()?.id ?: ""
                _s.value = EditorState(
                    loading = false, id = repo.newId(), isNew = true, currency = cur,
                    draft = TxDraft(type = type, accountId = first,
                        toAccountId = if (type == TxType.TRANSFER) accounts.firstOrNull { it.id != first }?.id ?: "" else "",
                        category = if (type == TxType.TRANSFER) "transfer" else ""),
                )
            }
        }
    }

    fun edit(block: (TxDraft) -> TxDraft) = _s.update { s ->
        val d = block(s.draft)
        // Switching type clears a category that belongs to the other type.
        val fixed = if (d.type != s.draft.type) d.copy(category = if (d.type == TxType.TRANSFER) "transfer" else "") else d
        s.copy(draft = fixed, errors = s.errors - changedKeys(s.draft, fixed))
    }

    private fun changedKeys(a: TxDraft, b: TxDraft) = buildSet {
        if (a.amountText != b.amountText) add("amount")
        if (a.category != b.category) add("category")
        if (a.accountId != b.accountId) { add("account"); add("toAccount") }
        if (a.toAccountId != b.toAccountId) add("toAccount")
        if (a.date != b.date) add("date")
        if (a.note != b.note) add("note")
    }

    /**
     * Starts a save. Returns false (and does nothing) when a save is already running or finished,
     * which is what stops double taps from creating two transactions.
     */
    fun save(): Boolean {
        val s = _s.value
        if (s.saving || s.saved || s.loading) return false
        _s.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ids = repo.accounts.first().map { it.id }.toSet()
            val v = Validation.validateTx(s.draft, s.currency, ids)
            if (!v.ok) { _s.update { it.copy(saving = false, errors = v.errors) }; return@launch }
            val d = s.draft
            val base = s.extra
            val t = TxEntity(
                id = s.id, date = d.date, time = base?.time ?: "", type = d.type,
                category = if (d.type == TxType.TRANSFER) "transfer" else d.category,
                amountMils = v.amountMils!!, accountId = d.accountId,
                toAccountId = if (d.type == TxType.TRANSFER) d.toAccountId else null,
                note = d.note.trim(), merchant = d.merchant.trim(), method = d.method,
                tags = d.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                billId = base?.billId, recurringId = base?.recurringId, createdAt = s.createdAt,
            )
            try {
                repo.saveTx(t)
                _s.update { it.copy(saving = false, saved = true, errors = emptyMap()) }
            } catch (e: Exception) {
                _s.update { it.copy(saving = false, errors = mapOf("form" to "Couldn't save. Your entry is still here, so try again.")) }
            }
        }
        return true
    }
}
