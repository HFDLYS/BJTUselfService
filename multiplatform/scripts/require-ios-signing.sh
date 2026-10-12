#!/bin/sh
set -eu

# Only Simulator builds are guarded: an unsigned Simulator app loses its Keychain
# entries, so saved logins disappear on every reinstall. Device builds stay open —
# Xcode Run signs them, and the unsigned IPA (local or CI) is re-signed on install.
if [ "${PLATFORM_NAME:-}" != iphonesimulator ]; then
    exit 0
fi

# Reject command-line overrides too: xcconfig defaults alone are not sufficient.
if [ "${CODE_SIGNING_ALLOWED:-}" != YES ] || [ "${CODE_SIGNING_REQUIRED:-}" != YES ]; then
    echo "error: iOS Simulator builds must enable code signing. Unsigned Simulator apps cannot reliably save login credentials in Keychain." >&2
    exit 1
fi
if [ "${DEVELOPMENT_TEAM:-}" != 34S53DC6T6 ]; then
    echo "error: iOS Simulator builds must use the configured development team 34S53DC6T6." >&2
    exit 1
fi
