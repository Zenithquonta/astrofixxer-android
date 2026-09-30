#!/usr/bin/env bash
# Google Play forbids apps that update themselves: fail if the release APK asks for the install permission.
#   tools/ci/check-play-build.sh app/build/outputs/apk/release/*.apk
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=apk-info.sh
. "$here/apk-info.sh"
[ "$#" -ge 1 ] || { echo "usage: check-play-build.sh RELEASE.apk"; exit 1; }
find_build_tools
for apk in "$@"; do
  [ -f "$apk" ] || { echo "::error::$apk does not exist"; exit 1; }
  if has_permission "$apk" REQUEST_INSTALL_PACKAGES; then
    echo "::error::$apk requests REQUEST_INSTALL_PACKAGES; Google Play builds must not (see app/src/preview/AndroidManifest.xml)"; exit 1
  fi
  echo "$apk: no self-update permission, as Google Play requires"
done
