# PureHeat – Status (Einstieg für jede neue Session)

Stand: 2026-11-03 abends. Diese Datei kurz halten und am Ende jeder Aufgabe **ersetzen** (nicht
anhängen). Details je Schritt: `NEXT_STEPS.md` (Abschnitt per Grep suchen), Messungen: `FINDINGS.md`,
offene Review-Befunde: `REVIEW.md` (falls vorhanden). „mach weiter“ = erste offene Aufgabe unten.

## Wo wir stehen
- Läuft auf dem Telefon des Nutzers (Galaxy S25 Ultra, adb `R5CY81ZW5EZ`, nur zeitweise per USB –
  vorher `adb devices`; UI nur bedienen, wenn `mCurrentFocus` Launcher/PureHeat ist).
- Fertig: Dashboard, Verlauf (2 Jahre, Modell 60 Tage), Detailansicht (Bereiche per „Ansicht“
  ausblendbar), Heizprofile bearbeiten, Raummodell, Fenster-offen-Erkennung, Prognose, Probelauf
  (seit 2026-11-02), Hilfe, 12 Sprachen, Raumeigenschaften (Gemini Nano läuft am Telefon).
- Am Gerät geprüft 2026-11-03: durchgehende Kurven, „Ansicht“-Dialog, Raumeigenschaften mit Nano.
  Daraus behoben: Stichwörter gewinnen vor Nano („1. Stock“), Nano-`false` = unbekannt,
  „über unbeheizter Tiefgarage“ erkannt. Aufzeichnung lückenlos.
- Neu: Skill `/pureheat-review` (Code-Review), „mach weiter“-Regel in CLAUDE.md.

## Nächste Aufgaben
1. Nutzer: Bad-Beschreibung einmal neu „erkennen“ + speichern (gespeichert ist noch „Erdgeschoss“,
   Text sagt „1. Stock“) – nur Hinweis, nicht selbst ändern.
2. Erstes Review mit `/pureheat-review full` laufen lassen, Befunde in `docs/REVIEW.md`.
3. Probelauf-Ergebnisse nach einigen Tagen ansehen (Trefferquote je Raum; Startwerte vs. gelernt).
4. Schritt 6: Komfortzeiten + Optimal-Profil als Vorschlag (NEXT_STEPS „Schritt 6“); Nutzung aus
   den Raumeigenschaften als Vorschlag für Komfortzeiten.
5. Sonnengewinn: Open-Meteo `shortwave_radiation` + Fensterrichtung (NEXT_STEPS „Raumeigenschaften“).
6. Für KI-Training (später, nur Simulationsmodus): Export der Verlaufsdaten (CSV) überlegen.

## Kleinigkeiten (gesehen beim Gerätetest)
- Detail-Temperaturdiagramm: y-Beschriftung „28 °C“ liegt über der Ist-Linie.
- Übersicht: Ereignis-Beschriftungen über dem Diagramm stehen teils dicht/überlappend.

## Offene Prüfungen am Gerät (älter)
- Nie am echten System ausgeführt: „Profil aktivieren“, „Letzte Speicherung rückgängig“,
  „auf andere Räume übernehmen“. Akku/Datenverbrauch messen (NEXT_STEPS „Schritt 2“).
