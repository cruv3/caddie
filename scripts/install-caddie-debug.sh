#!/usr/bin/env bash
# Build and install only the Caddie debug app.
# Requires: bash, Java/Gradle prerequisites, and adb on PATH (or ADB=/path/to/adb).
set -euo pipefail

repository="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
adb_command="${ADB:-adb}"
package="com.caddie.debug"
activity="${package}/com.caddie.app.MainActivity"
accessibility_service="${package}/com.caddie.app.CompanionAccessibilityService"
apk="${repository}/app/build/outputs/apk/normal/debug/app-normal-debug.apk"

command -v "${adb_command}" >/dev/null 2>&1 || {
  echo "adb not found. Put adb on PATH or set ADB=/path/to/adb." >&2
  exit 1
}

cd "${repository}"
./gradlew :app:assembleNormalDebug --console=plain
[[ -f "${apk}" ]] || { echo "APK not found: ${apk}" >&2; exit 1; }

"${adb_command}" get-state >/dev/null
"${adb_command}" install -r "${apk}"
"${adb_command}" shell pm grant "${package}" android.permission.POST_NOTIFICATIONS || true
"${adb_command}" shell appops set "${package}" SYSTEM_ALERT_WINDOW allow

enabled="$(${adb_command} shell settings get secure enabled_accessibility_services | tr -d '\r')"
case ":${enabled}:" in
  *":${accessibility_service}:"*) ;;
  "::"|":null:") enabled="${accessibility_service}" ;;
  *) enabled="${enabled}:${accessibility_service}" ;;
esac
"${adb_command}" shell settings put secure enabled_accessibility_services "${enabled}"
"${adb_command}" shell settings put secure accessibility_enabled 1
"${adb_command}" shell am start -W -n "${activity}"

echo "Caddie debug app installed for text tasks. Voice input is optional; study fixtures were not installed."
