#!/usr/bin/env bash
set -e

echo "=== CachyOS Android 建置環境自動化設定 (免 root / 免 sudo) ==="

# 1. 檢查並安裝可攜式 OpenJDK 21
export JAVA_HOME="$HOME/jdk-21"
if [ ! -d "$JAVA_HOME/bin" ]; then
    echo "[1/4] 下載並安裝 OpenJDK 21 至 $JAVA_HOME..."
    mkdir -p "$JAVA_HOME"
    curl -L "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz" | tar -xz -C "$JAVA_HOME" --strip-components=1
fi
echo "[1/4] Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"

# 2. 下載並安裝 Android Command-line Tools 到使用者家目錄
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then
    echo "[2/4] 下載 Google Android Command-line Tools..."
    cd "$ANDROID_HOME/cmdline-tools"
    curl -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    unzip -q cmdline-tools.zip
    rm -rf latest
    mv cmdline-tools latest
    rm -f cmdline-tools.zip
else
    echo "[2/4] Android Command-line Tools 已就緒於 $ANDROID_HOME/cmdline-tools/latest"
fi

# 3. 接受授權與安裝 platform 36, build-tools 36.0.0
echo "[3/4] 接受 Android SDK 授權並安裝 platform-tools 與 build-tools..."
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# 4. 配置環境變數至 ~/.bashrc 與 fish shell
echo "[4/4] 配置環境變數至 ~/.bashrc 與 fish shell 全域變數..."

if ! grep -q "JAVA_HOME" "$HOME/.bashrc" 2>/dev/null; then
    cat << 'EOF' >> "$HOME/.bashrc"

# OpenJDK & Android SDK
export JAVA_HOME="$HOME/jdk-21"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
fi

if command -v fish &> /dev/null; then
    fish -c 'set -Ux JAVA_HOME $HOME/jdk-21'
    fish -c 'set -Ux ANDROID_HOME $HOME/android-sdk'
    fish -c 'set -Ux ANDROID_SDK_ROOT $HOME/android-sdk'
    fish -c 'fish_add_path $HOME/jdk-21/bin'
    fish -c 'fish_add_path $HOME/android-sdk/cmdline-tools/latest/bin'
    fish -c 'fish_add_path $HOME/android-sdk/platform-tools'
fi

echo "=== 設定完成！CachyOS 已具備完整 Android Gradle 建置環境 ==="
