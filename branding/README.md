# PlayAgain32 identity

Name: **PlayAgain32**. Tagline: **Old games. New phones.**

The mark combines a mint gamepad, an ivory replay arrow and a play control, communicating old games becoming playable again. It uses the app's forest/mint palette. The title is rendered as real Android text beside the mark for crisp lettering and accessibility.

- `playagain32-mark.png`: final generated artwork on a forest background (1254 × 1254).
- `../android/app/src/main/res/drawable-nodpi/playagain32_mark.png`: 512 × 512 downscaled app asset, used by the header and launcher.
- `../android/app/src/main/res/drawable/ic_launcher_foreground.xml`: 12 dp inset for adaptive-icon safe placement.
- `../android/app/src/main/res/values/ic_launcher_background.xml`: forest `#101815` launcher background.
- `generation-prompt.txt`: initial prompt and exact final rendering prompt.

Generated with the built-in image_gen tool on 10 November 2026; no separate API key or CLI fallback was used. The project asset is a local copy; it does not depend on the image generator's output folder or a network URL.

The final rendering uses an opaque forest backdrop to avoid fringe artifacts around transparent edges at launcher size. Android supplies the adaptive mask; the logo is rendered beside the accessible PlayAgain32 title in the app.

Product text, manager package namespace, project title, build outputs and docs use PlayAgain32. The internal application ID `dev.legacybridge.app` and game-signing alias `legacybridge-games` intentionally remain stable for updates of the earlier alpha. Upstream Game Bridge attribution, native library names and injected helper-class contracts retain their original identities.
