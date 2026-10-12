#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ios_app="${IOS_APP_PATH:-$project_root/iosApp/build-generic/Build/Products/Debug-iphonesimulator/交大自由行 KMP.app}"
mac_app="${MAC_APP_PATH:-$project_root/desktopApp/build/compose/binaries/main/app/BJTUselfServiceKMP.app}"

fail() {
  echo "Apple release metadata verification failed: $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing file: $1"
}

require_absent() {
  [[ ! -e "$1" ]] || fail "unexpected removed artifact: $1"
}

require_value() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [[ "$actual" == "$expected" ]] || fail "$label expected '$expected', got '$actual'"
}

ios_info="$ios_app/Info.plist"
ios_privacy="$ios_app/PrivacyInfo.xcprivacy"
mac_info="$mac_app/Contents/Info.plist"
mac_privacy="$mac_app/Contents/Resources/PrivacyInfo.xcprivacy"
mac_icon="$mac_app/Contents/Resources/BJTUselfServiceKMP.icns"
mac_icon_source="$project_root/desktopApp/src/main/resources/BJTUselfServiceKMP-v2.icns"
ios_icon_source="$project_root/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-v2.png"

require_file "$ios_info"
require_file "$ios_privacy"
require_file "$ios_app/Assets.car"
require_file "$ios_app/AppIcon60x60@2x.png"
require_file "$ios_app/AppIcon76x76@2x~ipad.png"
require_file "$ios_icon_source"
ios_widget="$ios_app/PlugIns/CourseScheduleWidget.appex"
require_file "$ios_widget/Info.plist"

plutil -lint "$ios_info" "$ios_privacy" >/dev/null
require_value "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$ios_info")" \
  "team.bjtuss.bjtuselfservice.kmp.ios" "iOS bundle ID"
require_value "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$ios_widget/Info.plist")" \
  "team.bjtuss.bjtuselfservice.kmp.ios.widget" "iOS widget bundle ID"
# App Store 要求扩展与宿主版本一致；两处 Info.plist 都是手写版本号。
for key in CFBundleShortVersionString CFBundleVersion; do
  require_value "$(/usr/libexec/PlistBuddy -c "Print :$key" "$ios_widget/Info.plist")" \
    "$(/usr/libexec/PlistBuddy -c "Print :$key" "$ios_info")" "iOS widget $key"
done
require_value "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconName' "$ios_info")" \
  "AppIcon" "iOS app icon name"
require_value "$(/usr/libexec/PlistBuddy -c 'Print :NSAppTransportSecurity:NSExceptionDomains:yaya.csoci.com:NSExceptionAllowsInsecureHTTPLoads' "$ios_info")" \
  "true" "iOS classroom ATS exception"
if /usr/libexec/PlistBuddy -c 'Print :NSAppTransportSecurity:NSAllowsArbitraryLoads' "$ios_info" >/dev/null 2>&1; then
  fail "iOS must not enable NSAllowsArbitraryLoads"
fi
require_value "$(sips -g hasAlpha "$ios_icon_source" | awk '/hasAlpha:/{print $2}')" \
  "no" "iOS 1024 icon alpha"
file "$ios_app/交大自由行 KMP" | grep -q 'Mach-O 64-bit executable arm64' \
  || fail "iOS Simulator executable is not arm64 Mach-O"
require_file "$mac_info"
require_file "$mac_privacy"
require_file "$mac_icon"
require_file "$mac_icon_source"

plutil -lint "$mac_info" "$mac_privacy" >/dev/null
require_value "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$mac_info")" \
  "team.bjtuss.bjtuselfservice.kmp.macos" "macOS bundle ID"
require_value "$(/usr/libexec/PlistBuddy -c 'Print :LSApplicationCategoryType' "$mac_info")" \
  "public.app-category.education" "macOS app category"
require_value "$(/usr/libexec/PlistBuddy -c 'Print :LSMinimumSystemVersion' "$mac_info")" \
  "12.0" "macOS minimum system version"
cmp -s "$mac_icon" "$mac_icon_source" || fail "packaged macOS icon differs from v2 source"
file "$mac_app/Contents/MacOS/BJTUselfServiceKMP" | grep -q 'Mach-O 64-bit executable arm64' \
  || fail "macOS executable is not arm64 Mach-O"
codesign --verify --deep --strict "$mac_app"

echo "Apple release metadata verification passed."
