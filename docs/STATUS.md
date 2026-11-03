# PureHeat – Status (Einstieg für jede neue Session)

Stand: 2026-11-03 nachts. Diese Datei kurz halten und am Ende jeder Aufgabe **ersetzen** (nicht
anhängen). Details je Schritt: `NEXT_STEPS.md` (Abschnitt per Grep suchen), Messungen: `FINDINGS.md`,
offene Review-Befunde: `REVIEW.md` (falls vorhanden). „mach weiter“ = erste offene Aufgabe unten.

## Wo wir stehen
- Läuft auf dem Telefon des Nutzers (Galaxy S25 Ultra, adb `R5CY81ZW5EZ`, nur zeitweise per USB –
  vorher `adb devices`; UI nur bedienen, wenn `mCurrentFocus` Launcher/PureHeat ist).
- Fertig: Dashboard (Raum-Reihenfolge: Thermostate hinter ihrem Raum, selbst anordnen),
  Verlauf (2 Jahre, Modell 60 Tage), Detailansicht (Bereiche ausblendbar), Heizprofile bearbeiten
  und **umbenennen**, Raummodell, Fenster-offen- und **Duschen/Baden-Erkennung**, Prognose,
  Probelauf, Hilfe, 12 Sprachen, Raumeigenschaften (Gemini Nano läuft, inkl. Dusche/Badewanne).
- Open Source: Lizenzkopf in allen Kotlin-Dateien, `LICENSE.md`, `NOTICE`, `CONTRIBUTING.md`,
  `ReleaseNotes.md`, README verlinkt alles; Repo-Link in Hilfe und Info.
- Skill `/pureheat-review`, „mach weiter“-Regel in CLAUDE.md.

## Nächste Aufgaben
1. **Am Gerät prüfen** (neue Version ist installiert, falls Telefon verbunden war – sonst
   installieren): Profil umbenennen (erster echter Test des undokumentierten Wegs! Meldet die App
   „nicht übernommen“, Ergebnis in FINDINGS eintragen und ggf. auf lokale Namen umstellen),
   Reihenfolge-Dialog, Duschen/Baden-Markierung im Bad (Bad muss als Bad/Dusche beschrieben sein).
2. Nutzer-Hinweis: Bad-Beschreibung neu erkennen + speichern (gespeichert noch „Erdgeschoss“).
3. Erstes Review mit `/pureheat-review full`, Befunde in `docs/REVIEW.md`.
4. Probelauf-Ergebnisse nach einigen Tagen ansehen (Trefferquote je Raum).
5. Schritt 6: Komfortzeiten + Optimal-Profil als Vorschlag (NEXT_STEPS „Schritt 6“); Nutzung und
   Duschzeiten als Hinweis für Komfortzeiten im Bad.
6. Sonnengewinn: Open-Meteo `shortwave_radiation` + Fensterrichtung.
7. Für KI-Training (später, nur Simulationsmodus): Export der Verlaufsdaten (CSV) überlegen.

## Kleinigkeiten
- Detail-Temperaturdiagramm: y-Beschriftung „28 °C“ liegt über der Ist-Linie.
- Übersicht: Ereignis-Beschriftungen über dem Diagramm stehen teils dicht/überlappend.

## Offene Prüfungen am Gerät (älter)
- Nie am echten System ausgeführt: „Profil aktivieren“, „Letzte Speicherung rückgängig“,
  „auf andere Räume übernehmen“. Akku/Datenverbrauch messen (NEXT_STEPS „Schritt 2“).
