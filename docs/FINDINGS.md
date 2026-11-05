# PureHeat – Findings

**English** · [Deutsch](FINDINGS.de.md)

What we learned on the real system (as of 2026-11-02). Complements [ARCHITECTURE.md](ARCHITECTURE.md)
(structure) and [NEXT_STEPS.md](NEXT_STEPS.md) (plan). Please add new findings here with a date.
Do not add IDs, locations or personal names – the repo is public.

## Homematic IP Cloud API

| Topic | Finding | Since |
|---|---|---|
| `getCurrentState` | Body must be `{clientCharacteristics, id}`; only `clientCharacteristics` → 400 | 2026-11-01 |
| Size | ~100 KB per fetch, **no compression** (`Accept-Encoding: gzip` is ignored) | 2026-11-01 |
| Lookup | `getHost` answers 200 for any SGTIN; with too many lookups briefly **418** → cache the REST URL | 2026-11-02 |
| Reading profiles | `getProfile` with `{groupId, profileIndex, profileId}`; response `{id, groupId, homeId, type, profileDays}` | 2026-11-01 |
| Base temperature | is called **`baseValue`** (homematicip-rest-api documents `baseTemperature`) | 2026-11-01 |
| Writing profiles | `updateProfile` needs **`profileIndex` at the top level**, otherwise 400 INVALID_REQUEST; returns 200 without body | 2026-11-01 |
| Weather | `home.weather`: `temperature`, `minTemperature`, `maxTemperature`, `humidity`, `windSpeed`, … | 2026-11-01 |
| Location | `home.location`: `city`, `latitude`, `longitude` – as **strings** | 2026-11-01 |
| Unused channels | The floor heating actuator reports unused outputs without group and without `valvePosition` (`ADJUSTMENT_TOO_SMALL`) | 2026-11-01 |
| Window contacts | none present → `windowState` always null; windows can only be detected from the temperature curve | 2026-11-02 |
| Lookup cache | REST URL in SharedPreferences: no more `getHost` after a process restart (confirmed on the device) | 2026-11-02 |
| Humidity | `groups[*].humidity` (integer, % RH) per room; recorded since DB v3 | 2026-11-02 |

## Open-Meteo

- Response with `timeformat=unixtime` and `timezone=UTC`: `hourly.time[]` (seconds) and
  `hourly.temperature_2m[]` (single values may be `null`). ~5 KB for 3 days.
- First forecast on the evening of 2026-11-02: next 24 h 4–10 °C (measured 6.7 °C) – plausible.

## Android

- **App standby buckets slow down background jobs massively.** A rarely opened app is demoted step
  by step (ACTIVE → WORKING_SET → FREQUENT → RARE). Measured: 60 instead of 30 min interval during
  the day, then 9 h without any recording. Fix: Battery → "Unrestricted" (the app shows a hint
  card for it). Diagnosis: `adb shell dumpsys usagestats | grep pureheat`.
- **Effect of "Unrestricted" (measured 2026-11-03):** 66 recordings in 22 h
  (11-02 19:35 – 11-03 17:52), at night every 60 min as planned, during the day 15–42 min, no gap
  over 62 min (before: 36 in 28 h with a 9 h gap).
- **DB growth:** ~380 `room_sample` + ~330 `circuit_sample` rows/day, ~120 KB/day incl. WAL →
  about 90 MB with 2 years of retention (acceptable; 2 years instead of 60 days since 2026-11-03).
- **ML Kit `genai-prompt` 1.0.0-beta5 is built with Kotlin 2.4** – with Kotlin 2.2 the build fails
  ("incompatible version of Kotlin"). The project therefore uses Kotlin 2.4.21; `kotlinOptions`
  replaced by `kotlin { compilerOptions { jvmTarget } }`. KSP 2.3.13 works with it.
- Lint (`StringFormatInvalid`) treats "% a", "% e", "% H" in texts without placeholders as format
  specifiers – rephrase in translations ("procent", "(%)").
- **User's phone (2026-11-03):** Galaxy S25 Ultra (SM-S938B), Android 17;
  `com.google.android.aicore` installed and active (Gemini Nano possible), Google app present
  (system speech input for "Dictate"). Whether Nano is already downloaded only shows on the first
  call of "Recognise properties" (hint below the button; debug log `Nano: …`).
- **Gemini Nano tested on the device (2026-11-03):** available, answer in ~2 s, returns JSON in a
  ```json block (gets stripped). Weaknesses: "1. Stock" (German first floor) → `GROUND` (American
  counting) and `false` for yes/no fields not mentioned instead of `null`. Therefore: keyword hits
  win, Nano fills gaps; for yes/no only `true` from Nano counts.
- `am force-stop` pauses all WorkManager jobs until the next app start (this causes gaps during
  development).
- WorkManager jobs live in the namespace `androidx.work.systemjobscheduler`; to force one:
  `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler de.cplagens.pureheat <jobId>`.
- From inside a worker, re-schedule the own chain with `APPEND_OR_REPLACE` – `REPLACE` would cancel
  the running worker itself.

## Installation and building (anonymised)

- Flat in an apartment building, central heating (heat meter in the return line); no Homematic
  device switches pump or boiler. The flow temperature is unknown to the app.
- One floor heating actuator (8 channels) on one manifold; two circuits without actuator (always
  open, ~5 l/min each) – probably the supply lines of towel radiators.
- **Stuck valve pin** in one circuit (room 2): the actuator reported 100 %, the room did not get
  warm (0.09 °C/h). After freeing the pin on 2026-11-01: ~0.55 °C/h.
  → Lesson: "valve open" does not mean "heat arrives"; the app therefore shows "valve open" instead
  of "heating" and warns when the valve has been open for a long time but the room does not get
  warmer.
- **Well insulated:** with the window closed the temperature stays almost constant for hours in the
  evening (e.g. 23.2 °C from 7 to 9 pm in room 2).
- **At night the windows in rooms 1–3 are open** (according to the user). The nightly cooling
  (k ≈ 0.017–0.026/h, 0.3–0.4 K/h) is therefore almost only ventilation loss, not the room's heat
  loss. The app detects this from the temperature drop and leaves these times out when learning.
  First night: rooms 2 and 3 detected, room 1 not (even cooling, no drop visible with hourly
  data points).
- **Screed dead time ~1.5 h:** after the valve opens the room keeps cooling at first; then
  ~0.55 K/h heat-up (room 2), heating power P ≈ 0.8 K/h.
- **Heating with an open window:** in room 2 the profile starts at 03:00 while the window is
  probably still open – a starting point for the optimal profile (heat later or show a hint).
- **Bathroom and WC** stay at 25–27 °C all day, above target: the towel radiators (target
  26–27 °C) heat the room, the floor heating never runs there. **Kitchen/living room** stays at
  23–24 °C without heating. These rooms provide hardly any heat-up phases for the model.
- Outdoor temperature in the first days only 6–8 °C → the model sees little weather variation so
  far.

## Model and analysis

- Phases "valve closed, temperature rising" (bathroom/WC from towel radiators) say nothing about
  the heat loss and are discarded.
- Measure heating power only during **free heat-up** (from the minimum after the dead time until
  0.3 K below target), otherwise "holding the temperature" underestimates it.
- "Eco → comfort" from the target values (e.g. 15 → 24 °C) is unrealistic because the rooms only
  drop to ~21 °C at night – better calculate from the typical morning temperature (see NEXT_STEPS).
- **Showering/bathing in the data (2026-11-03):** humidity jumps between two data points, e.g.
  +15 %RH in 5 min at +0.3 K (bathroom, evening), +5 %RH in 15 min at +0.3 K, mornings/evenings
  +4–5 %RH in other rooms. Afterwards the humidity falls back over ~1–2 h. From this the thresholds
  of `BathingDetector` (≥ 5 %RH at ≥ 10 %RH/h, temperature not falling). Cooking looks similar
  → shown only in rooms with bathroom/shower/bathtub; all events are left out of the model.
- **Profile name:** `getProfile` returns no name; it is only in the state
  (`groups[].profiles[].name`). Renaming is tried via `updateProfile` with `name` in the profile
  object, checked with a new `getCurrentState` – not yet tried on the real system.
- **First probe run analysis (2026-11-03, 7 predictions, all with starting values):** 5 "does not
  count" (window open or manual change – expected in the rooms with night ventilation), 2
  "earlier": both predicted as "unreachable" but reached (target 25 and 23 °C in rooms kept warm by
  other heat sources). No statement about accuracy possible yet.
- Event times are shown rounded to 15 min because the data points are up to 1 h apart. "Window
  closed" is only shown if the closing was detected from the temperature rising again.
- **Shower in the real bathroom (2026-11-05):** 45 → 54 %RH between 19:26 and 19:44 at +0.3 K,
  back to 48–49 % after ~2.5 h; detected (detail view: 19:35–22:18, +0.8 K). On the dashboard
  the label was crowded out by five earlier setpoint labels – fixed: detected events are placed first.
- **Gemini Nano, heating profile from text (2026-11-05):** "Werktags 5:30 bis 7:30 Uhr 23 Grad und
  abends 19 bis 22 Uhr 23 Grad, am Wochenende 8 bis 11 Uhr 23 Grad, sonst 21 Grad" → correct periods,
  but `base` 23 in the weekday/weekend rules and "sonst 21" as an extra rule for all days with empty
  `periods`. `ProfileFromText.apply` handles this (last base per day wins, periods are merged, an
  empty rule keeps them) → result exactly as described. Answer in ~2 s.
