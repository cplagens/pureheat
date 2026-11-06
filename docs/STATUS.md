# PureHeat – Status (entry point for every new session)

As of: 2026-11-06. Keep this file short and **replace** it at the end of every task (don't append).
Open items per topic: `NEXT_STEPS.md` (grep for the section), what was built: `HISTORY.md`,
measurements: `FINDINGS.md`, open review findings: `REVIEW.md` (if present). "continue" = first open task below.

## Where we are
- Runs on the user's phone (Galaxy S25 Ultra, adb `R5CY81ZW5EZ`, only connected via USB at times –
  run `adb devices` first; only operate the UI when `mCurrentFocus` is the launcher/PureHeat).
  Debug build of 2026-11-06 (window detection with fewer false alarms, not committed yet) installed.
- Done: dashboard (order), history (2 years), detail view, heating profiles (edit, rename), room
  model, window and shower/bath detection, forecast, probe run, help, 12 languages, room
  properties (Gemini Nano), **export/import** (Info → Data), target API 36, open-source files,
  **heating profile from text/dictation** (Nano or keywords, suggestion only), room description
  limits detection (no window → no open-window detection; bathroom without shower/bathtub → no
  shower/bath detection), **demo mode** (setup screen, offline sample flat; README screenshots
  from it in `docs/screenshots/`).
- Play Store guide: `PLAY_STORE_GUIDE.md` (local, in `.gitignore`). Release signing from
  `keystore.properties` built in (unsigned without the file), `docs/privacy.md` (de + en) created.
- Docs: English only since 2026-11-06; the German `*.de.md` are only updated for a release or on
  request (they lag behind now: ReleaseNotes, FINDINGS, CONTRIBUTING). Tooling: `scripts/check.sh`,
  `scripts/pull-db.sh`, `scripts/sql/` (see CLAUDE.md). Done steps were moved from NEXT_STEPS to
  `HISTORY.md` (2026-11-06); NEXT_STEPS now only lists open items.

## Next tasks
0. Outside humidity is recorded since 2026-11-06 (cloud `vaporAmount`, DB v6) and used by the
   window detection (HISTORY "Open-window detection"). Next there: NEXT_STEPS "Open-window
   detection" items 2–5 (model-based criterion, slow drops, end at heating start, hint).
1. **Check on the device** (2026-11-06 done: outdoor line, 7/60 days, dark mode, language switch,
   window detection after the rework – HISTORY): still open export share/save as file, import of the
   own file (the user taps through), rename profile (cloud write – only with the user's OK).
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
- Overview: with many setpoint changes some setpoint labels are left out (detected events now
  always get a place first).
- Order dialog: the ↑/↓ arrows are small.

## Open device checks (older)
- Never run on the real system: "Activate profile", "Undo last save", "Apply to other rooms".
  Measure battery/data use (NEXT_STEPS "Battery and data use").
