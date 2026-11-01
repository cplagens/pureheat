# PureHeat

Android-App, die Daten einer Homematic IP Fußbodenheizung anzeigt, darunter auch das, was die offizielle App nicht zeigt:

- welcher Heizkreis gerade heizt
- wie weit die einzelnen Stellantriebe geöffnet sind
- Ist-/Solltemperatur, Luftfeuchte und Fensterstatus je Raum

Die App nutzt die Homematic IP Cloud über den Access Point (HmIP-HAP). Sie liest derzeit nur Daten und steuert nichts.

> Inoffizielles Projekt, nicht mit eQ-3 verbunden. Die Cloud-API ist nicht offiziell dokumentiert und kann sich ändern.

## Einrichtung

1. App bauen und installieren: `./gradlew installDebug`
2. In der App die SGTIN des Access Points eingeben (steht auf dem Aufkleber unter dem Gerät).
3. Wenn die App dazu auffordert, den blauen Knopf am Access Point drücken.

Das Auth-Token wird nur lokal auf dem Gerät gespeichert.

## Lizenz

Apache 2.0
