#!/usr/bin/env bash
# Integrity check of a built APK against its update.json; any mismatch fails the job:
#   tools/ci/verify-apk.sh AstroFixxer.apk update.json
# - apksigner verifies the APK signature (and prints the certificate, for the log);
# - aapt2's versionCode, versionName and applicationId must equal update.json's;
# - update.json's apk name, sha256 and size must describe this very file;
# - optional: EXPECTED_VERSION_CODE (what the build was told to use) and EXPECTED_APPLICATION_ID must match too.
set -euo pipefail
apk="${1:?usage: verify-apk.sh APK update.json}"
json="${2:?usage: verify-apk.sh APK update.json}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=apk-info.sh
. "$here/apk-info.sh"

find_build_tools
APKSIGNER="${APKSIGNER:-$BUILD_TOOLS/apksigner}"
"$APKSIGNER" verify --print-certs "$apk" || { echo "::error::apksigner rejected $apk"; exit 1; }
read_badging "$apk"

fail=0
same() { # what, from the APK, from update.json
  if [ "$2" != "$3" ]; then echo "::error::$1 differs: APK says '$2', update.json says '$3'"; fail=1; fi
}
same versionCode "$VERSION_CODE" "$(jq -r '.versionCode' "$json")"
same versionName "$VERSION_NAME" "$(jq -r '.versionName' "$json")"
same applicationId "$APP_ID" "$(jq -r '.applicationId' "$json")"
same apk "$(basename "$apk")" "$(jq -r '.apk' "$json")"
same sha256 "$(sha256sum "$apk" | cut -d' ' -f1)" "$(jq -r '.sha256' "$json")"
same size "$(stat -c %s "$apk")" "$(jq -r '.size' "$json")"
[ "$(jq -r '.permanentKey | type' "$json")" = boolean ] || { echo "::error::permanentKey in update.json is not true or false"; fail=1; }
[ -z "${EXPECTED_VERSION_CODE:-}" ] || same "versionCode (expected from the build number)" "$VERSION_CODE" "$EXPECTED_VERSION_CODE"
[ -z "${EXPECTED_APPLICATION_ID:-}" ] || same "applicationId (expected)" "$APP_ID" "$EXPECTED_APPLICATION_ID"
# The updater installs this file, so it must be allowed to.
has_permission "$apk" REQUEST_INSTALL_PACKAGES || { echo "::error::the preview APK lacks REQUEST_INSTALL_PACKAGES, so its updater could not install"; fail=1; }
[ "$fail" = 0 ] || exit 1
echo "APK integrity OK: $APP_ID versionCode $VERSION_CODE ($VERSION_NAME)"
