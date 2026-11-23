# Änderungen

Alle nennenswerten Änderungen an Öpen-V. Versionen folgen [Semantic Versioning](https://semver.org/lang/de/); in Klammern steht der Android-`versionCode`.

## [Unveröffentlicht]

## [1.0.5] (105) · 2026-11-23

- Zeitstrahl der Verbindungsdetails: Linien enden genau an ihren Haltepunkten. Bisher malte jede Zeile ihr Stück Linie bis an ihren eigenen Rand – die Hinweis-Zeile vor einem Halt zog die Linie der Fahrt über den Einstiegspunkt hinaus, die Ankunftszeile ließ sie unter dem Ausstiegspunkt weiterlaufen (mit grauem Stummel vor einem Fußweg). Jetzt wird die Schiene einmal für die ganze Reise von Punkt zu Punkt gezeichnet; Fußspuren füllen genau den Weg zwischen ihren beiden Punkten.
- Neu gestaltet: Jede Fahrt steht als ruhiger Block in einem Hauch ihrer Linienfarbe; Start und Ziel der Reise haben einen eigenen Ring, Zwischenhalte sitzen wie auf einem Liniennetzplan als kleine Punkte auf der Linie. Umstiege stehen als kleine Uhr-Kapsel („8 Min. Umstieg“), eine ersetzte Fahrplanzeit ist durchgestrichen.
- Bewegung: Beim Öffnen einer Verbindung zeichnet sich die Schiene von oben ein, die Punkte springen kurz auf, die Zeilen gleiten an ihren Platz. Aufgeklappte Zwischenhalte erscheinen Halt für Halt federnd; Pfeil und Beschriftung von „Zwischenhalte anzeigen“ und „Hinweise“ wechseln mit einer Feder. Im Ruhemodus bleibt alles still.
- „Abfahrt/Ankunft“: Die Kapsel schwingt beim Hereinschießen wie ein kleiner Tropfen – sie hängt zurück und dehnt sich, schwingt beim Abbremsen weiter, wieder zurück und kommt weich zur Ruhe. An der Kante der Pille wird sie gestaucht, statt hart anzuschlagen.
- Tests: Robolectrics Android-Laufzeit kommt über Gradle (mit Cache) statt bei jedem Lauf von Maven Central; gedrosselte Downloads ließen zuvor ganze CI-Läufe scheitern.

## [1.0.4] (104) · 2026-11-23

- Netzplan bearbeiten: Der ausgeklappte Bereich ist doppelt so hoch. Oben stehen die drei Gesten als schlichter Text mit Trennlinien (ohne eigene Fläche, damit sie nicht wie weitere Knöpfe wirken) – „Halten und schieben“ verschiebt, „Ziehen“ verbindet eine Linie, „Tippen“ öffnet die Einstellungen des Favoriten –, darunter wie bisher „Favoriten-Einstellungen“, „Favorit hinzufügen“ und Fertig. Der Netzplan bleibt an seinem Platz; der Bereich wächst nach oben.
- „Abfahrt/Ankunft“ fliegt dafür beim Ausklappen federnd links aus dem Bild und schießt beim Einklappen wieder herein; die Kapsel im Schalter schwingt dabei mit eigener Feder etwas weiter und zurück.
- Favoriten löschen: Hält man einen Favoriten zum Verschieben, weichen die beiden Knöpfe federnd einer roten Zone „Hierhin verschieben, um den Favoriten zu löschen“ (bei roten Designs eine andere Signalfarbe). Darüber heißt es „Loslassen zum Löschen“; der Favorit ist dann gelöscht, „Rückgängig“ holt ihn mit seinen Linien zurück.
- „Favorit löschen“ in den Einstellungen eines Favoriten steht in einem eigenen Rahmen in derselben Signalfarbe.

## [1.0.3] (103) · 2026-11-23

- Balkendiagramm bei „Ankunft“: keine Lücke mehr zwischen den Balken. Die Vorab-Suche holte die nächste spätere Verbindung ab der Ankunftszeit als Abfahrtszeit; das Diagramm lud dann „später“ erst ab dort weiter, und alle Fahrten dazwischen fehlten. Jetzt schließt die spätere Seite an die letzte noch rechtzeitig ankommende Verbindung an.
- Ladefolge der Verbindungssuche: Schon bei der Zielwahl werden die nächsten zwei Verbindungen und die letzte davor gesucht. Nach „Suchen“ stehen sie sofort da, ohne erneute Anfrage; dann folgen drei spätere und danach drei frühere, anschließend je drei in Wischrichtung. Vorher lud die Suche automatisch 15 Verbindungen in fünf Wellen und fragte die erste Seite trotz Vorab-Suche noch einmal ab.
- Frühere Verbindungen: Das erste Zeitfenster richtet sich nach dem Takt der schon geladenen Verbindungen – meist eine Anfrage statt drei (10, 30, 90 Minuten zurück).
- Gemessen mit simuliertem Dienst (800 ms je Anfrage, 20-Minuten-Takt): Vorab-Suche 0,8 s statt 1,6 s; Balken nach „Suchen“ sofort statt nach 0,8 s; Hintergrund fertig nach 1,6 s mit 2 Anfragen statt nach 8,0 s mit 10.
- Fehler bei der Suche: nur noch eine Meldung. Neben der Fehlerkarte der Suche stehen keine weiteren Zeilen; scheitern frühere und spätere Verbindungen zugleich, gibt es eine gemeinsame Zeile mit einem „Erneut versuchen“.

## [1.0.2] (102) · 2026-11-23

- Favorit hinzufügen: Das Blatt öffnet sich nicht mehr ein zweites Mal, wenn die Tastatur erscheint. Ursache war, dass das Blatt seine Bewegung bei jeder Änderung der Fensterhöhe neu anlegte; jetzt behält es seine Oberkante, nur der untere Teil weicht der Tastatur, und die Tastatur kommt erst, wenn das Blatt gelandet ist.
- Favorit hinzufügen: Suchfeld direkt unter einem einzeiligen Kopf, Treffer gleich darunter und über der Tastatur. Beim Tippen treten Rollen und „Oft gesucht“ zur Seite; Frage-Überschrift und Hinweistext entfallen.
- Vorschläge („Oft gesucht“) nur noch aus Zielen, die mindestens zweimal gesucht wurden – nie ein Favorit oder ein Ort im Umkreis von 150 m um einen Favoriten, keine Haltestellen der Anzeigetafel.
- Ersteinrichtung in drei kurzen Schritten: Freigaben (Standort und Mitteilungen mit je einem „Erlauben“), erster Favorit, Design (sechs ausgewählte Designs als Kacheln, sofort angewendet). Übersicht-, Netzplan- und Zusammenfassungsseite entfallen.
- Netzplan: Beim Öffnen der Bearbeitung bleibt der Plan auf derselben Höhe (die Werkzeugleiste belegt immer ihre offene Höhe). In der normalen Ansicht sind keine Rasterpunkte mehr zu sehen; beim Öffnen erscheinen erst Punkte, die zu Plus-Zeichen wachsen. Der Knopf „Favorit hinzufügen“ ist 10 % kleiner.
- „Fahrplandaten: VRR“ steht unter dem Seitentitel; Anzeigetafel und angeheftete Reise auf der Übersicht wiederholen es nicht mehr.

## [1.0.1] (101) · 2026-11-23

- Favoriten hinzufügen neu gestaltet – in der Ersteinrichtung und später gleich: drei ruhige Schritte statt einer vollen Seite. Erst der Ort (Suche, zuletzt gesuchte Ziele oder vorher „Zuhause“, „Arbeit“ … antippen), dann das Aussehen mit großem Live-Symbol, Name, Ortsart als große Kacheln und Farbe, dann ein „Gespeichert“-Moment mit dem nächsten Schritt („Mit „Zuhause“ verbinden“, „Noch einen Favoriten“, „Fertig“). Alle Symbole, Farben und Formen klappen direkt im Blatt auf, es öffnen sich keine weiteren Fenster; Übergänge federn, Symbol und Farbe springen bei jeder Änderung kurz an. Das Hinweisbanner über dem Netzplan entfällt.
- Ersteinrichtung: Der Startbildschirm mit „Verbindung suchen“ und „App einrichten“ erscheint wieder. Seit Build 96 lag er unter der Hauptansicht und war weder sichtbar noch bedienbar; dadurch blieben auch Tutorial, Navigationsleiste und automatischer Standort-Start aus. Start, Einrichtung und Abschluss gehen jetzt fließend ineinander über.
- Navigationsleiste: Übersicht, Suchen und Abfahrten haben in jedem farbigen Design klar verschiedene Kapselfarben (mindestens 70° Farbton auseinander), aus den Farben des Designs oder seiner Farbharmonie. Beim Wandern blendet die Kapsel über den Farbkreis statt durch Grau.
- Netzplan bearbeiten: Freie Rasterpunkte erscheinen als kleine graue Plus-Zeichen.

## [1.0.0] (100) · 2026-11-22

Erste Version.

- Favoriten mit Symbol, Farbe und Zeitregeln als eigener Netzplan; ein Tipp auf eine Strecke sucht die nächsten Verbindungen.
- Balkendiagramm mit kommenden und gerade verpassten Verbindungen, Zeitstrahl und Textübersicht als lesbarer Satz.
- Anzeigetafel in 16 Designs und Widgets für den Startbildschirm; Widgets aktualisieren sich nur bei eingeschaltetem Bildschirm.
- Reisebegleitung für eine angeheftete Fahrt mit Benachrichtigung, Störungshinweisen und erreichbaren Alternativen.
- Über hundert App-Designs, hell, dunkel und AMOLED; Deutsch und Englisch.
- Fahrplandaten von VRR OpenService, Haltestellenverzeichnis für Deutschland offline mitgeliefert.
- Test-App „Öpen-V · Vorschau“ (`dev.ambivalenton.openv.next`), die sich neben der Store-App installiert.

[Unveröffentlicht]: https://github.com/Ambivalenton/openv-release/compare/v1.0.5...HEAD
[1.0.5]: https://github.com/Ambivalenton/openv-release/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/Ambivalenton/openv-release/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/Ambivalenton/openv-release/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/Ambivalenton/openv-release/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/Ambivalenton/openv-release/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/Ambivalenton/openv-release/releases/tag/v1.0.0
