# Änderungen

Alle nennenswerten Änderungen an Öpen-V. Versionen folgen [Semantic Versioning](https://semver.org/lang/de/); in Klammern steht der Android-`versionCode`.

## [Unveröffentlicht]

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

[Unveröffentlicht]: https://github.com/Ambivalenton/openv-release/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/Ambivalenton/openv-release/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/Ambivalenton/openv-release/releases/tag/v1.0.0
