#!/usr/bin/env bash
# Build Launchio with the bare SDK tools (no Gradle) and install it on the TV as the home screen.
#   launcher/build-and-install.sh                  build + install (TV=192.168.1.20:5555 to pick one)
#   launcher/build-and-install.sh --build          build only
# Needs a JDK (17+), the Android SDK (platform 34, build-tools 35) and adb. macOS, Linux, or Git Bash
# on Windows. Override paths with JAVA_HOME, ANDROID_HOME, BUILD_TOOLS, ADB.
set -euo pipefail
cd "$(dirname "$0")"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
# 35, not 34: build-tools 34's d8 crashes on classes compiled by JDK 21.
BT="$SDK/build-tools/${BUILD_TOOLS:-35.0.0}"
JAR="$SDK/platforms/android-34/android.jar"
exe() { [ -x "$BT/$1.exe" ] && echo "$BT/$1.exe" || echo "$BT/$1"; }
APP=io.github.peshke.launchio
# Google TV's stock home; older Android TV sets use com.google.android.tvlauncher.
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.apps.tv.launcherx}"
B=build

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$(exe aapt2)" compile --dir res -o "$B/res.zip"
"$(exe aapt2)" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  -A assets --min-sdk-version 30 --target-sdk-version 30 "$B/res.zip"
javac -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')
jar cf "$B/classes.jar" -C "$B/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 30 --lib "$JAR" --output "$B/dex" "$B/classes.jar"
cp "$B/base.apk" "$B/unsigned.apk"
jar uMf "$B/unsigned.apk" -C "$B/dex" classes.dex  # the JDK's jar, so no zip needed (Git Bash has none)
"$(exe zipalign)" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias launchio -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=launchio" >/dev/null 2>&1
java -jar "$BT/lib/apksigner.jar" sign --ks debug.keystore --ks-pass pass:android --out "$B/launchio.apk" "$B/aligned.apk"
echo "built $B/launchio.apk ($(wc -c < "$B/launchio.apk") bytes)"
[ "${1:-}" = "--build" ] && exit 0

ADB="${ADB:-adb}"
if [ -n "${TV:-}" ]; then "$ADB" connect "$TV" >/dev/null; set -- -s "$TV"; else set --; fi
"$ADB" "$@" install -r "$B/launchio.apk"
# One command per adb call: chained pm commands can stop after the first.
for cmd in \
  "pm grant $APP android.permission.ACCESS_FINE_LOCATION" \
  "pm grant $APP android.permission.READ_TV_LISTINGS" \
  "pm grant $APP android.permission.WRITE_SECURE_SETTINGS" \
  "cmd notification allow_listener $APP/$APP.NowPlayingListener" \
  's=$(settings get secure enabled_accessibility_services); case "$s" in *launchio/.PowerService*) ;; null|"") settings put secure enabled_accessibility_services '"$APP"'/.PowerService ;; *) settings put secure enabled_accessibility_services "$s:'"$APP"'/.PowerService" ;; esac' \
  "settings put secure accessibility_enabled 1" \
  "cmd package set-home-activity --user 0 $APP/.MainActivity" \
  "pm disable-user --user 0 $STOCK_LAUNCHER" \
  "pm disable-user --user 0 com.google.android.tungsten.setupwraith" \
  "cmd package set-home-activity --user 0 $APP/.MainActivity" \
  "am force-stop com.android.tv.settings" \
  "input keyevent KEYCODE_HOME"; do
  "$ADB" "$@" shell "$cmd"
done
echo "Installed. Press Home on the remote."
