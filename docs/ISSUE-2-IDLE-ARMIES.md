# Idle Armies: startup and idle gameplay

Tested 11 November 2026 for **0.1.5-alpha**. [Issue #2](https://github.com/OTAKUWeBer/PlayAgain32/issues/2) supplied Idle Armies 1.20, package `com.grumpyrhinogames.idlearmies`, version code 31.

## What failed

The previous diagnostic displayed only the first six missing imports. Preflight over **all four ARM libraries** found 19:

```text
execve fabsf flock getgrgid getgrnam getpwnam getresuid ptrace realpath
recvmsg sendfile sendmsg setresuid strsep strtoimax strtoumax tkill unsetenv waitpid
```

All 19 now have handlers, and diagnostics include every missing name. The unknown-import installation gate stays enabled.

Passing imports was insufficient: Unity 2018.2 then stopped a translated graphics thread on a null function pointer. Runtime evidence identified missing shader-binary and base-vertex drawing extension handlers. The GLES generator now includes those extensions and the related query, copy, label and marker functions requested by this engine. Pointer arguments still use the existing guest-pointer/bound-buffer conversion. The KHR debug callback follows the existing core callback policy: it is not forwarded as a host function pointer.

## Native behavior and ownership

- Account lookups copy ARM32 `passwd`/`group` layouts, strings and member pointers into reusable guest buffers. They never return host pointers. Lookup buffers are capped at 64 KiB; repeated lookups reuse allocations.
- `sendmsg`/`recvmsg` convert the ARM32 message, iovec and ancillary layouts. Vector/control sizes are bounded, timestamp widths are converted, and file descriptors omitted by control-buffer truncation are closed.
- `sendfile` uses a separate host offset before copying back the four-byte guest offset. `flock` and blocking I/O release the guest execution lock and preserve error results.
- `strtoimax`/`strtoumax` preserve signedness, end pointers and overflow errors. `strsep`, `realpath`, UID queries and `fabsf` use their normal semantics.
- `unsetenv` shares the existing environment publication/ownership logic, preserving live interior aliases and reclaiming replaced allocations. Guest string duplication now checks allocation failure before writing.
- `tkill` targets registered guest identities only. Unsupported `execve`/`ptrace` return `-1`/`ENOSYS`; `waitpid` returns `ECHILD` because guest child creation is unsupported. These calls do not pretend to execute child processes or trace other apps.

## Supplied APK checks

Original SHA-256:

```text
1a09818f6e88c3d5814a4bf1763d264c6c8218e7956efb86d6aa3f50faad22b5
```

Android SDK `apksigner` verified its v1/v2/v3 signatures. ZIP CRC/path checks passed. Manifest review found Internet, network-state and billing permissions, with no declared accessibility service, device administration, contacts or SMS access. Local Defender reported no threats. These checks are not proof that an APK is harmless or that its signer is the original developer. No game APK, proprietary assets, personal screenshots or raw device logs are distributed here.

Testing used an explicitly prepared **ARM64 diagnostic copy** with the source-built runtime, offline throughout. No accounts, ads or purchases were exercised. Wi-Fi and mobile data remained off; device security protections were not disabled.

## Observed tests

| Check | Result |
|---|---|
| Full original ARM library import preflight | Zero missing handlers |
| Automated host/JVM checks | 69 passed; Android lint and APK assembly passed |
| Final physical-device validator | Six combined tests passed: imports, Mono/POSIX and GLES, each in ARM and Thumb mode |
| GLES shader-binary round trip | Compiled, exported, reloaded and linked a real shader program |
| GLES base-vertex and instanced base-vertex draws | Read-back pixel assertions passed with a bound index buffer |
| Account/resource stress | 10,000 repeated lookups with bounded arena growth; 2,048 truncated descriptor-transfer cycles |
| Actual game | ARM64 install, loading screen, animated combat, increasing coins/steps and touch navigation to Feats observed |
| Lifecycle | Background/resume and cold restart returned to gameplay with progress retained |
| Captured final game logs | No stopped guest threads, host panic, fatal Java exception or refused heap operation found |

The phone runs Android 14/API 34, uses 4 KiB pages and also supports ARM32. The installed diagnostic game's native ABI was verified as ARM64; the original ARM32 libraries ran through the translator. This does **not** establish Android 17, ARM64-only system behavior or the automatic in-app translation route on such phones. The normal manager would offer the compatible original on this test phone.

Some left-edge DPS/step text is clipped in the captured game UI. General layout repair, long-session stability, game audio, purchases, cloud services, completed-level save integrity and 16 KiB-page execution are not established by this test.

## Android 17 startup follow-up

On 10 November 2026, the supplied APK was reproduced on an Android 17/API 37 ARM64-only device with 4 KiB pages. Its SHA-256 matched the APK above. The installed 0.1.5-alpha manager successfully prepared and installed the game, but launch aborted from Unity's `nativeRender` during initial GLES framebuffer setup. The bridge trace ended after `glFramebufferRenderbuffer`; the guest jumped to address zero.

The failure exposed a resolver gap. The game requested GLES extension names through `eglGetProcAddress`, but the bridge had no guest-side wrappers. A separate GLES 3.2 probe confirmed that the host driver exposes five of the requested names: `glLabelObjectEXT`, `glGetObjectLabelEXT`, `glDrawBuffersEXT`, `glBlitFramebufferNV` and `glMapBufferRangeEXT`. The bridge now wraps those calls, converts guest pointers, and only returns a guest stub when EGL exposes the requested function. `glFramebufferTexture3D` and `glTexImage2DMultisample` were absent from the host probe and remain unsupported/null. Optional aliases remain blocked for static-import preflight, where the host context cannot be checked.

The first rebuilt manager no longer aborted at startup, but the game remained on a black screen. Tracing showed that the game repeatedly called `glMapBufferRangeEXT` with `GL_MAP_FLUSH_EXPLICIT_BIT`, then called `glFlushMappedBufferRangeEXT`. The map handler returned a guest-memory copy, but the EXT flush name still used the generated host wrapper, so writes to that copy were never uploaded. Both flush names now upload explicitly flushed guest ranges. The existing EXT import remains available, and calls for driver-owned mappings still go to the host driver.

The source-built Android ARM64 runtime/converter build and the full `tools/test.ps1` workflow passed with the fix. The fork's 0.1.6-alpha/code 7 debug manager updated in place, the supplied APK was prepared and installed again, and its launch reached the game screen on the tested ARM64-only Android 17/API 37, 4 KiB configuration. The user confirmed that the game loaded successfully. This verifies the startup path on that device only; gameplay duration, other Android versions, 16 KiB pages and broad game/device compatibility remain unverified. The locally built manager uses the Android Debug certificate; a persistent release keystore is not configured in this workspace.

## Idle freeze follow-up

With the startup fix alone, the foreground game stopped presenting frames during an idle session. The display stayed awake and Unity's Choreographer callbacks continued. Unity's main thread was blocked in Mono's garbage collector, waiting for a guest thread to acknowledge a suspend signal. The bridge only let signal helpers borrow threads with a live guest call. A thread waiting in Java between native calls could leave Mono waiting until touch input entered guest code again.

The parallel translator now lets signal helpers deliver pending guest signals between top-level calls. The helper uses the target's guest thread identity and an empty guest stack context. Guest re-entry and thread teardown wait for any active helper before using or releasing that stack.

On Android 17/API 37, ARM64-only, with 4 KiB pages, the rebuilt debug manager's packaged converter and on-device signer prepared the original Idle Armies 1.20 APK. The prepared game installed over the previous copy using ADB. SurfaceFlinger continued reporting new frames throughout a three-minute foreground run without input. The game also resumed rendering after a trip to the Android home screen in the same process. The idle signal regression, all six ARM/Thumb device fixtures and `tools/test.ps1` passed. This run covers the reported idle freeze on that configuration.

Install a manager build that includes the fix and select **Prepare & install** on the imported Idle Armies APK. Preparation copies the updated runtime into the game. Android can update the prepared game in place when the manager still has the signing key used for the existing copy.

## Independent 0.1.6 maintainer validation

On 11 November 2026, all 70 host/JVM checks and all seven physical-device checks passed on Android 14/API 34 with 4 KiB pages. The seventh check is the idle-signal regression; it is now compiled by the native build and executed by the same device validator as the ARM/Thumb fixtures.

The original APK hash matched the report above. The new runtime prepared an ARM64 diagnostic copy with zero missing imports. Installation, offline startup, touch navigation, background/resume and cold restart returned to the game screen. SurfaceFlinger presentation timestamps advanced at all seven samples taken 30 seconds apart over three minutes without agent input. Captured final logs contained no stopped guest thread/signal handler, fatal Java exception, host panic or refused heap operation. The production manager updated from 0.1.5 to 0.1.6 in place and cold-launched without clearing app data.

The test device also supports ARM32, but the diagnostic game and runner were explicitly ARM64. The conditional EXT map/flush fixture branch was skipped because this driver did not advertise `GL_EXT_map_buffer_range`; the contributor's Android 17 run remains the execution evidence for that branch. This does not add independent ARM64-only, Android 17 or 16 KiB-page coverage. Existing left-edge HUD clipping remains.

## Repeatable validator

Run the normal native build, host/Android checks and device validator. The native build now compiles both the CLI runner and the idle-signal regression automatically:

```powershell
./tools/build-android.ps1 -NativeOnly
./tools/test.ps1
./tools/test-device.ps1 -Serial YOUR_AUTHORIZED_DEVICE
```

The device script copies only project-owned fixtures into its own temporary folder, checks all their imports, runs ARM/Thumb native and real GPU tests with timeouts and lock checks, requires explicit zero test results, and removes its temporary files. Where the driver advertises `GL_EXT_map_buffer_range`, the GLES fixture maps through a dynamic lookup, calls the static EXT flush import, and checks the rendered pixel. This path passed in both ARM and Thumb mode on the Android 17 test device. It installs no app and changes no device settings. CI compiles all fixtures; actual GPU execution requires the separate device check. A static import validator cannot detect every optional dynamic lookup, graphics failure, missing asset or offline-server dependency. Real game launch remains part of release acceptance.

The idle-signal test is included in the same device command, with a timeout and explicit pass-result check. It sends a guest signal from another host thread and checks that the handler finishes before the target re-enters guest code. The source build stages this executable as `target/host-tests/idle-signals`; no manual Cargo-artifact lookup or separate ADB invocation is required.
