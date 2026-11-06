# PureHeat – Erkenntnisse

[English](FINDINGS.md) · **Deutsch**

Was wir am echten System gelernt haben (Stand 2026-11-02). Ergänzt [ARCHITECTURE.de.md](ARCHITECTURE.de.md)
(Aufbau) und [NEXT_STEPS.md](NEXT_STEPS.md) (Plan, Englisch). Neue Erkenntnisse bitte hier mit Datum eintragen.
Keine IDs, Standorte oder Personennamen eintragen – das Repo ist öffentlich.

## Homematic IP Cloud-API

| Thema | Erkenntnis | Seit |
|---|---|---|
| `getCurrentState` | Body muss `{clientCharacteristics, id}` sein, nur `clientCharacteristics` → 400 | 2026-11-01 |
| Größe | ~100 KB pro Abruf, **keine Kompression** (`Accept-Encoding: gzip` wird ignoriert) | 2026-11-01 |
| Lookup | `getHost` antwortet für jede SGTIN mit 200; bei zu vielen Lookups kurz **418** → REST-URL cachen | 2026-11-02 |
| Profile lesen | `getProfile` mit `{groupId, profileIndex, profileId}`; Antwort `{id, groupId, homeId, type, profileDays}` | 2026-11-01 |
| Grundtemperatur | heißt **`baseValue`** (homematicip-rest-api dokumentiert `baseTemperature`) | 2026-11-01 |
| Profile schreiben | `updateProfile` braucht **`profileIndex` auf oberster Ebene**, sonst 400 INVALID_REQUEST; Rückgabe 200 ohne Body | 2026-11-01 |
| Wetter | `home.weather`: `temperature`, `minTemperature`, `maxTemperature`, `humidity`, `windSpeed`, … | 2026-11-01 |
| Standort | `home.location`: `city`, `latitude`, `longitude` – als **Strings** | 2026-11-01 |
| Unbelegte Kanäle | Fußbodenheizungsaktor meldet unbelegte Ausgänge ohne Gruppe und ohne `valvePosition` (`ADJUSTMENT_TOO_SMALL`) | 2026-11-01 |
| Fensterkontakte | keine vorhanden → `windowState` immer null; Fenster nur über den Temperaturverlauf erkennbar | 2026-11-02 |
| Lookup-Cache | REST-URL in SharedPreferences: nach Prozess-Neustart kein `getHost` mehr (am Gerät bestätigt) | 2026-11-02 |
| Luftfeuchte | `groups[*].humidity` (ganzzahlig, % rF) je Raum; seit DB v3 aufgezeichnet | 2026-11-02 |

## Open-Meteo

- Antwort mit `timeformat=unixtime` und `timezone=UTC`: `hourly.time[]` (Sekunden) und
  `hourly.temperature_2m[]` (einzelne Werte können `null` sein). ~5 KB für 3 Tage.
- Erste Prognose am 2026-11-02 abends: nächste 24 h 4–10 °C (gemessen 6,7 °C) – plausibel.

## Android

- **App-Standby-Buckets bremsen Hintergrundjobs massiv.** Ein selten geöffneter App wird
  stufenweise herabgestuft (ACTIVE → WORKING_SET → FREQUENT → RARE). Gemessen: tagsüber 60 statt
  30 min Abstand, dann 9 h ganz ohne Aufzeichnung. Abhilfe: Akku → „Nicht eingeschränkt“ (die App
  zeigt dafür eine Hinweiskarte). Diagnose: `adb shell dumpsys usagestats | grep pureheat`.
- **Wirkung von „Nicht eingeschränkt“ (gemessen 2026-11-03):** 66 Aufzeichnungen in 22 h
  (2.11. 19:35 – 3.11. 17:52), nachts wie geplant alle 60 min, tagsüber 15–42 min, keine Lücke
  über 62 min (vorher: 36 in 28 h mit 9 h Lücke).
- **DB-Wachstum:** ~380 `room_sample`- + ~330 `circuit_sample`-Zeilen/Tag, ~120 KB/Tag inkl.
  WAL → bei 2 Jahren Aufbewahrung ca. 90 MB (vertretbar; seit 2026-11-03 2 Jahre statt 60 Tage).
- **ML Kit `genai-prompt` 1.0.0-beta5 ist mit Kotlin 2.4 gebaut** – mit Kotlin 2.2 scheitert der
  Build („incompatible version of Kotlin“). Projekt daher auf Kotlin 2.4.21; `kotlinOptions`
  durch `kotlin { compilerOptions { jvmTarget } }` ersetzt. KSP 2.3.13 läuft damit.
- Lint (`StringFormatInvalid`) hält in Texten ohne Platzhalter „% a“, „% e“, „% H“ für
  Formatangaben – in Übersetzungen umformulieren („procent“, „(%)“).
- **Telefon des Nutzers (2026-11-03):** Galaxy S25 Ultra (SM-S938B), Android 17;
  `com.google.android.aicore` installiert und aktiv (Gemini Nano möglich), Google-App vorhanden
  (System-Spracheingabe für „Diktieren“). Ob Nano schon heruntergeladen ist, zeigt erst der erste
  Aufruf von „Eigenschaften erkennen“ (Hinweis unter dem Knopf; Debug-Log `Nano: …`).
- **Gemini Nano am Gerät getestet (2026-11-03):** verfügbar, Antwort in ~2 s, liefert JSON in
  einem ```json-Block (wird abgeschnitten). Schwächen: „1. Stock“ → `GROUND` (amerikanische
  Zählweise) und `false` für nicht erwähnte Ja/Nein-Felder statt `null`. Deshalb: Stichwort-Treffer
  gewinnen, Nano füllt Lücken; von Nano zählt bei Ja/Nein nur `true`.
- `am force-stop` pausiert alle WorkManager-Jobs bis zum nächsten App-Start (beim Entwickeln
  entstehen so Lücken).
- WorkManager-Jobs liegen im Namespace `androidx.work.systemjobscheduler`; zum Erzwingen:
  `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler de.cplagens.pureheat <jobId>`.
- Aus einem Worker heraus die eigene Kette mit `APPEND_OR_REPLACE` neu planen – `REPLACE` würde
  den laufenden Worker selbst abbrechen.

## Anlage und Gebäude (anonymisiert)

- Wohnung in einem Mehrfamilienhaus, Zentralheizung (Wärmemengenzähler im Rücklauf); kein
  Homematic-Gerät schaltet Pumpe oder Kessel. Vorlauftemperatur ist der App unbekannt.
- Ein Fußbodenheizungsaktor (8 Kanäle) an einem Verteiler; zwei Kreise ohne Stellantrieb
  (immer offen, je ~5 l/min) – vermutlich Handtuchheizkörper-Zuleitungen.
- **Festsitzender Ventilstift** in einem Kreis (Zimmer 2): Stellantrieb meldete 100 %, Raum wurde
  nicht warm (0,09 °C/h). Nach dem Lösen des Stifts am 2026-11-01: ~0,55 °C/h.
  → Lehre: „Ventil offen“ heißt nicht „Wärme kommt an“; die App zeigt deshalb „Ventil offen“
  statt „heizt“ und warnt, wenn das Ventil lange offen ist, der Raum aber nicht wärmer wird.
- **Gut gedämmt:** Mit geschlossenem Fenster bleibt die Temperatur abends über Stunden fast
  konstant (z. B. 23,2 °C von 19 bis 21 Uhr in Zimmer 2).
- **Nachts sind in den Zimmern 1–3 die Fenster offen** (Auskunft des Nutzers). Die nächtliche
  Abkühlung (k ≈ 0,017–0,026/h, 0,3–0,4 K/h) ist daher fast nur Lüftungsverlust, nicht der
  Wärmeverlust des Raums. Die App erkennt das am Temperatursturz und klammert diese Zeiten beim
  Lernen aus. Erste Nacht: Zimmer 2 und 3 erkannt, Zimmer 1 nicht (gleichmäßiges Abkühlen,
  kein Sturz sichtbar bei stündlichen Messpunkten).
- **Totzeit des Estrichs ~1,5 h:** Nach dem Öffnen des Ventils kühlt der Raum zunächst weiter;
  danach ~0,55 K/h Aufheizen (Zimmer 2), Heizleistung P ≈ 0,8 K/h.
- **Heizen bei offenem Fenster:** In Zimmer 2 startet das Profil um 03:00, während das Fenster
  vermutlich noch offen ist – ein Ansatzpunkt fürs Optimal-Profil (später heizen oder Hinweis).
- **Bad und WC** liegen ganztägig bei 25–27 °C über Soll: die Handtuchheizkörper (Soll 26–27 °C)
  heizen den Raum, die Fußbodenheizung läuft dort nie. **Küche/Wohnzimmer** bleibt ohne Heizen
  bei 23–24 °C. Diese Räume liefern kaum Aufheizphasen fürs Modell.
- Außentemperatur in den ersten Tagen nur 6–8 °C → das Modell sieht noch wenig Wetter-Variation.

## Modell und Auswertung

- Phasen „Ventil zu, Temperatur steigt“ (Bad/WC durch Handtuchheizkörper) sagen nichts über den
  Wärmeverlust und werden verworfen.
- Heizleistung nur im **freien Aufheizen** messen (ab Tiefpunkt nach der Totzeit bis 0,3 K unter
  Soll), sonst wird sie durch das „Temperatur halten“ unterschätzt.
- „Spar → Komfort“ aus den Sollwerten (z. B. 15 → 24 °C) ist unrealistisch, weil die Räume nachts
  nur auf ~21 °C absinken – besser von der typischen Morgentemperatur aus rechnen (siehe NEXT_STEPS).
- **Duschen/Baden in den Daten (2026-11-03):** Luftfeuchte-Sprünge zwischen zwei Messpunkten,
  z. B. +15 %rH in 5 min bei +0,3 K (Bad, abends), +5 %rH in 15 min bei +0,3 K, morgens/abends
  +4–5 %rH in weiteren Räumen. Danach fällt die Feuchte über ~1–2 h zurück. Daraus die Schwellen
  von `BathingDetector` (≥ 5 %rH bei ≥ 10 %rH/h, Temperatur fällt nicht). Kochen sieht ähnlich aus
  → Anzeige nur in Räumen mit Bad/Dusche/Wanne, aus dem Modell werden alle ausgeklammert.
- **Profilname:** `getProfile` liefert keinen Namen; er steht nur im Zustand
  (`groups[].profiles[].name`). Umbenennen probeweise über `updateProfile` mit `name` im
  Profilobjekt, geprüft per neuem `getCurrentState` – am echten System noch nicht ausprobiert.
- **Erste Probelauf-Auswertung (2026-11-03, 7 Vorhersagen, alle mit Startwerten):** 5 „zählt
  nicht“ (Fenster offen bzw. manuelle Änderung – erwartbar in den Zimmern mit Nachtlüftung),
  2 „früher“: beide als „nicht erreichbar“ vorhergesagt, aber erreicht (Ziel 25 bzw. 23 °C in
  Räumen, die von anderen Wärmequellen warm sind). Noch keine Aussage zur Genauigkeit möglich.
- Ereigniszeiten werden auf 15 min gerundet angezeigt, weil die Messpunkte bis zu 1 h auseinander
  liegen. „Fenster zu“ wird nur angezeigt, wenn das Schließen am Wiederanstieg erkannt wurde.
- **Duschen im echten Bad (2026-11-05):** 45 → 54 %rF zwischen 19:26 und 19:44 bei +0,3 K, nach
  ~2,5 h zurück auf 48–49 %; erkannt (Detailansicht: 19:35–22:18, +0,8 K). In der Übersicht wurde
  die Beschriftung von fünf früheren Sollwert-Beschriftungen verdrängt – behoben: erkannte
  Ereignisse werden zuerst platziert.
- **Gemini Nano, Heizprofil aus Text (2026-11-05):** „Werktags 5:30 bis 7:30 Uhr 23 Grad und abends
  19 bis 22 Uhr 23 Grad, am Wochenende 8 bis 11 Uhr 23 Grad, sonst 21 Grad“ → Zeiten richtig, aber
  `base` 23 in den Werktags-/Wochenend-Regeln und „sonst 21“ als eigene Regel für alle Tage mit leeren
  `periods`. `ProfileFromText.apply` fängt das ab (letzte Grundtemperatur je Tag gewinnt, Perioden
  werden zusammengeführt, eine leere Regel lässt sie stehen) → Ergebnis genau wie beschrieben.
  Antwort in ~2 s.
- **Fehlalarme Fenster offen (2026-11-06):** Temperaturstufen zwischen Messpunkten (alle Räume seit
  1.11.): 0,1 K ×177, 0,2 K ×128, **0,3 K ×213**, 0,4 K ×36 – manche Sensoren melden nur grobe Stufen.
  Zimmer 2: ~4 h unverändert 23,9 °C, dann 23,5 °C in 19 min bei Luftfeuchte 45 → 47 % (kein Fenster)
  → falsches „offen“, das auch das echte Öffnen ~1 h später verschluckte (23,5 → 23,2 °C, 47 → 43 %).
  Von 80 erkannten Öffnungen seit 1.11. fiel nur bei 26 die Luftfeuchte (38 gleich, 10 steigend).
  Mit Rate ab der letzten Wertänderung und „keine steigende Luftfeuchte“: noch 46; das echte Öffnen
  bleibt erkannt.
- **Absolute Feuchte bei Temperaturstürzen (2026-11-06):** Bei Stürzen ≥ 0,3 K mit geschlossenem
  Ventil fiel die absolute Feuchte im Mittel nur um 0,44 g/m³; viele Stürze hatten gar keinen Abfall
  (keine Außenluft → kein Fenster). Mit „Abfall ≥ 0,3 g/m³“ liegen die Öffnungen überwiegend
  abends/nachts in den Räumen mit Nachtlüftung (einzelne morgens); Zimmer 2 am 6.11.: 21:31 ✓,
  20:11 (−0,2 g/m³, also steigend) verworfen. 1 %rH ≈ 0,2 g/m³ bei 22–24 °C.
