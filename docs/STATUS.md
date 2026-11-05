# PureHeat – Status (entry point for every new session)

**English** · [Deutsch](STATUS.de.md)

As of: 2026-11-05. Keep this file short and **replace** it at the end of every task (don't append).
Details per step: `NEXT_STEPS.md` (grep for the section), measurements: `FINDINGS.md`, open review
findings: `REVIEW.md` (if present). "continue" = first open task below.

## Where we are
- Runs on the user's phone (Galaxy S25 Ultra, adb `R5CY81ZW5EZ`, only connected via USB at times –
  run `adb devices` first; only operate the UI when `mCurrentFocus` is the launcher/PureHeat).
  The state of 2026-11-04 (commit `334b6e7`) is installed but not yet clicked through.
- Done: dashboard (order), history (2 years), detail view, heating profiles (edit, rename), room
  model, window and shower/bath detection, forecast, probe run, help, 12 languages, room
  properties (Gemini Nano), **export/import** (Info → Data), target API 36, open-source files,
  **heating profile from text/dictation** (Nano or keywords, suggestion only), room description
  limits detection (no window → no open-window detection; bathroom without shower/bathtub → no
  shower/bath detection), **demo mode** (setup screen, offline sample flat; README screenshots
  from it in `docs/screenshots/`).
- Play Store guide: `PLAY_STORE_GUIDE.md` (local, in `.gitignore`). Release signing from
  `keystore.properties` built in (unsigned without the file), `docs/privacy.md` (de + en) created.
- Docs are bilingual: English `*.md`, German `*.de.md` (README, ReleaseNotes, CONTRIBUTING,
  ARCHITECTURE, FINDINGS, NEXT_STEPS, STATUS); keep both in sync.

## Next tasks
1. **Check on the device**: heating profile from description (look at the Nano answer in the debug
   log `Nano:`, sharpen the prompt if needed), bathroom without window marker, export share/save as
   file, import of the own file, rename profile (undocumented way – result into FINDINGS), order
   dialog, shower/bath in the bathroom, everything with targetSdk 36.
2. Play Store preparation (checklist in `PLAY_STORE_GUIDE.md`): demo mode done (mention it in the
   review notes, try it on the phone), logo 512 px + feature graphic (next agent task). For the user: create upload keystore +
   `keystore.properties`, enable GitHub Pages for `/docs`, confirm contact in `privacy.md`,
   `.github/FUNDING.yml`: `github:` only once GitHub Sponsors is enabled.
   Decided 2026-11-05: **no donation links in the app** (Play policies, `SupportLinks.kt` empty →
   section hidden); PayPal only in the README (en `README.md`, de `README.de.md`).
3. First review with `/pureheat-review full`, findings in `docs/REVIEW.md`.
4. Note for the user: re-recognise + save the bathroom description (still saved as "ground floor").
5. Look at probe run results; step 6 (comfort times + optimal profile as a suggestion).
6. Solar gain (Open-Meteo `shortwave_radiation` + window direction).

## Small things
- Detail temperature chart: y label "28 °C" lies over the current-temperature line.
- Overview: event labels above the chart are partly crowded/overlapping.

## Open device checks (older)
- Never run on the real system: "Activate profile", "Undo last save", "Apply to other rooms".
  Measure battery/data use (NEXT_STEPS "Step 2").
