# Compatibility testing

This page records game smoke tests and the Idle Armies follow-up. See [issue #2 evidence and repeatable device validator](ISSUE-2-IDLE-ARMIES.md) and [0.1.4 native-import regressions](ISSUE-1-NATIVE-IMPORTS.md).

The original smoke tests used Android 14/API 34, 4 KiB pages, with both ARM32 and ARM64 support. The Idle Armies follow-up used Android 17/API 37, ARM64-only, with 4 KiB pages.

| Game/version | Preparation and observed result |
|---|---|
| Idle Armies 1.20 | The 0.1.5 ARM64 diagnostic copy reached offline combat, touch navigation, background/resume and cold restart with progress retained. The contributor verified the [idle-freeze fix](ISSUE-2-IDLE-ARMIES.md#idle-freeze-follow-up) on Android 17. Independent 0.1.6 Android 14 testing confirmed startup, touch navigation, resume, cold restart and advancing frame timestamps throughout a three-minute no-input sample. Some left-edge DPS/step text still clips. |
| Angry Birds Seasons 6.6.2 | Previously prepared ARM64/API-23 diagnostic copy: gameplay, touch, confirmed sound, background/resume, cold restart and offline level loading passed. See [original report](DEVICE-TEST-2026-11-10.md). |
| 2048 1.1.3 | Java-only, target 22. Imported, prepared to target 23, signed on the phone and installed through PlayAgain32. Grid rendered and swipe input changed tiles. |
| GLTron 1.1.2 | Java/OpenGL, effective target 8. Imported, prepared to target 23 and installed through PlayAgain32. Tap-to-start and 3D gameplay observed. Some original instruction/end-screen text clips; unresolved. |
| Frozen Bubble 4.4 | Official archive APK downloaded; installation and gameplay were not completed. |
| Freedoom 0.4.3 | Official ARM32 APK downloaded; installation and gameplay were not completed. |
| SuperTux 0.7.0 ARM32 | Official APK downloaded and release digest verified; installation and gameplay were not completed. |

Per-app installation warnings were confirmed for the legacy tests. Global Play Protect remained enabled. Downloaded games, personal screenshots, signing keys and device logs are excluded from Git.

## Screen fitting

The optional Fill screen flag changes Android's `resizeableActivity` manifest metadata. It remains off by default. In Angry Birds, allowing a wider window exposed menu/HUD clipping. A diagnostic display-measurement patch was explored but was not accepted as a verified fix. The original, previously tested fitting diagnostic APK was restored without clearing game data. Leave Fill screen off for this game. Wider windows do not guarantee that a game's internal UI adapts correctly.

## Download provenance

| APK | Official source | SHA-256 |
|---|---|---|
| 2048 1.1.3 | [Developer release](https://github.com/tpcstld/2048/releases/tag/v1.1.3) | `60d369aa977389d37fce5cc110f18480f3352660501c4f8e539d16d2b31ee6d5` |
| GLTron 1.1.2 | [F-Droid archive APK](https://f-droid.org/archive/com.glTron_4.apk) | `5301908839635adb4bf597d6b4f0af06f7846e6e3c6af1bf9650bda7b019976a` |
| Frozen Bubble 4.4 | [F-Droid archive APK](https://f-droid.org/archive/org.jfedor.frozenbubble_54.apk) | `bf291a12e88c84e559bd4f0faf99163a6a5b55da995df69ef10f888c6bba77e4` |
| Freedoom 0.4.3 | [Developer release](https://github.com/mkrupczak3/Freedoom-for-Android/releases/tag/v0.4.3) | `43c94079d78ed2dffb90e521ffcc74364706fb8e334ba55f15b43168450625c6` |
| SuperTux 0.7.0 ARM32 | [Developer release](https://github.com/SuperTux/supertux/releases/tag/v0.7.0) | `63d9cc609ea9f029579b699493133fc02280b9808b91249c8ecb310e621ff149` |

No malware samples were downloaded. Completed-level save integrity, sustained thermal/performance testing, ARM64-only phones and 16 KiB-page devices remain unverified.
