# Mitmachen bei PureHeat

[English](CONTRIBUTING.md) · **Deutsch**

Danke für dein Interesse! Fehlermeldungen, Ideen, Übersetzungskorrekturen und Pull Requests sind
willkommen – gern auch auf Englisch.

## Fehler melden und Ideen einbringen

- Bitte ein [Issue](https://github.com/cplagens/pureheat/issues) anlegen: Was hast du erwartet,
  was ist passiert, Android-Version, App-Version (unter „Info“).
- **Keine persönlichen Daten posten:** keine SGTIN, kein Auth-Token, keine PIN, keine
  Rohdaten der Cloud (`last_state.json` enthält IDs und den Standort). Ausschnitte bitte
  anonymisieren.

## Entwickeln

Voraussetzungen: Android Studio (aktuell), JDK 17, ein Android-Gerät oder Emulator ab Android 8.0.

```bash
./gradlew assembleDebug        # bauen
./gradlew testDebugUnitTest    # Unit-Tests (reine Kotlin-Logik)
./gradlew lintDebug            # Lint, u. a. fehlende Übersetzungen
./gradlew installDebug         # auf dem verbundenen Gerät installieren
```

Zum Ausprobieren brauchst du einen Homematic IP Access Point (HmIP-HAP) mit Fußbodenheizungsaktor.
Aufbau, Datenfluss und API-Besonderheiten stehen in [docs/ARCHITECTURE.de.md](docs/ARCHITECTURE.de.md).

## Regeln für Beiträge

- **Schreibzugriffe auf die Heizung** nur für Heizprofile und nur nach Bestätigung durch den Nutzer
  (mit Sicherung vorher und Prüfung nachher). Keine neuen Schreibaufrufe ohne vorherige Absprache
  im Issue.
- **Die Cloud-API ist inoffiziell:** jedes Feld als optional behandeln, unbekannte Felder
  ignorieren bzw. beim Zurückschreiben erhalten. Neue Felder erst an einem echten (anonymisierten)
  Datensatz prüfen.
- **Datenschutz:** Daten verlassen das Gerät nur zur Homematic IP Cloud und zu Open-Meteo
  (gerundeter Standort). Keine Analyse-, Werbe- oder Tracking-Bibliotheken; KI nur auf dem Gerät.
- **Texte:** keine festen Texte im Kotlin-Code, alles in `res/values/strings.xml` (Englisch) und in
  **allen** Übersetzungen (`values-de`, `-fr`, `-es`, `-it`, `-pt`, `-nl`, `-pl`, `-tr`, `-ru`,
  `-ja`, `-zh-rCN`). Wer eine Sprache nicht kann, übernimmt den englischen Text und vermerkt das im PR.
- **Tests:** Änderungen an reiner Logik (Analyse, Modell, Erkennung, Parser) mit Unit-Tests;
  Build, Tests und Lint müssen grün sein.
- **Stil:** wie der umgebende Code (Kotlin, Jetpack Compose, kurze KDoc-Kommentare, die das „Warum“
  erklären). Neue Dateien beginnen mit dem Lizenzkopf:

  ```kotlin
  /*
   * PureHeat – Copyright 2026 Christian Plagens
   * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
   * SPDX-License-Identifier: Apache-2.0
   */
  ```
- „Homematic“ nicht im App-Namen oder Icon verwenden (Marke von eQ-3).

## Pull Requests

1. Fork anlegen, Branch erstellen, Änderung klein und fokussiert halten.
2. Beschreiben, was sich ändert und warum; bei UI-Änderungen gern ein Screenshot.
3. Größere Neuerungen in [ReleaseNotes.de.md](ReleaseNotes.de.md) unter „Unveröffentlicht“ eintragen.

## Lizenz

Mit deinem Beitrag stimmst du zu, dass er unter der [Apache-Lizenz 2.0](LICENSE.md) veröffentlicht
wird (wie der Rest des Projekts, siehe Abschnitt 5 der Lizenz).
