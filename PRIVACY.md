# Privacy

Taskfern is built to keep your plans on your phone.

- **No account, no sign-in.** There is nothing to register.
- **No internet access.** The app does not request Android's `INTERNET` permission, so it cannot send or receive
  anything over the network. A build check fails if any dependency tries to add it.
- **No ads, analytics, crash reporters or trackers.** No Google Play services or Firebase are included.
- **Your data stays local.** Tasks, lists, shopping lists, routines, templates and settings are stored in the app's
  private storage on your device.
- **Android cloud backup is turned off** for Taskfern (`allowBackup="false"` and data-extraction rules exclude
  everything), so your data is not copied to a cloud account behind your back.
- **Backups are files you control.** *Settings → Backup & restore* writes a JSON file to a place you pick with the
  Android file picker. Nothing is uploaded. Treat that file as private: it contains your tasks in plain text.
- **Sharing is explicit.** Sharing a shopping list sends plain text only to the app you choose in Android's share sheet.
- **Links** in the About screen (GitHub repository, releases, issues) open in your browser; the browser, not Taskfern,
  makes that connection.

Permissions Taskfern asks for:

| Permission | Why |
| --- | --- |
| Notifications | To show reminders, routine nudges, the optional daily agenda and focus-timer alerts |
| Alarms & reminders (exact alarms) | To deliver a reminder at the minute you chose; without it reminders may arrive a few minutes late |
| Run at startup | To re-arm your reminders after the phone restarts |

If you have questions, open an issue at https://github.com/riyadmondol2006/Taskfern/issues.
