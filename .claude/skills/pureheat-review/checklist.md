# PureHeat review – checklist

Mark each point: finding / ok / not applicable. With `diff`, read only the sections that fit the
changed files (grep the heading); with `full`, all of them.

## A. Public repo & secrets (repo is public!)
- `git grep` for SGTIN-like ids (`3014F711A0…` with 14 more hex), auth tokens (long hex/base64
  strings), PINs, `latitude`/`longitude` with more than 2 decimals outside tests with fake values,
  e-mail addresses, room labels/personal names in code, tests or docs.
- No raw dumps tracked (`last_state.json`, `last_profile.json`, `*.db`, backups); `.gitignore` ok.
- Test JSON anonymised (fake ids, fake coordinates).
- Logging: `Log.*` with response bodies, tokens or SGTIN only behind `BuildConfig.DEBUG`;
  `debugDump` only in debug builds; no secrets in exception messages shown in the UI.

## B. Security
- Network: HTTPS only, no cleartext (`usesCleartextTraffic`, network security config), no custom
  `TrustManager`/`HostnameVerifier`, timeouts set.
- Manifest: only `MainActivity` exported; services `exported=false`; `<queries>` minimal;
  `allowBackup=false` (or data extraction rules excluding credentials); no unneeded permissions.
- Credentials only in app-private SharedPreferences; nothing on external storage; files written
  with app-private paths only.
- Intents/PendingIntents: explicit, `FLAG_IMMUTABLE`; no untrusted extras used unchecked.
- Release build: not debuggable, R8/keep rules ok (kotlinx.serialization, Room, ML Kit).
- Dependencies: no known-vulnerable or abandoned versions in `gradle/libs.versions.toml`; preview
  (alpha/beta) libraries noted as risk.

## C. Privacy
- Data leaving the device: only Homematic IP cloud, Open-Meteo (location rounded to 2 decimals
  **before** the request), nothing else. On-device AI (Gemini Nano) only via ML Kit/AICore; no
  cloud AI without explicit consent. Dictation uses the system speech service – mentioned in help.
- No analytics, crash reporting or ads SDKs.
- Logout deletes everything: credentials, DB tables, `hmip_server`, `probe`, `room_descriptions`,
  profile backups, debug dumps (check `HeatingRepository.clear()` and `MainViewModel.logout()`
  against every SharedPreferences name and file created anywhere – Grep `getSharedPreferences`,
  `filesDir`, `File(`). `view_settings` may stay (no personal data).
- Help/privacy texts match what the code actually does.

## D. Write safety (CLAUDE.md rule)
- Grep `HmipClient` for endpoints: writes only `updateProfile`, `setActiveProfile` (and the
  registration flow). Each write path: user confirmation dialog → backup → write → read-back.
- No automatic/background writes; AI/probe code never calls write functions.

## E. Correctness / bugs
- Coroutines: no blocking I/O on Main; `runCatching`/`catch (e: Exception)` around suspend calls
  must not swallow `CancellationException` in loops/long-running work; scopes cancelled
  (`onCleared`, `close()` of ML Kit clients).
- Room: every entity/column change bumps `version` with a `Migration` (no destructive fallback);
  queries over the 2-year history are bounded by time.
- Lenient parsing: every API field optional; no `!!` on API data; enums tolerate unknown values.
- Math: division by zero / NaN / Infinity in model and charts (`k`, `P`, spans, `minOf` on empty
  lists), `Long` for milliseconds (`730 * 24 * 60 * 60 * 1000L`), time zones and DST, Double
  rounding at thresholds (`21.4 - 21.2 < 0.2`).
- Compose: no side effects or heavy work in composition, `remember` keys correct, `LazyColumn` keys
  unique, state survives rotation where needed (`rememberSaveable`).
- Lifecycle/background: polling only in `STARTED`; WorkManager unique names and policies; no
  duplicate chains.
- Error handling: user-visible messages via `UiText`, no crash on network/API errors.

## F. UI text & languages
- No literal UI strings in Kotlin (`Text("`, `contentDescription = "`, toasts).
- Lint `MissingTranslation`, `ExtraTranslation`, `StringFormatInvalid` clean; same format
  arguments in all 12 languages; plurals with the forms each language needs.

## G. Battery & data
- No new periodic work or polling faster than `PollingPolicy`; no extra requests per fetch;
  heavy computation only when charging or in the foreground.

## H. Performance
- Large lists/points: charts downsample or stay bounded; DB access off the main thread;
  model fit only on the 60-day window.

## I. Accessibility
- Icons/images with `contentDescription` (or decorative `null`); information not by colour alone
  (legends); touch targets ≥ 48 dp; text scales.

## J. Tests & docs
- Changed pure-Kotlin logic has unit tests; build/test/lint green.
- `docs/STATUS.md`, `ARCHITECTURE.md` reflect the change; no "Homematic" in app name or icon.

Add checks that fit the actual change (new permission, library, endpoint) even if not listed.
