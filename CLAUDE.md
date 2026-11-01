# PureHeat

Android app (Kotlin, Jetpack Compose) that reads a Homematic IP floor heating system via the
Homematic IP Cloud (Access Point HmIP-HAP) and shows what the official app doesn't:
which heating circuit is currently heating and how far each valve actuator is open.

## Build & Run
- Build: `./gradlew assembleDebug`
- Install on emulator/device: `./gradlew installDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Raw API state (debug builds only): `adb logcat -s PureHeat`

## Structure
- `app/src/main/java/de/cplagens/pureheat/hmip/HmipClient.kt` — cloud API: lookup, client registration
  (blue button on access point), `getCurrentState`
- `hmip/HeatingState.kt` — parses raw JSON into rooms (HEATING groups) and circuits
  (channels of `FLOOR_TERMINAL_BLOCK*` devices)
- `data/CredentialStore.kt` — SGTIN + auth token in app-private SharedPreferences
- `ui/` — `MainViewModel` (polls every 60 s), `SetupScreen`, `DashboardScreen`

## Rules
- The repo is **public**: never commit the SGTIN, auth token, PIN or raw API dumps containing them.
  Anonymize IDs before adding JSON samples to tests.
- The Homematic IP Cloud API is unofficial (reverse-engineered, see homematicip-rest-api).
  Keep parsing lenient: every field is optional, unknown keys are ignored.
- Read-only first: don't add write calls (setting temperatures etc.) without being asked.
- Do not use "Homematic" in the app name or icon (eQ-3 trademark).
- UI text is German.
- Build after every change before reporting done.
