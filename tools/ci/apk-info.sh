#!/usr/bin/env bash
# Sourced by the other scripts here. Finds the Android SDK build-tools and reads an APK's identity from the APK itself.
#   find_build_tools     sets AAPT2 and BUILD_TOOLS (override with AAPT2=/path/to/aapt2 to test elsewhere)
#   read_badging APK     sets APP_ID, VERSION_CODE, VERSION_NAME from `aapt2 dump badging`
# Never take the version from an environment variable: the point is to report what is really inside the APK.

find_build_tools() {
  if [ -n "${AAPT2:-}" ]; then BUILD_TOOLS=$(dirname "$AAPT2"); return; fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || { echo "::error::Android SDK build-tools not found (set ANDROID_HOME)"; exit 1; }
  local newest
  newest=$(ls -1 "$sdk/build-tools" | sort -V | tail -n 1)
  [ -n "$newest" ] || { echo "::error::no build-tools version in $sdk/build-tools"; exit 1; }
  BUILD_TOOLS="$sdk/build-tools/$newest"
  AAPT2="$BUILD_TOOLS/aapt2"
}

read_badging() {
  local apk="$1" badging line
  [ -f "$apk" ] || { echo "::error::$apk does not exist"; exit 1; }
  # Capture everything first: piping straight into `grep -m1` can kill aapt2 with SIGPIPE, which pipefail turns into a failure.
  badging=$("$AAPT2" dump badging "$apk") || { echo "::error::aapt2 could not read $apk"; exit 1; }
  line=$(grep -m1 '^package:' <<<"$badging") || { echo "::error::no package line in the badging of $apk"; exit 1; }
  APP_ID=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$line")
  VERSION_CODE=$(sed -n "s/.* versionCode='\([0-9]*\)'.*/\1/p" <<<"$line")
  VERSION_NAME=$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<<"$line")
  if [ -z "$APP_ID" ] || [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
    echo "::error::could not read applicationId, versionCode and versionName from: $line"; exit 1
  fi
}

# has_permission APK NAME: true when the APK's manifest requests android.permission.NAME.
has_permission() {
  local perms
  perms=$("$AAPT2" dump permissions "$1") || { echo "::error::aapt2 could not read the permissions of $1"; exit 1; }
  grep -q "android.permission.$2'" <<<"$perms"
}
