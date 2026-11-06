# Contributing to PureHeat

**English** · [Deutsch](CONTRIBUTING.de.md)

Thanks for your interest! Bug reports, ideas, translation fixes and pull requests are welcome – in
English or German.

## Reporting bugs and suggesting ideas

- Please open an [issue](https://github.com/cplagens/pureheat/issues): what did you expect, what
  happened, Android version, app version (under "Info").
- **Do not post personal data:** no SGTIN, no auth token, no PIN, no raw cloud data
  (`last_state.json` contains IDs and the location). Please anonymise excerpts.

## Development

Requirements: Android Studio (current), JDK 17, an Android device or emulator with Android 8.0 or
newer.

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests (pure Kotlin logic)
./gradlew lintDebug            # lint, including missing translations
./gradlew installDebug         # install on the connected device
```

To try it out you need a Homematic IP Access Point (HmIP-HAP) with a floor heating actuator.
Structure, data flow and API quirks are described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
measurements on the real system in [docs/FINDINGS.md](docs/FINDINGS.md), the plan in
[docs/NEXT_STEPS.md](docs/NEXT_STEPS.md).

## Contribution rules

- **Writes to the heating** only for heating profiles and only after confirmation by the user (with
  a backup before and a check after). No new write calls without discussing them in an issue first.
- **The cloud API is unofficial:** treat every field as optional, ignore unknown fields or keep them
  when writing back. Check new fields against a real (anonymised) data set first.
- **Privacy:** data leaves the device only to the Homematic IP Cloud and to Open-Meteo (rounded
  location). No analytics, advertising or tracking libraries; AI only on the device.
- **Texts:** no hard-coded texts in Kotlin code, everything in `res/values/strings.xml` (English)
  and in **all** translations (`values-de`, `-fr`, `-es`, `-it`, `-pt`, `-nl`, `-pl`, `-tr`, `-ru`,
  `-ja`, `-zh-rCN`). If you don't speak a language, copy the English text and mention it in the PR.
- **Tests:** changes to pure logic (analysis, model, detection, parser) come with unit tests;
  build, tests and lint must be green.
- **Style:** like the surrounding code (Kotlin, Jetpack Compose, short KDoc comments that explain
  the "why"). New files start with the licence header:

  ```kotlin
  /*
   * PureHeat – Copyright 2026 Christian Plagens
   * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
   * SPDX-License-Identifier: Apache-2.0
   */
  ```
- Do not use "Homematic" in the app name or icon (trademark of eQ-3).
- **Documentation** is maintained in English (`*.md`); the German versions (`*.de.md`) are brought
  in line for each release, so updating the English one is enough.
- Checks before a PR: `scripts/check.sh` (build, unit tests, lint).

## Pull requests

1. Fork, create a branch, keep the change small and focused.
2. Describe what changes and why; for UI changes a screenshot is welcome.
3. Add larger new features to [ReleaseNotes.md](ReleaseNotes.md) under "Unreleased".

## Licence

By contributing you agree that your contribution is published under the
[Apache License 2.0](LICENSE.md) (like the rest of the project, see section 5 of the licence).
