# Öpen-V – Hinweise für Claude Code

Freie, werbefreie ÖPNV-App für Android (GPL-3.0-or-later). Herausgeber: Ambivalenton. Dieses öffentliche Repository ist die offizielle Quelle von Öpen-V; hier findet die Weiterentwicklung statt.
Verbindliche Arbeitsregeln: @PROJEKTREGELN.md

## Stand und Identität

- Paket `dev.ambivalenton.openv`; `versionCode`/`versionName` in `app/build.gradle.kts` (öffentlicher Start: 100 / 1.0.0).
- Kotlin 2.3.21, Jetpack Compose (Material 3 Expressive), AGP 8.13.2, Gradle 8.13 (Wrapper), compile/targetSdk 36, minSdk 26, Java-Ziel 17.
- Eine einzige Variante, keine Product Flavors. Build-Typen: `debug`, `preview` („Öpen-V · Vorschau“, `dev.ambivalenton.openv.next`), `release`.
- Quellcode: `app/src/main/java/dev/ambivalenton/openv/`, Tests: `app/src/test/`. Architektur: `docs/architecture.md`.
- Fahrplandienst: nur VRR OpenService (EFA-JSON). Gewählt in `gradle.properties` (`openv.timetable`, `openv.timetable.protocol`) über `data/model/TimetableService.kt`; ein deutschlandweiter Dienst (DELFI-Navi, EFA oder TRIAS) ist im Code vorbereitet, aber nicht aktiv, siehe `docs/timetable-services.md`. In README, Changelog, Store-Texten und Webseite DELFI-Navi nicht als Ziel oder Partner nennen, solange es keinen Kontakt gibt.
- Nutzungsbedingungen des Fahrplandienstes sind verbindlich: `docs/data-sources.md`. Neue automatische Abrufe nur bedarfsgerecht; kein Teilen oder Export von Fahrplandaten als Bild oder Text.
- Haltestellenkatalog `app/src/main/assets/stops/stops.catalog` wird mitgeliefert, nicht neu importieren.

## Bauen und testen

```bash
python3 tools/check_release.py                  # Versionen, Lizenzköpfe, Manifest, Texte, alle Generatoren
./gradlew --no-daemon :app:testDebugUnitTest    # gezielt: --tests "*Name"
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:assemblePreview -PapkSplits=true -PapkAbis=arm64-v8a
python3 tools/verify_apk.py --apk <apk> --package dev.ambivalenton.openv.next --report-cert
```

- Benötigt: JDK 17+, Android SDK Platform 36, Build-Tools 36.0.0 (`ANDROID_HOME`). In Cloud-Sitzungen ggf. vorher installieren; dafür muss `dl.google.com` erreichbar sein.
- Keine parallelen Gradle-Läufe, kein routinemäßiges `clean`.
- Generierte Dateien (Übersetzungen, Glossar, Lizenzhinweise, Rechtstexte) nie von Hand ändern, sondern Quelle bearbeiten und Generator ausführen: Tabelle in `docs/development.md`.

## Lieferung

- GitHub Actions (`.github/workflows/ci.yml`) prüft jeden Push und Pull Request. Auf `main` erscheint die ARM64-Vorschau-APK im Release `vorschau`. Öffentliche Repositorys haben unbegrenzte Actions-Minuten.
- Ein Tag `v<versionName>` startet `.github/workflows/release.yml`: signierte APK, App Bundle und Prüfsummen als GitHub-Release (`docs/release.md`).
- Signierung nur über Repository-Secrets: `OPENV_REVIEW_KEYSTORE_BASE64` (Vorschau) und `OPENV_KEYSTORE_BASE64`, `OPENV_STORE_PASSWORD`, `OPENV_KEY_ALIAS`, `OPENV_KEY_PASSWORD` (Release). Niemals einen Schlüssel erzeugen und einchecken.
- Bei einer neuen Version `versionCode` erhöhen, `CHANGELOG.md` und `fastlane/metadata/android/*/changelogs/<versionCode>.txt` ergänzen.

## Arbeitsweise im Repository

- Der Herausgeber arbeitet am Smartphone: Ergebnis ist ein gepushter Stand mit grünem Actions-Lauf; die Vorschau-APK lädt er aus dem Release `vorschau`.
- Dies ist ein öffentliches Repository: keine Schlüssel, Passwörter, `local.properties`, Build-Ausgaben, persönlichen Daten oder private Korrespondenz committen. Prüfnachweise (`audit/`) gehören nicht hinein.
- Commit-Autor: `Ambivalenton <354080168+Ambivalenton@users.noreply.github.com>`.
- Keine pro-Build-Berichtsdateien anlegen – die Git-Historie ersetzt sie. Wichtige Design- und Verhaltensentscheidungen knapp in `docs/` und `CHANGELOG.md` festhalten.
- Texte für Nutzer: Deutsch als Quelle, Englisch in `localization/en.json`; Begriffe nach `docs/glossary.md`.
