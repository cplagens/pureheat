# PureHeat – Next steps

Roadmap with **open items only**. The entry point is [STATUS.md](STATUS.md); what was built and
decided per step is in [HISTORY.md](HISTORY.md) (grep the section named in brackets). When an
item or step is done, move its log to HISTORY.md and delete it here (see CLAUDE.md).

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
- **AI:** physical model first; grey-box corrections later from the probe-run residuals; an AI only
  in simulation mode side by side (`prediction.strategy`) – HISTORY "AI model … (assessment)".
- UI texts in resources (English default, German for the user + 10 more languages).
- **Retention 2 years** (since 2026-11-03, before 60 days): training data for a later AI/grey box
  in simulation mode over whole heating seasons. The room model still only uses the last 60 days
  (`MODEL_WINDOW_MS`); predictions (`prediction`) are also kept for 2 years.
- **Configurable detail view** (2026-11-03): "View" hides sections (temperature, valves,
  humidity, room properties, model, probe run, window, phases); applies to all rooms, the hidden
  ones are stored (`ViewSettings`), default: everything visible.
- **Never put measures with different units on one shared axis** (e.g. °C and % RH): separate
  panels with a shared time axis (also for humidity, 2026-11-02).

## Open: check on the device

1. Outdoor line in the detail view; detail view in dark mode and with 7/14/60 days.
2. Language switch in the Info dialog (the activity is rebuilt), English UI, help screen.
3. Never run on the real system: "Activate profile" (`setActiveProfile`), "Undo last save",
   "Apply to other rooms".
4. **Rename profile** (cloud write via the undocumented way, only with the user's OK): e.g. rename
   "Profile 4" in the bathroom and back; check that the name shows up in the state and in the
   official app, result into FINDINGS. Fallback if the cloud ignores it: local display name only.
5. **Export/import** (the user taps through, a target has to be chosen): Info → Data → "Export and
   share" and "Save as file", then import the own file (overwrites settings, adds measurements).
6. Open-window detection after the 2026-11-06 rework: watch a few days (room 2 evening of 11-06:
   the opening ~21:15 should be the only event).

## Open-window detection (HISTORY "Open-window detection")

1. **Outside humidity from Open-Meteo** (`hourly=relative_humidity_2m`, or `dew_point_2m`): store
   it with the forecast/weather (new column + DB migration), compute the outside absolute humidity
   and use it in `WindowDetector`: only require a humidity fall if the outside air is clearly
   drier than inside (e.g. ≥ 1.5 g/m³); on mild, wet days fall back to the temperature rules.
   Later also: expected fall when airing (inside vs. outside) as a plausibility check, and a hint
   "airing helps against humidity now / not now" for the bathroom.
2. Second criterion once k with closed windows is known: cooling much faster than the model
   expects (e.g. > 3×) → window open, even without a drop.
3. Slow drops spread over several small steps (e.g. 0.2 + 0.2 K) are still missed; airing while
   the valve is open is never detected.
4. Detect the end better when the heating starts in the morning while the window is still open
   (valve open, temperature keeps falling → window still open).
5. Hint to the user when the heating profile heats with an open window (room 2: heating start
   03:00, window probably still open → energy lost).

## Battery and data use (HISTORY "Step 2")

1. **Measure** (after 1–2 days): number of fetches from the log
   (`adb logcat -s PureHeat | grep "next run"`) or the app's data use in the Android settings
   (Apps → PureHeat → Mobile data/Wi-Fi) and battery
   (`adb shell dumpsys batterystats --charged de.cplagens.pureheat`; the phone is mostly on the
   charger, so data use is the more meaningful measure). Result into FINDINGS.
2. **Profile-aware fetching:** 15 min around the switching times of the active profile so that the
   model sees exactly these transitions. Extend `PollingPolicy` with "next switching time".
3. **Night job** (`PeriodicWorkRequest` 24 h, `setRequiresCharging(true)`,
   `setRequiresDeviceIdle(true)`) – only once it has work to do (fit the model, fetch the forecast,
   calculate predictions/profile).

## Room model (HISTORY "Step 3", "Analysis of the first 28 h")

1. Check whether the values are plausible (detail view → room model). Typical for floor heating:
   k ≈ 0.02–0.08/h, P ≈ 0.2–1 K/h, dead time 0.5–3 h.
2. Room 2 initially heated at only ~0.09 °C/h (the valve pin was stuck, freed on 2026-11-01) –
   possibly ignore this room's data from before 2026-11-02.
3. Rooms whose valve is almost never fully closed may never provide "valve closed" phases → then
   estimate k from phases with a small valve value.
4. Take eco/comfort values directly from the active heating profile instead of the history (short
   test values like 26 °C push the 90th percentile up for up to 7 days).
5. **Better prediction:** "eco → comfort" (e.g. 15 → 24 °C) is unrealistic because the rooms only
   drop to ~21 °C at night. Heat-up from the *typical temperature at the end of the setback*
   (median of the current temperature at the target rise); cool-down as "after X h of setback
   still about Y °C" (`T(t) = Tₐ + (T₀ − Tₐ)·e^(−k t)`, X = typical setback duration).

## Weather forecast (HISTORY "Step 4")

1. Show the forecast in the detail view as a continuation of the outdoor curve into the future.
2. For steps 5/6 use the hourly forecast at the respective time instead of a mean.

## Probe run (HISTORY "Step 5")

1. Look at the evaluations; rooms with night ventilation (rooms 1–3) will often be "does not count"
   if the window is still open at heating start.
2. Store residuals (prediction − measurement) as a time series once a grey-box or AI strategy is
   added.
3. Switch "Activate optimal control" per room only above a threshold (suggestion: ≥ 14 days of
   data, ≥ 80 % on time, mean error ≤ 30 min). The user switches it on.

## Room properties (HISTORY "Room properties …")

1. **Solar gain**: also fetch Open-Meteo `shortwave_radiation`; window area × direction ×
   radiation as an additional term (grey box), especially for south/west rooms on sunny days.
2. Further details only once the model can use them: position of the flat (neighbouring flats
   heated?), neighbouring rooms (unheated/cool), occupancy, flow temperature/heating curve, pipe
   spacing, screed thickness.
3. With many rooms enter shared values (building, glazing) once for all.
4. Check with the probe run whether starting values with properties hit better than without.

## Smaller ideas

- Showering/bathing: shower times as a comfort time suggestion for the bathroom; tell bathtub from
  shower by duration/temperature rise.
- Export: automatic weekly backup into a chosen folder (SAF tree URI).

## Play Store (HISTORY "Play Store preparation …", "Export/import, target API 36 …")

1. Mention the demo mode in the review notes.
2. **Donations via Google Play Billing** – *needs the user's decision first.* Effects: the listing
   shows "Contains in-app purchases"; a payments profile makes the developer's **physical address
   public** in the listing; Google keeps 15 %; tax details in the payments profile; IARC
   questionnaire: digital purchases ("In-app purchases" note, same age rating); Data safety: check
   "purchase history"; one sentence on Google Play payments in `docs/privacy.md`. External links
   (PayPal, GitHub Sponsors) stay out of the app (decided 2026-11-05).
   Implementation: Play Billing Library, section "Support" with 2–3 consumable products, `consumeAsync`
   within 3 days (otherwise auto-refund), no server verification needed (nothing is unlocked), all
   texts in the 12 languages, store text "all features free, optional voluntary support". For a
   later F-Droid build: product flavour without Billing.

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

**Local operation without the cloud** (assessment 2026-11-06):
- The HmIP-HAP has no local interface; its cloud protocol is closed and TLS-encrypted – a
  self-hosted replacement for the cloud is not realistic. The way is a different **central unit**:
  - **OpenCCU (RaspberryMatic) on the Pi** + radio module (RPI-RF-MOD or HmIP-RFUSB): local
    XML-RPC/JSON-RPC, well documented (e.g. Home Assistant "Homematic(IP) Local"); the official
    Homematic IP app no longer works. Fits this step (same Pi).
  - **HmIP Home Control Unit (HCU1)**: official local Connect API (WebSocket); before deciding,
    check which data it provides (valve per channel, humidity, week profiles) and whether a
    migration from the HAP is offered.
- Steps (CCU): set up the central unit (fixed IP, backups); reset all devices (floor heating
  actuator, wall thermostats) and teach them in again, recreate rooms, heating groups, direct links,
  week profiles – the heating is partly unregulated meanwhile (outside the heating season or room by
  room); in the app a `CcuClient` behind `HeatingCloud` (like `DemoCloud`): mapping of actual/target
  temperature, valve `LEVEL` per channel, humidity, `WEEK_PROFILE` read/write; setup choice cloud vs.
  local (address, user, password); LAN with self-signed certificate → pinning, local-network
  permission on newer Android; from outside only via VPN. Gains: a few KB per fetch instead of
  ~100 KB, real-time events (listener on the Pi), no unofficial API. Write rule unchanged.
- Hardware for OpenCCU (rough prices/power from 2026-11-06, check before buying): ≥ 1 GB RAM
  (no Pi Zero 2 W), SSD better than SD card. Radio: RPI-RF-MOD (Pi GPIO; on a PC via HB-RF-USB-2)
  or HmIP-RFUSB stick (enough for HmIP only; on a short USB extension, away from USB 3).
  | Option | ~cost | idle | note |
  |---|---|---|---|
  | used thin client (Futro S740, Wyse 5070, t630) + HmIP-RFUSB | 60–85 € | 4–7 W | cheapest robust, x86, SSD; PureHeat service fits too |
  | Raspberry Pi 4 (2 GB) + RPI-RF-MOD | 100–120 € new | 3–4 W | best documented |
  | Intel N100 mini PC + HmIP-RFUSB | 130–200 € | 6–10 W | Proxmox: OpenCCU VM + PureHeat + Home Assistant |
  | eQ-3 CCU3 | 150–180 € | ~3 W | Pi 3 + radio in a case |
  Running cost at 0.35 €/kWh: 3 W ≈ 9 €, 6 W ≈ 18 €, 10 W ≈ 31 € per year.
- An Android tablet as the always-on device instead of the Pi was considered and dropped
  (2026-11-06); it can't be the radio central unit anyway (OpenCCU needs Linux kernel drivers).

## Way of working / notes for agents

- After every change: `scripts/check.sh` (build, unit tests, lint); install with
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Always check new API fields against the real dump first (`files/last_state.json`), never guess –
  the library partly differs (`baseValue`, `profileIndex`).
- Write experiments against the cloud only with unchanged content (no-op) or after explicit
  consent of the user.
- No real IDs, locations, SGTIN or tokens in repo, tests or documents.
