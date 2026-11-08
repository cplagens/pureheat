# PureHeat – Architecture

**English** · [Deutsch](ARCHITECTURE.de.md)

As of: 2026-11-01. Describes how the app is built, which data it gets from where and which
assumptions about the (unofficial) Homematic IP Cloud API apply.
What is planned next is in [NEXT_STEPS.md](NEXT_STEPS.md).

## Overview

```
 ┌───────────────────────── Android app (de.cplagens.pureheat) ─────────────────────────┐
 │                                                                                      │
 │  UI (Compose)                    ViewModels                Data                      │
 │  ─────────────                   ──────────                ────                      │
 │  SetupScreen ──────────────┐                                                         │
 │  DashboardScreen ──────────┼──► MainViewModel ─────┐                                 │
 │   └ RoomHistoryChart       │    (5 min polling,     │                                 │
 │  RoomDetailScreen ─────────┼──► RoomDetailViewModel├──► HeatingRepository ──► HmipClient ──► HmIP Cloud
 │   └ TimeSeriesChart        │                       │     │  (fetch, record,        (OkHttp)
 │  ProfileScreen ────────────┼──► ProfileViewModel ──┘     │   load/save            
 │  AboutDialog               │                             │   profiles)            
 │                            │                             ├──► Room DB history.db   
 │  HistoryWorker (WorkManager, 15/30/60 min adaptive) ─────┘     (60 days)           
 │                                                                                      │
 │  Pure Kotlin (no Android dependencies, unit-tested):                                 │
 │  HeatingStateParser · HeatingProfileCodec · ProfileEditing · HistoryAnalysis         │
 └──────────────────────────────────────────────────────────────────────────────────────┘
```

The actual heating control always runs on the Access Point (HmIP-HAP) and the devices. The app
reads states, records them and only writes heating profiles – and only after explicit
confirmation.

## Packages and files

Paths relative to `app/src/main/java/de/cplagens/pureheat/`.

| File | Purpose |
|---|---|
| `PureHeatApp.kt` | `Application`: holds `CredentialStore` and `HeatingRepository` (simple manual DI), schedules the `HistoryWorker` |
| `MainActivity.kt` | Single-activity app; navigation via state (`profileRoomId`, `historyRoomId`, `propertiesRoomId`) + `BackHandler`; starts the 5 min polling only in state `STARTED` |
| `SupportLinks.kt` | Source code link (no payment links – Play policies) |
| `data/support/CoffeeBilling.kt` | Voluntary tip via Google Play Billing: consumable product `coffee`, consumed right away; `CoffeeState` (price or hidden, pending, thanked) |
| **hmip/** | |
| `HmipClient.kt` | Cloud API: lookup, client registration (blue button), `getCurrentState`, `getProfile`, `updateProfile` (also renaming: `name` in the profile), `setActiveProfile` |
| `ErrorMessages.kt` | Exceptions → user error messages (`toUserMessage()`), HTTP error hints |
| `HeatingState.kt` | `HeatingStateParser`: raw JSON → `HeatingState` (rooms, heating circuits, profile references, outdoor temperature) |
| `HeatingProfile.kt` | `HeatingProfileCodec` (read/write profile JSON, unknown fields are kept), `ProfileEditing` (pure editing logic) |
| **data/** | |
| `CredentialStore.kt` | SGTIN + auth token in app-private SharedPreferences |
| `HeatingRepository.kt` | Only access to the cloud: fetch + record state, load/save profiles (backup before, check after), debug dumps |
| `ForecastClient.kt` | Open-Meteo fetch (hourly outdoor temperature, 3 days) with rounded location |
| `HistoryWorker.kt` | Self-rescheduling fetch chain (`history-chain`) + hourly `HistoryWatchdogWorker` |
| `PollingPolicy.kt` | Pure logic: next fetch in 15 min (valve/target changing), 30 min (stable) or 60 min (night) |
| `history/HistoryDatabase.kt` | Room DB `history.db` (version 6): `room_sample`, `circuit_sample`, `weather_sample`, `forecast_sample`, `prediction` |
| `history/HistoryAnalysis.kt` | Pure analysis: warning "valve open but no heat", heat-up rate, phases "valve open/closed" (with dead-time minimum and end of free heat-up) |
| `history/BathingDetection.kt` | Pure heuristic: showering/bathing from a quick rise in humidity; exclusion for the model |
| `RoomOrder.kt` | Pure logic: order of the room cards (thermostats after their room, saved order) |
| `history/WindowDetection.kt` | Pure heuristic: window open from temperature drop/rise (+ humidity) with the valve closed; exclusion of these times for the model |
| `model/RoomModel.kt` | Pure thermal model per room (k, P, dead time), fitting from phases, prediction of heat-up/cool-down time; `SetPointLevels` (eco/comfort temperature from target values) |
| `model/RoomSimulator.kt` | Pure forward simulation of a room (thermostat behaviour, dead time, forecast) |
| `probe/ProbeRun.kt` | Pure probe run: prediction of heat-up times from the active profile, evaluation against the measurement, hit rate |
| `model/RoomInsight.kt` | Model + eco/comfort + outdoor temperature per room, with ready-made predictions |
| `room/RoomProperties.kt` | Room properties (area, windows + direction, glazing, exterior walls, position, floor covering, …), lenient JSON reading, `RoomPriors` (starting values k/P/dead time from the properties) |
| `room/KeywordExtractor.kt` | Pure keyword fallback (German/English): free text → `RoomProperties` |
| `ai/OnDeviceAi.kt` | Gemini Nano via ML Kit: availability, system download, JSON answer (shared) |
| `profile/ProfileFromText.kt` / `ProfileDescriber.kt` | Text → heating profile suggestion (Nano or keywords), validation against grid/limits |
| `export/Csv.kt` / `DataTransfer.kt` | Export (ZIP with CSV + settings.json) and import |
| `demo/DemoHome.kt` | Pure: invented flat (6 rooms, profiles, weather), deterministic simulation incl. aired bedroom and showers |
| `demo/DemoCloud.kt` | Offline `HeatingCloud` for demo mode (raw JSON like the real cloud, profile edits in memory) + seed history (3 weeks, probe results, descriptions) |
| `hmip/HeatingCloud.kt` | Interface of the cloud calls after login; `HmipClient` or `DemoCloud` (chosen by `DemoHome.SGTIN` in the repository) |
| `room/RoomDescriber.kt` | Gemini Nano on the device (ML Kit Prompt API) + keywords; triggers the system download if needed |
| `room/RoomDescriptionStore.kt` | Text + properties per room in SharedPreferences `room_descriptions` |
| **ui/** | |
| `MainViewModel.kt` | Setup/registration, polling every 5 min while visible, 24 h history + warnings for the dashboard |
| `DashboardScreen.kt` | Room cards: current/target, valve per channel, 24 h chart, links "History ›" / "Heating profile ›" |
| `RoomHistoryChart.kt` | Compact 24 h chart (temperature on top, valve below) |
| `RoomDetailScreen.kt` / `RoomDetailViewModel.kt` | Detail view: range 24 h–60 days, current/target/outdoor, valve per channel, values on tap, room properties, room model, probe run, detected phases |
| `RoomPropertiesScreen.kt` / `RoomPropertiesViewModel.kt` | Describe a room (type/dictate, example keywords), recognise properties, review/correct, save |
| `ModelTexts.kt` | Texts for model predictions (duration, "unreachable", "still learning") |
| `TimeSeriesChart.kt` | Reusable time series chart with a shared time cursor |
| `ProfileScreen.kt` / `ProfileViewModel.kt` | View/edit/save/activate/undo weekly profiles |
| `AboutDialog.kt` | Version, description, language, data, coffee tip (`SupportSection` + `SupportViewModel`), logo preview |
| `SetupScreen.kt` | SGTIN/PIN input, hint about the blue button |

Resources: adaptive icon in `res/mipmap-anydpi-v26/`, foreground/background/logo in
`res/drawable/` (same design as PureRemote: gradient teal → navy, white symbol). For the README the
logo also exists as `docs/assets/logo.svg`.

## Data flow

### Reading / recording
1. `HeatingRepository.fetch()` – called by the UI polling (5 min, only while visible) or by the
   `HistoryWorker` (background, 15/30/60 min depending on `PollingPolicy`, only with network and
   when the battery is not low).
2. If needed, lookup of the REST server (cached, repeated after an error).
3. `getCurrentState` (~95 KB JSON) → `HeatingStateParser.parse()`.
4. `record()`: at most one record every 4 min per room, per channel and one weather value;
   deletes everything older than 2 years (room model: only the last 60 days).
5. The UI observes the Room tables as a `Flow`.

### Writing profiles
`ProfileViewModel.save()` → `HeatingRepository.saveProfile()`:
1. load the current profile fresh from the cloud and store it as a backup in
   `files/profile_backups/<groupId>_<PROFILE_n>.json`,
2. send `updateProfile`,
3. load the profile again and compare it with what was sent; difference → error message.
"Undo" plays the backup back the same way.

## Data model (Room, `history.db`, version 6)

| Table | Key | Fields |
|---|---|---|
| `room_sample` | roomId + timestamp | actualTemperature, setPointTemperature, valvePosition (max of the channels or group value), humidity (since v3) |
| `circuit_sample` | deviceId + channelIndex + timestamp | valvePosition |
| `weather_sample` | timestamp | outsideTemperature (`home.weather.temperature`), outsideAbsHumidity g/m³ (`home.weather.vaporAmount`, since v6) |
| `prediction` | roomId + strategy + eventTime | Probe run: target, start temperature, predicted/actual reach time, model source (fit/prior), result (since v5) |
| `forecast_sample` | timestamp | temperature (Open-Meteo, hourly), fetchedAt; replaced on every fetch (since v4) |

Migration 1 → 2 creates `weather_sample`, 2 → 3 adds `room_sample.humidity`, 3 → 4 creates
`forecast_sample`, 4 → 5 `prediction`, 5 → 6 adds `weather_sample.outsideAbsHumidity`; existing data is kept. The REST server address is cached in SharedPreferences
`hmip_server` (new lookup only after an error – the lookup service answers 418 to too many
requests).
Measured size: about 710 rows or 120 KB/day → about 90 MB after 2 years.
Settings outside the DB: `room_descriptions` (room properties), `view_settings` (hidden sections
of the detail view), `probe`, `hmip_server`.

## Homematic IP Cloud API (unofficial)

Basis: [homematicip-rest-api](https://github.com/hahn-th/homematicip-rest-api). All requests are
`POST` with JSON, header `VERSION: 12`, `CLIENTAUTH` (SHA-512 of SGTIN + fixed salt),
`ACCESSPOINT-ID`, after registration also `AUTHTOKEN`.

| Endpoint | Body | Remarks (checked on the real system) |
|---|---|---|
| `lookup.homematic.com:48335/getHost` | `{clientCharacteristics, id}` | returns `urlREST` (e.g. `https://srv00xx.a.homematic.com:6969`); answers 200 for any SGTIN |
| `hmip/auth/connectionRequest` … `confirmAuthToken` | – | registration, blue button |
| `hmip/home/getCurrentState` | `{clientCharacteristics, id}` | **not** only `clientCharacteristics` – otherwise 400 |
| `hmip/group/heating/getProfile` | `{groupId, profileIndex, profileId}` | response: `{id, groupId, homeId, type, profileDays}`; the base temperature is called **`baseValue`** (not `baseTemperature`) |
| `hmip/group/heating/updateProfile` | `{groupId, profileIndex, profile}` | **`profileIndex` at the top level is required**, otherwise 400 INVALID_REQUEST |
| `hmip/group/heating/setActiveProfile` | `{groupId, profileIndex}` | not yet tested on the real system |

Important fields in `getCurrentState`:
- `groups[*]` with `type == "HEATING"` = room: `actualTemperature`, `setPointTemperature`,
  `humidity`, `valvePosition` (only with radiator thermostats), `activeProfile`,
  `profiles{PROFILE_n: {profileId, name, enabled, visible}}`, `minTemperature`/`maxTemperature`.
- `devices[*]` with `type` `FLOOR_TERMINAL_BLOCK_*` = floor heating actuator; channels
  `FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL` with `valvePosition` (0–1), `valveState`, `groups`.
  Unused channels: no group, no position (e.g. `ADJUSTMENT_TOO_SMALL`) → hidden.
- `home.weather.temperature` = outdoor temperature; `home.location` (according to the library
  `latitude`/`longitude`, not yet checked on the real system).

Principle: every field optional, ignore unknown fields or keep them when writing back.

## Battery and data

- Foreground polling every 5 min, only while the app is visible (`repeatOnLifecycle(STARTED)`).
- Background: chain of one-time WorkManager jobs; `PollingPolicy` chooses 15/30/60 min.
  Constraints: `NetworkType.CONNECTED`, `setRequiresBatteryNotLow(true)`. An hourly watchdog job
  (without network) restarts the chain if it breaks. Android batches the jobs and stretches them
  in Doze. `am force-stop` pauses all jobs until the next app start.
- Look at the jobs on the device: `adb shell dumpsys jobscheduler | grep -A3 de.cplagens.pureheat`;
  force one: `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler de.cplagens.pureheat <jobId>`.
- Debug builds log compression and size per request (`adb logcat -s PureHeat`) to measure data
  use.
- Measured: the cloud does **not** compress (ignores `Accept-Encoding: gzip`);
  `getCurrentState` ≈ 100 KB per fetch. Fetching less often is the main lever (see HISTORY
  "Step 2", open items in NEXT_STEPS "Battery and data use").

## Languages and texts

- All texts in string resources: `values/strings.xml` = English (default and fallback for
  unsupported languages), plus, as in PureRemote, `values-de`, `-fr`, `-es`, `-it`, `-pt`, `-nl`,
  `-pl`, `-tr`, `-ru`, `-ja`, `-zh-rCN`. New keys go into all files; `lintDebug` checks that.
  Caution: in texts without placeholders no "% " before a/e/h/d/s … – lint reads it as a format.
- The app follows the system language; own choice in the Info dialog (`LanguagePicker`) or, from
  Android 13, in the system settings "App languages" (`res/xml/locales_config.xml`).
  Implementation as in PureRemote: `AppCompatDelegate.setApplicationLocales`, which is why
  `MainActivity` is an `AppCompatActivity` (theme `Theme.AppCompat.DayNight.NoActionBar`), and
  `AppLocalesMetadataHolderService` also stores the choice on Android ≤ 12.
- ViewModels and the data layer return no finished texts but `UiText` (resource + arguments),
  resolved only in the UI – so the language is right even after a switch.
  Errors from the cloud: `HmipException.uiText`, `Throwable.toUiText()`.
- Numbers, times and weekdays are formatted per language via resources/`DateFormat`
  (comma/point, 12/24 h).
- The help screen (`HelpScreen`) explains the displays; reachable via "Help" in the overview and
  on the setup screen.

## Security and privacy

- The repo is public: never commit SGTIN, token or raw dumps (`files/last_state.json`,
  `files/last_profile.json`; backups stay app-private on the device).
- Token only in app-private SharedPreferences; `allowBackup=false`.
- Writes only for heating profiles and only after confirmation (see `CLAUDE.md`).
- Room descriptions are only evaluated on the device (Gemini Nano via AICore or keywords);
  dictation uses the system speech input (its provider may recognise online).

## Tests

`./gradlew testDebugUnitTest` – JVM unit tests for the pure Kotlin parts:
`HeatingStateParserTest`, `HeatingProfileTest`, `HistoryAnalysisTest`, `ThermalPhaseTest`,
`PollingPolicyTest`, `RoomModelTest` (simulated room with known parameters), `WindowDetectorTest`,
`ForecastTest`, `ProbeRunTest`, `RoomPropertiesTest` (keywords, JSON, starting values).
JSON examples in tests are anonymised.

## Debugging

- Full raw state: `adb exec-out run-as de.cplagens.pureheat cat files/last_state.json`
- Last loaded profile: `… cat files/last_profile.json`
- Profile backups: `… ls files/profile_backups/`
- Background job: `adb shell dumpsys jobscheduler | grep -A3 de.cplagens.pureheat`
