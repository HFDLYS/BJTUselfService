#!/bin/bash
set -euo pipefail

APP_NAME="BJTUselfServiceMac"
APP_DISPLAY_NAME="交大自由行"
BUNDLE_ID="team.bjtuss.bjtuselfservice.mac"
VERSION="1.0.0"
BUILD_NUMBER="1"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGING_DIR="$ROOT_DIR/Packaging"
BUILD_DIR="$ROOT_DIR/.build"
STAGING_DIR="$BUILD_DIR/dmg-staging"
APP_BUNDLE="$STAGING_DIR/$APP_NAME.app"

echo "==> [1/8] Release build (arm64)"
cd "$ROOT_DIR"
swift build -c release
BUILT_BIN="$BUILD_DIR/arm64-apple-macosx/release/$APP_NAME"
RESOURCE_BUNDLE="$BUILD_DIR/arm64-apple-macosx/release/${APP_NAME}_${APP_NAME}.bundle"
MLPACKAGE_SRC="$ROOT_DIR/Sources/BJTUselfServiceMac/Resources/CaptchaCRNN.mlpackage"

if [[ ! -f "$BUILT_BIN" ]]; then
    echo "ERROR: built binary not found at $BUILT_BIN"
    exit 1
fi

echo "==> [2/8] Clean staging"
rm -rf "$STAGING_DIR"
mkdir -p "$APP_BUNDLE/Contents/MacOS"
mkdir -p "$APP_BUNDLE/Contents/Resources"

echo "==> [3/8] Copy executable"
cp "$BUILT_BIN" "$APP_BUNDLE/Contents/MacOS/$APP_NAME"

echo "==> [4/8] Pre-compile CoreML model -> .mlmodelc"
MLMODELC_TMP="$BUILD_DIR/CaptchaCRNN.mlmodelc"
rm -rf "$MLMODELC_TMP"
xcrun coremlcompiler compile "$MLPACKAGE_SRC" "$BUILD_DIR" >/dev/null 2>&1 || {
    echo "  coremlcompiler failed, falling back to runtime compilation"
}
if [[ -d "$MLMODELC_TMP" ]]; then
    cp -R "$MLMODELC_TMP" "$APP_BUNDLE/Contents/Resources/CaptchaCRNN.mlmodelc"
    echo "  pre-compiled model included"
fi

echo "==> [5/8] Copy Info.plist and optional icon"
cp "$PACKAGING_DIR/Info.plist" "$APP_BUNDLE/Contents/Info.plist"

if [[ -f "$PACKAGING_DIR/AppIcon.icns" ]]; then
    cp "$PACKAGING_DIR/AppIcon.icns" "$APP_BUNDLE/Contents/Resources/AppIcon.icns"
    /usr/libexec/PlistBuddy -c "Add :CFBundleIconFile string AppIcon" "$APP_BUNDLE/Contents/Info.plist" 2>/dev/null || true
    echo "  icon included"
else
    echo "  no AppIcon.icns found (optional) - app will use default icon"
fi

if [[ -d "$RESOURCE_BUNDLE" ]]; then
    cp -R "$RESOURCE_BUNDLE" "$APP_BUNDLE/Contents/Resources/" 2>/dev/null || true
fi

echo "==> [6/8] Fix executable bit"
chmod +x "$APP_BUNDLE/Contents/MacOS/$APP_NAME"

echo "==> [7/8] Code sign"
DEV_IDENTITY=$(security find-identity -v -p codesigning 2>/dev/null | grep "Developer ID Application" | head -1 | sed -E 's/.*"(.*)".*/\1/' || true)

if [[ -n "${DEV_IDENTITY:-}" ]]; then
    echo "  signing with Developer ID: $DEV_IDENTITY"
    codesign --deep --force --options runtime --sign "$DEV_IDENTITY" "$APP_BUNDLE"
    echo "  NOTE: notarization skipped. Run notarytool separately with your Apple ID."
else
    echo "  no Developer ID certificate found - ad-hoc signing"
    codesign --deep --force --sign - "$APP_BUNDLE"
    echo "  WARNING: ad-hoc signed. Users must right-click -> Open, or run:"
    echo "    xattr -cr '/Applications/$APP_NAME.app'"
fi

echo "==> [8/8] Create DMG"
DMG_OUTPUT="$ROOT_DIR/build/${APP_NAME}-${VERSION}.dmg"
mkdir -p "$(dirname "$DMG_OUTPUT")"
rm -f "$DMG_OUTPUT"

ln -s /Applications "$STAGING_DIR/Applications"

hdiutil create \
    -volname "$APP_DISPLAY_NAME" \
    -fs HFS+ \
    -srcfolder "$STAGING_DIR" \
    -format UDZO \
    -imagekey zlib-level=9 \
    "$DMG_OUTPUT" >/dev/null 2>&1

DMG_SIZE=$(du -h "$DMG_OUTPUT" | cut -f1)
echo ""
echo "========================================"
echo "DONE"
echo "  DMG:   $DMG_OUTPUT"
echo "  Size:  $DMG_SIZE"
echo "========================================"
echo ""
echo "To distribute:"
echo "  1. If Developer ID signed: notarize with"
echo "     xcrun notarytool submit \"$DMG_OUTPUT\" --apple-id YOU@EMAIL.com --team-id TEAMID --wait"
echo "     xcrun stapler staple \"$DMG_OUTPUT\""
echo "  2. If ad-hoc signed: users run 'xattr -cr' on the app after drag-install"
