# Contributing

PlayAgain32 is an experimental Android compatibility runtime. Keep changes small and preserve upstream copyright and license notices. Contributions must be compatible with the repository's GPL terms.

1. Follow [the Windows build guide](tools/BUILD-WINDOWS.md) and build the native runtime.
2. Run `./tools/test.ps1` from the repository root. It runs publication checks, native host tests, Android JVM tests, lint and debug assembly. `-SkipAndroid` runs the SDK-independent checks only.
3. Explain the problem, changed behavior and validation in your pull request. Add regression coverage for parser, import, storage and installation changes.

For compatibility reports, include phone model, Android version, page size when known, game package/version, APK SHA-256, app version and exported diagnostics. Describe the exact failing step and whether input, audio, rendering, restart and saves work. Remove personal paths or identifiers from reports. Do not upload commercial APKs, OBBs, saves, private keys or account tokens.

Tests passing do not establish game compatibility. Keep unsupported functions and unverified devices visible to users; never claim every old game works.
