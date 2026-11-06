# PureHeat – Next steps

Roadmap and detailed log per step. **The entry point is [STATUS.md](STATUS.md)** – only read this
document section by section (grep for the heading).

## Goal

PureHeat should become a **learning optimal control** for the floor heating:
you only state *when* you want *which* temperature (comfort times). The app learns per room how
quickly it heats up and cools down (depending on the outdoor temperature), takes the weather
forecast into account and calculates the heating profile from that. At first it only runs **as a
probe run** (predictions + hit rate), and only once there is enough trust does the user switch on
automatic control per room. In addition the app should show how much heating time/cost this saves.

## Decisions made

- **Control stays on the Access Point.** The app rewrites the profile at most **once a day** (at
  night). If the phone fails, the last written profile keeps running.
- **Writing only with consent:** at first only suggestions (confirmation per save as today);
  automatic mode only after explicitly switching it on per room, with a log and undo.
- **Weather forecast from Open-Meteo** (free, no account). Location from `home.location`,
  **rounded to 2 decimal places (~1 km)** – approved like this by the user. Fetch at most every 6 h.
- **Saving battery** has priority (the phone is practically always on, but should not suffer):
  heavy computing only while charging, adaptive fetches, no permanent connection.
- **Maybe a Raspberry Pi later:** write model, forecast and profile calculation in **pure Kotlin
  without Android dependencies** (like `HistoryAnalysis`, `ProfileEditing`) so they can later move
  into a JVM module `:core`. For now everything stays on the phone.
- UI texts in resources (English default, German for the user + 10 more languages).
- **Retention 2 years** (since 2026-11-03, before 60 days): training data for a later AI/grey box
  in simulation mode over whole heating seasons. The room model still only uses the last 60 days
  (`MODEL_WINDOW_MS`); predictions (`prediction`) are also kept for 2 years.
- **Configurable detail view** (2026-11-03): "View" hides sections (temperature, valves,
  humidity, room properties, model, probe run, window, phases); applies to all rooms, the hidden
  ones are stored (`ViewSettings`), default: everything visible.
- **Never put measures with different units on one shared axis** (e.g. °C and % RH): separate
  panels with a shared time axis (also for humidity, 2026-11-02).

## Done (step 1, 2026-11-01)

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

## Analysis of the first 28 h of history (2026-11-02)

- **Android throttles the background jobs** via app standby buckets: during the day
  ACTIVE → WORKING_SET (10:08) → FREQUENT (13:46) → RARE (18:08). Result: 60 instead of 30 min
  during the day, then **9 h without recording** (10:34–19:35). Only 36 recordings in 28 h.
  Fix in the app: hint card (`ui/BatteryHint.kt`) → App info → Battery → "Unrestricted"
  (`isIgnoringBatteryOptimizations`). Measure again afterwards.
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
- **Improve the prediction (suggestion, still open):** "eco → comfort" (e.g. 15 → 24 °C) is
  unrealistic because the rooms only drop to ~21 °C at night. Better: heat-up from the *typical
  temperature at the end of the setback* (median of the current temperature at the target rise)
  and cool-down as "after X h of setback still about Y °C"
  (`T(t) = Tₐ + (T₀ − Tₐ)·e^(−k t)`, X = typical setback duration).
- Overview chart: current/target now as continuous lines across recording gaps. Since 2026-11-03
  (user's wish) also in the detail view (`ChartSeries.breakOnGaps`); only the valve areas still
  show gaps > 1 h.

## Multiple languages and help (2026-11-02)

- Implemented: all texts in resources, English (default) + German, the system language is
  followed, language choice in the Info dialog and (Android 13+) in the system "App languages",
  help screen.
- **Done 2026-11-03:** translations as in PureRemote for **fr, es, it, pt, nl, pl, tr, ru, ja,
  zh-CN** (plurals: ru/pl one/few/many/other, fr/es/it/pt additionally many, ja/zh only other),
  registered in `Lang.kt` and `locales_config.xml`. New keys always go into all 12 files.
  Machine-translated – feedback from native speakers welcome.
- Check on the device: language switch in the Info dialog (the activity is rebuilt), English UI,
  help screen.

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
  For this, build the probe run (step 5) so that several "strategies" can store predictions side
  by side (column `strategy`, e.g. `physics`, later `greybox`/`ai`).

## Room properties by free text/speech (implemented 2026-11-03)

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
- Open / ideas:
  1. Check on the device: is Gemini Nano available on the phone (`checkStatus`)? Dictation?
  2. **Solar gain**: also fetch Open-Meteo `shortwave_radiation`; window area × direction ×
     radiation as an additional term (grey box), especially for south/west rooms on sunny days.
  3. Further useful details: position of the flat (neighbouring flats heated?), neighbouring rooms
     (unheated/cool), occupancy (how many people when), flow temperature/heating curve of the
     central heating, pipe spacing, screed thickness – only add once the model can use them.
  4. With many rooms enter shared values (building, glazing) once for all.

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
- Open: demo for the Play reviewers is now there (mention it in the review notes).

## Export/import, target API 36, Play Store guide (2026-11-04)

- **Export:** Info → Data → "Export and share" (ZIP in `cache/exports`, `FileProvider`, share
  sheet) or "Save as file" (SAF `CreateDocument`, also Google Drive). Content: `settings.json`
  (room descriptions, hidden sections, room order, language – **no credentials**),
  `room_names.csv`, `rooms.csv`, `circuits.csv`, `weather.csv`, `predictions.csv`, `README.txt`.
  CSV: comma, decimal point, epoch ms + UTC ISO (`data/export/Csv.kt`, tested). Written in weekly
  slices (2 years do not fit into memory at once).
- **Import:** ZIP or only `settings.json`; measurements are added (REPLACE per key), settings
  replaced, language set last. Confirmation dialog before.
- Idea for later: automatic weekly backup into a chosen folder (SAF tree URI).
- `compileSdk`/`targetSdk` 36 (required by Play since 2026-08-31); edge-to-edge and back handling
  were already fine. Check everything again on the device.
- **Play Store guide:** `PLAY_STORE_GUIDE.md` in the project folder (in `.gitignore`, not in the
  repo): account, signing, store texts, questionnaires with answers, privacy policy, 12-tester
  test, production application, GitHub Sponsors. Open decisions from it: demo mode for reviewers,
  hide donation links in the Play build (decided 2026-11-05: removed from the app entirely),
  release signing.

## Rename profiles, showering/bathing, room order, open source (2026-11-03)

- **Rename:** "Rename" in the heating profile screen (dialog = confirmation). No documented
  endpoint; `HeatingRepository.renameProfile` sends `updateProfile` with unchanged days and `name`
  in the profile object, backs up before and checks afterwards via `getCurrentState`. If the cloud
  ignores the name: error message, nothing broken. Fallback idea: local display name only.
- **Showering/bathing:** `RoomProperties.shower/bathtub` (keywords, Nano, yes/no fields);
  `data/history/BathingDetection.kt` (humidity ≥ 5 %RH at ≥ 10 %RH/h, temperature not falling,
  end on return to ≤ start value + 3 or after 3 h). Display (marker "Showering/bathing", list, view
  section) only for rooms with bathroom/shower/bathtub (`hasBathing`); all events are excluded from
  model and phases. Ideas: shower times as a comfort time suggestion for the bathroom, tell bathtub
  from shower by duration/temperature rise.
- **Order:** `data/RoomOrder.kt` – default alphabetical, groups whose name contains another room
  name as a word directly after it; own order via dialog (↑/↓) in `ViewSettings.roomOrder`, new
  rooms at the end.
- **Open source:** licence header (SPDX Apache-2.0, Christian Plagens) in all Kotlin files,
  `LICENSE` → `LICENSE.md`, `NOTICE`, `CONTRIBUTING.md`, `ReleaseNotes.md`, linked from the README;
  help section "Open source" with repo link, licence line in the Info dialog.

## Open-window detection (2026-11-02)

User info: in rooms 1–3 the windows are open at night. There are no window contacts.

- `data/history/WindowDetection.kt` (pure Kotlin, `WindowDetectorTest`): **opened** when, with the
  valve closed, the temperature between two data points falls by ≥ 0.3 K at ≥ 0.6 K/h (or ≥ 0.2 K
  together with a ≥ 4 %RH drop in humidity); **closed** when it rises again by ≥ 0.2 K without
  heating, otherwise end at heating start, gap > 2.5 h or after 16 h.
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
- Open / ideas:
  1. Check again after lifting the battery restriction (denser data points), especially room 1.
  2. Second criterion once k with closed windows is known: cooling much faster than the model
     expects (e.g. > 3×) → window open, even without a drop.
  3. Calibrate the humidity criterion with real data (drop on opening? rise on closing?).
  4. Detect the end better when the heating starts in the morning while the window is still open
     (valve open, temperature keeps falling → window still open).
  5. Hint to the user when the heating profile heats with an open window (room 2: heating start
     03:00, window probably still open → energy lost).
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
- **Next: outside humidity from Open-Meteo** (`hourly=relative_humidity_2m`, or `dew_point_2m`):
  store it with the forecast/weather (new column + DB migration), compute the outside absolute
  humidity and use it in `WindowDetector`: only require a humidity fall if the outside air is
  clearly drier than inside (e.g. ≥ 1.5 g/m³); on mild, wet days fall back to the temperature rules.
  Later also: expected fall when airing (inside vs. outside) as a plausibility check, and a hint
  "airing helps against humidity now / not now" for the bathroom.

## Open: check on the device

1. Outdoor line in the detail view after a few recordings (first `weather_sample` rows).
2. Detail view in dark mode and with 7/14/60 days once there is enough data.
3. Never run on the real system: "Activate profile" (`setActiveProfile`), "Undo last save",
   "Apply to other rooms".
4. **Rename profile** (cloud write via the undocumented way, only with the user's OK): e.g. rename
   "Profile 4" in the bathroom and back; check that the name shows up in the state and in the
   official app, result into FINDINGS.
5. **Export/import** (the user taps through, a target has to be chosen): Info → Data → "Export and
   share" and "Save as file", then import the own file (overwrites settings, adds measurements).
6. Checked 2026-11-05 (done): app runs with targetSdk 36, heating profile from description with
   Nano, order dialog, shower/bath label on the dashboard (after the fix in `ChartMarkers.kt`).

## Step 2 – battery measurement and optimisation (implemented 2026-11-01, measurement open)

Done:
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

Open:
1. **Measure** (after 1–2 days): number of fetches from the log
   (`adb logcat -s PureHeat | grep "next run"`) or the app's data use in the Android settings
   (Apps → PureHeat → Mobile data/Wi-Fi) and battery
   (`adb shell dumpsys batterystats --charged de.cplagens.pureheat`; the phone is mostly on the
   charger, so data use is the more meaningful measure). Enter the result here.
2. **Profile-aware fetching** (once profiles are loaded in the night job, step 3/5): 15 min
   around the switching times of the active profile so that the model sees exactly these
   transitions. Extend `PollingPolicy` with "next switching time" for this.
3. **Night job** (`PeriodicWorkRequest` 24 h, `setRequiresCharging(true)`,
   `setRequiresDeviceIdle(true)`) – deliberately only create it with step 3, once it has work to do
   (fit the model, fetch the forecast, calculate predictions/profile).
4. No permanent WebSocket connection on the phone (that would be something for the Raspberry Pi).
5. According to homematicip-rest-api there are no slimmer queries; fetching less often remains the
   lever.
6. Minor: every new worker process does a lookup first (`getHost`, 223 bytes) – negligible; could
   be cached in SharedPreferences.

## Step 3 – room model (implemented 2026-11-01, now learning with real data)

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
  and the eco/comfort temperature. A night job is not needed for this (yet).
- A unit test simulates a room with known k, P, dead time and checks that the fit finds them again
  (`RoomModelTest`).

Open / watch:
1. After a few days check whether the values are plausible (detail view → room model).
   Typical for floor heating: k ≈ 0.02–0.08/h, P ≈ 0.2–1 K/h, dead time 0.5–3 h.
2. Room 2 initially heated at only ~0.09 °C/h (the valve pin was stuck, freed on 2026-11-01) –
   possibly ignore this room's data from before 2026-11-02.
3. Rooms whose valve is almost never fully closed may never provide "valve closed" phases → then
   estimate k from phases with a small valve value (extension).
4. Later (step 5/6) take eco/comfort values directly from the active heating profile.

## Step 4 – weather forecast (implemented 2026-11-02)

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
- Open: show the forecast in the detail view as a continuation of the outdoor curve into the
  future; for step 5/6 use the hourly forecast at the respective time instead of a mean.

## Step 5 – probe run with hit rate (implemented 2026-11-02, now collecting data)

Implemented:
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

Open / watch:
1. From 2026-11-03 look at the first evaluations; rooms with night ventilation (rooms 1–3) will
   often be "does not count" if the window is still open at heating start.
2. Target level: short test values (26 °C in room 2) push the 90th percentile up for up to 7 days
   – possibly take values from the profile instead of the history.
3. Also store residuals (prediction − measurement) as a time series once a grey-box or AI strategy
   is added.

Original plan:

- A night job creates predictions per room for the next day from the **currently active
  profile**: for every change base → period "target temperature reached at …" and for every change
  period → base "temperature at … at …". Also: when *would* the optimal control start.
- Table `prediction(roomId, strategy, createdAt, targetTime, targetTemperature, predictedReachTime, actualReachTime?, ...)`
  – `strategy` allows AI/grey box in simulation mode next to the physical model later.
- A night job (`setRequiresCharging`, `setRequiresDeviceIdle`) makes sense here: load profile(s),
  create predictions, evaluate the previous day. Cache profiles while doing so (also needed for
  the profile-aware fetching from step 2).
- Evaluate on the following day with `room_sample` → error in minutes, "on time ±0.5 K" yes/no.
- Display per room: "prediction off by X min on average, target Y % on time (14 days)".
- Switch "Activate optimal control" only above a threshold (suggestion: ≥ 14 days of data,
  ≥ 80 % on time, mean error ≤ 30 min). The user switches it on.

## Step 6 – comfort times and optimal profile

- Input per room: comfort window per weekday (start, end, temperature) + eco temperature.
  Table `comfort_window`.
- Calculation per window: start = window start − heat-up time (base → target, forecast) − buffer;
  end = window end − cool-down time for 0.5 K (residual heat of the screed). Merge overlapping
  ones, max. 6 periods/day (`ProfileEditing.MAX_PERIODS`), 15 min grid.
- First only as a **suggestion** with a list of changes (`ProfileEditing.diff`) → normal save path.
- Automatic mode (after opt-in): the night job only writes on change, max. once a day, log table,
  undo via the existing backups.
- Hint to the user: Homematic has a built-in function "Optimum Start/Stop" (off for the user) – do
  not run it at the same time as the app's automatic mode.

## Step 7 – savings

- **Valve hours** (valve opening × time, summed over all channels) as a measure of heating time.
- **Simulation:** use the room model to recalculate how the old profile would have run on the same
  day with the same outdoor temperature → saved valve hours and lower mean room temperature.
- **Weather adjustment** via heating degree days (e.g. base 15 °C).
- Optional kWh/€: the user occasionally enters the reading of the heat meter (Qundis, present in
  the flat) and the price → calibration valve hours ↔ kWh.
- Display e.g. "This week about 18 % less heating time, comfort times kept 92 %".

## Later – Raspberry Pi

Move model/forecast/profile calculation into a JVM module `:core`, run it as a service on the Pi
(possibly with WebSocket for real-time events there). The phone then only displays. Only tackle
this once control on the phone works reliably.

## Way of working / notes for agents

- Build and test after every change: `./gradlew assembleDebug testDebugUnitTest`.
- In Android Studio long shell calls sometimes abort with a timeout → start Gradle in the
  background (`runInBackground`) and wait for the end; if needed install directly with
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Always check new API fields against the real dump first (`files/last_state.json`), never guess –
  the library partly differs (`baseValue`, `profileIndex`).
- Write experiments against the cloud only with unchanged content (no-op) or after explicit
  consent of the user.
- No real IDs, locations, SGTIN or tokens in repo, tests or documents.
