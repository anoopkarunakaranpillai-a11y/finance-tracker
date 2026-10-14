package com.anoop.financetracker.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anoop.financetracker.AppContainer
import com.anoop.financetracker.TestEnv
import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.ui.theme.FinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppUiTest {
    @get:Rule val rule = createComposeRule()

    private fun start(settings: AppSettings = AppSettings(welcomeDone = true), seedAccounts: Boolean = true): AppContainer {
        val c = TestEnv.container(settings)
        if (seedAccounts) runBlocking { c.repo.createDefaultAccounts("KWD") }
        rule.setContent { FinanceTheme("dark") { AppRoot(c) } }
        return c
    }

    private fun waitTag(tag: String) = rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String, substring: Boolean = true) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    private fun tag(t: String): SemanticsNodeInteraction = rule.onNodeWithTag(t, useUnmergedTree = true)

    private fun addExpense(amount: String, category: String = "groceries", note: String = "") {
        tag("fab-add").performClick()
        waitTag("field-amount")
        tag("type-expense").performClick()
        tag("field-amount").performTextInput(amount)
        tag("cat-$category").performClick()
        if (note.isNotEmpty()) tag("field-note").performScrollTo().performTextInput(note)
        tag("tx-save").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tx-save").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun TC_UI_01_bottom_navigation_opens_all_five_screens() {
        start()
        waitTag("total-balance")
        tag("nav-transactions").performClick(); waitTag("tx-search")
        tag("nav-plan").performClick(); waitTag("plan-Budgets")
        tag("nav-reports").performClick(); waitTag("period-MONTH")
        tag("nav-settings").performClick(); waitTag("field-currency")
        tag("nav-dashboard").performClick(); waitTag("total-balance")
    }

    @Test fun TC_UI_02_adding_an_expense_updates_dashboard_and_list() {
        val c = start()
        waitTag("total-balance")
        addExpense("12.750", note = "Lulu weekly shop")
        waitText("KWD 12.750")
        rule.onNodeWithTag("kpi-expense").assertIsDisplayed()
        rule.onNodeWithTag("total-balance").assertTextEquals("-KWD 12.750")
        val txs = runBlocking { c.repo.allTxs() }
        assertEquals(1, txs.size)
        assertEquals(12_750L, txs[0].amountMils)
        tag("nav-transactions").performClick()
        waitText("Lulu weekly shop")
    }

    @Test fun TC_UI_03_invalid_amounts_show_errors_and_nothing_is_saved() {
        val c = start()
        waitTag("fab-add")
        tag("fab-add").performClick(); waitTag("field-amount")
        tag("cat-groceries").performClick()
        tag("tx-save").performScrollTo().performClick()
        waitText("Enter an amount.")
        tag("field-amount").performScrollTo().performTextInput("-5")
        tag("tx-save").performScrollTo().performClick()
        waitText("can't be negative")
        assertTrue(runBlocking { c.repo.allTxs() }.isEmpty())
    }

    @Test fun TC_UI_04_transfer_does_not_count_as_income_or_expense() {
        val c = start()
        waitTag("qa-transfer")
        tag("qa-transfer").performClick(); waitTag("field-amount")
        tag("field-amount").performTextInput("60")
        tag("tx-save").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { c.repo.allTxs() }.isNotEmpty() }
        waitTag("total-balance")
        val t = runBlocking { c.repo.allTxs() }.single()
        assertEquals("transfer", t.type)
        assertEquals("pa-cash", t.toAccountId)
        // Net worth stays at zero and the month shows no income or spending.
        rule.onNodeWithTag("total-balance").assertTextEquals("KWD 0.000")
        val m = com.anoop.financetracker.domain.FinanceMath.summary(listOf(t),
            com.anoop.financetracker.domain.DateRange(java.time.LocalDate.MIN, java.time.LocalDate.MAX))
        assertEquals(0L, m.incomeMils); assertEquals(0L, m.expenseMils); assertEquals(60_000L, m.transferMils)
    }

    @Test fun TC_UI_05_delete_asks_for_confirmation_then_removes() {
        val c = start()
        waitTag("fab-add")
        addExpense("3")
        val id = runBlocking { c.repo.allTxs() }.single().id
        tag("nav-transactions").performClick()
        waitTag("tx-$id")
        tag("tx-$id").performClick()
        waitTag("tx-delete")
        tag("tx-delete").performClick()
        waitText("Delete this transaction?")
        assertEquals(1, runBlocking { c.repo.allTxs() }.size) // nothing removed before confirming
        rule.onNodeWithTag("confirm").performClick()
        rule.waitUntil(5_000) { runBlocking { c.repo.allTxs() }.isEmpty() }
    }

    @Test fun TC_UI_06_first_run_start_fresh_creates_default_accounts() {
        val c = start(AppSettings(welcomeDone = false), seedAccounts = false)
        waitTag("welcome-start")
        tag("welcome-start").performClick()
        waitTag("total-balance")
        rule.waitUntil(5_000) { runBlocking { c.repo.snapshot("KWD").accounts.size } == 3 }
    }

    @Test fun TC_UI_07_empty_states_are_helpful() {
        start()
        waitTag("total-balance")
        tag("nav-transactions").performClick()
        waitText("No transactions yet")
        tag("nav-plan").performClick()
        tag("plan-Goals").performClick()
        waitText("No goals yet")
    }

    @Test fun TC_UI_08_navigation_and_main_buttons_have_large_touch_targets() {
        start()
        waitTag("nav-dashboard")
        listOf("nav-dashboard", "nav-transactions", "nav-plan", "nav-reports", "nav-settings", "qa-income", "fab-add").forEach {
            rule.onNodeWithTag(it).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test fun TC_UI_09_budget_can_be_set_and_shows_on_dashboard() {
        val c = start()
        waitTag("nav-plan")
        tag("nav-plan").performClick()
        waitTag("budget-groceries")
        tag("budget-groceries").performClick()
        waitTag("field-budget")
        tag("field-budget").performTextInput("150")
        tag("budget-save").performClick()
        rule.waitUntil(5_000) { runBlocking { c.repo.snapshot("KWD").budgets }.isNotEmpty() }
        tag("nav-dashboard").performClick()
        waitText("KWD 0.000 of KWD 150.000")
    }
}
