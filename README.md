# PlayAgain32

<img src="branding/playagain32-mark.png" width="128" alt="PlayAgain32 gamepad and replay-arrow logo">

**Old games. New phones.**

An offline Android app, built as a modified fork of [Game Bridge](https://github.com/braxten509/arm32-bridge), for importing older games and preparing ARM32 native APKs for ARM64-only phones. Early alpha; compatibility is experimental and not universal.

**Early alpha · Android 12+ · ARM64.** Compatibility varies by game and device.

**[Download APK](https://github.com/OTAKUWeBer/PlayAgain32/releases/download/v0.1.5-alpha/PlayAgain32-0.1.5-alpha-arm64-v8a.apk)** · [Release notes](https://github.com/OTAKUWeBer/PlayAgain32/releases/tag/v0.1.5-alpha) · [Compatibility and device tests](docs/COMPATIBILITY-TESTING.md) · [Build status](BUILD-STATUS.md)

## Using the app

1. Obtain a standalone game APK you have the right to use. Split APK sets and store download bundles are not supported yet.
2. Open PlayAgain32 and import the APK using Android's document picker. The app copies it into private storage and explains its architecture and compatibility.
3. Tap **Prepare & install** for unsupported ARM32 games or games below Android's minimum install target; compatible originals use **Install original**. Allow installation from PlayAgain32 when Android asks, then return to the app.
4. Confirm installation in Android's installer. Import the matching main/patch OBB files if the game needs them and follow the app's data-copy instructions.
5. Launch the installed game. If it fails, export diagnostics and [report the device, game version and failing step](CONTRIBUTING.md).

Official APKs are published on [this repository's Releases page](https://github.com/OTAKUWeBer/PlayAgain32/releases). A source push does not create a release. GitHub Actions development artifacts are for testing and do not provide a persistent update signing identity.

The Android manager uses Kotlin/Jetpack Compose. The native runtime and APK converter use Rust, with the existing Game Bridge ARM/Thumb translator and patched Unicorn reference/fallback engine. Games are installed as their own Android packages; their original Java runs on Android ART.

## Implemented

- Document-picker imports into private storage; original source files are never moved or edited.
- Bounded ZIP/CRC/ELF checks, content-hash deduplication, atomic publication and free-space checks.
- Architecture routing: translate unsupported ARM32 native code; install compatible originals unchanged; prepare old-target APKs without translating their compatible Java/native code; explain unsupported architectures/minimum Android versions.
- Optional **Fill screen** preparation flag, off by default. It allows a wider Android window; games with fixed layouts can clip their menu/HUD. Leave it off for Angry Birds Seasons; a general layout correction is not implemented.
- On-device conversion, per-phone Android Keystore signing and Android-confirmed installation.
- No automatic uninstall/replacement of differently signed originals.
- Main/patch OBB imports for the selected game, with bounded copying and filename validation.
- Import and conversion busy state survives activity recreation. Local progress and diagnostic-report export.
- Hardened ELF metadata loading; original game pacing retained by default.
- No account, ads, INTERNET permission, whole-device storage permission or cloud backend.

## Requirements and current limits

- Android 12+; ARM64 phone for the packaged translation runtime.
- Single standalone APKs up to 512 MiB; expanded ZIP data at most 2 GiB. Split APK sets and XAPK/APKM/APKS imports are intentionally rejected in this alpha.
- Main/patch expansion files up to 2 GiB each. Android/obb access remains device-policy dependent; after enabling installation permission, restart the app when prompted. The app reports data-copy failures rather than calling installation a successful game run.
- Compatible originals retain their signature. Preparing an old target SDK (minimum 23 on Android 14, 24 on Android 15+) or enabling Fill screen changes the manifest and requires re-signing; signature-sensitive games can fail. These changes do not repair every Android behavior change.
- Converted guest ARM32 libraries in an ARM64 folder are accepted only with matching bridge/loader components and validated guest/host ELF headers. Angry Birds' runtime test still used a manually prepared diagnostic copy; a complete in-app Angry Birds preparation flow is unverified.
- Translation refuses preflight-detected missing native functions. That can reject games which happen to avoid those functions during a short play session; broader compatibility needs tested implementations/profiles.
- 16 KiB ELF/APK packaging alignment does not establish runtime correctness on 16 KiB-page devices. The UI identifies this unverified configuration.
- Keep the app open during conversion. Activity rotation is handled; Android process termination interrupts work. Imported source files remain available for retry, but conversion is not a durable background service.
- Game signing keys are device-local and removed when PlayAgain32 is uninstalled. Reinstalling can prevent updates of previously prepared games without uninstalling those games, which can erase their saves. There is no general saved-game backup/restore feature in this alpha.
- Missing assets, removed servers, original-signature checks, proprietary APIs and unsupported engines can prevent play. No game/device compatibility is independently verified by compilation alone.

## Build on Windows

See [the tested Windows toolchain instructions](tools/BUILD-WINDOWS.md). The pinned build uses Rust 1.99.1 Windows GNU, Android ARM64 target, Android NDK 28.2.13676358, CMake 3.22.1/Ninja, Git for Windows and libclang 18.1.1. CI selects a full JDK 21; the tested local JDK was 25.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\tools\build-android.ps1 -Sdk $env:ANDROID_HOME -RustToolchain 1.99.1-x86_64-pc-windows-gnu
```

The script builds native binaries from source before assembling the app. It does not download a prebuilt runtime, install a game or contact a phone. Android Studio/Gradle packaging also checks that the four native runtime files exist and rejects an embedded debug signing key. Build-time Maven/Cargo/SDK downloads require network access; the installed manager works offline.

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`. It is a local test build, not a signed production release. Publishing requires a maintained app release key, corresponding source/notices and device acceptance tests.

Local named artifacts live under ignored `dist/`; APKs and private signing keys are not committed. The manager code lives under `dev.playagain32.app`. The installed application ID and game-key alias remain unchanged from the earlier alpha so updates signed with the same certificate can preserve imported files and signing identity; details are in [branding/README.md](branding/README.md).

## Checks

```powershell
.\tools\test.ps1 -RustToolchain 1.99.1-x86_64-pc-windows-gnu
```

For publication checks and native host tests without Android:

```powershell
.\tools\test.ps1 -SkipAndroid -RustToolchain 1.99.1-x86_64-pc-windows-gnu
```

See [BUILD-STATUS.md](BUILD-STATUS.md) for checks actually performed, artifacts and outstanding device validation. Hardware/game validation must cover input/audio/rendering, cold restart, pause/resume, save integrity, thermal behavior and representative 4 KiB/16 KiB phones before stability claims.

[CI](.github/workflows/ci.yml) rebuilds native files, runs publication and parser tests, Android JVM tests and lint, and assembles a development APK. The [manual release workflow](.github/workflows/release.yml) requires a maintained signing key and produces a draft alpha with matching source and checksums. Read [release setup](docs/RELEASING.md), [contribution guidance](CONTRIBUTING.md) and [security reporting](SECURITY.md) before distributing builds.

## Attribution and license

**Original project and author:** [Game Bridge / arm32-bridge](https://github.com/braxten509/arm32-bridge) by [Braxten Chenay (@braxten509)](https://github.com/braxten509). PlayAgain32 is a modified fork based on [upstream commit 83438366](https://github.com/braxten509/arm32-bridge/commit/83438366eaf3d6728b1bd221feb745f7db50f032); its ARM/Thumb translator, runtime and converter originate there. Original copyright and notices are retained.

**PlayAgain32 changes and maintenance:** [OTAKUWeBer](https://github.com/OTAKUWeBer). This repository starts with a consolidated source snapshot; that history cleanup does not imply sole authorship or change the upstream license. PlayAgain32 is an independent modified version and is not an upstream release.

GPL-2.0-or-later; see [LICENSE](LICENSE). The built bridge includes patched Unicorn and carries the applicable GPLv2 obligations; preserve its notices and corresponding source. Kotlin/AndroidX and other dependencies retain their individual licenses. No ZettaBridge/Afterlife code was imported. The repository retains upstream source components outside the shipped Android flow; iOS playback, broad storage scanning, arbitrary frame-rate unlocking and server-revival features are not exposed by this manager.

This GPL-derived project is not MIT-licensed. A license change would require the relevant copyright permissions or replacing the GPL code; see [release licensing notes](docs/RELEASING.md#license).
