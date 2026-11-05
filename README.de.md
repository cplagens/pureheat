<p align="center">
  <img src="docs/assets/logo.svg" width="128" height="128" alt="PureHeat Logo">
</p>

<h1 align="center">PureHeat</h1>

<p align="center">
  <a href="README.md">English</a> · <b>Deutsch</b>
</p>

<p align="center">
  <b>Sieh, was deine Fußbodenheizung wirklich tut.</b><br>
  Android-App für Homematic IP Fußbodenheizungen – zeigt, was die offizielle App nicht zeigt,
  und lernt, wie sich jeder Raum verhält.
</p>

<p align="center">
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/Lizenz-Apache%202.0-blue.svg" alt="Lizenz: Apache 2.0"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin und Jetpack Compose">
  <img src="https://img.shields.io/badge/Sprachen-12-14B8A6" alt="12 Sprachen">
  <img src="https://img.shields.io/badge/Werbung%20%26%20Tracking-keine-0B3B5B" alt="Ohne Werbung und Tracking">
</p>

<p align="center">
  <a href="https://paypal.me/ChristianPlagens"><img src="https://img.shields.io/badge/PayPal-spenden-00457C?logo=paypal&logoColor=white" alt="Über PayPal spenden"></a>
</p>

---

| <img src="docs/screenshots/dashboard.png" width="200" alt="Übersicht mit Ventilöffnung je Heizkreis, erkanntem Duschen und offenem Fenster"> | <img src="docs/screenshots/room-detail.png" width="200" alt="Detailansicht mit Temperatur, Außentemperatur, Ventil und Luftfeuchte"> | <img src="docs/screenshots/room-model.png" width="200" alt="Gelerntes Raummodell und Probelauf mit Trefferquote"> | <img src="docs/screenshots/heating-profile.png" width="200" alt="Wochen-Heizprofil"> |
|:-:|:-:|:-:|:-:|
| Welcher Kreis heizt | Erkanntes offenes Fenster | Raummodell und Probelauf | Heizprofile |

<sub>Screenshots aus dem eingebauten Demo-Modus mit erfundenen Daten (englische Oberfläche; die App gibt es auch auf Deutsch).</sub>

## Warum PureHeat?

Die offizielle App zeigt Soll- und Ist-Temperatur – aber nicht, **was im Heizkreisverteiler passiert**.
Bei einer Fußbodenheizung mit trägem Estrich ist genau das entscheidend: Welcher Kreis heizt gerade?
Wie weit ist das Ventil offen? Wie lange braucht das Bad morgens, bis es warm ist? PureHeat
beantwortet diese Fragen aus den Daten, die dein System ohnehin liefert.

## Funktionen

### 🔥 Heizkreise auf einen Blick
- Sieh sofort, **welcher Heizkreis offen ist** und wie weit jeder Stellantrieb geöffnet ist.
- Warnung, wenn ein Ventil lange offen ist, der Raum aber nicht wärmer wird – z. B. bei einem
  hängenden Ventilstift.
- Ist- und Solltemperatur, Luftfeuchte, Außentemperatur und Wetterprognose.

### 📈 Verlauf
- Wird im Hintergrund aufgezeichnet (alle 15–60 Minuten, akkuschonend) und **2 Jahre** gespeichert.
- Detailansicht je Raum bis 60 Tage mit Temperatur, Ventilöffnung und Ereignissen.
- Export und Import von Messwerten und Einstellungen.

### 🧠 Lernendes Raummodell
- Für jeden Raum ein physikalisches Modell: **Wärmeverlust**, **Heizleistung** und **Totzeit** des
  Estrichs.
- Daraus Aufheiz- und Abkühlzeiten: „Wie lange dauert es von 19 auf 22 °C?“
- Ein **Probelauf** prüft die Vorhersagen täglich an der Wirklichkeit – du siehst, wie gut das Modell
  wirklich ist.

### 🪟 Ereignisse erkennen
- **Offene Fenster** und **Duschen/Baden** werden aus Temperatur und Luftfeuchte erkannt.
- **Raumeigenschaften** per Text oder Diktat beschreiben („Bad mit Dusche, kein Fenster“) –
  ausgewertet mit KI **auf dem Gerät** (Gemini Nano), ohne Cloud. Die Beschreibung schärft die
  Erkennung.

### 🗓️ Heizprofile komfortabel bearbeiten
- Profile bearbeiten, umbenennen, aktivieren und rückgängig machen.
- Vorschlag für ein Heizprofil aus einer Beschreibung in eigenen Worten.
- Vor jedem Speichern eine **Änderungsliste** und Bestätigung, vorher eine **Sicherung**, danach
  eine **Prüfung**, ob die Cloud alles richtig übernommen hat.

### 🌍 Und außerdem
- 12 Sprachen (Deutsch, Englisch, Französisch, Spanisch, Italienisch, Portugiesisch, Niederländisch,
  Polnisch, Türkisch, Russisch, Japanisch, Chinesisch).
- Ausführliche Hilfe direkt in der App.

## Datenschutz und Sicherheit

### Was aufgezeichnet wird

PureHeat speichert nur, was für Verlauf und Raummodell nötig ist – in einer Datenbank **auf deinem
Gerät**:

| Daten | Wofür |
|---|---|
| Ist- und Solltemperatur, Luftfeuchte und Ventilöffnung je Raum | Verlauf, Raummodell, Fenster- und Duscherkennung |
| Ventilöffnung je Heizkreis | Welcher Kreis heizt, Ventil-Warnungen |
| Außentemperatur und stündliche Prognose | Wärmeverlust im Modell, Aufheiz-Vorhersagen |
| Vorhersagen des Probelaufs und ihre Ergebnisse | Prüfen, wie gut das Modell ist |
| Deine Raumbeschreibungen und Ansichtseinstellungen | Erkennungsregeln, Darstellung |

Der Verlauf wird 2 Jahre aufbewahrt (ca. 120 KB pro Tag, nach 2 Jahren rund 90 MB) und danach
automatisch gelöscht. Export und Import passieren nur, wenn du sie auslöst.

### Warum deine Daten sicher sind

- **Keine Werbung, kein Tracking, keine Analyse, kein eigener Server** – es gibt kein
  PureHeat-Backend, das irgendetwas sammeln könnte.
- **Nur eine Berechtigung: Internet.** Kein Zugriff auf GPS, Kontakte, Kamera oder Dateien.
- Auth-Token und alle Messwerte liegen im privaten Speicher der App; die Android-Sicherung ist
  abgeschaltet, nichts landet in einem Cloud-Backup.
- Daten gehen nur an die Homematic IP Cloud (die dein System ohnehin nutzt) und an
  [Open-Meteo](https://open-meteo.com) für die Wetterprognose – mit auf ~1 km gerundetem
  Standort des Hauses und ohne Kennung.
- KI läuft **auf dem Gerät** (Gemini Nano); Raumbeschreibungen verlassen das Telefon nicht.
- **Schreibzugriffe nur für Heizprofile** und nur nach deiner Bestätigung. PureHeat stellt nie von
  sich aus Temperaturen ein; die KI macht nur Vorschläge.
- Open Source: Du kannst all das im Code nachprüfen.
- Details: [Datenschutzerklärung](docs/privacy.md#deutsch)

## Voraussetzungen

- Homematic IP mit Access Point **HmIP-HAP** und Cloud-Anbindung
- Fußbodenheizungs-Aktor (z. B. HmIP-FAL) mit Wandthermostaten
- Android 8.0 oder neuer; für die KI-Funktionen ein Gerät mit Gemini Nano (sonst Stichwort-Erkennung)

## Einrichtung

1. App bauen und installieren: `./gradlew installDebug`
2. In der App die SGTIN des Access Points eingeben (steht auf dem Aufkleber unter dem Gerät).
3. Wenn die App dazu auffordert, den blauen Knopf am Access Point drücken.

Fertig – ab jetzt zeichnet PureHeat im Hintergrund auf und das Raummodell lernt mit jedem Tag dazu.

**Kein Access Point zur Hand?** Auf dem Einrichtungsbildschirm **„Demo ausprobieren“** tippen: eine
Beispielwohnung mit drei Wochen erfundenem Verlauf, gelerntem Raummodell, gelüftetem Schlafzimmer und
Duschen im Bad. Funktioniert offline, sendet nichts und wird über „Abmelden“ beendet.

### Akku-Einstellung für einen lückenlosen Verlauf

Android bremst Hintergrundjobs von Apps, die selten geöffnet werden – am echten System entstanden
dadurch Lücken von bis zu 9 Stunden. Für durchgehende Messwerte **App-Info → Akku → „Nicht
eingeschränkt“** einstellen (solange die App eingeschränkt ist, zeigt sie dazu eine Hinweiskarte mit
direktem Link zu dieser Einstellung).

Der Akkuverbrauch bleibt trotzdem minimal:

- PureHeat ruft den Zustand nur **alle 15 Minuten** ab, solange sich etwas ändert, sonst alle
  30 Minuten und nachts alle 60 Minuten – jeweils eine kurze Anfrage mit ca. 100 KB.
- Keine Dauerverbindung, kein Vordergrunddienst, kein GPS. Die Jobs laufen über Androids
  WorkManager, der sie mit anderen Apps bündelt, und pausieren bei niedrigem Akkustand.

> **Hinweis:** Inoffizielles Projekt, nicht mit eQ-3 verbunden. Die Cloud-API ist nicht offiziell
> dokumentiert und kann sich ändern.

## Unterstützen

PureHeat ist und bleibt kostenlos, ohne Werbung und ohne Tracking. Wenn dir die App gefällt, kannst du
die Entwicklung freiwillig unterstützen:

- 💙 [PayPal](https://paypal.me/ChristianPlagens)

Ein ⭐ für das Repository, Fehlermeldungen und Verbesserungsvorschläge helfen ebenfalls – siehe
[CONTRIBUTING.de.md](CONTRIBUTING.de.md).

## Dokumentation

| Datei | Inhalt |
|---|---|
| [ReleaseNotes.de.md](ReleaseNotes.de.md) | Was sich in welcher Version geändert hat |
| [CONTRIBUTING.de.md](CONTRIBUTING.de.md) | Fehler melden, bauen, testen, Regeln für Beiträge |
| [LICENSE.md](LICENSE.md) | Apache-Lizenz 2.0 (nur auf Englisch verbindlich) |
| [NOTICE](NOTICE) | Urheberrecht und Hinweise (Englisch) |
| [docs/privacy.md](docs/privacy.md#deutsch) | Datenschutzerklärung |
| [docs/ARCHITECTURE.de.md](docs/ARCHITECTURE.de.md) | Aufbau, Datenfluss, Datenbank, API-Besonderheiten |
| [docs/FINDINGS.de.md](docs/FINDINGS.de.md) | Erkenntnisse am echten System |
| [docs/NEXT_STEPS.de.md](docs/NEXT_STEPS.de.md) | Roadmap |
| [docs/STATUS.de.md](docs/STATUS.de.md) | Aktueller Entwicklungsstand |

## Lizenz

Copyright 2026 Christian Plagens. Veröffentlicht unter der [Apache-Lizenz 2.0](LICENSE.md).

„Homematic IP“ ist eine Marke der eQ-3 AG. PureHeat ist ein unabhängiges Projekt.
