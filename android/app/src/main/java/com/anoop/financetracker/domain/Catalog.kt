package com.anoop.financetracker.domain

/** Transaction kinds, matching the web tracker's "type" values. */
object TxType {
    const val INCOME = "income"
    const val EXPENSE = "expense"
    const val TRANSFER = "transfer"
    val ALL = listOf(EXPENSE, INCOME, TRANSFER)
    fun label(t: String) = when (t) { INCOME -> "Income"; EXPENSE -> "Expense"; TRANSFER -> "Transfer"; else -> t }
}

data class Category(val key: String, val name: String, val emoji: String, val kind: String, val custom: Boolean = false)

/** Built-in categories, identical keys to the web tracker so backups round-trip. */
object Categories {
    val EXPENSE = listOf(
        Category("housing", "Housing & rent", "🏠", TxType.EXPENSE),
        Category("groceries", "Groceries", "🛒", TxType.EXPENSE),
        Category("dining", "Dining out", "🍽️", TxType.EXPENSE),
        Category("transport", "Transport & fuel", "⛽", TxType.EXPENSE),
        Category("utilities", "Utilities & phone", "💡", TxType.EXPENSE),
        Category("shopping", "Shopping", "🛍️", TxType.EXPENSE),
        Category("health", "Health", "💊", TxType.EXPENSE),
        Category("insurance", "Insurance", "🛡️", TxType.EXPENSE),
        Category("education", "Education", "🎓", TxType.EXPENSE),
        Category("family", "Family & kids", "👨‍👩‍👧", TxType.EXPENSE),
        Category("fun", "Entertainment", "🎬", TxType.EXPENSE),
        Category("travel", "Travel", "✈️", TxType.EXPENSE),
        Category("subs", "Subscriptions", "📺", TxType.EXPENSE),
        Category("loans", "Loans & EMI", "🏦", TxType.EXPENSE),
        Category("care", "Personal care", "💇", TxType.EXPENSE),
        Category("gifts", "Gifts", "🎁", TxType.EXPENSE),
        Category("charity", "Charity", "🤲", TxType.EXPENSE),
        Category("other", "Other", "📦", TxType.EXPENSE),
    )
    val INCOME = listOf(
        Category("salary", "Salary", "💼", TxType.INCOME),
        Category("bonus", "Bonus", "🎉", TxType.INCOME),
        Category("business", "Business / side income", "🏢", TxType.INCOME),
        Category("rental", "Rental income", "🏘️", TxType.INCOME),
        Category("invest", "Investments & dividends", "📊", TxType.INCOME),
        Category("gifts_in", "Gifts received", "🎁", TxType.INCOME),
        Category("refund", "Refunds", "↩️", TxType.INCOME),
        Category("other_inc", "Other income", "➕", TxType.INCOME),
    )
    val TRANSFER = Category("transfer", "Transfer", "⇄", TxType.TRANSFER)

    val METHODS = listOf("Card", "Cash", "Bank transfer", "K-Net", "Apple Pay", "Other")

    fun forKind(kind: String, custom: List<Category>): List<Category> = when (kind) {
        TxType.INCOME -> INCOME + custom.filter { it.kind == TxType.INCOME }
        TxType.EXPENSE -> EXPENSE + custom.filter { it.kind == TxType.EXPENSE }
        else -> listOf(TRANSFER)
    }

    fun find(key: String, custom: List<Category> = emptyList()): Category =
        (EXPENSE + INCOME + custom).firstOrNull { it.key == key }
            ?: if (key == "transfer") TRANSFER else Category(key, key.replaceFirstChar { it.uppercase() }, "🏷️", TxType.EXPENSE, true)
}

data class AccountType(val key: String, val name: String, val emoji: String, val group: String)

object AccountTypes {
    val ALL = listOf(
        AccountType("savings", "Savings", "🐷", "cash"),
        AccountType("checking", "Checking", "🏛️", "cash"),
        AccountType("cash", "Cash", "💵", "cash"),
        AccountType("bank", "Bank account", "🏦", "cash"),
        AccountType("wallet", "Wallet", "👛", "cash"),
        AccountType("card", "Credit card", "💳", "credit"),
        AccountType("loc", "Line of credit", "💰", "credit"),
        AccountType("brokerage", "Brokerage", "📊", "invest"),
        AccountType("invest", "Investments", "🌱", "invest"),
        AccountType("insurance", "Insurance", "🛡️", "invest"),
        AccountType("retire", "Retirement", "🪑", "invest"),
        AccountType("crypto", "Crypto", "₿", "invest"),
        AccountType("loan", "Loan", "🤲", "loan"),
        AccountType("mortgage", "Mortgage", "🏠", "loan"),
        AccountType("person", "Person", "👤", "loan"),
        AccountType("property", "Property", "🏡", "asset"),
        AccountType("other", "Other account", "🏛️", "other"),
    )
    val GROUPS = listOf("cash" to "Cash & bank", "credit" to "Credit", "invest" to "Investments", "loan" to "Loans", "asset" to "Assets", "other" to "Others")

    fun of(key: String): AccountType = ALL.firstOrNull { it.key == key } ?: ALL.first { it.key == "bank" }

    /** Liabilities (credit cards, loans you owe) carry a negative balance in the tracker. */
    fun isLiability(type: String, personDirection: String? = null): Boolean {
        val g = of(type).group
        return g == "credit" || (g == "loan" && !(type == "person" && personDirection == "lent"))
    }
}

object Repeat {
    const val NONE = "none"
    val ALL = listOf("none" to "Does not repeat", "daily" to "Every day", "weekly" to "Every week",
        "monthly" to "Every month", "quarterly" to "Every 3 months", "yearly" to "Every year")
    fun label(k: String) = ALL.firstOrNull { it.first == k }?.second ?: k
}
