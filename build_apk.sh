#!/bin/bash
set -e

export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

echo "=== MRB Controller Pro Build Script ==="
echo "ANDROID_HOME: $ANDROID_HOME"
java -version 2>&1

# Setup Android SDK if not present
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "Installing Android command-line tools..."
    mkdir -p "$ANDROID_HOME"
    curl -s -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-raw
    mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
    mv /tmp/cmdline-tools-raw/cmdline-tools/* "$ANDROID_HOME/cmdline-tools/latest/"
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-raw
    echo "Android command-line tools installed."
fi

if [ ! -d "$ANDROID_HOME/platforms/android-34" ]; then
    echo "Installing Android SDK components..."
    yes | sdkmanager --licenses > /dev/null 2>&1 || true
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
    echo "Android SDK components installed."
fi

# Update local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo ""
echo "Building Debug APK..."
chmod +x gradlew
./gradlew assembleDebug --no-daemon
STATUS=$?

if [ $STATUS -eq 0 ]; then
    echo ""
    echo "=== BUILD SUCCESS ==="
    find . -name "*.apk" -not -path "*/intermediates/*" 2>/dev/null
    echo ""
    echo "APK built successfully. Install on your Android device to use."
else
    echo ""
    echo "=== BUILD FAILED ==="
fi

exit $STATUS
