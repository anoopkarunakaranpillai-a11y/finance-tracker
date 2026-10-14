package com.anoop.financetracker.io

import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.BillEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.CategoryEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.RecurringEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.parseDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything the finance part of the app stores. */
data class Snapshot(
    val currency: String,
    val accounts: List<AccountEntity>,
    val txs: List<TxEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val bills: List<BillEntity>,
    val recurring: List<RecurringEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
)

data class ImportResult(val snapshot: Snapshot, val skipped: Int, val createdAccounts: Int) {
    val summary: String get() = buildString {
        append("${snapshot.txs.size} transactions, ${snapshot.accounts.size} accounts, ${snapshot.bills.size} bills, ${snapshot.goals.size} goals, ${snapshot.budgets.size} budgets")
        if (skipped > 0) append(" · $skipped invalid entries skipped")
        if (createdAccounts > 0) append(" · $createdAccounts missing accounts recreated")
    }
}

class BackupException(message: String) : Exception(message)

/**
 * Reads and writes backups in the web tracker's format (app "anoops-finance-tracker", state.money version 4),
 * so data exported from the website imports into the phone app.
 */
object Backup {
    const val APP = "anoops-finance-tracker"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------- export ----------

    fun export(s: Snapshot, now: Instant = Instant.now()): String {
        val money = buildJsonObject {
            put("v", 4)
            put("cur", s.currency)
            put("tx", buildJsonArray { s.txs.forEach { add(txJson(it)) } })
            put("budgets", buildJsonObject { s.budgets.forEach { put(it.category, Money.toDouble(it.limitMils)) } })
            put("goals", buildJsonArray { s.goals.forEach { g ->
                add(buildJsonObject {
                    put("id", g.id); put("name", g.name); put("type", g.type)
                    put("target", Money.toDouble(g.targetMils)); put("saved", Money.toDouble(g.savedMils))
                    put("deadline", g.deadline); put("color", g.color)
                    put("history", parseOr(g.historyJson, JsonArray(emptyList())))
                })
            } })
            put("accounts", buildJsonArray { s.accounts.forEach { a ->
                add(buildJsonObject {
                    put("id", a.id); put("name", a.name); put("type", a.type); put("cur", a.currency)
                    put("opening", Money.toDouble(a.openingMils))
                    put("d", parseOr(a.detailsJson, JsonObject(emptyMap())))
                    if (a.archived) put("archived", true)
                })
            } })
            put("bills", buildJsonArray { s.bills.forEach { b ->
                add(buildJsonObject {
                    put("id", b.id); put("title", b.title); put("amount", Money.toDouble(b.amountMils)); put("cat", b.category)
                    put("provider", b.provider); put("due", b.due); put("repeat", b.repeat); put("remindDays", b.remindDays)
                    put("acct", b.accountId); put("note", b.note); put("number", b.number)
                    put("paid", parseOr(b.paidJson, JsonArray(emptyList())))
                    if (b.done) put("done", true)
                    b.loanAccountId?.let { put("loan", it) }
                })
            } })
        }
        val root = buildJsonObject {
            put("app", APP)
            put("version", 1)
            put("source", "android")
            put("exported", now.toString())
            put("state", buildJsonObject { put("money", money) })
            put("android", buildJsonObject {
                put("recurring", buildJsonArray { s.recurring.forEach { r ->
                    add(buildJsonObject {
                        put("id", r.id); put("type", r.type); put("cat", r.category); put("amount", Money.toDouble(r.amountMils))
                        put("acct", r.accountId); r.toAccountId?.let { put("toAcct", it) }; put("note", r.note)
                        put("next", r.nextDate); put("repeat", r.repeat); put("end", r.endDate); put("active", r.active)
                    })
                } })
                put("categories", buildJsonArray { s.categories.forEach { c ->
                    add(buildJsonObject { put("key", c.key); put("name", c.name); put("emoji", c.emoji); put("kind", c.kind) })
                } })
            })
        }
        return root.toString()
    }

    private fun txJson(t: TxEntity) = buildJsonObject {
        put("id", t.id); put("date", t.date); put("time", t.time); put("type", t.type); put("cat", t.category)
        put("amount", Money.toDouble(t.amountMils)); put("note", t.note); put("acct", t.accountId)
        t.toAccountId?.let { put("toAcct", it) }
        if (t.merchant.isNotEmpty()) put("merchant", t.merchant)
        if (t.method.isNotEmpty()) put("method", t.method)
        if (t.tags.isNotEmpty()) put("tags", buildJsonArray { t.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
        t.billId?.let { put("billId", it) }
        t.recurringId?.let { put("recurringId", it) }
    }

    private fun parseOr(s: String, fallback: JsonElement): JsonElement = try { json.parseToJsonElement(s) } catch (e: Exception) { fallback }

    // ---------- import ----------

    fun isEncrypted(text: String): Boolean = try {
        val o = json.parseToJsonElement(text).jsonObject
        o["enc"] != null && o["data"] != null
    } catch (e: Exception) { false }

    fun import(text: String, defaultCurrency: String = "KWD"): ImportResult {
        val root = try { json.parseToJsonElement(text) } catch (e: Exception) { throw BackupException("This file isn't a valid backup (not JSON).") }
        val obj = root as? JsonObject ?: throw BackupException("This file isn't a tracker backup.")
        if (obj["enc"] != null) throw BackupException("This backup is password-protected.")
        val money: JsonObject = (obj.obj("state")?.obj("money")) ?: obj.obj("money")
            ?: (if (obj["tx"] != null || obj["accounts"] != null) obj else null)
            ?: throw BackupException("No finance data found in this file. Export a backup from the tracker's Settings first.")

        val cur = money.str("cur").ifBlank { defaultCurrency }.uppercase()
        var skipped = 0

        val accounts = LinkedHashMap<String, AccountEntity>()
        money.arr("accounts").forEachIndexed { i, el ->
            val a = el as? JsonObject ?: run { skipped++; return@forEachIndexed }
            val id = a.str("id").ifBlank { "acct$i" }
            accounts[id] = AccountEntity(
                id = id, name = a.str("name").ifBlank { "Account ${i + 1}" }.take(60), type = a.str("type").ifBlank { "bank" },
                currency = a.str("cur").ifBlank { cur }, openingMils = Money.fromDouble(a.num("opening") ?: 0.0),
                detailsJson = (a["d"] as? JsonObject)?.toString() ?: "{}", archived = a.bool("archived"), sortOrder = i,
            )
        }
        // Old tracker data without accounts: same defaults the web app creates.
        val methodMap = mapOf("Bank transfer" to "pa-bank", "K-Net" to "pa-bank", "Cash" to "pa-cash", "Card" to "pa-card", "Apple Pay" to "pa-card")
        if (accounts.isEmpty()) {
            listOf(Triple("pa-bank", "Bank account", "bank"), Triple("pa-cash", "Cash", "cash"), Triple("pa-card", "Credit card", "card"))
                .forEachIndexed { i, (id, n, t) -> accounts[id] = AccountEntity(id, n, t, cur, 0, sortOrder = i) }
        }

        val txs = ArrayList<TxEntity>()
        val seen = HashSet<String>()
        var created = 0
        money.arr("tx").forEachIndexed { i, el ->
            val t = el as? JsonObject ?: run { skipped++; return@forEachIndexed }
            val type = t.str("type")
            val amount = t.num("amount")
            val date = t.str("date")
            if (type !in TxType.ALL || amount == null || amount <= 0.0 || parseDate(date) == null) { skipped++; return@forEachIndexed }
            var id = t.str("id").ifBlank { "imp$i" }
            if (!seen.add(id)) { id = "$id-$i"; seen.add(id) }
            var acct = t.str("acct").ifBlank { methodMap[t.str("method")] ?: accounts.keys.first() }
            if (acct !in accounts) { accounts[acct] = AccountEntity(acct, "Imported account ${accounts.size + 1}", "bank", cur, 0, sortOrder = accounts.size); created++ }
            var to: String? = t.str("toAcct").ifBlank { null }
            if (type == TxType.TRANSFER) {
                if (to == null) { skipped++; return@forEachIndexed }
                if (to !in accounts) { accounts[to] = AccountEntity(to, "Imported account ${accounts.size + 1}", "bank", cur, 0, sortOrder = accounts.size); created++ }
            } else to = null
            val tags = (t["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.joinToString(",") ?: t.str("tags")
            txs += TxEntity(
                id = id, date = date, time = t.str("time"), type = type,
                category = t.str("cat").ifBlank { if (type == TxType.INCOME) "other_inc" else if (type == TxType.TRANSFER) "transfer" else "other" },
                amountMils = Money.fromDouble(amount), accountId = acct, toAccountId = to,
                note = t.str("note").take(200), merchant = t.str("merchant").take(60), method = t.str("method"),
                tags = tags, billId = t.str("billId").ifBlank { null }, recurringId = t.str("recurringId").ifBlank { null },
            )
        }

        val budgets = (money["budgets"] as? JsonObject)?.mapNotNull { (k, v) ->
            val n = (v as? JsonPrimitive)?.doubleOrNull ?: (v as? JsonPrimitive)?.content?.toDoubleOrNull()
            if (n != null && n > 0) BudgetEntity(k, Money.fromDouble(n)) else null
        } ?: emptyList()

        val goals = money.arr("goals").mapIndexedNotNull { i, el ->
            val g = el as? JsonObject ?: run { skipped++; return@mapIndexedNotNull null }
            val target = g.num("target") ?: 0.0
            if (g.str("name").isBlank() || target <= 0) { skipped++; return@mapIndexedNotNull null }
            GoalEntity(
                id = g.str("id").ifBlank { "g$i" }, name = g.str("name").take(60), type = g.str("type").ifBlank { "savings" },
                targetMils = Money.fromDouble(target), savedMils = Money.fromDouble(g.num("saved") ?: 0.0),
                deadline = g.str("deadline"), color = g.str("color").ifBlank { "--c-teal" },
                historyJson = (g["history"] as? JsonArray)?.toString() ?: "[]",
            )
        }

        val bills = money.arr("bills").mapIndexedNotNull { i, el ->
            val b = el as? JsonObject ?: run { skipped++; return@mapIndexedNotNull null }
            if (parseDate(b.str("due")) == null) { skipped++; return@mapIndexedNotNull null }
            BillEntity(
                id = b.str("id").ifBlank { "b$i" }, title = b.str("title").ifBlank { "Bill" }.take(60),
                amountMils = Money.fromDouble(b.num("amount") ?: 0.0), category = b.str("cat").ifBlank { "utilities" },
                provider = b.str("provider"), due = b.str("due"), repeat = b.str("repeat").ifBlank { "none" },
                remindDays = (b.num("remindDays") ?: 3.0).toInt(), accountId = b.str("acct"), note = b.str("note"),
                number = b.str("number"), paidJson = (b["paid"] as? JsonArray)?.toString() ?: "[]",
                done = b.bool("done"), loanAccountId = b.str("loan").ifBlank { null },
            )
        }

        val android = obj.obj("android")
        val recurring = android?.arr("recurring")?.mapNotNull { el ->
            val r = el as? JsonObject ?: return@mapNotNull null
            val amt = r.num("amount") ?: return@mapNotNull null
            if (amt <= 0 || parseDate(r.str("next")) == null) return@mapNotNull null
            RecurringEntity(
                id = r.str("id"), type = r.str("type"), category = r.str("cat"), amountMils = Money.fromDouble(amt),
                accountId = r.str("acct"), toAccountId = r.str("toAcct").ifBlank { null }, note = r.str("note"),
                nextDate = r.str("next"), repeat = r.str("repeat").ifBlank { "monthly" }, endDate = r.str("end"),
                active = r["active"]?.let { (it as? JsonPrimitive)?.content == "true" } ?: true,
            )
        }?.filter { it.id.isNotBlank() && it.accountId in accounts } ?: emptyList()
        val cats = android?.arr("categories")?.mapNotNull { el ->
            val c = el as? JsonObject ?: return@mapNotNull null
            if (c.str("key").isBlank()) null else CategoryEntity(c.str("key"), c.str("name"), c.str("emoji").ifBlank { "🏷️" }, c.str("kind").ifBlank { TxType.EXPENSE })
        } ?: emptyList()

        return ImportResult(Snapshot(cur, accounts.values.toList(), txs, budgets, goals, bills, recurring, cats), skipped, created)
    }

    // ---------- password protection ----------

    private const val ITER = 210_000

    fun encrypt(plain: String, password: CharArray): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt, ITER), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val e = Base64.getEncoder()
        return buildJsonObject {
            put("app", APP); put("enc", "AES-256-GCM/PBKDF2-SHA256"); put("iter", ITER)
            put("salt", e.encodeToString(salt)); put("iv", e.encodeToString(iv)); put("data", e.encodeToString(ct))
        }.toString()
    }

    fun decrypt(text: String, password: CharArray): String {
        val o = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { throw BackupException("This file isn't a valid backup.") }
        val d = Base64.getDecoder()
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, d.decode(o.str("salt")), (o.num("iter") ?: ITER.toDouble()).toInt()), GCMParameterSpec(128, d.decode(o.str("iv"))))
            String(c.doFinal(d.decode(o.str("data"))), Charsets.UTF_8)
        } catch (e: Exception) {
            throw BackupException("Wrong password, or the file is damaged.")
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, iter, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    // ---------- small JSON helpers ----------
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String): List<JsonElement> = (this[k] as? JsonArray) ?: emptyList()
    private fun JsonObject.str(k: String): String = when (val v = this[k]) {
        null, is JsonNull -> ""
        is JsonPrimitive -> v.content
        else -> ""
    }
    private fun JsonObject.num(k: String): Double? = when (val v = this[k]) {
        is JsonPrimitive -> v.doubleOrNull ?: v.content.replace(",", "").toDoubleOrNull()
        else -> null
    }
    private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.content == "true"
}
