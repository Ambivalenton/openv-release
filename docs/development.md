# Entwicklung

## Voraussetzungen

| Werkzeug | Version |
| --- | --- |
| JDK | 17 oder neuer (CI nutzt 21) |
| Android SDK | Platform 36, Build-Tools 36.0.0; `ANDROID_HOME` gesetzt |
| Python | 3.10 oder neuer, nur für Generatoren und Prüfungen |

Gradle 8.13 kommt über den Wrapper; Kotlin 2.3.21 und AGP 8.13.2 sind in `build.gradle.kts` festgelegt.

## Bauen und prüfen

```bash
python3 tools/check_release.py               # Versionen, Lizenzköpfe, Manifest, Texte, Generatoren
./gradlew :app:testDebugUnitTest             # alle Tests (gezielt: --tests "*Name")
./gradlew :app:lintDebug                     # Android-Lint
./gradlew :app:assemblePreview               # Test-App „Öpen-V · Vorschau“ (dev.ambivalenton.openv.next)
./gradlew :app:assembleRelease               # Release-APK (unsigniert ohne Upload-Schlüssel)
./gradlew :app:bundleRelease                 # App Bundle für Google Play
python3 tools/verify_apk.py --apk <apk> --package dev.ambivalenton.openv.next --report-cert   # gebaute APK prüfen
```

`-PapkSplits=true -PapkAbis=arm64-v8a` baut eine APK nur für ARM64-Geräte (kleiner). Gradle-Läufe nicht parallel starten; `clean` ist selten nötig.

## Varianten und Kennungen

| Build-Typ | Paket | Name | Signatur |
| --- | --- | --- | --- |
| `debug` | `dev.ambivalenton.openv` | Öpen-V | lokaler Debug-Schlüssel |
| `preview` | `dev.ambivalenton.openv.next` | Öpen-V · Vorschau | Testschlüssel aus `OPENV_REVIEW_KEYSTORE`, sonst Debug-Schlüssel |
| `release` | `dev.ambivalenton.openv` | Öpen-V | Upload-Schlüssel aus `OPENV_KEYSTORE`, sonst unsigniert |

Die Vorschau installiert sich neben der Store-App. Schlüssel liegen nie im Repository; CI stellt sie aus Secrets wieder her (siehe [release.md](release.md)).

Der Fahrplandienst wird in `gradle.properties` gewählt (`openv.timetable`), siehe [timetable-services.md](timetable-services.md).

## Generierte Dateien

Einige Kotlin- und Textdateien werden aus gepflegten Quellen erzeugt und eingecheckt. Nach Änderungen an den Quellen den Generator ausführen; `--check` prüft in CI, dass nichts veraltet ist.

| Quelle | Generator | Ergebnis |
| --- | --- | --- |
| `localization/en.json` | `tools/generate_translations.py` | `localization/EnglishCatalog.kt` (nur Texte, die die App verwendet) |
| `localization/glossary.json` | `tools/generate_glossary.py` | `data/model/AppGlossary.kt`, [glossary.md](glossary.md) |
| `content/journey_content.json` | `tools/generate_journey_content.py` | Texte der Übersicht |
| `content/icon_context.json` | `tools/generate_icon_content.py` | Texte zu Favoritensymbolen |
| `localization/fonts.json` | `tools/generate_fonts.py` | Schriftkatalog, [fonts.md](fonts.md) |
| `content/dependency-licenses.json`, `content/licenses-*.md` | `tools/generate_notices.py` | Lizenzhinweise in der App, `THIRD_PARTY_NOTICES.md` |
| `PRIVACY.md`, `PRIVACY_EN.md`, `docs/legal/publisher.*.md` | `tools/sync_publication.py` | Rechtstexte in der App, `docs/privacy/index.html` |

Der Haltestellenkatalog (`app/src/main/assets/stops/stops.catalog`) wird mitgeliefert und nur bei einem neuen DELFI-Export mit `tools/build_stop_catalog.py` neu gebaut; `tools/check_stop_catalog.py` prüft ihn.

## Texte und Sprachen

Oberflächentexte sind deutsche Vorlagen in `tr("…")`; die englische Übersetzung steht in `localization/en.json`. Begriffe folgen dem [Glossar](glossary.md). Nutzereingaben, Ortsnamen und Anbieterdaten werden nie übersetzt. Mehr in [localization/README.md](../localization/README.md).

## Tests

Die Tests laufen auf der JVM mit [Robolectric](https://robolectric.org/) (native Grafik), auch die Compose-Oberflächen. Die Android-Laufzeit für Robolectric (`android-all-instrumented`, SDK 35) löst Gradle wie jede andere Abhängigkeit auf und legt sie in `app/build/robolectric-runtime/` ab; Robolectric läuft offline darauf, statt sie bei jedem Lauf selbst von Maven Central zu laden. Bei einem Robolectric-Update die Version in `app/build.gradle.kts` mit anpassen. Bildschirmaufnahmen einzelner Tests landen in `audit/` und gehören nicht ins Repository. Neue Funktionen bekommen einen Test, der das Verhalten prüft, nicht nur die Konfiguration.

## Konventionen

- Kotlin-Dateien beginnen mit `// SPDX-License-Identifier: GPL-3.0-or-later`.
- Eine Datenquelle pro Thema: Ansichten lesen den Zustand des ViewModels und halten keine Kopie.
- Netzwerkanfragen nur über `NetworkClient`; neue automatische Aktualisierungen müssen die Regeln aus [data-sources.md](data-sources.md) einhalten.
- Keine proprietären SDKs (Google Play Services, Firebase, Werbung, Tracking); `tools/check_release.py` prüft das.
- Commits beschreiben das Verhalten aus Sicht der App; wichtige Entscheidungen gehören zusätzlich in `CHANGELOG.md` oder `docs/`.
