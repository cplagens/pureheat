# PureHeat – Status (Einstieg für jede neue Session)

[English](STATUS.md) · **Deutsch**

Stand: 2026-11-05. Diese Datei kurz halten und am Ende jeder Aufgabe **ersetzen** (nicht anhängen).
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
  Dusche/Wanne → kein Duschen/Baden), **Demo-Modus** (Einrichtung, offline Beispielwohnung;
  README-Screenshots daraus in `docs/screenshots/`).
- Play-Store-Anleitung: `PLAY_STORE_GUIDE.md` (lokal, in `.gitignore`). Release-Signatur aus
  `keystore.properties` eingebaut (ohne Datei unsigniert), `docs/privacy.md` (de + en) angelegt.
- Doku zweisprachig: Englisch `*.md`, Deutsch `*.de.md` (README, ReleaseNotes, CONTRIBUTING,
  ARCHITECTURE, FINDINGS, NEXT_STEPS, STATUS); beide Fassungen gleich halten.

## Nächste Aufgaben
1. **Am Gerät prüfen** (2026-11-05 erledigt: Heizprofil aus Beschreibung mit Nano ✓, Reihenfolge-
   Dialog ✓, Duschen/Baden in der Übersicht ✓ nach einer Korrektur, App läuft mit targetSdk 36 ✓):
   noch offen Export teilen/als Datei speichern, Import der eigenen Datei (klickt der Nutzer durch),
   Profil umbenennen (Schreibzugriff auf die Cloud – nur mit OK des Nutzers; Ergebnis in FINDINGS).
2. Vorbereitung Play Store (Checkliste in `PLAY_STORE_GUIDE.md`): Demo-Modus fertig (in den
   Prüfhinweisen erwähnen, am Telefon ausprobieren), Logo 512 px + Feature-Grafik (nächste Agent-Aufgabe). Beim Nutzer: Upload-Keystore +
   `keystore.properties` anlegen, GitHub Pages für `/docs` einschalten, Kontakt in `privacy.md`
   bestätigen, `.github/FUNDING.yml`: `github:` erst wenn GitHub Sponsors freigeschaltet ist.
   Entschieden 2026-11-05: **keine Spenden-Links in der App** (Play-Richtlinien, `SupportLinks.kt`
   leer → Bereich ausgeblendet); PayPal nur in README (en, `README.de.md` de).
3. Erstes Review mit `/pureheat-review full`, Befunde in `docs/REVIEW.md`.
4. Nutzer-Hinweis: Bad-Beschreibung neu erkennen + speichern (gespeichert noch „Erdgeschoss“).
5. Probelauf-Ergebnisse ansehen; Schritt 6 (Komfortzeiten + Optimal-Profil als Vorschlag).
6. Sonnengewinn (Open-Meteo `shortwave_radiation` + Fensterrichtung).

## Kleinigkeiten
- Detail-Temperaturdiagramm: y-Beschriftung „28 °C“ liegt über der Ist-Linie.
- Übersicht: Bei vielen Sollwert-Wechseln fallen einzelne Sollwert-Beschriftungen weg (erkannte
  Ereignisse bekommen jetzt immer zuerst Platz).
- Reihenfolge-Dialog: Die ↑/↓-Pfeile sind klein.

## Offene Prüfungen am Gerät (älter)
- Nie am echten System ausgeführt: „Profil aktivieren“, „Letzte Speicherung rückgängig“,
  „auf andere Räume übernehmen“. Akku/Datenverbrauch messen (NEXT_STEPS „Schritt 2“).
