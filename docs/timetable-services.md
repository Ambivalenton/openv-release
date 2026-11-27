# Fahrplandienste

Die öffentliche Vorschau fragt **VRR OpenService** direkt vom Gerät ab. Der EFA-Adapter verarbeitet Haltestellen, Umkreissuchen, Verbindungen und Abfahrten im JSON-Format. Das deutschlandweite [Zentrale Haltestellenverzeichnis](data-sources.md) wird als lokaler Suchkatalog ausgeliefert.

## Konfiguration im aktuellen Build

Ein Build verwendet genau einen Fahrplandienst. In [gradle.properties](../gradle.properties) stehen diese Werte:

```properties
openv.timetable=vrr
openv.timetable.protocol=efa
```

[TimetableService](../app/src/main/java/dev/ambivalenton/openv/data/model/TimetableService.kt) beschreibt Endpunkt, Abdeckung und Quellenangabe. Die [TransitProviderFactory](../app/src/main/java/dev/ambivalenton/openv/data/repository/TransitProviderFactory.kt) erstellt heute einen EFA-Adapter. Für DELFI ist ein Konfigurationsprofil vorhanden. Ein TRIAS-Adapter fehlt, und [Gradle](../app/build.gradle.kts) lehnt diese Protokollwahl beim Build ab.

## Deutschlandweite Auskunft anbinden

DELFI beschreibt den Zugang zu **DELFI-Navi** über die TRIAS-Schnittstelle eines Landesauskunftssystems. Welche Prognosen und Störungen eine Verbindung enthält, richtet sich nach den verfügbaren Daten der beteiligten Systeme. [Dienstbeschreibung von DELFI](https://www.delfi.de/de/leistungen-produkte/daten-dienste/)

| Funktion in Öpen-V | Für die Anbindung zu klären |
| --- | --- |
| Ziel wählen | Orts- und Haltestellensuche, Suche per Koordinate, Zuordnung der lokalen DHID zur Haltestellenreferenz des Dienstes |
| Verbindungen vergleichen | Fahrten ab Abfahrtszeit oder bis Ankunftszeit, frühere und spätere Verbindungen, Umstiege und Fußwege |
| Abfahrtstafel und Widgets | Abfahrten an einer Haltestelle, Sollzeit, Prognose, Ausfall, Gleis oder Steig |
| Angeheftete Fahrt begleiten | Stabile Fahrtreferenz, gezielte Aktualisierung einer gewählten Fahrt sowie verfügbare Störungsmeldungen und Gleiswechsel |

Für die Umsetzung sind folgende Schritte vorgesehen:

1. Mit dem Betreiber Test- und Produktivzugang, TRIAS-Version und unterstützte Dienste klären. Dazu gehören die erlaubte Anzeige in App, Widgets und Benachrichtigungen, Quellenangabe, Zwischenspeicherung, Abrufintervalle und Quoten pro Projekt oder Gerät.
2. Das Zugangsverfahren festlegen. `OPENV_TIMETABLE_ENDPOINT` wird beim Bauen in die APK übernommen. Diese Variable eignet sich für einen freigegebenen HTTPS-Endpunkt. Ein vertraulicher Schlüssel braucht das vom Betreiber vorgesehene Zugangsmodell.
3. Einen TRIAS-Adapter für [TransitRepository](../app/src/main/java/dev/ambivalenton/openv/data/repository/TransitRepository.kt) implementieren. Mit freigegebenen Beispielen oder synthetischen Antworten Haltestellensuche, Verbindungssuche, Abfahrtstafel und Fahrtaktualisierung testen. Wichtige Fälle sind fehlende Prognosen, Ausfälle, Gleiswechsel und Umstiege über Landesgrenzen. Falls der Betreiber einen kompatiblen EFA-Zugang anbietet, dieselben Fälle gegen den vorhandenen Adapter prüfen.
4. Erst mit funktionierendem Adapter die Protokollauswahl in der Factory aktivieren, die Build-Prüfung anpassen und den freigegebenen Endpunkt konfigurieren. Das Anfragebudget und die Aktualisierungen gemäß den Betriebsvorgaben einstellen.
5. Quellenangaben, Abdeckung, Datenschutz- und Hilfetexte sowie Store-Einträge anpassen. `python3 tools/sync_publication.py` und `python3 tools/check_release.py` ausführen. Die Vorschau mit echten Auskünften auf dem Gerät prüfen.

Die aktuellen Intervalle und die Regeln für den Umgang mit Antworten sind unter [Datenquellen und Abrufverhalten](data-sources.md) dokumentiert.
