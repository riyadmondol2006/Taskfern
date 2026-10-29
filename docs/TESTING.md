# Testing

## Automated tests

| Suite | Command | Where | Result (2026-10-29) |
| --- | --- | --- | --- |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` | `app/src/test` | 196 passed, 0 failed |
| Instrumented tests | `./gradlew :app:connectedDebugAndroidTest` | `app/src/androidTest` | 25 passed, 0 failed (Pixel 6, Android 17) |
| Android Lint | `./gradlew :app:lintDebug` | — | 0 errors, 22 warnings (unused resources, plural suggestions, obsolete SDK checks) |
| Offline check | `./gradlew :app:verifyOfflineManifestRelease` | build | passes: no network or broad-storage permission |
| Release verification | `scripts/verify-apk.sh` | CI | signature v2+, not debug-signed, application id, version, no `INTERNET` |

What the unit tests cover:

- **Recurrence engine** (`RecurrenceEngineTest`): daily/weekly/monthly/yearly, intervals, weekdays and chosen days,
  every-2-weeks anchoring, the 31st in short months, 29 February in non-leap years, repeat after completion, catching
  up missed occurrences, end dates and occurrence counts, skip, pause/resume realignment, calendar projection, 10 years
  without drift.
- **Reminder maths** (`ReminderCalculatorTest`): offsets, default time, snooze, DST spring-forward gap and fall-back
  overlap, time-zone changes, routine reminders.
- **Quick Add parser**: relative days, weekdays, 12/24-hour times, priorities, tags, ambiguous numeric dates left
  alone, never an empty title.
- **Smart views and sorting** (`TaskFiltersTest`): every view, week start, refinements, buckets, 5 000 tasks.
- **Shopping**: input parsing, aisle guessing, grouping, duplicate quantities, price totals (per piece vs per weight).
- **Routines**: weekday masks, streaks and history.
- **Backup**: model mapping, validation (format, version, references), merge planning, `.ics` generation.
- **Insights** aggregation and **widget** models, action parameters and update coalescing.

What the instrumented tests cover (real Room database on the device): task create/edit/complete/undo, recurring
completion with snapshot + history + exact undo, a repeated widget/notification tap that must not complete the next
occurrence, end-after-N, skip, pause/resume, trash/restore/purge, deleting lists, LIKE-wildcard escaping, duplicate,
3 000 tasks loading under 1.5 s, the exported Room schema, backup round trips (merge and replace), and widget
action data effects.

## Manual QA on a real device

Device: **Google Pixel 6** (`oriole`), Android 17 (API 37), 1080 × 2400, Pixel Launcher. (The project brief named a
Pixel 6 Pro; the phone actually connected was a Pixel 6.) The release build was R8-minified and signed with a local
test key for this QA.

| Area | Result |
| --- | --- |
| Install, first launch, cold start | Passed. Cold start 204 ms (`am start -W`). |
| Upgrade install keeps data | Passed (versions 1 → 11 installed over each other). |
| Create tasks with Quick Add, smart parsing | Passed. Found and fixed: the screen's default date overrode a typed date. |
| Task editor, complete/reopen, checklist | Passed. |
| Recurring tasks: complete from widget rolls forward | Passed. |
| Real reminder delivery | Passed: exact alarm fired at 12:01:00.03 with Complete / Snooze / Tomorrow actions. |
| Complete from the notification | Passed. |
| Alarms after force-stop / relaunch | Passed: all pending reminders re-armed, no duplicates. |
| Notification permission denied | Passed: Settings shows “Off”, Today shows a banner. The system dialog ignores adb-injected taps, so the permission was granted with `pm grant` afterwards. |
| Shopping list, totals | Passed after fixing totals for weights and volumes. |
| Routines, calendar, search, settings, templates, insights, focus timer, about | Passed. |
| Backup export (Storage Access Framework) | Passed; file validated. |
| Restore: Replace and Merge | Passed; Merge skipped every duplicate. |
| Themes: light, dark, AMOLED, Material You, accents | Passed. |
| Landscape | Passed after adding a navigation rail on wide screens and capping calendar cell height. |
| Font scale 1.3 | Passed. |
| All five widgets added through the launcher pin dialog | Passed. |
| Widget check-off (Today, Shopping, Routine), fast double tap | Passed. |
| Widget content per kind in release builds | **Bug found and fixed**: R8 merged the widget classes so one widget's update redrew the others. |
| Widget rows fill the space | Fixed by switching to exact sizing. |
| TalkBack labels | Fixed: extended floating buttons were missing from the accessibility tree. |
| Share text into Taskfern (Android share sheet intent) | Passed: Quick Add opens with the shared text. |
| Launcher shortcuts | Passed: New task, Today and Shopping are registered; their intents were exercised. |
| Logcat | No crashes or ANRs from Taskfern during the session. |

### Not verified on the device

- A full **reboot** (BOOT_COMPLETED). The re-arm path is the same code exercised after force-stop and app update, but
  the phone was not rebooted to avoid disrupting it.
- **Changing the system time zone or date** on the phone; covered by unit tests only.
- **Resizing** widgets by dragging their handles, and multiple instances of the same widget kind (per-instance
  configuration is covered by tests).
- **Reminders without the exact-alarm permission** waiting through Doze.
- A physical **tablet**, and Android versions other than 17.

## Reproducing the demo data

`tools/demo/make_demo_backup.py` writes a backup with fictional tasks, lists and routines dated relative to a day. Import
it with *Settings → Backup & restore → Choose file → Replace* on a test device. It is never bundled in the app.
