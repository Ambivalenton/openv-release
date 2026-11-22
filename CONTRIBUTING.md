# Mitmachen bei Öpen-V

Danke, dass du Öpen-V besser machen willst. Fehlerberichte, Ideen, Übersetzungen und Code sind willkommen.

## Fehler und Ideen

- **Fehler:** [Fehler melden](https://github.com/Ambivalenton/openv-release/issues/new?template=bug_report.yml) mit App-Version (Einstellungen → Info & Hilfe), Android-Version und den Schritten, die zum Fehler führen.
- **Ideen:** [Idee vorschlagen](https://github.com/Ambivalenton/openv-release/issues/new?template=feature_request.yml). Beschreibe zuerst das Problem im Alltag, dann deinen Vorschlag.
- **Sicherheitslücken** bitte nicht öffentlich melden, sondern wie in [SECURITY.md](SECURITY.md) beschrieben.

Bitte keine echten Wohnadressen, Bewegungsprofile oder Bildschirmfotos mit persönlichen Daten in Issues stellen.

## Code beitragen

1. Repository forken und einen Branch anlegen.
2. Bauen und testen, wie in [docs/development.md](docs/development.md) beschrieben:
   ```bash
   python3 tools/check_release.py
   ./gradlew :app:testDebugUnitTest :app:lintDebug
   ```
3. Für neues Verhalten einen Test schreiben, der das Verhalten prüft.
4. Pull Request öffnen und die Vorlage ausfüllen. Die Prüfungen in GitHub Actions müssen grün sein.

### Was einen Beitrag leicht annehmbar macht

- **Klein und fokussiert:** eine Änderung pro Pull Request.
- **Datenschutz bleibt gewahrt:** keine Tracking-, Werbe- oder Analyse-Bibliotheken, keine Google Play Services, keine neuen Server ohne Absprache.
- **Fahrplandienst schonen:** neue automatische Abrufe nur, wenn sie der Person nützen, und nur nach den Regeln in [docs/data-sources.md](docs/data-sources.md). Fahrplandaten werden nicht als Bild oder Text exportiert.
- **Texte:** Deutsch ist die Quellsprache; neue Texte stehen in `tr("…")` und brauchen eine englische Übersetzung in `localization/en.json` (danach `python3 tools/generate_translations.py`). Begriffe folgen dem [Glossar](docs/glossary.md).
- **Lizenz:** Jede Kotlin-Datei beginnt mit `// SPDX-License-Identifier: GPL-3.0-or-later`. Mit deinem Beitrag stimmst du zu, dass er unter GPL-3.0-or-later steht. Fremder Code, Schriften oder Daten brauchen eine verträgliche Lizenz und einen Eintrag in `content/dependency-licenses.json`.

## Übersetzungen

Öpen-V unterstützt Deutsch und Englisch. Eine weitere Sprache braucht einen vollständigen, geprüften Katalog samt Rechts- und Hilfetexten; einzelne übersetzte Knöpfe reichen nicht. Sprich eine neue Sprache bitte vorher in einem Issue an.

## Kontakt

[oepenvapp@gmail.com](mailto:oepenvapp@gmail.com)
