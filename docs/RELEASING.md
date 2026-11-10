# Release process

## Before a public APK

Keep the app marked alpha until physical-device acceptance covers installation, input/audio/rendering, 30 minutes of play, pause/resume, cold restart and save integrity on representative ARM64-only 4 KiB and 16 KiB devices. Record game APK hashes and exported diagnostics. [BUILD-STATUS.md](../BUILD-STATUS.md) states the current evidence and gaps.

Use a persistent private manager-signing keystore backed up outside the repository. Do not use the debug certificate for a public release. A different certificate cannot update the existing development APK in place. Keep the same production certificate for future updates and increment `versionCode` and `versionName` in `android/app/build.gradle.kts`.

## GitHub automation

[Build and test](../.github/workflows/ci.yml) runs on main pushes, pull requests and manual dispatch. It rebuilds native libraries, runs all focused checks and uploads a short-lived **development APK**, plus test/lint reports. CI signing identities are disposable and are not suitable for stable user updates. Actions are pinned to verified upstream commits; normal CI has read-only repository permissions and no signing secrets.

For a signed release, create a GitHub environment named `release`, restrict it to `main`, and configure required reviewers where available. Store these environment secrets:

| Secret | Value |
| --- | --- |
| `PLAYAGAIN32_KEYSTORE_BASE64` | Base64 of your persistent JKS/PKCS12 keystore |
| `PLAYAGAIN32_KEY_PASSWORD` | Store and key password; this build uses the same password for both |
| `PLAYAGAIN32_KEY_ALIAS` | Alias of the private signing entry, normally `playagain32` |

Run [Draft signed alpha release](../.github/workflows/release.yml) manually on `main`. It reruns tests, requires signing secrets, verifies the APK signature and 16 KiB ZIP alignment, creates a new source tag, and creates a **draft prerelease** with the matching source ZIP and SHA256SUMS.txt. Its temporary keystore is deleted even when signing fails. It does not publish the draft automatically. An existing tag or version fails rather than replacing assets. If draft creation fails after tag creation, inspect that tag and recover the draft manually against the same commit; do not move a distributed version's tag.

Before publishing the draft, inspect the workflow result, certificate fingerprint, source archive/notices and hardware test results. Keep an immutable tag and the exact corresponding source for each distributed APK. Do not upload games or proprietary assets.

For a local signed build, first build native files, then set `PLAYAGAIN32_KEYSTORE` to the absolute keystore path, `PLAYAGAIN32_KEY_PASSWORD` and optionally `PLAYAGAIN32_KEY_ALIAS`. Run `android/gradlew.bat -p android assembleRelease`. Missing signing credentials fail release packaging; debug signing is never substituted.

## License

The repository retains GPL-2.0-or-later source and GPL components, including the patched Unicorn runtime. This combined project cannot be relabeled MIT without the relevant copyright permissions or replacement of GPL components. Preserve [LICENSE](../LICENSE) and bundled third-party notices, and distribute corresponding source alongside binaries. Dependencies retain their own licenses.
