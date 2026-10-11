#!/bin/sh
# Runs inside android-emulator-runner (see poster3d-probe.yml): only the 3D
# poster test, with the device log streamed to a file the workflow prints
# even when the test hangs. Kept short and separate from device-check.sh.
adb logcat -c
adb logcat -v time Poster3D:V Filament:V AndroidRuntime:E ActivityManager:W TestRunner:V *:S > /tmp/probe_logcat.txt 2>&1 &
LOGPID=$!
timeout 600 gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.Poster3dOnDeviceTest --no-daemon > /tmp/probe_gradle.txt 2>&1
code=$?
kill $LOGPID 2>/dev/null
echo "gradle exit: $code"
tail -60 /tmp/probe_gradle.txt
echo "----- device log -----"
tail -200 /tmp/probe_logcat.txt
adb pull /sdcard/Download/rtl-proof /tmp/rtl-proof > /dev/null 2>&1 || true
exit $code
