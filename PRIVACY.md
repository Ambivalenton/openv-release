# Datenschutzerklärung für Öpen-V

Stand: 22. November 2026.

**Kurz gelesen:** kein Konto, keine Werbung, keine Analyse-SDKs. Deine Favoriten und Einstellungen bleiben auf dem Gerät. Fahrplan-, Such-, Karten- und Wetterdienste erhalten die Daten, die ihre jeweilige Funktion benötigt, sowie technische Verbindungsdaten.

## 1 · Verantwortlicher und Geltungsbereich

Verantwortlicher für Öpen-V: **Erik Tomczyk**, Entwicklername **Ambivalenton**.

Kontakt für Support und Datenschutz: [oepenvapp@gmail.com](mailto:oepenvapp@gmail.com).

Öffentliche Datenschutzerklärung: [ambivalenton.github.io/openv-release/privacy](https://ambivalenton.github.io/openv-release/privacy/).

Diese Erklärung gilt für die Android-App Öpen-V (dev.ambivalenton.openv). Bei selbst eingetragenen Orts-, Karten- oder Gehführungsdiensten ändern sich die entsprechenden Empfänger. Externe Anbieter und das Betriebssystem verarbeiten Daten zusätzlich nach ihren eigenen Informationen. Der Entwickler betreibt keinen eigenen Server zum Sammeln von App-Nutzungsdaten.

## 2 · Was auf deinem Gerät bleibt

| Daten | Zweck und Aufbewahrung |
| --- | --- |
| Favoriten mit Orten, Namen, Symbolen, Filtern und Zeitregeln; Netzplan mit Linien, Richtungen und Rollen | Deine Wege planen. Im privaten App-Speicher bis zum Löschen oder Ersetzen. |
| Einstellungen, persönliche Themes, App-Logo, Einrichtungsentwurf und abgeschlossene Bedienhinweise | Darstellung und Einrichtung beibehalten. Bis zum Zurücksetzen oder Löschen. |
| Suchverlauf mit bis zu 50 zusammengefassten Suchwegen; Vormerkungen; Haltestellen-Liste und letzte Abfahrten | Häufige Wege und Abfahrten wiederfinden. Bis zum Löschen, Überschreiben oder Ablauf; Vormerkungen nur bis zur bekannten Abfahrt. |
| Textchronik und Zähler für die erste Nutzungswoche | Bereits gelesene Beiträge und Bedienungstipps anzeigen. Lokal, ohne Nutzungsanalyse. |
| Zuletzt freigegebene Position mit Zeitpunkt und Genauigkeit | Startpunkt und nahe Haltestellen bestimmen. Wird durch neuere Messungen ersetzt; kein fortlaufendes Roh-GPS-Profil. |

Der Favoriten-Umkreis und die Netzplan-Richtung werden mit bereits vorhandenen Positionsdaten lokal berechnet. Dafür entstehen keine zusätzlichen Geofences oder Empfänger. Der gebündelte Haltestellenkatalog wird lokal entpackt und durchsucht; Eingaben gehen dabei nicht an DELFI. Eine zusätzlich gestartete Online-Suche bleibt davon unabhängig.

Die App deaktiviert Android-Cloud-Backup und die von ihr angebotene automatische Geräte-Datenübernahme. Eigene, bewusst erstellte Sicherungen sind unter Abschnitt 6 beschrieben.

## 3 · Standort und laufende Reisebegleitung

Standort wird nach Android-Freigabe genutzt, etwa für einen automatischen Start oder nahe Haltestellen. Ein manueller Start und feste Haltestellen funktionieren ohne Freigabe. Öpen-V nutzt Androids LocationManager; Google Play Services oder ein Google-Konto sind dafür nicht erforderlich. Je nach System können GPS, Netzwerk- oder kombinierte Ortung beteiligt sein. Deren Datenverarbeitung hängt auch von deinen Geräteeinstellungen ab.

**Eine angepinnte Reise** startet einen sichtbaren Vordergrunddienst mit Benachrichtigung. Dieser aktualisiert die Fahrt beim gewählten Fahrplandienst und nutzt bei Freigabe laufende Standortmessungen für den lokalen Reisefortschritt. Der angeforderte Messabstand liegt situationsabhängig ungefähr zwischen zwei und zehn Sekunden; Android kann Messungen verzögern. Der Live-Abgleich erfolgt normalerweise ungefähr minütlich, im Stromsparmodus seltener. Dort entfällt die dauernde Standortanmeldung; kurze gelegentliche Prüfungen suchen nach Abweichungen.

Vorhandene Beschleunigungssensoren können während dieser Begleitung lokale Schritt-/Laufmuster ergänzen. Es werden keine Fitness- oder Gesundheitsdaten gelesen und keine Aktivitätserkennungsberechtigung verlangt. Sensorrohdaten und die kurze Schritt-Pulshistorie bleiben im Arbeitsspeicher und werden beim Beenden verworfen. Schritte allein bestätigen keinen Ausstieg. Bei Standortlücken verwendet die App nach begrenzter Überbrückung Anbieterprognosen; ohne Freigabe oder bei ausgeschalteter Ortung sofort.

Start, Ziel, gegebenenfalls Zwischenstopps und benötigte Koordinaten können zur Fahrtaktualisierung und zur Suche erreichbarer Alternativen an den Fahrplandienst gehen. Eine klar verpasste Abfahrt oder ein Anschlusswechsel kann eine erneute Suche auslösen. Standort, Prognosen, beobachtete Halte und die ursprüngliche Reiseabsicht werden lokal zusammengeführt. Das kann angezeigte Ankunftszeiten und Vorschläge verändern.

**Minimieren beendet die Begleitung nicht.** Das Entfernen auf der Reisekarte oder die Aktion Reise beenden in der Benachrichtigung beendet Dienst, Sensoranmeldung und weitere Fahrtabfragen; eine plausibel erkannte Zielankunft kann die Reise ebenfalls abschließen. Android kann Hintergrundarbeit selbst beenden. Benachrichtigungen werden lokal erzeugt, ohne Push-Server; ab Android 13 wird die Freigabe separat angefragt.

**Widgets und Anzeigetafel:** Abfahrten können auch bei geschlossener App aktualisiert werden, ungefähr viertelstündlich und nur bei eingeschaltetem Bildschirm; beim Entsperren wird ein älterer Stand nachgeladen. Bei ausgeschaltetem Bildschirm stellt Öpen-V dafür keine Anfragen. Öpen-V fordert keine zusätzliche Hintergrundstandort-Berechtigung für Widgets an. Als Grundlage dienen ein fester Ort oder die zuletzt freigegebene Position; eine neue Position kann bei geöffneter App oder über die sichtbare manuelle Aktualisierung angefordert werden. Android kann diese Aktualisierungen verschieben.

**Bubble:** Die freiwillige Sondererlaubnis Über anderen Apps einblenden zeigt die vorhandene Reise als Overlay. Die Bubble startet keine zweite Standort-, Sensor- oder Netzwerkschleife. Ihre Seite, Höhe und eine reisebezogene Ausblendung werden lokal gespeichert. Auf dem Sperrbildschirm bleibt sie verborgen. Schließen blendet nur die Bubble aus.

## 4 · Welche externen Dienste Daten erhalten

Jede direkte Internetanfrage übermittelt technisch insbesondere **IP-Adresse, Zeitpunkt, angefragte Adresse und technische Header**. Der Anfrageinhalt richtet sich nach der Funktion. Es gibt keinen eigenen App-Proxy, der deine IP-Adresse vor diesen Anbietern verbirgt.

### Fahrpläne und Abfahrten

VRR OpenService erhält Suchtexte, Haltestellenkennungen, Start-/Zielorte oder Koordinaten, Zwischenstopps, gewünschte Zeiten und Routing-/Verkehrsoptionen. Sobald in der Suche ein Ziel gewählt ist, fragt die App die nächste und die vorige Verbindung schon im Hintergrund an, damit das Ergebnis beim Tippen auf „Suchen“ sofort erscheint (nicht im Stromsparmodus). Die Übersicht kann Wege zu sichtbaren Favoriten beim Öffnen und Aktualisieren abfragen. Diese allgemeinen Anfragen enden beim Verlassen der App; eine angepinnte Reise und systemgesteuerte Abfahrts-Widgets werden wie oben beschrieben weiter aktualisiert.

- [VRR OpenService](https://www.opendata-oepnv.de/ht/de/api) · [Datenschutz des VRR](https://www.vrr.de/datenschutz/).

### Orts- und Adresssuche

Bereits beim Tippen können nach kurzen Eingabepausen Anfragen an den Photon-Dienst gehen, standardmäßig https://photon.komoot.io/. Übertragen werden der Suchtext und gegebenenfalls Koordinaten als Suchumkreis. Die Adresssuche ist auf Deutschland ausgerichtet. Bei unklaren Eingaben kann dieselbe Suche in einer vereinfachten Schreibweise oder ohne Hausnummer wiederholt werden. Bei fehlenden passenden Treffern kann die konfigurierte EFA-Adresssuche zusätzlich den Text erhalten. Die App begrenzt und verwirft überholte Anfragen.

[Photon-Projekt](https://github.com/komoot/photon) · [Datenschutz von komoot](https://www.komoot.com/privacy).

### Karten und genaue Gehführung

Sichtbare Karten laden benötigte Kacheln vom gewählten Server, standardmäßig https://tile.openstreetmap.org/. Die Kachelkoordinaten lassen auf den betrachteten Ort schließen. Dazu gehört die Zielkarte der Suche, sobald dort ein Ziel gewählt ist (nicht im Stromsparmodus). Es gibt keine Offline-Vorratsladung. Verdeckte Gehwegkarten pausieren Abrufe; beim Verlassen werden laufende Abrufe abgebrochen.

Die standardmäßig eingeschaltete genaue Gehführung kann beim Öffnen des angepinnten Zeitstrahls zwei Fußweg-Endpunkte, Gehprofil, Geschwindigkeit und Sprache an den konfigurierten Valhalla-Dienst senden. Standard ist https://valhalla1.openstreetmap.de/. Favoritennamen und ein Roh-Bewegungsverlauf gehören nicht dazu. Diese Anfragen sind unabhängig vom bewussten Öffnen einer externen Karten-App. Abschalten unter Fahrt & Navigation → Fußwege & Navigation beendet weitere Gehführungsanfragen.

[OSMF-Datenschutz](https://osmfoundation.org/wiki/Privacy_Policy) · [OSM-Kachelregeln](https://operations.osmfoundation.org/policies/tiles/) · [Valhalla-Dienstinformationen](https://valhalla.github.io/valhalla/start/introduction/).

### Freiwilliges Wetter bei der Ankunft

Wetter ist anfangs aus. Nach Aktivierung erhält https://api.open-meteo.com/ Zielkoordinaten, gerundet auf drei Nachkommastellen. Auch gerundete Koordinaten können einen Ort eingrenzen. Favoritennamen und Suchverlauf werden nicht mitgesendet. Die Zuordnung zur Ankunft und die Texte entstehen lokal, ohne zusätzliche Ortung oder KI-Textdienst. Ausschalten beendet Wetterabrufe; eine angepinnte Reise kann das Zielwetter bis zum Ende der Begleitung aktualisieren.

Open-Meteo nennt für technische API-Protokolle eine Löschung nach 90 Tagen. Maßgeblich sind die aktuellen [Datenschutz- und Dienstinformationen](https://open-meteo.com/en/terms).

## 5 · Reisezustand und begrenzte Zwischenspeicher

Die laufende Reise wird im privaten Speicher gesichert: Orte, Abschnitte, Zeiten, letzter bestätigter Fortschritt, abgelehnte Alternativen und bereits gezeigte Hinweise. Bis zu 24 beobachtete Halte-/Abfahrtsereignisse können lokale Prognosen korrigieren. Höchstens 16 aktuelle Telemetriemessungen aus zwei Minuten bleiben nur im Arbeitsspeicher.

Bis zu acht geschlossene oder ersetzte Reisen können mit dem letzten zuverlässigen Standortpunkt höchstens sechs Stunden zur Wiederherstellung angeboten werden, solange sie noch nutzbar sind. Abgelaufene Einträge werden beim nächsten Laden oder Speichern bereinigt. Suchverlauf löschen entfernt auch diese Karten und den Verbindungscache. Reise-Sitzungsdateien sind nicht Teil des allgemeinen JSON-Exports.

| Cache | Begrenzung / Entfernung |
| --- | --- |
| Verbindungsansichten | Bis acht Ansichten, höchstens 30 Minuten und 8 MiB im privaten Cache. Verlauf löschen leert sie. |
| Wiederkehrende Direktfahrtsuchen | Bis 64 lokale Einträge mit gehashtem Suchschlüssel, gehashtem Fahrplanmuster, Kennung der Einstiegshaltestelle, Zähler und Prüfzeit. Nach 48 Stunden für Schnellabfragen inaktiv, bei der nächsten Speicherung nach 14 Tagen bereinigt. „Cache löschen“ entfernt sie. |
| Persönlicher Fahrplan | Auszug des mitgelieferten VRR-Sollfahrplans für höchstens 24 eigene Haltestellen (Favoriten, Abfahrtsliste, gewählter Start, in den letzten 30 Tagen gesuchte Orte), höchstens 8 MiB. Enthält nur Fahrplandaten und die Liste dieser Haltestellen, keine Standort- oder Echtzeitdaten. Eine Haltestelle, die nicht mehr dazugehört, fällt beim nächsten Aufbau heraus. |
| Allgemeine HTTP-Antworten | Höchstens 20 MiB, nach Server-Cachevorgaben. Android kann sie entfernen. |
| Gehweg-Kartenkacheln | Höchstens 32 MiB im privaten Cache und 16 MiB im RAM. Servervorgaben, sonst sieben Tage. MapLibre hat daneben einen eigenen Kartencache. |
| Genaue Gehrouten | Bis 32 Einträge, höchstens 24 Stunden im RAM; diese Anfragen verwenden HTTP no-store. |
| Wetter | Bis 48 Ziele, 30 Minuten im RAM; nach Fehlern kurze Abrufpause. |

Kurzlebige Such-/Fahrplancaches liegen zusätzlich im RAM. Betriebssystem und Servervorgaben können Daten früher entfernen; ein voller Cache ist keine dauerhafte Archivierung.

## 6 · Sichern, Kompaktansicht und externe Navigation

**Export / Import** öffnet erst nach Antippen den Android-Dateidialog. Eine unverschlüsselte JSON-Sicherung enthält Einstellungen, persönliche Themes, Favoriten mit Orten, Netzplan, Suchverlauf und Textchronik. Von vorgemerkten Fahrten werden nur die nutzerseitigen Suchangaben im Suchverlauf gesichert. Vollständige Fahrtantworten, Prognosen, Live-GPS und aktuelle Abfahrtscaches werden nicht exportiert. Öpen-V lädt die Datei nicht selbst hoch. Wählst du einen Cloud-Speicher, verarbeitet dessen Betreiber die Datei nach seinen Bedingungen. Import prüft die Datei und ersetzt den Bestand erst nach Bestätigung. Lösche Exportdateien selbst am gewählten Speicherort.

**Kompaktansicht** zeigt eine Verbindung oder die Live-Verbindung als Karte im Vollbild, mit Datenquelle und Stand. Öpen-V erstellt dabei keine Bilddatei und gibt nichts an andere Apps weiter. Ein Bildschirmfoto entsteht nur, wenn du es selbst mit Android aufnimmst; dafür gelten die Einstellungen deines Geräts. Die Live-Ansicht kann deinen aktuellen Standort, Verzögerungen, Ankunftsprognosen und Zeitpunkte zeigen.

**Externe Navigation** öffnet nach Antippen OpenStreetMap oder die gewählte Karten-App. Je nach Modus gehen Ziel und ein bereits bekannter Start an OpenStreetMap mit Routingdienst oder an Google Maps. Das Gehprofil wird bei direkter Navigation übergeben; Google Maps bestimmt seinen Start nach eigenen Freigaben. Der reine Standortmodus übergibt nur das Ziel. Fehlt die App, kann eine Webansicht geöffnet werden. Öpen-V fordert dafür keinen zusätzlichen Standort an. Es gelten die Bedingungen des gewählten Dienstes.

## 7 · Rechtsgrundlagen und internationale Verarbeitung

Für die Bereitstellung ausdrücklich angeforderter Kernfunktionen und notwendige Speicherung ist Art. 6 Abs. 1 Buchst. b DSGVO vorgesehen, soweit ein entsprechendes Nutzungsverhältnis besteht. Freiwillige Standort- und Wetterfunktionen sind auf eine informierte, widerrufbare Einwilligung nach Art. 6 Abs. 1 Buchst. a DSGVO auszurichten. Eine Android-Berechtigung allein ersetzt diese Prüfung nicht. Technisch notwendige Gerätezugriffe sind nach § 25 Abs. 2 TDDDG zu beurteilen; für weitere Zugriffe kann eine Einwilligung erforderlich sein. Die freiwilligen Funktionen können in den App- oder Android-Einstellungen beendet werden; die manuelle Verbindungssuche bleibt verfügbar.

Externe Anbieter handeln nach ihren eigenen Datenschutzinformationen. Bei der Nutzung externer Dienste sind Verarbeitungen außerhalb der EU/des EWR möglich, insbesondere bei selbst gewählten Diensten. Informationen zu Verarbeitungsländern und den vom jeweiligen Anbieter eingesetzten Garantien enthalten dessen verlinkte Datenschutzinformationen. Es wird weder ausschließliche EU-Verarbeitung noch ein ungeprüftes Auftragsverarbeitungsverhältnis zugesagt. Öpen-V kann Speicherfristen fremder Server nicht erzwingen.

## 8 · Deine Rechte und Löschmöglichkeiten

Unter den gesetzlichen Voraussetzungen hast du Rechte auf Auskunft, Berichtigung, Löschung, Einschränkung und Datenübertragbarkeit. Eine Einwilligung kannst du jederzeit mit Wirkung für die Zukunft widerrufen. Gegen Verarbeitungen auf Grundlage berechtigter Interessen steht dir ein Widerspruchsrecht zu. Du kannst dich bei einer Datenschutzaufsichtsbehörde beschweren, etwa der [Landesbeauftragten für Datenschutz und Informationsfreiheit NRW](https://www.ldi.nrw.de/).

- Favoriten und Haltestellen einzeln entfernen; Suchverlauf unter Info & Hilfe → Dienste & Daten leeren.
- Standort, Benachrichtigungen und Overlay-Erlaubnis in Android entziehen.
- Alle privaten Daten und Caches über Android → Apps → Öpen-V → Speicher → Daten löschen entfernen oder die App deinstallieren.
- Eigene Sicherungen und geteilte Dateien separat löschen. Technische Protokolle externer Anbieter bleiben von einer lokalen Löschung unberührt; wende dich dafür an den jeweiligen Anbieter.

Es gibt kein Benutzerkonto und damit keine Kontolöschung. Standort- oder Wetterfreigaben sind keine Voraussetzung für eine manuelle Verbindungssuche. Ohne nötige Angaben können einzelne Funktionen entfallen. Die App betreibt keine automatisierten Entscheidungen mit rechtlicher oder vergleichbar erheblicher Wirkung im Sinne von Art. 22 DSGVO; Reisevorschläge sind unverbindliche Auskünfte.

## 9 · Sicherheit und Änderungen

Die App nutzt HTTPS und lässt keinen unverschlüsselten Netzwerkverkehr zu. Redaktionelle Texte, Hilfen, Illustrationen und Übersetzungen funktionieren lokal. Es gibt keinen generativen KI-Dienst während der Nutzung. Absolute Sicherheit kann keine Software garantieren. Bei geänderten Funktionen oder Diensten wird diese Datenschutzerklärung angepasst.


## 10 · Diese Datenschutz-Webseite

Diese Webseite wird über GitHub Pages von GitHub, Inc. bereitgestellt. Beim Abruf erhält GitHub technisch erforderliche Verbindungsdaten, insbesondere IP-Adresse, Zeitpunkt und angefragte Ressource. Es werden durch Öpen-V keine Analyse-Skripte, Werbeinhalte, externen Schriftdateien oder eigenen Tracking-Cookies auf dieser Seite eingebunden. Die Bereitstellung erfolgt zur Erfüllung der Informationspflichten und zum sicheren Betrieb der Seite (Art. 6 Abs. 1 Buchst. c und f DSGVO). Weitere Informationen: [GitHub General Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
