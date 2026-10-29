# Architecture

Taskfern is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose. It has no server and no
sync. Its only network access is an optional, user-approved update check against GitHub Releases. This page explains
how the code is organised and why.

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
├── update/                        optional update check: release JSON parser, throttle rules, HTTPS fetch, controller
├── widget/                        Glance widgets, action callbacks, configuration activities, update coalescing
└── ui/                            Compose screens and ViewModels per feature, theme, navigation, shared components
                                   (ui/update: the one-time update question and the update popup)
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
  network-state, Wi-Fi-state, broad storage or advertising-ID permissions. `INTERNET` is the one network permission
  allowed, and only the update check uses it; `scripts/verify-apk.sh` repeats the check on the signed release APK.
- **Update check.** `update/UpdateChecker` makes one HTTPS GET to
  `https://api.github.com/repos/<UPDATE_REPO>/releases/latest` (`HttpURLConnection` on `Dispatchers.IO`, 10 s
  timeouts, 256 KB response cap, no cookies or identifiers). `ReleaseParser` and `UpdatePolicy` are pure and
  unit-tested: tags `vMAJOR.MINOR.CODE` are compared by versionCode, drafts, pre-releases and unknown tags are never
  offered, and the APK asset is preferred over the release page. `UpdateController` (in `AppContainer`) runs the
  automatic check at most once per 24 hours and only when the user agreed (`updateCheck` in DataStore; release builds
  ask once on first start), remembers a skipped version, and holds the release shown by `ui/update/UpdatePrompts`,
  which the root UI hosts. Download opens the APK URL in the browser; the app never downloads or installs anything.
  Forks point the check at their own repository with `-Ptaskfern.updateRepo=owner/name`.

## Background work

| Mechanism | Used for | Why |
| --- | --- | --- |
| `AlarmManager` (exact when allowed) | task and routine reminders, focus-timer end | user-chosen times |
| `WorkManager` one-time work, re-enqueued daily | midnight rollover (widgets, trash purge), daily agenda | batched by the OS, survives reboot |
| Broadcast receivers | boot, app update, time and time-zone change, exact-alarm permission change | rebuild alarms |
| Network: one HTTPS request on app start, at most once a day, only if enabled | update check (GitHub Releases) | the only network use; no background polling |

There is no foreground service and nothing runs continuously.

## Testing

See [TESTING.md](TESTING.md).

## Third-party libraries

All runtime dependencies are AndroidX or Kotlin libraries under the Apache License 2.0: Core, Activity, Lifecycle,
Navigation, Compose (UI, Foundation, Material 3, Material Icons), SplashScreen, Room, DataStore, WorkManager, Glance,
kotlinx.coroutines and kotlinx.serialization. Test-only: JUnit 4 (EPL 1.0), AndroidX Test (Apache 2.0) and Turbine
(Apache 2.0).
