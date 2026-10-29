# Security policy

## Supported versions

Only the latest release on https://github.com/riyadmondol2006/Taskfern/releases/latest receives fixes.

## Reporting a vulnerability

Please do **not** open a public issue for security problems. Use GitHub's private vulnerability reporting
("Security" tab → "Report a vulnerability") on this repository. Include steps to reproduce, the Taskfern version
(Settings → About) and your Android version. You will get a reply as soon as possible.

## How releases are protected

- Every official APK is built by GitHub Actions from the tagged commit on `main` and signed with the project's release
  key. The signing key is stored only as encrypted GitHub Actions secrets; it is never committed.
- Each release lists the APK's SHA-256 checksum and the signing certificate's SHA-256 fingerprint. Android refuses to
  update an installed app with an APK signed by a different key, which protects existing installs from tampered files.
- Download Taskfern only from the official repository's Releases page.

## Scope notes

Taskfern has no server and no accounts, and its only network use is the optional update check (one HTTPS GET to
`api.github.com`, response capped at 256 KB and parsed leniently, links accepted only over HTTPS), so most web-style
attacks do not apply. Backup files are plain JSON that you choose where to store; protect them like any other
personal document.
