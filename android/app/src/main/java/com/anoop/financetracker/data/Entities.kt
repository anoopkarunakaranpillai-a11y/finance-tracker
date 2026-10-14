package com.anoop.financetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Field names mirror the web tracker's state.money (version 4) so a tracker backup
 * imports without loss. Amounts are stored in mils (1/1000 of a currency unit).
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val openingMils: Long,
    /** Extra details from the tracker (bank name, last 4 digits, limit, rate...) as a JSON object. */
    val detailsJson: String = "{}",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("category"), Index("type")],
)
data class TxEntity(
    @PrimaryKey val id: String,
    /** ISO date yyyy-MM-dd. */
    val date: String,
    /** Optional HH:mm. */
    val time: String = "",
    /** income | expense | transfer */
    val type: String,
    val category: String,
    /** Always positive; the type decides the direction. */
    val amountMils: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val note: String = "",
    val merchant: String = "",
    val method: String = "",
    /** Comma-separated tags. */
    val tags: String = "",
    val billId: String? = null,
    val recurringId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val limitMils: Long,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "savings",
    val targetMils: Long,
    val savedMils: Long,
    val deadline: String = "",
    val color: String = "--c-teal",
    /** JSON array of {date, amount} entries, as in the tracker. */
    val historyJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountMils: Long,
    val category: String,
    val provider: String = "",
    /** Next due date yyyy-MM-dd. */
    val due: String,
    val repeat: String = "monthly",
    val remindDays: Int = 3,
    val accountId: String = "",
    val note: String = "",
    val number: String = "",
    /** JSON array of paid dates, as in the tracker. */
    val paidJson: String = "[]",
    val done: Boolean = false,
    val loanAccountId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Recurring income or expense that the app posts automatically on its due dates. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val id: String,
    val type: String,
    val category: String,
    val amountMils: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val note: String = "",
    /** Next date to post, yyyy-MM-dd. */
    val nextDate: String,
    val repeat: String = "monthly",
    /** Optional last date (inclusive), yyyy-MM-dd. */
    val endDate: String = "",
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val key: String,
    val name: String,
    val emoji: String,
    /** income | expense */
    val kind: String,
)
