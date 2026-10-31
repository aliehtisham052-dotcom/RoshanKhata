#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner (screen-proof.yml): pictures of the phone's screens at the
# owner's width — 360 dp (1080 px at 480 dpi) — in four looks (ScreenProofPhoneTest), pulled to /tmp/screen-proof.
# One sh process, POSIX sh (see device-check.sh for why).
adb shell wm size 1080x2160
adb shell wm density 480
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.ScreenProofPhoneTest \
  --no-daemon --stacktrace > /tmp/screen_proof_output.txt 2>&1
rc=$?
tail -60 /tmp/screen_proof_output.txt
adb pull /sdcard/Download/screen-proof /tmp/screen-proof > /dev/null 2>&1 || true
ls -R /tmp/screen-proof 2>/dev/null
adb shell wm size reset
adb shell wm density reset
exit $rc
