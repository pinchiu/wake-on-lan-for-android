#!/usr/bin/env bash
set -e
export PATH=/home/scott/jdk-21/bin:/home/scott/android-sdk/cmdline-tools/latest/bin:/home/scott/android-sdk/platform-tools:/usr/local/bin:/usr/bin:/bin
export JAVA_HOME=/home/scott/jdk-21
export ANDROID_HOME=/home/scott/android-sdk
export ANDROID_SDK_ROOT=/home/scott/android-sdk

echo "=== [1/2] Building Home Phone (test & release) ==="
cd /home/scott/wake-on-lan-for-android/wakeonlanhomephone
chmod +x ./gradlew
./gradlew testDebugUnitTest assembleRelease

echo "=== [2/2] Building Remote Phone (release) ==="
cd /home/scott/wake-on-lan-for-android/wakeonwanremotephone
chmod +x ./gradlew
./gradlew assembleRelease

echo "=== All Builds Successfully Finished ==="
