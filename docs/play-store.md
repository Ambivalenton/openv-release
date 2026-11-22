# Öpen-V im Google Play Store

Stand: 22. November 2026. Verantwortlich: Erik Tomczyk / Ambivalenton, oepenvapp@gmail.com.

## Paket

| Zweck | Build-Typ | Android-Paket | Datei |
| --- | --- | --- | --- |
| Store-Version und geschlossener Test | `release` | `dev.ambivalenton.openv` | Android App Bundle aus dem Release-Workflow |
| Gerätetest neben der Store-App | `preview` | `dev.ambivalenton.openv.next` | ARM64-APK „Öpen-V · Vorschau“ |

Die App spricht VRR OpenService an (keine Anbieterwahl, keine eigenen Server), zeigt sichtbare Quellenhinweise, bietet eine Kompaktansicht statt Bildexport, fordert keine Hintergrundortung an, begrenzt Anfragen je Aktion und speichert in Sicherungen keine vollständigen Fahrtantworten. Persönliche JSON-Sicherungen anderer Öpen-V-Installationen lassen sich importieren; vollständige gespeicherte Fahrtantworten werden dabei verworfen, die Suchabsicht bleibt erhalten.

## 1. Entwicklerkonto und Signierung abschließen

Im Google-Play-Entwicklerkonto müssen Identität, Kontakt-/Telefonangaben und gegebenenfalls ein verifiziertes Android-Gerät bestätigt sein. Den angezeigten Status für EU-Vertrieb und etwaige Händlerangaben entsprechend dem tatsächlichen Hobbyprojekt beantworten. Google-Verträge und kostenpflichtige Kontoschritte bestätigt der Kontoinhaber selbst. Diese Kontoeinrichtung ist kein Bestandteil des App-Builds.

Actions nutzt den Testschlüssel (`OPENV_REVIEW_KEYSTORE_BASE64`) nur für die Vorschau-APK. Ein AAB wird ausschließlich mit dem vorgesehenen Upload-Schlüssel signiert. Der Testschlüssel wird dafür nicht verwendet und es wird kein Ersatz erzeugt.

Für die Release-Signierung sind diese Repository-Secrets vorgesehen:

- `OPENV_KEYSTORE_BASE64`: vorhandener Upload-Keystore als Base64.
- `OPENV_STORE_PASSWORD`, `OPENV_KEY_ALIAS`, `OPENV_KEY_PASSWORD`: passende Werte.

Fehlt einer dieser Werte, baut Actions ein ausdrücklich `UNSIGNIERT` genanntes AAB. Dieses kann nicht bei Play hochgeladen werden. Zugangsdaten und Schlüssel ausschließlich über GitHubs Secrets-Verwaltung hinterlegen; niemals in Chat, Quellcode oder Issues. Ist bereits eine Play-App mit dem Paket vorhanden, müssen Uploadzertifikat und vorhandene App-Signing-Konfiguration dazu passen. Bei einer neuen Play-App die Upload-/App-Signing-Schlüsselentscheidung im Entwicklerkonto abschließen und sicher aufbewahren.

Actions prüft das tatsächlich erzeugte AAB mit Googles fest versioniertem bundletool, die Paket-/SDK-Angaben, Signierung und 16-KB-Konfiguration. Aus demselben AAB erzeugte temporäre APKs werden zusätzlich auf Berechtigungen, alle vier ABIs, 64-Bit-ELF-Ausrichtung, Assets, Widget-Klassen und unerwünschte Google-SDKs geprüft. Deren temporäre Test-Signatur beweist keine Play-Signatur. R8 und Ressourcenverkleinerung sind aktiv.

## 2. Store-Eintrag

- App-Name: **Öpen-V** (6 Zeichen).
- Kategorie: **Karten & Navigation**; kostenlose App, keine Werbung, kein Konto, keine In-App-Käufe.
- Support: **oepenvapp@gmail.com**.
- Datenschutzerklärung: **https://ambivalenton.github.io/openv-release/privacy/**. Öffentliches HTML auf Deutsch und Englisch, kein Login und keine PDF-Datei.
- Texte: `fastlane/metadata/android/de-DE/` und `en-US/`; Kurzbeschreibung ≤80, Langbeschreibung ≤4.000 und Versionshinweise ≤500 Zeichen.
- Bilder: PNG-App-Symbol 512×512, Feature-Grafik 1024×500 und vier echte Telefon-Screenshots (1080×1920, RGB ohne Alpha). Keine echten Fahrgast-/Standortdaten verwenden; Beispieldaten klar kenntlich machen. Automatisiert erzeugte App-Ansichten (`StoreAssetsTest`) zusätzlich am echten Telefon kontrollieren.
- Reichweite im Eintrag: VRR / Rhein-Ruhr. Keine vollständige Deutschland-Auskunft zusagen. Der Herausgeber entscheidet die Vertriebsländer; für den ersten geschlossenen Test bietet sich Deutschland an.

## 3. App-Inhalte in der Console

| Formular | Grundlage für die Eingabe |
| --- | --- |
| App-Zugriff | Alle Kernfunktionen ohne Anmeldung nutzbar; kein Testkonto erforderlich. Standort und Wetter freiwillig. |
| Werbung | Keine Werbung. |
| Zielgruppe | Altersgruppen anhand der tatsächlich beabsichtigten Nutzer wählen. Es ist keine speziell für Kinder entwickelte App. Der Herausgeber bestätigt diese Auswahl. |
| Inhaltsbewertung | IARC-Fragen anhand der App und ihrer lokalen Texte beantworten. Kein Chat, nutzergenerierter öffentlicher Inhalt, Glücksspiel, Verkauf oder Account. Eine USK-/PEGI-Einstufung wird erst durch das Formular bestimmt. |
| Berechtigungen | Vordergrundstandort für Start/Umgebung und bewusst gestartete Reise; Benachrichtigungen freiwillig; Overlay nur für die freiwillige Bubble. Kein Zugriff auf Kontakte, Kamera, Mikrofon oder Dateien außerhalb des Android-Dateidialogs. |
| Hintergrundstandort | `ACCESS_BACKGROUND_LOCATION` fehlt. Die Reisebegleitung ist trotzdem bei ausgeschaltetem Bildschirm aktiv; ihre tatsächliche Funktionsweise im FGS-Formular und gegebenenfalls Rückfragen vollständig erklären. |
| Gesundheits-, Finanz-, Behörden- und Nachrichtenfunktionen | Keine entsprechenden Kernfunktionen. Reisehinweise sind unverbindliche Fahrplanauskünfte. |
| Konto-Löschung | Keine Konten erstellbar. Daten lokal über App-/Android-Einstellungen löschen; kein Kontolöschungs-Link erforderlich. |

### Datensicherheit: Datenfluss als Eingabegrundlage

Nicht pauschal „keine Daten erhoben“ angeben. Drittanbieter erhalten funktionale Anfragen direkt vom Gerät, auch ohne Analyse-SDK.

| Datentyp | Erhebung / Übertragung | Zweck / Freiwilligkeit |
| --- | --- | --- |
| Ungefährer und genauer Standort | Android-Ortung; nötige Start-/Zielkoordinaten an VRR, gegebenenfalls Photon und Gehführungsdienst; gerundetes Ziel an optionales Wetter; betrachtete Region über Kartenkacheln | App-Funktionalität. Geräteortung und Wetter können abgelehnt werden. Manuelle Ortsangaben bleiben für die angeforderte Suche erforderlich. |
| App-Aktivität → Suchverlauf | Getippte Orts-/Haltestellensuche, Start/Ziel, gewünschte Zeit und Filter an die jeweils verwendeten Dienste | App-Funktionalität, wenn die Person die Suche verwendet. Kein Upload des gesamten lokalen Verlaufs. |
| Technische Verbindungsdaten | IP-Adresse, Zeitpunkt, Ressource, User-Agent an den angesprochenen Dienst | Erforderlicher Betrieb der jeweiligen Anfrage. Keine eigene Analyse oder Geräte-Werbe-ID. IP-Protokolle anhand der Console-Definitionen bei Standort/Identifikatoren berücksichtigen. |
| Favoriten, persönliche Designs und Sensorrohdaten | Privat lokal; kein eigener Upload | Nicht allein wegen lokaler Speicherung als serverseitig erhoben melden. Bewusst exportierte persönliche Sicherungen werden über den vom Nutzer gewählten Speicher abgelegt. |

Für übertragene Kategorien **App-Funktionalität** wählen; keine Werbung/Marketing, Analytik oder Personalisierung beim Entwickler. Nicht „nur flüchtig“ zusichern: Anbieter können technische Protokolle speichern, Open-Meteo beschreibt bis zu 90 Tage. „Optional“ bei Geräteortung bedeutet nicht, dass eine manuell beauftragte Verbindung ohne Start/Ziel auskommt.

Eine Weitergabe an externe Dienste ist vorhanden. Die Console kennt Ausnahmen für ausdrücklich beauftragte, erwartbare Übertragungen oder tatsächlich beauftragte Dienstleister. Diese nicht ohne Prüfung als pauschale Ausnahme für alle Anbieter verwenden. Konservativ die betroffenen Standort-/Suchkategorien auch als geteilt deklarieren, soweit keine belegte Ausnahme greift. Die App verwendet HTTPS und verbietet Klartextverkehr. Es gibt keinen Datenverkauf. Lokales Löschen entfernt keine Protokolle externer Anbieter; deren Kontaktwege stehen in der Datenschutzerklärung.

### Abrufgrenzen des VRR-Profils

Diese Client-Grenzen sind keine VRR-Quote: höchstens drei parallele JSON-Anfragen, ein gemeinsames Budget von 24 tatsächlichen Anfragen pro Übersichtslauf (auch über mehrere Favoriten und Routenvarianten), 32 beim Öffnen einer Verbindungssuche, zwölf pro bewusstem Rand-Nachladen und 16 pro Reiseabgleich. Identische laufende Anfragen teilen ihren Transport. Bereits gelieferte brauchbare Ergebnisse bleiben bei Fehlern erhalten; weitere Ziele können einzeln geöffnet werden.

## 4. Vordergrunddienst erklären und filmen

Manifest-Typen: `location` und `dataSync`. Die Begleitung beginnt durch bewusstes Anpinnen einer Reise. Sie zeigt nächsten Einstieg/Umstieg und Prognosen, gleicht ungefähr minütlich beim Anbieter ab und verwendet erlaubten Standort für lokalen Reisefortschritt. Ohne GPS bleibt der Fahrplanabgleich verfügbar. Im Stromsparmodus wird reduziert; Anbieterpausen und Android-Beschränkungen gelten. Die Bubble zeigt dieselbe Sitzung und startet keine zweite Schleife.

Für jeden angefragten FGS-Typ in der Console eine Beschreibung, Nutzerwirkung bei Unterbrechung und ein echtes Demonstrationsvideo angeben. Vor dem Store-Test am Telefon filmen:

1. Eine Verbindung manuell suchen und anpinnen.
2. Standort-/Benachrichtigungs-Erklärung und bewusste Freigabe zeigen.
3. App verlassen, laufende Benachrichtigung und Fortsetzung zeigen.
4. Optional Bubble ein-/ausblenden; die Sitzung läuft dabei unverändert.
5. **Reise beenden** in der Benachrichtigung drücken; Dienst/Benachrichtigung und Standortanzeige enden.

Das Video muss die ausgelieferte Funktion zeigen. Automatisierte UI-Bilder ersetzen dieses Video und die reale Geräteprüfung nicht.

## 5. Geschlossenen Test starten

1. Final signiertes AAB in den internen Test laden; Play-Prüfungen und Pre-Launch Report auswerten.
2. Gerätetest: Android 8 und aktuelles Android, ohne/mit ungefährem/genauem Standort, Benachrichtigungen verweigert, Stromsparmodus, Offline/429, große Schrift, Widgets und Reiseende. Reale Fahrten für GPS-Verhalten durchführen.
3. Im geschlossenen Test Tester-Liste oder Google-Gruppe, Opt-in-Link, Länder und Release anlegen. Eingeladene Tester müssen tatsächlich beitreten.
4. Für neue persönliche Entwicklerkonten kann Google mindestens zwölf durchgehend angemeldete Tester über 14 Tage und danach einen Antrag auf Produktionszugang verlangen. Die im eigenen Konto angezeigte Vorgabe ist maßgeblich.
5. Testerfeedback, Fehler und tatsächliche Nutzung sammeln; keine Produktionsfreigabe behaupten, bevor Google sie erteilt hat.

## 6. Quellcode und spätere Reichweite

Der vollständige, baubare Quellcode jeder Version liegt öffentlich in diesem Repository; jedes Release ist ein Tag auf `main`. Keine privaten Schlüssel oder Konfigurationen einchecken.

VRR hat das kleine Hobbyprojekt mit OpenService ohne gesonderten Produktivzugang bestätigt. Es gibt keine zugesagte feste Abrufquote. Deutlich wachsendes Volumen, eine geänderte Nutzung oder Datenexport erneut abstimmen. Die Korrespondenz wird nicht im Repository abgelegt.

Ein Wechsel des Fahrplandienstes ist vorbereitet, siehe [timetable-services.md](timetable-services.md): Schnittstelle, Echtzeit, Gebiet, Lizenz, Attribution, Zugang und Anfragevolumen klären; danach Adapter, Dokumentation, Datenschutz und Store-Angaben gemeinsam umstellen.

## Primärquellen

- VRR API: https://www.opendata-oepnv.de/ht/de/api
- VRR Bedingungen: https://www.opendata-oepnv.de/ht/de/standards/nutzungsvereinbarung-api
- Play Datensicherheit: https://support.google.com/googleplay/android-developer/answer/10787469
- Play Nutzerdaten: https://support.google.com/googleplay/android-developer/answer/10144311
- Play Vordergrunddienste: https://support.google.com/googleplay/android-developer/answer/13392821
- Play persönliche Konten/Tests: https://support.google.com/googleplay/android-developer/answer/14151465
- Bundle-Prüfung: https://developer.android.com/guide/app-bundle/test
- Store-Bilder: https://support.google.com/googleplay/android-developer/answer/9866151
- 16-KB-Seiten: https://developer.android.com/guide/practices/page-sizes
