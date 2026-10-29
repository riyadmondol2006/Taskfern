#!/usr/bin/env bash
# Verifies a release APK before publishing: valid v2+ signature, expected application id, version name and version code,
# and no permission beyond what Taskfern needs.
# Prints `cert_sha256=<fingerprint>` and `apk_sha256=<hash>` for $GITHUB_OUTPUT. Never prints secrets.
#
# usage: verify-apk.sh <apk> <application-id> <version-name> <version-code>
set -euo pipefail

apk="$1"; app_id="$2"; version_name="$3"; version_code="$4"
[[ -f $apk ]] || { echo "verify-apk: $apk not found" >&2; exit 1; }

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
bt=$(ls -1 "$sdk/build-tools" | sort -V | tail -n 1)
apksigner="$sdk/build-tools/$bt/apksigner"
aapt2="$sdk/build-tools/$bt/aapt2"

# Signature: must verify with APK Signature Scheme v2 or newer.
verify_out=$("$apksigner" verify --verbose --print-certs "$apk")
grep -Eq "Verified using v(2|3|3\.1|4) scheme \(APK Signature Scheme v[234](\.[0-9])?\): true" <<< "$verify_out" \
  || { echo "verify-apk: APK is not signed with signature scheme v2 or newer" >&2; echo "$verify_out" | grep -i verified >&2; exit 1; }
if grep -q "CN=Android Debug" <<< "$verify_out"; then
  echo "verify-apk: refusing to publish an APK signed with a debug key" >&2
  exit 1
fi
# apksigner prints "Signer #1 certificate SHA-256 digest:" or, in newer versions, "V2 Signer: certificate SHA-256 digest:".
cert=$(grep -m1 -E "Signer.*certificate SHA-256 digest:" <<< "$verify_out" | awk -F'digest: ' '{print $2}' || true)
[[ -n $cert ]] || { echo "verify-apk: could not read the signing certificate" >&2; exit 1; }

# Identity and version.
badging=$("$aapt2" dump badging "$apk")
badging=$(grep -m1 "^package:" <<< "$badging" || true)
pkg=$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" <<< "$badging")
vcode=$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")
vname=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$badging")
[[ $pkg == "$app_id" ]] || { echo "verify-apk: application id is '$pkg', expected '$app_id'" >&2; exit 1; }
[[ $vcode == "$version_code" ]] || { echo "verify-apk: versionCode is '$vcode', expected '$version_code'" >&2; exit 1; }
[[ $vname == "$version_name" ]] || { echo "verify-apk: versionName is '$vname', expected '$version_name'" >&2; exit 1; }

# Permissions: INTERNET is allowed for the optional update check only. Network-state, Wi-Fi-state, broad storage and
# advertising-ID permissions must never ship.
permissions=$("$aapt2" dump permissions "$apk")
for forbidden in \
  android.permission.ACCESS_NETWORK_STATE \
  android.permission.ACCESS_WIFI_STATE \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.MANAGE_EXTERNAL_STORAGE \
  com.google.android.gms.permission.AD_ID; do
  if grep -q "name='$forbidden'" <<< "$permissions"; then
    echo "verify-apk: the APK requests $forbidden, which Taskfern must not do" >&2
    exit 1
  fi
done

echo "verify-apk: OK $pkg $vname ($vcode), signer SHA-256 $cert" >&2
echo "cert_sha256=$cert"
echo "apk_sha256=$(sha256sum "$apk" | awk '{print $1}')"
