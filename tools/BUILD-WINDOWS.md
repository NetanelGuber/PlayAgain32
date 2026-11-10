# Building on Windows

Tested with Rust 1.99.1 Windows GNU, Android NDK 28.2.13676358, SDK CMake 3.22.1 and libclang 18.1.1. A modern JDK compatible with Gradle 9.7.1 is required for the Android app. The GNU Rust toolchain avoids requiring Visual Studio Build Tools.

Install the native build prerequisites once:

```powershell
rustup toolchain install 1.99.1-x86_64-pc-windows-gnu --profile minimal --target aarch64-linux-android
python -m pip install --target .build/python-tools libclang==18.1.1

# With Android SDK command-line tools installed and ANDROID_HOME configured:
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --licenses
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" 'platforms;android-37.0' 'build-tools;36.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1'
```

Set `JAVA_HOME` to the JDK and `ANDROID_HOME` to the Android SDK, then run:

```powershell
./tools/build-android.ps1 -RustToolchain 1.99.1-x86_64-pc-windows-gnu
./tools/test.ps1 -RustToolchain 1.99.1-x86_64-pc-windows-gnu
```

`-Sdk` and `-Ndk` override SDK/NDK paths. `-NativeOnly` builds and checks native files without Gradle. `LIBCLANG_PATH` may point to an existing `libclang.dll` instead of the project-local Python package. Git for Windows supplies `sh`, which the vendored Unicorn build needs. SDK CMake supplies Ninja.

The script builds the patched runtime, ARM64 converter executable, frame helper and stand-in library from source. The runtime's build script also builds its ARM32 zlib dependency from NDK sources. Only the Android libraries are staged; the iOS runtime is not built. Rust's self-contained LLVM linker is used for Windows build tools because the GNU linker mishandled the LLVM-generated Windows imports in this environment.

Native output goes to `android/app/src/main/jniLibs/arm64-v8a`. The converter travels as `libapkconvert.so` so Android extracts it, but is an ARM64 PIE executable with an entry point. The script checks ARM64 ELF64 ET_DYN identity and rejects an embedded `assets/debug.keystore`. It does not install anything on a phone. `assembleDebug` signs the app with a local development certificate; no private signing key is embedded in the app's assets.

The native build passing establishes compilation and package identity, not game compatibility. No game or on-device runtime test is performed by this build command.
