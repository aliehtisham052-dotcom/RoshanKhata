#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner (see instrumented.yml).
#
# Kept in a file, not in the workflow's `script:`, because that action splits
# `script:` on newlines and runs each line as its own `sh -c`. Here the whole
# file is one `sh` process, so it can be written normally.
#
# POSIX sh, not bash: no ${PIPESTATUS}, no [[ ]], no arrays.
#
# Two parts, both on the same booted emulator:
#   1. The written tests in app/src/androidTest — every screen opens, rotates,
#      goes to the background and back, with real rows in the ledger.
#   2. The monkey: thousands of random taps, swipes and typing across the whole
#      app, the way a hurried owner (or a child holding the phone) uses it.
#      Written tests only go where someone thought to send them; the monkey
#      goes everywhere. It fails on a CRASH only — a slow emulator produces
#      false "not responding" reports, so those are counted, not failed on.

PKG=com.innovation313.roshankhata
EVENTS=4000
SEED=313

# ---- 1. Written tests ------------------------------------------------------
gradle connectedDebugAndroidTest --no-daemon --stacktrace > /tmp/instrumented_output.txt 2>&1
tests=$?
tail -150 /tmp/instrumented_output.txt

# ---- 2. Monkey -------------------------------------------------------------
# connectedDebugAndroidTest uninstalls the app when it finishes, so install
# the same debug APK again: fresh, as a new owner would get it. -g grants every
# runtime permission up front — a permission dialog belongs to another app,
# and the monkey is not allowed to leave ours, so it would stall on it.
apk=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
monkey=0
if [ -z "$apk" ]; then
  echo "No debug APK found for the monkey run" > /tmp/monkey_output.txt
  monkey=1
else
  adb install -r -g "$apk" > /tmp/monkey_install.txt 2>&1
  adb logcat -b all -c
  # --pct-syskeys 0: no HOME / call / volume keys, which only leave the app.
  # --ignore-timeouts: see above; ANRs are counted below instead.
  # Fixed seed: a crash found here can be replayed with the same taps.
  adb shell monkey -p $PKG -s $SEED --throttle 75 --pct-syskeys 0 --ignore-timeouts --ignore-security-exceptions -v $EVENTS > /tmp/monkey_output.txt 2>&1
  monkey=$?
  adb logcat -d -b crash > /tmp/crash_log.txt 2>&1
  if grep -q "// CRASH" /tmp/monkey_output.txt; then monkey=1; fi
  if grep -q "$PKG" /tmp/crash_log.txt; then monkey=1; fi
fi

anrs=$(grep -c "NOT RESPONDING" /tmp/monkey_output.txt 2>/dev/null)
echo "---- monkey: exit=$monkey  events=$EVENTS  seed=$SEED  anr_reports=${anrs:-0}"
tail -40 /tmp/monkey_output.txt

if [ "$tests" -ne 0 ]; then exit "$tests"; fi
if [ "$monkey" -ne 0 ]; then exit 1; fi
exit 0
