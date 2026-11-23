# Release

## Automatisch bei jedem Push auf `main`

Der Workflow [`ci.yml`](../.github/workflows/ci.yml) prüft Quellen und Texte, führt alle Tests und Lint aus und baut die Test-App **„Öpen-V · Vorschau“** (`dev.ambivalenton.openv.next`, ARM64). Auf `main` erscheint sie im Release [`vorschau`](https://github.com/Ambivalenton/openv-release/releases/tag/vorschau), das jedes Mal ersetzt wird. Die Beschreibung des Releases entsteht aus `fastlane/metadata/android/{de-DE,en-US}/changelogs/<versionCode>.txt` (eine Zeile je Änderung, beginnend mit „• “); diese Texte sind öffentlich und daher allgemein, sachlich und ohne interne Details formuliert. Bei Pull Requests liegt sie als Actions-Artefakt bereit.

Damit sich jede Vorschau als Update installiert, braucht das Repository das Secret `OPENV_REVIEW_KEYSTORE_BASE64` (Base64 eines Android-Debug-Keystores mit Alias `androiddebugkey`, Passwort `android`). Fehlt es, ist die Vorschau mit einem wechselnden Schlüssel signiert und lässt sich nur nach Deinstallation neu installieren.

## Version veröffentlichen

1. In `app/build.gradle.kts` `versionCode` erhöhen und `versionName` setzen (z. B. `1.0.1`).
2. `CHANGELOG.md` ergänzen und die Store-Hinweise anlegen: `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt` und `en-US/…` (je höchstens 500 Zeichen).
3. Auf `main` committen und pushen, grünen CI-Lauf abwarten.
4. Tag setzen: `git tag v1.0.1 && git push origin v1.0.1` – oder am Handy: Releases → *Draft a new release* → neuen Tag `v1.0.1` auf `main` → *Publish*.

Der Workflow [`release.yml`](../.github/workflows/release.yml) baut dann:

| Datei | Zweck |
| --- | --- |
| `OpenV-<version>.apk` | Universelle APK für die direkte Installation |
| `OpenV-<version>.aab` | App Bundle für Google Play |
| `OpenV-<version>-SHA256SUMS.txt` | Prüfsummen |

und hängt sie an das GitHub-Release. Der Tag muss zur `versionName` passen, sonst bricht der Lauf ab.

### Signierung

| Secret | Inhalt |
| --- | --- |
| `OPENV_KEYSTORE_BASE64` | Base64 des Upload-Keystores für Google Play |
| `OPENV_STORE_PASSWORD`, `OPENV_KEY_ALIAS`, `OPENV_KEY_PASSWORD` | Zugangsdaten dazu |

Ohne diese Secrets entstehen ausdrücklich als `UNSIGNIERT` markierte Dateien. Mit Play App Signing signiert Google die ausgelieferte App neu; eine von GitHub installierte APK und die Play-Version lassen sich dann nicht gegenseitig aktualisieren.

Secrets setzt du unter *Settings → Secrets and variables → Actions → New repository secret* – das geht auch im Handy-Browser.

## Google Play

Store-Eintrag, App-Inhalte, Datensicherheit, Vordergrunddienst und geschlossener Test: [play-store.md](play-store.md). Store-Texte und Bilder liegen unter `fastlane/metadata/android/` (Deutsch, Englisch), die Datenschutz-URL ist <https://ambivalenton.github.io/openv-release/privacy/>.

## GitHub Pages

Pages veröffentlicht den Ordner `docs/` von `main` (*Settings → Pages → Deploy from a branch → main /docs*). Die Datenschutzseite `docs/privacy/index.html` wird von `tools/sync_publication.py` erzeugt; nicht von Hand bearbeiten.
