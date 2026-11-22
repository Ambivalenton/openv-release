# Lizenzen & Datenquellen

Öpen-V verbindet freie Software mit öffentlichen Daten. Hier findest du Herkunft, Bedingungen und vollständige Software-Lizenztexte – auch ohne Internet. Externe Quellen öffnen erst nach Antippen.

## App-Code und Gestaltung

Eigener App-Code: **GNU GPL Version 3 oder später (GPL-3.0-or-later)**. Copyright 2026 Ambivalenton, soweit Rechte bestehen. Frühere Apache-2.0-Lizenzgewährungen bleiben gültig. Fremde Urheber- und Lizenzangaben bleiben erhalten; es werden keine ausschließlichen Rechte an rein KI-generierten Anteilen behauptet.

Die eigenen Vektor-/Canvas-Gestaltungen, Logovarianten und Texte gehören zum Projekt unter dieser Lizenz. Betriebssysteme, Kunstepochen und Städte dienen einzelnen Designs als Inspiration. Es werden dadurch keine Herstellerverbindung und keine Übernahme fremder Systemlogos, Original-Hintergrundbilder oder proprietärer Schriften behauptet.

> **App-Lizenz und Serverzugang sind verschieden.** Die GPL erlaubt nicht automatisch die beliebige Nutzung fremder Fahrplan-, Karten- oder Wetterdienste. Deren Bedingungen und Kapazitätsgrenzen gelten zusätzlich.

## Fahrpläne, Haltestellen und Ortssuche

### Aktuelle Fahrten

Verbindungen und Abfahrten stammen von [VRR OpenService](https://www.opendata-oepnv.de/ht/de/api). Fahrplanauskünfte werden durch den Verkehrsverbund Rhein-Ruhr zur Verfügung gestellt. Alle Angaben ohne Gewähr. API-Nutzungsbedingungen und App-Lizenz gelten unabhängig voneinander.

### Haltestellensuche deutschlandweit

Lokaler Suchkatalog: **Zentrales Haltestellenverzeichnis, © DELFI e.V., Export 02.11.2026**. Ergänzende Namen und Kennungszuordnungen: **VRR-Soll-Fahrplandaten (GTFS), © Verkehrsverbund Rhein-Ruhr AöR**, Feed-Version 20261013, gültig 01.09.2026 bis 28.02.2027.

Quellen: [DELFI auf OpenData ÖPNV](https://www.opendata-oepnv.de/ht/de/organisation/delfi/startseite) · [VRR-Datensätze](https://www.opendata-oepnv.de/ht/de/organisation/verkehrsverbuende/vrr/openvrr/datensaetze).

Die Download-Metadaten verweisen auf [Creative Commons Namensnennung (CC-BY)](https://opendefinition.org/licenses/cc-by/), ohne eigene numerische Versionsangabe. Dieser Originalverweis bleibt maßgeblich erhalten; die Übersicht verlinkt auf [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Aus dem Übersichtslink wird keine nachträgliche Lizenzversion abgeleitet.

Bearbeitet wurden die Auswahl der Haltestellen auf Ortsebene, normalisierte und indexierte Suchtexte sowie VRR-Kennungszuordnungen über dokumentierte Eltern-/DHID-Felder. Einträge mit letztem Bedienungstag vor dem Exportdatum sind aus Vorschlägen entfernt; das belegt allein keine endgültige Stilllegung. Kennungen werden nicht durch Abschneiden zugeordnet. Die abgelaufene separate Linien-CSV wird nicht eingebunden.

Der Katalog aktualisiert sich nicht automatisch. Er ist **kein Offline-Fahrplan und kein Echtzeitzugang**. Die Online-Suche läuft ergänzend. Es wird keine Unterstützung oder Freigabe durch die Datengeber behauptet.

### Zusätzliche Ortsvorschläge

[Photon von komoot](https://github.com/komoot/photon) liefert Orts-/Adressvorschläge per HTTP-API auf Basis von OpenStreetMap. Der Photon-Servercode ist nicht in der App enthalten. OSM-Daten: © OpenStreetMap-Mitwirkende, ODbL.

## Karten, Gehführung und Wetter

| Quelle | Verwendung und Bedingungen |
| --- | --- |
| OpenStreetMap | © OpenStreetMap-Mitwirkende, ODbL. [Urheber- und Lizenzhinweise](https://www.openstreetmap.org/copyright). |
| Kartenkacheln | Sichtbare Ausschnitte des gewählten Servers, standardmäßig tile.openstreetmap.org. [OSM-Kachelregeln](https://operations.osmfoundation.org/policies/tiles/). Begrenzte Caches, keine Offline-Vorratsladung. |
| Valhalla | Genaue Geh-Anweisungen per HTTP-API, standardmäßig valhalla1.openstreetmap.de mit OSM-Daten. [Projekt](https://valhalla.github.io/valhalla/) und [öffentlicher Demodienst / Fair Use](https://valhalla.github.io/valhalla/start/introduction/). Kein eingebetteter Servercode. |
| Open-Meteo | Wetterdaten unter CC BY 4.0; lokal zu Ankunftshinweisen umformuliert. [Lizenz](https://open-meteo.com/en/licence), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) und [Dienstbedingungen](https://open-meteo.com/en/terms). Die freie gehostete API ist auf nichtkommerzielle Nutzung und begrenztes Anfragevolumen beschränkt. |

Externe Navigation öffnet den von dir gewählten Kartendienst nach Antippen. Dessen Bedingungen gelten eigenständig. Karten- und Wetterdaten können unvollständig sein; eine Quellenangabe ist keine Genauigkeitsgarantie.

## Schriften, Symbole und redaktionelle Inhalte

Die 62 gebündelten, unveränderten Schriften aus [google/fonts](https://github.com/google/fonts) stehen unter **SIL Open Font License 1.1**. Vollständige Lizenz- und Urheberangaben öffnest du bei der jeweiligen Schriftauswahl über Lizenz anzeigen. Die Quellherkunft mit fixierter Revision und Prüfsummen ist im Projekt in localization/fonts.json und docs/FONTS.md dokumentiert. Schriftlizenzen gelten neben der GPL des App-Codes.

Material Icons und die übernommenen Algorithmen der Material Color Utilities: Copyright Google LLC / The Android Open Source Project, **Apache-2.0**. Original-Header bleiben erhalten; Paket-/Importpfade wurden angepasst. [Material Icons](https://github.com/google/material-design-icons) · [Material Color Utilities](https://github.com/material-foundation/material-color-utilities).

Fakten sind eigenständig formulierte kurze Zusammenfassungen. Quellen und stabile Inhaltskennungen stehen im Quellkatalog des Projekts; verfügbare Quellen lassen sich auch in der Textchronik öffnen. Die App ruft keinen generativen KI-Dienst zur Texterstellung auf.

## Bibliotheken dieser Fassung

Die folgende aufgelöste Laufzeit-Inventarliste benennt Komponenten und ihre Lizenzen. Unveränderte MapLibre-Originalhinweise können optionale SDK-Bestandteile beschreiben; die Liste zeigt die tatsächlich aufgelösten Komponenten dieser App.

