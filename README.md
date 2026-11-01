# PureHeat

Android-App, die Daten einer Homematic IP Fußbodenheizung anzeigt, darunter auch das, was die offizielle App nicht zeigt:

- bei welchem Heizkreis das Ventil gerade offen ist und wie weit die einzelnen Stellantriebe geöffnet sind
- Ist-/Solltemperatur, Luftfeuchte und Fensterstatus je Raum
- Verlauf der letzten 24 Stunden je Raum (wird auch im Hintergrund aufgezeichnet, etwa alle 15 Minuten) und eine Warnung, wenn ein Ventil lange offen ist, der Raum aber nicht wärmer wird
- Heizprofile als Wochenübersicht; Heizphasen per Ziehen oder +/− bearbeiten, Tage kopieren, auf andere Räume übertragen, Profil aktivieren. Vor dem Speichern zeigt die App alle Änderungen an, sichert die alte Version und prüft danach, ob die Cloud die neuen Werte übernommen hat.

Die App nutzt die Homematic IP Cloud über den Access Point (HmIP-HAP). Schreibzugriffe gibt es nur für Heizprofile und nur nach Bestätigung.

> Inoffizielles Projekt, nicht mit eQ-3 verbunden. Die Cloud-API ist nicht offiziell dokumentiert und kann sich ändern.

## Einrichtung

1. App bauen und installieren: `./gradlew installDebug`
2. In der App die SGTIN des Access Points eingeben (steht auf dem Aufkleber unter dem Gerät).
3. Wenn die App dazu auffordert, den blauen Knopf am Access Point drücken.

Das Auth-Token wird nur lokal auf dem Gerät gespeichert.

## Unterstützen
PureHeat ist und bleibt kostenlos, ohne Werbung und ohne Tracking. Wer möchte, kann die Entwicklung freiwillig unterstützen.
Die Links werden in `app/src/main/java/de/cplagens/pureheat/SupportLinks.kt` eingetragen; leere Einträge werden in der App ausgeblendet.

## Lizenz

Apache 2.0
