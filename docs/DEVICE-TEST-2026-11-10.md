# Physical-device smoke test — 10 November 2026

This is one bounded compatibility result, not a universal or production-stability claim.

## Device and inputs

- an anonymized Android test device, Android 14 / API 34, 4 KiB memory pages, 1080 × 2400 display.
- Reported ABIs: `arm64-v8a,armeabi-v7a,armeabi`. This is **not** an ARM64-only device.
- Manager: PlayAgain32 0.1.2-alpha / version code 3, development-signed. Updated through ADB without clearing app data.
- Angry Birds Seasons 6.6.2 / code 6620, package `com.rovio.angrybirdsseasons`; minimum API 16, target API 22, ARM32 and x86 native libraries.
- Original APK: 104,717,299 bytes; SHA-256 `4d02bef2a363b48d33744c748588a2f8e1d5fcf96b4bd8ea6973a8c9ec84a148`.

No game APK, game assets, phone serial, account identifiers or raw phone screenshots are committed. Local evidence and proprietary test files are kept under ignored `.build/device-test/`.

## Findings and fix

The first import failed because five legitimate texture assets exceeded the old 200:1 ZIP compression-ratio heuristic. The largest observed ratio was about 380:1; the entire APK expands to 297,458,440 bytes across 4,816 entries, within the existing 2 GiB limit.

Version 0.1.2 removes that heuristic and retains absolute APK/expansion/entry bounds, streamed byte accounting, CRC checks, path validation and ELF inspection. Automated regression coverage accepts a highly compressed texture and rejects an oversized declared entry before inflation. The real original APK passes the compiled inspector and imports through Android's document picker. Its imported library entry survives force-stop/relaunch.

Normal installation through PlayAgain32 still fails: Android requires target API 23, while this APK targets API 22. The app displays that specific refusal. The compatible-original route does not automatically repair target SDK metadata.

## Runtime probe and its manual steps

The packaged, source-built converter was executed on the phone against the private imported APK, producing a separate diagnostic copy with target API 23 and ARM64 bridge/stand-in libraries. The converter reported no missing native functions in its preflight. That alone is not a gameplay result.

The diagnostic copy was signed on the host with a development key and installed directly via ADB. No low-target-SDK bypass flag was used. Play Protect still displayed an old-app privacy warning; per-app installation confirmation was completed. Global Play Protect was not disabled. There was no installed original game to remove, and no game or save was uninstalled.

Re-importing that externally converted APK through the manager is currently rejected: its guest ELF32 libraries live beside the bridge under the ARM64 folder, which conflicts with the inspector's ordinary native-folder check. The current compiled inspector reproduced `Native library architecture does not match its APK folder.` Therefore this test **does not establish a complete automatic in-app preparation flow** for this game/device combination.

The diagnostic copy was a local test artifact with a development signing identity. It was not distributed.

## Observed runtime results

| Check | Result |
| --- | --- |
| Installed native ABI | Android reports `primaryCpuAbi=arm64-v8a`; target API 23 |
| Native translation | Live `arm32bridge` logs show guest library loading and graphics calls |
| Launch/rendering | Main menu, tutorial and level 1-1 rendered |
| Touch/gameplay | Bird launches, collisions and score changes observed,  |
| Audio | Audio playback confirmed |
| Background/resume | Home then launcher intent returned to the paused level; resume returned to gameplay |
| Cold restart | New process launched and reached the menu with “Continue playing” |
| Offline continuation | A level loaded without an active network connection; connectivity was restored afterward |
| Crash observations | No game crash appeared in the collected bounded logs during this session |

The short session does not establish sustained performance, peak memory use or absence of leaks.

## Still unverified

Automatic in-app old-target-SDK preparation, external converted-APK re-import, ARM64-only devices, 16 KiB runtime behavior, completed-level save integrity, long sessions, thermal/battery behavior, other episodes/engines, OBB flows and production release signing remain unverified or incomplete. In-progress score did not persist through the cold restart; Continue loaded the current level at score zero, so this test does not certify save preservation. Automated manager rotation was blocked by the phone's shell permission policy; its rotation setting remained unchanged.

The installed manager remains an alpha. Successful manual preparation for one APK does not imply that every older game will install or run.
