# Taskfern: Product Research

## 1. Scope and method

- **Date:** 2026-10-29
- **Question:** Which problems do people publicly report with mainstream Android to-do, planner and shopping-list apps, and which of them can an offline-first, account-free app (Kotlin, Compose, Material 3, widgets) address?
- **Apps covered:** Google Tasks, Microsoft To Do, TickTick, Tasks.org, Todoist, AnyList and Bring!, plus Listonic and Out of Milk for a light look at shopping lists.
- **Sources consulted (all opened during this research):**
  - Official help and support pages: Microsoft Support, Todoist Help, Tasks.org docs, AnyList Help, TickTick's Android reminder guide, Android Developers.
  - Official community forums: Microsoft Tech Community (To Do).
  - Tasks.org GitHub issues (`github.com/tasks/tasks`), read with the `gh` CLI.
  - dontkillmyapp.com.
  - Press reviews: Android Authority (2026), TapSmart (2018–2020), Ctrl blog (2018).
  - User reviews on Trustpilot (AnyList).
  - Google Groups (Full Screen for Google Tasks).
  - A TasksBoard vendor blog about Google Tasks. This is a commercial source and is marked as such where cited.
- **Limitations:**
  - **Reddit could not be read.** reddit.com blocked automated fetches, old.reddit required a login, and the mirrors I tried failed. Reddit threads that showed up in search results are therefore **not cited**.
  - **Google Play reviews were not read directly.** A Kimola review-analysis page for Bring! returned HTTP 410 and was dropped.
  - **This is not a survey.** It shows which problems recur in public channels, not how common they are. No popularity or percentage claims are made.
  - **Some sources are old.** Ctrl blog (2018) and TapSmart (2018–2020) may describe behaviour that has since changed. Each is flagged inline.
  - **Some themes have thin evidence.** Slow startup, slow shopping-list editing and cluttered dashboards have little direct support. They are labelled "weak evidence" below.

## 2. User problems identified

| # | Problem | Evidence summary | Sources |
|---|---------|------------------|---------|
| P1 | **Unreliable or late reminders** | Microsoft says To Do reminders "may get suppressed by the Android operating system" and gives battery-exemption steps for Samsung, Xiaomi, Huawei, OnePlus, OPPO and other OEMs. Forum users report missed reminders on Android (2021–2022), and one calls the app "never properly worked as a reminder solution". Tasks.org has issues on late notifications even with battery optimization off (#604, #766). Its own docs send users to dontkillmyapp.com, which rates Samsung, Xiaomi, OnePlus and Huawei at its worst score of 5. TickTick's guide says security apps "might block notification from TickTick". | [MS Support](https://support.microsoft.com/en-us/todo/troubleshoot-reminder-issues-in-microsoft-to-do-for-android), [MS Tech Community thread](https://techcommunity.microsoft.com/discussions/to-do/to-do-task-reminders-not-always-working/2082879), [Tasks.org troubleshooting](https://tasks.org/docs/troubleshooting_notifications), [tasks#604](https://github.com/tasks/tasks/issues/604), [tasks#766](https://github.com/tasks/tasks/issues/766), [dontkillmyapp.com](https://dontkillmyapp.com/), [TickTick guide](https://ticktick.com/public/android-reminder/html/en/index.html) |
| P2 | **Exact-alarm permission changes** (platform constraint) | On Android 14, `SCHEDULE_EXACT_ALARM` is "denied by default" for most new installs targeting API 33+. Without it, `setExact`, `setExactAndAllowWhileIdle` and `setAlarmClock` throw `SecurityException`. Google tells apps to check `canScheduleExactAlarms()` and to "gracefully degrade". A DEV article (anecdotal) argues that WorkManager is wrong for time-critical reminders. | [Android Developers](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [DEV article](https://dev.to/amit_raz_4280cb3a49bb4086/why-your-android-reminder-app-is-silently-failing-you-and-how-to-fix-it-2ff2) |
| P3 | **Recurring tasks behave unexpectedly** | Microsoft To Do users have asked since 2021 for "Repeat x days/weeks/months/years after completion", and one reply says "There is no workaround". Microsoft's support page says the next recurring reminder needs the previous task completed *and* an internet connection. In Google Tasks, a completed recurring task disappears until the next day (user reports, 2021). Tasks.org has a bug where a recurring task's notification came back after it was completed, and completing it again advanced the next occurrence (#2876). Todoist and Tasks.org both offer an explicit "from due date / from completion" choice, but Todoist hides it behind `every` vs `every!` syntax. | [MS flexible recurrence](https://techcommunity.microsoft.com/discussions/to-do/flexible-recurrence-needed-feature/2815541), [MS Support](https://support.microsoft.com/en-us/todo/troubleshoot-reminder-issues-in-microsoft-to-do-for-android), [Google Groups](https://groups.google.com/g/full-screen-for-google-tasks/c/qiYgfDWc790), [tasks#2876](https://github.com/tasks/tasks/issues/2876), [Todoist recurring](https://www.todoist.com/help/articles/introduction-to-recurring-dates-YUYVJJAV), [Tasks.org recurrence](https://www.tasks.org/docs/recurrence) |
| P4 | **Too many taps to capture a task** | A Tasks.org issue (open since 2018) says the Material redesign removed an inline entry bar. Adding a task became "+, enter title, save", which hurts bulk entry "(think the shopping list...)". A separate request (#904) asks for a compact quick-add shortcut. | [tasks#735](https://github.com/tasks/tasks/issues/735), [tasks#904](https://github.com/tasks/tasks/issues/904) |
| P5 | **Weak or non-interactive widgets** | Microsoft's To Do widget help covers adding tasks to My Day and viewing tasks. It does not mention completing them. An AnyList reviewer (2025) says the widget does not sync "unless you open the app up", and AnyList replied that it is "at the mercy of iOS and Android". Tasks.org has widget flickering issues (#1159, #3902, the latter open) and a widget that ignored the chosen sort order (#1928). Examples that work well: AnyList's Android widget, where "Tapping on an item will cross it off", and Todoist's task list widget, though check-off is "not available in Compact mode". | [MS widget help](https://support.microsoft.com/en-us/todo/using-the-android-widget-with-microsoft-to-do), [Trustpilot AnyList](https://www.trustpilot.com/review/anylist.com), [tasks#3902](https://github.com/tasks/tasks/issues/3902), [tasks#1159](https://github.com/tasks/tasks/issues/1159), [tasks#1928](https://github.com/tasks/tasks/issues/1928), [AnyList widgets](https://help.anylist.com/articles/anylist-widgets/), [Todoist widgets](https://www.todoist.com/help/articles/use-a-todoist-widget-on-your-android-device-632pZA) |
| P6 | **Paywalled basics** | Todoist's free plan caps active personal projects at 5 and filters at 3. Custom, time-based, location-based and recurring *reminders* are Pro/Business only, and daily automatic backups are not on the free plan. On Trustpilot, AnyList users report losing web access after a paid period lapsed, and one calls pricing tools "a premium feature by the way" (2026). TapSmart's 2019 update criticises Bring!'s monthly fee. Android Authority (2026) instead describes TickTick's free features as "a major selling point". I did not verify TickTick's paywall details. | [Todoist plans](https://www.todoist.com/help/articles/todoist-plans-pricing), [Trustpilot AnyList](https://www.trustpilot.com/review/anylist.com), [TapSmart Bring!](https://tapsmart.com/apps/review-bring-shopping-lists-made-easy), [Android Authority TickTick](https://www.androidauthority.com/keep-coming-back-to-ticktick-3719344/) |
| P7 | **Forced accounts and account failures** | TapSmart's "only real complaint" about Bring! was that "you can't use it without registering for an account" (2018 text). Microsoft To Do's supported accounts are Microsoft personal accounts or licensed work/school accounts. In 2025, Android users reported a sign-in loop with endless loading and lists missing on every platform, with no official fix posted. | [TapSmart Bring!](https://tapsmart.com/apps/review-bring-shopping-lists-made-easy), [MS To Do requirements](https://support.microsoft.com/en-us/todo/microsoft-to-do-system-requirements), [MS login loop thread](https://techcommunity.microsoft.com/discussions/to-do/microsoft-to-do-login-issue-and-missing-tasks-across-platforms/4431858) |
| P8 | **Sync makes the app slow or loses edits** | A Tasks.org issue (open) reports that with CalDAV sync "the app is slow" and "local changes sometimes reverted". Ctrl blog (2018, possibly outdated) said Microsoft To Do edits "sometimes just disappear" and the Android app freezes "for upwards to ten seconds". | [tasks#2856](https://github.com/tasks/tasks/issues/2856), [Ctrl blog](https://www.ctrl.blog/entry/microsoft-not-todo) |
| P9 | **No proper backup or export** | Google Tasks has no in-app export. The route is Google Takeout, documented by a third-party converter project and a vendor blog. Todoist's free plan has no daily automatic backups. A Tasks.org user lost a day of tasks after a ROM crash and asked for configurable backup frequency (#495, open since 2017). Ctrl blog (2018) reported a Microsoft To Do export taking "over 12 weeks". | [GoogleTaskConverter](https://github.com/LukaszLapaj/GoogleTaskConverter), [TasksBoard export guide (vendor)](https://tasksboard.com/blog/google-tasks-export-guide), [Todoist plans](https://www.todoist.com/help/articles/todoist-plans-pricing), [tasks#495](https://github.com/tasks/tasks/issues/495), [Ctrl blog](https://www.ctrl.blog/entry/microsoft-not-todo) |
| P10 | **Priority systems: missing or confusing** | A vendor blog (2026) says Google Tasks has "no priority field". Todoist's four levels run in reverse, with P1 the highest and P4 preselected by default, and the names and colours cannot be changed. One blogger found P4 "didn't even feel like a priority level". | [TasksBoard priority (vendor)](https://tasksboard.com/blog/google-tasks-priority-guide), [Todoist priorities](https://todoist.com/help/articles/introduction-to-priorities-Wy82Jp), [Hulry blog](https://hulry.com/todoist-priority-levels-moscow/) |
| P11 | **Cluttered or buried UI** *(weak evidence)* | Android Authority (Oct 2026) says TickTick "buries its own features" and that its flows "aren't consistent", with the location option "just absent" unless you open the full-screen task view. A 2026 article calls Google Tasks "very basic, to a fault", which shows that too few features is also a complaint. | [Android Authority TickTick](https://www.androidauthority.com/keep-coming-back-to-ticktick-3719344/), [Android Authority Google Tasks](https://www.androidauthority.com/google-tasks-bad-to-do-list-app-still-use-why-3712518/) |
| P12 | **Dark mode gaps** *(weak evidence)* | Tasks.org has requests to let "System default" use pure-black dark mode (#1622, open), with an OLED power argument. A 2023 request asks for a black top bar to match the black theme (#2162). A 2025 issue reports washed-out checkbox and priority colours (#3270). | [tasks#1622](https://github.com/tasks/tasks/issues/1622), [tasks#2162](https://github.com/tasks/tasks/issues/2162), [tasks#3270](https://github.com/tasks/tasks/issues/3270) |
| P13 | **Slow shopping-list entry; ads in list apps** *(weak evidence)* | The main evidence is the Tasks.org bulk-entry complaint (P4), which uses a shopping list as its example. A Listonic Trustpilot reviewer complained about ads, and Listonic replied that "we need ads in our app to keep it 100% free". I found no verified evidence on Bring! offline behaviour. | [tasks#735](https://github.com/tasks/tasks/issues/735), [Trustpilot Listonic](https://ie.trustpilot.com/review/listonic.com) |

**Not verified in this pass:** slow cold start as a specific complaint (only indirect evidence in P8), the Google Tasks widget missing check-off for recurring tasks (seen only in an unreadable Reddit thread), and TickTick pricing details.

## 3. Selected feature responses for Taskfern

| Problem | Taskfern response |
|---------|-------------------|
| P1, P2 reminders | Schedule with `AlarmManager.setExactAndAllowWhileIdle()` when `canScheduleExactAlarms()` is true. Otherwise fall back to `setAndAllowWhileIdle()` / `setWindow()` and show a non-blocking banner explaining the trade-off. Re-register alarms on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and app update. Add a **Reminder health** screen that shows notification permission, exact-alarm permission and battery-optimization status, with deep links. For Samsung, Xiaomi, OnePlus and Huawei, add an OEM hint linking to dontkillmyapp.com. Never use WorkManager for user-set times. |
| P3 recurrence | Recurrence editor with an explicit, labelled choice: **"Repeat from due date"** or **"Repeat from completion date"** (no hidden syntax). Generate the next occurrence locally and immediately on completion (no network dependency), and show it inline ("Next: Tue 4 Nov"). Completion is idempotent: re-tapping a stale notification must not skip an occurrence (see tasks#2876). |
| P4 capture friction | **One-tap quick-add sheet** from the FAB, the widget, a launcher shortcut and a Quick Settings tile. The keyboard is focused on open, Enter saves and keeps the sheet open for the next item, and optional chips cover date, priority and list. Local date parsing ("tomorrow 9am") is a Could. |
| P5 widgets | **Glance widgets** that are interactive: check off a task or shopping item in place via `ActionCallback`, a "+" that opens the quick-add sheet, and updates driven from Room writes (no app launch needed). Ship Today, List and Shopping widgets, test with large font sizes, and avoid full-widget redraw flicker. |
| P6 paywalls | Everything is free and local, with no feature tiers. Reminders, recurrence, backup and widgets are core, not upsells. |
| P7 accounts | No account and no sign-in screen. The app is usable within seconds of first launch. |
| P8 sync slowness | Single local Room database with no sync layer, so no "local changes reverted". All writes go to disk immediately. |
| P9 backup/export | **Local JSON backup and restore via Storage Access Framework** (user picks the file or folder, no storage permission). Optional scheduled auto-backup to a user-chosen folder with rotation. Versioned schema, restore preview, and CSV/plain-text export of lists. |
| P10 priority | **Three priority levels (High / Medium / Low, default none)** with fixed, readable labels, plus a separate **"Important" flag** for "pin to top". Labels are never inverted. |
| P11 clutter | **Today-first home**: overdue, today, then "later" collapsed. Bottom navigation with at most 4 destinations (Today, Lists, Shopping, Routines). Advanced fields are behind "More" in the editor. No dashboards or stats on the home screen. |
| P12 dark mode | Material 3 light/dark following the system, an optional **AMOLED pure-black** variant that also applies under "System default", dynamic colour on Android 12+, and contrast-checked priority and checkbox colours. |
| P13 shopping | **Shopping list with inline add and check-off** (no detail screen needed), auto-assigned **categories / aisles** with the list grouped by category, checked items sinking to a collapsible "In cart" section, and **buy-again suggestions** from local purchase history. Optional quantity and unit fields. No ads. |

## 4. Prioritization

**Must (v1)**
- Room-backed local data model for tasks, lists, shopping items and routines. No account, no network permission.
- Quick-add sheet with keyboard focus and multi-add.
- Reminders: exact alarms with inexact fallback, boot/time-change rescheduling, Reminder health screen.
- Recurrence with an explicit "from due date / from completion" choice. Idempotent completion.
- Interactive Glance widgets: Today and Shopping, with check-off and add.
- Shopping list: inline add and check-off, categories, "In cart" section.
- JSON backup and restore via SAF.
- 3-level priority plus Important flag.
- Light/dark/system themes, plus AMOLED black.

**Should**
- Routines (daily/weekly checklists that reset) with their own widget.
- Buy-again suggestions and item autocomplete from history.
- Scheduled auto-backup to a user folder. CSV/text export.
- Notification actions: Done and Snooze (re-scheduled locally).
- OEM-specific battery guidance (deep links plus dontkillmyapp.com).

**Could**
- On-device date parsing in quick add (rule-based, no cloud).
- Quick Settings tile and app shortcuts.
- Per-list colours and icons, and Material You dynamic colour toggle.
- Import from Google Tasks Takeout `Tasks.json`.

## 5. Features intentionally excluded

| Excluded | Reason |
|----------|--------|
| Cloud sync | Adds network, servers and conflict resolution. P8 shows sync can make apps slow and revert edits, and offline-first with no network is the product promise. SAF backup covers data safety. |
| Accounts / sign-in | P7 shows forced sign-up is a recurring complaint and sign-in failures can lock users out. With no server, an account has no purpose. |
| AI / cloud NLP | Needs the network and sends user text off-device. Todoist meters its AI features even on paid tiers. A rule-based local parser is the offline substitute (Could). |
| Live collaboration / shared lists | Requires sync and accounts. Shared lists in Bring! and AnyList are account-based cloud features (TapSmart notes Bring! needs an account). Sharing a list as plain text through the Android share sheet is the offline compromise. |
| Location reminders | Needs background location permission, drains the battery, and is hard to make reliable under the same OEM restrictions as P1. Android Authority lists it as missing in Google Tasks, but it is outside v1 scope. |
| Google Play publishing (for now) | Out of scope for the current build. Note that on Play, `USE_EXACT_ALARM` is limited to apps that meet policy criteria, so a future listing would rely on `SCHEDULE_EXACT_ALARM` plus a fallback. |
| Gamification (karma, streaks, stats) | Not linked to any problem found here. It adds dashboard clutter (P11) and pressure. |

## 6. Product design rationale

1. **Reliability is the product.** The most consistent evidence across apps is about reminders that do not fire or fire late (P1, P2). Taskfern cannot override OEM battery killers, but it can use the strongest allowed alarm API, degrade honestly, and *show* users what is blocking reminders instead of failing silently.
2. **Local-first removes whole problem classes.** No account (P7), no sync lag or reverted edits (P8), and no paywalled basics (P6) come almost for free when there is no server. The cost is no multi-device use or sharing, which is mitigated by user-owned JSON backups (P9).
3. **Make hidden rules explicit.** Recurrence anchoring (P3) and priority meaning (P10) confuse people when they are implicit (Todoist's `every!`, reversed P1–P4) or missing (Microsoft To Do, Google Tasks). Taskfern shows these as plain-language choices at the point of use.
4. **Capture and complete in one place.** The fastest path, from widget or quick-add to saved, should take one tap plus typing (P4). Completion should work wherever the task is shown: widget, notification or list (P5).
5. **Shopping is its own mode.** Grocery lists are entered in bursts and checked off in store aisles. Inline entry, category grouping and buy-again suggestions fit that better than a generic task editor (P13).
6. **Restraint over feature count.** Reviews criticise both an overloaded app (TickTick, P11) and an overly basic one (Google Tasks). Taskfern aims for a small, complete core: tasks, shopping, routines, reminders and widgets. Anything that needs a network or adds dashboards is deliberately excluded (Section 5).
