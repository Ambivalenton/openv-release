## Was ändert sich?

<!-- Verhalten aus Sicht der App, kurz und konkret. -->

## Warum?

<!-- Problem oder Issue, z. B. „behebt #12“. -->

## Geprüft

- [ ] `python3 tools/check_release.py`
- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug`
- [ ] Neues Verhalten hat einen Test
- [ ] Auf einem Gerät oder Emulator ausprobiert (falls Oberfläche betroffen)
- [ ] Keine neuen automatischen Abrufe ohne Regel aus `docs/data-sources.md`
