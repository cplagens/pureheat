#!/usr/bin/env bash
# PureHeat – drive the emulator for Play Store screenshots (only the emulator, never the phone).
#
#   scripts/emu.sh setup [en|de] [TZ]  1080×1920 (9:16, Play allows ≤ 2:1), time zone TZ where it is
#                                  about 7:00 now (morning heat-up in the demo): Berlin evening →
#                                  Australia/Brisbane (default), Berlin morning → Atlantic/Azores,
#                                  clean status bar, app language; then start the app → "Try demo mode"
#   scripts/emu.sh find TEXT       bounds of views whose text starts with TEXT (no screenshot needed)
#   scripts/emu.sh tap X Y [S]     tap, wait S seconds (default 2)
#   scripts/emu.sh swipe | swipedown | scroll DY | back
#   scripts/emu.sh shot NAME       screenshot → /tmp/ph/NAME.png
#   scripts/emu.sh clean NAME DEST paint over a system notification icon left of the clock
#   scripts/emu.sh reset           back to the emulator's normal size, time zone and status bar
#
# Coordinates are in the 1080×1920 space. Status bar background in light mode: #FEF7FF.
# Screenshots: docs/store/screenshots/{en,de}/NN-name.png (demo data only).
set -u
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
mkdir -p /tmp/ph

bar() {
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
  for c in "notifications -e visible false" "clock -e hhmm 0700" "battery -e level 100 -e plugged false" \
    "network -e wifi show -e level 4 -e mobile hide" \
    "status -e vpn hide -e location hide -e bluetooth hide -e alarm hide -e sync hide -e mute hide -e zen hide"; do
    adb shell am broadcast -a com.android.systemui.demo -e command $c > /dev/null
  done
}

case "${1:-}" in
  setup)
    adb shell wm size 1080x1920
    adb shell service call alarm 3 s16 "${3:-Australia/Brisbane}" > /dev/null
    adb shell cmd uimode night no > /dev/null
    adb shell cmd locale set-app-locales de.cplagens.pureheat --locales "${2:-en}" > /dev/null
    bar
    adb shell am force-stop de.cplagens.pureheat
    adb shell monkey -p de.cplagens.pureheat -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    sleep 4
    ;;
  bar) bar ;;
  tap) adb shell input tap "$2" "$3"; sleep "${4:-2}" ;;
  swipe) adb shell input swipe 540 1600 540 500 700; sleep 2 ;;
  swipedown) adb shell input swipe 540 500 540 1600 700; sleep 2 ;;
  scroll) adb shell input swipe 540 1500 540 $((1500 - $2)) 1500; sleep 2 ;;
  back) adb shell input keyevent 4; sleep 2 ;;
  shot) adb exec-out screencap -p > "/tmp/ph/$2.png" ;;
  clean) ffmpeg -loglevel error -y -i "/tmp/ph/$2.png" -vf "drawbox=x=90:y=0:w=120:h=62:color=0xFEF7FF:t=fill" "$3" ;;
  find)
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$2" | grep -o 'bounds="[^"]*"'
    ;;
  reset)
    adb shell wm size reset
    adb shell service call alarm 3 s16 Europe/Berlin > /dev/null
    adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
    ;;
  *) sed -n '2,16p' "$0" ;;
esac
