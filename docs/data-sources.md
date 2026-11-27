# Datenquellen und Abdeckung

Öpen-V bezieht **Verbindungen und Abfahrten von VRR OpenService**. Der Schwerpunkt liegt auf Rhein-Ruhr und den vom VRR eingebundenen Verkehren. Bei einer Verbindung über diesen Bereich hinaus entscheidet der Datenbestand des Dienstes, welche Auskunft möglich ist. Prognosen, Störungen und Gleisangaben können je Fahrt fehlen.

Die **deutschlandweite Haltestellensuche** nutzt einen mitgelieferten Auszug aus dem Zentralen Haltestellenverzeichnis (ZHV) von DELFI, ergänzt um Namen und Kennungen aus VRR-Solldaten. Eine Haltestelle lässt sich dadurch auch offline finden. Für Abfahrtszeiten und neue Routen braucht die App eine Online-Auskunft. Datenstand, Bearbeitung und Quellen sind im [Hinweis zum Haltestellenkatalog](../app/src/main/assets/stops/NOTICE.txt) dokumentiert.

## Welche Dienste erhalten eine Anfrage?

| Dienst | Abruf und Zweck |
| --- | --- |
| VRR OpenService (EFA-JSON) | Haltestellen-, Umkreis- und Verbindungssuche sowie Abfahrten. Die ausgewählte Suche übermittelt beispielsweise Haltestellenkennungen oder Koordinaten, Zeit und Verkehrsmittel. |
| DELFI-ZHV | Der mitgelieferte Katalog wird auf dem Gerät durchsucht. Diese Suche stellt keine Anfrage an DELFI. Die zusätzliche Online-Suche nutzt die jeweils zuständigen Dienste. |
| Photon | Nach einer Eingabepause werden Orts- und Adressvorschläge in Deutschland gesucht. Höchstens eine neue Anfrage pro Sekunde, zehn Minuten lokaler Suchcache. |
| OpenStreetMap | Sichtbare Karten laden die angezeigten Kacheln. Die App nennt die Quelle und nutzt einen Cache. |
| Valhalla (FOSSGIS) | Die genaue Gehführung fragt mit den Endpunkten eines Fußwegs eine Route ab. Sie lässt sich in der App ausschalten. |
| Open-Meteo | Nach dem Einschalten der Wetteranzeige fragt die App mit gerundeten Zielkoordinaten eine Vorhersage ab. |

Ohne Standortfreigabe lassen sich Start und Haltestelle von Hand wählen. Favoriten und Einstellungen bleiben lokal. Die [Datenschutzerklärung](../PRIVACY.md) beschreibt die übertragenen Angaben und die jeweiligen Speicherfristen.

## Abrufverhalten gegenüber dem Fahrplandienst

Die folgenden Werte beschreiben das Verhalten **einer Installation**. Das gesamte Anfragevolumen hängt von der Zahl aktiver Geräte, ihren Ansichten und den laufenden Fahrten ab.

| Anlass | Verhalten der App |
| --- | --- |
| Ein Ziel wird ausgewählt | Außerhalb des Stromsparmodus kann die App bereits Verbindungen für genau diese Suche vorladen. Das Budget dieser Vorab-Suche beträgt höchstens acht Abrufe. |
| Eine Ansicht ist geöffnet | Der reguläre Aktualisierungstakt beträgt für Übersicht und Ergebnisse etwa 60 Sekunden, für die Abfahrtstafel etwa 30 Sekunden. Im Stromsparmodus wird seltener abgefragt. |
| Eine Fahrt wird angeheftet | Die bewusst gestartete Reisebegleitung kann auch bei gesperrtem Bildschirm weiterlaufen. Ihr regulärer Abruf erfolgt etwa einmal pro Minute. Ereignisse während der Fahrt können eine neue Suche auslösen. |
| Ein Abfahrtswidget ist eingerichtet | Die periodische Aktualisierung ist im 15-Minuten-Takt eingeplant und fragt nur bei eingeschaltetem Bildschirm ab. Beim Entsperren kann ein alter Stand nachgeladen werden. Auch das Einrichten oder Ändern eines Widgets kann einen Abruf auslösen. |
| Mehrere Anfragen fallen zusammen | Der gemeinsame Netzwerkclient kennzeichnet Fahrplanabfragen mit App-Version und Projektkontakt und lässt höchstens drei gleichzeitige Abrufe zu. Identische laufende Abfragen werden gebündelt. Bei HTTP 429 oder HTTP 503 mit `Retry-After` pausiert der Client weitere Anfragen an diesen Host. |
| Eine Person sichert oder teilt Daten | Fahrplandaten werden für einzelne Auskünfte abgerufen. Die App bietet weder einen Massenabruf noch einen Bild- oder Textexport von Fahrplanantworten. Persönliche Sicherungen enthalten die eigenen Suchangaben. Quellenhinweise erscheinen auch in Widgets und Benachrichtigungen. |

Für das bisherige Hobbyprojekt mit kleinem Anfragevolumen liegt eine Rückmeldung des VRR zur Nutzung seines OpenService vor. Bei wachsendem Volumen oder einer erweiterten Verwendung wird der Zugang mit dem Betreiber abgestimmt. Die lokalen Grenzen oben sind keine vom Betreiber zugesagte Quote. Für den öffentlichen Produktivbetrieb nennt das Portal eigene Zugänge und Kontakte. [OpenService-Dokumentation](https://www.opendata-oepnv.de/ht/de/api) · [Nutzungsvereinbarung](https://www.opendata-oepnv.de/ht/de/standards/nutzungsvereinbarung-api)

## Quellen und Technik

- [DELFI-Daten und -Dienste](https://www.delfi.de/de/leistungen-produkte/daten-dienste/) · [Lizenz- und Bearbeitungshinweise zum Katalog](../app/src/main/assets/stops/NOTICE.txt)
- [Photon](https://photon.komoot.io/) · [OpenStreetMap-Kacheln](https://operations.osmfoundation.org/policies/tiles/) · [FOSSGIS-Routing](https://routing.openstreetmap.de/about.html) · [Open-Meteo](https://open-meteo.com/en/terms)
- [Fahrplandienste im Code](timetable-services.md) · [Weitere Lizenzen](../THIRD_PARTY_NOTICES.md)

### English summary

VRR OpenService supplies journeys and departures with a Rhine-Ruhr focus. The bundled nationwide DELFI stop directory works offline. The app queries services directly from the device for a search, an active view, a pinned journey or a widget update. A pinned journey can continue with the screen locked. The common network client limits concurrency per installation and honours provider backoff. Provider terms apply separately from the software licence.
