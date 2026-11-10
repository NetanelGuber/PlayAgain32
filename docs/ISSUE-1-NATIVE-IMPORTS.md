# Idle Armies missing imports: candidate fix and device evidence

Status: **0.1.4-alpha native-import fix**, tested 11 November 2026. The public 0.1.3-alpha assets have not been replaced. [Issue #1](https://github.com/OTAKUWeBer/PlayAgain32/issues/1) remains a game-compatibility report, not a verified fix for the entire game.

The submitted report identifies Idle Armies 1.20 (`com.grumpyrhinogames.idlearmies`) on Android 17/API 37, ARM64-only, 4 KiB pages. Its listed imports were unhandled in 0.1.3. The existing diagnostic message shows at most six missing names, so the report does not establish that these are the only missing imports.

## Implemented behavior

| Symbol | 0.1.4 behavior |
|---|---|
| `div` | Signed quotient/remainder with C truncation rules, written through the ARM32 hidden result pointer. Undefined zero/overflow cases stop guest execution rather than panic the host. |
| `environ` | Guest-memory, null-terminated vector of 32-bit pointers to guest strings. Initialized from the process environment; guest `getenv`/`setenv` use this same vector, including guest replacement of the vector. Guest environment changes do not mutate the Android host environment. |
| `epoll_create`, `epoll_create1` | Real host epoll descriptors and errno on failure. |
| `epoll_ctl` | Converts the ARM32 event layout explicitly and preserves all 64 bits of event data. Supports add/modify/delete, including null events for deletion. |
| `epoll_wait` | Real events, timeouts and errno; releases the guest execution lock while waiting. Host scratch space is bounded to 1024 events per return, which may be fewer than the caller's maximum. |
| `execv` | Explicit `-1`/`ENOSYS`, matching existing `fork`/`execl` process-operation policy. It does not execute an ARM32 child or replace the Android app process. Games requiring successful process replacement are still unsupported. |

References: [Arm AAPCS32 result return](https://github.com/ARM-software/abi-aa/blob/main/aapcs32/aapcs32.rst#result-return), [Android epoll event layout](https://android.googlesource.com/platform/bionic/+/master/libc/include/bits/epoll_event.h). Installed Android NDK 28.2.13676358 headers and compiled ARM32 static assertions also establish the 16-byte event size/data offset 8.

The unknown-import installation gate remains enabled. No fake success handler was added for these calls.

## Checks observed

- All 65 focused automated tests passed, including production ABI/environment-layout coverage and binary build-path privacy detection. Android lint and debug APK assembly passed; the ARM64 runtime/converter were rebuilt from source.
- `tests/guest/idle_armies_imports.c` compiled for both ARM and Thumb with NDK Clang, warnings treated as errors. Static assertions check 32-bit pointers, the 8-byte division result and epoll event layout. `tools/test.ps1` now compiles both variants when Android checks run.
- Physical device: an anonymized Android test device, Android 14/API 34, 4 KiB pages. It supports both ARM32 and ARM64; the runner is an ARM64 executable explicitly running ARM32 guest code through the compatibility engine.
- Combined import preflight over both test libraries reported **0 imports without a handler**.
- Default ARM64 translator: **18 successful final runtime invocations** across ARM and Thumb, including individual symbol tests, combined tests, memory/resource stress and heap-growth measurement. Lock checking was enabled. Measured guest arena growth after 10,000 environment replacements was zero bytes in each mode; forced allocation failures preserved state, and 2,048 epoll descriptor cycles passed.
- Initial reference-engine runs exposed an allocator warning during thread-local shutdown, also reproduced by an import-free control. Explicit engine cleanup plus stopping/joining the engine-preparation worker before allocator teardown corrected the command-line/converter lifecycle. Five final repeated fast-preflight runs also exited cleanly. Both final combined reference-engine runs returned zero without the warning.
- The development manager updated from 0.1.3 to 0.1.4 in place and cold-launched successfully. Its existing imported files remained present, with the same private-storage paths. No app uninstall/data clearing was performed.

Local evidence is retained under ignored `.build/issue-1/`, including individual device logs and `device-results.json`. No private device logs, game APKs or game assets are committed.

## Reproducing the device regression

Build native files with `tools/build-android.ps1 -NativeOnly`. In that same PowerShell process, build the existing CLI runner:

```powershell
cargo +stable-x86_64-pc-windows-gnu build --locked --release --target aarch64-linux-android --bin arm32-bridge
./tools/test.ps1
```

Copy `target/aarch64-linux-android/release/arm32-bridge` and both `target/host-tests/libimports-{arm,thumb}.so` files into a dedicated `/data/local/tmp/` folder on an authorized phone. Make the runner executable and run its `imports` command over the two libraries. Then run `call LIBRARY run_import_tests` with `ARM32_ENGINE=jit` and `ARM32_LOCKCHECK=1`; the expected printed result is `run_import_tests returned 0`. Individual exported `test_div`, `test_environment`, `test_epoll` and `test_execv` functions isolate a failure. The CLI's process exit status alone does not establish that a test returned zero.

## Remaining acceptance gate

The Idle Armies APK was neither installed nor found under Downloads on the test phone. Its actual full import list, APK hash, in-app preparation/install, startup, gameplay and save behavior have not been verified. Android 17/ARM64-only behavior also remains untested. These synthetic tests establish the tested handler behavior; they do not establish that Idle Armies is playable. Do not close the compatibility issue solely because these symbols now resolve.

See the [focused security and memory review](SECURITY-REVIEW-0.1.4.md) for allocation ownership, test scope and publication privacy.
