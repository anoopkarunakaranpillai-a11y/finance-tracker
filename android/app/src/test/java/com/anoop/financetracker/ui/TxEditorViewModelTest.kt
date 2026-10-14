package com.anoop.financetracker.ui

import com.anoop.financetracker.FakeSettings
import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.FinanceRepository
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.TxType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TxEditorViewModelTest {
    private val accounts = listOf(AccountEntity("a", "Bank", "bank", "KWD", 0), AccountEntity("b", "Cash", "cash", "KWD", 0))
    private lateinit var repo: FinanceRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = mockk(relaxed = true)
        every { repo.accounts } returns flowOf(accounts)
        every { repo.newId() } returns "new-id"
        coEvery { repo.tx(any()) } returns null
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun TC_TX_05_double_tap_on_save_saves_once() = runTest {
        val vm = TxEditorViewModel(repo, FakeSettings(), null, TxType.EXPENSE)
        vm.edit { it.copy(amountText = "4.250", category = "groceries") }
        val first = vm.save()
        val second = vm.save()
        val third = vm.save()
        assertTrue(first); assertFalse(second); assertFalse(third)
        val saved = slot<TxEntity>()
        coVerify(exactly = 1) { repo.saveTx(capture(saved)) }
        assertEquals("new-id", saved.captured.id)
        assertEquals(4_250L, saved.captured.amountMils)
        assertEquals("a", saved.captured.accountId)
        assertTrue(vm.state.value.saved)
    }

    @Test fun TC_VAL_11_invalid_amount_shows_error_and_saves_nothing() = runTest {
        val vm = TxEditorViewModel(repo, FakeSettings(), null, TxType.EXPENSE)
        vm.edit { it.copy(amountText = "-3", category = "groceries") }
        vm.save()
        assertTrue(vm.state.value.errors["amount"]!!.contains("negative"))
        assertFalse(vm.state.value.saved)
        coVerify(exactly = 0) { repo.saveTx(any()) }
        // The user can fix it and save.
        vm.edit { it.copy(amountText = "3") }
        assertFalse(vm.state.value.errors.containsKey("amount"))
        assertTrue(vm.save())
        coVerify(exactly = 1) { repo.saveTx(any()) }
    }

    @Test fun TC_TX_06_editing_loads_existing_values_and_keeps_links() = runTest {
        val existing = TxEntity("e1", "2026-10-01", "08:00", TxType.EXPENSE, "housing", 450_000, "a", note = "Rent", billId = "b1", createdAt = 5)
        coEvery { repo.tx("e1") } returns existing
        val vm = TxEditorViewModel(repo, FakeSettings(), "e1", null)
        val d = vm.state.value.draft
        assertFalse(vm.state.value.isNew)
        assertEquals("450.000", d.amountText)
        assertEquals("housing", d.category)
        vm.edit { it.copy(amountText = "460") }
        vm.save()
        val saved = slot<TxEntity>()
        coVerify { repo.saveTx(capture(saved)) }
        assertEquals("e1", saved.captured.id)
        assertEquals("b1", saved.captured.billId)
        assertEquals("08:00", saved.captured.time)
        assertEquals(460_000L, saved.captured.amountMils)
    }

    @Test fun TC_TX_07_transfer_preset_picks_two_accounts_and_no_category() = runTest {
        val vm = TxEditorViewModel(repo, FakeSettings(), null, TxType.TRANSFER)
        val d = vm.state.value.draft
        assertEquals("a", d.accountId)
        assertEquals("b", d.toAccountId)
        assertEquals("transfer", d.category)
    }
}
