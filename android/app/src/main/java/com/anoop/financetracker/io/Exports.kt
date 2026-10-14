package com.anoop.financetracker.io

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.BudgetStatus
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.Category
import com.anoop.financetracker.domain.CategoryTotal
import com.anoop.financetracker.domain.DateRange
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.Summary
import com.anoop.financetracker.domain.TxType
import java.io.OutputStream
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExport {
    val HEADER = listOf("Date", "Time", "Type", "Category", "Amount", "Signed amount", "Currency", "Account", "To account", "Payee", "Method", "Note", "Tags")

    /** One row per transaction. Fields are quoted, and cells that start with = + - @ are neutralised for spreadsheets. */
    fun build(txs: List<TxEntity>, accounts: List<AccountEntity>, currency: String, custom: List<Category> = emptyList()): String {
        val names = accounts.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",") { cell(it) }).append("\r\n")
        for (t in txs) {
            val amt = plain(t.amountMils, currency)
            val signed = when (t.type) { TxType.EXPENSE -> "-$amt"; TxType.INCOME -> amt; else -> "0" }
            val row = listOf(
                t.date, t.time, TxType.label(t.type), Categories.find(t.category, custom).name, amt, signed, currency,
                names[t.accountId] ?: t.accountId, t.toAccountId?.let { names[it] ?: it } ?: "", t.merchant, t.method, t.note, t.tags,
            )
            sb.append(row.joinToString(",") { cell(it, numeric = it == amt || it == signed) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun plain(mils: Long, currency: String) =
        BigDecimal.valueOf(mils).divide(BigDecimal.valueOf(Money.SCALE)).setScale(Money.decimals(currency), java.math.RoundingMode.HALF_UP).toPlainString()

    fun cell(v: String, numeric: Boolean = false): String {
        var s = v.replace("\r", " ").replace("\n", " ")
        if (!numeric && s.isNotEmpty() && s[0] in charArrayOf('=', '+', '-', '@', '\t')) s = "'$s"
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}

/** What a report contains, computed by the Reports screen and written to PDF. */
data class ReportData(
    val title: String,
    val range: DateRange,
    val filterText: String,
    val currency: String,
    val summary: Summary,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    val budget: BudgetStatus?,
    val txs: List<TxEntity>,
    val accounts: List<AccountEntity>,
    val custom: List<Category>,
)

object PdfReport {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 40f

    fun write(r: ReportData, out: OutputStream) {
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(17, 24, 39) }
        val h2 = Paint().apply { textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(17, 24, 39) }
        val body = Paint().apply { textSize = 9.5f; color = Color.rgb(31, 41, 55) }
        val muted = Paint().apply { textSize = 8.5f; color = Color.rgb(107, 114, 128) }
        val line = Paint().apply { color = Color.rgb(229, 231, 235); strokeWidth = 0.8f }
        val accent = Paint().apply { color = Color.rgb(59, 130, 246) }
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val names = r.accounts.associate { it.id to it.name }
        val m = { v: Long -> Money.format(v, r.currency) }

        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f
        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            page.canvas.drawRect(0f, 0f, W.toFloat(), 6f, accent)
            page.canvas.drawText("Anoop's Finance Tracker · ${r.title}", M, H - 22f, muted)
            page.canvas.drawText("Page $pageNo", W - M - muted.measureText("Page $pageNo"), H - 22f, muted)
            y = M + 10f
        }
        fun need(h: Float) { if (y + h > H - 50f) newPage() }
        fun text(s: String, p: Paint, x: Float = M) { page.canvas.drawText(s, x, y, p) }
        fun right(s: String, p: Paint, xr: Float) { page.canvas.drawText(s, xr - p.measureText(s), y, p) }
        fun clip(s: String, p: Paint, w: Float): String {
            if (p.measureText(s) <= w) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t…") > w) t = t.dropLast(1)
            return "$t…"
        }

        newPage()
        text(r.title, title); y += 18f
        text("${r.range.from.format(fmt)} – ${r.range.to.format(fmt)}${if (r.filterText.isNotEmpty()) " · " + r.filterText else ""}", muted); y += 22f

        // Summary
        text("Summary", h2); y += 16f
        val s = r.summary
        val rows = listOf(
            "Income" to m(s.incomeMils), "Expenses" to m(s.expenseMils), "Net savings" to Money.format(s.netMils, r.currency, true),
            "Savings rate" to (s.savingsRate?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "—"),
            "Transfers (not counted above)" to m(s.transferMils), "Transactions" to s.count.toString(),
        )
        rows.forEach { (k, v) -> need(14f); text(k, body); right(v, body, W - M); y += 14f }
        y += 10f

        fun catTable(label: String, list: List<CategoryTotal>) {
            if (list.isEmpty()) return
            need(40f); text(label, h2); y += 16f
            list.forEach { c ->
                need(14f)
                text(Categories.find(c.category, r.custom).name, body)
                right(String.format(Locale.US, "%.1f%%", c.share * 100), muted, W - M - 110f)
                right(m(c.mils), body, W - M); y += 14f
            }
            y += 10f
        }
        catTable("Spending by category", r.expenseByCategory)
        catTable("Income by source", r.incomeByCategory)

        r.budget?.takeIf { it.lines.isNotEmpty() }?.let { b ->
            need(40f); text("Budget performance", h2); y += 16f
            b.lines.forEach { l ->
                need(14f)
                text(Categories.find(l.category, r.custom).name, body)
                right("${m(l.spentMils)} of ${m(l.limitMils)}", body, W - M - 70f)
                right(String.format(Locale.US, "%.0f%%", l.ratio * 100), if (l.ratio >= 1) h2 else body, W - M); y += 14f
            }
            y += 10f
        }

        need(40f); text("Transactions (${r.txs.size})", h2); y += 16f
        val cols = floatArrayOf(M, M + 62f, M + 120f, M + 235f, M + 345f)
        fun header() {
            listOf("Date", "Type", "Category", "Account", "Note").forEachIndexed { i, h -> page.canvas.drawText(h, cols[i], y, muted) }
            right("Amount", muted, W - M); y += 6f; page.canvas.drawLine(M, y, W - M, y, line); y += 11f
        }
        header()
        for (t in r.txs) {
            if (y + 13f > H - 50f) { newPage(); header() }
            page.canvas.drawText(t.date, cols[0], y, body)
            page.canvas.drawText(TxType.label(t.type), cols[1], y, body)
            page.canvas.drawText(clip(Categories.find(t.category, r.custom).name, body, 110f), cols[2], y, body)
            val acct = (names[t.accountId] ?: "") + (t.toAccountId?.let { " → " + (names[it] ?: "") } ?: "")
            page.canvas.drawText(clip(acct, body, 105f), cols[3], y, body)
            page.canvas.drawText(clip(t.note.ifEmpty { t.merchant }, body, W - M - cols[4] - 80f), cols[4], y, body)
            val sign = when (t.type) { TxType.EXPENSE -> -1; TxType.INCOME -> 1; else -> 0 }
            right(if (sign == 0) m(t.amountMils) else Money.format(sign * t.amountMils, r.currency, true), body, W - M)
            y += 13f
        }
        if (r.txs.isEmpty()) { text("No transactions in this period.", muted); y += 14f }

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
