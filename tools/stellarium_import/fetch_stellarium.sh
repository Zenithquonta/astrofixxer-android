#!/usr/bin/env bash
# Sparse-checkout only the Stellarium data files the importer needs, at a pinned commit.
# Usage: fetch_stellarium.sh [target_dir]   (default: .cache/stellarium)
set -euo pipefail

STELLARIUM_COMMIT="${STELLARIUM_COMMIT:-9910a2f}"
TARGET="${1:-.cache/stellarium}"

if [ ! -d "$TARGET/.git" ]; then
  git clone --quiet --filter=blob:none --sparse --no-checkout \
    https://github.com/Stellarium/stellarium.git "$TARGET"
fi

git -C "$TARGET" sparse-checkout set \
  nebulae/default \
  skycultures/modern \
  skycultures/indian \
  plugins/MeteorShowers/resources
git -C "$TARGET" fetch --quiet --depth 1 origin "$STELLARIUM_COMMIT" 2>/dev/null \
  || git -C "$TARGET" fetch --quiet origin
git -C "$TARGET" checkout --quiet "$STELLARIUM_COMMIT"

echo "Stellarium data at $TARGET ($(git -C "$TARGET" rev-parse --short HEAD))"
