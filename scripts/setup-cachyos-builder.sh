#!/usr/bin/env bash
set -e

echo "=== CachyOS Android 建置環境自動化設定 ==="

# 1. 檢查並安裝 OpenJDK 21 與相關工具
if ! command -v javac &> /dev/null; then
    echo "[1/4] 安裝 OpenJDK 21 與相關相依套件..."
    sudo pacman -S --needed --noconfirm jdk21-openjdk git curl unzip
    if command -v archlinux-java &> /dev/null; then
        sudo archlinux-java set java-21-openjdk
    fi
else
    echo "[1/4] Java 已安裝: $(java -version 2>&1 | head -n 1)"
fi

# 2. 下載並安裝 Android Command-line Tools 到使用者家目錄
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "[2/4] 下載 Google Android Command-line Tools..."
    cd "$ANDROID_HOME/cmdline-tools"
    curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q cmdline-tools.zip
    mv cmdline-tools latest
    rm cmdline-tools.zip
else
    echo "[2/4] Android Command-line Tools 已存在於 $ANDROID_HOME/cmdline-tools/latest"
fi

# 3. 接受授權與安裝 platform 36, build-tools 36.0.0
echo "[3/4] 接受 Android SDK 授權並安裝 platform 與 build-tools..."
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# 4. 配置環境變數
echo "[4/4] 配置環境變數至 ~/.bashrc 與 fish shell..."

# Bash 設定
if ! grep -q "ANDROID_HOME" "$HOME/.bashrc" 2>/dev/null; then
    cat << 'EOF' >> "$HOME/.bashrc"

# Android SDK
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
EOF
fi

# Fish shell 設定
if command -v fish &> /dev/null; then
    fish -c 'set -Ux ANDROID_HOME $HOME/android-sdk'
    fish -c 'set -Ux ANDROID_SDK_ROOT $HOME/android-sdk'
    fish -c 'fish_add_path $HOME/android-sdk/cmdline-tools/latest/bin'
    fish -c 'fish_add_path $HOME/android-sdk/platform-tools'
fi

# 5. Clone 儲存庫（若尚未存在）
if [ ! -d "$HOME/wake-on-lan-for-android" ]; then
    echo "正在 Clone 專案儲存庫..."
    cd "$HOME"
    git clone https://github.com/pinchiu/wake-on-lan-for-android.git
fi

echo "=== 設定完成！CachyOS 已具備完整 Android Gradle 建置環境 ==="
