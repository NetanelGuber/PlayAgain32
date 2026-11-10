# PlayAgain32 development alpha

Offline ARM32 game import, compatibility diagnosis and experimental preparation for ARM64 Android phones. Requires Android 12+ and an ARM64 device. Standalone APKs only; see the README for supported sizes and OBB handling.

This is not a universal compatibility or stability release. Physical-device gameplay, performance, save integrity and 16 KiB runtime correctness remain unverified. Some games cannot work because of missing native APIs, signature checks, unavailable services or platform restrictions.

Back up game saves using the game's own supported method before changing installations. Uninstalling PlayAgain32 removes its device-local game-signing key and can prevent updates of prepared games. Development APKs use a different signing identity from production builds.

The release includes matching source and SHA-256 checksums. GPL terms and upstream notices apply. Read BUILD-STATUS.md and docs/RELEASING.md in the corresponding source before publishing this draft.
