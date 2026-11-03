# PureHeat

Android app (Kotlin 2.4, Jetpack Compose) that reads a Homematic IP floor heating system via the
Homematic IP Cloud (Access Point HmIP-HAP) and shows what the official app doesn't: which circuit is
heating, how far each valve is open, a learned per-room thermal model and a probe run of its predictions.

## Start of a session (keep token use low)
1. Read **`docs/STATUS.md`** only (short: current state, next tasks, open device checks). It is the
   hand-off between sessions – don't reconstruct context from old transcripts.
2. Read further docs **only the section you need** (Grep for the heading, then read that line range):
   `docs/NEXT_STEPS.md` (roadmap + detailed log per step), `docs/ARCHITECTURE.md` (files, data flow,
   DB schema, API quirks), `docs/FINDINGS.md` (measurements on the real system).
3. Find code with Grep / file outline, read line ranges, not whole files; don't re-read what is
   already in context. Don't open translation files (`values-*/strings.xml`) to "check" them.

## End of a task (hand-off)
- Update `docs/STATUS.md`: **replace** the state, don't append history (keep it under ~60 lines).
  Details of what was built/decided go into the matching section of `NEXT_STEPS.md`, measurements
  into `FINDINGS.md` (with date). The next session must be able to continue from STATUS.md alone.
- Ask before pushing. Commit messages: short English summary.

## Build & check (token-saving)
- Gradle is slow in the IDE shell: run in the background and write to a log, then grep it:
  `./gradlew assembleDebug testDebugUnitTest lintDebug --continue --console=plain > /tmp/ph/build.log 2>&1`
  → `grep -E "^e:|FAILED|BUILD" /tmp/ph/build.log`; lint errors:
  `grep ": Error:" app/build/reports/lint-results-debug.txt`; test totals from
  `app/build/test-results/testDebugUnitTest/*.xml`. Never paste full Gradle output.
- Install: `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk` (phone: see STATUS).
- Device data: copy `databases/history.db*` via `adb exec-out run-as de.cplagens.pureheat cat …`
  into `/tmp/ph/` and query with `sqlite3` (aggregates, not full tables). Raw dumps
  (`files/last_state.json`, `files/last_profile.json`) contain IDs/location: grep single fields only,
  never print them in full, never commit them. `adb logcat -s PureHeat` (debug builds).
- Build after every change before reporting done.

## Structure (details: ARCHITECTURE.md)
Package `app/src/main/java/de/cplagens/pureheat/`:
- `hmip/` — cloud API (`HmipClient`), state parser, profile codec + pure editing helpers, errors
- `data/` — `HeatingRepository` (fetch, record, forecast, probe run, profiles with backup/read-back),
  `HistoryWorker` + `PollingPolicy` (15/30/60 min), `ForecastClient` (Open-Meteo, rounded location),
  `ViewSettings` (visible detail sections); `history/` Room DB (**2 years**, model uses 60 days),
  analysis, window detection; `model/` thermal model (k, P, dead time) + simulator; `probe/` probe run;
  `room/` room properties from free text (on-device Gemini Nano via ML Kit, keyword fallback)
- `ui/` — screens + ViewModels (dashboard, room detail, room properties, profiles, help, setup), charts,
  `UiText`; `Lang.kt` per-app language
- Pure-Kotlin logic (analysis, model, probe, keywords) has JVM unit tests in `app/src/test/`.

## Rules
- The repo is **public**: never commit the SGTIN, auth token, PIN, location or raw API dumps.
  Anonymize IDs in test JSON. No personal names (room labels contain names) in repo or docs.
- The Homematic IP Cloud API is unofficial (reverse-engineered, see homematicip-rest-api).
  Keep parsing lenient: every field is optional, unknown keys are ignored.
- Writes only where the user asked for them: heating profiles (edit, activate, undo), always behind a
  confirmation. Don't add other write calls (set temperature, boost, …) without being asked.
- AI: physics model first; AI only on-device or in simulation mode (predictions side by side,
  `prediction.strategy`), never writing to the heating on its own.
- Do not use "Homematic" in the app name or icon (eQ-3 trademark).
- All UI text lives in string resources: `values/strings.xml` (English, default) + 11 translations
  (de, fr, es, it, pt, nl, pl, tr, ru, ja, zh-rCN; German is the user's language, informal "du").
  No literal UI strings in Kotlin; ViewModels/data layer return `UiText`. New keys go into **all 12
  files** – append them before `</resources>` without reading the files. In texts without
  placeholders avoid "% " followed by a letter (lint reads it as a format).
