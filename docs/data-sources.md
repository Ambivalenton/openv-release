# Datenquellen, Abdeckung und Nutzungsbedingungen

## Wo funktioniert Öpen-V?

Öpen-V nutzt für Verbindungen, Abfahrten und Echtzeit **VRR OpenService**. Der Schwerpunkt liegt auf Rhein-Ruhr und den Verkehren, die der VRR einbindet. Eine vollständige Abdeckung Nordrhein-Westfalens, Deutschlands oder grenzüberschreitender Wege wird nicht zugesichert. Ein Adresstreffer irgendwo auf der Welt bedeutet keine verfügbare ÖPNV-Verbindung. Weitere Fahrplandienste lassen sich anbinden, siehe [timetable-services.md](timetable-services.md).

Ein manueller Start und feste Haltestellen funktionieren ohne Standortfreigabe. Neue Verbindungen, Online-Ortsvorschläge, Karten und Wetter brauchen Internet. Favoriten, Einstellungen, Texte und der mitgelieferte Haltestellenkatalog bleiben offline verfügbar. Zuletzt geladene Verbindungen und Abfahrten sind nur eine kurzzeitige Hilfe; Alter und Zeitstempel stehen dabei. Für direkte Fahrten zwischen zugeordneten Haltestellen gibt es einen regionalen Sollfahrplan auf dem Gerät.

Echtzeitprognosen hängen vom Anbieter ab. Fehlende Prognosen bedeuten nicht, dass eine Fahrt pünktlich ist. Es gibt keine Fahrkarten, keine Buchungen und keine geprüfte barrierefreie Routenplanung.

## Dienste

| Dienst | Zweck | Umgang |
| --- | --- | --- |
| **VRR OpenService** (EFA-JSON: StopFinder, Trip, DM, AddInfo) | Verbindungen, Abfahrten, Echtzeit, Meldungen | Einzelne, nutzerbezogene Abfragen; Regeln unten |
| **DELFI-Haltestellenverzeichnis (zHV)** | Haltestellenvorschläge, auch offline | In der App mitgeliefert (`app/src/main/assets/stops/`), reproduzierbar mit `tools/build_stop_catalog.py`; Eingaben gehen dabei an keinen Server |
| **Verkehrsverbund Rhein-Ruhr AöR · VRR-Sollfahrplan (CC BY)** | Geplante Direktfahrten zwischen Haltestellen im Gültigkeitszeitraum des Feeds | Lokal mitgelieferter SQLite-Index mit Verkehrstagen und Ausnahmen. Enthält ausgewählte und verdichtete Daten aus dem GTFS-Feed. [Quelle und Lizenz](https://www.opendata-oepnv.de/ht/de/organisation/verkehrsverbuende/vrr/openvrr/datensaetze) |
| **Photon** (komoot) | Orts- und Adresssuche | Eingabepause, höchstens eine neue Anfrage pro Sekunde, zehn Minuten Suchcache |
| **OpenStreetMap** | Kartenkacheln | Nur benötigte Kacheln, sichtbare Attribution, erkennbarer Client, Cache; keine Massendownloads |
| **Valhalla · FOSSGIS** | Genaue Gehführung | Begrenzte Anfragen und Cache; öffentlicher Dienst ohne garantierte Kapazität |
| **Open-Meteo** | Wetter am Ziel | Anfangs aus; nach dem Einschalten gerundete Zielkoordinaten und 30 Minuten Cache; nichtkommerzielle Nutzung |

Alle Anfragen tragen einen erkennbaren User-Agent mit Kontaktadresse (`NetworkClient.userAgent`).

## Nutzungsbedingungen von VRR OpenService

Der VRR hat die Nutzung von OpenService für Öpen-V ohne gesonderten Produktivzugang bestätigt, solange das Anfragevolumen klein bleibt. Er hat dabei diese Bedingungen genannt; die rechte Spalte zeigt, wie Öpen-V sie einhält.

| Bedingung | Umsetzung |
| --- | --- |
| Daten nur im Rahmen einzelner, nutzerbezogener Live-Abfragen; kein systematischer oder massenhafter Abruf | Jede Abfrage geht auf eine Handlung oder eine sichtbare Ansicht zurück. Der Haltestellenkatalog stammt aus dem DELFI-Verzeichnis, nicht vom VRR. Die Vorab-Suche beim Wählen eines Ziels gilt nur für genau diese Anfrage und ist auf acht Abrufe begrenzt. |
| Automatische Aktualisierungen bedarfsgerecht | In der App nur, solange die Ansicht sichtbar ist: Übersicht alle 60 s und Abfahrten alle 30 s. Ergebnisse fragen bei anstehenden Fahrten bis zu zwei Haltestellentafeln alle drei Minuten ab (fünf im Stromsparmodus). Eine vollständige Verbindungssuche folgt bei Bedarf nach 15 Minuten oder per manuellem Aktualisieren. Eine angeheftete Reise etwa einmal pro Minute, nur mit sichtbarer Benachrichtigung. Widgets alle 15 Minuten, aber nur bei eingeschaltetem Bildschirm; beim Entsperren wird ein Stand nachgeladen, der älter als 15 Minuten ist (`WidgetRefreshScheduler`). |
| Keine Weitergabe der Daten als Bild oder Text ohne Abstimmung | Kein Teilen und kein Bildexport von Fahrplandaten; das lange Drücken im Diagramm öffnet eine Kompaktansicht. Persönliche Sicherungen enthalten nur die eigenen Suchangaben, keine Fahrtantworten. |
| Abrufe können technisch begrenzt werden | Höchstens drei gleichzeitige Anfragen, identische laufende Anfragen werden gebündelt, kurze Caches. HTTP 429 und 503 mit `Retry-After` lösen eine Pause für den Host aus; Sekundenwerte und HTTP-Daten werden beachtet, lange Pausen nicht verkürzt. |
| Quellcode-Lizenz getrennt von der Schnittstellennutzung | Der Code steht unter GPL-3.0-or-later. Die Nutzung der Schnittstelle richtet sich allein nach den Bedingungen des Anbieters; README und Lizenzhinweise sagen das ausdrücklich. |

Sichtbare Quellenangabe: „Fahrplandaten: VRR“ in Benachrichtigungen und Widgets sowie ein Link zu den Nutzungsbedingungen in der App.

Die lokalen Grenzen sind keine vom VRR zugesagte Quote und keine Verfügbarkeitsgarantie. Bei deutlich wachsendem Anfragevolumen oder erweitertem Datenexport wird die Nutzung erneut mit dem VRR abgestimmt.

## VRR-Sollfahrplan aktualisieren

`python3 tools/build_regional_timetable.py google_transit.zip app/src/main/assets/timetable` erstellt den regionalen Index aus dem veröffentlichten GTFS-ZIP der Verkehrsverbund Rhein-Ruhr AöR. `schedule.json` enthält Ausgabeversion, Gültigkeit und Prüfsummen. Die aktuelle Ausgabe trägt die Feedversion 20261013 und endet am 28. Februar 2027. Das Datumsfenster und die Verkehrstage begrenzen die lokale Ausgabe. Abweichende Fahrten, Adressen, Umstiege und Randfälle deckt die Onlinesuche ab.

## Quellen

- VRR OpenService und Dokumentation: <https://www.opendata-oepnv.de/ht/de/api>
- Nutzungsvereinbarung API: <https://www.opendata-oepnv.de/ht/de/standards/nutzungsvereinbarung-api>
- Photon: <https://photon.komoot.io/>
- OpenStreetMap Tile Usage Policy: <https://operations.osmfoundation.org/policies/tiles/>
- FOSSGIS-Routing: <https://routing.openstreetmap.de/about.html>
- Open-Meteo Terms: <https://open-meteo.com/en/terms>

---

### English summary

Öpen-V uses VRR OpenService (Rhine-Ruhr focus) for journeys, departures and real time; the bundled DELFI stop directory works offline. Requests are user-initiated or tied to a visible screen; widgets refresh every 15 minutes only while the screen is on. No timetable data are exported as images or text, backups contain only the user's own queries, at most three requests run in parallel, identical requests are shared and `Retry-After` is honoured. The software licence (GPL-3.0-or-later) is separate from the provider's terms of use.
