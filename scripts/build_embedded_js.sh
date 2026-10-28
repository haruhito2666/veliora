#!/usr/bin/env bash
# Bundle backend/sofuu/embedded.js for the Android app's Sofuu runtime.
#
# The output is committed because the Android build does not require Node.
# Re-run this after changing backend/sofuu, then rebuild the APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p veliora/app/src/main/assets
npx --yes esbuild backend/sofuu/embedded.js \
    --bundle \
    --format=iife \
    --platform=neutral \
    --target=es2020 \
    --legal-comments=none \
    --outfile=veliora/app/src/main/assets/veliora-embedded.js

echo "✓ veliora/app/src/main/assets/veliora-embedded.js ($(du -h veliora/app/src/main/assets/veliora-embedded.js | cut -f1))"
