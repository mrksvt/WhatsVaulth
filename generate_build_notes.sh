#!/bin/bash

# Generate build notes for WaEnhancer
# Usage: ./generate_build_notes.sh [commit_hash]

COMMIT=${1:-HEAD}
SHORT_HASH=$(git rev-parse --short $COMMIT)
VERSION="1.5.5"

# Get supported versions from arrays.xml
SUPPORTED_VERSIONS=$(grep -A2 "supported_versions_wpp" app/src/main/res/values/arrays.xml | tail -2 | head -1 | sed -e 's/.*<item>//' -e 's/<\/item>.*//' -e 's/, / - /g')
SUPPORTED_VERSIONS="$SUPPORTED_VERSIONS - 2.26.30.xx"

# Get latest fixes (last 5 commits with "fix:" in message)
FIXES=$(git log --oneline --grep="fix:" --since="2026-07-01" --pretty=format:"• %s" | head -5 | sed -e 's/fix: //g' -e 's/• add /• /g' -e 's/• fix /• /g')

# Add PremiumMessageFix and AntiUpdater info
FIXES="$FIXES
• PremiumMessageFix - WAB 2.26.30 schema migration (no duplicate column errors)
• AntiUpdater removed - no more update checks in all builds"

# Get APK size
APK_SIZE=$(ls -lh /home/mrksvt/Documents/Coding/Android/WaEnhancer/app/build/outputs/apk/whatsapp/release/WaEnhancer-*.apk | awk '{print $5}')

# Generate notes
cat <<EOF
WaEnhancer $VERSION ($SHORT_HASH) [$APK_SIZE]

Support WA + WAB $SUPPORTED_VERSIONS

Fix:
$FIXES
EOF