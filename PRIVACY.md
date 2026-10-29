# Privacy

Taskfern is built to keep your plans on your phone.

- **No account, no sign-in.** There is nothing to register.
- **One optional network request: the update check.** Taskfern has Android's `INTERNET` permission only to check
  for new versions. On the first start of a release build it asks whether to check automatically; nothing is sent
  until you agree. When it is on, the app makes **one HTTPS request to `api.github.com` at most once a day** to read
  the newest release of https://github.com/riyadmondol2006/Taskfern (its version number, release notes and download
  link). The request carries only standard headers (including a `Taskfern/<version>` user agent): no account, no
  device identifier, nothing about you or your tasks. Like any web request, GitHub sees your IP address and handles it
  under [GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).
  *Settings → Updates → Check now* makes the same request when you tap it. Turn automatic checks off any time in
  *Settings → Updates*. Taskfern never downloads or installs an update itself: **Download** opens the file in your
  browser.
- **No other network use.** A build check fails if any dependency adds network-state, Wi-Fi-state, broad storage or
  advertising-ID permissions.
- **No ads, analytics, crash reporters or trackers.** No Google Play services or Firebase are included.
- **Your data stays local.** Tasks, lists, shopping lists, routines, templates and settings are stored in the app's
  private storage on your device.
- **Android cloud backup is turned off** for Taskfern (`allowBackup="false"` and data-extraction rules exclude
  everything), so your data is not copied to a cloud account behind your back.
- **Backups are files you control.** *Settings → Backup & restore* writes a JSON file to a place you pick with the
  Android file picker. Nothing is uploaded. Treat that file as private: it contains your tasks in plain text.
- **Sharing is explicit.** Sharing a shopping list sends plain text only to the app you choose in Android's share sheet.
- **Links** in the About screen (GitHub repository, releases, issues) and the update popup's **Download** button open
  in your browser; the browser, not Taskfern, makes that connection.

Permissions Taskfern asks for:

| Permission | Why |
| --- | --- |
| Notifications | To show reminders, routine nudges, the optional daily agenda and focus-timer alerts |
| Alarms & reminders (exact alarms) | To deliver a reminder at the minute you chose; without it reminders may arrive a few minutes late |
| Run at startup | To re-arm your reminders after the phone restarts |
| Internet (granted at install, no prompt) | Only for the optional update check described above |

If you have questions, open an issue at https://github.com/riyadmondol2006/Taskfern/issues.
