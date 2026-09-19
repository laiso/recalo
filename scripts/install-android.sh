#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
apk="$repo_root/apps/android/app/build/outputs/apk/dev/debug/app-dev-debug.apk"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb is not on PATH. Add the Android SDK platform-tools directory." >&2
    exit 1
fi

if [[ ! -f "$apk" ]]; then
    echo "Development APK not found. Build it first:" >&2
    echo "  cd apps/android && ./gradlew assembleDevDebug" >&2
    exit 1
fi

target="${1:-first}"
case "$target" in
    first|device|emulator|wireless) ;;
    *) echo "Unknown target: $target" >&2; exit 1 ;;
esac

# Preserve adb's order and ignore offline or unauthorized entries.
device_list="$(adb devices)"
serial="$(awk -v target="$target" '
    $2 == "device" && (target == "first" ||
        (target == "device" && $1 !~ /^emulator-/) ||
        (target == "emulator" && $1 ~ /^emulator-/) ||
        (target == "wireless" && $1 ~ /_adb-tls-connect/)) {print $1; exit}
' <<< "$device_list")"

if [[ -z "$serial" ]]; then
    echo "No connected device matches target: $target" >&2
    exit 1
fi

printf 'Installing on %s\n' "$serial"
adb -s "$serial" install -r "$apk"
