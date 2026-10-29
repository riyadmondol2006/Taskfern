#!/usr/bin/env bash
# Prints the release version for a GitHub Actions run as `name=value` lines for $GITHUB_OUTPUT:
#
#   version_code=42
#   version_name=1.0.42
#   tag=v1.0.42
#
# Scheme (docs/RELEASING.md): versionCode = VERSION_CODE_OFFSET + run number of the release workflow,
# versionName = <major>.<minor>.<versionCode>. Re-running a run keeps its run number, so a rerun produces the same
# version and tag (idempotent). Each new push gets a higher run number, so versions only ever grow.
set -euo pipefail

run_number="${1:?usage: release-version.sh <run-number>}"
major="${TASKFERN_MAJOR:-1}"
minor="${TASKFERN_MINOR:-0}"
offset="${VERSION_CODE_OFFSET:-0}"
max_code=2100000000 # Android's practical versionCode ceiling

[[ $run_number =~ ^[1-9][0-9]*$ ]] || { echo "release-version: run number '$run_number' is not a positive integer" >&2; exit 1; }
[[ $major =~ ^[0-9]+$ && $minor =~ ^[0-9]+$ && $offset =~ ^[0-9]+$ ]] || { echo "release-version: bad major/minor/offset" >&2; exit 1; }

code=$(( offset + run_number ))
if (( code < 1 || code > max_code )); then
  echo "release-version: versionCode $code is outside 1..$max_code" >&2
  exit 1
fi

name="$major.$minor.$code"
echo "version_code=$code"
echo "version_name=$name"
echo "tag=v$name"
