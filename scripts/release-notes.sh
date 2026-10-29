#!/usr/bin/env bash
# Writes Markdown release notes for <tag> to stdout: changes since the previous release tag, how to install, and how to
# check the download. usage: release-notes.sh <tag> <apk-file-name> <apk-sha256> <cert-sha256>
set -euo pipefail
tag="$1"; apk="$2"; sha="$3"; cert="$4"
repo="${GITHUB_REPOSITORY:-riyadmondol2006/Taskfern}"

prev=$(git tag --list 'v*' --sort=-v:refname | grep -vx "$tag" | head -n 1 || true)
if [[ -n $prev ]]; then
  range="$prev..HEAD"
  heading="Changes since $prev"
else
  range="HEAD"
  heading="Changes"
fi

echo "## $heading"
echo
# One line per commit subject; skip merge commits. Limited so very long histories stay readable.
git log --no-merges --format='- %s' "$range" | head -n 60
echo
echo "## Install"
echo
echo "1. Download **$apk** below on your Android phone (Android 8.0 or newer)."
echo "2. Open it and allow your browser or file manager to install unknown apps when Android asks."
echo "3. Updating: install the new APK over the old one. Your tasks stay; it must be signed with the same key (it is)."
echo
echo "## Verify the download"
echo
echo "- SHA-256 of \`$apk\`: \`$sha\` (also in \`$apk.sha256\`)"
echo "- Signing certificate SHA-256: \`$cert\`"
echo
echo "Only download Taskfern from https://github.com/$repo/releases."
