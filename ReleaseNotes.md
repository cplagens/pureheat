# Release Notes

Neueste Version oben. Versionen folgen `versionName` in `app/build.gradle.kts`.

## Unveröffentlicht

- **Heizprofile umbenennen** (Name wird in der Cloud gespeichert und danach geprüft).
- **Dusche und Badewanne** als Raumeigenschaften; in Bädern erkennt die App **Duschen/Baden** am
  schnellen Anstieg der Luftfeuchte, markiert es in den Diagrammen und lässt diese Zeiten beim
  Lernen des Raummodells weg.
- **Reihenfolge der Räume:** Thermostate, deren Name einen Raumnamen enthält (z. B.
  Handtuchheizkörper), stehen direkt hinter dem Raum; „Reihenfolge“ in der Übersicht zum Selbst-Anordnen.
- Open Source sichtbar gemacht: Link zum Repository in Hilfe und Info, Lizenzhinweis in jeder
  Quelldatei, `CONTRIBUTING.md`, `NOTICE`, `LICENSE.md`.

## 0.1.0 – erste Version (November 2026)

**Anzeige**
- Übersicht je Raum: Ist/Soll, Luftfeuchte, Öffnung jedes Ventils (Heizkreis), Warnung bei
  „Ventil lange offen, Raum wird nicht wärmer“.
- 24-h-Verlauf je Raum; Detailansicht mit 24 h bis 60 Tagen, Außentemperatur, jedem Kanal,
  Werten per Antippen; Bereiche über „Ansicht“ ausblendbar.
- Außentemperatur und Wetterprognose (Open-Meteo, Standort auf ~1 km gerundet).

**Lernen und Vorhersagen**
- Aufzeichnung im Hintergrund (15/30/60 min je nach Aktivität), 2 Jahre Aufbewahrung.
- Raummodell je Raum (Wärmeverlust k, Heizleistung P, Totzeit des Estrichs) mit
  Aufheiz-/Abkühlzeiten.
- Fenster-offen-Erkennung aus der Temperaturkurve, ohne Fensterkontakte.
- Probelauf: tägliche Vorhersagen, wann Aufheizphasen ihr Ziel erreichen, mit Trefferquote.
- Raumeigenschaften per Text oder Diktat, erkannt mit KI auf dem Gerät (Gemini Nano) bzw.
  Stichwörtern; sie liefern die Startwerte des Modells.

**Heizprofile**
- Wochenprofile ansehen und bearbeiten (Ziehen, genaue Zeiten, Tage kopieren, auf andere Räume
  übertragen), aktivieren, letzte Speicherung rückgängig machen. Vor dem Speichern Liste aller
  Änderungen, Sicherung der alten Version und Prüfung nach dem Speichern.

**Sonstiges**
- 12 Sprachen (Deutsch, Englisch, Französisch, Spanisch, Italienisch, Portugiesisch,
  Niederländisch, Polnisch, Türkisch, Russisch, Japanisch, Chinesisch), Systemsprache oder eigene Wahl.
- Hilfe in der App, Hinweis zur Akku-Einstellung für lückenlose Aufzeichnung.
- Kostenlos, ohne Werbung, ohne Tracking.
