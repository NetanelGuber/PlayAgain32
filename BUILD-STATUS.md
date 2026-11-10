# PlayAgain32 0.1.4-alpha build and validation status

Verified locally on 11 November 2026. This is an experimental alpha. Resolving a game's imported symbols does not establish gameplay compatibility.

## Artifacts and signing

- Public release: [v0.1.4-alpha](https://github.com/OTAKUWeBer/PlayAgain32/releases/tag/v0.1.4-alpha).
- Release assets: signed ARM64 APK, exact corresponding-source ZIP and `SHA256SUMS.txt`. Use the checksums attached to that release; development APKs have different bytes and signatures.
- Application ID `dev.legacybridge.app`; version `0.1.4-alpha`, code 5.
- Android minimum API 31, target API 36, compile API 37; native ABI `arm64-v8a`.
- Public signing certificate SHA-256: `2a8f9fdbe244488cebf9ebff3e04046731af2feb5f8a74ddf6a71560f09f05f5`. This is the persistent release identity introduced in 0.1.3, distinct from the local debug identity.
- APK signature and `zipalign -c -P 16 4` checks are required before publication. The private release key stays outside Git and is never embedded in the manager.

## Automated checks

| Check | Observed local result |
|---|---|
| Publication checks: ignored secrets/build outputs, private-key scan, documentation links | 4 passed |
| Production compiled-manifest / SDK-copy tests | 16 passed |
| Shared ELF metadata validator tests | 14 passed |
| Frame-policy tests | 2 passed |
| ARM32 libc layout/environment/division tests | 4 passed |
| Android JVM tests: APK inspection | 12 passed |
| Android JVM tests: bounded/atomic storage | 6 passed |
| Android JVM tests: compatibility routing | 6 passed |
| Android JVM tests: OBB ownership/names | 1 passed |
| Synthetic import fixture compilation, ARM and Thumb | Passed, warnings treated as errors; static ABI assertions enforced |
| Source-built ARM64 runtime and converter | Passed |
| Android Kotlin/Compose compilation, lint and debug assembly | Passed; existing lint warnings remain |
| Development APK signature / ZIP/native alignment | Passed |

**65 focused automated tests passed.** Android tasks may reuse valid Gradle outputs. Local build/test logs are in ignored `.build/issue-1-*.log`; Android reports are under `android/app/build/`. [Hosted CI](https://github.com/OTAKUWeBer/PlayAgain32/actions) rebuilds native files from source and runs the checks; local results do not certify a new hosted run before it finishes.

## Physical-device evidence

On a an anonymized Android test device with Android 14/API 34 and 4 KiB pages, an ARM64 runner executed synthetic ARM32 code through the compatibility engine. The phone also supports native ARM32; this does not establish Android 17 or ARM64-only system behavior.

- Combined import preflight for ARM/Thumb regression libraries reported zero missing imports.
- Eighteen final default-translator invocations passed across ARM and Thumb, with lock checking enabled, including memory/resource stress and heap-growth measurements.
- Tests exercise the division hidden result pointer, environment vector/overwrite behavior, epoll cookies/one-shot/rearm/delete/timeouts/errors, and explicit `execv` refusal.
- An allocator warning in initial CLI shutdown tests was corrected by releasing engines and stopping/joining the preparation worker before allocator teardown; five repeated fast preflight exits were also clean. Final combined ARM and Thumb reference-engine tests returned zero without the warning.
- The development manager updated in place to 0.1.4 and cold-launched; existing imported files remained present. No installed app or game data was cleared.

See [issue #1 implementation and evidence](docs/ISSUE-1-NATIVE-IMPORTS.md), [game smoke-test matrix](docs/COMPATIBILITY-TESTING.md), and [original Angry Birds device report](docs/DEVICE-TEST-2026-11-10.md).

## Security and privacy

Replaced environment strings and vectors are reclaimed, failed updates roll back safely, and epoll scratch allocations remain bounded. Repeated-update tests measured zero additional guest arena growth after warm-up. Personal device details were removed from public history/archives, and native builds remap private paths. Local, CI and release APK checks reject embedded build-user home paths. See the [focused review](docs/SECURITY-REVIEW-0.1.4.md); these checks do not establish global leak freedom or replace an external audit.

## Scope and remaining limits

0.1.4 adds the reported `div`, `environ`, epoll and `execv` handlers. `execv` returns `-1` with `ENOSYS`, matching the existing unsupported process-operation policy; it cannot launch ARM32 child processes. The unknown-import installation gate remains enabled.

The Idle Armies APK was absent from the test phone. Its full import list, in-app preparation/install, startup, gameplay and saves remain unverified. Do not interpret the synthetic tests as a verified game fix or close issue #1 on this evidence alone.

ARM64-only/Android 17 and 16 KiB-page runtime behavior, sustained performance/thermal behavior, completed-level save integrity, general full-screen layout repair, split APK bundles, durable conversion after process death and general save/key migration remain unverified or unfinished. Angry Birds' menu/HUD can clip when Fill screen is enabled; leave that option off for this game. Frozen Bubble, Freedoom and SuperTux gameplay tests remain incomplete.

The manager works offline and has no network permission. Individual games retain their own permissions and requirements. Prepared-game signing keys are phone-local and removed when PlayAgain32 is uninstalled; losing that key can prevent updates without reinstalling those games and potentially losing saves.

## Rebuild environment

Rust 1.99.1 Windows GNU, Android ARM64 target, NDK 28.2.13676358, CMake 3.22.1/Ninja, libclang 18.1.1, Git for Windows; Gradle 9.7.1, AGP 9.4.1, local JDK 25, SDK platform 37/build-tools 36. CI uses a full JDK 21. See [Windows build instructions](tools/BUILD-WINDOWS.md) and [release process](docs/RELEASING.md).
