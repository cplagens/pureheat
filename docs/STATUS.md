# PureHeat – Status (Einstieg für jede neue Session)

Stand: 2026-11-04. Diese Datei kurz halten und am Ende jeder Aufgabe **ersetzen** (nicht anhängen).
Details je Schritt: `NEXT_STEPS.md` (Abschnitt per Grep suchen), Messungen: `FINDINGS.md`, offene
Review-Befunde: `REVIEW.md` (falls vorhanden). „mach weiter“ = erste offene Aufgabe unten.

## Wo wir stehen
- Läuft auf dem Telefon des Nutzers (Galaxy S25 Ultra, adb `R5CY81ZW5EZ`, nur zeitweise per USB –
  vorher `adb devices`; UI nur bedienen, wenn `mCurrentFocus` Launcher/PureHeat ist).
  Stand vom 04.11. (Commit `334b6e7`) ist installiert, aber noch nicht durchgeklickt.
- Fertig: Dashboard (Reihenfolge), Verlauf (2 Jahre), Detailansicht, Heizprofile (bearbeiten,
  umbenennen), Raummodell, Fenster- und Duschen/Baden-Erkennung, Prognose, Probelauf, Hilfe,
  12 Sprachen, Raumeigenschaften (Gemini Nano), **Export/Import** (Info → Daten), Ziel-API 36,
  Open-Source-Dateien, **Heizprofil aus Text/Diktat** (Nano oder Stichwörter, nur Vorschlag),
  Raumbeschreibung begrenzt Erkennung (kein Fenster → keine Fenster-offen-Erkennung; Bad ohne
  Dusche/Wanne → kein Duschen/Baden).
- Play-Store-Anleitung: `PLAY_STORE_GUIDE.md` (lokal, in `.gitignore`). Release-Signatur aus
  `keystore.properties` eingebaut (ohne Datei unsigniert), `docs/privacy.md` (de + en) angelegt.

## Nächste Aufgaben
1. **Am Gerät prüfen**: Heizprofil aus Beschreibung (Nano-Antwort im Debug-Log `Nano:` ansehen,
   ggf. Prompt nachschärfen), Bad ohne Fenster-Markierung, Export teilen/als Datei speichern, Import der eigenen Datei,
   Profil umbenennen (undokumentierter Weg – Ergebnis in FINDINGS), Reihenfolge-Dialog,
   Duschen/Baden im Bad, alles mit targetSdk 36.
2. Vorbereitung Play Store (Checkliste in `PLAY_STORE_GUIDE.md`): **Demo-Modus** für Prüfer/Tester
   (nächste Agent-Aufgabe), Logo 512 px + Feature-Grafik. Beim Nutzer: Upload-Keystore +
   `keystore.properties` anlegen, GitHub Pages für `/docs` einschalten, Kontakt in `privacy.md`
   bestätigen, Spenden-Links im Play-Build ja/nein entscheiden, `.github/FUNDING.yml` erst wenn
   GitHub Sponsors freigeschaltet ist.
3. Erstes Review mit `/pureheat-review full`, Befunde in `docs/REVIEW.md`.
4. Nutzer-Hinweis: Bad-Beschreibung neu erkennen + speichern (gespeichert noch „Erdgeschoss“).
5. Probelauf-Ergebnisse ansehen; Schritt 6 (Komfortzeiten + Optimal-Profil als Vorschlag).
6. Sonnengewinn (Open-Meteo `shortwave_radiation` + Fensterrichtung).

## Kleinigkeiten
- Detail-Temperaturdiagramm: y-Beschriftung „28 °C“ liegt über der Ist-Linie.
- Übersicht: Ereignis-Beschriftungen über dem Diagramm stehen teils dicht/überlappend.

## Offene Prüfungen am Gerät (älter)
- Nie am echten System ausgeführt: „Profil aktivieren“, „Letzte Speicherung rückgängig“,
  „auf andere Räume übernehmen“. Akku/Datenverbrauch messen (NEXT_STEPS „Schritt 2“).
