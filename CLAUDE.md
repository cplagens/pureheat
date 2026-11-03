# PureHeat

Android app (Kotlin, Jetpack Compose) that reads a Homematic IP floor heating system via the
Homematic IP Cloud (Access Point HmIP-HAP) and shows what the official app doesn't:
which heating circuit is currently heating and how far each valve actuator is open.

## Docs
- `docs/ARCHITECTURE.md` — structure, data flow, DB schema, verified API quirks
- `docs/FINDINGS.md` — what we learned on the real system (API, Android, building, model); add new
  findings there with a date
- `docs/NEXT_STEPS.md` — roadmap (room model, forecast, shadow mode, optimal profile, savings) and
  open device checks. **Start here in a new session** and keep it updated when finishing a step.

## Build & Run
- Build: `./gradlew assembleDebug`
- Install on emulator/device: `./gradlew installDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Raw API state (debug builds only): `adb logcat -s PureHeat` (truncated at ~4 KB); full dumps:
  `adb exec-out run-as de.cplagens.pureheat cat files/last_state.json` / `files/last_profile.json`

## Structure
- `app/src/main/java/de/cplagens/pureheat/hmip/HmipClient.kt` — cloud API: lookup, client registration
  (blue button on access point), `getCurrentState`, heating profiles (`getProfile`, `updateProfile`,
  `setActiveProfile`)
- `hmip/HeatingProfile.kt` — profile JSON codec (keeps unknown fields) and pure editing helpers
- `hmip/HeatingState.kt` — parses raw JSON into rooms (HEATING groups) and circuits
  (channels of `FLOOR_TERMINAL_BLOCK*` devices)
- `data/CredentialStore.kt` — SGTIN + auth token in app-private SharedPreferences
- `data/HeatingRepository.kt` — fetch + record history, load/save profiles (backup before save, read-back check)
- `data/HistoryWorker.kt` — self-rescheduling WorkManager chain (15/30/60 min via `PollingPolicy`) +
  hourly watchdog; `data/history/` — Room DB (60 days, incl.
  outside temperature) and analysis (warnings, heat-up rate, heating/cooling phases);
  `data/model/` — per-room thermal model (k, P, dead time) and heat-up/cool-down predictions;
  `data/room/` — room properties from free text (on-device Gemini Nano via ML Kit, keyword fallback),
  used as the model's starting values
- `ui/` — `MainViewModel` (polls every 5 min while visible; each fetch is ~100 KB, uncompressed), `SetupScreen`, `DashboardScreen`,
  `RoomHistoryChart`, `RoomDetailScreen`/`RoomDetailViewModel` + `TimeSeriesChart` (full history),
  `ProfileScreen`/`ProfileViewModel`, `AboutDialog` (support links, language picker), `HelpScreen` (in-app guide),
  `UiText` (translatable texts from ViewModels); `Lang.kt` — per-app language via AppCompat (as in PureRemote)

## Rules
- The repo is **public**: never commit the SGTIN, auth token, PIN or raw API dumps containing them.
  Anonymize IDs before adding JSON samples to tests.
- The Homematic IP Cloud API is unofficial (reverse-engineered, see homematicip-rest-api).
  Keep parsing lenient: every field is optional, unknown keys are ignored.
- Writes only where the user asked for them: heating profiles (edit, activate, undo), always behind a
  confirmation. Don't add other write calls (set temperature, boost, …) without being asked.
- Do not use "Homematic" in the app name or icon (eQ-3 trademark).
- All UI text lives in string resources: `values/strings.xml` (English, default) plus translations in
  `values-<lang>/` (German `values-de` is the user's language). No literal UI strings in Kotlin; ViewModels
  and the data layer return `UiText` (resolved in the UI). New keys must be added to every language;
  a language is only listed in `Lang.kt`/`locales_config.xml` once its file is complete.
- Build after every change before reporting done.
