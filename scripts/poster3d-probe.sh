#!/bin/sh
# Runs inside android-emulator-runner (see poster3d-probe.yml): only the 3D
# poster test, with the device log streamed to a file the workflow prints
# even when the test hangs. Kept short and separate from device-check.sh.
adb logcat -c
adb logcat -v time Poster3D:V Filament:V AndroidRuntime:E ActivityManager:W TestRunner:V DEBUG:V libc:V *:E > /tmp/probe_logcat.txt 2>&1 &
LOGPID=$!
timeout 600 gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.Poster3dOnDeviceTest -Pandroid.testInstrumentationRunnerArguments.look="${LOOK:-full}" --no-daemon > /tmp/probe_gradle.txt 2>&1
code=$?
kill $LOGPID 2>/dev/null
echo "gradle exit: $code"
tail -60 /tmp/probe_gradle.txt
echo "----- device log -----"
tail -200 /tmp/probe_logcat.txt
adb pull /sdcard/Download/rtl-proof /tmp/rtl-proof > /dev/null 2>&1 || true
# Job logs are not always downloadable; annotations are. One annotation per
# file, newlines escaped as GitHub requires (%0A), so both reach the API.
enc() { sed -e 's/%/%25/g' -e 's/\r//g' | awk '{printf "%s%%0A", $0}'; }
echo "::notice title=gradle exit $code::$(grep -E 'FAILED|Exception|Error|error:|tests completed|BUILD|Tests on|expected|assert' /tmp/probe_gradle.txt | tail -40 | enc)"
echo "::notice title=device log::$(grep -vE 'ActivityManager' /tmp/probe_logcat.txt | tail -60 | enc)"
r=$(find app/build/outputs/androidTest-results -name '*.xml' 2>/dev/null | head -1)
[ -n "$r" ] && echo "::notice title=test failure::$(grep -A25 '<failure' "$r" | head -40 | enc)"
echo "::notice title=picture::$(grep -E 'Poster3D.*(grid|solid|look:)' /tmp/probe_logcat.txt | sed 's/^.*Poster3D([ 0-9]*): //' | enc)"
echo "::notice title=device crashes::$(grep -E 'FATAL|AndroidRuntime|Poster3D|SIG|abort|DEBUG|libc|Filament' /tmp/probe_logcat.txt | tail -40 | enc)"
exit $code
