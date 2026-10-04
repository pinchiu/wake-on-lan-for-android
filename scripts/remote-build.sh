#!/usr/bin/env bash
# ==============================================================================
# scripts/remote-build.sh
# 跨平台 (Linux / macOS / WSL) 遠端與本地建置腳本
#
# 功能：
#   優先透過 SSH 連線至 Homeserver 執行 Gradle 建置與測試；
#   若無法連線 Homeserver，則自動降級為本地端 (POSIX) 執行。
#
# 使用方式：
#   ./scripts/remote-build.sh -a test              # 僅執行單元測試
#   ./scripts/remote-build.sh -a build             # 僅打包 Release APK
#   ./scripts/remote-build.sh -a all               # 執行單元測試並打包 Release APK (預設)
#   ./scripts/remote-build.sh -a all -t both       # 同時驗證與打包 Home 和 Remote 雙端應用
# ==============================================================================

set -uo pipefail

ACTION="all"
TARGET="home"
HOST_NAME="${BUILD_HOST:-homeserver}"
REMOTE_REPO_PATH="~/wake-on-lan-for-android"

usage() {
    echo "用法: $0 [-a test|build|all] [-t home|remote|both]"
    echo "  -a : 執行動作 (預設: all)"
    echo "  -t : 目標模組 (home: 家用端, remote: 外出端, both: 雙端, 預設: home)"
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
    *) echo "錯誤: 無效的 Action '$ACTION'，請選擇 test, build, 或 all"; exit 1 ;;
esac

case "$TARGET" in
    home|remote|both) ;;
    *) echo "錯誤: 無效的 Target '$TARGET'，請選擇 home, remote, 或 both"; exit 1 ;;
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

# 取得專案根目錄
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo ">>> 檢查遠端建置主機 ($HOST_NAME) 連線狀態..."
TEST_CONN=$(ssh -q -o BatchMode=yes -o ConnectTimeout=4 "$HOST_NAME" "echo ok" 2>/dev/null || true)

if [ "$TEST_CONN" = "ok" ]; then
    echo ">>> 已連線至 $HOST_NAME，開始遠端建置..."

    # 確保遠端專案目錄存在
    ssh "$HOST_NAME" "bash -c 'if [ ! -d $REMOTE_REPO_PATH ]; then git clone https://github.com/pinchiu/wake-on-lan-for-android.git $REMOTE_REPO_PATH; fi'"

    for PROJ in "${PROJECTS[@]}"; do
        echo ">>> [$PROJ] 遠端執行: $TASKS_STRING"
        REMOTE_SCRIPT="export JAVA_HOME=\$HOME/jdk-21; export ANDROID_HOME=\$HOME/android-sdk; export ANDROID_SDK_ROOT=\$HOME/android-sdk; export PATH=\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH; cd $REMOTE_REPO_PATH && git pull && cd $PROJ && chmod +x ./gradlew && ./gradlew $TASKS_STRING --stacktrace"
        ssh "$HOST_NAME" "bash -lc \"$REMOTE_SCRIPT\""
    done

    # 若包含 build，下載 APK 至本地專案根目錄
    if [ "$ACTION" = "build" ] || [ "$ACTION" = "all" ]; then
        echo ">>> 下載編譯產物至本地..."
        if [ "$TARGET" = "home" ] || [ "$TARGET" = "both" ]; then
            scp "$HOST_NAME:$REMOTE_REPO_PATH/wakeonlanhomephone/app/build/outputs/apk/release/app-release.apk" "$ROOT_DIR/wakeonlan-home-phone.apk"
            echo ">>> 已下載家用端 APK 至: $ROOT_DIR/wakeonlan-home-phone.apk"
        fi
        if [ "$TARGET" = "remote" ] || [ "$TARGET" = "both" ]; then
            scp "$HOST_NAME:$REMOTE_REPO_PATH/wakeonwanremotephone/app/build/outputs/apk/release/app-release.apk" "$ROOT_DIR/wakeonwan-remote-phone.apk"
            echo ">>> 已下載外出端 APK 至: $ROOT_DIR/wakeonwan-remote-phone.apk"
        fi
    fi
else
    echo ">>> 無法連線至 $HOST_NAME (主機可能關機或非同內網)，自動降級為本地端執行！"

    for PROJ in "${PROJECTS[@]}"; do
        echo ">>> [$PROJ] 本地執行: $TASKS_STRING"
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
                echo ">>> 已同步家用端 APK 至: $APK_DST"
            fi
        fi
        if [ "$TARGET" = "remote" ] || [ "$TARGET" = "both" ]; then
            APK_SRC="$ROOT_DIR/wakeonwanremotephone/app/build/outputs/apk/release/app-release.apk"
            APK_DST="$ROOT_DIR/wakeonwan-remote-phone.apk"
            if [ -f "$APK_SRC" ]; then
                cp -f "$APK_SRC" "$APK_DST"
                echo ">>> 已同步外出端 APK 至: $APK_DST"
            fi
        fi
    fi
fi

echo ">>> 全部任務執行完成！"
