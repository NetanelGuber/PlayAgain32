#!/bin/bash
# Builds the phone (arm64 Android) binary. Android has no libpthread, but
# Unicorn's CMake links it, so an empty stand-in archive is put on the path.
# TARGET=x86_64-linux-android builds the copy for the PC's Android emulator.
set -e
cd "$(dirname "$0")"
export NDK=${NDK:-$HOME/Android/Sdk/ndk/30.0.16248370}
TARGET=${TARGET:-aarch64-linux-android}
case "$(uname -s)" in
    Darwin) HOST=darwin-x86_64 ;;
    Linux) HOST=linux-x86_64 ;;
    *) echo 'On Windows use tools/build-android.ps1.' >&2; exit 1 ;;
esac
case "$TARGET" in
    aarch64-linux-android) ARCH=aarch64 ;;
    x86_64-linux-android) ARCH=x86_64 ;;
    *) echo "Unsupported Android target: $TARGET" >&2; exit 1 ;;
esac
BIN=$NDK/toolchains/llvm/prebuilt/$HOST/bin
TARGET_ENV=${TARGET//-/_}
TARGET_UPPER=$(printf '%s' "$TARGET_ENV" | tr '[:lower:]' '[:upper:]')
export "CC_$TARGET_ENV=$BIN/${TARGET}30-clang"
export "CXX_$TARGET_ENV=$BIN/${TARGET}30-clang++"
export "AR_$TARGET_ENV=$BIN/llvm-ar"
export "CARGO_TARGET_${TARGET_UPPER}_LINKER=$BIN/${TARGET}30-clang"
export "BINDGEN_EXTRA_CLANG_ARGS_$TARGET_ENV=--target=${TARGET}30 --sysroot=$NDK/toolchains/llvm/prebuilt/$HOST/sysroot"
export CMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake
BUILTINS=$(find "$NDK/toolchains/llvm/prebuilt/$HOST/lib/clang" -name "libclang_rt.builtins-${ARCH}-android.a" -print -quit)
export RUSTFLAGS="${RUSTFLAGS:-} -L$PWD/android-link-stubs -C link-arg=$BUILTINS"
export LDFLAGS="-L$PWD/android-link-stubs"
cargo build --release --target "$TARGET" "$@"
