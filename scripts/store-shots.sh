#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner (screenshots.yml): the
# store screenshots alone, on a seeded demo book, pulled to /tmp/store-shots.
# One sh process, POSIX sh (see device-check.sh for why).
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.StoreScreenshotsTest \
  --no-daemon --stacktrace > /tmp/store_shots_output.txt 2>&1
rc=$?
tail -60 /tmp/store_shots_output.txt
adb pull /sdcard/Download/store-shots /tmp/store-shots > /dev/null 2>&1 || true
ls -R /tmp/store-shots 2>/dev/null
exit $rc
