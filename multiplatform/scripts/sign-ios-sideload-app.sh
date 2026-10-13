#!/usr/bin/env bash

# Run on macOS after all bundle/executable renames and before creating the IPA.
# SideStore replaces these ad-hoc signatures with the user's provisioning profiles.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app="${1:?Usage: sign-ios-sideload-app.sh /path/to/App.app}"
widget="$app/PlugIns/CourseScheduleWidget.appex"
test -f "$app/Info.plist"
test -f "$widget/Info.plist"

# Sign nested code first; never use --deep to assign the host's entitlements
# to its frameworks or extensions.
while IFS= read -r -d '' framework; do
  codesign --force --sign - --timestamp=none "$framework"
done < <(find "$app" -depth -type d -name '*.framework' -print0)

codesign --force --sign - --timestamp=none \
  --entitlements "$project_root/iosApp/CourseScheduleWidget/CourseScheduleWidget.entitlements" "$widget"
codesign --force --sign - --timestamp=none \
  --entitlements "$project_root/iosApp/iosApp/iosApp.entitlements" "$app"
codesign --verify --deep --strict "$app"

# Verify the actual signed executables, not just the source .entitlements files.
python3 - "$app" "$widget" <<'PY'
import pathlib
import plistlib
import subprocess
import sys

expected = ["group.team.bjtuss.bjtuselfservice.kmp"]
infos = []
for bundle in map(pathlib.Path, sys.argv[1:]):
    with (bundle / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    executable = bundle / info["CFBundleExecutable"]
    result = subprocess.run(
        ["codesign", "--display", "--entitlements", ":-", str(executable)],
        check=True, stdout=subprocess.PIPE,
    )
    entitlements = plistlib.loads(result.stdout)
    groups = entitlements.get("com.apple.security.application-groups")
    if groups != expected:
        raise SystemExit(f"{bundle.name}: expected App Groups {expected}, got {groups}")
    print(f"{bundle.name}: signed executable App Groups verified: {groups}")
    infos.append(info)
for key in ("CFBundleShortVersionString", "CFBundleVersion"):
    if infos[0][key] != infos[1][key]:
        raise SystemExit(f"Host/widget {key} mismatch")
print("Sideload entitlements and host/widget versions verified.")
PY
