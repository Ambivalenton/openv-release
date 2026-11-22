# Lokalisierung / localisation

Unterstützt: Deutsch und Englisch. Ohne App-spezifische Auswahl folgt Öpen-V der Android-Sprache. Andere Sprachen verwenden Englisch. Android 13+ erkennt beide Sprachen über locales_config.xml und kann eine App-Sprache vorgeben.

`en.json` ist der gepflegte Katalog: Schlüssel sind wörtliche deutsche Quellvorlagen, Werte die englische Übersetzung. `tr(...)` darf nur vom Projekt festgelegte Vorlagen und kontrollierte redaktionelle Katalogtexte erhalten. Nutzereingaben, Ortsnamen, Provider-IDs und Netzantworten werden niemals als Übersetzungsschlüssel verwendet. Formatargumente bleiben Daten; „Arbeit“ als eigener Ortsname wird nicht zu „Work“ umgeschrieben.

`Strings.kt` ist eine reine Kotlin-Textgrenze ohne Android-Context oder veränderliche globale Konfiguration. Sie wählt per aktuellem Locale und formatiert nummerierte Argumente. `EnglishCatalog.kt` wird aus JSON generiert. Android-Strings unter values/ (Englisch) und values-de/ bedienen Manifest, Widget-Galerie und statische RemoteViews. Offline-Hilfe, Datenschutz und Quellen unter assets/legal/ besitzen eine englische Fassung unter assets/legal/en/.

Nach Textänderungen:

```sh
python3 tools/generate_translations.py
python3 tools/generate_translations.py --check
./gradlew :app:testDebugUnitTest --tests '*ReleaseReadinessTest' --tests '*ReleaseIntro*UiTest'
```

Der Generator prüft nummerierte Formatargumente. Enum-Titel und redaktionelle Anzeigen werden beim Zugriff gewählt, damit ein Konfigurationswechsel nicht auf einer beim Prozessstart eingefrorenen Sprache bleibt. API-Anfragen verwenden de oder en. Eigene und fremde Ortsnamen werden beibehalten. Der Migrationshelfer für die einmalige Umstellung ist nicht Teil des Releasepakets; neue Texte werden direkt mit tr(...) und einem Katalogeintrag ergänzt.

English: maintain German source templates and English translations in en.json, regenerate the pure Kotlin catalog, and run the focused language tests. Android handles locale configuration; no translation service or account is involved. User-entered names and provider identifiers remain untouched. Legal and help texts are maintained separately in both languages. Additional languages require a complete reviewed catalog, matching Android resources/legal assets, an explicit resolver branch, locale registration and UI checks; a language is not advertised merely because a few buttons were translated.
