# Feature inventory and integration plan

## 1. What exists today (inspected 15 Oct 2026)

| Item in the brief | What the project actually has |
|---|---|
| Java 17 / Spring Boot 3.1.5 backend (`com.anoop:finance-tracker:1.0.0`) | **Not present.** The repository and workspace contain no Java, Gradle/Maven build, REST API or server database. |
| HTML/CSS/JS prototype | A single-page web app (`index.html`, about 1.5 MB). It is also published as a Claude artifact and on GitHub Pages. |
| Database | The browser's `localStorage` (key `pipledger.v1`). The Claude artifact copy also uses the artifact's private per-user storage. There is no shared server database. |
| Dark theme `#0b0e14` / `#151a23` / `#3b82f6` | Reused as the Android app's default dark theme. |

### Finance features in the web app (Personal mode)

The data model is `state.money`, version 4:

- `cur`: the home currency (KWD by default).
- `tx[]`: transactions with these fields:
  - `id`, `date`, `time`
  - `type`: income, expense or transfer
  - `cat`, `amount`, `note`
  - `acct`, `toAcct`
  - `merchant`, `method`, `tags[]`, `billId`
- `accounts[]`: `id`, `name`, `type` (17 types in 6 groups), `cur`, `opening`, and `d` (details).
- `budgets{category: monthlyLimit}`
- `goals[]`: `id`, `name`, `type`, `target`, `saved`, `deadline`, `color`, `history[]`.
- `bills[]`:
  - `id`, `title`, `amount`, `cat`, `provider`, `due`
  - `repeat`: none, daily, weekly, monthly, quarterly or yearly
  - `remindDays`, `acct`, `paid[]`, `done`, `loan`

Business rules carried over unchanged:

- Balance = opening + income − expense − transfers out + transfers in. Liabilities (cards, loans) hold negative balances.
- Transfers never count as income or expense.
- Budgets are monthly limits per expense category.
- Bill repeats follow the tracker's `addPeriod()`: same day of month, clamped to the month's length.
- Paying a bill records an expense with `billId` and moves the bill to its next due date. A bill that does not repeat is marked done.
- 18 expense categories and 8 income categories, with the same keys as the web app.

The web app also has a trading journal, net worth tracking, live market data, a learning library, Navamika's KG workbook, games and Story Time, and Arya's Kerala PSC study window. Its daily content is published by a scheduled task at 08:48 Kuwait time, with a retry at 11:48.

## 2. Decisions

1. **Finance data lives on the phone.** There is no backend to connect to, and running a server would add cost and attack surface for a single user. The app uses Room on SQLCipher, and the database key is protected by the Android Keystore.
2. **The web tracker's backup format is the bridge.** The app imports the website's JSON backup (Settings → Export backup). It also exports in the same `state.money` v4 format, so nothing is lost either way.
3. **The education modules stay where they work.** The Study corner opens the existing Navamika and Arya pages in a WebView, using a new study-only mode (`?study=navw|arya`) that hides every finance and trading area.
   - A JavaScript bridge adds what Android WebView lacks: saving PDFs to Downloads, the share sheet, printing, and text-to-speech for the games.
   - Their progress is stored separately in the WebView. They cannot read finance data.
4. **Server-side scheduling stays as it is.** Daily study content is already produced by a scheduled cloud task and published to the website. The phone app does not need to be open. On the phone, WorkManager only posts the user's own recurring transactions and reminders.
5. **Sync is deferred.** Offline sync, conflict resolution and idempotent server requests need a server. Every write is local and immediate, so there is no pending-sync state. Local writes are already idempotent: stable ids, bill payment ids derived from the due date, and `rec-<item>-<date>` for recurring posts.
   - A future backend can be added behind `FinanceRepository` without changing the screens.

## 3. Phases as delivered

| Phase | Status |
|---|---|
| 1. MVP | Done:<br>• app shell and dashboard<br>• income, expense and transfer management<br>• history, search, filters and sort<br>• accounts and budgets<br>• encrypted persistence, charts and tests |
| 2. Complete finance management | Done:<br>• advanced reports<br>• PDF and CSV export plus share<br>• recurring transactions, savings goals and bills<br>• notification preferences<br>• encrypted backup and restore<br>**Not applicable:** server sync (no backend) |
| 3. Existing modules | Done: the Study corner with Navamika and Arya, using the website's server-side daily schedule |
| 4. Release | Done:<br>• CI builds the debug APK, release APK and AAB<br>• emulator tests and launch timing<br>• lint, R8 shrinking, backup rules and the Play checklist (`RELEASE.md`)<br>**Needs the owner:** a signing key and the Play Console listing |
