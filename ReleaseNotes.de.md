# Release Notes

[English](ReleaseNotes.md) · **Deutsch**

Neueste Version oben. Die Version ist der Git-Tag des Releases (`vX.Y.Z`, siehe `appVersion` in
`gradle.properties`).

## Unveröffentlicht

## 1.1.0 – Raummodell aus allen Daten (November 2026)

- Übersicht: die Aufheiz-/Abkühlzeile jedes Raums kommt jetzt aus dem Raummodell über alle
  Daten und zeigt damit schon ab der ersten Woche Zeiten statt „lernt noch“. Zeigt der Probelauf,
  dass das klassische Modell für einen Raum deutlich genauer ist, wird dieses angezeigt.
- Raumdetail: das Temperaturdiagramm zeigt die nächsten 12 Stunden so, wie das Raummodell sie
  erwartet, und wann jedes Aufheizen sein Ziel erreichen soll. Raummodell-Karte überarbeitet:
  Zuverlässigkeits-Abzeichen, Kennzahlen, morgen mit deinem aktiven Profil; Details auf Wunsch.
  Probelauf als Trefferquote, Abweichungsdiagramm und nächste Prüfung.
- Raummodell-Karte: Diagramm eines simulierten Tages (Absenkung → Komfort → Absenkung) für beide
  Modelle; das Modell aus allen Daten mit seinen Vorhersagen, welche Werte gemessen oder nur
  geschätzt sind, und seine wöchentlichen Versionen mit dem Fehler der letzten Woche (auch als
  Diagramm).
- Probelauf: sobald es ein Raummodell aus allen Daten gibt (nach 7 Tagen), sagt es neben dem
  Physikmodell voraus; die Detailansicht zeigt seine eigene Trefferquote.
- Fenster offen: gilt nicht mehr als geschlossen, wenn die Heizung anläuft oder die Temperatur
  nur leicht schwankt; morgendliches Heizen gegen ein offenes Fenster fließt nicht ins Raummodell.
- Heizprofil: „Letztes Speichern rückgängig“ fragt jetzt vor dem Schreiben in die Cloud nach.
- Heizprofil-Editor: „Phase hinzufügen“ hängt die neue Phase direkt hinter die letzte an (statt
  in die größte Lücke, meist am frühen Morgen).
- Heizprofil speichern: die Auswahl weiterer Räume kommt zuerst, lange Änderungslisten sind
  eingeklappt; andere Räume bekommen nur die geänderten Tage (vorher: die ganze Woche).
- Info: die Sprachauswahl ist auf eine Zeile eingeklappt.
- Abmelden löscht auch den letzten Datenexport aus dem App-Cache.
- Profil-Editor: Screenreader sagen die −/+-Tasten mit dem Namen ihres Werts an.

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
