# Release Notes

[English](ReleaseNotes.md) · **Deutsch**

Neueste Version oben. Die Version ist der Git-Tag des Releases (`vX.Y.Z`, siehe `appVersion` in
`gradle.properties`).

## Unveröffentlicht

## 1.0.0 – erste Veröffentlichung (November 2026)

**Anzeige**
- Übersicht je Raum: Ist/Soll, Luftfeuchte, Öffnung jedes Ventils (Heizkreis), Warnung bei
  „Ventil lange offen, Raum wird nicht wärmer“. Reihenfolge: Thermostate, deren Name einen
  Raumnamen enthält (z. B. Handtuchheizkörper), stehen hinter dem Raum; „Reihenfolge“ zum
  Selbst-Anordnen.
- 24-h-Verlauf je Raum mit Zeitmarken für Sollwert-Wechsel und erkannte Ereignisse; Detailansicht
  mit 24 h bis 60 Tagen, Außentemperatur, jedem Kanal und Luftfeuchte, Werten per Antippen; Datum
  und Wochenraster bei langen Zeiträumen; Bereiche über „Ansicht“ ausblendbar.
- Außentemperatur und Wetterprognose (Open-Meteo, Standort auf ~1 km gerundet).

**Lernen und Vorhersagen**
- Aufzeichnung im Hintergrund (15/30/60 min je nach Aktivität), 2 Jahre Aufbewahrung.
- Raummodell je Raum (Wärmeverlust k, Heizleistung P, Totzeit des Estrichs) mit
  Aufheiz-/Abkühlzeiten.
- **Fenster-offen-Erkennung** aus der Temperaturkurve, ohne Fensterkontakte: prüft, ob trockenere
  Außenluft hereinkam (absolute Luftfeuchte innen und außen), und kommt mit Sensoren zurecht, die
  nur größere Sprünge melden.
- **Duschen/Baden** wird in Bädern am schnellen Anstieg der Luftfeuchte erkannt.
- Offene Fenster und Duschen bleiben beim Lernen des Raummodells außen vor.
- Probelauf: tägliche Vorhersagen, wann Aufheizphasen ihr Ziel erreichen, mit Trefferquote.
- Raumeigenschaften per Text oder Diktat, erkannt mit KI auf dem Gerät (Gemini Nano) bzw.
  Stichwörtern; sie liefern die Startwerte des Modells und bestimmen, was erkannt wird (keine
  Fenster → keine Fenster-offen-Erkennung, keine Dusche/Wanne → keine Duschen-Erkennung).

**Heizprofile**
- Wochenprofile ansehen und bearbeiten (Ziehen, genaue Zeiten, Tage kopieren, auf andere Räume
  übertragen), umbenennen, aktivieren, letzte Speicherung rückgängig machen. Vor dem Speichern
  Liste aller Änderungen, Sicherung der alten Version und Prüfung nach dem Speichern.
- **Aus Beschreibung erstellen:** Woche in eigenen Worten schreiben oder diktieren, die KI auf dem
  Gerät macht daraus einen Vorschlag zum Prüfen und Speichern.

**Sonstiges**
- **Demo-Modus:** „Demo ausprobieren“ auf dem Einrichtungsbildschirm zeigt eine Beispielwohnung
  mit erfundenen Daten – ohne Access Point, offline; beenden mit „Abmelden“.
- **Export und Import:** alle Messwerte (CSV) und Einstellungen als ZIP – teilen oder als Datei
  speichern; Import z. B. auf einem neuen Telefon (Info → Daten).
- **Kaffee spendieren:** unter Info eine freiwillige Unterstützung von 1,99 € / 1,99 $ über
  Google Play. Sie schaltet nichts frei; der Bereich erscheint nur, wenn die App aus Google Play
  installiert wurde.
- 12 Sprachen (Deutsch, Englisch, Französisch, Spanisch, Italienisch, Portugiesisch,
  Niederländisch, Polnisch, Türkisch, Russisch, Japanisch, Chinesisch), Systemsprache oder eigene Wahl.
- Hilfe in der App, Hinweis zur Akku-Einstellung für lückenlose Aufzeichnung.
- Android 8 bis 16 (Ziel-API 36).
- Kostenlos und Open Source (Apache 2.0), ohne Werbung, ohne Tracking.
