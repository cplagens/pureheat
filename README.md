# PureHeat

Android-App für Homematic IP Fußbodenheizungen, die zeigt, was die offizielle App nicht zeigt – und
lernt, wie sich jeder Raum verhält.

- **Welcher Heizkreis gerade offen ist** und wie weit jeder Stellantrieb geöffnet ist; Warnung, wenn
  ein Ventil lange offen ist, der Raum aber nicht wärmer wird
- Ist-/Solltemperatur, Luftfeuchte, Außentemperatur und Wetterprognose
- **Verlauf** (im Hintergrund aufgezeichnet, 2 Jahre gespeichert) mit Detailansicht bis 60 Tage
- **Raummodell** je Raum (Wärmeverlust, Heizleistung, Totzeit des Estrichs) mit Aufheiz- und
  Abkühlzeiten und einem **Probelauf**, der die Vorhersagen täglich an der Wirklichkeit misst
- Erkennung von **offenen Fenstern** und **Duschen/Baden** aus Temperatur und Luftfeuchte
- **Raumeigenschaften** per Text oder Diktat, erkannt mit KI auf dem Gerät (Gemini Nano)
- **Heizprofile** komfortabel bearbeiten, umbenennen, aktivieren und rückgängig machen – mit
  Änderungsliste, Sicherung und Prüfung nach dem Speichern
- 12 Sprachen, Hilfe in der App

Die App nutzt die Homematic IP Cloud über den Access Point (HmIP-HAP). Schreibzugriffe gibt es nur
für Heizprofile und nur nach Bestätigung.

> Inoffizielles Projekt, nicht mit eQ-3 verbunden. Die Cloud-API ist nicht offiziell dokumentiert und
> kann sich ändern.

## Einrichtung

1. App bauen und installieren: `./gradlew installDebug`
2. In der App die SGTIN des Access Points eingeben (steht auf dem Aufkleber unter dem Gerät).
3. Wenn die App dazu auffordert, den blauen Knopf am Access Point drücken.

Das Auth-Token wird nur lokal auf dem Gerät gespeichert. Daten gehen nur an die Homematic IP Cloud
und (mit auf ~1 km gerundetem Standort) an Open-Meteo für die Wetterprognose.

## Dokumentation

| Datei | Inhalt |
|---|---|
| [ReleaseNotes.md](ReleaseNotes.md) | Was sich in welcher Version geändert hat |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Fehler melden, bauen, testen, Regeln für Beiträge |
| [LICENSE.md](LICENSE.md) | Apache-Lizenz 2.0 |
| [NOTICE](NOTICE) | Urheberrecht und Hinweise |
| [docs/privacy.md](docs/privacy.md) | Datenschutzerklärung |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Aufbau, Datenfluss, Datenbank, API-Besonderheiten |
| [docs/FINDINGS.md](docs/FINDINGS.md) | Erkenntnisse am echten System |
| [docs/NEXT_STEPS.md](docs/NEXT_STEPS.md) | Roadmap |
| [docs/STATUS.md](docs/STATUS.md) | Aktueller Entwicklungsstand |

## Unterstützen

PureHeat ist und bleibt kostenlos, ohne Werbung und ohne Tracking. Wer möchte, kann die Entwicklung
freiwillig unterstützen (Links in der App unter „Info“). Die Links werden in
`app/src/main/java/de/cplagens/pureheat/SupportLinks.kt` eingetragen; leere Einträge werden in der App
ausgeblendet.

## Lizenz

Copyright 2026 Christian Plagens. Veröffentlicht unter der [Apache-Lizenz 2.0](LICENSE.md).
