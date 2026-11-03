# PureHeat – Status (Einstieg für jede neue Session)

Stand: 2026-11-03. Diese Datei kurz halten und am Ende jeder Aufgabe **ersetzen** (nicht anhängen).
Details je Schritt: `NEXT_STEPS.md` (Abschnitt per Grep suchen), Messungen: `FINDINGS.md`.

## Wo wir stehen
- Läuft auf dem Telefon des Nutzers (Samsung, adb-Seriennummer `R5CY81ZW5EZ`, oft nur zeitweise
  per USB verbunden – vor `adb`-Befehlen `adb devices` prüfen).
- Fertig: Dashboard, Verlauf (Room-DB, **2 Jahre** Aufbewahrung, Modell nutzt 60 Tage),
  Detailansicht (Bereiche über „Ansicht“ ein-/ausblendbar, Standard alles), Heizprofile bearbeiten,
  Raummodell (k, P, Totzeit), Fenster-offen-Erkennung (vom Nutzer als sehr gut bestätigt),
  Wetterprognose, Probelauf (Schritt 5, sammelt seit 2026-11-02), Hilfe, 12 Sprachen,
  **Raumeigenschaften** per Text/Diktat → Gemini Nano auf dem Gerät oder Stichwörter → Startwerte
  fürs Modell.
- Aufzeichnung seit Akku „Nicht eingeschränkt“ lückenlos (siehe FINDINGS 2026-11-03).
- Letzter Commit vor dieser Session: `d86de37` (gepusht). Diese Session (noch nicht committet bzw.
  gepusht – `git log` prüfen): Übersetzungen, durchgehende Kurven in der Detailansicht,
  Raumeigenschaften, Kotlin 2.4.21 + ML Kit genai-prompt, 2 Jahre Aufbewahrung, konfigurierbare
  Detailansicht, schlankere Doku (diese Datei, CLAUDE.md).

## Nächste Aufgaben
1. **Am Gerät prüfen** (App-Version dieser Session ist evtl. noch nicht installiert):
   Raumeigenschaften (Ist Gemini Nano verfügbar? Diktieren? Erkennung deutscher Sätze?),
   „Ansicht“-Dialog, durchgehende Kurven, Sprachwechsel auf eine der neuen Sprachen.
2. Probelauf-Ergebnisse nach einigen Tagen ansehen (Trefferquote je Raum; Startwerte vs. gelernt).
3. Schritt 6: Komfortzeiten + Optimal-Profil als Vorschlag (NEXT_STEPS „Schritt 6“); Nutzung
   aus den Raumeigenschaften (Schlafzimmer, Bad …) als Vorschlag für Komfortzeiten.
4. Sonnengewinn: Open-Meteo `shortwave_radiation` + Fensterrichtung (NEXT_STEPS „Raumeigenschaften“).
5. Für KI-Training (später, nur Simulationsmodus): Export der Verlaufsdaten (CSV) überlegen.

## Offene Prüfungen am Gerät (älter)
- Nie am echten System ausgeführt: „Profil aktivieren“, „Letzte Speicherung rückgängig“,
  „auf andere Räume übernehmen“.
- Akku/Datenverbrauch messen (NEXT_STEPS „Schritt 2“).
