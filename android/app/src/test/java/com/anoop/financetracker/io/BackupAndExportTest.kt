package com.anoop.financetracker.io

import com.anoop.financetracker.data.AccountEntity
import com.anoop.financetracker.data.RecurringEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.DateRange
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupAndExportTest {
    private fun fixture() = javaClass.classLoader!!.getResource("tracker-backup.json")!!.readText()

    @Test fun TC_BAK_01_imports_website_tracker_backup() {
        val r = Backup.import(fixture())
        val s = r.snapshot
        assertEquals("KWD", s.currency)
        assertEquals(7, s.txs.size)
        assertEquals(2, r.skipped)               // bad date and negative amount
        assertEquals(1, r.createdAccounts)       // "pa-missing" recreated so nothing is lost
        assertEquals(4, s.accounts.size)
        assertEquals(2, s.budgets.size)
        assertEquals(1, s.goals.size)
        assertEquals(1_200_000L, s.goals[0].savedMils)
        assertEquals(1, s.bills.size)
        assertEquals("2026-10-01", s.bills[0].due)
        val bank = s.accounts.first { it.id == "pa-bank" }
        assertEquals("NBK Salary", bank.name)
        assertTrue(bank.detailsJson.contains("1234"))
        val m3 = s.txs.first { it.id == "m3" }
        assertEquals("family,food", m3.tags)
        assertEquals("09:15", m3.time)
        assertEquals(18_750L, s.txs.first { it.id == "m6" }.amountMils) // amount given as a string
    }

    @Test fun TC_BAK_08_imported_figures_reconcile() {
        val s = Backup.import(fixture()).snapshot
        val b = FinanceMath.balances(s.accounts, s.txs)
        assertEquals(3_319_750L, b["pa-bank"])
        assertEquals(140_000L, b["pa-cash"])
        assertEquals(-87_000L, b["pa-card"])
        assertEquals(3_361_750L, FinanceMath.totalBalance(s.accounts, s.txs))
        val sep = FinanceMath.summary(s.txs, DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        assertEquals(1_450_000L, sep.incomeMils)
        assertEquals(518_250L, sep.expenseMils)
        assertEquals(180_250L, sep.transferMils)
    }

    @Test fun TC_BAK_02_export_then_import_round_trips() {
        val a = Backup.import(fixture()).snapshot.copy(
            recurring = listOf(RecurringEntity("r1", TxType.INCOME, "salary", 1_450_000, "pa-bank", null, "Salary", "2026-11-01", "monthly")),
        )
        val back = Backup.import(Backup.export(a)).snapshot
        assertEquals(a.txs.sortedBy { it.id }.map { listOf(it.id, it.date, it.type, it.category, it.amountMils, it.accountId, it.toAccountId, it.tags, it.note) },
            back.txs.sortedBy { it.id }.map { listOf(it.id, it.date, it.type, it.category, it.amountMils, it.accountId, it.toAccountId, it.tags, it.note) })
        assertEquals(a.accounts.map { it.id to it.openingMils }, back.accounts.map { it.id to it.openingMils })
        assertEquals(a.budgets.toSet(), back.budgets.toSet())
        assertEquals(1, back.recurring.size)
        assertEquals(a.bills[0].paidJson.replace(" ", ""), back.bills[0].paidJson.replace(" ", ""))
    }

    @Test fun TC_BAK_03_password_protected_backup_round_trips() {
        val plain = Backup.export(Backup.import(fixture()).snapshot)
        val enc = Backup.encrypt(plain, "correct horse".toCharArray())
        assertTrue(Backup.isEncrypted(enc))
        assertFalse(enc.contains("NBK Salary"))
        assertEquals(plain, Backup.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test fun TC_BAK_04_wrong_password_is_reported() {
        val enc = Backup.encrypt("{}", "one".toCharArray())
        val e = assertThrows(BackupException::class.java) { Backup.decrypt(enc, "two".toCharArray()) }
        assertTrue(e.message!!.contains("Wrong password"))
        assertThrows(BackupException::class.java) { Backup.import(enc) }
    }

    @Test fun TC_BAK_05_invalid_files_are_rejected_clearly() {
        assertThrows(BackupException::class.java) { Backup.import("this is not json") }
        assertThrows(BackupException::class.java) { Backup.import("[1,2,3]") }
        val e = assertThrows(BackupException::class.java) { Backup.import("""{"app":"other","state":{}}""") }
        assertTrue(e.message!!.contains("No finance data"))
    }

    @Test fun TC_BAK_07_old_data_without_accounts_gets_default_accounts() {
        val r = Backup.import("""{"money":{"cur":"KWD","tx":[{"id":"x","date":"2026-01-02","type":"expense","cat":"fun","amount":3,"method":"Cash"}]}}""")
        assertEquals(listOf("pa-bank", "pa-cash", "pa-card"), r.snapshot.accounts.map { it.id })
        assertEquals("pa-cash", r.snapshot.txs[0].accountId)
    }

    // ---------- CSV ----------
    private val accts = listOf(AccountEntity("a", "Bank, main", "bank", "KWD", 0), AccountEntity("b", "Cash", "cash", "KWD", 0))

    @Test fun TC_EXP_01_csv_has_header_and_one_row_per_transaction() {
        val csv = CsvExport.build(listOf(
            TxEntity("1", "2026-10-01", "", TxType.EXPENSE, "groceries", 12_500, "a", note = "Food"),
            TxEntity("2", "2026-10-02", "", TxType.TRANSFER, "transfer", 5_000, "a", "b"),
        ), accts, "KWD")
        val lines = csv.trim().split("\r\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("\"Date\",\"Time\",\"Type\""))
        assertTrue(lines[1].contains("\"Groceries\",\"12.500\",\"-12.500\",\"KWD\",\"Bank, main\""))
        assertTrue(lines[2].contains("\"Transfer\"") && lines[2].contains("\"Cash\""))
    }

    @Test fun TC_EXP_02_csv_escapes_quotes_commas_and_newlines() {
        val csv = CsvExport.build(listOf(TxEntity("1", "2026-10-01", "", TxType.INCOME, "salary", 1_000, "a", note = "He said \"hi\",\nbye")), accts, "KWD")
        assertTrue(csv.contains("\"He said \"\"hi\"\", bye\""))
    }

    @Test fun TC_EXP_03_csv_neutralises_spreadsheet_formulas() {
        assertEquals("\"'=HYPERLINK(1)\"", CsvExport.cell("=HYPERLINK(1)"))
        assertEquals("\"'@cmd\"", CsvExport.cell("@cmd"))
        assertEquals("\"-12.500\"", CsvExport.cell("-12.500", numeric = true))
    }
}
