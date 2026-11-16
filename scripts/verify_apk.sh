#!/usr/bin/env bash
set -euo pipefail
build_tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
apk=app/build/outputs/apk/release/app-release.apk
"$build_tools/apksigner" verify --verbose --print-certs "$apk" > "$RUNNER_TEMP/new-cert.txt"
python3 scripts/verify_certificate.py "$RUNNER_TEMP/new-cert.txt"
"$build_tools/aapt" dump badging "$apk" > "$RUNNER_TEMP/badging.txt"
python3 - "$RUNNER_TEMP/badging.txt" <<'PY'
import os, re, sys
s = open(sys.argv[1]).read()
assert re.search(r"package: name='fi.omasali.app'", s)
assert f"versionCode='{os.environ['VERSION_CODE']}'" in s
assert f"versionName='{os.environ['VERSION_NAME']}'" in s
assert "application-debuggable" not in s
PY
# Listing succeeds or fails explicitly; network/API errors must not bypass certificate checks.
gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$RUNNER_TEMP/releases.json"
previous=$(python3 - "$RUNNER_TEMP/releases.json" <<'PY'
import json, sys
rs=[r for r in json.load(open(sys.argv[1])) if not r['draft'] and not r['prerelease'] and any(a['name'].endswith('.apk') for a in r['assets'])]
print(rs[0]['tag_name'] if rs else '')
PY
)
if [ -n "$previous" ]; then
  mkdir -p "$RUNNER_TEMP/previous"
  gh release download "$previous" --repo "$GITHUB_REPOSITORY" --pattern 'OMA-SALI-*.apk' --dir "$RUNNER_TEMP/previous"
  old_apk=$(find "$RUNNER_TEMP/previous" -name '*.apk' -print -quit)
  "$build_tools/apksigner" verify --print-certs "$old_apk" > "$RUNNER_TEMP/old-cert.txt"
  python3 scripts/verify_certificate.py "$RUNNER_TEMP/old-cert.txt"
  "$build_tools/aapt" dump badging "$old_apk" > "$RUNNER_TEMP/old-badging.txt"
  python3 - "$RUNNER_TEMP/old-badging.txt" <<'PY'
import os, re, sys
s=open(sys.argv[1]).read()
assert "package: name='fi.omasali.app'" in s
assert int(re.search(r"versionCode='(\d+)'", s)[1]) < int(os.environ['VERSION_CODE'])
PY
fi
mkdir -p dist
cp "$apk" "dist/OMA-SALI-v$VERSION_NAME.apk"
