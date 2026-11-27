# Änderungen

Alle nennenswerten Änderungen an Öpen-V. Versionen folgen [Semantic Versioning](https://semver.org/lang/de/); kleine Folgeversionen zählen seit 1.2.0.1 in einer vierten Stelle. In Klammern steht der Android-`versionCode`.

## [Unveröffentlicht]

## [1.2.0.10] (130) · 2026-11-27

- Datum- und Uhrzeitwalzen: Die Ziffern stehen genau in der Mitte der Linse, in jeder Schrift. Bisher wurden sie mit einem festen Anteil der Schriftgröße gesetzt, was bei Schriften mit großem Zeilenraum wie Fredoka oder Baloo („Weltraumgelee“) sichtbar zu tief saß. Jetzt wird die Mitte an den tatsächlich gezeichneten Ziffern gemessen.
- Jede Design-Familie hat ihre eigene Walze: Material mit tonaler, flüssiger Linse; Glas mit Mattglasscheibe, Spiegelung und Leuchten dahinter; Lichtkante mit Verlaufsrand, der sich mit der Walze dreht; Soft UI als eingedrückte Mulde; Knete prall und federnd beim Einrasten; Neo-Brutalismus als Farbblock mit Tintenrand und hartem Schatten, der beim Einrasten stempelt; Swiss mit Akzentlinie, Editorial mit Doppellinie; Linien-UI mit Skala am Rand; Kacheln, Bento und Farbblöcke als klare Fläche; Tonal mit Tonstufen; Japandi mit einem Pinselstrich; Organisch als atmender Kiesel; E-Ink invertiert; Chrom poliert; Papier geschichtet; Stoff mit Naht; 8-Bit mit Pixelstufen in den eigenen Farben statt schwarzem Display.
- Butterweicher: Ein Wert wechselt seine Farbe genau dort, wo er die Linse kreuzt, und wächst stufenlos zur Mitte hin. Schnelle Drehungen ziehen eine feine Spur. Ein Schwung rollt weich aus und kommt ohne Nachfedern zum Stehen. Jeder durchlaufene Wert tickt spürbar, die Überträge zwischen Minute, Stunde und Tag laufen ruhiger.

## [1.2.0.9] (129) · 2026-11-27

- Persönlicher Fahrplan: Für deine eigenen Haltestellen – Favoriten, Abfahrtsliste, gewählter Start und in den letzten 30 Tagen gesuchte Orte, höchstens 24 – hält die App einen Auszug des VRR-Sollfahrplans. Ihre Abfahrten erscheinen sofort beim Öffnen, noch bevor die Live-Antwort da ist, und bleiben auch ohne Internet sichtbar: zuerst die letzten Live-Zeilen, die noch bevorstehen, danach der Sollfahrplan, jede Zeile ohne Echtzeit als „Nur Fahrplan“ gekennzeichnet. Die Live-Abfrage läuft weiter wie bisher und ersetzt die geplanten Zeiten, sobald sie antwortet. Gilt auch für Abfahrtstafel und Widget („Offline · Sollfahrplan“).
- Direktfahrten aus dem Sollfahrplan gibt es nur noch zwischen eigenen Haltestellen. Für andere Orte wäre die Liste unvollständig gewesen.
- Speicher: Statt des ganzen VRR-Fahrplans (rund 29 MB entpackt) bleibt nur dieser Auszug auf dem Gerät (meist unter 4 MB, höchstens 8 MB). Der vollständige Index wird nur zum Aufbau kurz entpackt und gleich wieder gelöscht. Eine Haltestelle, die nicht mehr dazugehört, etwa ein seit 30 Tagen nicht mehr gesuchter Ort, fällt beim nächsten Aufbau heraus. Gebaut wird nur, wenn sich die Haltestellen ändern.

## [1.2.0.8] (128) · 2026-11-27

- Nach dem Tauschen von Start und Ziel sucht „Verbindung suchen“ wieder vom angezeigten Start. Bisher verglich der Suchknopf das neue Ziel mit dem Start vor dem Tausch und meldete fälschlich „Start und Ziel sind gleich“; ein danach im Startfeld gewählter Ort wurde nicht verwendet. Ursache: Der Knopf hielt seine Aktion aus einer früheren Darstellung der Seite fest.
- Nach dem Schließen eines Zeitstrahls wechseln „Übersicht“, „Suchen“ und „Abfahrten“ wieder per Tipp.
- Der Wellenbalken markiert Fortschritt und verbleibende Strecke durchgehend, auch beim Umstieg.
- Wiederholte Suchen verwenden gespeicherte Fahrten. Bekannte Direktfahrten können aus dem lokalen VRR-Sollfahrplan kommen. Der Abfahrtsmonitor ergänzt verfügbare Echtzeitdaten.

## [1.2.0.7] (127) · 2026-11-26

- Start und Ziel tauschen in einer einzigen Bewegung: Beide Felder verwandeln sich unterwegs stufenlos in die Form ihres neuen Platzes – Ecken, Füllung und Rand, Schriftgröße und -stärke, das Orts- bzw. GPS-Symbol, „Ab“ und Pfeil des Startfelds, das Löschen-Kreuz und die Beschriftung „Ziel, Adresse oder Haltestelle“ blenden auf dem Weg über. Sie fliegen auf leichten Bögen aneinander vorbei, das aufsteigende Feld darüber mit einem weichen Schatten, und liegen am Ende deckungsgleich auf den echten Feldern, die darunter einblenden. Bisher behielten die Felder unterwegs ihre alte Form und wechselten Rahmen und Beschriftung erst beim Ankommen mit einem Ruck.
- Rotationswalzen passen sich dem Design an: Linse, Walzen und Gehäuse übernehmen dessen Eckenform (eckig, abgeschrägt, Pixel, rund …); flache Designs bekommen eine flache Linse ohne Schatten und Verlauf. Zählwerk (Retro) und Display sind jetzt selbst der Rahmen der Zeitwahl statt eines Kastens im Kasten, und „Jetzt abfahren“ wächst beim Zählwerk aus demselben Bakelit mit Messingschrift.
- Neue Design-Gruppe „Kacheln & Farbe“: neun bunte, eckige und minimale Designs – flache Kachelflächen ohne Schatten und Muster, rechte Winkel, große leichte Überschriften, kräftige Akzentfarben (Kobalt, Magenta, Smaragd, Petrol, Karmin, Anthrazit, Weiß, Limette, Signalorange); die Zeitwahl wählt dort mit einer vollfarbigen Kachel.
- Bei der Ersteinrichtung folgt „Standard“ der Hell-/Dunkeleinstellung von Android. „Seeglas“ bringt als zweite Auswahl helles, mattes Türkisglas.
- Bei aktueller Uhrzeit werden beide Punkte gemeinsam kleiner, verschwinden und wachsen wieder. Ein Zyklus dauert vier Sekunden. Bei gewählter Zeit, reduzierter Bewegung oder beim Drehen bleiben sie sichtbar.

## [1.2.0.6] (126) · 2026-11-26

- Tauschen-Knopf: Der Pfeil bleibt nach dem Tauschen in seinem Kreis. Ursache: Beim Zeichnen meinte „Mitte“ die ganze Zeichenfläche statt des eingerückten Pfeils; die halbe Drehung spiegelte ihn um den falschen Punkt nach unten rechts.
- Der Kreis hinter dem Pfeil ist jetzt flüssig: Ein Tipp drückt ihn von oben flach, beim Drehen schieben die vorbeistreichenden Pfeilspitzen ihn nach außen, an den anderen Stellen zieht er sich zusammen (sein Volumen bleibt), danach schwingt er nach und kommt zur Ruhe. Berechnet als kleine Physik (Rand mit Federn, Oberflächenspannung, Volumen), nicht als aufgesetzte Maße.
- Rotationsrollen neu gestaltet:
  - Modern: die Auswahl als schwebende Glaslinse mit Licht oben und Schatten unten; sie ist flüssig – beim Drehen dehnt sie sich in Drehrichtung, bleibt etwas zurück und schwingt beim Einrasten nach. Der eingerastete Wert springt kurz auf, ferne Werte werden auf der Walze auch schmaler.
  - Retro: ein echtes Zählwerk – Bakelitgehäuse mit Messingeinlage und vier Messingschrauben, elfenbeinfarbene Walzen mit tiefem Zylinderlicht, gezahnte Seitenräder, die sich mitdrehen, ein Messingfenster mit Schattenwurf, Glas mit Lichtreflex und Messingnieten als Doppelpunkt; jedes Einrasten ruckt wie eine Mechanik.
  - Display (Neon, Terminal, 8-Bit, Synthwave): schwarzes Glas mit Punktraster und leuchtendem Rand, unbeleuchtete „88“-Segmente hinter den Ziffern, leuchtende Ziffern mit Nachglühen, das beim Einrasten aufblitzt, Bildröhren-Vignette, Rasterlinien, leuchtende Schienen und Doppelpunkt; Synthwave mit Farbsaum.

## [1.2.0.5] (125) · 2026-11-26

- Angepinnte Karte zeigt nur noch, was gerade zählt. In der Bahn: wo und wann du ankommst und wie viele Halte es noch sind – Linie und Ankunftsgleis wiederholt sie nicht mehr, die Anschlussfahrt wartet in einer ruhigen Zeile („Weiter mit U49 um 07:18 · 5 Min. Umstieg“). Kurz vor dem Umstieg (zwei Halte oder vier Minuten vorher) rückt der Anschluss mit Gleis, Richtung und Abfahrt nach vorn, das Aussteigen schrumpft auf eine Zeile mit Ankunftsgleis. Beim Warten zählen Linie, Richtung, Gleis und die Minuten bis zur Abfahrt; auf der letzten Fahrt heißt es kurz vor dem Ziel „Gleich aussteigen“, ein Fußweg danach steht dabei. Wechsel zwischen diesen Ansichten blenden weich über, die Kartenhöhe gleitet.
- Kein Flackern mehr zwischen „07:13“ und „ca. 07:16 +3 Min.“: Die eigene GPS-Schätzung wurde bei jeder Messung neu gegen die Prognose des Fahrplandienstes gestellt; die Schwelle hängt zusätzlich von der GPS-Genauigkeit ab, die im Zug stark schwankt. Jetzt entscheidet die App mit Gedächtnis: Sie wechselt erst bei klarer Abweichung zur eigenen Schätzung, folgt ihr in Minutenschritten und kehrt erst bei klarer Übereinstimmung zur Prognose zurück. Karte, Benachrichtigung und Bubble zeigen denselben Stand.
- Die Uhrzeit steht in einer eigenen Spalte („an“, „ca. an“, „ab“ darüber, Verspätung darunter); ein „ca.“ oder „+3 Min.“ bricht den Ort nicht mehr um.
- Seite „Unterwegs“: „Dein weiterer Weg“ listet nur noch Fahrten, die oben nicht schon stehen; die Verlässlichkeit erscheint nur, wenn sie warnt; „Wie fährt die S2?“ fragt erst gegen Ende der Fahrt (dann weißt du es) statt direkt nach dem Einsteigen.

## [1.2.0.4] (124) · 2026-11-26

- Tagesrolle aufgeräumt: Wochentag und Datum stehen jetzt in einer Zeile („Di. 01.12.“, die ersten beiden Tage „Heute“ und „Morgen“), die Linse ist so hoch wie bei Stunde und Minute. Bei großer Systemschrift liefen die beiden Zeilen bisher in die Nachbartage und die Linse stand höher als die anderen. Alle Werte einer Rolle haben eine gemeinsame, passende Größe; die Ziffern wachsen mit der Systemschrift nur so weit, wie die Walze Platz hat. Die Ziffern sitzen optisch mittig in der Linse; der gewählte Wochentag trägt die Akzentfarbe, auf den Retro-Zählwerken ist der Sonntag rot wie auf einem Abreißkalender.
- „Jetzt abfahren“ zeigt wieder die Uhr mit der aktuellen Uhrzeit (sie wächst mit der Schrift). Beim Tippen dreht ihr Minutenzeiger eine Runde zurück, während die Rollen zurückspulen.
- „Abfahrt | Ankunft“ wird von „Jetzt abfahren“ nicht mehr gestaucht: Die Umschaltung behält ihre natürliche Breite, der Jetzt-Bereich nimmt den Platz daneben und bricht seinen Text notfalls in zwei Zeilen um.
- Angepinnte Fahrt: Die Welle läuft jetzt über die ganze Breite und lebt – sie atmet, plustert sich auf und zieht sich zusammen, Wellenberge wandern an ihr entlang; Tempo, Dicke und Farbe folgen der Situation (ruhig beim Warten, pochend rot beim Einsteigen, langsam grün bei pünktlicher Fahrt, unruhig bei Verspätung, in Schritten zu Fuß, flach auslaufend am Ziel). Alle zehn Sekunden und bei jeder Änderung zeigt sie kurz den Fortschritt: Ein Lichtschimmer läuft bis zur aktuellen Stelle, dort landet ein Punkt, der Rest tritt kurz zurück. Bisher war sie zu Fahrtbeginn nur ein Punkt auf einer flachen Linie. Mit „Bewegung reduzieren“ steht die Welle still und zeigt den Fortschritt dauerhaft.
- Angepinnter Zeitstrahl: Lange Haltestellennamen werden zuerst etwas kleiner und brechen sonst nur zwischen Wörtern oder nach „-“ und „/“ um – nicht mehr „Westfalenpar|k“ oder „DO-Wischling|en S“.
- „Dein weiterer Weg“: Die Strecken wurden bei großer Schrift und wenig Platz winzig klein; die Liste behält jetzt ihre Schriftgröße und wird unten abgeschnitten.
- Favoriten-Einstellungen aus dem Netzplan: Das Blatt reicht wieder bis kurz über den unteren Rand. Es hielt den Platz der ausgeblendeten Navigationsleiste frei, unten blieb ein breiter dunkler Streifen.
- Der Doppelpunkt zwischen Stunde und Minute blinkt jetzt nur noch in der Zeichenebene; die Rollen werden nicht mehr jede Sekunde neu aufgebaut (spart Energie).

## [1.2.0.3] (123) · 2026-11-26

- Neu: Rotationsrollen für Datum und Uhrzeit unter „Suchen“. Eine Rolle für den Tag (Wochentag und Datum auf dem Ziffernblatt, nach oben gedreht kommen die nächsten Tage), je eine für Stunde und Minute. Sie drehen wie Walzen, rasten mit spürbarem Ticken und einer Feder ein, nehmen beim Loslassen etwas Schwung mit und tragen über wie ein Kilometerzähler (nach :59 dreht die Stunde weiter, nach 23 Uhr der Tag). Ein Tipp auf die Tagesrolle drückt sie wie einen Knopf hinein und öffnet den Kalender, ein Tipp auf die Uhrzeit die Eingabe per Tastatur. Solange „jetzt“ gilt, laufen die Rollen mit der Uhr und der Doppelpunkt blinkt.
- Die Rollen tragen das Design: moderne Walzen mit Linse in Material-Designs, cremefarbene Zählwerkrollen mit Riffelung und Messingfenster in den Retro-Designs, leuchtende Ziffern mit Rasterlinien in Neon-, Terminal- und 8-Bit-Designs (auch Synthwave).
- „Jetzt abfahren“ springt wie bisher aus dem Rahmen, trägt die Optik der Rollen und ein Rückspul-Symbol; ein Tipp lässt alle Rollen sichtbar auf die aktuelle Zeit zurückspulen.
- Angepinnte Fahrt: Der Wellen-Fortschritt ist zurück und lebt mit der Situation – ruhig atmend vor dem Einstieg, dick, rot und pochend beim Einsteigen, dünn, grün und langsam bei pünktlicher Fahrt, orange und unruhiger bei Verspätung, in Schritten beim Gehen, orange-rot bei knappem Umstieg, gezackt und flackernd bei einer Störung, gestrichelt in der Pause; zum Ziel hin wird er ruhig. Jeder Wechsel federt, der Punkt an der Spitze landet mit einem kleinen Hüpfer. Bisher war die Welle vor der Abfahrt flach.

## [1.2.0.2] (122) · 2026-11-26

- Behoben: „Suchen“ reagierte ab und zu nicht. Ursache: Seit die Kapsel der Navigationsleiste gezogen werden kann, zählte ein Tipp, bei dem der Finger nur ein paar Pixel zur Seite rutschte, als Ziehen – die Kapsel federte zurück, der Tab wechselte nicht. Ein solcher Tipp öffnet jetzt den Tab, auf dem er begann.
- Behoben: Nach „Tauschen“ und „Aktueller Standort“ hieß es oft „Start und Ziel sind gleich“. Ursache: Hatte ein Favorit ein Zeitfenster für „Standort nach Zeit“ (auch aus der früheren Funktion „Zeitlich ausblenden“), stand er als Start ein, sobald der letzte GPS-Standort ein paar Minuten alt war – beim Tauschen wurde er so zum Ziel. Mit eingeschaltetem Standort gilt jetzt immer der gemessene Standort; Tauschen misst ihn notfalls zuerst.
- Angepinnte Ansicht: keine Laufschrift mehr. Lange Texte nehmen eine zweite Zeile, die Karte springt danach nicht zurück.
- Suchen: Der Start heißt „Ab …“ mit GPS- oder Ortssymbol und ist farbig, solange er fehlt. Der Tauschen-Knopf ist immer sichtbar (rund hinterlegt); ohne Ziel erklärt er, was zu tun ist.
- Alternativen unterwegs: Jede Karte nennt zuerst Linie und Abfahrt („Bus 310 ab 08:22“), dann Ankunft, den knappsten Umstieg („Nur 2 Min. zum Umsteigen“) und die Verlässlichkeit in Worten – auch für TalkBack. Bei großer Schrift steht eine Karte pro Zeile.
- Uhrzeiten in Ortszeit: Abfahrten und Ankünfte erscheinen immer in deutscher Ortszeit; ist das Handy auf eine andere Zeitzone gestellt, steht „Ortszeit Deutschland · Mi., 25.11.“ dabei.
- Offline-Suche: „Duesseldorf“, „Koeln“, Tippfehler wie „Hauptbanhof“ sowie „central station“, „main station“ und „airport“ werden verstanden.
- Fehlermeldungen: „Fahrplan gerade nicht erreichbar“ ist klar von „keine Fahrt zu dieser Zeit“ getrennt; die Meldung sagt, dass deine Suche für „Erneut suchen“ erhalten bleibt.
- Gleisänderung: Meldet der Fahrplandienst ein anderes Gleis, sagt die angepinnte Karte „jetzt Gleis 5 statt 3“ – fett, rot hinterlegt, mit kurzem Aufleuchten.
- Netzplan: „Rückgängig“ nennt beide Orte der gelöschten Linie und bleibt so lange, wie Android es für Bedienhilfen empfiehlt (mindestens 6 Sekunden).
- Barrierefreiheit und Datenschutz: TalkBack nennt die Favoritenfarben beim Namen; der Wetter-Hinweis (Open-Meteo erhält den Zielort) erscheint direkt beim Einschalten; vor dem Ersetzen einer Sicherung heißt der Knopf „Aktuelle Daten zuerst sichern“.

## [1.2.0.1] (121) · 2026-11-25

- Ersteinrichtung neu: Der Startbildschirm stellt „App einrichten“ in den Mittelpunkt, darüber gleitet ein kleiner Zug von Halt zu Halt. „Direkt eine Verbindung suchen“ und „Sicherung importieren“ bleiben als kleine Auswege darunter; im Ablauf selbst gibt es keinen Import mehr. Jeder Schritt hat jetzt einen „Weiter“-Knopf, der auch bei großer Schrift nicht mehr aus der Zeile gedrängt wird; „Überspringen“ erscheint nur noch, wo „Weiter“ auf eine Auswahl wartet. Kein Wort bricht mehr mitten in zwei Zeilen (bisher „Verbindunge-n“, „Übersprin-gen“): Freigaben stehen als Karten mit eigenem Knopf darunter, Beschriftungen passen sich an. Der Fortschritt oben ist jetzt ein kleiner Zeitstrahl: Ein Zug aus dem Netzplan fährt federnd von Haltestelle zu Haltestelle, erreichte Haltestellen füllen sich, und seine Farbe gleitet fließend von der Farbe einer Haltestelle zur nächsten.
- Ersteinrichtung, Startpunkt: Ohne Standortfreigabe folgt ein Schritt, in dem du einen festen Startpunkt wählst (überspringbar). Deine Suchen beginnen dann dort – die App funktioniert sofort, auch ganz ohne Berechtigungen.
- Ersteinrichtung, Modi: Ein eigener Schritt für Schwarz/Weiß, Minimalismus, Ruhemodus und Planermodus, jeweils mit seinem animierten Bild wie unter „Modi & Verhalten“.
- Farbwahl: Beim Ziehen über das Farbfeld flackerte das ganze Blatt. Ursache: Der Farbname wechselte zwischen einer und zwei Zeilen („Türkis“, „Dunkles Türkis“), dadurch sprang die Höhe. Der Name bleibt jetzt einzeilig und wird bei Bedarf etwas kleiner.
- Suchen: Beim Wechsel von „Übersicht“ zu „Suchen“ zog sich „Abfahrt | Ankunft“ beim Einrasten sichtbar zusammen. Bei großer Systemschrift stand der Schalter genau an seiner Grenze neben dem immer freigehaltenen Platz für „Jetzt abfahren“. Dieser Platz wird jetzt nur noch frei gemacht, solange die Fläche ausgeklappt ist; der Schalter weicht dann weich zur Seite.
- Verlässlichkeit: Ein Anschluss, der laut Prognose schon vor der Ankunft des Zubringers abfährt, bekam dieselbe Bestnote wie ein pünktlicher. Ursache: Hatte der Zubringer nur eine Abfahrtsprognose, wurde die daraus übertragene Ankunftsverspätung als „pünktlich“ gelesen. Übertragene Verspätungen zählen jetzt, frühe Abfahrten verkürzen die Umstiegszeit, und ein laut Prognose unerreichbarer Anschluss gilt als „sehr unsicher“.
- Einstellungssuche: Sie findet jetzt jedes Wort, das auf einer Einstellungsseite steht – auch in Beschreibungen und zusammengesetzten Wörtern. „Fuß“ findet zum Beispiel die Fußabdrücke im Balkendiagramm ebenso wie das Gehtempo. Lange Treffer zeigen den passenden Ausschnitt.
- Versionen: Kleine Folgeversionen zählen ab jetzt in einer vierten Stelle (1.2.0.1, 1.2.0.2, …).

## [1.2.0] (120) · 2026-11-25

- Zwischenstopps: Orte abseits des direkten Wegs funktionieren jetzt. Bisher wurde ein Zwischenstopp abgelehnt, sobald er hinter dem Start oder hinter dem Ziel lag („würde eine Rückfahrt erfordern“), etwa Düsseldorf auf dem Weg von Dortmund nach Herne – auch, wenn es nur ein einziger Stopp war. Ein solcher Umweg ist gewollt: Öpen-V plant ihn ein (in der optimierten Reihenfolge am Anfang bzw. am Ende) und sagt nur ruhig, dass der Ort nicht auf dem direkten Weg liegt.
- Zwischenstopps, Dialog: Das Suchfeld ist in jedem Design sichtbar (feiner Rahmen, auch in dunklen und Monospace-Designs), seine Vorschläge liegen über den Knöpfen statt darunter, der Inhalt blendet unter dem Titel und über den Knöpfen weich aus, statt abgeschnitten zu wirken. Der Aufenthalt rastet in 5-Minuten-Schritten ein und zeigt ganze Minuten (keine „29,0 Min“ mehr, kein überlaufendes Feld).
- Farbwahl auf einer Seite: Beim Mischen steht alles ohne Scrollen auf dem Bildschirm – ein kleineres Farbfeld, darunter Farbton, eine Reihe „Heller und dunkler“ und eine Reihe „Passt dazu“ (mit Akzent und zuletzt gewählten Farben). Der Hex-Code steht direkt in der Vorschau und lässt sich dort bearbeiten. Ein gewählter Vorschlag lässt die Markierungen im Feld und auf dem Regenbogen federnd an ihren Platz springen, die Vorschau gleitet in die neue Farbe, der gewählte Vorschlag wird mit einer Feder etwas größer. Auch die Auswahl-Seite scrollt nicht mehr; auf sehr kleinen Bildschirmen wird sie als Ganzes etwas verkleinert. Bisher war unter dem Farbton alles abgeschnitten und ließ sich nicht erreichen.
- Darstellung im Diagramm: Die Farbknöpfe der Uhrzeitlinie („Akzent“, „Zweitfarbe“, „Textfarbe“) und die Wege der Fußabdrücke brechen in die nächste Zeile um, statt „Textfarbe“ Buchstabe für Buchstabe untereinander zu quetschen. Die Navigationsleiste tritt auf dieser Seite zur Seite und verdeckt keine Kacheln mehr.

## [1.1.9] (119) · 2026-11-24

- Netzplan, Linien löschen: Im Bearbeitungsmodus lässt sich eine Verbindungslinie gedrückt halten. Sie zittert erst immer stärker und zerspringt dann wie Konfetti in ihrer Farbe; die Linie ist gelöscht. „Rückgängig“ unter dem Plan bringt sie zurück. Ein kurzer Tipp öffnet wie bisher das Linienmenü; mit „Bewegung reduzieren“ verschwindet die Linie ohne Konfetti.
- Netzplan, Kreuzungen: Wo sich zwei Linien kreuzen, liegt eine oben. Züge der unteren Linie fahren jetzt sichtbar unter ihr hindurch, statt über sie hinweg. Würden sich zwei Züge an einer Kreuzung treffen, bremst der, der später ankäme, sanft ab und beschleunigt wieder, sobald der andere durch ist.
- Netzplan, Fortschrittszug: Eine angepinnte Fahrt zwischen zwei Favoriten wird zuverlässiger erkannt. Bisher fehlte der Zug, wenn die Fahrt am eigenen Standort begann, der nur wenige Dutzend Meter von der gespeicherten Adresse („Zuhause“) entfernt lag, oder wenn ein Bahnhof ohne Ortsnamen gespeichert war („Hauptbahnhof“ statt „Dortmund Hbf“). Für den Netzplan gelten Adressen und der eigene Standort jetzt im Umkreis von 250 Metern als derselbe Ort, Bahnhöfe auch mit nahem Gleis oder ohne Ortsnamen; Suche und Zeitstrahl bleiben bei ihrer genauen Zuordnung.

## [1.1.8] (118) · 2026-11-24

- Behoben: Ein Tipp auf „Suchen“ landete nach kurzer Pause auf „Abfahrten“, und von „Abfahrten“ aus schien „Suchen“ gar nicht zu reagieren. Ursache war das Verhalten der verwendeten Compose-Version: Bat ein Element auf einer Seite darum, ins Bild geholt zu werden (etwa beim Fokus), rastete die Seitenleiste eine ganze Seite weiter. Solche Anfragen enden jetzt an der jeweiligen Seite; die Seiten wechseln nur noch durch Wischen oder einen Tab. Außerdem führt die Suche „Ziel ändern“ und „Start wählen“ nur noch einmal aus, statt bei jeder Rückkehr auf die Seite erneut den Fokus zu setzen, und ein Tab gilt nur dann als bereits geöffnet, wenn seine Seite tatsächlich dort steht.
- Abfahrt/Ankunft wie ein Kippschalter: Die Kapsel lässt sich mit dem Finger ziehen. Sie neigt sich erst etwas mit, rastet hinter der Mitte spürbar auf die andere Seite um und federt beim Loslassen in ihre Raste. Antippen funktioniert wie bisher.
- Navigationsleiste: Auch die Kapsel unten lässt sich ziehen. Sie rastet mit einem kurzen Ticken in jeden Tab ein, die Seiten folgen; ein schneller Wisch springt eine Raste weiter.

## [1.1.7] (117) · 2026-11-24

- Andere Verbindungen am Umstieg: Steht die angepinnte Reise an einem Bahnhof – vor dem Einsteigen, auf dem Weg dorthin oder kurz vor einem Umstieg –, zeigt die Reisekarte unten bis zu drei andere erreichbare Verbindungen. Jeder Knopf nennt die neue Ankunftszeit, die Umstiege und die geschätzte Verlässlichkeit. Antippen öffnet eine kurze Rückfrage mit „Ankunft 16:39 statt 16:33“ und der kompakten Zusammenfassung der ganzen neuen Verbindung; erst „Wechseln“ ersetzt die laufende Reisebegleitung. Die Verbindungen stammen aus der Alternativsuche, die die Reisebegleitung ohnehin regelmäßig ausführt – es entstehen keine zusätzlichen Abfragen. Angeboten werden nur Verbindungen, die höchstens drei Minuten alt sind und noch erreichbar sind.
- Verlässlichkeit (Grundgerüst): Öpen-V schätzt, wie sicher eine Verbindung wie geplant klappt – ob alle Fahrten fahren, jeder Umstieg hält und die Ankunft im Rahmen bleibt. Grundlage sind veröffentlichte Pünktlichkeitswerte je Verkehrsmittel (Fern- und Regionalverkehr, S-Bahn, Stadtbahn, Bus), Berufsverkehr, Wochenende, Nacht und Jahreszeit, die Umstiegszeit abzüglich Fußweg und – wo vorhanden – die Echtzeitprognose. Angezeigt werden fünf Stufen von „Sehr sicher“ bis „Sehr unsicher“ als kleine Balken: auf der Unterwegs-Seite und auf den Knöpfen für andere Verbindungen. Es ist eine begründete Schätzung, keine Messung; die Suche selbst bleibt unverändert. Recherche, Modell und Grenzen: docs/reliability.md.
- Eigene Rückmeldung: Auf der Unterwegs-Seite fragt eine ruhige Zeile „Wie fährt die RE1?“ mit „Pünktlich“ und „Verspätet“. Jede Fahrt zählt einmal; ab drei Rückmeldungen passt Öpen-V die Einschätzung für diese Linie (sonst für das Verkehrsmittel) zur jeweiligen Tageszeit an. Die Angaben bleiben auf dem Gerät, gehören nicht zu Sicherungen und lassen sich unter „Dienste“ löschen.

## [1.1.6] (116) · 2026-11-24

- Unterwegs übernimmt die Reise die Übersicht: Sobald der erste Abschnitt einer angepinnten Verbindung begonnen hat, löst sich die Karte aus der Liste und füllt mit einer federnden Bewegung die ganze Seite zwischen Kopfzeile und Navigationsleiste – fest, ohne Scrollen. Die übrigen Inhalte der Übersicht treten so lange zurück; in der Navigationsleiste heißt der Reiter „Unterwegs“ und zeigt ein Navigationssymbol. Ist die Reise vorbei, abgelöst oder schwebt sie als Bubble, kehrt die Übersicht ebenso federnd zurück. Auf der Seite steht unter dem aktuellen Schritt der weitere Weg (Linie, von → nach, Zeiten, Fußwege).
- Angepinnte Karte ohne Doppelungen: Der Schritt im Blick steht einmal mit Verb, Ort und Zeit („Aussteigen · Bochum Hbf · 15:42“); Linie, Ankunftsgleis und verbleibende Halte stehen ruhig darunter. Nach einem Umstieg folgt ein eigener Block „Weiter mit U35“ mit Richtung, hervorgehobenem Abfahrtsgleis, Abfahrtszeit und Umstiegszeit bzw. „Umstieg knapp“. Die Ankunft am Ziel erscheint nur einmal und rot, wenn sie sich verspätet. Bisher standen Umstiegshalt, Gleis und Ziel bis zu viermal auf der Karte.
- Kompaktansicht der angepinnten Reise: „Minimieren“ zeigt jetzt dieselbe kompakte Zusammenfassung wie beim normalen Zeitstrahl, und die Navigationsleiste taucht dabei nach unten ab.
- Wegkarte am Umstieg: Fehlt ein Fußweg in der Verbindung, fragt Öpen-V den Weg vom Ankunfts- zum Abfahrtspunkt selbst an (sofern Ein- und Ausstieg mindestens 15 Meter auseinanderliegen und die Wegführung eingeschaltet ist). Gibt es dennoch keinen Weg, zeigt die Karte statt Geschäften die Haltestellen der Umgebung als Haltestellenschilder aus dem mitgelieferten Katalog, dazu Ankunfts- und Abfahrtspunkt; die Kartenkacheln treten dafür etwas zurück.
- Behoben: Beim Zuklappen der Zwischenhalte im Zeitstrahl wanderten die Kreise nach oben, bevor sie verschwanden. Die Zeilen schrumpfen jetzt nach oben weg, die Kreise vergehen an Ort und Stelle.

## [1.1.5] (115) · 2026-11-24

- Bedienhinweise ohne Text: In den ersten Stunden der Nutzung zeigen kleine Animationen, was sich antippen, halten oder wischen lässt – das Zahnrad nickt, der Stift über dem Netzplan pulsiert, die Datums- und Zeitwahl und „Design“ unter der Anzeigetafel pulsieren leise, im Balkendiagramm füllt sich ein Halte-Ring auf der ersten Verbindung, über die Navigationsleiste gleitet ein Punkt (Seiten wischen). Jeder Hinweis endet für immer, sobald die Geste einmal genutzt wurde; nach etwa drei Stunden Nutzung (höchstens zwei Wochen) enden alle. Hinweise spielen kurz und ruhen dann einige Sekunden, ohne in der Pause zu zeichnen. Abschaltbar unter Modi & Verhalten („Bedienhinweise“); „Bedienhinweise erneut zeigen“ unter Info & Hilfe startet sie neu. Im Ruhe- und Stromsparmodus und neben einer Text-Anleitung erscheinen sie nicht.
- Netzplan-Leiste: Die Felder „Halten und schieben“, „Ziehen“ und „Tippen“ zeigen nacheinander je einen kurzen Film ihrer Geste – ein Favorit wird gehalten und in ein freies Feld getragen, eine Linie zum Nachbarn gezogen, ein Tipp öffnet eine kleine Einstellungskarte –, dann federt der Text zurück.
- Netzplan in den ersten Stunden: Im Bearbeitungsmodus zeigt ein Finger an einem echten Favoriten, wie er sich verschieben, verbinden und einstellen lässt (gestrichelter Pfeil ins freie Feld, wachsende Linie, aufspringende Karte); außerhalb zeigt ein Halte-Ring auf freier Fläche, dass Gedrückthalten die Namen zeigt.
- Behoben: Bei System-Designs wie „2001 · Hügelblau“ erschien unten in der federnden Navigationsleiste eine helle Linie, wo sich die Leiste wölbt.
- Behoben: Die schwebende Reisekarte am rechten Bildschirmrand rutschte beim Auf- und Zuklappen leicht zur Seite; sie bleibt jetzt stehen wie am linken Rand.
- Die Wegkarte am Umstieg verzichtet auf den Erklärsatz über dem Kartenausschnitt.

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
