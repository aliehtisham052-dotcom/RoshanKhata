#!/bin/sh
# Runs inside reactivecircus/android-emulator-runner (see signature-proof.yml).
# POSIX sh, one process. SignatureProofTest walks the signature screens and
# saves a picture of each step to Download/signature-proof.
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.innovation313.roshankhata.SignatureProofTest \
  --no-daemon --stacktrace > /tmp/instrumented_output.txt 2>&1
tests=$?
tail -60 /tmp/instrumented_output.txt
mkdir -p /tmp/signature-proof
adb pull /sdcard/Download/signature-proof /tmp/sigpull > /dev/null 2>&1 || true
find /tmp/sigpull -name '*.png' -exec cp {} /tmp/signature-proof/ \; 2>/dev/null
ls -l /tmp/signature-proof
exit $tests
