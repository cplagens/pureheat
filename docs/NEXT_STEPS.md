# PureHeat – Nächste Schritte

Stand: 2026-11-01. Dieses Dokument ist der Einstieg für eine neue Agent-Session.
Erst `CLAUDE.md` (Regeln) und [ARCHITECTURE.md](ARCHITECTURE.md) (Aufbau, API-Erkenntnisse) lesen.

## Ziel

Aus PureHeat soll eine **lernende Optimal-Steuerung** für die Fußbodenheizung werden:
Man gibt nur an, *wann* man *welche* Temperatur haben möchte (Komfortzeiten). Die App lernt pro
Raum, wie schnell er aufheizt und abkühlt (abhängig von der Außentemperatur), berücksichtigt
die Wetterprognose und berechnet daraus das Heizprofil. Sie läuft zuerst nur **im Probelauf**
(Vorhersagen + Trefferquote), und erst wenn genug Vertrauen da ist, schaltet der Nutzer die
automatische Steuerung pro Raum selbst ein. Zusätzlich soll die App zeigen, wie viel
Heizzeit/Kosten das spart.

## Getroffene Entscheidungen

- **Regelung bleibt auf dem Access Point.** Die App schreibt höchstens **einmal pro Tag**
  (nachts) das Profil neu. Fällt das Telefon aus, läuft das zuletzt geschriebene Profil weiter.
- **Schreiben nur mit Einwilligung:** zuerst nur Vorschläge (Bestätigung pro Speichern wie heute);
  Automatik nur nach ausdrücklichem Einschalten pro Raum, mit Protokoll und Rückgängig.
- **Wetterprognose von Open-Meteo** (kostenlos, ohne Konto). Standort aus `home.location`,
  **auf 2 Nachkommastellen gerundet (~1 km)** – vom Nutzer so freigegeben. Einmal pro Tag abrufen.
- **Akku sparen** hat Priorität (Telefon ist praktisch immer an, soll aber nicht leiden):
  rechnen nur beim Laden, Abrufe adaptiv, keine Dauerverbindung.
- **Später evtl. Raspberry Pi:** Modell, Prognose und Profilberechnung in **reinem Kotlin ohne
  Android-Abhängigkeiten** schreiben (wie `HistoryAnalysis`, `ProfileEditing`), damit sie später
  in ein JVM-Modul `:core` umziehen können. Vorerst bleibt alles auf dem Telefon.
- UI-Text Deutsch; die Daten der Detailansicht bleiben 60 Tage gespeichert.

## Erledigt (Schritt 1, 2026-11-01)

- Außentemperatur wird bei jeder Aufzeichnung gespeichert (`weather_sample`, DB-Version 2).
- Detailansicht pro Raum (`RoomDetailScreen`): Antippen des 24-h-Diagramms oder „Verlauf ›“;
  Zeitraum 24 h / 3 / 7 / 14 / 60 Tage; Ist, Soll, Außen; Ventil je Kanal; Werte per
  Antippen/Wischen; erkannte Aufheiz- und Abkühlphasen (`HistoryAnalysis.phases`) mit Rate.
- Debug-Log pro HTTP-Request mit Kompression und Größe (für die Akku-/Datenmessung).
- Aufbewahrung von 14 auf 60 Tage erhöht (Modell braucht unterschiedliches Wetter).

## Am Gerät geprüft (2026-11-01, abends)

- Detailansicht läuft auf dem Telefon (24 h, Antippen, Phasenliste).
- `home.weather` enthält `temperature`, `minTemperature`, `maxTemperature` (Tagesprognose!),
  `humidity`, `windSpeed`, `windDirection`, `weatherCondition`, `weatherDayTime`, `vaporAmount`.
- `home.location` hat `city`, `latitude`, `longitude` – **als Strings**. Werte nie ins Repo/Logs.
- **Keine Kompression:** Die Cloud ignoriert `Accept-Encoding: gzip`. `getCurrentState` überträgt
  ~100 KB unkomprimiert (Log: `encoding=none wire=99606`). Das heißt: Hintergrund alle 15 min
  ≈ 9,5 MB/Tag; geöffnete App mit 60-s-Polling ≈ 6 MB/Stunde. → Schritt 2 ist wichtig.
- Lücken im Verlauf vom 2026-11-01 nachmittags stammen von `am force-stop` beim Entwickeln
  (das pausiert WorkManager-Jobs bis zum nächsten App-Start) – kein Fehler der App.

## Offen: am Gerät prüfen

1. Außen-Linie in der Detailansicht nach einigen Aufzeichnungen (erste `weather_sample`-Zeilen).
2. Detailansicht im Dunkelmodus und mit 7/14/60 Tagen, sobald genug Daten da sind.
3. Noch nie am echten System ausgeführt: „Profil aktivieren“ (`setActiveProfile`),
   „Letzte Speicherung rückgängig“, „auf andere Räume übernehmen“.

## Schritt 2 – Akkumessung und -optimierung (umgesetzt 2026-11-01, Messung offen)

Erledigt:
- **Vordergrund:** Abruf alle **5 min** (vom Nutzer so gewünscht), „Aktualisieren“ sofort.
- **Hintergrund adaptiv:** `HistoryWorker` ist eine sich selbst neu planende Kette aus
  `OneTimeWorkRequest`s (`history-chain`, `APPEND_OR_REPLACE` aus dem Worker heraus).
  `PollingPolicy` (reines Kotlin, getestet) wählt den Abstand anhand der letzten zwei
  Aufzeichnungen: **15 min**, wenn sich ein Ventil um ≥ 10 % oder ein Sollwert um ≥ 0,25 K
  geändert hat; sonst **30 min**, nachts (22–05 Uhr) **60 min**.
- `HistoryWatchdogWorker` (stündlich, ohne Netz) startet die Kette neu, falls sie abreißt.
  Der alte feste 15-min-Job (`history`) wird beim Start entfernt.
- Bedingungen: Netz vorhanden **und Akku nicht niedrig**.
- Am Gerät geprüft: erzwungener Lauf (`adb shell cmd jobscheduler run -f -n
  androidx.work.systemjobscheduler de.cplagens.pureheat <jobId>`) → Abruf →
  „next run in 30 min“ im Log → genau ein Folgeauftrag eingeplant.
- Erwartung: statt 96 nun ca. 40–60 Abrufe/Tag → ca. 4–6 MB/Tag statt 9,5 MB.

Offen:
1. **Messen** (nach 1–2 Tagen): Anzahl Abrufe aus dem Log
   (`adb logcat -s PureHeat | grep "next run"`) bzw. Datenverbrauch der App in den
   Android-Einstellungen (Apps → PureHeat → Mobile Daten/WLAN) und Akku
   (`adb shell dumpsys batterystats --charged de.cplagens.pureheat`; Telefon hängt meist am
   Ladegerät, daher ist der Datenverbrauch die aussagekräftigere Größe). Ergebnis hier eintragen.
2. **Profilbewusst abrufen** (sobald Profile im Nachtjob geladen werden, Schritt 3/5):
   rund um Umschaltzeiten des aktiven Profils 15 min, damit das Modell genau diese Übergänge
   sieht. Dazu `PollingPolicy` um „nächste Umschaltzeit“ erweitern.
3. **Nachtjob** (`PeriodicWorkRequest` 24 h, `setRequiresCharging(true)`,
   `setRequiresDeviceIdle(true)`) – bewusst erst mit Schritt 3 anlegen, wenn er Arbeit hat
   (Modell anpassen, Prognose holen, Vorhersagen/Profil berechnen).
4. Keine WebSocket-Dauerverbindung auf dem Telefon (wäre etwas für den Raspberry Pi).
5. Schlankere Abfragen gibt es laut homematicip-rest-api nicht; seltener abrufen bleibt der Hebel.
6. Kleinigkeit: Jeder neue Worker-Prozess macht zuerst einen Lookup (`getHost`, 223 Bytes) –
   vernachlässigbar; könnte man in SharedPreferences cachen.

## Schritt 3 – Raummodell (Aufheiz-/Abkühlzeiten in der Übersicht)

Einfaches Wärmemodell 1. Ordnung pro Raum (T = Raum, Tₐ = außen):

- Abkühlen (Ventil zu): `dT/dt = −k·(T − Tₐ)` → je Abkühlphase `k = −rate / (T̄ − T̄ₐ)`
- Aufheizen (Ventil offen): `dT/dt = P − k·(T − Tₐ)` → je Aufheizphase `P = rate + k·(T̄ − T̄ₐ)`
- Robust schätzen: Median über alle Phasen der letzten 60 Tage; zusätzlich **Totzeit** `d`
  (Estrich!): Zeit vom Öffnen des Ventils bis die Temperatur um > 0,1 K steigt.
- Vorhersagen:
  - Aufheizen T₀ → T₁: `t = d + (1/k)·ln((P − k(T₀−Tₐ)) / (P − k(T₁−Tₐ)))`;
    wenn `P ≤ k(T₁−Tₐ)` → „Ziel bei dieser Außentemperatur nicht erreichbar“.
  - Abkühlen T₀ → T₁ (T₁ > Tₐ): `t = (1/k)·ln((T₀−Tₐ) / (T₁−Tₐ))`.
- Neue reine Klasse z. B. `data/model/RoomModel.kt` (`fit(phases)`, `heatUpTime(...)`,
  `coolDownTime(...)`) mit Unit-Tests auf synthetischen Daten.
- `ThermalPhase` braucht dafür zusätzlich die mittlere Raumtemperatur (T̄) – ergänzen.
- **Übersicht:** pro Raumkarte eine Zeile, z. B. „15 → 24 °C in ca. 7 h · 24 → 15 °C in ca. 16 h
  (bei 10 °C außen)“; Ausgangs-/Zielwerte aus dem aktiven Heizprofil (Grundtemperatur ↔
  Phasentemperatur). Vor genügend Daten (z. B. < 3 Phasen je Art): „lernt noch …“.
- Detailansicht: Modellwerte (k, P, Totzeit, Anzahl Phasen) und eine Güteangabe.
- Hinweis: Der Raum „Zimmer 2“ heizte im ersten Verlauf nur mit ~0,09 °C/h (Ventilstift war
  festgesessen, wurde am 2026-11-01 gelöst) – frühe Daten dieses Raums sind nicht repräsentativ.

## Schritt 4 – Wetterprognose

- `home.location` parsen (lat/lon, lenient), runden auf 2 Nachkommastellen.
- Open-Meteo: `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&hourly=temperature_2m&forecast_days=3&timezone=auto`
- Neue Tabelle `forecast_sample(timestamp, temperature, fetchedAt)`; DB-Migration 2 → 3.
- Abruf im Nachtjob; ohne Prognose mit aktueller Außentemperatur rechnen.

## Schritt 5 – Probelauf mit Trefferquote

- Nachtjob erstellt pro Raum Vorhersagen für den nächsten Tag aus dem **aktuell aktiven Profil**:
  für jeden Wechsel Grund → Phase „Zieltemperatur erreicht um …“ und für jeden Wechsel
  Phase → Grund „Temperatur um … bei …“. Außerdem: wann *würde* die Optimal-Steuerung starten.
- Tabelle `prediction(roomId, createdAt, targetTime, targetTemperature, predictedReachTime, actualReachTime?, ...)`.
- Am Folgetag mit `room_sample` auswerten → Fehler in Minuten, „pünktlich ±0,5 K“ ja/nein.
- Anzeige pro Raum: „Vorhersage im Schnitt X min daneben, Ziel zu Y % pünktlich (14 Tage)“.
- Schalter „Optimal-Steuerung aktivieren“ erst ab Schwelle (Vorschlag: ≥ 14 Tage Daten,
  ≥ 80 % pünktlich, mittlerer Fehler ≤ 30 min). Der Nutzer schaltet selbst ein.

## Schritt 6 – Komfortzeiten und Optimal-Profil

- Eingabe pro Raum: Komfortfenster je Wochentag (Start, Ende, Temperatur) + Spartemperatur.
  Tabelle `comfort_window`.
- Berechnung pro Fenster: Start = Fensterbeginn − Aufheizzeit(Grund → Ziel, Prognose) − Puffer;
  Ende = Fensterende − Auskühlzeit für 0,5 K (Restwärme Estrich). Überlappende zusammenfassen,
  max. 6 Phasen/Tag (`ProfileEditing.MAX_PERIODS`), 15-min-Raster.
- Erst als **Vorschlag** mit Änderungsliste (`ProfileEditing.diff`) → normaler Speichern-Weg.
- Automatik (nach Opt-in): Nachtjob schreibt nur bei Änderung, max. 1×/Tag, Protokoll-Tabelle,
  Rückgängig über vorhandene Backups.
- Hinweis an den Nutzer: Homematic hat eine eingebaute Funktion „Optimum Start/Stop“
  (beim Nutzer aus) – nicht gleichzeitig mit der App-Automatik betreiben.

## Schritt 7 – Ersparnis

- **Ventil-Stunden** (Ventilöffnung × Zeit, summiert über alle Kanäle) als Maß für Heizzeit.
- **Simulation:** Mit dem Raummodell nachrechnen, wie das alte Profil am selben Tag mit
  derselben Außentemperatur gelaufen wäre → gesparte Ventil-Stunden und niedrigere mittlere
  Raumtemperatur.
- **Wetterbereinigung** über Heizgradtage (z. B. Basis 15 °C).
- Optional kWh/€: Nutzer trägt gelegentlich den Stand des Wärmemengenzählers (Qundis, in der
  Wohnung vorhanden) und den Preis ein → Kalibrierung Ventil-Stunden ↔ kWh.
- Anzeige z. B. „Diese Woche ca. 18 % weniger Heizzeit, Komfortzeiten zu 92 % eingehalten“.

## Später – Raspberry Pi

Modell/Prognose/Profilberechnung in ein JVM-Modul `:core` auslagern, auf dem Pi als Dienst
laufen lassen (dort ggf. WebSocket für Echtzeit-Ereignisse). Das Telefon zeigt dann nur noch an.
Erst angehen, wenn die Steuerung auf dem Telefon zuverlässig läuft.

## Arbeitsweise / Hinweise für Agents

- Nach jeder Änderung bauen und testen: `./gradlew assembleDebug testDebugUnitTest`.
- In Android Studio brechen lange Shell-Aufrufe manchmal mit Timeout ab → Gradle im Hintergrund
  starten (`runInBackground`) und auf das Ende warten; installieren notfalls direkt mit
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Neue API-Felder immer erst am echten Dump prüfen (`files/last_state.json`), nie raten –
  die Bibliothek weicht teils ab (`baseValue`, `profileIndex`).
- Schreib-Experimente gegen die Cloud nur mit unverändertem Inhalt (No-op) oder nach
  ausdrücklicher Zustimmung des Nutzers.
- Keine echten IDs, Standorte, SGTIN oder Tokens in Repo, Tests oder Dokumente.
