# Taskfern — Implementation plan

Taskfern is an offline-first Android to-do list, daily planner, shopping list and routine manager. This document
records the architecture, the feature priorities and the acceptance criteria the app is built against. The product
reasoning behind the priorities is in [PRODUCT_RESEARCH.md](PRODUCT_RESEARCH.md).

## Platform decisions

| Decision | Choice | Why |
| --- | --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Modern, declarative, Material You dynamic colour built in |
| minSdk / targetSdk | 26 (Android 8.0) / 37 | ~97% of active devices; java.time and adaptive icons are available without desugaring |
| Build | AGP 9.4, Kotlin 2.4, Gradle 9.8 (wrapper), version catalog | Matches the installed toolchain; AGP 9 compiles Kotlin itself |
| Persistence | Room (SQLite, WAL) + DataStore for preferences | Transactions, migrations, observable queries |
| Background | AlarmManager for reminders, WorkManager for daily jobs | Exact alarms only where a user-chosen time requires it; WorkManager batches the rest |
| Widgets | Jetpack Glance (RemoteViews underneath) | Interactive lists and per-instance state on Android 8+ |
| DI | Hand-written `AppContainer` | The graph is small; no annotation processing at startup |
| Network | Only an optional update check: one HTTPS request to `api.github.com` at most once a day, after the user agrees. `INTERNET` is the only network permission (enforced by a build check) | Privacy and offline promise, while letting sideloaded users learn about new releases |
| Distribution | Signed APK on GitHub Releases only | No Play Store, no Play services, no Firebase |

## Architecture

```
ui (Compose screens + ViewModels, StateFlow)        widget (Glance widgets, action callbacks, config activities)
        │                                                   │
        └──────────────► data/repo (repositories) ◄─────────┘
                              │          │
            domain (pure Kotlin: recurrence engine, reminder maths, quick-add parser, grocery categories)
                              │
                    data/db (Room entities, DAOs)  data/prefs (DataStore)
                              │
         reminders (AlarmManager scheduler, receivers, WorkManager jobs, notifications)
```

* **Single source of truth.** Every screen and widget reads Room through Flows. All smart views (Today, Tomorrow, This
  week, Upcoming, Overdue, Important, No date, Completed, Recently created) are queries or filters over the same
  tables, so a change anywhere shows up everywhere immediately.
* **Side effects after commit.** Repositories write in a transaction, then raise `DataEvents`; the container re-arms
  the affected alarms and asks the widgets to refresh. Nothing polls.
* **Recurring tasks** are one row whose due date rolls forward. Each completed occurrence is saved as its own completed
  row (`source_task_id` → series), plus an occurrence log entry. This never creates duplicate future tasks, makes the
  Completed view and statistics uniform, and lets Undo restore the exact previous state.
* **Double-tap safety.** Widget and notification "complete" actions carry the due date they were showing; the repository
  ignores the action when the task has moved on, so a second tap cannot complete the next occurrence.
* **Reminders**: one alarm per task for its next reminder, keyed by a unique URI (re-arming replaces, never duplicates),
  with the scheduled instant stored on the row to ignore stale alarms and to catch up after a reboot.

## Feature priorities

**Must (release blockers)**
1. Tasks: create, edit, complete with undo, delete to trash with restore, due date/time, notes, priority, tags, lists,
   subtasks/checklists, pin, duplicate, archive, multi-select bulk actions, search, sort, filter, manual reorder.
2. Smart views listed above; Today dashboard with overdue, due today, routines, progress and a greeting.
3. Quick Add with keyboard focus, add-another, date/time/reminder/priority/list and a local, visible date parser.
4. Recurring engine: daily, weekdays, selected weekdays, weekly, monthly, yearly, custom interval, repeat after
   completion, end by date or count, pause/resume, skip; correct across short months, leap years, DST and zones.
5. Reminders: notification permission flow, exact alarms with inexact fallback, snooze, complete and reschedule from
   the notification, reboot / time-change / update recovery, optional daily agenda.
6. Shopping lists: multiple lists, quantity/unit/category, inline check-off, hide checked, clear checked, duplicate,
   templates, share as text, suggestions, buy again, optional prices and total, duplicate detection.
7. Routines: steps, weekdays, optional time with reminder, per-day check-off, progress, history, pause, templates.
8. Calendar: month grid with due indicators, day agenda, quick reschedule.
9. Backup: JSON export/import through the Storage Access Framework, validation, merge or replace, then reminders
   and widgets reconciled.
10. Widgets: Today's tasks (compact/medium/large via responsive sizes), Quick Add, Shopping list, Weekly agenda, Routine.
11. Themes: system/light/dark, AMOLED black, Material You dynamic colour, accent palettes.

**Should**: templates screen, launcher shortcuts, share-sheet capture, insights, focus timer, ICS export of dated tasks.

**Won't (this release)**: accounts, cloud sync, collaboration, location reminders, AI or network features (beyond
the opt-in update check), Play Store.

## Acceptance criteria

* `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` succeed.
* The merged manifest has no network-state, Wi-Fi-state, broad storage or advertising-ID permission; `INTERNET` is used
  only by the opt-in update check.
* Recurrence engine unit tests cover month ends, leap years, intervals, weekday sets, end conditions, missed occurrences.
* Reminder maths tests cover DST gaps/overlaps and zone changes.
* Instrumented tests cover DAO queries, completing/undoing recurring tasks, backup round-trip, and the schema.
* On the test phone: every screen opens, every widget can be added, configured and used, data survives a restart,
  themes switch, notification denial is handled, backup export/import round-trips, and Logcat shows no crashes.
* A signed release APK is produced by GitHub Actions and attached to a GitHub Release with its SHA-256.
