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

## Auswertung der ersten 28 h Verlauf (2026-11-02)

- **Android drosselt die Hintergrundjobs** über App-Standby-Buckets: im Laufe des Tages
  ACTIVE → WORKING_SET (10:08) → FREQUENT (13:46) → RARE (18:08). Folge: tagsüber 60 statt
  30 min, dann **9 h ohne Aufzeichnung** (10:34–19:35). Nur 36 Aufzeichnungen in 28 h.
  Abhilfe in der App: Hinweis-Karte (`ui/BatteryHint.kt`) → App-Info → Akku →
  „Nicht eingeschränkt“ (`isIgnoringBatteryOptimizations`). Danach erneut messen.
- **Lookup-Dienst antwortete einmal 418** (zu viele Lookups: jeder Prozessstart machte einen).
  REST-URL wird jetzt in SharedPreferences `hmip_server` gecacht, neuer Lookup nur nach Fehler.
- **Physik plausibel** (Zimmer 2 nach dem Lösen des Ventilstifts): nachts Ventil zu,
  23,2 → 21,0 °C in 8 h bei ~7 °C außen (k ≈ 0,02/h); morgens ab 03:00 Ventil offen, Raum
  kühlt noch bis ~04:30 weiter (Totzeit ~1,5 h), dann 20,6 → 22,8 °C in 4 h (~0,55 K/h,
  P ≈ 0,8 K/h); Soll 23 °C gegen 10:30 erreicht.
- Übrige Räume: Schlafzimmer kühlen nachts mit k ≈ 0,017–0,026/h ab. Bad und WC liegen
  ganztägig bei 25–27 °C über Soll (Handtuchheizkörper mit Soll 26–27 °C heizen den Raum),
  Küche/Wohnzimmer bleibt ohne Heizen bei 23–24 °C → diese Räume liefern kaum
  „Ventil offen“-Phasen; das Modell wird dort lange „lernt noch“ zeigen.
- **Vorhersage verbessern (Vorschlag, noch offen):** „Spar → Komfort“ (z. B. 15 → 24 °C)
  ist unrealistisch, weil die Räume nachts nur auf ~21 °C absinken. Besser:
  Aufheizen ab der *typischen Temperatur am Ende der Absenkung* (Median der Ist-Temperatur
  beim Sollwert-Anstieg) und Abkühlen als „nach X h Absenkung noch ca. Y °C“
  (`T(t) = Tₐ + (T₀ − Tₐ)·e^(−k t)`, X = typische Absenkdauer).
- Übersichtsdiagramm: Ist/Soll jetzt als durchgehende Linien über Aufzeichnungslücken
  hinweg; die Detailansicht zeigt Lücken weiterhin.

## Mehrsprachigkeit und Hilfe (2026-11-02)

- Umgesetzt: alle Texte in Ressourcen, Englisch (Standard) + Deutsch, Systemsprache wird befolgt,
  Sprachwahl im Info-Dialog und (Android 13+) in den System-„App-Sprachen“, Hilfe-Bildschirm.
- **Offen:** Übersetzungen wie in PureRemote für **fr, es, it, pt, nl, pl, tr, ru, ja, zh-CN**:
  je eine `values-<tag>/strings.xml` mit allen Schlüsseln aus `values/strings.xml` (inkl. Plurals –
  für ru/pl die Formen one/few/many/other, für ja/zh nur other), dann die Sprache in `Lang.kt` und
  `res/xml/locales_config.xml` eintragen. Mit `./gradlew lintDebug` auf `MissingTranslation`/
  `StringFormatInvalid` prüfen. Bis dahin bietet die App nur en/de an.
- Auf dem Gerät prüfen: Sprachwechsel im Info-Dialog (Activity wird neu aufgebaut), englische
  Oberfläche, Hilfe-Bildschirm.

## KI-Modell für Heizverhalten und Tagesplan (Einschätzung 2026-11-02)

Frage des Nutzers: Wäre ein KI-Modell sinnvoll, um das Heizverhalten und den Plan für den
nächsten Tag besser zu lernen? Einschätzung: **vorerst nicht als Ersatz, später als Ergänzung.**

- Datenlage: wenige Wochen, 8 Räume, 15–60-min-Punkte, Außentemperatur nur 6–8 °C bisher. Ein
  physikalisches Modell mit 3 Parametern (k, P, Totzeit) lernt daraus robust; ein neuronales Netz
  bräuchte Monate inkl. Winter und wäre nicht erklärbar.
- Erklärbarkeit ist Voraussetzung für das Vertrauen (Probelauf): „Start 04:30, weil k=0,02 und
  6 °C außen“ ist prüfbar, eine Black-Box-Vorhersage nicht.
- Sinnvolle Zwischenstufe („Grey Box“): das Physikmodell um gelernte Korrekturen erweitern –
  z. B. Tageszeit-/Wochentagseffekte (Kochen, Sonne, Bewohner), Wind, Sonneneinstrahlung
  (Wetterprognose), Nachbarräume; per Regression auf die Residuen (Vorhersagefehler) des
  Physikmodells. Läuft auf dem Telefon ohne Cloud.
- Typische Nutzungsmuster (wann ist wer zu Hause, wann sind Fenster offen) lassen sich statistisch
  aus dem Verlauf ableiten und als Vorschlag für Komfortzeiten anbieten.
- Ein großes Sprachmodell (LLM) ist für die Regelung ungeeignet, könnte aber später Erklärungen
  formulieren („Warum wurde heute früher geheizt?“) – nur mit ausdrücklicher Zustimmung, da Daten
  dann das Gerät verlassen würden (oder on-device, z. B. Gemini Nano).
- Entscheidung: Schritte 3–6 wie geplant mit dem Physikmodell; im Probelauf (Schritt 5) die
  Residuen speichern, damit später eine Grey-Box-Korrektur gelernt und gegen das reine Modell
  verglichen werden kann.

## Fenster-offen-Erkennung (2026-11-02)

Nutzer-Info: In den Zimmern 1–3 sind nachts die Fenster offen. Es gibt keine Fensterkontakte.

- `data/history/WindowDetection.kt` (reines Kotlin, `WindowDetectorTest`): **geöffnet**, wenn bei
  geschlossenem Ventil die Temperatur zwischen zwei Messpunkten um ≥ 0,3 K mit ≥ 0,6 K/h fällt
  (oder ≥ 0,2 K zusammen mit ≥ 4 %rH Luftfeuchte-Abfall); **geschlossen** bei Wiederanstieg
  ≥ 0,2 K ohne Heizen, sonst Ende bei Heizbeginn, Lücke > 2,5 h oder nach 16 h.
- Luftfeuchte wird seit DB-Version 3 je Raum gespeichert (`room_sample.humidity`).
- `PollingPolicy`: Temperaturänderung ≥ 0,3 K zählt als „aktiv“ → 15-min-Abruf, damit der
  Sturz beim Öffnen sichtbar wird.
- Modell: Fenster-offen-Zeiten werden aus den Phasen ausgeklammert (`withoutWindowTimes`).
- Anzeige: hellblaues Band in Übersicht und Detailansicht, Zeile „Fenster vermutlich offen …“,
  Liste in der Detailansicht, Hinweis in der Modellkarte.
- **Erste Prüfung mit echten Daten (Nacht 1.–2.11., nur stündliche Messpunkte):**
  Zimmer 2 erkannt 20:51–01:11 (−1,6 K; Ende wegen Heizbeginn 03:00 – Fenster war vermutlich
  länger offen), Zimmer 3 erkannt 23:10–07:31 (−1,9 K), **Zimmer 1 nicht erkannt** (gleichmäßig
  ~0,3 K/h, kein Sturz sichtbar).
- Offen / Ideen:
  1. Nach Aufhebung der Akku-Einschränkung (dichtere Messpunkte) erneut prüfen, v. a. Zimmer 1.
  2. Zweites Kriterium, sobald k für geschlossene Fenster bekannt ist: Abkühlung deutlich
     schneller als vom Modell erwartet (z. B. > 3×) → Fenster offen, auch ohne Sturz.
  3. Luftfeuchte-Kriterium an echten Daten kalibrieren (Abfall beim Öffnen? Anstieg beim Schließen?).
  4. Ende besser erkennen, wenn morgens die Heizung startet, solange das Fenster noch offen ist
     (Ventil offen, Temperatur fällt weiter → Fenster noch offen).
  5. Hinweis an den Nutzer, wenn das Heizprofil bei offenem Fenster heizt (Zimmer 2: Heizbeginn
     03:00, Fenster vermutlich noch offen → Energie verloren).

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

## Schritt 3 – Raummodell (umgesetzt 2026-11-01, lernt jetzt mit echten Daten)

Wärmemodell 1. Ordnung pro Raum (T = Raum, Tₐ = außen, Zeiten in Stunden), Code in
`data/model/RoomModel.kt` (reines Kotlin):

- Ventil zu: `dT/dt = −k·(T − Tₐ)` → je Phase `k = −rate / (T̄ − T̄ₐ)`. Nur Phasen mit
  `T̄ − T̄ₐ ≥ 3 K` und fallender Temperatur (Ventil zu + steigende Temperatur, z. B. durch
  Handtuchheizkörper, sagt nichts über den Verlust).
- Ventil offen: `dT/dt = P − k·(T − Tₐ)` → `P = rate + k·(T̄ − T̄ₐ)`, ausgewertet nur im
  **freien Aufheizen**: vom Tiefpunkt nach der Totzeit (`ThermalPhase.responseStart`) bis die
  Temperatur 0,3 K unter dem Sollwert ist (`heatUpEnd`); danach hält das Ventil nur noch.
- **Totzeit** `d` = Phasenbeginn bis Tiefpunkt (der Raum kühlt weiter, während der Estrich
  aufheizt). Alles als Median; Streuung von k als (Q3−Q1)/Median.
- Vorhersagen (`RoomModel.heatUp/coolDown` → `Prediction.Hours | Unreachable | Unknown`):
  - Aufheizen: `t = d + (1/k)·ln((P − k(T₀−Tₐ)) / (P − k(T₁−Tₐ)))`, nicht erreichbar
    wenn `P ≤ k(T₁−Tₐ)`.
  - Abkühlen: `t = (1/k)·ln((T₀−Tₐ) / (T₁−Tₐ))`.
- **Vertrauenswürdig** ab je 3 verwertbaren Phasen (`RoomModel.MIN_PHASES`), sonst zeigt die
  App „Raummodell lernt noch (Ventil offen x/3, Ventil zu y/3)“.
- **Spar-/Komforttemperatur** kommen aus den aufgezeichneten Sollwerten der letzten 7 Tage
  (10./90. Perzentil, `SetPointLevels`) – keine zusätzlichen API-Aufrufe; kurze manuelle
  Änderungen fallen heraus. Außentemperatur = letzter aufgezeichneter Wert.
- Berechnet in `HeatingRepository.roomInsights()` (60 Tage, `Dispatchers.Default`), im
  Dashboard höchstens alle 30 min neu; die Detailansicht zeigt k, P, Totzeit, Phasenanzahl,
  Streuung und die Spar-/Komforttemperatur. Ein Nachtjob ist dafür (noch) nicht nötig.
- Unit-Test simuliert einen Raum mit bekanntem k, P, Totzeit und prüft, dass die Anpassung
  sie wiederfindet (`RoomModelTest`).

Offen / beobachten:
1. Nach einigen Tagen prüfen, ob die Werte plausibel sind (Detailansicht → Raummodell).
   Typisch für Fußbodenheizung: k ≈ 0,02–0,08/h, P ≈ 0,2–1 K/h, Totzeit 0,5–3 h.
2. Der Raum „Zimmer 2“ heizte anfangs nur mit ~0,09 °C/h (Ventilstift saß fest, am
   2026-11-01 gelöst) – Daten von vor dem 2026-11-02 dieses Raums ggf. ignorieren.
3. Räume, deren Ventil fast nie ganz zu ist, liefern evtl. nie „Ventil zu“-Phasen → dann
   k aus Phasen mit kleinem Ventilwert schätzen (Erweiterung).
4. Später (Schritt 5/6) Spar-/Komfortwerte direkt aus dem aktiven Heizprofil nehmen.

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
