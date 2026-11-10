# PlayAgain32 0.1.3-alpha build status

Verified locally before the fresh source publication. This is a development alpha, not a production release or universal-game compatibility certification.

## Installable development artifact

- File: `dist/PlayAgain32-0.1.3-alpha.apk` (ignored local output)
- Size: 36759850 bytes
- SHA-256: `9e0a6a3e8e950c978c148a782ee94871f811a35aa7eb717a3633b83ad5c8bec3`
- Application: `dev.legacybridge.app`, version `0.1.3-alpha` / code 4
- Android minimum API 31; target API 36; compile API 37; native ABI arm64-v8a
- Launcher: `dev.playagain32.app.OfflineActivity`
- Local Android development certificate; APK v2 signature and `zipalign -c -P 16 4` verification passed. No production release key has been used.
- Product name/icon: PlayAgain32. Existing install ID and game-key alias are retained for update compatibility.

## Checks performed

| Check | Observed result |
|---|---|
| Source-built ARM64 runtime, converter, pacer and stand-in | Passed; no downloaded prebuilt runtime substituted |
| Publication checks: ignored secrets/build outputs, private-key scan, documentation links | 3 passed |
| Production compiled-manifest / SDK-copy host tests | 16 passed |
| Shared ELF metadata validator host tests | 14 passed |
| Frame-policy host tests | 2 passed |
| Android JVM tests: APK inspection | 12 passed |
| Android JVM tests: bounded/atomic storage | 6 passed |
| Android JVM tests: compatibility routing | 6 passed |
| Android JVM tests: OBB ownership/names | 1 passed |
| Android lint | Passed; existing warnings remain |
| Kotlin/Compose compilation and APK assembly | Passed |
| APK signature and ZIP/native alignment | Passed |
| Packaged permissions | No INTERNET, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES or deletion permission |
| License/notices/private-key checks | GPL/Unicorn notices retained; no embedded private signing key |
| Physical-device smoke tests | Angry Birds diagnostic runtime copy; in-app preparation/install and short play checks for 2048 and GLTron; see test matrix |

Total focused automated tests: **60 passed**, including all 25 Android JVM tests. Android tasks may reuse Gradle's valid outputs. Latest local check output is retained in ignored `.build/final-publication-tests.log`; Android XML results and lint reports are under `android/app/build/`.

The preceding published source passed [hosted CI](https://github.com/OTAKUWeBer/PlayAgain32/actions). A fresh source push starts its own CI; that earlier result does not certify the new commit. [CI](.github/workflows/ci.yml) builds native files from source, tests, lints, assembles and verifies the APK. The manual [release workflow](.github/workflows/release.yml) requires a persistent owner-provided signing key and produces a draft alpha with source/checksums; production signing has not been exercised.

## Changes in this snapshot

- Offline private APK import/library, bounded ZIP/CRC/ELF validation, atomic storage, content deduplication, source-built ARM32 conversion, phone-local game signing and Android-confirmed installation.
- Old-target compatible Java/native APKs can be prepared without CPU translation: minimum target 23 on Android 14 and 24 on Android 15+. This changes the signature and can expose Android behavior changes.
- Valid converted bridge/guest layouts and obsolete extra MIPS ABIs are inspected without confusing their ELF class with the host runtime. Unsupported-only architectures remain rejected.
- Fill screen is an optional manifest change, off by default. It is not a general game-layout repair; Angry Birds menu/HUD clipping remains unresolved with that option enabled.
- Original game APKs, game data and differently signed installations are preserved. Game timing remains unchanged by default. OBB import and diagnostic export remain available.

## Device evidence and remaining limits

See [the current test matrix](docs/COMPATIBILITY-TESTING.md) and [the original Angry Birds report](docs/DEVICE-TEST-2026-11-10.md). Testing used one ARM32-capable Android 14 phone. The wider-window Angry Birds probe was reverted to the previously tested fitting build without clearing data. Frozen Bubble, Freedoom and SuperTux were downloaded, but their install/gameplay checks were stopped at the owner's request.

ARM64-only and 16 KiB-page runtime behavior, long-session performance/thermal behavior, completed-level save integrity, a complete in-app Angry Birds flow, general layout correction, split APK bundles, saved-game backup, durable conversion after process death and production signing remain unverified or unfinished. Missing services, original-signature checks and unsupported native functions can still prevent play.

## Rebuild environment

Rust 1.99.1 Windows GNU, Android ARM64 target, NDK 28.2.13676358, CMake 3.22.1/Ninja, libclang 18.1.1, Git for Windows; Gradle 9.7.1, AGP 9.4.1, local JDK 25, SDK platform 37/build-tools 36. CI selects a full JDK 21. See [Windows build instructions](tools/BUILD-WINDOWS.md).

Build-time dependency downloads require network access. The installed manager has no network permission; individual imported games retain their own permissions and network requirements. Keep PlayAgain32 installed to retain its phone-local game signing key. Removing it can prevent future prepared-game updates without reinstalling those games and losing their saves.
