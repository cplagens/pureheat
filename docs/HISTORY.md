# PureHeat – History

Log of what was built and decided, per step, oldest first. Open items are **not** here but in
[NEXT_STEPS.md](NEXT_STEPS.md). Don't read this file at session start – grep the section you need.
When a step in NEXT_STEPS is done, its log moves to the end of this file (see CLAUDE.md).

## Step 1 – history and detail view (2026-11-01)

- The outdoor temperature is stored with every recording (`weather_sample`, DB version 2).
- Detail view per room (`RoomDetailScreen`): tap the 24 h chart or "History ›"; range 24 h / 3 /
  7 / 14 / 60 days; current, target, outdoor; valve per channel; values on tap/swipe; detected
  heat-up and cool-down phases (`HistoryAnalysis.phases`) with rate.
- Debug log per HTTP request with compression and size (for measuring battery/data use).
- Retention raised from 14 to 60 days (the model needs different weather).

## Checked on the device (2026-11-01, evening)

- The detail view runs on the phone (24 h, tapping, phase list).
- `home.weather` contains `temperature`, `minTemperature`, `maxTemperature` (daily forecast!),
  `humidity`, `windSpeed`, `windDirection`, `weatherCondition`, `weatherDayTime`, `vaporAmount`.
- `home.location` has `city`, `latitude`, `longitude` – **as strings**. Never put the values into
  the repo/logs.
- **No compression:** the cloud ignores `Accept-Encoding: gzip`. `getCurrentState` transfers
  ~100 KB uncompressed (log: `encoding=none wire=99606`). That means: background every 15 min
  ≈ 9.5 MB/day; open app with 60 s polling ≈ 6 MB/hour. → Step 2 is important.
- Gaps in the history on the afternoon of 2026-11-01 come from `am force-stop` during development
  (this pauses WorkManager jobs until the next app start) – not a bug in the app.

## Step 2 – battery and data use (2026-11-01)

- **Foreground:** fetch every **5 min** (as the user wanted), "Refresh" immediately.
- **Adaptive background:** `HistoryWorker` is a self-rescheduling chain of `OneTimeWorkRequest`s
  (`history-chain`, `APPEND_OR_REPLACE` from inside the worker). `PollingPolicy` (pure Kotlin,
  tested) chooses the interval from the last two recordings: **15 min** if a valve changed by
  ≥ 10 % or a target by ≥ 0.25 K; otherwise **30 min**, at night (22–05) **60 min**.
- `HistoryWatchdogWorker` (hourly, without network) restarts the chain if it breaks. The old fixed
  15 min job (`history`) is removed at start.
- Constraints: network available **and battery not low**.
- Checked on the device: forced run (`adb shell cmd jobscheduler run -f -n
  androidx.work.systemjobscheduler de.cplagens.pureheat <jobId>`) → fetch → "next run in 30 min"
  in the log → exactly one follow-up job scheduled.
- Expectation: about 40–60 fetches/day instead of 96 → about 4–6 MB/day instead of 9.5 MB.
- No permanent WebSocket connection on the phone (that would be something for the Raspberry Pi);
  according to homematicip-rest-api there are no slimmer queries – fetching less often is the lever.

## Step 3 – room model (2026-11-01)

First-order thermal model per room (T = room, Tₐ = outdoor, times in hours), code in
`data/model/RoomModel.kt` (pure Kotlin):

- Valve closed: `dT/dt = −k·(T − Tₐ)` → per phase `k = −rate / (T̄ − T̄ₐ)`. Only phases with
  `T̄ − T̄ₐ ≥ 3 K` and falling temperature (valve closed + rising temperature, e.g. from a towel
  radiator, says nothing about the loss).
- Valve open: `dT/dt = P − k·(T − Tₐ)` → `P = rate + k·(T̄ − T̄ₐ)`, evaluated only during **free
  heat-up**: from the minimum after the dead time (`ThermalPhase.responseStart`) until the
  temperature is 0.3 K below the target (`heatUpEnd`); after that the valve only holds.
- **Dead time** `d` = phase start to minimum (the room keeps cooling while the screed heats up).
  Everything as a median; spread of k as (Q3−Q1)/median.
- Predictions (`RoomModel.heatUp/coolDown` → `Prediction.Hours | Unreachable | Unknown`):
  - Heat-up: `t = d + (1/k)·ln((P − k(T₀−Tₐ)) / (P − k(T₁−Tₐ)))`, unreachable if
    `P ≤ k(T₁−Tₐ)`.
  - Cool-down: `t = (1/k)·ln((T₀−Tₐ) / (T₁−Tₐ))`.
- **Trustworthy** from 3 usable phases each (`RoomModel.MIN_PHASES`), otherwise the app shows
  "Room model still learning (valve open x/3, valve closed y/3)".
- **Eco/comfort temperature** come from the recorded targets of the last 7 days (10th/90th
  percentile, `SetPointLevels`) – no additional API calls; short manual changes drop out. Outdoor
  temperature = last recorded value.
- Calculated in `HeatingRepository.roomInsights()` (60 days, `Dispatchers.Default`), in the
  dashboard at most every 30 min; the detail view shows k, P, dead time, number of phases, spread
  and the eco/comfort temperature.
- A unit test simulates a room with known k, P, dead time and checks that the fit finds them again
  (`RoomModelTest`).

## Analysis of the first 28 h of history (2026-11-02)

- **Android throttles the background jobs** via app standby buckets: during the day
  ACTIVE → WORKING_SET (10:08) → FREQUENT (13:46) → RARE (18:08). Result: 60 instead of 30 min
  during the day, then **9 h without recording** (10:34–19:35). Only 36 recordings in 28 h.
  Fix in the app: hint card (`ui/BatteryHint.kt`) → App info → Battery → "Unrestricted"
  (`isIgnoringBatteryOptimizations`).
- **The lookup service answered 418 once** (too many lookups: every process start did one).
  The REST URL is now cached in SharedPreferences `hmip_server`, new lookup only after an error.
- **Physics plausible** (room 2 after freeing the valve pin): at night valve closed,
  23.2 → 21.0 °C in 8 h at ~7 °C outdoors (k ≈ 0.02/h); in the morning valve open from 03:00, the
  room keeps cooling until ~04:30 (dead time ~1.5 h), then 20.6 → 22.8 °C in 4 h (~0.55 K/h,
  P ≈ 0.8 K/h); target 23 °C reached around 10:30.
- Other rooms: bedrooms cool at night with k ≈ 0.017–0.026/h. Bathroom and WC stay at 25–27 °C
  above target all day (towel radiators with target 26–27 °C heat the room), kitchen/living room
  stays at 23–24 °C without heating → these rooms provide hardly any "valve open" phases; the
  model will show "still learning" there for a long time.
- Overview chart: current/target now as continuous lines across recording gaps. Since 2026-11-03
  (user's wish) also in the detail view (`ChartSeries.breakOnGaps`); only the valve areas still
  show gaps > 1 h.

## Multiple languages and help (2026-11-02)

- All texts in resources, English (default) + German, the system language is followed, language
  choice in the Info dialog and (Android 13+) in the system "App languages", help screen.
- **2026-11-03:** translations as in PureRemote for **fr, es, it, pt, nl, pl, tr, ru, ja, zh-CN**
  (plurals: ru/pl one/few/many/other, fr/es/it/pt additionally many, ja/zh only other),
  registered in `Lang.kt` and `locales_config.xml`. New keys always go into all 12 files.
  Machine-translated – feedback from native speakers welcome.

## AI model for heating behaviour and daily plan (assessment 2026-11-02)

User's question: would an AI model make sense to learn the heating behaviour and the plan for the
next day better? Assessment: **not as a replacement for now, later as an addition.**

- Data situation: a few weeks, 8 rooms, 15–60 min data points, outdoor temperature only 6–8 °C so
  far. A physical model with 3 parameters (k, P, dead time) learns robustly from this; a neural
  network would need months including winter and would not be explainable.
- Explainability is a precondition for trust (probe run): "start 04:30 because k=0.02 and 6 °C
  outdoors" can be checked, a black-box prediction cannot.
- Sensible intermediate stage ("grey box"): extend the physical model with learned corrections –
  e.g. time-of-day/weekday effects (cooking, sun, occupants), wind, solar radiation (weather
  forecast), neighbouring rooms; via regression on the residuals (prediction errors) of the
  physical model. Runs on the phone without cloud.
- Typical usage patterns (when is who at home, when are windows open) can be derived statistically
  from the history and offered as a suggestion for comfort times.
- A large language model (LLM) is unsuitable for control, but could later phrase explanations
  ("Why did it heat earlier today?") – only with explicit consent, since data would then leave the
  device (or on-device, e.g. Gemini Nano).
- Decision: steps 3–6 as planned with the physical model; in the probe run (step 5) store the
  residuals so that a grey-box correction can be learned later and compared with the pure model.
- **Added by the user (2026-11-02):** an AI may later also run **only in simulation mode**: it
  makes its own decisions in parallel (start times/profile) that are not written, and the app
  compares how it would have done against the physical model and reality (hit rate, heating time).
  For this the probe run stores predictions of several "strategies" side by side (column
  `strategy`, e.g. `physics`, later `greybox`/`ai`).

## Open-window detection (2026-11-02, reworked 2026-11-06)

User info: in rooms 1–3 the windows are open at night. There are no window contacts.

- `data/history/WindowDetection.kt` (pure Kotlin, `WindowDetectorTest`), first version: **opened**
  when, with the valve closed, the temperature between two data points falls by ≥ 0.3 K at
  ≥ 0.6 K/h (or ≥ 0.2 K together with a ≥ 4 %RH drop in humidity); **closed** when it rises again
  by ≥ 0.2 K without heating, otherwise end at heating start, gap > 2.5 h or after 16 h.
- Humidity is stored per room since DB version 3 (`room_sample.humidity`).
- `PollingPolicy`: a temperature change ≥ 0.3 K counts as "active" → 15 min fetch, so that the
  drop on opening becomes visible.
- Model: open-window times are excluded from the phases (`withoutWindowTimes`).
- Display: light-blue band in overview and detail view, line "Window probably open …", list in the
  detail view, hint in the model card.
- **First check with real data (night 11-01 to 11-02, only hourly data points):**
  room 2 detected 20:51–01:11 (−1.6 K; end because of heating start 03:00 – the window was probably
  open longer), room 3 detected 23:10–07:31 (−1.9 K), **room 1 not detected** (even ~0.3 K/h, no
  drop visible).
- **Fewer false alarms (2026-11-06):** with denser sampling and sensors that only report steps of
  ~0.3–0.4 K, a slow cooling after hours of the same value looked like a sudden drop (FINDINGS).
  Now: the rate is taken since the value *first appeared* (not since the previous sample); a drop
  ≥ 0.5 K between two samples (`STRONG_DROP`) still counts; closing = rise ≥ 0.2 K above the lowest
  point (also over several small steps); differences rounded to 0.01 K. Tests incl. the real
  evening of room 2.
- **Absolute humidity (2026-11-06):** all rooms have a humidity sensor. %rH rises when the air
  cools, so it hides an open window; the detector now uses g/m³ (Magnus formula,
  `WindowDetector.absoluteHumidity`). With humidity an opening needs a fall of ≥ 0.3 g/m³
  (`MIN_ABS_HUMIDITY_DROP`, ~1.5 × the 1 %rH rounding noise); ≥ 0.6 g/m³ (`ABS_HUMIDITY_DROP`)
  already counts at ≥ 0.2 K. Rooms without humidity keep the temperature-only rules.
  Demo (`DemoHome.simulate`): the humidity now falls one step after opening, together with the
  temperature (before, the two fell on different samples and `DemoHomeTest` found only 2 windows).
  Analysis queries: `scripts/sql/window-hits.sql` (same rules).
- **Outside humidity (2026-11-06):** the cloud already sends `home.weather.vaporAmount` = outside
  water vapour in g/m³ (checked: 2.07 °C, 73 % → 4.08 g/m³, as the Magnus formula gives) – so no
  Open-Meteo request is needed and the value is recorded with every snapshot, aligned with the
  rooms. `weather_sample.outsideAbsHumidity` (DB v6, `MIGRATION_5_6`; fallback from temperature +
  `humidity` if `vaporAmount` is missing); CSV export/import with a 4th column (old exports still
  import). `WindowDetector.detect(samples, outsideHumidity)`: the humidity rules only apply while
  the room is ≥ 1.5 g/m³ (`DRY_OUTSIDE`) moister than outside or the outside value is unknown;
  otherwise (mild, wet days) only the temperature rules. `WindowDetector.outsideHumidity(weather)`
  = nearest value within 2 h. Used in dashboard, detail view, room model and probe run; demo with
  75 % outside humidity.

## Step 4 – weather forecast (2026-11-02)

- `home.location` (strings) is parsed and immediately rounded to 2 decimal places
  (`GeoPoint.rounded`); only these values go to Open-Meteo.
- `data/ForecastClient.kt`: `…/v1/forecast?latitude=..&longitude=..&hourly=temperature_2m&forecast_days=3&timeformat=unixtime&timezone=UTC`
  (~5 KB). No night job needed: the fetch is attached to the normal state fetch, at most every 6 h
  (`HeatingRepository.refreshForecastIfDue`), errors are ignored.
- Table `forecast_sample(timestamp, temperature, fetchedAt)`, DB migration 3 → 4; replaced on
  every fetch, past hours are kept for 6 h (for comparing forecast ↔ measurement).
- Use: overview on top "Outdoor 6.7 °C · next 24 h 4–10 °C"; the room model uses the forecast mean
  of the next 12 h (text "forecast X °C outdoors"), otherwise the measured value.
- Checked on the device: the forecast arrives (display 4–10 °C on the evening of 2026-11-02).

## Step 5 – probe run with hit rate (2026-11-02)

- `data/model/RoomSimulator.kt`: forward simulation (5 min steps) like a thermostat – valve opens
  at ≤ target − 0.2 K, closes at target; dead time only after the valve has been closed for ≥ 2 h
  (screed still warm). Also the basis for step 6 (optimal profile) and 7 (savings).
- `data/probe/ProbeRun.kt`: prediction of all target rises (≥ 0.4 K) in the next 24 h from the
  active profile, room model and hourly forecast; evaluation after 12 h: reached = current ≥
  target − 0.2 K; "on time" = ±30 min; not counted with an open window (±2 h) or a manual target.
  As long as a room does not know k/P: starting values k 0.02/h, P 0.8 K/h, dead time 1.5 h
  (`ModelSource.PRIOR`, shown as "starting values").
- Table `prediction` (DB v5) with column `strategy` (now `physics`; later grey box/AI in
  simulation mode side by side).
- Daily run `HeatingRepository.maybeRunProbe`: at most every 20 h; in the background only while
  charging (or if > 36 h ago), in the open app immediately. Loads the active profile per room
  (~1–2 KB per room). No own night job needed – attached to the fetch chain.
- Detail view: card "Probe run" with hit rate (14 days), release status, planned and evaluated
  predictions; help section "Probe run".
- On the device: first run 2026-11-02 20:43 – 6 profiles loaded, 7 predictions.
- Original plan (kept for reference): night job with predictions per room from the active
  profile (base → period "target reached at …", period → base "temperature at …"), evaluation on
  the following day, display "off by X min, Y % on time (14 days)", switch "Activate optimal
  control" only above a threshold – the open part of this is in NEXT_STEPS.

## Room properties by free text/speech (2026-11-03)

User's wish: enter properties per room as text or by speaking, turn them into structured
properties with AI that influence heating; show keywords as suggestions.

- Screen `ui/RoomPropertiesScreen.kt` (detail view → card "Room properties" → "Describe room ›"):
  free text, "🎤 Dictate" (system speech input via `RecognizerIntent`, no own microphone
  permission), tappable example keywords (`props_examples`), "Recognise properties", then all
  fields as chips/number fields to review and correct, save. Shows which starting values (k, P,
  dead time) result from them.
- Recognition (`data/room/RoomDescriber.kt`): **Gemini Nano on the device** via the ML Kit Prompt
  API (`com.google.mlkit:genai-prompt`, JSON answer, read leniently in `RoomProperties.fromJson`),
  complemented by the **keyword fallback** `KeywordExtractor` (German + English, pure Kotlin,
  tested). Without Nano only keywords; if Nano is downloadable, the app triggers the system
  download. No cloud service, no API key (the repo is public).
  Merging (since the device test 2026-11-03): **keyword hits win**, Nano fills the rest; yes/no
  from Nano only when `true` (see FINDINGS). Nano runs on the user's phone.
  For this Kotlin 2.2.10 → **2.4.21** (the ML Kit library is built with 2.4).
- Storage: SharedPreferences `room_descriptions` (`RoomDescriptionStore`), not in the history DB
  (a setting, not a measurement; survives the 60-day limit), deleted on sign-out.
- Fields (`RoomProperties`) and why they matter:
  | Field | Effect |
  |---|---|
  | Area, room height | air volume/storage; with the number of windows → glass share; P ∝ 2.5 m / height |
  | Number of windows, compass directions | heat loss (k); **south/west windows = solar gain** (later with radiation forecast) |
  | Glazing (single/double/triple) | k ×1.3 / ×1 / ×0.85 |
  | Exterior walls (0–4) | k ×0.7 … ×1.3 |
  | Position (basement/ground floor/upper floor/under the roof), above unheated basement | roof k ×1.2, above basement k ×1.1 |
  | Floor covering | **strongest lever for P and dead time** with floor heating: tiles P ×1.15 / dead time −0.25 h, parquet ×0.85 / +0.25 h, carpet ×0.7 / +0.5 h |
  | Roller shutters | later: lower night losses when closed |
  | Other heat sources (towel radiator, stove, electronics, cooking) | explains "warm without valve"; later grey-box correction |
  | Use (bedroom, bathroom, …) | later: suggestion for comfort times (step 6) |
  | Window tilted at night | k ×1.1; expectation for open-window detection |
- Use now: only as **starting values** (`RoomPriors.model`, in `ProbeRun.modelFor`) as long as the
  room model has not learned k/P itself – the learned model always wins. The factors are rough
  physics heuristics, not measurements; check with the probe run whether starting values with
  properties hit better than without.

## Rename profiles, showering/bathing, room order, open source (2026-11-03)

- **Rename:** "Rename" in the heating profile screen (dialog = confirmation). No documented
  endpoint; `HeatingRepository.renameProfile` sends `updateProfile` with unchanged days and `name`
  in the profile object, backs up before and checks afterwards via `getCurrentState`. If the cloud
  ignores the name: error message, nothing broken.
- **Showering/bathing:** `RoomProperties.shower/bathtub` (keywords, Nano, yes/no fields);
  `data/history/BathingDetection.kt` (humidity ≥ 5 %RH at ≥ 10 %RH/h, temperature not falling,
  end on return to ≤ start value + 3 or after 3 h). Display (marker "Showering/bathing", list, view
  section) only for rooms with bathroom/shower/bathtub (`hasBathing`); all events are excluded from
  model and phases.
- **Order:** `data/RoomOrder.kt` – default alphabetical, groups whose name contains another room
  name as a word directly after it; own order via dialog (↑/↓) in `ViewSettings.roomOrder`, new
  rooms at the end.
- **Open source:** licence header (SPDX Apache-2.0, Christian Plagens) in all Kotlin files,
  `LICENSE` → `LICENSE.md`, `NOTICE`, `CONTRIBUTING.md`, `ReleaseNotes.md`, linked from the README;
  help section "Open source" with repo link, licence line in the Info dialog.

## Play Store preparation: release signing, privacy policy (2026-11-04)

- `app/build.gradle.kts` reads `keystore.properties` (project root, in `.gitignore`: `storeFile`,
  `storePassword`, `keyAlias`, `keyPassword`). If the file is missing, the release build stays
  unsigned as before (`app-release-unsigned.apk`). Checked with test properties (debug keystore):
  a signed `app-release.apk` is produced; the test file was deleted afterwards. The user has to
  create the upload keystore themselves (`keytool` hangs in the IDE shell, run it in an own
  terminal).
- `docs/privacy.md` (de + en) after the draft in `PLAY_STORE_GUIDE.md` 2.6, statements checked
  against the code (sign-out deletes credentials, history, descriptions, probe run, backups).
  Contact: GitHub issues instead of e-mail (no address in the public repo). Linked in `README.md`.
  Publishing: GitHub Pages (branch `main`, folder `/docs`) – done by the user.
- **Reviewer instructions (2026-11-08):** `PLAY_STORE_GUIDE.md` 3.2 "App access" now walks the Play
  reviewers through the demo mode (setup screen → "Try demo mode" below "Connect", what to try,
  leave via "Log out" at the end of the overview; no credentials). Checklist item ticked.
- **Store graphics (2026-11-08):** `docs/store/icon.svg` → `icon-512.png` (512 × 512, 32-bit, full-bleed
  square of the logo – Play masks the corners), `feature-graphic-{en,de}.svg` → `.jpg` (1024 × 500,
  no alpha: symbol + "PureHeat" + tagline from the short description on the teal→navy gradient).
  `scripts/render-store-graphics.sh` renders them with headless Chrome; ffmpeg adds the alpha
  channel Chrome drops for opaque images.
- **Buy me a coffee (2026-11-08, user's decision):** Play Billing Library 9.1.0
  (`com.android.billingclient:billing`). `data/support/CoffeeBilling`: one consumable INAPP product
  `coffee` (price in the Play Console: 1.99 EUR / 1.99 USD), `enablePendingPurchases(…
  enableOneTimeProducts())`, `enableAutoServiceReconnection()`, PBL 8+ `QueryProductDetailsResult`;
  purchases are consumed right away (nothing unlocked, no server check), open/pending ones on the
  next connect. `SupportViewModel` + `SupportSection` in the Info dialog replace the empty link
  list (`SupportLinks` now only has the source link); the section is hidden when Play can't offer
  the product (sideload, debug, not set up). Texts `coffee_buy/pending/thanks` in 12 languages;
  `docs/privacy.md` (de + en) mentions Google Play payments.
- **Versioning and release pipeline (2026-11-08):** `appVersion` in `gradle.properties`;
  `versionCode = major·10000 + minor·100 + patch`. `.github/workflows/release.yml`: tag `vX.Y.Z`
  → tests + lint → signed `bundleRelease` (upload key from environment secrets via
  `PUREHEAT_KEYSTORE*` variables) → `r0adkll/upload-google-play` to `vars.PLAY_TRACK` (default
  internal) with `distribution/whatsnew/`. GitHub environment `play-release` with required reviewer
  and tag rule; Play App Signing makes the upload key resettable; RSA 4096 instead of EC for the
  upload key (Play-documented, EC acceptance uncertain). Details and setup: `docs/RELEASE.md`.

## Room configuration limits detection, heating profile from text (2026-11-04)

- `RoomProperties.hasWindows` (only an explicit "0 windows" = no): no open-window detection in the
  overview, detail view, room model (`RoomInsight.forAllRooms(properties)`) and probe run
  evaluation. `hasBathing`: shower/bathtub yes → yes; both explicitly no → no; otherwise bathroom
  use. The overview recalculates immediately when the description changes (combined flow).
  Excluding humidity jumps from the model stays for all rooms (cooking etc.).
- **Heating profile from text:** card "Create from description" in the heating profile
  (type/dictate). `data/profile/ProfileDescriber` → Gemini Nano (JSON `rules[days, base,
  periods]`), otherwise `ProfileFromText.fromKeywords` (German/English: weekdays/weekend/daily, day
  ranges "Mon-Fri", short day names only capitalised + before a time, time ranges incl. am/pm,
  "otherwise/at night" = base temperature of the days in the sentence). `ProfileFromText.apply`
  snaps to 15 min/0.5 °C, room limits, cuts overlaps, max. 6 periods; days not mentioned stay.
  The result only lands in the editor – saving goes through the normal confirmed path. Tested in
  `ProfileFromTextTest`.
- Shared Nano access: `data/ai/OnDeviceAi` (status, download, JSON answer), used by
  `RoomDescriber` and `ProfileDescriber`.

## Export/import, target API 36, Play Store guide (2026-11-04)

- **Export:** Info → Data → "Export and share" (ZIP in `cache/exports`, `FileProvider`, share
  sheet) or "Save as file" (SAF `CreateDocument`, also Google Drive). Content: `settings.json`
  (room descriptions, hidden sections, room order, language – **no credentials**),
  `room_names.csv`, `rooms.csv`, `circuits.csv`, `weather.csv`, `predictions.csv`, `README.txt`.
  CSV: comma, decimal point, epoch ms + UTC ISO (`data/export/Csv.kt`, tested). Written in weekly
  slices (2 years do not fit into memory at once).
- **Import:** ZIP or only `settings.json`; measurements are added (REPLACE per key), settings
  replaced, language set last. Confirmation dialog before.
- `compileSdk`/`targetSdk` 36 (required by Play since 2026-08-31); edge-to-edge and back handling
  were already fine.
- **Play Store guide:** `PLAY_STORE_GUIDE.md` in the project folder (in `.gitignore`, not in the
  repo): account, signing, store texts, questionnaires with answers, privacy policy, 12-tester
  test, production application, GitHub Sponsors. Decided 2026-11-05: donation links removed from
  the app entirely; demo mode for reviewers built (see below).

## Demo mode and README screenshots (2026-11-05)

- Setup screen: "Try demo mode" → `MainViewModel.startDemo` → `HeatingRepository.seedDemo` (3 weeks
  of history every 15 min, evaluated probe results of the last week, forecast, three room
  descriptions), then credentials `DemoHome.SGTIN` / `"demo"`. The repository sends all cloud calls
  for that SGTIN to `DemoCloud` (no lookup, no network, no Open-Meteo – invented forecast instead).
  Profile edits, rename and activate work in memory (lost on process death). Logout clears it
  like real data. Banner "Demo mode – invented sample data" on the dashboard, no battery hint.
- Simulation (`DemoHome.simulate`): same first-order physics and thermostat as `RoomSimulator`, plus
  bedroom window wide 22:45–23:00 then tilted until 06:15 on two of three nights, showers at
  06:40/08:15 and every second evening (+18 %RH, +1.2 K/h, fading over 150 min). No sensor noise:
  with 0.1 K rounding it produced false open-window detections. `DemoHomeTest`: model confident
  for all rooms with k within ±60 %, windows only in the bedroom, showers only in the bathroom.
- Screenshots in `docs/screenshots/` (emulator, English, time zone set to Australia/Brisbane via
  `adb shell service call alarm 3 s16 …` to get morning heating; no root needed). Retake after UI
  changes the same way.

## Checked on the device (2026-11-05)

App runs with targetSdk 36, heating profile from description with Nano, order dialog,
shower/bath label on the dashboard (after the fix in `ChartMarkers.kt`), Gemini Nano available on
the phone.

## Checked on the device (2026-11-06)

- Detail view: outdoor line ✓, 7 and 60 days ✓ (chart issues → NEXT_STEPS "Charts"), dark mode
  readable ✓ (system night mode switched on and back via `adb shell cmd uimode night yes|no`).
- Language: Info → English rebuilds the activity (~1 s black), English UI with 12 h clock and
  decimal point, help in English ✓; back to "System language" ✓. Channel labels from the cloud
  ("Kanal 4") stay as the user named them.
- Open-window detection after the rework: room 2 evening of 11-06 shows one event from 21:13
  (23.5 → 22.0 °C, absolute humidity 9.9 → 7.4 g/m³ by 22:32); the 20:11 sensor step is gone.

## Chart fixes (2026-11-06)

- `TimeAxis`: in the app language and 12/24 h setting (was fixed to German): time of day up to
  26 h, weekday + hour up to 10 days ("Fr, 22 Uhr"), dates beyond ("7. Okt.").
- `timeTicks` (java.time, local time, DST-safe, `TimeTicksTest`): 6 h up to 2 days, midnights up
  to 10 days, Mondays beyond (60 days: 8 lines instead of 60).
- `TimeSeriesChart`: the plot leaves room above the max and below the min for the y labels
  ("28 °C" no longer on the line); valve areas fill down to the plot baseline.
- `MarkerStrip`: a second-lane label may not cover the tick of a first-lane label and vice versa;
  the label that doesn't fit is left out. Checked on the phone (overview, detail 24 h / 7 / 60 days).

## Docs and tooling (2026-11-06)

- STATUS and NEXT_STEPS English only; all docs English only, German `*.de.md` only for a release
  or on request. Done steps move from NEXT_STEPS to this file.
- `scripts/check.sh` (build/test/lint summary), `scripts/pull-db.sh`, `scripts/sql/`; review skill
  split into `SKILL.md` + `checklist.md`; `git config core.pager cat`.
