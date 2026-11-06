# PureHeat

Android app (Kotlin 2.4, Jetpack Compose) that reads a Homematic IP floor heating system via the
Homematic IP Cloud and shows what the official app doesn't: which circuit is heating, valve
openings, a learned per-room thermal model and a probe run of its predictions.
Code layout, data flow, DB schema, API quirks: `docs/ARCHITECTURE.md` (grep the section you need).

## "mach weiter" / "weiter" / "continue"
Without a new task: read `docs/STATUS.md`, work on the first open item under "Next tasks" right
away. Skip items that need the user (tapping on the phone, a decision, pushing) and say so briefly;
if blocked, take the next one. At the end update STATUS.md and report in a few lines.

## Code review
`/pureheat-review` (`diff` | `full` | path); open findings in `docs/REVIEW.md`. Use it when asked.

## Token use
- Start: read **`docs/STATUS.md`** only (the hand-off between sessions). Other docs only the section
  you need (Grep the heading, read that range): `NEXT_STEPS.md` (open items only),
  `ARCHITECTURE.md`, `FINDINGS.md` (measurements on the real system), `HISTORY.md` (what was
  built/decided per step – only when you need the background).
- Code: Grep / file outline, read line ranges, don't re-read what is in context. Never open
  translation files (`values-*/strings.xml`).
- Use the scripts instead of writing commands anew; keep tool output small (aggregates, `head`).
- `git` never pages (`core.pager cat` is set locally); still prefer `git --no-pager` in new clones.

## Build, device, data
- `scripts/check.sh` – build, unit tests, lint; prints only a short summary (log: `/tmp/ph/build.log`).
  Run it after every change before reporting done (≈ 1–2 min; run in the background).
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (phone: see STATUS; it is
  only connected at times – check `adb devices`).
- `scripts/pull-db.sh` copies the history DB to `/tmp/ph/`; queries in `scripts/sql/` (see its
  README). Raw dumps (`files/last_state.json`, `last_profile.json`) contain IDs/location: grep or jq
  single fields only, never print or commit them. `adb logcat -s PureHeat` (debug builds).

## Docs and hand-off
- Update `docs/STATUS.md` at the end of a task: **replace** the state (< ~60 lines). Measurements
  go into `FINDINGS.md` (with date).
- **`NEXT_STEPS.md` holds open items only.** When you finish an item or step, always (without
  being asked) write what was built/decided as a dated section at the end of `docs/HISTORY.md`
  (or extend that topic's existing section) and delete the item from NEXT_STEPS; a topic section
  without open items disappears. Reference HISTORY sections by name (`HISTORY "Step 3"`).
- **Docs are written in English only** (`X.md`). The German `X.de.md` (README, ReleaseNotes,
  CONTRIBUTING, ARCHITECTURE, FINDINGS) are only brought in line **for a release or when the user
  asks**. `docs/privacy.md` holds both languages (Play Store URL) – keep both there.
- User-visible changes: a line under "Unreleased" in `ReleaseNotes.md`.
- Ask before pushing. Commit messages: short English summary.

## Rules
- The repo is **public**: never commit the SGTIN, auth token, PIN, location or raw API dumps.
  Anonymize IDs in test JSON. No personal names (room labels contain names) in repo or docs.
- The cloud API is unofficial (reverse-engineered, see homematicip-rest-api): lenient parsing,
  every field optional, unknown keys ignored.
- Writes only where the user asked for them: heating profiles (edit, activate, undo), always behind
  a confirmation. No other write calls (set temperature, boost, …) without being asked.
- AI: physics model first; AI only on-device or in simulation mode (`prediction.strategy`), never
  writing to the heating on its own.
- No "Homematic" in the app name or icon (eQ-3 trademark).
- Open source (Apache 2.0, Christian Plagens): every new Kotlin file starts with the licence header
  from `CONTRIBUTING.md`.
- UI text only in string resources: `values/strings.xml` (English) + 11 translations (de, fr, es,
  it, pt, nl, pl, tr, ru, ja, zh-rCN; German informal "du"). No literal UI strings in Kotlin;
  ViewModels/data layer return `UiText`. New keys go into **all 12 files** – collect them per task,
  append before `</resources>` without reading the files. Avoid "% " + letter in texts without
  placeholders (lint reads it as a format).
- Pure-Kotlin logic gets JVM unit tests in `app/src/test/`.
