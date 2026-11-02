#!/usr/bin/env bash
# ==============================================================================
# scripts/remote-build.sh
# Cross-platform (Linux / macOS / WSL) remote and local build script
#
# Purpose:
#   Prioritizes SSH delegation to Homeserver for Gradle builds and tests;
#   gracefully falls back to local execution if Homeserver is unreachable.
#
# Usage:
#   ./scripts/remote-build.sh -a test              # Run unit tests only
#   ./scripts/remote-build.sh -a build             # Assemble Release APKs only
#   ./scripts/remote-build.sh -a all               # Run tests & assemble Release APKs (default)
#   ./scripts/remote-build.sh -a all -t both       # Build both Home and Remote apps
# ==============================================================================

set -uo pipefail

ACTION="all"
TARGET="home"
HOST_NAME="${BUILD_HOST:-homeserver}"
REMOTE_REPO_PATH="~/wake-on-lan-for-android"

usage() {
    echo "Usage: $0 [-a test|build|all] [-t home|remote|both]"
    echo "  -a : Action to execute (default: all)"
    echo "  -t : Target module (home: Home Phone, remote: Remote Phone, both: both apps, default: home)"
    exit 1
}

while getopts "a:t:h" opt; do
    case "$opt" in
        a) ACTION="$OPTARG" ;;
        t) TARGET="$OPTARG" ;;
        h|*) usage ;;
    esac
done

case "$ACTION" in
    test|build|all) ;;
    *) echo "Error: Invalid Action '$ACTION', choose test, build, or all"; exit 1 ;;
esac

case "$TARGET" in
    home|remote|both) ;;
    *) echo "Error: Invalid Target '$TARGET', choose home, remote, or both"; exit 1 ;;
esac

GRADLE_TASKS=()
if [ "$ACTION" = "test" ] || [ "$ACTION" = "all" ]; then
    GRADLE_TASKS+=("testDebugUnitTest")
fi
if [ "$ACTION" = "build" ] || [ "$ACTION" = "all" ]; then
    GRADLE_TASKS+=("assembleRelease")
fi
TASKS_STRING="${GRADLE_TASKS[*]}"

PROJECTS=()
if [ "$TARGET" = "home" ] || [ "$TARGET" = "both" ]; then
    PROJECTS+=("wakeonlanhomephone")
fi
if [ "$TARGET" = "remote" ] || [ "$TARGET" = "both" ]; then
    PROJECTS+=("wakeonwanremotephone")
fi

# Get repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo ">>> Checking remote build host ($HOST_NAME) connection..."
TEST_CONN=$(ssh -q -o BatchMode=yes -o ConnectTimeout=4 "$HOST_NAME" "echo ok" 2>/dev/null || true)

if [ "$TEST_CONN" = "ok" ]; then
    echo ">>> Connected to $HOST_NAME, starting remote build..."

    for PROJ in "${PROJECTS[@]}"; do
        echo ">>> [$PROJ] Remote execution: $TASKS_STRING"
        REMOTE_SCRIPT="export PATH=/home/scott/jdk-21/bin:/home/scott/android-sdk/cmdline-tools/latest/bin:/home/scott/android-sdk/platform-tools:/usr/local/bin:/usr/bin:/bin; export JAVA_HOME=/home/scott/jdk-21; export ANDROID_HOME=/home/scott/android-sdk; export ANDROID_SDK_ROOT=/home/scott/android-sdk; cd $REMOTE_REPO_PATH/$PROJ && chmod +x ./gradlew && ./gradlew $TASKS_STRING --stacktrace"
        ssh "$HOST_NAME" "bash -c '$REMOTE_SCRIPT'"
    done

    # If action includes build, download APKs to local project root
    if [ "$ACTION" = "build" ] || [ "$ACTION" = "all" ]; then
        echo ">>> Downloading build artifacts to local root..."
        if [ "$TARGET" = "home" ] || [ "$TARGET" = "both" ]; then
            scp "$HOST_NAME:$REMOTE_REPO_PATH/wakeonlanhomephone/app/build/outputs/apk/release/app-release.apk" "$ROOT_DIR/wakeonlan-home-phone.apk"
            echo ">>> Downloaded Home Phone APK to: $ROOT_DIR/wakeonlan-home-phone.apk"
        fi
        if [ "$TARGET" = "remote" ] || [ "$TARGET" = "both" ]; then
            scp "$HOST_NAME:$REMOTE_REPO_PATH/wakeonwanremotephone/app/build/outputs/apk/release/app-release.apk" "$ROOT_DIR/wakeonwan-remote-phone.apk"
            echo ">>> Downloaded Remote Phone APK to: $ROOT_DIR/wakeonwan-remote-phone.apk"
        fi
    fi
else
    echo ">>> Cannot connect to $HOST_NAME (host offline or not on LAN), falling back to local build..."

    for PROJ in "${PROJECTS[@]}"; do
        echo ">>> [$PROJ] Local execution: $TASKS_STRING"
        PROJ_DIR="$ROOT_DIR/$PROJ"
        (
            cd "$PROJ_DIR"
            chmod +x ./gradlew
            ./gradlew "${GRADLE_TASKS[@]}" --stacktrace
        )
    done

    if [ "$ACTION" = "build" ] || [ "$ACTION" = "all" ]; then
        if [ "$TARGET" = "home" ] || [ "$TARGET" = "both" ]; then
            APK_SRC="$ROOT_DIR/wakeonlanhomephone/app/build/outputs/apk/release/app-release.apk"
            APK_DST="$ROOT_DIR/wakeonlan-home-phone.apk"
            if [ -f "$APK_SRC" ]; then
                cp -f "$APK_SRC" "$APK_DST"
                echo ">>> Synced Home Phone APK to: $APK_DST"
            fi
        fi
        if [ "$TARGET" = "remote" ] || [ "$TARGET" = "both" ]; then
            APK_SRC="$ROOT_DIR/wakeonwanremotephone/app/build/outputs/apk/release/app-release.apk"
            APK_DST="$ROOT_DIR/wakeonwan-remote-phone.apk"
            if [ -f "$APK_SRC" ]; then
                cp -f "$APK_SRC" "$APK_DST"
                echo ">>> Synced Remote Phone APK to: $APK_DST"
            fi
        fi
    fi
fi

echo ">>> All tasks completed successfully!"
