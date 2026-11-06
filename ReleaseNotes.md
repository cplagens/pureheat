# Release Notes

**English** · [Deutsch](ReleaseNotes.de.md)

Newest version on top. Versions follow `versionName` in `app/build.gradle.kts`.

## Unreleased

- **Demo mode:** “Try demo mode” on the setup screen shows a sample flat with invented data – without
  an access point, offline; leave it with “Log out”.
- Overview: a detected shower or open window is always labelled above the chart, even when there
  were many setpoint changes.
- Open-window detection: far fewer false alarms after hours of an unchanged temperature or with
  rising humidity; closing is also recognised when the room warms up slowly.
- **Heating profile from a description:** "Create from description" – write or dictate your week
  in your own words; the on-device AI turns it into a suggestion to review and save.
- The room description now counts: rooms without windows get no open-window detection, bathrooms
  without shower and bathtub no shower/bath detection.
- Donation links removed from the app (Google Play policies); they are now only in the README.

- **Export and import:** all measurements (CSV) and settings as a ZIP – share (e.g. Google Drive,
  mail) or save as a file; import e.g. on a new phone (Info → Data).
- Target API Android 16 (API 36), as required by Google Play since 2026-08-31.
- **Rename heating profiles** (the name is stored in the cloud and checked afterwards).
- **Shower and bathtub** as room properties; in bathrooms the app detects **showering/bathing** from
  the quick rise in humidity, marks it in the charts and leaves these times out when learning the
  room model.
- **Room order:** thermostats whose name contains a room name (e.g. towel radiators) are placed
  directly after that room; "Order" in the overview to arrange rooms yourself.
- Open source made visible: link to the repository in Help and Info, licence header in every source
  file, `CONTRIBUTING.md`, `NOTICE`, `LICENSE.md`.

## 0.1.0 – first version (November 2026)

**Display**
- Overview per room: current/target, humidity, opening of every valve (heating circuit), warning
  for "valve open for a long time, room not getting warmer".
- 24 h history per room; detail view from 24 h to 60 days with outdoor temperature, every channel,
  values on tap; sections can be hidden via "View".
- Outdoor temperature and weather forecast (Open-Meteo, location rounded to ~1 km).

**Learning and predictions**
- Background recording (15/30/60 min depending on activity), kept for 2 years.
- Room model per room (heat loss k, heating power P, dead time of the screed) with heat-up and
  cool-down times.
- Open-window detection from the temperature curve, without window contacts.
- Probe run: daily predictions of when heat-up phases reach their target, with hit rate.
- Room properties by text or dictation, recognised by on-device AI (Gemini Nano) or keywords; they
  provide the model's starting values.

**Heating profiles**
- View and edit weekly profiles (drag, exact times, copy days, apply to other rooms), activate,
  undo the last save. Before saving a list of all changes, a backup of the old version and a
  read-back check after saving.

**Other**
- 12 languages (German, English, French, Spanish, Italian, Portuguese, Dutch, Polish, Turkish,
  Russian, Japanese, Chinese), system language or own choice.
- Help in the app, hint about the battery setting for gap-free recording.
- Free, no ads, no tracking.
