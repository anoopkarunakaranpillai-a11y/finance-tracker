# Interfaces and data contracts

The app has no network API for finance data: everything is stored on the device. This page describes the interfaces that other code, files or pages rely on.

## 1. Repository (Kotlin, `data/FinanceRepository.kt`)

The repository is the single gateway to the database, and every screen goes through it. All methods are `suspend`. Amounts are `Long` **mils** (1/1000 of the currency unit, so KWD 1.250 = 1250).

| Method | Contract |
|---|---|
| `saveTx(t)` | Upsert by `id`. The editor creates the id when the form opens, so repeated taps and retries update one row. `createdAt` is preserved on edit. |
| `deleteTx(id): Boolean` | `false` if the transaction did not exist. |
| `saveAccount(a)`, `deleteAccount(id): Boolean` | Delete is refused when transactions reference the account. The UI then archives it instead. |
| `setBudget(category, mils?)` | `null` or ≤0 removes the limit. |
| `saveGoal`, `deleteGoal`, `addToGoal(id, mils, date, expenseFrom?)` | `addToGoal` returns the milestones crossed (25/50/75/100). It never drops a goal below 0. |
| `saveBill`, `deleteBill`, `payBill(id, mils, date, from): Boolean` | Records an expense with id `bill-<billId>-<due>`, then advances `due` by the repeat rule or marks the bill done. Paying the same due date twice returns `false`. |
| `saveRecurring`, `deleteRecurring`, `postDueRecurring(): List<TxEntity>` | Posts every due date up to today with id `rec-<id>-<date>` (insert-or-ignore), so running it twice or concurrently is safe. |
| `snapshot(currency)`, `replaceAll(snapshot)` | Used for backup and restore. Replacing runs in one database transaction. |

The pure calculations live in `domain/FinanceMath.kt`. Each function is covered by unit tests:

- balances and net worth
- summary (income, expense, transfers, net, savings rate)
- category totals
- monthly series
- chart buckets
- cash flow
- budget status
- goal milestones

## 2. Backup file format (JSON)

The format is compatible with the Finance Tracker website's "Export backup".

```json
{
  "app": "anoops-finance-tracker",
  "version": 1,
  "source": "android",
  "exported": "2026-10-15T09:00:00Z",
  "state": {
    "money": {
      "v": 4,
      "cur": "KWD",
      "tx": [{"id":"…","date":"2026-10-01","time":"","type":"expense","cat":"groceries","amount":12.5,
              "note":"","acct":"pa-bank","toAcct":null,"merchant":"","method":"Card","tags":["family"],"billId":null}],
      "budgets": {"groceries": 160},
      "goals": [{"id":"…","name":"Emergency fund","type":"savings","target":3000,"saved":1200,"deadline":"","color":"--c-teal","history":[]}],
      "accounts": [{"id":"pa-bank","name":"Bank account","type":"bank","cur":"KWD","opening":0,"d":{}}],
      "bills": [{"id":"…","title":"Rent","amount":450,"cat":"housing","provider":"","due":"2026-11-01","repeat":"monthly","remindDays":3,"acct":"pa-bank","note":"","number":"","paid":[]}]
    }
  },
  "android": {
    "recurring": [{"id":"…","type":"income","cat":"salary","amount":1450,"acct":"pa-bank","next":"2026-11-01","repeat":"monthly","end":"","active":true}],
    "categories": [{"key":"c_school_fees","name":"School fees","emoji":"🏫","kind":"expense"}]
  }
}
```

### Import rules

- These root shapes are accepted: `state.money`, `money`, or a bare `money` object.
- Invalid entries are skipped and counted. This covers a bad date, an amount ≤0 or an unknown type.
- A transaction that points to a missing account recreates that account, so its money is not lost.

### Password-protected backups

They use this wrapper:

```json
{"app":"anoops-finance-tracker","enc":"AES-256-GCM/PBKDF2-SHA256","iter":210000,"salt":"<base64>","iv":"<base64>","data":"<base64 ciphertext>"}
```

## 3. Study pages bridge (JavaScript → Android)

The Study corner opens `https://anoopkarunakaranpillai-a11y.github.io/finance-tracker/?study=navw|arya&app=android`.

With `?study=…`, the website shows only Navamika's or Arya's sections. Every finance and trading area is hidden, and mode switching is locked.

The app injects `window.FTAndroid`, and only acts on calls while the page is on that host:

| JS call | Android action |
|---|---|
| `<a download>` click on a `blob:`/`data:` URL | `saveFile(name, mime, base64)`: saves to Downloads (Android 10+) or opens the share sheet |
| `navigator.share({files})` | `shareFile(name, mime, base64, title)`: Android share sheet |
| `navigator.share({text,url})` | `shareText(title, text)` |
| `window.print()` | `print()`: Android print dialog (Save as PDF or a printer) |
| `speechSynthesis.speak(u)` | `speak(id, text, lang, pitch, rate, volume)`: Android text-to-speech. The page is called back via `__ftTtsDone(id, error)`, which fires `u.onend` or `u.onerror`. |
| `speechSynthesis.cancel()` | `stopSpeaking()` |

## 4. Scheduling

| Job | Where it runs | When |
|---|---|---|
| Daily study content (100+ questions, 4+ tests, Navamika's games) | Scheduled cloud task → GitHub → website | 08:48 Kuwait time, retry 11:48 (the app need not be open) |
| Recurring transactions and reminders | WorkManager `DailyWorker` on the phone | About 09:00 local time daily, and also when the app opens |
