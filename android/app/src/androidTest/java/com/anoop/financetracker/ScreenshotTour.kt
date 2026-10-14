package com.anoop.financetracker

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.BillEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.iso
import com.anoop.financetracker.io.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Not an assertion test: fills the app with a small demo data set and saves screenshots of the main screens
 * (to the app's private files/shots folder), so the CI run can show what each screen looks like on a phone.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun app() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApp
    private fun waitTag(t: String) = rule.waitUntil(15_000) { rule.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(700)
        // A real screen capture, so WebView content (the study pages) is included.
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(app().filesDir, "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun demo(): Snapshot {
        val n = LocalDate.now()
        fun d(monthsBack: Long, day: Int) = n.minusMonths(monthsBack).withDayOfMonth(minOf(day, n.minusMonths(monthsBack).lengthOfMonth())).iso()
        val acc = listOf(
            AccountEntity("pa-bank", "NBK Salary", "bank", "KWD", 2_100_000, """{"inst":"National Bank of Kuwait","last4":"4821"}""", sortOrder = 0),
            AccountEntity("pa-cash", "Cash", "cash", "KWD", 60_000, sortOrder = 1),
            AccountEntity("pa-card", "Visa Platinum", "card", "KWD", -120_000, """{"limit":"1500"}""", sortOrder = 2),
            AccountEntity("pa-sav", "Savings", "savings", "KWD", 3_000_000, sortOrder = 3),
        )
        val tx = ArrayList<TxEntity>()
        var i = 0
        fun t(date: String, type: String, cat: String, mils: Long, acct: String, note: String, to: String? = null, merchant: String = "") {
            if (LocalDate.parse(date).isAfter(n)) return
            tx += TxEntity("demo${i++}", date, "", type, cat, mils, acct, to, note, merchant)
        }
        for (m in 0L..5L) {
            t(d(m, 1), TxType.INCOME, "salary", 1_450_000, "pa-bank", "Monthly salary", merchant = "Employer")
            t(d(m, 1), TxType.EXPENSE, "housing", 450_000, "pa-bank", "Apartment rent", merchant = "Landlord")
            t(d(m, 3), TxType.EXPENSE, "groceries", 38_500 + m * 1_250, "pa-card", "Weekly groceries", merchant = "Lulu Hypermarket")
            t(d(m, 10), TxType.EXPENSE, "groceries", 42_250, "pa-card", "Weekly groceries", merchant = "Sultan Center")
            t(d(m, 6), TxType.EXPENSE, "transport", 14_000, "pa-card", "Fuel", merchant = "KNPC")
            t(d(m, 8), TxType.EXPENSE, "dining", 18_750, "pa-card", "Dinner out", merchant = "Talabat")
            t(d(m, 9), TxType.EXPENSE, "subs", 6_500, "pa-card", "Streaming", merchant = "Netflix")
            t(d(m, 5), TxType.EXPENSE, "utilities", 12_500, "pa-bank", "Mobile plan", merchant = "Zain")
            t(d(m, 4), TxType.TRANSFER, "transfer", 60_000, "pa-bank", "ATM withdrawal", to = "pa-cash")
            t(d(m, 2), TxType.TRANSFER, "transfer", 200_000, "pa-bank", "Monthly saving", to = "pa-sav")
            if (m % 2 == 0L) t(d(m, 16), TxType.INCOME, "business", 150_000, "pa-bank", "Freelance project", merchant = "Client")
            t(d(m, 27), TxType.EXPENSE, "family", 60_000, "pa-bank", "Family support")
        }
        val bills = listOf(
            BillEntity("b1", "Apartment rent", 450_000, "housing", "Landlord", n.plusDays(2).iso(), "monthly", 3, "pa-bank"),
            BillEntity("b2", "Zain mobile", 12_500, "utilities", "Zain", n.plusDays(9).iso(), "monthly", 3, "pa-bank"),
        )
        val goals = listOf(
            GoalEntity("g1", "Emergency fund", targetMils = 5_000_000, savedMils = 3_200_000, deadline = n.plusMonths(8).iso()),
            GoalEntity("g2", "New car", targetMils = 8_000_000, savedMils = 1_500_000),
        )
        val budgets = listOf(BudgetEntity("groceries", 160_000), BudgetEntity("dining", 40_000), BudgetEntity("transport", 30_000), BudgetEntity("subs", 10_000))
        return Snapshot("KWD", acc, tx, budgets, goals, bills)
    }

    @Test fun tour() {
        val c = app().container()!!
        runBlocking {
            c.repo.replaceAll(demo())
            c.settings.update { it.copy(welcomeDone = true, theme = "dark", lockEnabled = false) }
        }
        waitTag("total-balance")
        shot("01-dashboard")
        rule.onNodeWithTag("nav-transactions").performClick(); waitTag("tx-search"); shot("02-transactions")
        rule.onNodeWithTag("fab-add").performClick(); waitTag("field-amount"); shot("03-add-expense")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitTag("nav-plan")
        rule.onNodeWithTag("nav-plan").performClick(); waitTag("plan-Budgets"); shot("04-budgets")
        rule.onNodeWithTag("plan-Goals").performClick(); waitTag("goal-add"); shot("05-goals")
        rule.onNodeWithTag("plan-Bills").performClick(); waitTag("bill-add"); shot("06-bills")
        rule.onNodeWithTag("nav-reports").performClick(); waitTag("period-MONTH"); shot("07-reports")
        rule.onNodeWithTag("nav-settings").performClick(); waitTag("field-currency"); shot("08-settings")
        rule.onNodeWithTag("nav-dashboard").performClick(); waitTag("open-study")
        rule.onNodeWithTag("open-study").performClick(); waitTag("study-arya"); shot("09-study-corner")
        // The study pages load from the website inside the app (the emulator has internet in CI).
        rule.onNodeWithTag("study-navw").performClick(); Thread.sleep(12_000); shot("11-study-navamika")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitTag("study-arya")
        rule.onNodeWithTag("study-arya").performClick(); Thread.sleep(12_000); shot("12-study-arya")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitTag("study-arya")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        runBlocking { c.settings.update { it.copy(theme = "light") } }
        waitTag("total-balance"); shot("10-dashboard-light")
        runBlocking { c.settings.update { it.copy(theme = "dark") } }
    }
}
