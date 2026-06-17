#!/usr/bin/env bash
# Clean reinstall of Caddie with ALL permissions granted via adb — no manual tapping.
# Usage:  bash reinstall.sh
# Always: uninstall -> build+install -> grant runtime/overlay -> enable accessibility -> adb tunnels.
set -uo pipefail

PKG="com.caddie"
SVC="$PKG/com.caddie.accessibility.CompanionAccessibilityService"
ADB="${ADB:-/c/Users/Andreas/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
cd "$(dirname "$0")"

echo "==> uninstall $PKG (ignore if absent)"
"$ADB" uninstall "$PKG" >/dev/null 2>&1 || true

echo "==> build + install"
./gradlew installDebug --console=plain 2>&1 | tail -4 || { echo "BUILD/INSTALL FAILED"; exit 1; }

echo "==> grant runtime + overlay permissions"
"$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
"$ADB" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true

echo "==> enable accessibility service (append, don't clobber)"
CUR=$("$ADB" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
case "$CUR" in
  *"$SVC"*) NEW="$CUR" ;;
  ""|null)  NEW="$SVC" ;;
  *)        NEW="$CUR:$SVC" ;;
esac
"$ADB" shell settings put secure enabled_accessibility_services "$NEW"
"$ADB" shell settings put secure accessibility_enabled 1

echo "==> adb tunnels (phone<->host agent API + bridge)"
"$ADB" reverse tcp:8787 tcp:8787 >/dev/null 2>&1 || true
"$ADB" forward tcp:8765 tcp:8765 >/dev/null 2>&1 || true

echo "==> launch app (starts WakeWord + Overlay services via accessibility launcher)"
"$ADB" shell am start -n "$PKG/com.caddie.MainActivity" >/dev/null 2>&1 || true

echo "==> done. Verify:"
echo "    RECORD_AUDIO : $("$ADB" shell dumpsys package "$PKG" | grep -m1 RECORD_AUDIO | tr -d '\r' | sed 's/^ *//')"
echo "    overlay(appop): $("$ADB" shell appops get "$PKG" SYSTEM_ALERT_WINDOW 2>/dev/null | tr -d '\r')"
echo "    a11y enabled : $("$ADB" shell settings get secure accessibility_enabled | tr -d '\r')"
echo "    a11y svc set : $("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r' | grep -o "$SVC" || echo MISSING)"
