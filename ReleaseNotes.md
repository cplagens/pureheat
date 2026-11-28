# Release Notes

**English** · [Deutsch](ReleaseNotes.de.md)

Newest version on top. The version is the git tag of the release (`vX.Y.Z`, see `appVersion` in
`gradle.properties`).

## Unreleased

- Faults are shown right away: a banner at the top of the overview and a line in the room card
  (e.g. actuator not seated on the valve, valve stuck, device unreachable, low battery, emergency
  mode). Tap it for the likely causes and the steps to fix it.
- New "Comfort times" screen (from the heating profile): set per weekday when a room should be
  warm, with presets and a draft from the current profile. Saved on the phone only; nothing is
  written to the heating yet.
- Comfort times: a card shows what the planner suggests for your windows and how it would have done
  last week compared with the current profile (comfort kept, valve hours, mean temperature, both
  curves). "Open in the profile editor" puts the suggestion into the profile editor; it is only
  saved after you check the changes and confirm.

## 1.1.0 – room model from all data (November 2026)

- Overview: each room's heat-up/cool-down line now comes from the room model fitted over all
  data, so it shows times from the first week on instead of "still learning". If the probe run
  shows the classic model is clearly more accurate for a room, that one is shown instead.
- Room detail: the temperature chart shows the next 12 hours as the room model expects them,
  and when each heat-up should reach its target. Room model card reworked: reliability badge,
  key figures, tomorrow with your active profile; details on demand. Probe run as a hit rate,
  a deviation chart and the next check.
- Room model card: a chart of a simulated day (setback → comfort → setback) for both models;
  the model from all data with its predictions, which values are measured or only estimated,
  and its weekly versions with the error on the last week (also as a chart).
- Probe run: once a room model from all data exists (after 7 days), it predicts side by side
  with the physics model; the detail view shows its own hit rate.
- Open window: no longer counted as closed when the heating starts or the temperature only
  wobbles slightly; morning heating against an open window is left out of the room model.
- Heating profile: "Undo last save" now asks for confirmation before writing to the cloud.
- Heating profile editor: "Add phase" now appends the new phase right after the last one
  (instead of in the largest gap, usually the early morning).
- Saving a heating profile: the choice of other rooms now comes first and long change lists are
  collapsed; other rooms only get the changed days (before: the whole week).
- Info: the language choice is collapsed to one line.
- Logging out also deletes the last data export from the app cache.
- Profile editor: the −/+ buttons are announced with their value name by screen readers.

## 1.0.0 – first release (November 2026)

**Display**
- Overview per room: current/target, humidity, opening of every valve (heating circuit), warning
  for "valve open for a long time, room not getting warmer". Room order: thermostats whose name
  contains a room name (e.g. towel radiators) follow that room; "Order" to arrange rooms yourself.
- 24 h history per room with time labels for setpoint changes and detected events; detail view
  from 24 h to 60 days with outdoor temperature, every channel and humidity, values on tap; dates
  and a weekly grid for long ranges; sections can be hidden via "View".
- Outdoor temperature and weather forecast (Open-Meteo, location rounded to ~1 km).

**Learning and predictions**
- Background recording (15/30/60 min depending on activity), kept for 2 years.
- Room model per room (heat loss k, heating power P, dead time of the screed) with heat-up and
  cool-down times.
- **Open-window detection** from the temperature curve, without window contacts: checks whether
  drier outside air came in (absolute humidity inside and outside) and copes with sensors that
  report only bigger steps.
- **Showering/bathing** detected in bathrooms from the quick rise in humidity.
- Open windows and showers are left out when the room model learns.
- Probe run: daily predictions of when heat-up phases reach their target, with hit rate.
- Room properties by text or dictation, recognised by on-device AI (Gemini Nano) or keywords; they
  provide the model's starting values and decide what is detected (no windows → no open-window
  detection, no shower/bathtub → no shower detection).

**Heating profiles**
- View and edit weekly profiles (drag, exact times, copy days, apply to other rooms), rename,
  activate, undo the last save. Before saving a list of all changes, a backup of the old version
  and a read-back check after saving.
- **Create from description:** write or dictate your week in your own words; the on-device AI
  turns it into a suggestion to review and save.

**Other**
- **Demo mode:** "Try demo mode" on the setup screen shows a sample flat with invented data –
  without an access point, offline; leave it with "Log out".
- **Export and import:** all measurements (CSV) and settings as a ZIP – share or save as a file;
  import e.g. on a new phone (Info → Data).
- **Buy me a coffee:** in Info, a voluntary tip of 1.99 € / $1.99 via Google Play. It unlocks
  nothing; the section only appears when the app is installed from Google Play.
- 12 languages (German, English, French, Spanish, Italian, Portuguese, Dutch, Polish, Turkish,
  Russian, Japanese, Chinese), system language or own choice.
- Help in the app, hint about the battery setting for gap-free recording.
- Android 8 to 16 (target API 36).
- Free and open source (Apache 2.0), no ads, no tracking.
