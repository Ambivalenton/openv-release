# Verlässlichkeit von Verbindungen

Stand: 1.1.7 (Build 117, Grundgerüst). Dieses Dokument hält Recherche, Modell, Startwerte und Grenzen fest. Code: `data/reliability/` (`Reliability.kt` Modell, `JourneyReliability.kt` Verbindung → Modell, `ReliabilityMemory.kt` eigene Rückmeldungen).

## Ziel

Öpen-V schätzt für jede Verbindung, wie sicher sie wie geplant funktioniert: ob alle Fahrten fahren, ob jeder Umstieg klappt und ob die Ankunft im Rahmen bleibt. Intern ist das ein Prozentwert, nach außen eine von fünf Stufen: **Sehr sicher** (ab 90 %), **Sicher** (75–89 %), **Mittel** (55–74 %), **Unsicher** (35–54 %), **Sehr unsicher** (unter 35 %). Zunächst dient die Bewertung nur Hinweisen und Hervorhebungen: auf der Unterwegs-Seite („Verlässlichkeit“) und auf den Knöpfen für andere Verbindungen am Umstieg. Die Suche und ihre Reihenfolge bleiben unverändert.

## Recherche (Startdatenbank)

| Verkehrsmittel | Veröffentlichter Wert | Schwelle | Quelle |
|---|---|---|---|
| Fernverkehr (DB) | 60,1 % pünktlich (2025; 2024: 62,5 %) | < 6 min | DB-Jahresbilanz, Geschäftsbericht DB Fernverkehr 2025 |
| Regionalverkehr DB bundesweit | 88,7 % (2025; 2024: 90,3 %) | < 6 min | ebd. |
| S-Bahn NRW | 77,3 % (2025; 2024: 78,4 %) | < 4 min | Qualitätsmonitor NRW (mobil.nrw) |
| Regional-Express NRW | 63,9 % (2025; 2024: 66,7 %) | < 4 min | ebd. |
| Regionalbahn NRW | 74,5 % (2025; 2024: 78,6 %) | < 4 min | ebd. |
| SPNV-Ausfälle VRR | 20,57 % der bestellten Zugkilometer (2024), davon 10,5 % baubedingt | – | VRR, SPNV-Qualitätsbericht 2024 |
| Stadtbahn Rheinbahn | 77,5–83,4 % (2023–2025) | ≤ 3 min | Qualitätsberichte Rheinbahn |
| Bus Rheinbahn | 77,3–85,6 % (2023–2025) | ≤ 3 min | ebd. |
| Stadtbus Bremen / Regionalbus | 78 % / 61 % (2025) | ≤ 5 min | VBN/ZVBN 2025 |
| U-Bahn Hamburg (eigener Fahrweg) | 98,1 % | – | Senat Hamburg 2025 |

Weitere Befunde:

- **Tageszeit:** Busse und Straßenbahnen sind im Berufsverkehr deutlich unpünktlicher; mehrere AVL-Studien sehen den Abend stärker als den Morgen (Stockholm 16–18 Uhr). Ein Frankfurter Busspur-Versuch hob die Pünktlichkeit im Nachmittagsverkehr an einer Haltestelle von 60 auf 75 %.
- **Wochenende:** dünnerer Takt, weniger Verspätungsübertragung (CEUR-Studie), aber viele geplante Bauarbeiten bei der Bahn (Wochenenden, Nächte).
- **Wetter:** Winter- und Hitzeextreme treffen vor allem die Schiene (Fernverkehr Januar 2026 rund 52 %; Infrastrukturstörungen +40 %).
- **Verlauf einer Fahrt:** Verspätungen bauen sich entlang des Laufs auf, besonders bei knappen Puffern; Reserven können sie wieder abbauen.
- **Umstiege:** Ob ein Anschluss hält, hängt von geplanter Übergangszeit, den Ankunfts- und Abfahrtsverteilungen beider Linien, dem Takt der Anschlusslinie und der Gehzeit ab (Literatur zu Anschlusszuverlässigkeit; Goverde 1999, Bruinsma/Rietveld).

## Modell

1. **Fahrt:** Ankunftsverspätung als Mischung: mit Anteil *p₀* pünktlich (0 min), sonst exponentiell verteilt mit Mittel *μ*; dazu kurzfristige Ausfälle *c*. *p₀* wird so bestimmt, dass P(Verspätung < Schwelle) den veröffentlichten Wert trifft: Anteil = p₀ + (1 − p₀)(1 − e^(−t/μ)).
2. **Kontext:** Faktoren auf Verspätungsanteil und -mittel: Berufsverkehr (Straße morgens ×1,25, abends ×1,4; Schiene ×1,15), Wochenende (Straße ×0,85), Nacht (Straße ×0,8, Schiene ×1,15), Fernverkehr Freitag- und Sonntagnachmittag ×1,15, Jahreszeit (Schiene: Winter ×1,15, Sommer ×1,08, Herbstlaub ×1,05), Weg in den Lauf (bis +20 % nach einer Stunde).
3. **Echtzeit:** Gibt es eine Prognose, ersetzt sie die Annahme: schmale Verteilung um die prognostizierte Verspätung mit kleinem Rest-Schwanz; Ausfallrisiko halbiert.
4. **Umstieg:** Hält, wenn Zubringerverspätung − Anschlussverspätung ≤ Puffer (geplanter Abstand minus Gehzeit). Beide Verteilungen werden auf einem Minutenraster (0–90 min) gefaltet.
5. **Verbindung:** Anschlusssicherheit = Π(1 − Ausfall) · Π(Umstieg hält); Ankunft = P(Endverspätung ≤ heutige Prognose + 5 min). Gesamt = 65 % Anschlusssicherheit + 35 % Ankunft. Ohne Fußweg zählt ein Umstieg mit mindestens einer Minute. Eine ausgefallene Fahrt macht die Verbindung „sehr unsicher“.
6. **Persönlich und lokal:** Rückmeldungen („Pünktlich“, „Verspätet“) zu angepinnten Fahrten zählen je Linie und je Verkehrsmittel im Zeitfenster (werktags Berufsverkehr, werktags sonst, Wochenende, Nacht). Der Anteil „pünktlich“ wird als Beta-Verteilung fortgeschrieben; der veröffentlichte Startwert zählt als zwölf Pseudobeobachtungen. Eine Linie nutzt eigene Rückmeldungen ab drei, sonst die ihres Verkehrsmittels, sonst keine. Jede Fahrt zählt einmal. Höchstens 300 Schlüssel, die ältesten fallen weg; alles bleibt auf dem Gerät, gehört nicht zu Sicherungen und lässt sich unter „Dienste“ löschen. Eigene Schätzungen werden nie als Beobachtung zurückgeführt. Automatisch beobachtete Ankünfte sind noch nicht angebunden.

## Startwerte im Modell

| Modus | Anteil pünktlich (Schwelle) | Mittel der Verspätung, wenn verspätet | Davon exakt pünktlich (p₀) | Kurzfristige Ausfälle |
|---|---|---|---|---|
| Fernverkehr | 60,1 % (< 6 min) | 14 min | 39 % | 3 % |
| Regional-Express | 63,9 % (< 4 min) | 7 min | 36 % | 4 % |
| Regionalbahn | 74,5 % (< 4 min) | 5,5 min | 47 % | 4 % |
| S-Bahn | 77,3 % (< 4 min) | 4,5 min | 45 % | 3 % |
| U-/Stadtbahn | 85 % (< 3 min) | 3 min | 59 % | 1 % |
| Straßenbahn | 80 % (< 3 min) | 3 min | 46 % | 1 % |
| Stadtbus | 78 % (< 5 min) | 5 min | 40 % | 1,5 % |
| Regionalbus/Schnellbus | 61 % (< 5 min) | 8 min | 27 % | 2 % |

Die Linie entscheidet über den Modus: „RE…“ Regional-Express, „RB…“ Regionalbahn, „SB…“/„X…“/„R…“-Busse Regionalbus. U- und Stadtbahn liegen zwischen Rheinbahn-Stadtbahn und eigenem Fahrweg (Hamburg), weil Ruhrgebietsstrecken teils im Straßenraum fahren. Die Ausfallwerte sind Schätzungen für kurzfristige Ausfälle; die VRR-Quote von 20,57 % enthält geplante, bereits im Fahrplan fehlende Fahrten.

## Wie realistisch ist das?

- **Was trägt:** Echtzeitprognosen des VRR (wo vorhanden), Umstiegspuffer und Takt sind die stärksten Signale; sie liegen in jeder Antwort vor. Damit lassen sich knappe Umstiege zuverlässig markieren.
- **Was grob bleibt:** Die Startwerte sind Jahres- und Netzmittel. Einzelne Linien weichen stark ab (RE 78: 92,9 %, RE 10 deutlich schlechter). Ohne Liniendaten ist die Stufe eine begründete Schätzung, keine Messung – das Modell zeigt darum Stufen statt scheinbar genauer Prozente.
- **Was wächst:** Persönliche Rückmeldungen und beobachtete Ankünfte machen die eigenen Linien mit der Zeit genauer. Vor einer Änderung der Suchreihenfolge braucht es eine Auswertung (Vorhersage gegen tatsächliche Ankunft, lokal, z. B. Brier-Score).
- **Nicht im Modell:** Anschlusssicherung (wartende Busse), Streiks, Großstörungen, Umleitungen. Störungsmeldungen der Anbieter fließen später als eigener Faktor ein.

## Quellen

- DB, Jahresbilanz 2025 und Geschäftsbericht DB Fernverkehr 2025 (Pünktlichkeit Fern-/Regionalverkehr, Definition < 6 min).
- mobil.nrw, Qualitätsmonitor SPNV NRW (Pünktlichkeit S, RE, RB 2024/2025) und SPNV-Qualitätsbericht NRW 2024.
- VRR, SPNV-Qualitätsbericht 2024 (Ausfallquote).
- Rheinbahn, Qualitätsberichte 2018, 2023, 2025 (Kreis Mettmann).
- VBN/ZVBN 2025 (Bremen), Senat Hamburg (Hochbahn, U-Bahn).
- Tagesspiegel „Warum kommt der Bus zu spät“; traffiQ-Busspurversuch Frankfurt.
- Studien zu Verspätungsmustern, Verspätungsausbreitung und Anschlusszuverlässigkeit (arXiv 2107.14094; CEUR Vol-2718 paper06; „Reliability and delay distributions of train connections“; Bruinsma/Rietveld, Tinbergen 98-130; Goverde 1999; MDPI Information 14/5/269).
