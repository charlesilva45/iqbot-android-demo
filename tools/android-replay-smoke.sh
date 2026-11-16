#!/usr/bin/env bash
set -euo pipefail

adb shell locksettings set-disabled true
adb install app/build/outputs/apk/debug/app-debug.apk
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.iqbot.demo.lab android.permission.POST_NOTIFICATIONS
adb shell am instrument -w com.iqbot.demo.lab.test/com.iqbot.demo.ReplayInstrumentation | tee android-test-results.txt
grep -Fq 'PASS: Android replay, screen off, and notification stop' android-test-results.txt
grep -Fq 'INSTRUMENTATION_CODE: -1' android-test-results.txt
