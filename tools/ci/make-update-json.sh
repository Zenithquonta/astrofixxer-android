#!/usr/bin/env bash
# Writes update.json for the in-app updater, describing an APK:
#   tools/ci/make-update-json.sh AstroFixxer.apk update.json
# versionCode, versionName and applicationId come from the built APK (aapt2), not from the environment.
# Environment: GITHUB_SHA (the commit) and PERMANENT_KEY=true|false (signed with the permanent preview key, or the throwaway debug key).
set -euo pipefail
apk="${1:?usage: make-update-json.sh APK OUT.json}"
out="${2:?usage: make-update-json.sh APK OUT.json}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=apk-info.sh
. "$here/apk-info.sh"

case "${PERMANENT_KEY:-}" in true|false) ;; *) echo "::error::PERMANENT_KEY must be true or false"; exit 1 ;; esac
find_build_tools
read_badging "$apk"

sha256=$(sha256sum "$apk" | cut -d' ' -f1)
size=$(stat -c %s "$apk")
[ "$size" -gt 0 ] || { echo "::error::$apk is empty"; exit 1; }

jq -n \
  --argjson versionCode "$VERSION_CODE" --arg versionName "$VERSION_NAME" --arg applicationId "$APP_ID" \
  --arg apk "$(basename "$apk")" --arg sha256 "$sha256" --argjson size "$size" \
  --arg commit "${GITHUB_SHA:-}" --argjson permanentKey "$PERMANENT_KEY" \
  '{versionCode: $versionCode, versionName: $versionName, applicationId: $applicationId, apk: $apk, sha256: $sha256, size: $size, commit: $commit, permanentKey: $permanentKey}' \
  > "$out"
