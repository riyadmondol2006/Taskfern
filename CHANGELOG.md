# Changelog

All notable changes to Taskfern are listed here. Every push to `main` is published automatically as a GitHub Release
named `vMAJOR.MINOR.BUILD` (see [docs/RELEASING.md](docs/RELEASING.md)); the release page lists the commits it contains.

## Unreleased

### Added
- Update check: Taskfern can look for a newer release on GitHub once a day and show a popup with the new version,
  short release notes and **Download** (opens the APK in your browser), **Later** and **Skip this version**. Release
  builds ask once on first start whether to turn it on; *Settings → Updates* has the switch, **Check now**, the last
  check time and the installed version.

### Changed
- Taskfern now has the `INTERNET` permission, used only by the optional update check (one HTTPS request to
  `api.github.com` at most once a day, nothing about you or your tasks is sent). The build and release checks still
  reject network-state, Wi-Fi-state, broad storage and advertising-ID permissions.

## Unreleased

### Added
- Plans: tasks for a week, month or year, with a Plans view and a Today section (database version 2, migrated
  automatically).
- Subtasks inside every task: a Subtasks section in the editor and expandable subtasks in every list.
- Streaks for routines and repeating tasks on Today, task rows, the editor, the routine widget and Insights.
- Quick Add understands repeats (“every monday and thursday”, “every 3 days”, “monthly”) and plans (“this month”).
- New day starts at: choose when “today” rolls over.

### Fixed
- Crashes when acting on a task, list or routine that had just been deleted (stale widgets, open screens, undo).
- A fast double tap on a repeating task in the app could complete two occurrences.
- Long-overdue repeating tasks now catch up in one step; negative amounts keep their minus sign; navigation labels
  no longer break mid-word at large font sizes.

## 1.0 — first public release

### Added
- Today dashboard with greeting, progress, overdue and due-today tasks, routines, upcoming reminders and important tasks.
- Tasks: lists, priorities, tags, notes, checklists, pinning, duplicate, archive, Recently deleted with restore,
  multi-select bulk actions, search, sort, filters and manual ordering.
- Smart views: Inbox, Today, Tomorrow, This week, Upcoming, Overdue, Important, No due date, Recently created,
  Completed and All tasks.
- Quick Add sheet with keyboard focus, add-another, and visible local date/time/priority/tag recognition.
- Repeating tasks: daily, weekdays, chosen weekdays, weekly, monthly, yearly, custom intervals, repeat after
  completion, end on a date or after a number of times, pause/resume and skip.
- Reminders: several per task, snooze, complete or move to tomorrow from the notification, recovery after restart,
  time-zone and clock changes, optional daily agenda.
- Shopping lists with aisles, quantities, units, suggestions, buy again, duplicate detection, prices and totals,
  templates and sharing as text.
- Daily routines with steps, weekdays, optional reminder time, per-day check-off, progress and history.
- Calendar with month and week views, projected repeats and quick rescheduling.
- Five interactive home-screen widgets: Today's tasks, Quick Add, Shopping list, Weekly agenda and Daily routine.
- Templates, focus timer, insights, JSON backup and restore (merge or replace) and calendar (.ics) export.
- Material You dynamic colour, light, dark and AMOLED black themes, five accent palettes.
- Original Taskfern fern-check logo and adaptive, themed launcher icon.
