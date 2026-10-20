#!/usr/bin/env bash
# Give the TV its own home screen back and remove Launchio.
#   launcher/uninstall.sh                     (TV=192.168.1.20:5555 to pick one)
#   STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity launcher/uninstall.sh   (older Android TV)
set -euo pipefail
APP=io.github.peshke.launchio
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.apps.tv.launcherx/.home.HomeActivity}"
ADB="${ADB:-adb}"
if [ -n "${TV:-}" ]; then "$ADB" connect "$TV" >/dev/null; set -- -s "$TV"; else set --; fi
for cmd in \
  "pm enable com.google.android.tungsten.setupwraith" \
  "pm enable ${STOCK_LAUNCHER%%/*}" \
  "cmd package set-home-activity --user 0 $STOCK_LAUNCHER" \
  's=$(settings get secure enabled_accessibility_services); settings put secure enabled_accessibility_services "$(echo "$s" | tr ":" "\n" | grep -v launchio | paste -sd: -)"' \
  "input keyevent KEYCODE_HOME"; do
  "$ADB" "$@" shell "$cmd"
done
"$ADB" "$@" uninstall "$APP"
echo "Launchio removed; the TV's own home screen is back."
