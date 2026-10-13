#!/usr/bin/env bash
# Publish a SharkOS update to the updates-only GitHub repo the car checks.
#
#   tools/release.sh "What changed, in a sentence"
#
# Needs: keystore.properties + the key (same key as the car's install), and git push access to the
# updates repo named in gradle.properties
# (sharkos.updateRepo=<user>/sharkos-updates). Bump versionCode/versionName in app/build.gradle.kts first.
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES="${1:-}"
REPO=$(grep -E '^sharkos.updateRepo=' gradle.properties | cut -d= -f2)
[ -n "$REPO" ] || { echo "Set sharkos.updateRepo=<user>/sharkos-updates in gradle.properties"; exit 1; }
[ -f keystore.properties ] || { echo "keystore.properties missing: updates must be signed with the car's key"; exit 1; }

CODE=$(grep -E 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
NAME=$(grep -E 'versionName = "' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
APK="dist/SharkOS-$NAME.apk"

./gradlew -q :app:assembleRelease
mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk "$APK"

# The APK lives in the updates repo itself (apk/), so publishing is a single git push: no release API.
URL="https://raw.githubusercontent.com/$REPO/main/apk/SharkOS-$NAME.apk"
python3 - "$CODE" "$NAME" "$URL" "$NOTES" > dist/latest.json <<'PY'
import json, sys
c, n, u, notes = sys.argv[1:]
print(json.dumps({"versionCode": int(c), "versionName": n, "apkUrl": u, "notes": notes}, indent=2))
PY

# One commit carries both: the APK and the latest.json pointing at it land together.
TMP=$(mktemp -d)
git clone -q --depth 1 "https://github.com/$REPO" "$TMP"
mkdir -p "$TMP/apk" && cp "$APK" "$TMP/apk/" && cp dist/latest.json "$TMP/latest.json"
git -C "$TMP" add -A
git -C "$TMP" commit -q -m "SharkOS $NAME"
git -C "$TMP" push -q
echo "Published SharkOS $NAME. Cars pick it up within a day, or straight away from Settings → Updates."
