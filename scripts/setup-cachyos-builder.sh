#!/usr/bin/env bash
set -e

echo "=== Automated CachyOS Android Build Environment Setup (No root / No sudo) ==="

# 1. Check and install portable OpenJDK 21
export JAVA_HOME="$HOME/jdk-21"
if [ ! -d "$JAVA_HOME/bin" ]; then
    echo "[1/4] Downloading and installing OpenJDK 21 to $JAVA_HOME..."
    mkdir -p "$JAVA_HOME"
    curl -L "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz" | tar -xz -C "$JAVA_HOME" --strip-components=1
fi
echo "[1/4] Java version: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"

# 2. Download and install Android Command-line Tools into user home directory
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then
    echo "[2/4] Downloading Google Android Command-line Tools..."
    cd "$ANDROID_HOME/cmdline-tools"
    curl -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    unzip -q cmdline-tools.zip
    rm -rf latest
    mv cmdline-tools latest
    rm -f cmdline-tools.zip
else
    echo "[2/4] Android Command-line Tools ready at $ANDROID_HOME/cmdline-tools/latest"
fi

# 3. Accept licenses and install platform 36, build-tools 36.0.0
echo "[3/4] Accepting Android SDK licenses and installing platform-tools & build-tools..."
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# 4. Configure environment variables in ~/.bashrc and fish shell
echo "[4/4] Configuring environment variables in ~/.bashrc and fish shell..."

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

echo "=== Setup complete! Android Gradle build environment is fully configured. ==="
