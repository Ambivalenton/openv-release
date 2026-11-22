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

- androidx.activity:activity-compose:1.11.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.activity:activity-ktx:1.11.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.activity:activity:1.11.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.annotation:annotation-experimental:1.4.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.annotation:annotation-jvm:1.9.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.appcompat:appcompat-resources:1.7.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.appcompat:appcompat:1.7.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.arch.core:core-common:2.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.arch.core:core-runtime:2.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.autofill:autofill:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.cardview:cardview:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.collection:collection-jvm:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.collection:collection-ktx:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.animation:animation-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.animation:animation-core-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.foundation:foundation-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.foundation:foundation-layout-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.material3:material3-android:1.5.0-alpha18 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.material:material-icons-core-android:1.7.8 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.material:material-ripple-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-annotation-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-retain-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-saveable-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-geometry-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-graphics-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-text-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-unit-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-util-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.concurrent:concurrent-futures-ktx:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.concurrent:concurrent-futures:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.constraintlayout:constraintlayout-core:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.constraintlayout:constraintlayout:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.coordinatorlayout:coordinatorlayout:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core-ktx:1.17.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core-remoteviews:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core-viewtree:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core:1.17.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.cursoradapter:cursoradapter:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.customview:customview-poolingcontainer:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.customview:customview:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-core-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-core-okio-jvm:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-preferences-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-preferences-core-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-preferences-external-protobuf:1.1.7 — BSD-3-Clause · https://opensource.org/licenses/BSD-3-Clause
- androidx.datastore:datastore-preferences-proto:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.drawerlayout:drawerlayout:1.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.dynamicanimation:dynamicanimation:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.emoji2:emoji2-views-helper:1.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.emoji2:emoji2:1.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.fragment:fragment-ktx:1.8.9 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.fragment:fragment:1.8.9 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance-appwidget-external-protobuf:1.2.0 — BSD-3-Clause · https://opensource.org/licenses/BSD-3-Clause
- androidx.glance:glance-appwidget-proto:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance-appwidget:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance-material3:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.graphics:graphics-path:1.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.graphics:graphics-shapes-android:1.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.interpolator:interpolator:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-common-java8:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-common-jvm:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-livedata-core-ktx:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-livedata-core:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-livedata:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-process:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-runtime-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-service:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.loader:loader:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.navigation:navigation-common-android:2.9.5 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.navigation:navigation-compose-android:2.9.5 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.navigation:navigation-runtime-android:2.9.5 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.profileinstaller:profileinstaller:1.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.recyclerview:recyclerview:1.2.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.resourceinspection:resourceinspection-annotation:1.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.room:room-common:2.6.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.room:room-ktx:2.6.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.room:room-runtime:2.6.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.savedstate:savedstate-android:1.3.2 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.savedstate:savedstate-compose-android:1.3.2 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.savedstate:savedstate-ktx:1.3.2 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.sqlite:sqlite-framework:2.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.sqlite:sqlite:2.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.startup:startup-runtime:1.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.tracing:tracing-ktx:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.tracing:tracing:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.transition:transition:1.6.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.vectordrawable:vectordrawable-animated:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.vectordrawable:vectordrawable:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.versionedparcelable:versionedparcelable:1.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.viewpager2:viewpager2:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.viewpager:viewpager:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.window:window-core-android:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.window:window:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.work:work-runtime-ktx:2.10.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.work:work-runtime:2.10.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- co.touchlab:stately-concurrency-jvm:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- co.touchlab:stately-concurrent-collections-jvm:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- co.touchlab:stately-strict-jvm:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.android.material:material:1.13.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.code.gson:gson:2.10.1 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.errorprone:error_prone_annotations:2.15.0 — Apache 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.guava:listenablefuture:1.0 — Apache License 2.0 (Guava project) · https://github.com/google/guava/blob/master/COPYING
- com.jakewharton.timber:timber:5.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.squareup.okhttp3:okhttp:4.12.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.squareup.okio:okio-jvm:3.6.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- io.insert-koin:koin-android:4.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- io.insert-koin:koin-core-jvm:4.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- io.insert-koin:koin-core-viewmodel-android:4.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.9.22 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-stdlib:2.3.21 — Apache-2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains:annotations:23.0.0 — The Apache Software License, Version 2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jspecify:jspecify:1.0.0 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.maplibre.gl:android-sdk-geojson:6.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.maplibre.gl:android-sdk-turf:6.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.maplibre.gl:android-sdk:11.13.5 — BSD · https://opensource.org/licenses/BSD-2-Clause
- org.maplibre.gl:maplibre-android-gestures:0.0.4 — BSD · https://opensource.org/licenses/BSD-2-Clause

---

# Licences & data sources

Öpen-V combines free software with public data. Find attribution, terms and complete software licence texts here, even offline. External sources open only after a tap.

## App code and design

Own app code: **GNU GPL version 3 or later (GPL-3.0-or-later)**. Copyright 2026 Ambivalenton, to the extent rights exist. Earlier Apache-2.0 grants remain valid. Third-party attribution is preserved; no exclusive rights are claimed in purely AI-generated material.

The project’s own vector/canvas designs, icon variants and texts use this licence. Operating systems, art periods and cities inspire some designs. This implies no manufacturer affiliation or inclusion of third-party system logos, original wallpapers or proprietary fonts.

> **The app licence is separate from server access.** The GPL does not automatically permit unrestricted use of external transit, map or weather services. Their terms and capacity limits also apply.

## Timetables, stops and place search

### Current services

Connections and departures come from [VRR OpenService](https://www.opendata-oepnv.de/ht/de/api), Verkehrsverbund Rhein-Ruhr, without warranty. API terms and the GPL app licence are separate.

### Nationwide stop search

Local search directory: **Central Stop Directory, © DELFI e.V., export 2 November 2026**. Additional names and identifier mappings: **VRR scheduled timetable data (GTFS), © Verkehrsverbund Rhein-Ruhr AöR**, feed version 20261013, valid 1 September 2026 to 28 February 2027.

Sources: [DELFI on OpenData ÖPNV](https://www.opendata-oepnv.de/ht/de/organisation/delfi/startseite) · [VRR datasets](https://www.opendata-oepnv.de/ht/de/organisation/verkehrsverbuende/vrr/openvrr/datensaetze).

Download metadata points to [Creative Commons Attribution (CC-BY)](https://opendefinition.org/licenses/cc-by/) without explicitly naming a numerical version. That original reference is preserved; its overview links to [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). An overview link is not treated as a retroactively specified licence version.

Modifications: selecting root-level stops, normalising and indexing search terms, and mapping VRR identifiers through documented parent/DHID fields. Entries with a last service day before the export date are omitted from suggestions; this alone does not prove permanent closure. Identifiers are not truncated to guess parents. The expired separate line CSV is not included.

The directory does not update automatically. It is **not an offline timetable or real-time access**. Online search runs alongside it. No endorsement or approval by the data providers is claimed.

### Additional place suggestions

[Photon by komoot](https://github.com/komoot/photon) provides place/address suggestions through an HTTP API using OpenStreetMap data. Photon server code is not embedded in the app. OSM data: © OpenStreetMap contributors, ODbL.

## Maps, walking guidance and weather

| Source | Use and conditions |
| --- | --- |
| OpenStreetMap | © OpenStreetMap contributors, ODbL. [Copyright and licence](https://www.openstreetmap.org/copyright). |
| Map tiles | Visible areas from the selected server, by default tile.openstreetmap.org. [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/). Bounded caches, no offline prefetch. |
| Valhalla | Detailed walking instructions over HTTP, by default valhalla1.openstreetmap.de using OSM data. [Project](https://valhalla.github.io/valhalla/) and [public demo / fair use](https://valhalla.github.io/valhalla/start/introduction/). No embedded server code. |
| Open-Meteo | Weather data under CC BY 4.0, locally rephrased into arrival hints. [Licence](https://open-meteo.com/en/licence), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) and [service terms](https://open-meteo.com/en/terms). The free hosted API is restricted to non-commercial use and limited request volume. |

External navigation opens your selected map service after a tap. Its terms apply independently. Map and weather data may be incomplete; attribution is not a guarantee of accuracy.

## Fonts, icons and editorial content

The 62 bundled, unchanged fonts from [google/fonts](https://github.com/google/fonts) use the **SIL Open Font License 1.1**. Open complete licence and attribution notices from each font selection using Show licence. Source revision and checksums are documented in localization/fonts.json and docs/FONTS.md. Font licences apply alongside the app’s GPL.

Material Icons and the included Material Color Utilities algorithms: Copyright Google LLC / The Android Open Source Project, **Apache-2.0**. Original headers are retained; package/import paths were adjusted. [Material Icons](https://github.com/google/material-design-icons) · [Material Color Utilities](https://github.com/material-foundation/material-color-utilities).

Facts are original short summaries. Sources and stable content IDs are recorded in the project’s source catalogue; available sources can also be opened from text history. No generative AI service creates text during use.

## Libraries in this revision

The following resolved runtime inventory names components and their licences. Unmodified MapLibre notices may describe optional SDK parts; this list identifies the components actually resolved for the app.

- androidx.activity:activity-compose:1.11.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.activity:activity-ktx:1.11.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.activity:activity:1.11.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.annotation:annotation-experimental:1.4.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.annotation:annotation-jvm:1.9.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.appcompat:appcompat-resources:1.7.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.appcompat:appcompat:1.7.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.arch.core:core-common:2.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.arch.core:core-runtime:2.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.autofill:autofill:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.cardview:cardview:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.collection:collection-jvm:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.collection:collection-ktx:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.animation:animation-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.animation:animation-core-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.foundation:foundation-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.foundation:foundation-layout-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.material3:material3-android:1.5.0-alpha18 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.material:material-icons-core-android:1.7.8 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.material:material-ripple-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-annotation-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-retain-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.runtime:runtime-saveable-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-geometry-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-graphics-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-text-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-unit-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.compose.ui:ui-util-android:1.11.0-beta02 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.concurrent:concurrent-futures-ktx:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.concurrent:concurrent-futures:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.constraintlayout:constraintlayout-core:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.constraintlayout:constraintlayout:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.coordinatorlayout:coordinatorlayout:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core-ktx:1.17.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core-remoteviews:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core-viewtree:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.core:core:1.17.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.cursoradapter:cursoradapter:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.customview:customview-poolingcontainer:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.customview:customview:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-core-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-core-okio-jvm:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-preferences-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-preferences-core-android:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.datastore:datastore-preferences-external-protobuf:1.1.7 — BSD-3-Clause · https://opensource.org/licenses/BSD-3-Clause
- androidx.datastore:datastore-preferences-proto:1.1.7 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.drawerlayout:drawerlayout:1.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.dynamicanimation:dynamicanimation:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.emoji2:emoji2-views-helper:1.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.emoji2:emoji2:1.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.fragment:fragment-ktx:1.8.9 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.fragment:fragment:1.8.9 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance-appwidget-external-protobuf:1.2.0 — BSD-3-Clause · https://opensource.org/licenses/BSD-3-Clause
- androidx.glance:glance-appwidget-proto:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance-appwidget:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance-material3:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.glance:glance:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.graphics:graphics-path:1.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.graphics:graphics-shapes-android:1.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.interpolator:interpolator:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-common-java8:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-common-jvm:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-livedata-core-ktx:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-livedata-core:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-livedata:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-process:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-runtime-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-service:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.lifecycle:lifecycle-viewmodel:2.9.4 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.loader:loader:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.navigation:navigation-common-android:2.9.5 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.navigation:navigation-compose-android:2.9.5 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.navigation:navigation-runtime-android:2.9.5 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.profileinstaller:profileinstaller:1.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.recyclerview:recyclerview:1.2.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.resourceinspection:resourceinspection-annotation:1.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.room:room-common:2.6.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.room:room-ktx:2.6.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.room:room-runtime:2.6.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.savedstate:savedstate-android:1.3.2 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.savedstate:savedstate-compose-android:1.3.2 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.savedstate:savedstate-ktx:1.3.2 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.sqlite:sqlite-framework:2.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.sqlite:sqlite:2.4.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.startup:startup-runtime:1.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.tracing:tracing-ktx:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.tracing:tracing:1.2.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.transition:transition:1.6.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.vectordrawable:vectordrawable-animated:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.vectordrawable:vectordrawable:1.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.versionedparcelable:versionedparcelable:1.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.viewpager2:viewpager2:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.viewpager:viewpager:1.0.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.window:window-core-android:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.window:window:1.5.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.work:work-runtime-ktx:2.10.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- androidx.work:work-runtime:2.10.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- co.touchlab:stately-concurrency-jvm:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- co.touchlab:stately-concurrent-collections-jvm:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- co.touchlab:stately-strict-jvm:2.1.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.android.material:material:1.13.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.code.gson:gson:2.10.1 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.errorprone:error_prone_annotations:2.15.0 — Apache 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.google.guava:listenablefuture:1.0 — Apache License 2.0 (Guava project) · https://github.com/google/guava/blob/master/COPYING
- com.jakewharton.timber:timber:5.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.squareup.okhttp3:okhttp:4.12.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- com.squareup.okio:okio-jvm:3.6.0 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- io.insert-koin:koin-android:4.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- io.insert-koin:koin-core-jvm:4.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- io.insert-koin:koin-core-viewmodel-android:4.1.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.9.22 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlin:kotlin-stdlib:2.3.21 — Apache-2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0 — Apache-2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jetbrains:annotations:23.0.0 — The Apache Software License, Version 2.0 · https://www.apache.org/licenses/LICENSE-2.0.txt
- org.jspecify:jspecify:1.0.0 — The Apache License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.maplibre.gl:android-sdk-geojson:6.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.maplibre.gl:android-sdk-turf:6.0.1 — The Apache Software License, Version 2.0 · http://www.apache.org/licenses/LICENSE-2.0.txt
- org.maplibre.gl:android-sdk:11.13.5 — BSD · https://opensource.org/licenses/BSD-2-Clause
- org.maplibre.gl:maplibre-android-gestures:0.0.4 — BSD · https://opensource.org/licenses/BSD-2-Clause

Full licence texts: app/src/main/assets/licenses/ and the offline app notices.
