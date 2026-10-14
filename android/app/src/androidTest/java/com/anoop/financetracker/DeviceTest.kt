package com.anoop.financetracker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on a real device or emulator with the real encrypted database. */
@RunWith(AndroidJUnit4::class)
class DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitTag(t: String) = rule.waitUntil(15_000) { rule.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty() }
    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)

    private fun passWelcome() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("welcome-start").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("total-balance").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithTag("welcome-start").fetchSemanticsNodes().isNotEmpty()) tag("welcome-start").performClick()
        waitTag("total-balance")
    }

    @Test fun TC_INST_01_app_installs_and_launches_to_a_usable_screen() {
        passWelcome()
        tag("fab-add").performClick()
        waitTag("field-amount")
    }

    @Test fun TC_INST_02_add_expense_end_to_end_on_device() {
        passWelcome()
        val note = "device-test-" + System.currentTimeMillis()
        tag("fab-add").performClick(); waitTag("field-amount")
        tag("field-amount").performTextInput("2.500")
        tag("cat-dining").performClick()
        tag("field-note").performScrollTo().performTextInput(note)
        tag("tx-save").performScrollTo().performClick()
        waitTag("total-balance")
        tag("nav-transactions").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText(note).fetchSemanticsNodes().isNotEmpty() }
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApp
        val saved = runBlocking { app.container()!!.repo.allTxs() }.filter { it.note == note }
        assertEquals(1, saved.size)
        assertEquals(2_500L, saved[0].amountMils)
    }

    @Test fun TC_PERSIST_02_data_is_still_there_after_the_activity_is_recreated() {
        passWelcome()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApp
        val before = runBlocking { app.container()!!.repo.allTxs().size }
        rule.activityRule.scenario.recreate()
        waitTag("total-balance")
        assertEquals(before, runBlocking { app.container()!!.repo.allTxs().size })
    }

    @Test fun TC_SEC_04_database_file_on_disk_is_encrypted() {
        passWelcome()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val f: File = ctx.getDatabasePath("finance.db")
        assertTrue(f.exists())
        val head = f.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        // A plain SQLite file starts with "SQLite format 3"; SQLCipher files do not.
        assertFalse(String(head, Charsets.US_ASCII).startsWith("SQLite format 3"))
    }
}
