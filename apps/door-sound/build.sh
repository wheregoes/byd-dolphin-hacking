#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

ANDROID_JAR="/tmp/android-10/android.jar"
AAPT2="/usr/bin/aapt2"
BUILD_DIR="build"
OUT_APK="door-sound.apk"
PACKAGE="com.wheregoes.doorsound"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR"
    echo "Download it: wget https://dl.google.com/android/repository/platform-29_r05.zip"
    exit 1
fi

echo "=== Cleaning ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{compiled-res,gen,classes,stubs-classes}

echo "=== Compiling resources ==="
$AAPT2 compile --dir src/main/res -o "$BUILD_DIR/compiled-res/"

echo "=== Linking resources ==="
$AAPT2 link \
    --manifest src/main/AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    -o "$BUILD_DIR/app-unsigned.apk" \
    --java "$BUILD_DIR/gen" \
    --min-sdk-version 28 \
    --target-sdk-version 29 \
    "$BUILD_DIR"/compiled-res/*.flat

echo "=== Compiling BYD API stubs ==="
find stubs -name "*.java" | xargs javac \
    -source 11 -target 11 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/stubs-classes"

echo "=== Compiling app sources ==="
find src/main/java -name "*.java" | xargs javac \
    -source 11 -target 11 \
    -cp "$ANDROID_JAR:$BUILD_DIR/stubs-classes:$BUILD_DIR/gen" \
    -d "$BUILD_DIR/classes"

echo "=== Dexing ==="
d8 --min-api 28 \
    --lib "$ANDROID_JAR" \
    --classpath "$BUILD_DIR/stubs-classes" \
    --output "$BUILD_DIR/classes.zip" \
    $(find "$BUILD_DIR/classes" -name "*.class")

echo "=== Packaging APK ==="
cp "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/$OUT_APK"
cd "$BUILD_DIR"
unzip -o classes.zip classes.dex
zip -u "$OUT_APK" classes.dex
cd "$SCRIPT_DIR"

echo "=== Generating keystore ==="
KEYSTORE="$BUILD_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias debug \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Debug,O=DoorSound"
fi

echo "=== Signing APK ==="
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias debug \
    --ks-pass pass:android \
    --key-pass pass:android \
    "$BUILD_DIR/$OUT_APK"

echo ""
echo "=== Build complete ==="
echo "APK: $BUILD_DIR/$OUT_APK"
echo ""
echo "Install: adb install $BUILD_DIR/$OUT_APK"
echo "After install, whitelist in BYD auto-start manager:"
echo "  Settings > Apps > Auto-start management > enable 'Door Sound'"
