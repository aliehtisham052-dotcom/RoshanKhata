#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner (see keyboard.yml).
# POSIX sh, one process (the runner splits its `script:` on newlines).
#
# The keyboard tests (KeyboardOnDeviceTest, KeyboardDialogsOnDeviceTest):
# every text field on every form, and the app's pop-up kinds, with the real
# on-screen keyboard up. The emulator has a hardware keyboard attached, and
# Android then hides the soft one; this setting shows it anyway, as on a
# phone.

adb shell settings put secure show_ime_with_hard_keyboard 1
adb logcat -c
adb logcat -v time KeyboardTest:I TestRunner:I AndroidRuntime:E *:S > /tmp/keyboard_log.txt 2>&1 &
lg=$!
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.KeyboardOnDeviceTest \
  --no-daemon --stacktrace > /tmp/instrumented_output.txt 2>&1
tests=$?
kill $lg 2>/dev/null
tail -80 /tmp/instrumented_output.txt
echo "---- measurements"
grep "KeyboardTest" /tmp/keyboard_log.txt | sed 's/^.*KeyboardTest[^:]*: //'
mkdir -p /tmp/keyboard-proof
adb pull /sdcard/Download/keyboard-proof /tmp/keyboard-proof > /dev/null 2>&1 || true
cp /tmp/keyboard_log.txt /tmp/keyboard-proof/ 2>/dev/null
exit $tests
