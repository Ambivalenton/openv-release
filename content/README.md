# Lokaler Content-Pool

`journey_content.json` ist die redaktionelle Quelle. IDs nach Veröffentlichung beibehalten und neue IDs ergänzen; Texte nicht durch Kombination generischer Satzfragmente vervielfältigen. Die vier Kategorien haben verschiedene Aufgaben: FACT benötigt eine belegende Primärquelle und Prüfdatum; THOUGHT, HAIKU und SATIRE tragen die Kennzeichnung eigener Formulierungen. Der Pool enthält 671 unterschiedliche Einträge (291 Fakten) in jeweils zwei Sprachen.

Nach Änderungen `python3 tools/generate_journey_content.py` ausführen. Die generierte Kotlin-Datei gehört zum Quellcode und wird eingecheckt. `--check` verifiziert, dass JSON und Kotlin übereinstimmen. Der reguläre Android-Build benötigt dafür weder Python noch einen Server. Zur Laufzeit wird eine unveränderliche Liste einmal lazy initialisiert; es gibt keine Asset-Dateizugriffe und keine Downloads im UI. Ein sprachabhängiger Shuffle-Bag erschöpft den gesamten Pool vor der nächsten Wiederholung, auch ohne unmittelbare Wiederholung an der Zyklusgrenze.

Es gibt keine Fremdzitate mit ungeprüfter Autorenzuordnung. Freie Reisehaikus sind ästhetische Dreizeiler ohne zugesicherte strenge Silbenmetrik. Faktenquellen und redaktionelle Regeln stehen in [`docs/fun-fact-sources.md`](../docs/fun-fact-sources.md).

Dazu gehören 80 kurze Astronomie-Fakten aus NASA-Primärquellen. Die Chronik speichert ausschließlich tatsächlich sichtbare Übersichtstexte lokal in einer separaten SQLite-Datei. Fakten und Sprüche sind getrennte, aufklappbare Gruppen; Gedanken, Haikus und Satire gehören zu Sprüche. Wiederholtes Lesen aktualisiert Zeitstempel und Zähler desselben Textes. Sprach- und Textfassungen bleiben erhalten.
