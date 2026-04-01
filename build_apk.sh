#!/bin/bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

echo "Building MRB Controller Pro APK..."
echo "ANDROID_HOME: $ANDROID_HOME"
java -version 2>&1

./gradlew assembleDebug --no-daemon
STATUS=$?

if [ $STATUS -eq 0 ]; then
    echo ""
    echo "=== BUILD SUCCESS ==="
    find . -name "*.apk" -not -path "*/intermediates/*" 2>/dev/null
else
    echo ""
    echo "=== BUILD FAILED ==="
fi

exit $STATUS
