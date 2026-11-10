# 0.1.4 focused security and memory review

Scope: the native-import fixes, their allocation/descriptor ownership, APK publication and public test documentation. This is an internal review, not an independent audit of the entire compatibility runtime. Translation is not a security sandbox; imported games still contain executable code.

## Corrections and ownership

- `environ` uses guest addresses throughout. `getenv` reads that same vector without allocating a new value on each lookup. `setenv` tracks its own allocated strings and releases replaced values that are no longer referenced by the environment; it does not free guest-owned strings or literals. Unchanged values avoid allocation. The initial environment snapshot is allocated once for the runtime lifetime.
- Environment changes publish only after both the value and pointer-vector allocations succeed. Failure frees temporary allocations, returns `ENOMEM` and preserves the previous environment. Allocation sizes are checked before conversion to 32-bit lengths. Environment handler operations hold the shared-state lock. As with C environment APIs, later environment mutations can invalidate earlier returned pointers.
- Epoll scratch memory is bounded to 1024 events per return. Invalid event counts and wrapping output pointers are refused. Guest event layouts are converted explicitly, and returned event data preserves all 64 bits. Host descriptors use the existing close handler.
- CLI and converter preflight engines are explicitly returned, and their engine-preparation worker is stopped and joined before allocator teardown. Both the reference-engine suites and five repeated fast preflight exits completed without the earlier allocation warning.
- Undefined division inputs stop guest execution instead of causing a Rust division panic. `execv` returns an explicit unsupported-operation error and never replaces the Android process. Unknown imports still block installation.

## Observed tests

- 65 focused automated tests, Android lint and debug assembly; native ARM64 components built from source.
- ARM and Thumb device regressions: 10,000 environment replacements after warm-up showed **0 additional bytes of guest heap arena growth** in both measured runs. This measures allocator arena growth, not whole-process RSS or every possible leak.
- Forced guest-heap exhaustion tested value-allocation and environment-vector allocation failure: the operation returned `ENOMEM`, retained the prior environment and freed temporary storage.
- 2,048 epoll create/close cycles, oversized event-count rejection and wrapping-pointer rejection passed in each instruction mode.
- All 18 final translator invocations passed without the earlier shutdown allocation warning. Both combined reference-engine runs returned zero without that warning after explicit cleanup. Preflight reported zero missing imports for the synthetic fixtures.

These bounded tests do not prove leak freedom under every workload. Long-session real-game memory profiling, complete save testing, Android 17/ARM64-only acceptance and an independent security audit remain outstanding. Idle Armies itself has not been tested because its APK was unavailable.

## Publication privacy

Public test reports retain only anonymous Android/API, ABI and page-size conditions. Personal device identifiers were removed from reachable public history and release source archives; superseded CI runs were deleted. Previously downloaded copies and GitHub's cached unreachable commits cannot be erased by a Git push.

The old 0.1.3 binary was withdrawn after finding embedded build-user paths. Native Rust/C builds now remap workspace/home paths. `tools/verify_apk.py` scans APK contents for private home paths, including UTF-16 representations; local checks, CI and release automation reject a failing artifact. Its regression test uses fictitious build paths. No private signing key, personal device log, game APK or game asset is included in release source.

The release APK must pass signature, alignment, privacy and checksum verification before publication. The persistent public release signing identity is retained. See [security reporting](../SECURITY.md) and [release instructions](RELEASING.md).
