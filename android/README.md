# Anoop's Finance for Android

This is a native Android app for **Anoop's Finance Tracker**. It is written in Kotlin with Jetpack Compose and Material 3, and it uses the tracker's dark theme (`#0b0e14`, `#151a23` and `#3b82f6`). A light theme is also available.

Your finance data stays on the phone in an encrypted database. The children's study pages open in a separate Study corner.

- **Download:** the [android-latest release](https://github.com/anoopkarunakaranpillai-a11y/finance-tracker/releases/tag/android-latest). Use `app-debug.apk` to install on a phone.
- **Docs:**
  - [Inventory & plan](docs/PLAN.md)
  - [Interfaces & data formats](docs/API.md)
  - [Build, signing & Google Play](docs/RELEASE.md)
  - [Test register & report](docs/TEST_REGISTER.md)

## Features

| Area | What you can do |
|---|---|
| Dashboard | See your total balance (net worth after cards and loans), plus this month's income, expenses and net savings with the savings rate. It also shows:<br>• budget used<br>• a 6-month income vs expense chart and a spending donut<br>• savings goals and bills due soon<br>• accounts and recent transactions<br>Quick buttons add income, an expense or a transfer. |
| Transactions | Add, edit, view and delete income, expenses and transfers. Each one has an amount, date, category, account, payee, payment method, notes and tags. You can also:<br>• search, filter by type, account or category, and sort<br>• undo a delete<br>Repeated taps never create duplicates. |
| Accounts | 17 account types in 6 groups, the same as the website. You can:<br>• set an opening balance (or the amount you owe)<br>• see each account's history and totals<br>• archive an account instead of deleting it |
| Budgets | Monthly limits per category with progress bars. Limits warn at 80% and 100%. |
| Goals | Track a target, the amount saved and a target date with a suggested monthly amount. Add or withdraw money, optionally as an expense from an account. |
| Bills | Due dates, repeats and reminders. "Mark paid" records the expense and moves the bill to its next due date. |
| Recurring | Salary, rent and subscriptions are posted automatically on their dates. Missed dates are caught up, and nothing is ever posted twice. |
| Reports | View by day, week, month, year or a custom range, filtered by account and category. Reports show:<br>• income vs expenses and cash flow<br>• category breakdown and income by source<br>• savings rate and budget performance<br>Save or share as **PDF** or **CSV**. |
| Notifications | Bills, recurring posts, budget limits, savings milestones and a monthly summary. Each one has its own switch. The notification permission is asked for only when you switch one on. |
| Security | Data is stored only on this device:<br>• the database is encrypted with SQLCipher, and its key is protected by the Android Keystore<br>• optional PIN app lock with fingerprint or face unlock and auto-lock<br>• screenshots are blocked while the lock is on<br>• cloud backup is switched off<br>• HTTPS only |
| Backup | Encrypted backups (password, AES-256-GCM) or plain JSON, saved where you choose. Restore or import the website tracker's JSON backup. |
| Study corner | Navamika's KG 1 workbook, games and Story Time, and Arya's PSC mock tests. These are the website's own pages in a locked study-only mode. Downloads, share, print and read-aloud all work in the app. Daily content still comes from the 9 AM server schedule. |

## Project layout

```
android/
  app/src/main/java/com/anoop/financetracker/
    data/      Room entities, DAOs, database (SQLCipher), repository, settings (DataStore)
    domain/    Money (exact mils), categories, account types, FinanceMath, validation
    io/        Backup import/export + encryption, CSV, PDF report, share helpers
    security/  PIN hashing (PBKDF2), Keystore-protected database key
    work/      Daily WorkManager job, reminder planner, notifications
    study/     Study corner WebView screens and the JavaScript bridge
    ui/        Theme, charts, shared components, ViewModels, screens
  app/src/test/         Unit, repository (Robolectric + Room) and Compose UI tests
  app/src/androidTest/  On-device tests and the screenshot tour
```

The app uses an MVVM structure:

- `AppViewModel` combines the repository flows into one `FinanceState`.
- `TxEditorViewModel` holds the transaction form and its save guard.
- All maths is in pure Kotlin (`FinanceMath`), so it is covered by fast unit tests.

## Build

See [docs/RELEASE.md](docs/RELEASE.md). In short, open `android/` in Android Studio, or run `./gradlew assembleDebug`. CI builds every push and publishes the APK and AAB.

## Known limitations

- **No server or sync.** The brief describes a Spring Boot backend, but none exists in the project. Data lives on one phone. To move it to another phone, use backup and restore (see `docs/PLAN.md`).
- **Single currency for amounts.** The currency setting changes the display only. Per-account currencies from the website are kept but are not converted.
- **Wrong PIN attempts.** The count resets if the app is force-closed.
- **Study corner needs internet once.** The service worker then keeps the pages for offline use.
- **Release signing.** It needs your upload key. Until then the release APK and AAB are unsigned (see RELEASE.md).
