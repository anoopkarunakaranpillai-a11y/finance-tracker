package com.anoop.financetracker.domain

import java.time.LocalDate

/** What the transaction form holds before it is saved. */
data class TxDraft(
    val type: String = TxType.EXPENSE,
    val amountText: String = "",
    val date: String = LocalDate.now().iso(),
    val category: String = "",
    val accountId: String = "",
    val toAccountId: String = "",
    val note: String = "",
    val merchant: String = "",
    val method: String = "",
    val tags: String = "",
)

data class TxValidation(val amountMils: Long?, val errors: Map<String, String>) {
    val ok get() = errors.isEmpty() && amountMils != null
}

object Validation {
    const val MAX_NOTE = 200
    const val MAX_SHORT = 60

    fun validateTx(d: TxDraft, currency: String, accountIds: Set<String>, today: LocalDate = LocalDate.now()): TxValidation {
        val e = LinkedHashMap<String, String>()
        var mils: Long? = null
        when (val p = Money.parsePositive(d.amountText, currency)) {
            is Money.Parse.Ok -> mils = p.mils
            is Money.Parse.Error -> e["amount"] = p.message
        }
        if (d.type !in TxType.ALL) e["type"] = "Choose income, expense or transfer."
        val date = parseDate(d.date)
        if (date == null) e["date"] = "Pick a valid date."
        else if (date.isAfter(today.plusYears(1))) e["date"] = "That date is more than a year ahead."
        else if (date.isBefore(LocalDate.of(1970, 1, 1))) e["date"] = "That date is too far in the past."
        if (d.accountId.isBlank() || d.accountId !in accountIds) e["account"] = if (d.type == TxType.TRANSFER) "Choose the account the money leaves." else "Choose an account."
        if (d.type == TxType.TRANSFER) {
            if (d.toAccountId.isBlank() || d.toAccountId !in accountIds) e["toAccount"] = "Choose the account the money goes to."
            else if (d.toAccountId == d.accountId) e["toAccount"] = "Pick two different accounts."
        } else if (d.category.isBlank()) e["category"] = "Choose a category."
        if (d.note.length > MAX_NOTE) e["note"] = "Keep the note under $MAX_NOTE characters."
        if (d.merchant.length > MAX_SHORT) e["merchant"] = "Keep this under $MAX_SHORT characters."
        return TxValidation(mils, e)
    }

    fun validateName(name: String, what: String): String? = when {
        name.isBlank() -> "Give the $what a name."
        name.length > MAX_SHORT -> "Keep the name under $MAX_SHORT characters."
        else -> null
    }
}
