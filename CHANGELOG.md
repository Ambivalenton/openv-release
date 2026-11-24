# Änderungen

Alle nennenswerten Änderungen an Öpen-V. Versionen folgen [Semantic Versioning](https://semver.org/lang/de/); in Klammern steht der Android-`versionCode`.

## [Unveröffentlicht]

## [1.1.4] (114) · 2026-11-24

- Balkendiagramm, Gesamtdauer: Die Minuten-Abzeichen über den Verbindungen richten sich nach den Verbindungen im Blick. Kürzere werden leicht größer (Schrift und Fläche), längere leicht kleiner; kürzere neigen sich etwas zur Akzentfarbe, längere zu einem ruhigen Grau. Wenige Minuten Unterschied bleiben dezent, doppelt so lange Fahrten sieht man deutlich. Die kürzeste Verbindung im Blick steht in der Akzentfarbe, die längste ist klar ausgegraut – ohne zusätzliche Größe. Beim Wischen wechselt alles mit einer weichen Feder; TalkBack nennt „kürzeste im Blick“ und „längste im Blick“.
- Uhrzeitlinie einstellbar: Im Balkendiagramm-Design öffnet „Darstellung im Diagramm“ (bisher „Text & Fußabdrücke“, ein Ausklappmenü) eine eigene Unterseite; der Eintrag ist als Einstellungszeile gestaltet und steht über der Überschrift „Designs“. Der neue Rahmen „Uhrzeitlinie“ hat oben einen Schalter zum Ausblenden und eine Live-Vorschau. Wählbar sind die Uhr (passend zum Design, digital, analog, beides oder ohne), die Linie (passend zum Design, durchgezogen, gestrichelt, gepunktet, Strich-Punkt, Doppellinie, Gleis mit Schwellen, Welle, leuchtend, Verlauf, Pfeile, Pixel, Naht), Stärke, Farbe (Akzent, Zweitfarbe, Textfarbe) und Sekunden. „Passend zum Design“ gibt etwa Swiss- und Editorial-Designs eine Bahnhofsuhr, Glas und Neon eine leuchtende Linie, 8-Bit Pixel, Metro-Schienen ein Gleis und Textil eine Naht.
- „Text im Balken“ neu aufgebaut: ein Schalter zum Ein- und Ausblenden, drei Stil-Kacheln („Schlicht“, „Mit Schatten“, „Plakette“), die den Stil im aktuellen Balken-Design zeigen, und nur bei „Schlicht“ ein Regler für Größe und Deckkraft. Der bisherige Kombi-Regler mit Rastpunkten und Erklärtext entfällt; gespeicherte Einstellungen bleiben erhalten.
- Netzplan: Die Bearbeitungsleiste schwingt mit einer weicheren Feder auf, und die Felder „Halten und schieben“, „Ziehen“ und „Tippen“ federn nacheinander ein. Bisher wurden ihre Texte in jedem Bild der Animation neu vermessen und wackelten kurz und starr.
- Behoben: Beim Gedrückthalten des Netzplans erschienen die Schatten der Namensschilder erst nach den Schildern. Schild und Schatten blenden jetzt gemeinsam ein, ebenso die Abfahrtszeiten an den Linien.

## [1.1.3] (113) · 2026-11-24

- Jedes Design hat seine eigene Farbwelt: Die Farbvorschläge für Favoriten und für die Farben des Designs (Akzent, Hintergrund, Flächen, Text) kommen jetzt aus den Farbtönen des Designs selbst – sein Akzent und seine Zweitfarbe in helleren, tieferen und ruhigeren Formen, ihre Nachbarn, Gegenfarben und Dreiklang-Partner. Bisher teilten sich bis zu dreißig Designs dieselben zehn Farben. Jeder Vorschlag ist auf dem Grund des Designs lesbar (Akzente mindestens 3:1, Textfarben 7:1), Hintergründe behalten die Helligkeit des Designs, und jede Farbe trägt ihren Namen („Dunkles Blau“, „Helles Grün“).
- Schrift passt sich ihrem Platz an: Bei großer Systemschrift bleiben Beschriftungen in ihren Kapseln, Knöpfen und Feldern, statt hinauszuragen oder abgeschnitten zu werden. Ein einzelnes Wort wird kleiner, aber nie getrennt; ein langer Knopftext wie „Verbindung suchen, damit du am Mittwoch um 08:15 ankommst“ nimmt eine zweite Zeile (nur zwischen Wörtern), bevor er schwer lesbar würde. Knöpfe behalten dabei ihre Form.
- Navigationsleiste: „Übersicht“, „Suchen“ und „Abfahrten“ stehen immer innerhalb der Kapsel – auch in sechseckigen und abgeschrägten Kapseln – und in einer gemeinsamen Größe, damit kein Wort größer wirkt als sein Nachbar.
- Auch „Haltestellen-Liste“ und „Design“ unter der Anzeigetafel, die Knöpfe der angepinnten Reise, Liniennamen in der Abfahrtsliste, die Titel im Favoriten-Studio und der Hinweis im Suchfeld der Einstellungen werden bei großer Schrift kleiner statt abgeschnitten.
- Behoben: In den Auswahlblasen der Einstellungen wurden lange Wörter getrennt („Kompaktansich-t öffnen“, „Favoriten-Symbol frei bewegen“), der Umkreis-Text unter „Favoriten“ wurde unten abgeschnitten, und die Mini-Navigationsleiste in der Design-Vorschau brach „Abfahrten“ um. Auch die Übersicht-Vorschau der Ersteinrichtung bricht nur noch zwischen Wörtern. Geprüft mit doppelter Systemschrift auf allen Seiten und Einstellungen in sieben Designs.

## [1.1.2] (112) · 2026-11-24

- Farbwahl neu aufgebaut: Beim Scrollen ändert sich keine Farbe mehr. Farbfläche, Farbton- und Deckkraft-Streifen liegen nie mehr in einem scrollenden Bereich; nur die Vorschläge darunter scrollen. Bisher konnte ein Finger, der scrollen wollte, auf der Fläche landen und die Farbe verstellen.
- Eine Fläche statt drei Ebenen: Das Farbblatt hat oben immer die gewählte Farbe groß mit Namen („Dunkles Blau“, „Helles Grün“), Code und „Vorher“ zum Zurücksetzen, darunter den Umschalter „Auswahl | Mischen“ und unten „Übernehmen“ in der gewählten Farbe. „Eigene Farbe wählen“ öffnet keinen zweiten Dialog mehr; die Mischpalette führt direkt hinein.
- Mischen: Eine Lupe über dem Finger zeigt die Farbe, die der Finger verdeckt; feine Haptik an den Rändern der Fläche und bei den reinen Farbtönen. Ein Tipp in die Ecken erreicht jetzt reines Weiß, Schwarz und die volle Farbe (vorher schluckten die abgerundeten Ecken den Tipp), die Markierung bleibt auch am Rand ganz sichtbar. Der Hex-Code erklärt, welche Zeichen er erwartet.
- Bildschirm-Epochen: Die Tafel „Display“ zeigt jede Epoche auf dem Bildschirm ihrer Zeit – Röhrenmonitor in Weiß, Blau, Bernstein und Grün für 1984 bis 1997, frühe Flachbildschirme ab 2000, E-Ink für 2011 und ein heller Kachel-Bildschirm für 2012 – statt das Systemfenster der App-Theme-Tafel zu wiederholen.

## [1.1.1] (111) · 2026-11-24

- Glas-Designs schweben über farbigem Licht: Hinter den Scheiben liegen vier große, weiche Lichter in den Farbtönen des Designs (Akzent, Zweitfarbe und ihre Mischungen), sodass Karten, Leiste und Netzplan wirklich wie Glas wirken. Die Lichter bleiben nahe an der Helligkeit des Grundes; Texte darauf bleiben in jedem Glas-Design lesbar (geprüft für alle).
- Anzeigetafel mit Segmentziffern: Bei einer Verspätung stehen die Ziffern jetzt bündig mit den übrigen Zeilen und in gleicher Größe; die Verspätung folgt dahinter.

## [1.1.0] (110) · 2026-11-24

- Design-Sammlungen bauen die App jetzt grundlegend verschieden auf, nicht nur in anderen Farben. Acht Sammlungen, die bisher nur Farbe und Muster wechselten, haben eine eigene Bauweise: Kosmos aus Glas über den Sternkarten, Minerale aus poliertem Chrom mit facettierten Ecken, Textil mit gesteppten Nähten (neue Bauweise „Naht & Stoff“), Architektur im Swiss-Raster mit farbigem Kopfband und kantigen Flächen, Café als Keramik-Relief, Druck als Editorial mit feinen Regeln, Gaming in 8-Bit mit Pixelecken und Stufenschatten, Haustiere aus weicher Knete. Karten, Felder, Knöpfe, Navigationsleiste, Balkendiagramm und Anzeigetafel folgen der Bauweise; die Motive der Sammlungen bleiben darunter sichtbar.
- Netzplan „App-Theme“: Kartenblatt und Stationen sind aus dem Material des Designs gebaut – Glasscheibe mit Lichtkante und Glaskugeln, Neonröhre mit Leuchtrand, Pixel-Sprites auf Rasterpunkten, Plakat mit Tuschkontur und Versatzschatten, pralle Knete, poliertes Chrom mit Fassung, geprägtes Relief, Papierlagen, gesteppter Stoff, Linienzeichnung, Druckringe, Terminalrahmen oder Swiss-Raster. Bisher sahen Blatt und Stationen in jedem Design gleich aus. Im Ruhemodus und bei hohem Textkontrast entfallen Glanz, Leuchten und Texturen; Formen und Konturen bleiben.
- Anzeigetafel „Display“ aus dem Material der Sammlung: Textil als Stickbild (Kreuzstich-Buchstaben auf Leinen, aufgenähte Linienflicken, gesteppter Rahmen), Minerale als polierte Steinplatte mit Adern, gravierter Schrift und Facettenrand, Haustiere und dunkle Café-Designs als Letterboard (Filz mit Rillen, Steckbuchstaben, Eichenrahmen). Jedes Gaming-Design hat seinen eigenen Spielbildschirm, vom Rollenspiel-Fenster über farbige Handheld-LCDs bis zur Konsole. Wie alle Displays steht die Tafel im Helligkeitskontrast zum Design: helle Designs zeigen einen dunklen Bildschirm, dunkle einen hellen.

## [1.0.9] (109) · 2026-11-24

- App-Designs kuratiert: Alle Designs wurden mit Übersicht, beiden Anzeigetafeln, Balkendiagramm und Netzplan nebeneinander geprüft. 105 Designs, die einem Geschwister nahezu glichen, sind aus der Auswahl genommen; 240 bleiben, jede Sammlung weiterhin mit hellen und dunklen Varianten. „Weich & taktil“ geht in „Sanftes Relief“ und „Knete & Clay“ auf, der Neo-Brutalismus aus „Kante & Kontrast“ in seine eigene Sammlung. Wer ein entferntes Design nutzt, behält beim Update das nächstverwandte (gleiche Helligkeit, meist dieselbe Sammlung) samt eigener Farben; auch Tag/Nacht, Zufallsdesign und gespeicherte eigene Designs werden umgestellt.
- „Betriebssysteme“ heißt jetzt „Bildschirm-Epochen“: zehn deutlich verschiedene Oberflächen von 1984 bis 2012, nach Jahr geordnet und ohne Markennamen (etwa „2001 · Hügelblau“, „1995 · Systemgrau“).
- Anzeigetafeln „Display“ und „App-Theme“: Uhrzeiten werden nie mehr abgeschnitten („23:…+2“) – bei Verspätung bekommt die Zeit mehr Platz und wird notfalls kleiner, das Ziel weicht. Schriften mit hoher Zeilenbox (etwa Source Sans 3, Baloo 2) erscheinen in voller Größe und sitzen mittig, statt auf zwei Drittel zu schrumpfen.

## [1.0.8] (108) · 2026-11-24

- „Eigene Farbe“ neu gestaltet: eine große Farbfläche (links blass, rechts kräftig, oben hell, unten dunkel) und ein Farbton-Regenbogen folgen dem Finger sofort – das Farbrad mit Gedrückthalten und die vier Schieberegler entfallen. Deckkraft als eigener Streifen, wo eine Farbe durchscheinen darf. Oben „Vorher“ und neue Farbe nebeneinander mit Leseprobe „Aa“ (ein Tipp auf „Vorher“ setzt zurück) und das Feld für den Hex-Code. Darunter mit einem Tipp: hellere und dunklere Stufen derselben Farbe, passende Farben (Nachbarn, Gegenfarbe, Dreiklang), der Akzent des App-Designs und zuletzt gewählte Farben. Klare Knöpfe „Abbrechen“ und „Übernehmen“ (in der gewählten Farbe). TalkBack: Fläche und Streifen nennen ihre Werte und lassen sich ohne Ziehen verstellen.
- Der Zugang „Eigene Farbe wählen“ ist eine eigene Zeile mit Farbkreis und zeigt die zuletzt gemischte Farbe; im Favoriten-Dialog und in der Farbauswahl gleich.
- Mischpalette: Sie federt aus dem Knopf heraus – die Breite eilt der Höhe etwas voraus, sodass sie wie ein Tropfen aufquillt –, schwingt leicht über und kommt zur Ruhe; die Farbnäpfe ploppen nacheinander auf. Beim Schließen sammeln sich erst die Näpfe, dann zieht sich die Palette ohne Nachschwingen zurück. Sie bleibt dabei immer ganz auf dem Bildschirm. Im Ruhemodus ohne Bewegung.

## [1.0.7] (107) · 2026-11-24

- Favoriten-Widget neu gestaltet: Jeder Favorit ist eine Kachel in seiner Farbe mit Symbol und – wo Platz ist – Namen; breite Kacheln nennen auch die Haltestelle. Die Anordnung passt sich jeder Größe an: 1×1 zeigt den wichtigsten Favoriten mit Namen, flache Widgets eine Reihe, schmale hohe eine Liste, große Widgets große Kacheln mit Suchknopf. Hervorgehobene Favoriten stehen vorn in kräftiger Farbe. Widgets mit nur einer Kachelreihe scrollen nicht mehr, sondern zeigen, was hineinpasst; bisher blieb 1×1 leer und es gab nirgends Namen.
- Behoben: Die in den Einstellungen gewählten Widget-Farben (Hintergrund, Symbole) wurden beim Speichern zurückgesetzt. Die Schalter für Schatten entfallen, das Widget ist flach.
- Abfahrten-Widget neu gestaltet: echte Schrift statt einer gezeichneten Tafel. Oben Haltestelle, Stand und Aktualisieren; darunter so viele Abfahrten wie passen mit Linie in ihrer Farbe, Ziel, Abfahrtszeit, Gleis und Verspätung (ausgefallene Fahrten durchgestrichen); unten die Quelle der Fahrplandaten. Flache Widgets zeigen die nächste Abfahrt, schmale setzen das Ziel unter Linie und Zeit. Es bleiben Uhrzeiten statt eines Countdowns, weil ein Widget nicht jede Minute neu gezeichnet wird; der Stand zeigt, wie frisch die Daten sind. Ein Tipp auf eine Abfahrt öffnet ihren Zeitstrahl. Design- und Einstellungsknopf entfallen; die Anzeigetafel in der App behält ihre Designs.
- Navigationsleiste: In den Einstellungen tritt sie zur Seite; auf „Design & Darstellung“ (dort wird das App-Design gewählt) bleibt sie sichtbar. Beim Bearbeiten des Netzplans bleibt ihr Platz frei, sodass der Plan an seiner Stelle bleibt, statt nach unten zu springen.
- Beide Widgets nehmen die Farben des App-Designs für Tag und Nacht und runden ihre Ecken wie der Launcher (ab Android 12). Die Vorschauen in der Widget-Auswahl zeigen das neue Aussehen.

## [1.0.6] (106) · 2026-11-23

- Navigationsleiste: Die Auswahlkapsel landet ohne Sprung (Federimpuls statt sofortiger Stauchung). Symbole reagieren, wenn die Kapsel sie erreicht: ein kleiner Hüpfer beim Durchfahren, ein voller mit Landung am Ziel. Beim Darübergleiten nehmen sie die Farbe der Kapsel an, beim Drücken geben sie leicht nach. Zug, Lupe und Übersicht bewegen sich mit Federn statt fester Zeitkurven.
- Navigationsleiste weicht aus: In geöffneten Verbindungen (Ergebnisse, Vorschau aus Übersicht und Anzeigetafel) und beim Bearbeiten des Netzplans hebt sie sich kurz und gleitet nach unten hinaus; danach kommt sie mit einer Feder zurück, ebenso nach dem Schließen der Tastatur. Übersicht, Abfahrten, Ergebnisliste, Reisebegleitung und Einstellungen behalten sie. Ohne Leiste halten Inhalte Abstand zur Gestenleiste des Systems.
- Planermodus: Gedrückthalten eines Favoriten im Netzplan trägt ihn oben als Start ein (eigene Einstellung, voreingestellt). „In Übersicht anzeigen“ wird dort nicht angeboten, weil der Planermodus keine Übersicht hat.
- Vorschau-Releases: sachliche Beschreibung mit kurzer Änderungsliste aus den Store-Texten (Deutsch, Englisch ausklappbar) und Installationshinweis.

## [1.0.5] (105) · 2026-11-23

- Zeitstrahl der Verbindungsdetails: Linien enden genau an ihren Haltepunkten. Bisher malte jede Zeile ihr Stück Linie bis an ihren eigenen Rand – die Hinweis-Zeile vor einem Halt zog die Linie der Fahrt über den Einstiegspunkt hinaus, die Ankunftszeile ließ sie unter dem Ausstiegspunkt weiterlaufen (mit grauem Stummel vor einem Fußweg). Jetzt wird die Schiene einmal für die ganze Reise von Punkt zu Punkt gezeichnet; Fußspuren füllen genau den Weg zwischen ihren beiden Punkten. Erkannte Hinweise (etwa „Haltestelle vorübergehend verlegt“) behalten ihr Ausrufezeichen auf der Schiene – direkt vor dem Einstieg, auf dem Weg dorthin; „1 Hinweis“ statt „1 Hinweise“.
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

[Unveröffentlicht]: https://github.com/Ambivalenton/openv-release/compare/v1.1.2...HEAD
[1.1.2]: https://github.com/Ambivalenton/openv-release/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/Ambivalenton/openv-release/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/Ambivalenton/openv-release/compare/v1.0.9...v1.1.0
[1.0.9]: https://github.com/Ambivalenton/openv-release/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/Ambivalenton/openv-release/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/Ambivalenton/openv-release/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/Ambivalenton/openv-release/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/Ambivalenton/openv-release/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/Ambivalenton/openv-release/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/Ambivalenton/openv-release/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/Ambivalenton/openv-release/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/Ambivalenton/openv-release/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/Ambivalenton/openv-release/releases/tag/v1.0.0
