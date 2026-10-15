# Test-case register and execution report

Generated 2026-10-15 from CI results (Run 26 · commit 7277efead270e5867df700c43d55a396ba7aa231 · gradle exit 0).

| Measure | Value |
|---|---|
| Test cases in register | 96 |
| Passed | 88 |
| Failed | 0 |
| Blocked | 1 |
| Not Run | 6 |
| Not Applicable (justified below) | 1 |
| **Pass rate** = Passed ÷ (Passed + Failed) | **100.0%** |
| Critical cases passed (of executed) | 31/31 |

Blocked and Not Run cases are reported separately and are **not** counted as passes. Not Applicable cases are excluded from the pass rate because the feature depends on a server that does not exist (see `PLAN.md`).

## Requirements

| ID | Requirement |
|---|---|
| REQ-01 | Dashboard figures from stored data, auto-updating |
| REQ-02 | Add, edit, view and delete transactions |
| REQ-03 | Validation and duplicate-submission protection |
| REQ-04 | Accounts, balances and transfers (transfers are not income/expense) |
| REQ-05 | Budgets, progress and limit alerts |
| REQ-06 | Goals, bills and recurring transactions |
| REQ-07 | Reports, filters, PDF/CSV export |
| REQ-08 | Reminders and notifications |
| REQ-09 | Education modules kept separate |
| REQ-10 | Navigation, UI states and accessibility |
| REQ-11 | Security, privacy and app lock |
| REQ-12 | Persistence, backup/restore, import |
| REQ-13 | Install, launch and performance |
| REQ-14 | Server sync, conflicts, backend retries |

## Register

| ID | Req | Module | Scenario | Expected | Actual | Status | Severity | Evidence |
|---|---|---|---|---|---|---|---|---|
| TC_CALC_01 | REQ-04 | Calculations | Account balances | Balance = opening + in − out for every account | As expected | Pass | Critical | FinanceMathTest.TC_CALC_01_account_balances_follow_every_transaction (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_02 | REQ-04 | Calculations | Transfers excluded from income/expense | Transfers counted separately; net worth unchanged | As expected | Pass | Critical | FinanceMathTest.TC_CALC_02_transfers_are_not_income_or_expense_and_do_not_change_net_worth (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_03 | REQ-01 | Calculations | Net savings and savings rate | Net = income − expense; rate = net ÷ income | As expected | Pass | Critical | FinanceMathTest.TC_CALC_03_net_savings_and_savings_rate (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_04 | REQ-01 | Calculations | Savings rate without income | Rate shown as empty, not 0% or an error | As expected | Pass | Medium | FinanceMathTest.TC_CALC_04_savings_rate_is_empty_without_income (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_05 | REQ-07 | Calculations | Category totals | Sorted totals; shares add up to 100% | As expected | Pass | High | FinanceMathTest.TC_CALC_05_category_totals_sorted_with_shares (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_06 | REQ-05 | Budgets | Budget levels | Levels: warning, over, fine; multi-month limit scaled | As expected | Pass | High | FinanceMathTest.TC_CALC_06_budget_levels_at_80_and_100_percent (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_07 | REQ-01 | Calculations | Monthly series for the trend chart | One point per month with correct sums | As expected | Pass | Medium | FinanceMathTest.TC_CALC_07_monthly_series_covers_each_month (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_08 | REQ-07 | Reports | Day/week/month/year ranges | Week starts Sunday; month and year boundaries correct | As expected | Pass | Medium | FinanceMathTest.TC_CALC_08_period_ranges (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_09 | REQ-06 | Recurring | Repeat rules match the web tracker | Dates clamp to month end; end date respected | As expected | Pass | High | FinanceMathTest.TC_CALC_09_recurrence_matches_tracker_rules (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_10 | REQ-06 | Goals | Goal milestones | Only crossed milestones reported | As expected | Pass | Medium | FinanceMathTest.TC_CALC_10_goal_milestones (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_11 | REQ-02 | Transactions | Search and filters | Correct matches; account filter includes transfers in | As expected | Pass | High | FinanceMathTest.TC_CALC_11_filters_and_search (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_12 | REQ-07 | Reports | Cash flow and chart buckets | Daily buckets for a month, monthly for a year; running total ends at net | As expected | Pass | Medium | FinanceMathTest.TC_CALC_12_cash_flow_running_total (0.07s) — CI unit/UI (Robolectric, JVM 17) |
| TC_CALC_13 | REQ-07 | Reports | Months in a range | 1 for a month, 12 for a year | As expected | Pass | Low | FinanceMathTest.TC_CALC_13_months_in_range (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_01 | REQ-03 | Validation | Valid amounts | Exact values in mils | As expected | Pass | Critical | MoneyAndValidationTest.TC_VAL_01_valid_amounts_parse_exactly (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_02 | REQ-03 | Validation | Negative amount | Rejected with a clear message | As expected | Pass | Critical | MoneyAndValidationTest.TC_VAL_02_negative_amount_rejected (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_03 | REQ-03 | Validation | Zero or empty amount | Rejected | As expected | Pass | Critical | MoneyAndValidationTest.TC_VAL_03_zero_and_empty_rejected (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_04 | REQ-03 | Validation | Too many decimals | Rejected per currency rule | As expected | Pass | High | MoneyAndValidationTest.TC_VAL_04_too_many_decimals_rejected (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_05 | REQ-03 | Validation | Non-numeric and huge amounts | Rejected | As expected | Pass | High | MoneyAndValidationTest.TC_VAL_05_non_numeric_and_huge_rejected (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_06 | REQ-04 | Validation | Transfer to the same account | First rejected, second valid | As expected | Pass | High | MoneyAndValidationTest.TC_VAL_06_transfer_needs_two_different_accounts (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_07 | REQ-03 | Validation | Missing category | Rejected | As expected | Pass | Medium | MoneyAndValidationTest.TC_VAL_07_income_and_expense_need_category (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_08 | REQ-03 | Validation | Unknown account | Rejected | As expected | Pass | High | MoneyAndValidationTest.TC_VAL_08_unknown_account_rejected (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_09 | REQ-03 | Validation | Valid draft | Passes with exact amount | As expected | Pass | High | MoneyAndValidationTest.TC_VAL_09_valid_draft_passes_with_amount (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_10 | REQ-03 | Validation | Invalid dates | Rejected; near-future allowed | As expected | Pass | Medium | MoneyAndValidationTest.TC_VAL_10_bad_dates_rejected (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_11 | REQ-03 | Validation | Form shows error and saves nothing | Error shown, no save; fixed value saves once | As expected | Pass | Critical | TxEditorViewModelTest.TC_VAL_11_invalid_amount_shows_error_and_saves_nothing (0.03s) — CI unit/UI (Robolectric, JVM 17) |
| TC_VAL_12 | REQ-03 | Validation | Over-long text | Rejected | As expected | Pass | Low | MoneyAndValidationTest.TC_VAL_12_long_text_rejected (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_FMT_01 | REQ-01 | Formatting | Currency display | KWD 3 decimals; symbols; sign handling | As expected | Pass | High | MoneyAndValidationTest.TC_FMT_01_currency_formatting (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_01 | REQ-02 | Transactions | Add transaction | Stored; balance falls by the amount | As expected | Pass | Critical | RepositoryTest.TC_TX_01_add_transaction_persists_and_updates_balance (0.08s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_02 | REQ-02 | Transactions | Edit transaction | One row, new values, original created time | As expected | Pass | Critical | RepositoryTest.TC_TX_02_edit_keeps_created_time_and_changes_values (0.09s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_03 | REQ-02 | Transactions | Delete transaction | First deletes; second reports nothing to delete | As expected | Pass | Critical | RepositoryTest.TC_TX_03_delete_transaction (0.12s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_04 | REQ-03 | Transactions | Repeated save of the same entry | Exactly one row | As expected | Pass | Critical | RepositoryTest.TC_TX_04_saving_the_same_entry_twice_does_not_duplicate (0.16s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_05 | REQ-03 | Transactions | Double tap on Save | Repository called once | As expected | Pass | Critical | TxEditorViewModelTest.TC_TX_05_double_tap_on_save_saves_once (0.03s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_06 | REQ-02 | Transactions | Edit keeps links | Same id; bill link and time kept | As expected | Pass | High | TxEditorViewModelTest.TC_TX_06_editing_loads_existing_values_and_keeps_links (4.74s) — CI unit/UI (Robolectric, JVM 17) |
| TC_TX_07 | REQ-04 | Transactions | Transfer preset | From and To prefilled with different accounts | As expected | Pass | Medium | TxEditorViewModelTest.TC_TX_07_transfer_preset_picks_two_accounts_and_no_category (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_ACC_01 | REQ-04 | Accounts | Delete account with history | Refused (archived instead in UI) | As expected | Pass | Critical | RepositoryTest.TC_ACC_01_account_with_transactions_cannot_be_deleted (0.13s) — CI unit/UI (Robolectric, JVM 17) |
| TC_ACC_02 | REQ-04 | Accounts | Delete unused account | Removed | As expected | Pass | Medium | RepositoryTest.TC_ACC_02_unused_account_can_be_deleted (0.10s) — CI unit/UI (Robolectric, JVM 17) |
| TC_ACC_03 | REQ-04 | Accounts | Transfer between accounts | −60 / +60; net worth unchanged | As expected | Pass | Critical | RepositoryTest.TC_ACC_03_transfer_moves_money_without_changing_net_worth (0.16s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BILL_01 | REQ-06 | Bills | Pay a repeating bill | Expense recorded with bill link; due moves a month | As expected | Pass | High | RepositoryTest.TC_BILL_01_paying_a_bill_records_expense_and_moves_due_date (0.14s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BILL_02 | REQ-03 | Bills | Pay the same bill twice | Second payment refused; one expense | As expected | Pass | Critical | RepositoryTest.TC_BILL_02_one_time_bill_is_done_and_cannot_be_paid_twice_for_same_due (0.15s) — CI unit/UI (Robolectric, JVM 17) |
| TC_REC_01 | REQ-06 | Recurring | Catch-up posting | Three dated transactions; next date advanced | As expected | Pass | High | RepositoryTest.TC_REC_01_recurring_posts_missed_occurrences (0.19s) — CI unit/UI (Robolectric, JVM 17) |
| TC_REC_02 | REQ-03 | Recurring | Idempotent posting | No duplicates | As expected | Pass | Critical | RepositoryTest.TC_REC_02_running_recurring_twice_creates_no_duplicates (0.18s) — CI unit/UI (Robolectric, JVM 17) |
| TC_REC_03 | REQ-06 | Recurring | End date | Stops at end date; marked finished | As expected | Pass | Medium | RepositoryTest.TC_REC_03_end_date_stops_recurring (0.20s) — CI unit/UI (Robolectric, JVM 17) |
| TC_REC_04 | REQ-04 | Recurring | Recurring transfer | Both accounts kept; category transfer | As expected | Pass | Medium | RepositoryTest.TC_REC_04_recurring_transfer_keeps_both_accounts (0.11s) — CI unit/UI (Robolectric, JVM 17) |
| TC_GOAL_01 | REQ-06 | Goals | Add to and withdraw from a goal | Milestones 25/50; expense recorded; never below 0 | As expected | Pass | Medium | RepositoryTest.TC_GOAL_01_adding_to_goal_reports_milestones_and_optional_expense (16.92s) — CI unit/UI (Robolectric, JVM 17) |
| TC_DATA_01 | REQ-12 | Import | Replace all data from a backup | Old data replaced in one step; totals reconcile | As expected | Pass | Critical | RepositoryTest.TC_DATA_01_import_replaces_everything_in_one_step (0.24s) — CI unit/UI (Robolectric, JVM 17) |
| TC_DATA_02 | REQ-01 | Data | Live updates | Observers receive the new list | As expected | Pass | High | RepositoryTest.TC_DATA_02_flows_emit_updates_after_changes (0.21s) — CI unit/UI (Robolectric, JVM 17) |
| TC_PERSIST_01 | REQ-12 | Persistence | Data survives restart | Transaction still there | As expected | Pass | Critical | RepositoryTest.TC_PERSIST_01_data_survives_closing_and_reopening_the_database (0.28s) — CI unit/UI (Robolectric, JVM 17) |
| TC_PERSIST_02 | REQ-12 | Persistence | Data survives activity recreation on device | Same transaction count | As expected | Pass | Critical | DeviceTest.TC_PERSIST_02_data_is_still_there_after_the_activity_is_recreated (4.17s) — CI emulator (Pixel 6, Android 14) |
| TC_BAK_01 | REQ-12 | Import | Import website tracker backup | 7 transactions, 4 accounts (1 recreated), budgets, goal, bill; 2 invalid skipped | As expected | Pass | Critical | BackupAndExportTest.TC_BAK_01_imports_website_tracker_backup (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BAK_02 | REQ-12 | Backup | Export then import | Identical transactions, accounts, budgets, bills, recurring | As expected | Pass | Critical | BackupAndExportTest.TC_BAK_02_export_then_import_round_trips (0.02s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BAK_03 | REQ-11 | Backup | Password-protected backup | Plain text hidden; exact round trip | As expected | Pass | High | BackupAndExportTest.TC_BAK_03_password_protected_backup_round_trips (1.09s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BAK_04 | REQ-11 | Backup | Wrong password | Clear 'Wrong password' error | As expected | Pass | High | BackupAndExportTest.TC_BAK_04_wrong_password_is_reported (1.12s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BAK_05 | REQ-12 | Backup | Invalid files | Clear errors, nothing changed | As expected | Pass | High | BackupAndExportTest.TC_BAK_05_invalid_files_are_rejected_clearly (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BAK_07 | REQ-12 | Import | Old tracker data without accounts | Default accounts created; methods mapped | As expected | Pass | Medium | BackupAndExportTest.TC_BAK_07_old_data_without_accounts_gets_default_accounts (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_BAK_08 | REQ-01 | Import | Imported figures reconcile | Bank 3,319.750; card −87.000; net worth 3,361.750; transfers excluded | As expected | Pass | Critical | BackupAndExportTest.TC_BAK_08_imported_figures_reconcile (0.07s) — CI unit/UI (Robolectric, JVM 17) |
| TC_EXP_01 | REQ-07 | Export | CSV content | Header + one row each; signed amounts; account names | As expected | Pass | High | BackupAndExportTest.TC_EXP_01_csv_has_header_and_one_row_per_transaction (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_EXP_02 | REQ-07 | Export | CSV escaping | Valid quoting | As expected | Pass | Medium | BackupAndExportTest.TC_EXP_02_csv_escapes_quotes_commas_and_newlines (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_EXP_03 | REQ-11 | Export | CSV formula injection | Cells neutralised | As expected | Pass | High | BackupAndExportTest.TC_EXP_03_csv_neutralises_spreadsheet_formulas (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_SEC_01 | REQ-11 | Security | PIN hash | Only correct PIN verifies; PIN not stored | As expected | Pass | Critical | PinAndAlertsTest.TC_SEC_01_pin_hash_verifies_only_the_right_pin (0.02s) — CI unit/UI (Robolectric, JVM 17) |
| TC_SEC_02 | REQ-11 | Security | Salted hashes | Different hashes | As expected | Pass | High | PinAndAlertsTest.TC_SEC_02_same_pin_hashes_differently_each_time (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_SEC_03 | REQ-11 | Security | PIN format | Only 4–8 digits accepted | As expected | Pass | Medium | PinAndAlertsTest.TC_SEC_03_pin_format_rules (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_SEC_04 | REQ-11 | Security | Database encrypted at rest | Not a plain SQLite file | As expected | Pass | Critical | DeviceTest.TC_SEC_04_database_file_on_disk_is_encrypted (3.85s) — CI emulator (Pixel 6, Android 14) |
| TC_NOT_01 | REQ-08 | Notifications | Bill reminders | Due-soon and overdue only | As expected | Pass | High | PinAndAlertsTest.TC_NOT_01_bill_reminders_due_soon_and_overdue (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_NOT_02 | REQ-08 | Notifications | Switched off | Nothing | As expected | Pass | High | PinAndAlertsTest.TC_NOT_02_nothing_when_switched_off (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_NOT_03 | REQ-08 | Notifications | Budget alerts | 80% and 100% alerts | As expected | Pass | High | PinAndAlertsTest.TC_NOT_03_budget_alerts_at_80_and_100_percent (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_NOT_04 | REQ-08 | Notifications | Goal milestone | 50% milestone alert | As expected | Pass | Medium | PinAndAlertsTest.TC_NOT_04_goal_milestone_alert (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_NOT_05 | REQ-08 | Notifications | Monthly summary | Only on the 1st, with last month's figures | As expected | Pass | Medium | PinAndAlertsTest.TC_NOT_05_monthly_summary_only_on_the_first (0.00s) — CI unit/UI (Robolectric, JVM 17) |
| TC_NOT_06 | REQ-08 | Notifications | Recurring notice | One notice listing them | As expected | Pass | Low | PinAndAlertsTest.TC_NOT_06_recurring_notice_lists_posted_items (0.01s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_01 | REQ-10 | Navigation | Bottom navigation | Each screen opens | As expected | Pass | Critical | AppUiTest.TC_UI_01_bottom_navigation_opens_all_five_screens (1.27s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_02 | REQ-01 | End to end | Add expense updates dashboard and list | Dashboard total and KPI update; listed in Transactions | As expected | Pass | Critical | AppUiTest.TC_UI_02_adding_an_expense_updates_dashboard_and_list (2.36s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_03 | REQ-03 | End to end | Invalid amounts in the form | Errors shown; nothing saved | As expected | Pass | Critical | AppUiTest.TC_UI_03_invalid_amounts_show_errors_and_nothing_is_saved (1.02s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_04 | REQ-04 | End to end | Transfer from the dashboard | Saved as transfer; net worth 0; income and expense 0 | As expected | Pass | Critical | AppUiTest.TC_UI_04_transfer_does_not_count_as_income_or_expense (1.87s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_05 | REQ-10 | End to end | Delete with confirmation | Nothing deleted before confirming; deleted after | As expected | Pass | Critical | AppUiTest.TC_UI_05_delete_asks_for_confirmation_then_removes (1.54s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_06 | REQ-10 | End to end | First run | Dashboard with three default accounts | As expected | Pass | High | AppUiTest.TC_UI_06_first_run_start_fresh_creates_default_accounts (0.63s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_07 | REQ-10 | UI states | Empty states | Helpful empty messages | As expected | Pass | Medium | AppUiTest.TC_UI_07_empty_states_are_helpful (0.84s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_08 | REQ-10 | Accessibility | Touch targets | At least 48 dp high | As expected | Pass | High | AppUiTest.TC_UI_08_navigation_and_main_buttons_have_large_touch_targets (0.40s) — CI unit/UI (Robolectric, JVM 17) |
| TC_UI_09 | REQ-05 | End to end | Set a budget | Saved; dashboard shows usage against 150 | As expected | Pass | High | AppUiTest.TC_UI_09_budget_can_be_set_and_shows_on_dashboard (9.20s) — CI unit/UI (Robolectric, JVM 17) |
| TC_INST_01 | REQ-13 | Install | Install and launch on device | Usable screen; add form opens | As expected | Pass | Critical | DeviceTest.TC_INST_01_app_installs_and_launches_to_a_usable_screen (7.59s) — CI emulator (Pixel 6, Android 14) |
| TC_INST_02 | REQ-02 | End to end | Add expense on device | Shown in list; stored once with exact amount | As expected | Pass | Critical | DeviceTest.TC_INST_02_add_expense_end_to_end_on_device (11.55s) — CI emulator (Pixel 6, Android 14) |
| TC_EDU_01 | REQ-09 | Study corner | Website study-only mode | Only Navamika's or Arya's tabs; finance and trading hidden; no errors | navw: 9 Navamika tabs only; arya: Arya tab only; normal mode unchanged; 0 page errors | Pass | High | Playwright run against the website build (study_t.js), 15 Oct 2026; tracker regression 64/64 |
| TC_EDU_02 | REQ-09 | Study corner | Navamika pages inside the app | Website loads in study-only mode; Navamika tabs only; no finance tabs; Android bridge and speech present | As expected | Pass | High | StudyCornerTest.TC_EDU_02_navamika_pages_load_in_study_only_mode (6.05s) — CI emulator (Pixel 6, Android 14) |
| TC_EDU_03 | REQ-09 | Study corner | Arya pages inside the app | Website loads in study-only mode; Arya tab only; Android bridge present | As expected | Pass | High | StudyCornerTest.TC_EDU_03_arya_pages_load_in_study_only_mode (11.32s) — CI emulator (Pixel 6, Android 14) |
| ENV_01 | REQ-13 | Device check | WebView can load a local page | Page finishes; JavaScript runs | As expected | Pass | Low | WebViewEnvTest.ENV_01_webview_can_load_a_local_page (0.54s) — CI emulator (Pixel 6, Android 14) |
| ENV_02 | REQ-09 | Device check | Study website loads in a plain WebView | Finishes in study-only mode | As expected | Pass | Medium | WebViewEnvTest.ENV_02_study_page_loads_in_plain_webview (0.60s) — CI emulator (Pixel 6, Android 14) |
| ENV_03 | REQ-09 | Device check | Study website loads with the app's script | Finishes; bridge visible | As expected | Pass | Medium | WebViewEnvTest.ENV_03_study_page_loads_with_app_shim (2.15s) — CI emulator (Pixel 6, Android 14) |
| ENV_04 | REQ-09 | Device check | Study website loads when shown in a window | Finishes | As expected | Pass | Medium | WebViewEnvTest.ENV_04_study_page_loads_when_attached_to_a_window (7.17s) — CI emulator (Pixel 6, Android 14) |
| ENV_05 | REQ-09 | Device check | Study website with software drawing | Finishes | As expected | Pass | Low | WebViewEnvTest.ENV_05_study_page_loads_attached_with_software_rendering (4.76s) — CI emulator (Pixel 6, Android 14) |
| ENV_06 | REQ-09 | Device check | The app's own study WebView | Finishes in study-only mode | As expected | Pass | High | WebViewEnvTest.ENV_06_app_study_webview_loads_when_attached_outside_compose (7.12s) — CI emulator (Pixel 6, Android 14) |
| TC_PERF_01 | REQ-13 | Performance | Cold launch time | Each under 3,000 ms | TotalTime 1701 ms, 1507 ms, 1779 ms | Pass | High | device-log-tail.txt (am start -W) — model: sdk_gphone64_x86_64; android: 14 (API 34); abi: x86_64;  |
| TC_MAN_01 | REQ-11 | Security | Biometric unlock | Biometric prompt unlocks; PIN fallback works | Needs a physical phone with biometrics enrolled; emulator CI cannot do this. | Not Run | High |  |
| TC_MAN_02 | REQ-08 | Notifications | Permission prompt and delivery | Permission asked once; notification shown; amounts hidden on lock screen | Needs a real phone and waiting for the 09:00 job; the alert logic is covered by TC_NOT_01–06. | Not Run | High |  |
| TC_MAN_03 | REQ-07 | Export | Save and share PDF/CSV on a phone | File saved via the picker; share sheet opens; PDF readable | System file picker and share sheet need a person on a phone; file contents are covered by TC_EXP_01–03. | Not Run | High |  |
| TC_MAN_04 | REQ-09 | Study corner | Downloads and read-aloud on a phone | PDF saved to Downloads; voice reads aloud | Needs a person listening on a phone; page loading and the bridge are covered by TC_EDU_02–03. | Not Run | High |  |
| TC_MAN_05 | REQ-13 | Install | Upgrade keeps data | Data kept | Needs a release signing key (see RELEASE.md) to install two signed versions. | Blocked | Critical |  |
| TC_MAN_06 | REQ-10 | Accessibility | TalkBack and 200% font size | All controls announced; no clipped text | Manual accessibility pass on a phone. | Not Run | Medium |  |
| TC_MAN_07 | REQ-11 | Security | Auto-lock after background | PIN screen shown; screenshots blocked | Needs a phone; lock logic is in MainActivity. | Not Run | High |  |
| TC_NA_01 | REQ-14 | Sync | Offline queue, conflicts, server retries | — | No backend server exists; data is stored and written locally only (see PLAN.md §2). All local writes are idempotent (TC_TX_04, TC_BILL_02, TC_REC_02). | Not Applicable | High |  |

The CSV next to this file has every field, including preconditions, steps, defect, tester, date and retest columns.
