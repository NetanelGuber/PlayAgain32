#!/bin/bash
# Builds the PlayAgain32 phone app (android/): the bridge, the shared stand-in,
# the 64-bit frame helper and the converter (apk-convert) go into its library folder;
# then Gradle builds the APK. Game signing keys stay in each phone's Android
# Keystore. With --install the app is installed over ADB.
# --public is the release build for other people; the app is
# signed with PlayAgain32's release key (tools/release-key.sh; password from KDE
# Wallet). It is copied to android/PlayAgain32-<version>.apk.
#
# usage: tools/build-app.sh [--public] [--install]
set -euo pipefail
PUBLIC=0 INSTALL=0
for option in "$@"; do
    case $option in
        --public) PUBLIC=1 ;;
        --install) INSTALL=1 ;;
        *) echo "unknown option $option" >&2; exit 2 ;;
    esac
done
ROOT=$(cd "$(dirname "$0")/.." && pwd)
NDK=${NDK:-$HOME/Android/Sdk/ndk/30.0.16248370}
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
LIBS=$ROOT/android/app/src/main/jniLibs/arm64-v8a
ASSETS=$ROOT/android/app/src/main/assets
mkdir -p "$LIBS" "$ASSETS"

echo "== bridge and converter"
(cd "$ROOT" && ./build-phone.sh -q --lib -p arm32-bridge && ./build-phone.sh -q -p apk-convert && ./build-phone.sh -q --lib -p gamebridge-pacer && ./build-phone.sh -q --lib -p gamebridge-ios)
OUT=$ROOT/target/aarch64-linux-android/release
cp "$OUT/libarm32_bridge.so" "$LIBS/libarm32_bridge.so"
# The frame helper for 64-bit games: the converter copies it into each one, so it travels small.
"$BIN/llvm-strip" -o "$LIBS/libgamebridge_pacer.so" "$OUT/libgamebridge_pacer.so"
# The iPhone layer, loaded by the iPhone game screen: 64-bit iPhone games run
# directly, 32-bit (ARMv7) ones on the translator built into it.
"$BIN/llvm-strip" -o "$LIBS/libgamebridge_ios.so" "$OUT/libgamebridge_ios.so"
# Android only unpacks lib*.so files, so the converter program travels under that name.
"$BIN/llvm-strip" -o "$LIBS/libapkconvert.so" "$OUT/apk-convert"
"$BIN/aarch64-linux-android30-clang" -O2 -shared -fPIC -Wl,--build-id=none -o "$LIBS/libstandin.so" \
    "$ROOT/tools/standin/standin.c" -llog -ldl
[ ! -e "$ASSETS/debug.keystore" ] || { echo "Remove assets/debug.keystore; private game-signing keys must not be packaged." >&2; exit 1; }
if [ $PUBLIC = 1 ]; then
    export PLAYAGAIN32_KEYSTORE=$HOME/dev/playagain32-key/playagain32-release.p12
    [ -f "$PLAYAGAIN32_KEYSTORE" ] || { echo "No release key; run tools/release-key.sh first." >&2; exit 1; }
    PLAYAGAIN32_KEY_PASSWORD=$(kwallet-query -f PlayAgain32 -r release-key kdewallet)
    export PLAYAGAIN32_KEY_PASSWORD
fi

# The template of each iPhone game's own app (IphoneApps.kt).
"$ROOT/tools/build-iphone-launcher.sh"

echo "== app"
(cd "$ROOT/android" && ./gradlew -q assembleRelease)
APK=$ROOT/android/app/build/outputs/apk/release/app-release.apk
if [ $PUBLIC = 1 ]; then
    VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$ROOT/android/app/build.gradle.kts")
    cp "$APK" "$ROOT/android/PlayAgain32-$VERSION.apk"
    APK=$ROOT/android/PlayAgain32-$VERSION.apk
fi
ls -la "$APK"
if [ $INSTALL = 1 ]; then
    adb install -r "$APK"
fi
