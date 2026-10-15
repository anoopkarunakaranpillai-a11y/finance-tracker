#!/usr/bin/env bash
# Runs on the Android emulator in CI: instrumented tests, screenshot tours, then three cold launches to measure start-up time.
set +e
cd "$(dirname "$0")/../../android"
adb logcat -c
./gradlew --no-daemon connectedDebugAndroidTest > ../device.log 2>&1
adb logcat -d -v brief "*:I" | grep -i "FTStudyEnv\|FTStudy\|chromium\|cr_\|AndroidRuntime\|TestRunner\|financetracker\|ActivityTaskManager\|WebView" | grep -v "^W/\(ziparchive\|adbd\|InputManager\|WifiService\|AppInfoHelper\|SQLiteLog\)" | tail -1500 > ../logcat.txt
echo "connected exit: $?" >> ../device.log
{
  echo "model: $(adb shell getprop ro.product.model)"
  echo "android: $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
  echo "abi: $(adb shell getprop ro.product.cpu.abi)"
} > ../device-info.txt
adb install -r app/build/outputs/apk/debug/app-debug.apk >> ../device.log 2>&1
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >> ../device.log 2>&1
# Screenshot tour with demo data (kept installed so the images can be pulled)
adb shell am instrument -w -e class com.anoop.financetracker.ScreenshotTour,com.anoop.financetracker.StudyScreenshots com.anoop.financetracker.debug.test/androidx.test.runner.AndroidJUnitRunner >> ../device.log 2>&1
mkdir -p ../shots
for f in $(adb shell run-as com.anoop.financetracker.debug ls files/shots 2>/dev/null | tr -d '\r'); do
  adb exec-out run-as com.anoop.financetracker.debug cat "files/shots/$f" > "../shots/$f"
done
ls -la ../shots >> ../device.log
: > ../launch.txt
for i in 1 2 3; do
  adb shell am force-stop com.anoop.financetracker.debug
  sleep 3
  adb shell am start -W -n com.anoop.financetracker.debug/com.anoop.financetracker.MainActivity >> ../launch.txt 2>&1
  sleep 4
done
exit 0
