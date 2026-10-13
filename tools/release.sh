#!/usr/bin/env bash
# Publish a SharkOS update to the updates-only GitHub repo the car checks.
#
#   tools/release.sh "What changed, in a sentence"
#
# Needs: keystore.properties + the key (same key as the car's install), and the GitHub CLI logged
# in (`gh auth login`) with push access to the updates repo named in gradle.properties
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

URL="https://github.com/$REPO/releases/download/v$NAME/SharkOS-$NAME.apk"
python3 - "$CODE" "$NAME" "$URL" "$NOTES" > dist/latest.json <<'PY'
import json, sys
c, n, u, notes = sys.argv[1:]
print(json.dumps({"versionCode": int(c), "versionName": n, "apkUrl": u, "notes": notes}, indent=2))
PY

# APK first, manifest second: the car must never see a latest.json pointing at a file that isn't up yet.
gh release create "v$NAME" "$APK" --repo "$REPO" --title "SharkOS $NAME" --notes "$NOTES"
TMP=$(mktemp -d)
gh repo clone "$REPO" "$TMP" -- -q --depth 1
cp dist/latest.json "$TMP/latest.json"
git -C "$TMP" add latest.json
git -C "$TMP" commit -q -m "SharkOS $NAME"
git -C "$TMP" push -q
echo "Published SharkOS $NAME. Cars pick it up within a day, or straight away from Settings → Updates."
