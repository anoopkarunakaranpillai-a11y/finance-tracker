package com.anoop.financetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyAndValidationTest {

    private fun ok(s: String, cur: String = "KWD") = (Money.parsePositive(s, cur) as Money.Parse.Ok).mils
    private fun err(s: String, cur: String = "KWD") = (Money.parsePositive(s, cur) as Money.Parse.Error).message

    @Test fun TC_VAL_01_valid_amounts_parse_exactly() {
        assertEquals(12_500L, ok("12.5"))
        assertEquals(1_250_250L, ok("1,250.250"))
        assertEquals(1L, ok("0.001"))
        assertEquals(10_000L, ok("10", "USD"))
        assertEquals(10_990L, ok("10.99", "USD"))
    }

    @Test fun TC_VAL_02_negative_amount_rejected() {
        assertTrue(err("-5").contains("negative"))
    }

    @Test fun TC_VAL_03_zero_and_empty_rejected() {
        assertTrue(err("0").contains("more than zero"))
        assertTrue(err("0.000").contains("more than zero"))
        assertTrue(err("").contains("Enter an amount"))
    }

    @Test fun TC_VAL_04_too_many_decimals_rejected() {
        assertTrue(err("1.2345").contains("3 decimal"))
        assertTrue(err("1.234", "USD").contains("2 decimal"))
        assertEquals(1_230L, ok("1.230", "USD")) // trailing zero is fine
    }

    @Test fun TC_VAL_05_non_numeric_and_huge_rejected() {
        assertTrue(err("abc").contains("number"))
        assertTrue(err("1.2.3").contains("number"))
        assertTrue(err(".").contains("number"))
        assertTrue(err("99999999999").contains("too large"))
    }

    @Test fun TC_FMT_01_currency_formatting() {
        assertEquals("KWD 1,234.500", Money.format(1_234_500, "KWD"))
        assertEquals("-KWD 0.250", Money.format(-250, "KWD"))
        assertEquals("$12.50", Money.format(12_500, "USD"))
        assertEquals("+₹1,000.00", Money.format(1_000_000, "INR", signed = true))
        assertEquals("$0.00", Money.format(-1, "USD")) // rounds to zero, no minus sign
        assertEquals("12.500", Money.toInput(12_500, "KWD"))
    }

    private val accts = setOf("a", "b")
    private val today = LocalDate.of(2026, 10, 15)
    private fun draft(type: String = TxType.EXPENSE, amount: String = "5", cat: String = "groceries", acct: String = "a", to: String = "") =
        TxDraft(type, amount, "2026-10-15", cat, acct, to)

    @Test fun TC_VAL_06_transfer_needs_two_different_accounts() {
        val v = Validation.validateTx(draft(TxType.TRANSFER, cat = "transfer", to = "a"), "KWD", accts, today)
        assertEquals("Pick two different accounts.", v.errors["toAccount"])
        assertTrue(Validation.validateTx(draft(TxType.TRANSFER, cat = "transfer", to = "b"), "KWD", accts, today).ok)
    }

    @Test fun TC_VAL_07_income_and_expense_need_category() {
        assertTrue(Validation.validateTx(draft(cat = ""), "KWD", accts, today).errors.containsKey("category"))
    }

    @Test fun TC_VAL_08_unknown_account_rejected() {
        assertTrue(Validation.validateTx(draft(acct = "zzz"), "KWD", accts, today).errors.containsKey("account"))
    }

    @Test fun TC_VAL_09_valid_draft_passes_with_amount() {
        val v = Validation.validateTx(draft(amount = "7.250"), "KWD", accts, today)
        assertTrue(v.ok)
        assertEquals(7_250L, v.amountMils)
    }

    @Test fun TC_VAL_10_bad_dates_rejected() {
        assertTrue(Validation.validateTx(draft().copy(date = "2026-13-40"), "KWD", accts, today).errors.containsKey("date"))
        assertTrue(Validation.validateTx(draft().copy(date = "2028-01-01"), "KWD", accts, today).errors.containsKey("date"))
        assertFalse(Validation.validateTx(draft().copy(date = "2026-12-31"), "KWD", accts, today).errors.containsKey("date"))
    }

    @Test fun TC_VAL_12_long_text_rejected() {
        assertTrue(Validation.validateTx(draft().copy(note = "x".repeat(201)), "KWD", accts, today).errors.containsKey("note"))
        assertTrue(Validation.validateName("", "account") != null)
    }
}
