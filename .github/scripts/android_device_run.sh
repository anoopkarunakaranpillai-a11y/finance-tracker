#!/usr/bin/env bash
# Runs on the Android emulator in CI: instrumented tests, then three cold launches to measure start-up time.
set +e
cd "$(dirname "$0")/../../android"
./gradlew --no-daemon connectedDebugAndroidTest > ../device.log 2>&1
echo "connected exit: $?" >> ../device.log
{
  echo "model: $(adb shell getprop ro.product.model)"
  echo "android: $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
  echo "abi: $(adb shell getprop ro.product.cpu.abi)"
} > ../device-info.txt
adb install -r app/build/outputs/apk/debug/app-debug.apk >> ../device.log 2>&1
: > ../launch.txt
for i in 1 2 3; do
  adb shell am force-stop com.anoop.financetracker.debug
  sleep 3
  adb shell am start -W -n com.anoop.financetracker.debug/com.anoop.financetracker.MainActivity >> ../launch.txt 2>&1
  sleep 4
done
exit 0
