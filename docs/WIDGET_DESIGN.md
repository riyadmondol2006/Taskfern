# Taskfern — Home-screen widget design

Taskfern ships five home-screen widgets built with Jetpack Glance (`GlanceAppWidget` + `GlanceAppWidgetReceiver`,
RemoteViews underneath). They are interactive views of the same Room database the app uses: everything a widget shows
is read from the database when it renders, and everything a widget does goes through the same repositories as the app.

| Widget | Receiver | Provider info | Configuration |
| --- | --- | --- | --- |
| Today's tasks | `TodayWidgetReceiver` | `xml/widget_info_today` | none |
| Quick Add | `QuickAddWidgetReceiver` | `xml/widget_info_quick_add` | `QuickAddWidgetConfigActivity` (optional, reconfigurable) |
| Shopping list | `ShoppingWidgetReceiver` | `xml/widget_info_shopping` | `ShoppingWidgetConfigActivity` (optional, reconfigurable) |
| Weekly agenda | `AgendaWidgetReceiver` | `xml/widget_info_agenda` | none |
| Daily routine | `RoutineWidgetReceiver` | `xml/widget_info_routine` | `RoutineWidgetConfigActivity` (optional, reconfigurable) |

All code lives in `com.riyadmondol2006.taskfern.widget` (config activities in `widget.config`).

## Supported sizes

Sizes are in dp. "Cells" are the usual launcher grid cells (`70 × n − 30` dp on older launchers). Every widget uses
`SizeMode.Responsive`, so the launcher receives one layout per breakpoint and switches between them while resizing
without waking the app. The layout picked inside a breakpoint is decided by `LocalSize`.

| Widget | Default (target cells) | Min resize | Responsive breakpoints | Layouts |
| --- | --- | --- | --- | --- |
| Today | 4 × 3 (250 × 180) | 2 × 2 (110 × 110) | 120×100, 220×100, 160×150, 250×150, 250×260, 320×340 | compact (< 140 tall or < 150 wide), medium, large (≥ 260 tall) |
| Quick Add | 1 × 1 (40 × 40) | 1 × 1 | 48×48, 110×48 | round button (< 100 wide), labelled pill |
| Shopping | 3 × 3 (180 × 180) | 2 × 2 (110 × 110) | 110×110, 180×180, 250×250 | compact (< 150 tall), list |
| Agenda | 4 × 3 (250 × 180) | 3 × 2 (180 × 110) | 180×110, 250×180, 250×300 | list (header shrinks when short) |
| Routine | 3 × 3 (180 × 180) | 2 × 2 (110 × 110) | 110×110, 180×180, 250×250 | compact (< 150 tall), checklist |

### What each layout shows

**Today's tasks** — overdue tasks plus tasks due today that are open and not paused (the same query as the Today screen).

* Compact: date, number of tasks left, overdue count, progress bar and a Quick Add button.
* Medium: header (title, date, counts, Quick Add), progress bar, scrollable task list; each task has a check-off button
  and opens the task when tapped.
* Large: medium plus due times, overdue marking with the original due day, list name, priority flags and an
  "Open Today" button.
* Progress = completed today ÷ (completed today + remaining). "Completed today" counts every task completed since local
  midnight, including the completed occurrences of recurring tasks.
* Empty state: "All clear for today".

**Quick Add** — opens the Quick Add sheet (`QuickAddActivity`) with the widget's default list and due date. The pill
layout shows the target list and due preset. No inline text entry (see platform constraints).

**Shopping list** — list name, number of items left, unchecked items with quantity + unit and a check-off button each,
an "add item" button that opens the list in the app. Checked items leave the widget; they can be unchecked in the app.

**Weekly agenda** — today's date, an overdue badge with the count (opens Today), tasks for today and the next six days
grouped by day (today is always shown, other days only when they have tasks), times and priority flags, and an
"Open Calendar" button. Tapping a task opens it.

**Daily routine** — routine emoji and name, today's steps with a toggle each, "n of m done" and a progress bar.
When the routine does not run today it shows "Not scheduled today" with the next day it runs; a paused routine says so.

## Interactions

| Element | Action | Parameters | Effect |
| --- | --- | --- | --- |
| Task check-off (Today) | `CompleteTaskAction` | task id, expected due (epoch day) | `TaskRepository.complete(id, expectedDue, checkExpected = true)` |
| Task row (Today, Agenda) | start `MainActivity` | `taskfern://task/{id}` | opens the task editor |
| Quick Add buttons | start `QuickAddActivity` | `EXTRA_LIST_ID`, `EXTRA_DUE` | opens the Quick Add sheet |
| Open Today / overdue badge | start `MainActivity` | `taskfern://today` | |
| Open Calendar | start `MainActivity` | `taskfern://calendar` | |
| Shopping item check-off | `CheckShoppingItemAction` | item id, target state | `ShoppingRepository.setChecked` (idempotent) |
| Shopping header / add | start `MainActivity` | `taskfern://shopping/{listId}` | opens the list, where items are typed |
| Routine step | `ToggleRoutineStepAction` | step id, routine id, epoch day, target state | `RoutineRepository.setStepChecked` |
| Routine header | start `MainActivity` | `taskfern://routine/{id}` | |
| "Choose another" banner | start the widget's config activity | widget id | reconfigure after the list/routine was deleted |

Rules that keep taps safe:

* **Stable ids only.** Every action carries the database ids it acts on, never a list position, so a list that changed
  between render and tap can never make a tap hit a neighbouring row. Lazy lists use the row id as the item id.
* **Double taps.** Completing a task passes the due date that was on screen; if the task already moved on (a recurring
  task rolled to its next occurrence, or the first tap already completed it) the repository does nothing. Shopping and
  routine actions carry the *target* state (checked/unchecked), not "toggle", so a repeated tap is a no-op.
* **Stale days.** A routine step tap carries the day it was rendered for; if that is no longer today (the widget showed
  yesterday's checklist just after midnight) the tap only refreshes the widget. A step id that no longer belongs to the
  routine is ignored.
* Every action refreshes the affected widget types before the callback returns.

## Per-instance configuration

Stored in the `widget_configs` table (`WidgetConfigEntity`, key = app widget id, `kind` = `WidgetKind.*`):

| Widget | `target_id` | `option` | Default when not configured |
| --- | --- | --- | --- |
| Quick Add | task list id, null = Inbox | `none` / `today` / `tomorrow` | Inbox, no date |
| Shopping | shopping list id, null = first list | — | first list |
| Routine | routine id, null = first routine | — | first routine |

* Configuration activities are Compose + Material 3 screens themed with `TaskfernTheme` and the user's settings. They
  return `RESULT_OK` with `EXTRA_APPWIDGET_ID`, save the row, and update that instance immediately (before Android 12
  the launcher does not send a first update for configurable widgets). Back/close returns `RESULT_CANCELED`, which makes
  the launcher drop a widget that is being placed.
* An activity only accepts widget ids that belong to its own provider, so another app cannot rewrite configuration.
* On Android 12+ the provider info declares `widgetFeatures="reconfigurable|configuration_optional"`: the widget is
  placed with its default and can be reconfigured from the launcher's long-press menu.
* Rows are deleted in `GlanceAppWidget.onDelete`, which Glance calls from the receiver's `onDeleted`. `refreshAll()`
  also prunes rows whose widget id no longer exists (a launcher that removed widgets without notifying the app).
* **Deleted targets.** If the configured shopping list or routine is deleted, the widget falls back to the first one
  and shows a "… was deleted · tap to choose another" banner that opens the configuration activity. With no lists or
  routines at all it shows an empty state with a button into the app. A Quick Add widget whose list was deleted falls
  back to the Inbox.

## Data synchronisation

* **Single source of truth.** Widgets read Room through the repositories; nothing is cached in widget state, so the
  widgets survive process death and reboots unchanged.
* **Event driven, never polling.** Repositories call `WidgetUpdater.tasksChanged()/shoppingChanged()/routinesChanged()`
  after each committed write. The updater coalesces bursts (300 ms quiet period, flushed at most 1.5 s after the first
  event) on the app scope and updates only the affected widget types:
  * tasks → Today, Agenda, Quick Add (list names and validity)
  * shopping → Shopping
  * routines → Routine
* `refreshAll()` updates every widget; it runs on boot, app update, time and time-zone changes and just after midnight
  (day rollover worker), so "today" is always today. Provider infos use `updatePeriodMillis="0"`.
* **Loading.** Each widget loads its data once per update with a few small one-shot queries (`provideGlance`). Glance
  keeps a session alive for a short time after an update and does not re-run `provideGlance` when `update()` is called
  on a live session, so each kind has an in-memory version counter (`WidgetSignals`). The updater bumps the counter
  before calling `update()`; a live session reloads when the counter changes, a closed session starts fresh. No
  database Flow is collected inside a widget session.
* **Theme.** Widgets follow the app's theme settings (system/light/dark, dynamic colour, accent), read on every
  update. Once the updater is running in the process (it starts with the first data change or widget refresh) it also
  watches the settings and redraws all widgets when the theme changes; otherwise the new theme shows on the next update.

## Look

* `GlanceTheme` with Material You dynamic colours (`DynamicThemeColorProviders`) on Android 12+ when dynamic colour is
  on and the theme follows the system. Otherwise `androidx.glance.material3.ColorProviders` built from the same
  accent-seeded light/dark schemes as the app (fern green `#2F6B4F`, dark `#8FD3AE`), day/night or forced by setting.
* Root: `widgetBackground` colour with the system widget corner radius on Android 12+ and a 20 dp rounded shape before.
  Rows are full-width tappable `surface` cards; check-off targets and round action buttons are 40 dp (32 dp on the
  smallest layouts) and every one has a content description.
* Typography: 16 sp titles, 14 sp rows, 12 sp metadata, a 30 sp count on compact layouts.
* Priority flags use the app's priority colours (high red, medium amber, low blue); overdue uses the theme's error
  colour.

## Platform constraints

* **RemoteViews only.** Widgets cannot host arbitrary views, animations or custom drawing. Glance maps composables to
  a fixed set of RemoteViews layouts; lists are RemoteViews collections (scrollable, but not reorderable).
* **No inline text entry.** App widgets cannot show an editable text field or open the keyboard. Quick Add and the
  shopping "add" button open the app (the Quick Add sheet or the list) instead of pretending to accept text.
* **Corner radius and colours before Android 12.** Rounded corners use a tinted shape drawable; check boxes are
  drawn with icons on every version so they look the same everywhere. Before Android 12 the light/dark choice is
  resolved when the widget renders, so a system dark-mode switch shows on the next update (at the latest after
  midnight or the next data change). Dynamic colour needs Android 12.
* **Launcher differences.** Cell sizes differ widely between launchers and devices, so layouts switch on measured dp,
  not cells. `targetCell*`, `maxResize*`, `description`, `previewLayout` and `widgetFeatures` are Android 12+ only;
  older launchers use `minWidth/minHeight` and always run the configuration activity when a widget is placed. Some
  third-party launchers ignore `configuration_optional`/`reconfigurable` — the "choose another" banner and the
  defaults keep the widget usable either way.
* **Update throttling.** The system may delay or batch widget updates (e.g. while the screen is off) and Glance
  rate-limits RemoteViews pushes; the widget catches up on the next update. Doze can delay the midnight worker by a
  few minutes.
* **AMOLED black** is not applied to widgets: the launcher draws them over the wallpaper and the system widget
  colours are used instead.
* Glance "generated previews" (Android 15) are not used; the picker shows the static `previewLayout` on Android 12+
  and the app icon before.

## Manual test checklist

Run on a phone with Android 12+ and, if possible, one with Android 8–11.

1. Widget picker lists all five Taskfern widgets with a description and preview (Android 12+).
2. **Today**: place at 2×2, 4×2, 4×3 and 4×4; check compact/medium/large layouts. Create tasks due today, overdue and
   with times/priorities in the app — the widget updates within a second.
3. Check off a task in the widget: it disappears and progress increases. Check off a daily recurring task: it
   disappears (next occurrence is tomorrow). Double-tap quickly: only one occurrence is completed (Completed view).
4. Tap a task row: the task editor opens. Tap Quick Add: the sheet opens with "Today" preselected.
5. Complete everything: "All clear for today" with full progress.
6. **Quick Add**: place it; on Android 12+ it is placed without configuration (Inbox). Long-press → reconfigure: pick a
   list and "Tomorrow"; the pill label updates and the sheet opens with those defaults. Delete that list in the app:
   the widget shows Inbox.
7. **Shopping**: place two instances showing two different lists. Check off items in the widget and confirm the app
   shows them checked; add an item in the app, it appears in the right widget only. Delete one list: that widget shows
   the "was deleted" banner and the first list; tap the banner and choose another list. Delete all lists: empty state.
8. **Agenda**: tasks for the next 7 days appear under the right day headers; overdue badge shows the count and opens
   Today; "Open Calendar" opens the calendar; tasks open on tap.
9. **Routine**: pick a routine; toggle steps on and off; progress updates. Pick a routine not scheduled today: "Not
   scheduled today · Next: …". Pause it in the app: "Paused". Delete it: banner + fallback / empty state.
10. Switch the app theme (light/dark, dynamic colour, accent) and the system dark mode: widgets follow.
11. Reboot the phone: all widgets show current data without opening the app. Change the time zone and the date:
    "today" follows. Leave the phone over midnight: Today and Agenda roll over.
12. Remove widgets from the home screen; re-add — no stale configuration is reused (fresh widget ids).
13. TalkBack: every button announces what it does ("Complete Buy milk", "Mark Eggs as bought", "Open Calendar").
14. Logcat shows no crashes or Glance errors while doing all of the above.
