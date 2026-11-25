#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner (screen-proof.yml): pictures of the phone's screens at the
# owner's width — 360 dp (1080 px at 480 dpi) — in four looks (ScreenProofPhoneTest), then the nine languages at 85, 100
# and 150% text, day and night, six layout-heavy screens each (ScreenProofLangTest, 27 looks); both pulled to /tmp/screen-proof.
# One sh process, POSIX sh (see device-check.sh for why).
adb shell wm size 1080x2160
adb shell wm density 480
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.ScreenProofPhoneTest \
  --no-daemon --stacktrace > /tmp/screen_proof_output.txt 2>&1
rc=$?
tail -60 /tmp/screen_proof_output.txt
# The second proof (R9-B4): its own run and output file, so the first one's verdict is never hidden by it.
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.ScreenProofLangTest \
  --no-daemon --stacktrace > /tmp/screen_proof_lang_output.txt 2>&1
rc2=$?
tail -60 /tmp/screen_proof_lang_output.txt
# One pull at the end: both runs' pictures are in Download/screen-proof (a second pull would nest the folder).
adb pull /sdcard/Download/screen-proof /tmp/screen-proof > /dev/null 2>&1 || true
ls -R /tmp/screen-proof 2>/dev/null
adb shell wm size reset
adb shell wm density reset
[ "$rc" -ne 0 ] && exit "$rc"
exit "$rc2"
