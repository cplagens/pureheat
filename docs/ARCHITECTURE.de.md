# PureHeat – Architektur

[English](ARCHITECTURE.md) · **Deutsch**

Stand: 2026-11-01. Beschreibt, wie die App aufgebaut ist, welche Daten sie woher bekommt und
welche Annahmen über die (inoffizielle) Homematic IP Cloud-API gelten.

## Überblick

```
 ┌───────────────────────── Android-App (de.cplagens.pureheat) ─────────────────────────┐
 │                                                                                      │
 │  UI (Compose)                    ViewModels                Daten                     │
 │  ─────────────                   ──────────                ─────                     │
 │  SetupScreen ──────────────┐                                                         │
 │  DashboardScreen ──────────┼──► MainViewModel ─────┐                                 │
 │   └ RoomHistoryChart       │    (5-min-Polling,     │                                 │
 │  RoomDetailScreen ─────────┼──► RoomDetailViewModel├──► HeatingRepository ──► HmipClient ──► HmIP-Cloud
 │   └ TimeSeriesChart        │                       │     │  (fetch, record,        (OkHttp)
 │  ProfileScreen ────────────┼──► ProfileViewModel ──┘     │   Profile laden/        
 │  AboutDialog               │                             │   speichern)           
 │                            │                             ├──► Room-DB history.db   
 │  HistoryWorker (WorkManager, 15/30/60 min adaptiv) ──────┘     (60 Tage)           
 │                                                                                      │
 │  Reines Kotlin (keine Android-Abhängigkeiten, unit-getestet):                        │
 │  HeatingStateParser · HeatingProfileCodec · ProfileEditing · HistoryAnalysis         │
 └──────────────────────────────────────────────────────────────────────────────────────┘
```

Die eigentliche Heizungsregelung läuft immer auf dem Access Point (HmIP-HAP) bzw. den
Geräten. Die App liest Zustände, zeichnet sie auf und schreibt nur Heizprofile – und das nur
nach ausdrücklicher Bestätigung.

## Pakete und Dateien

Pfade relativ zu `app/src/main/java/de/cplagens/pureheat/`.

| Datei | Aufgabe |
|---|---|
| `PureHeatApp.kt` | `Application`: hält `CredentialStore` und `HeatingRepository` (einfache manuelle DI), plant den `HistoryWorker` |
| `MainActivity.kt` | Ein-Activity-App; Navigation per Zustand (`profileRoomId`, `historyRoomId`, `propertiesRoomId`) + `BackHandler`; startet das 5-min-Polling nur im Zustand `STARTED` |
| `SupportLinks.kt` | Link zum Quellcode (keine Zahlungs-Links – Play-Richtlinien) |
| `data/support/CoffeeBilling.kt` | Freiwillige Unterstützung über Google Play Billing: verbrauchbares Produkt `coffee`, wird sofort verbraucht; `CoffeeState` (Preis oder ausgeblendet, ausstehend, bedankt) |
| **hmip/** | |
| `HmipClient.kt` | Cloud-API: Lookup, Client-Registrierung (blauer Knopf), `getCurrentState`, `getProfile`, `updateProfile` (auch Umbenennen: `name` im Profil), `setActiveProfile` |
| `ErrorMessages.kt` | Exceptions → deutsche Fehlermeldungen (`toUserMessage()`), HTTP-Fehlerhinweise |
| `HeatingState.kt` | `HeatingStateParser`: Roh-JSON → `HeatingState` (Räume, Heizkreise, Profil-Referenzen, Außentemperatur) |
| `HeatingProfile.kt` | `HeatingProfileCodec` (Profil-JSON lesen/schreiben, unbekannte Felder bleiben erhalten), `ProfileEditing` (reine Bearbeitungslogik) |
| **data/** | |
| `CredentialStore.kt` | SGTIN + Auth-Token in app-privaten SharedPreferences |
| `HeatingRepository.kt` | Einziger Zugang zur Cloud: Zustand holen + aufzeichnen, Profile laden/speichern (Backup vorher, Prüfung nachher), Debug-Dumps |
| `ForecastClient.kt` | Open-Meteo-Abruf (stündliche Außentemperatur, 3 Tage) mit gerundetem Standort |
| `HistoryWorker.kt` | Sich selbst neu planende Abrufkette (`history-chain`) + stündlicher `HistoryWatchdogWorker` |
| `PollingPolicy.kt` | Reine Logik: nächster Abruf in 15 min (Ventil/Sollwert ändert sich), 30 min (stabil) oder 60 min (nachts) |
| `history/HistoryDatabase.kt` | Room-DB `history.db` (Version 6): `room_sample`, `circuit_sample`, `weather_sample`, `forecast_sample`, `prediction` |
| `history/HistoryAnalysis.kt` | Reine Analyse: Warnung „Ventil offen, aber keine Wärme“, Aufheizrate, Phasen „Ventil offen/zu“ (mit Totzeit-Tiefpunkt und Ende des freien Aufheizens) |
| `history/BathingDetection.kt` | Reine Heuristik: Duschen/Baden aus schnellem Luftfeuchte-Anstieg; Ausklammern fürs Modell |
| `RoomOrder.kt` | Reine Logik: Reihenfolge der Raumkarten (Thermostate hinter ihrem Raum, gespeicherte Reihenfolge) |
| `history/WindowDetection.kt` | Reine Heuristik: Fenster offen aus Temperatursturz/-anstieg (+ Luftfeuchte) bei geschlossenem Ventil; Ausklammern dieser Zeiten fürs Modell |
| `model/RoomModel.kt` | Reines Wärmemodell je Raum (k, P, Totzeit), Anpassung aus Phasen, Vorhersage Aufheiz-/Abkühlzeit; `SetPointLevels` (Spar-/Komforttemperatur aus Sollwerten) |
| `model/RoomSimulator.kt` | Reine Vorwärtssimulation eines Raums (Thermostat-Verhalten, Totzeit, Prognose) |
| `probe/ProbeRun.kt` | Reiner Probelauf: Vorhersage der Aufheizzeiten aus dem aktiven Profil, Auswertung gegen die Messung, Trefferquote |
| `model/RoomInsight.kt` | Modell + Spar/Komfort + Außentemperatur je Raum, mit fertigen Vorhersagen |
| `room/RoomProperties.kt` | Raumeigenschaften (Fläche, Fenster + Richtung, Verglasung, Außenwände, Lage, Bodenbelag, …), nachsichtiges JSON-Lesen, `RoomPriors` (Startwerte k/P/Totzeit aus den Eigenschaften) |
| `room/KeywordExtractor.kt` | Reiner Stichwort-Fallback (Deutsch/Englisch): Freitext → `RoomProperties` |
| `ai/OnDeviceAi.kt` | Gemini Nano über ML Kit: Verfügbarkeit, System-Download, JSON-Antwort (geteilt) |
| `profile/ProfileFromText.kt` / `ProfileDescriber.kt` | Text → Heizprofil-Vorschlag (Nano oder Stichwörter), Validierung auf Raster/Grenzen |
| `export/Csv.kt` / `DataTransfer.kt` | Export (ZIP mit CSV + settings.json) und Import |
| `demo/DemoHome.kt` | Rein: erfundene Wohnung (6 Räume, Profile, Wetter), deterministische Simulation inkl. gelüftetem Schlafzimmer und Duschen |
| `demo/DemoCloud.kt` | Offline-`HeatingCloud` für den Demo-Modus (Roh-JSON wie die echte Cloud, Profiländerungen im Speicher) + Startdaten (3 Wochen, Probelauf-Ergebnisse, Beschreibungen) |
| `hmip/HeatingCloud.kt` | Schnittstelle der Cloud-Aufrufe nach dem Login; `HmipClient` oder `DemoCloud` (Auswahl im Repository über `DemoHome.SGTIN`) |
| `room/RoomDescriber.kt` | Gemini Nano auf dem Gerät (ML Kit Prompt API) + Stichwörter; stößt ggf. den System-Download an |
| `room/RoomDescriptionStore.kt` | Text + Eigenschaften je Raum in SharedPreferences `room_descriptions` |
| **ui/** | |
| `MainViewModel.kt` | Setup/Registrierung, Polling alle 5 min solange sichtbar, 24-h-Verlauf + Warnungen fürs Dashboard |
| `DashboardScreen.kt` | Raumkarten: Ist/Soll, Ventil je Kanal, 24-h-Diagramm, Links „Verlauf ›“ / „Heizprofil ›“ |
| `RoomHistoryChart.kt` | Kompaktes 24-h-Diagramm (Temperatur oben, Ventil unten) |
| `RoomDetailScreen.kt` / `RoomDetailViewModel.kt` | Detailansicht: Zeitraum 24 h–60 Tage, Ist/Soll/Außen, Ventil je Kanal, Werte per Antippen, Raumeigenschaften, Raummodell, Probelauf, erkannte Phasen |
| `RoomPropertiesScreen.kt` / `RoomPropertiesViewModel.kt` | Raum beschreiben (tippen/diktieren, Beispiel-Stichwörter), Eigenschaften erkennen, prüfen/korrigieren, speichern |
| `ModelTexts.kt` | Deutsche Texte für Modellvorhersagen (Dauer, „nicht erreichbar“, „lernt noch“) |
| `TimeSeriesChart.kt` | Wiederverwendbares Zeitreihen-Diagramm mit gemeinsamem Zeit-Cursor |
| `ProfileScreen.kt` / `ProfileViewModel.kt` | Wochenprofile ansehen/bearbeiten/speichern/aktivieren/rückgängig |
| `AboutDialog.kt` | Version, Beschreibung, Sprache, Daten, Kaffee spendieren (`SupportSection` + `SupportViewModel`), Logo-Preview |
| `SetupScreen.kt` | SGTIN/PIN-Eingabe, Hinweis auf blauen Knopf |

Ressourcen: adaptives Icon in `res/mipmap-anydpi-v26/`, Vordergrund/Hintergrund/Logo in
`res/drawable/` (gleiche Gestaltung wie PureRemote: Farbverlauf Türkis → Navy, weißes Symbol). Für die README gibt es
das Logo zusätzlich als `docs/assets/logo.svg`.

## Datenfluss

### Lesen / Aufzeichnen
1. `HeatingRepository.fetch()` – aufgerufen vom UI-Polling (5 min, nur sichtbar) oder vom
   `HistoryWorker` (Hintergrund, 15/30/60 min je nach `PollingPolicy`, nur mit Netz und
   wenn der Akku nicht niedrig ist).
2. Ggf. Lookup des REST-Servers (gecacht, nach Fehler neu).
3. `getCurrentState` (~95 KB JSON) → `HeatingStateParser.parse()`.
4. `record()`: höchstens alle 4 min ein Datensatz je Raum, je Kanal und ein Wetterwert;
   Löschen von allem älter als 2 Jahre (Raummodell: nur die letzten 60 Tage).
5. UI beobachtet die Room-Tabellen als `Flow`.

### Profile schreiben
`ProfileViewModel.save()` → `HeatingRepository.saveProfile()`:
1. aktuelles Profil frisch aus der Cloud laden und als Backup in
   `files/profile_backups/<groupId>_<PROFILE_n>.json` ablegen,
2. `updateProfile` senden,
3. Profil erneut laden und mit dem gesendeten vergleichen; Abweichung → Fehlermeldung.
„Rückgängig“ spielt das Backup auf demselben Weg zurück.

## Datenmodell (Room, `history.db`, Version 6)

| Tabelle | Schlüssel | Felder |
|---|---|---|
| `room_sample` | roomId + timestamp | actualTemperature, setPointTemperature, valvePosition (max. der Kanäle bzw. Gruppenwert), humidity (ab v3) |
| `circuit_sample` | deviceId + channelIndex + timestamp | valvePosition |
| `weather_sample` | timestamp | outsideTemperature (`home.weather.temperature`), outsideAbsHumidity g/m³ (`home.weather.vaporAmount`, seit v6) |
| `prediction` | roomId + strategy + eventTime | Probelauf: Ziel, Starttemperatur, vorhergesagte/tatsächliche Erreichzeit, Modellquelle (fit/prior), Ergebnis (ab v5) |
| `forecast_sample` | timestamp | temperature (Open-Meteo, stündlich), fetchedAt; bei jedem Abruf ersetzt (ab v4) |

Migration 1 → 2 legt `weather_sample` an, 2 → 3 ergänzt `room_sample.humidity`, 3 → 4 legt
`forecast_sample` an, 4 → 5 `prediction`, 5 → 6 ergänzt `weather_sample.outsideAbsHumidity`; vorhandene Daten
bleiben erhalten. Die REST-Server-Adresse wird in SharedPreferences `hmip_server` gecacht
(neuer Lookup nur nach Fehler – der Lookup-Dienst antwortet bei zu vielen Anfragen mit 418).
Größe gemessen: ca. 710 Zeilen bzw. 120 KB/Tag → nach 2 Jahren ca. 90 MB.
Einstellungen außerhalb der DB: `room_descriptions` (Raumeigenschaften), `view_settings`
(ausgeblendete Bereiche der Detailansicht), `probe`, `hmip_server`.

## Homematic IP Cloud-API (inoffiziell)

Basis: [homematicip-rest-api](https://github.com/hahn-th/homematicip-rest-api). Alle Requests sind
`POST` mit JSON, Header `VERSION: 12`, `CLIENTAUTH` (SHA-512 von SGTIN + festem Salt),
`ACCESSPOINT-ID`, nach der Registrierung zusätzlich `AUTHTOKEN`.

| Endpunkt | Body | Bemerkungen (am echten System geprüft) |
|---|---|---|
| `lookup.homematic.com:48335/getHost` | `{clientCharacteristics, id}` | liefert `urlREST` (z. B. `https://srv00xx.a.homematic.com:6969`); antwortet für jede SGTIN mit 200 |
| `hmip/auth/connectionRequest` … `confirmAuthToken` | – | Registrierung, blauer Knopf |
| `hmip/home/getCurrentState` | `{clientCharacteristics, id}` | **nicht** nur `clientCharacteristics` – sonst 400 |
| `hmip/group/heating/getProfile` | `{groupId, profileIndex, profileId}` | Antwort: `{id, groupId, homeId, type, profileDays}`; Grundtemperatur heißt **`baseValue`** (nicht `baseTemperature`) |
| `hmip/group/heating/updateProfile` | `{groupId, profileIndex, profile}` | **`profileIndex` auf oberster Ebene ist Pflicht**, sonst 400 INVALID_REQUEST |
| `hmip/group/heating/setActiveProfile` | `{groupId, profileIndex}` | noch nicht am echten System getestet |

Wichtige Felder in `getCurrentState`:
- `groups[*]` mit `type == "HEATING"` = Raum: `actualTemperature`, `setPointTemperature`,
  `humidity`, `valvePosition` (nur bei Heizkörperthermostaten), `activeProfile`,
  `profiles{PROFILE_n: {profileId, name, enabled, visible}}`, `minTemperature`/`maxTemperature`.
- `devices[*]` mit `type` `FLOOR_TERMINAL_BLOCK_*` = Fußbodenheizungsaktor; Kanäle
  `FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL` mit `valvePosition` (0–1), `valveState`, `groups`.
  Unbelegte Kanäle: keine Gruppe, keine Position (z. B. `ADJUSTMENT_TOO_SMALL`) → ausgeblendet.
- `home.weather.temperature` = Außentemperatur; `home.location` (laut Bibliothek
  `latitude`/`longitude`, am echten System noch nicht geprüft).

Grundsatz: jedes Feld optional, unbekannte Felder ignorieren bzw. beim Zurückschreiben erhalten.

## Akku und Daten

- Polling im Vordergrund alle 5 min, nur solange die App sichtbar ist (`repeatOnLifecycle(STARTED)`).
- Hintergrund: Kette aus einmaligen WorkManager-Jobs; `PollingPolicy` wählt 15/30/60 min.
  Bedingungen: `NetworkType.CONNECTED`, `setRequiresBatteryNotLow(true)`. Ein stündlicher
  Watchdog-Job (ohne Netz) startet die Kette neu, falls sie abreißt. Android bündelt die
  Jobs und streckt sie in Doze. `am force-stop` pausiert alle Jobs bis zum nächsten App-Start.
- Jobs am Gerät ansehen: `adb shell dumpsys jobscheduler | grep -A3 de.cplagens.pureheat`;
  erzwingen: `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler de.cplagens.pureheat <jobId>`.
- Debug-Builds loggen pro Request Kompression und Größe (`adb logcat -s PureHeat`),
  um den Datenverbrauch zu messen.
- Gemessen: Die Cloud komprimiert **nicht** (ignoriert `Accept-Encoding: gzip`);
  `getCurrentState` ≈ 100 KB pro Abruf. Seltener abrufen ist der wichtigste Hebel
  (adaptives Abrufen, siehe „Akku und Daten“).

## Sprachen und Texte

- Alle Texte in String-Ressourcen: `values/strings.xml` = Englisch (Standard und Rückfall für
  nicht unterstützte Sprachen), dazu wie in PureRemote `values-de`, `-fr`, `-es`, `-it`, `-pt`, `-nl`,
  `-pl`, `-tr`, `-ru`, `-ja`, `-zh-rCN`. Neue Schlüssel in alle Dateien; `lintDebug` prüft das.
  Achtung: In Texten ohne Platzhalter kein „% “ vor a/e/h/d/s … – Lint hält das für ein Format.
- Die App folgt der Systemsprache; eine eigene Wahl im Info-Dialog (`LanguagePicker`) bzw. ab
  Android 13 in den Systemeinstellungen „App-Sprachen“ (`res/xml/locales_config.xml`). Umsetzung
  wie in PureRemote: `AppCompatDelegate.setApplicationLocales`, deshalb ist `MainActivity` eine
  `AppCompatActivity` (Theme `Theme.AppCompat.DayNight.NoActionBar`), und
  `AppLocalesMetadataHolderService` speichert die Wahl auch auf Android ≤ 12.
- ViewModels und Datenschicht liefern keine fertigen Texte, sondern `UiText` (Ressource +
  Argumente), aufgelöst erst in der Oberfläche – so stimmt die Sprache auch nach einem Wechsel.
  Fehler aus der Cloud: `HmipException.uiText`, `Throwable.toUiText()`.
- Zahlen, Uhrzeiten und Wochentage werden über die Ressourcen/`DateFormat` sprachabhängig
  formatiert (Komma/Punkt, 12/24 h).
- Hilfe-Bildschirm (`HelpScreen`) erklärt die Anzeigen; erreichbar über „Hilfe“ in der Übersicht
  und auf dem Einrichtungsbildschirm.

## Sicherheit und Datenschutz

- Repo ist öffentlich: keine SGTIN, kein Token, keine Roh-Dumps einchecken
  (`files/last_state.json`, `files/last_profile.json`, Backups liegen nur app-privat auf dem Gerät).
- Token nur in app-privaten SharedPreferences; `allowBackup=false`.
- Schreibzugriffe nur für Heizprofile und nur nach Bestätigung (Projektregel).
- Raumbeschreibungen werden nur auf dem Gerät ausgewertet (Gemini Nano über AICore oder
  Stichwörter); Diktieren nutzt die System-Spracheingabe (deren Anbieter kann online erkennen).

## Tests

`./gradlew testDebugUnitTest` – JVM-Unit-Tests für die reinen Kotlin-Teile:
`HeatingStateParserTest`, `HeatingProfileTest`, `HistoryAnalysisTest`, `ThermalPhaseTest`,
`PollingPolicyTest`, `RoomModelTest` (simulierter Raum mit bekannten Parametern), `WindowDetectorTest`,
`ForecastTest`, `ProbeRunTest`, `RoomPropertiesTest` (Stichwörter, JSON, Startwerte).
JSON-Beispiele in Tests sind anonymisiert.

## Debugging

- Vollständiger Rohzustand: `adb exec-out run-as de.cplagens.pureheat cat files/last_state.json`
- Zuletzt geladenes Profil: `… cat files/last_profile.json`
- Profil-Backups: `… ls files/profile_backups/`
- Hintergrundjob: `adb shell dumpsys jobscheduler | grep -A3 de.cplagens.pureheat`
