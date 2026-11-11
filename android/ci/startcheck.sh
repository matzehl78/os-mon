#!/usr/bin/env bash
# Startet die Debug-APK im Emulator in zwei Zuständen (Ei nach dem Onboarding, geschlüpft) und gibt Abstürze aus.
set -u
PKG=com.example.osmon
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
OUT=startcheck-out; mkdir -p $OUT
adb install -r -g "$APK" || exit 1
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 6; adb shell am force-stop $PKG

run_case () {   # $1 Name, $2 growth.state (oder leer)
  adb shell pm clear $PKG >/dev/null
  adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4; adb shell am force-stop $PKG    # Prefs-Ordner anlegen
  { echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
    echo "<boolean name=\"onboarding.done\" value=\"true\" /><boolean name=\"intro.seen\" value=\"true\" />"
    [ -n "$2" ] && echo "<string name=\"growth.state\">$2</string>"
    echo "</map>"; } > $OUT/home.xml
  adb push $OUT/home.xml /data/local/tmp/home.xml >/dev/null
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/home.xml shared_prefs/home.xml'"
  adb logcat -c
  adb shell am start -n $PKG/.MainActivity >/dev/null
  sleep 14
  adb exec-out screencap -p > $OUT/$1.png
  adb logcat -d > $OUT/$1.log
  grep -E "chromium|WebView|OSPet|console" $OUT/$1.log | tail -25
  echo "SHOT-$1-BEGIN"; base64 -w0 $OUT/$1.png; echo; echo "SHOT-$1-END"
  echo "=== $1: Prozess läuft?"; adb shell pidof $PKG || echo "NEIN (abgestürzt oder beendet)"
  echo "=== $1: Abstürze"; grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $PKG" -A25 $OUT/$1.log | head -80
}
run_case egg   '{&quot;hatched&quot;:false,&quot;good&quot;:0,&quot;last&quot;:'$(( $(date +%s) / 86400 ))'}'
run_case baby  ''
exit 0
