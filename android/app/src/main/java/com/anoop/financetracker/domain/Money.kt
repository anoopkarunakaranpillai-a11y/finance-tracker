package com.anoop.financetracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money is stored as a whole number of "mils" (1/1000 of the currency unit).
 * This keeps Kuwaiti dinar amounts (3 decimals) exact and avoids floating-point drift.
 */
object Money {
    const val SCALE = 1000L

    val CURRENCIES = listOf("KWD", "USD", "INR", "AED", "SAR", "QAR", "BHD", "OMR", "EUR", "GBP")

    private val SYMBOLS = mapOf("USD" to "$", "INR" to "₹", "EUR" to "€", "GBP" to "£")

    fun decimals(currency: String): Int = when (currency) {
        "KWD", "BHD", "OMR", "JOD", "TND" -> 3
        "JPY", "KRW" -> 0
        else -> 2
    }

    fun fromDouble(v: Double): Long = (v * SCALE).roundToLong()
    fun toDouble(mils: Long): Double = mils.toDouble() / SCALE

    /** Formats an amount, e.g. "KWD 1,234.500", "$12.50", "-₹300.00". */
    fun format(mils: Long, currency: String, signed: Boolean = false): String {
        val d = decimals(currency)
        val bd = BigDecimal.valueOf(abs(mils)).divide(BigDecimal.valueOf(SCALE)).setScale(d, RoundingMode.HALF_UP)
        val body = groupThousands(bd.toPlainString())
        val sym = SYMBOLS[currency]
        val core = if (sym != null) sym + body else "$currency $body"
        val isZero = bd.signum() == 0
        return when {
            mils < 0 && !isZero -> "-$core"
            signed && mils > 0 && !isZero -> "+$core"
            else -> core
        }
    }

    /** Short form for chart labels: 1.2K, 3.4M. */
    fun compact(mils: Long, currency: String): String {
        val v = abs(toDouble(mils))
        val sign = if (mils < 0) "-" else ""
        val s = when {
            v >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", v / 1_000_000)
            v >= 10_000 -> String.format(java.util.Locale.US, "%.0fK", v / 1000)
            v >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", v / 1000)
            else -> String.format(java.util.Locale.US, "%.0f", v)
        }
        return sign + (SYMBOLS[currency] ?: "") + s
    }

    private fun groupThousands(plain: String): String {
        val parts = plain.split('.')
        val intPart = parts[0]
        val sb = StringBuilder()
        intPart.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append(',')
            sb.append(c)
        }
        val grouped = sb.reverse().toString()
        return if (parts.size > 1) grouped + "." + parts[1] else grouped
    }

    sealed class Parse {
        data class Ok(val mils: Long) : Parse()
        data class Error(val message: String) : Parse()
    }

    /**
     * Parses user input for a positive amount. Accepts "1,250.5" style input.
     * Rejects empty, zero, negative, non-numeric and too many decimals for the currency.
     */
    fun parsePositive(input: String, currency: String, max: Long = 1_000_000_000L * SCALE): Parse {
        val raw = input.trim().replace(",", "").replace(" ", "")
        if (raw.isEmpty()) return Parse.Error("Enter an amount.")
        if (raw.startsWith("-")) return Parse.Error("Amount can't be negative. Choose Income or Expense instead.")
        if (!Regex("^\\d*\\.?\\d*$").matches(raw) || raw == ".") return Parse.Error("Enter a number, like 12.500.")
        val bd = try { BigDecimal(raw) } catch (e: NumberFormatException) { return Parse.Error("Enter a number, like 12.500.") }
        val d = decimals(currency)
        if (bd.stripTrailingZeros().scale() > d) {
            return Parse.Error(if (d == 0) "$currency has no decimals." else "Use at most $d decimal places for $currency.")
        }
        val mils = bd.multiply(BigDecimal.valueOf(SCALE)).setScale(0, RoundingMode.HALF_UP).toLong()
        if (mils <= 0) return Parse.Error("Amount must be more than zero.")
        if (mils > max) return Parse.Error("That amount is too large.")
        return Parse.Ok(mils)
    }

    /** Plain editable text for an amount, e.g. 12500 mils in KWD -> "12.500". */
    fun toInput(mils: Long, currency: String): String =
        BigDecimal.valueOf(mils).divide(BigDecimal.valueOf(SCALE)).setScale(decimals(currency), RoundingMode.HALF_UP).toPlainString()
}
