# PlayAgain32 0.1.6-alpha build and validation status

## GLES and idle-signal fixes

The follow-up adds capability-gated GLES extension wrappers and correctly uploads explicitly flushed guest buffer ranges. The diagnosis and device evidence are recorded in [Issue #2 evidence](docs/ISSUE-2-IDLE-ARMIES.md#android-17-startup-follow-up).

The source-built ARM64 runtime/converter build and `tools/test.ps1` passed, including repository and parser/layout checks, ARM/Thumb fixture compilation, Android JVM tests, lint, debug assembly and APK privacy validation. On a connected ARM64-only Android 17/API 37 device with 4 KiB pages, the supplied Idle Armies 1.20 APK converted and installed with this build; after the buffer-flush follow-up, the game loaded successfully without the original startup abort or black screen. This verifies startup on that configuration only; it does not establish long-session play, 16 KiB-page support or broad device/game compatibility.

The runtime can now acknowledge Mono's suspend signal when the target guest thread is between native calls. Existing prepared installs pick up the fix when prepared again with a manager that includes the updated runtime. See the [idle freeze follow-up](docs/ISSUE-2-IDLE-ARMIES.md#idle-freeze-follow-up).

These changes were contributed in [PR #3](https://github.com/OTAKUWeBer/PlayAgain32/pull/3). The Android 17 evidence above is contributor-reported. Maintainer validation below uses a separate Android 14 configuration. Production APKs use the persistent release certificate; debug builds use a separate certificate and cannot update production installations in place.

Verified locally on 11 November 2026. Experimental alpha; compatibility varies by game and device.

## Artifacts and signing

- Release: [v0.1.6-alpha](https://github.com/OTAKUWeBer/PlayAgain32/releases/tag/v0.1.6-alpha).
- Assets: signed ARM64 manager APK, exact corresponding-source ZIP and `SHA256SUMS.txt`.
- Application ID `dev.legacybridge.app`; version `0.1.6-alpha`, code 7.
- Minimum Android API 31, target API 36, compile API 37; native ABI `arm64-v8a`.
- Persistent public signing certificate SHA-256: `2a8f9fdbe244488cebf9ebff3e04046731af2feb5f8a74ddf6a71560f09f05f5`.
- Signature verification, `zipalign -c -P 16 4` and build-path privacy checks are required before publication. The private release key stays outside Git and the APK.

## Automated checks

| Check | Local result |
|---|---|
| Publication/privacy/GLES lookup checks | 5 passed |
| Compiled-manifest / SDK-copy tests | 16 passed |
| ELF metadata validator | 14 passed |
| Frame policy | 2 passed |
| ARM32 libc layout/environment/division | 4 passed |
| Ancillary headers and timestamp conversion | 3 passed |
| Android JVM tests | 26 passed |
| ARM/Thumb native fixture compilation | Passed, warnings treated as errors |
| Source-built runtime/converter | Passed |
| Android compilation, lint and debug assembly | Passed; existing lint warnings remain |

**70 focused automated tests passed.** [Hosted CI](https://github.com/OTAKUWeBer/PlayAgain32/actions) rebuilds the runtime and compiles all fixtures, including the Android idle-signal test. Physical-device execution is a separate check: `tools/test-device.ps1` now runs all seven checks: six combined ARM/Thumb native/GPU tests and the idle-signal regression. All seven passed on the maintainer test device. The GPU checks assert real shader-binary round trips and rendered pixels, including explicit EXT buffer flushing when advertised. The idle check requires signal delivery before another guest call. Local logs and Android reports are ignored build output.

## Game and device evidence

[Issue #2 evidence](docs/ISSUE-2-IDLE-ARMIES.md) records the full 19 missing imports, their implementations, graphics startup fixes, APK hash and test scope. Idle Armies 1.20's prepared ARM64 diagnostic copy installed and reached animated offline gameplay. Touch navigation, increasing coins/steps, background/resume and cold restart with retained progress were observed. Final captured logs contained no stopped guest threads, panic, fatal Java exception or refused heap operation.

For 0.1.6, maintainer testing independently repeated the ARM64 diagnostic-game install, startup, touch navigation, background/resume and cold restart on Android 14/API 34 with 4 KiB pages. SurfaceFlinger presentation timestamps advanced at every 30-second sample over 180 seconds without agent input. Final captured logs contained no stopped guest threads or signal handlers, fatal Java exception, host panic or refused heap operation. The production-signed manager updated from 0.1.5 to 0.1.6 in place and cold-launched; no existing app was uninstalled or cleared for that update.

This maintainer device supports both ARM32 and ARM64; the diagnostic game and runner were explicitly ARM64, exercising translated ARM32 code. This driver's GLES fixture did not advertise `GL_EXT_map_buffer_range`, so its conditional EXT map/flush branch was skipped; execution of that branch is supported by the contributor's Android 17 evidence, not this independent run. Automatic in-app translation on an ARM64-only phone and the reporter's Android 17 environment were not independently repeated here. Earlier tests: [issue #1 native regressions](docs/ISSUE-1-NATIVE-IMPORTS.md), [game matrix](docs/COMPATIBILITY-TESTING.md), [Angry Birds report](docs/DEVICE-TEST-2026-11-10.md).

## Security and remaining limits

New message conversions bound vector/control sizes and close truncated transferred descriptors. Account results use bounded reusable guest buffers; environment updates reclaim owned allocations and preserve interior aliases. Unsupported process execution/tracing returns explicit errors. The unknown-import installation gate remains enabled; diagnostics now list all missing names. These tests do not establish global leak freedom or replace an external security audit. See [prior focused review](docs/SECURITY-REVIEW-0.1.4.md) and [new implementation evidence](docs/ISSUE-2-IDLE-ARMIES.md).

Personal device details and raw logs are excluded from Git and release assets. Native builds remap private host paths, and local/CI APK validation rejects embedded home paths. Downloaded games and proprietary assets are not distributed.

Android 17/ARM64-only coverage is contributor-reported. Independent 16 KiB-page execution, sustained thermal/performance behavior and completed-level save integrity remain unverified. Idle Armies has some left-edge DPS/step text clipping. General full-screen layout repair, split APK bundles, durable conversion after process death and general save/key migration are unfinished. Angry Birds' HUD can clip with Fill screen enabled; leave it off for that game. Other game-test gaps are listed in the matrix.

The manager works offline without a network permission. Individual games retain their own requirements. Prepared-game signing keys are phone-local and removed when PlayAgain32 is uninstalled; losing a key can prevent updates without reinstalling games and potentially losing saves.

Build environment: Rust 1.99.1 Windows GNU, NDK 28.2.13676358, CMake 3.22.1/Ninja, libclang 18.1.1; Gradle 9.7.1, AGP 9.4.1, SDK 37/build-tools 36. Local JDK 25; CI JDK 21. See [build instructions](tools/BUILD-WINDOWS.md) and [release process](docs/RELEASING.md).
