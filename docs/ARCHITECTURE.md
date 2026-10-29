# Architecture

Taskfern is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose. It has no server and no
network access. This page explains how the code is organised and why.

## Layers

```
com.riyadmondol2006.taskfern
├── TaskfernApp, AppContainer      process entry point and hand-written dependency container
├── MainActivity                   single activity hosting the Compose UI (splash, theme, deep links)
├── domain/                        pure Kotlin rules, unit-tested without Android
│   ├── recurrence/                RecurrenceRule + RecurrenceEngine (next dates, catch-up, end conditions, projection)
│   ├── ReminderCalculator         next reminder instant (offsets, snooze, default time, DST/time zones)
│   ├── QuickAddParser             local, conservative date/time/priority/tag recognition
│   └── GroceryCategories          offline aisle guess for shopping items
├── data/
│   ├── db/                        Room entities, DAOs, converters, TaskfernDatabase (schema in app/schemas)
│   ├── prefs/                     DataStore-backed Settings
│   └── repo/                      repositories: the only code that writes data
├── reminders/                     AlarmManager scheduler, broadcast receivers, notifications, WorkManager daily jobs
├── focus/                         focus-timer controller (timestamps + one alarm, no service)
├── backup/                        JSON backup/restore and .ics export
├── widget/                        Glance widgets, action callbacks, configuration activities, update coalescing
└── ui/                            Compose screens and ViewModels per feature, theme, navigation, shared components
```

## Data flow

1. Screens observe `StateFlow`s exposed by ViewModels, which combine Room `Flow`s and settings.
2. User actions call ViewModel functions, which call repository suspend functions.
3. Repositories write inside a Room transaction and then report the change through `DataEvents`.
4. `AppContainer` reacts to `DataEvents`: it re-arms the affected reminders and asks `WidgetUpdater` to refresh the
   affected widget types (debounced, never polled).
5. Room invalidation pushes new data to every observing screen, so all views stay consistent.

## Key decisions

- **Recurring tasks are one row.** Completing an occurrence inserts a completed copy (pointing back with
  `source_task_id`), logs it in `occurrence_log`, and moves the series to the next date in the same transaction. No
  future copies exist, so there is nothing to duplicate. Undo restores the previous row, deletes the copy and the log
  entry.
- **Idempotent actions.** Widget and notification actions carry the due date they displayed. `complete(…,
  checkExpected = true)` does nothing if the task has already moved on. Shopping and routine check-offs set an explicit
  state rather than toggling blindly.
- **Wall-clock dates.** Due dates are `LocalDate` and times `LocalTime` (stored as epoch day and minutes). Reminders
  are converted to instants in the current zone when scheduled, so they follow the user across time zones and DST.
- **One alarm per task.** The next reminder instant is stored on the task (`next_reminder_at`). The receiver ignores
  stale alarms; boot/update/time-change receivers rebuild all alarms and replay reminders missed in the last 24 hours.
  Exact alarms are used when the user allows them, otherwise `setAndAllowWhileIdle`.
- **No destructive migrations.** Schema changes need a migration and a `MigrationTest`; the database has no
  `fallbackToDestructiveMigration`.
- **Manual dependency injection.** `AppContainer` constructs repositories lazily; widgets, receivers and workers reach
  it through the `Application`. This keeps start-up fast and avoids annotation processing beyond Room.
- **Offline enforcement.** `verifyOfflineManifest<Variant>` fails the build when the merged manifest requests
  `INTERNET`, network-state, broad storage or advertising-ID permissions.

## Background work

| Mechanism | Used for | Why |
| --- | --- | --- |
| `AlarmManager` (exact when allowed) | task and routine reminders, focus-timer end | user-chosen times |
| `WorkManager` one-time work, re-enqueued daily | midnight rollover (widgets, trash purge), daily agenda | batched by the OS, survives reboot |
| Broadcast receivers | boot, app update, time and time-zone change, exact-alarm permission change | rebuild alarms |

There is no foreground service and nothing runs continuously.

## Testing

See [TESTING.md](TESTING.md).

## Third-party libraries

All runtime dependencies are AndroidX or Kotlin libraries under the Apache License 2.0: Core, Activity, Lifecycle,
Navigation, Compose (UI, Foundation, Material 3, Material Icons), SplashScreen, Room, DataStore, WorkManager, Glance,
kotlinx.coroutines and kotlinx.serialization. Test-only: JUnit 4 (EPL 1.0), AndroidX Test (Apache 2.0) and Turbine
(Apache 2.0).
