#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parents[1]
entries=json.loads((ROOT/'localization/glossary.json').read_text())
ids=[e['id'] for e in entries]
assert len(set(ids))==len(ids),'duplicate glossary ids'
assert all(all(isinstance(e.get(k),str) and e[k].strip() for k in ['id','de','en','description_de','description_en','category']) for e in entries)
def lit(s):return json.dumps(s,ensure_ascii=False).replace('$','\\$')
def kotlin_catalog():
    selected = entries
    return '''// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

data class AppGlossaryEntry(val id: String, val titleKey: String, val descriptionKey: String, val categoryKey: String)

val appGlossary: List<AppGlossaryEntry> = listOf(
'''+''.join('    AppGlossaryEntry('+','.join(lit(e[k]) for k in ['id','de','description_de','category'])+'),\n' for e in selected)+')\n'
md='# Verbindliche Begriffe · Öpen-V\n\nQuelle: `localization/glossary.json`, erzeugt mit `tools/generate_glossary.py`. Neue Oberfläche und Hilfetexte verwenden diese Begriffe in beiden Sprachen. Persistierte Schlüssel und Inhalts-IDs werden dafür nicht umbenannt. Zwischenhalt (Fahrzeug) und Zwischenstopp (selbst gewähltes Ziel) bleiben verschieden.\n\n| Deutsch | English | Bedeutung |\n| --- | --- | --- |\n'+''.join(f"| {e['de']} | {e['en']} | {e['description_de']} |\n" for e in entries)
catalog=json.loads((ROOT/'localization/en.json').read_text())
for e in entries:catalog[e['de']]=e['en'];catalog[e['description_de']]=e['description_en']
outputs = {'app/src/main/java/dev/ambivalenton/openv/data/model/AppGlossary.kt': kotlin_catalog()}
outputs.update({'docs/glossary.md':md,'localization/en.json':json.dumps(catalog,ensure_ascii=False,indent=2,sort_keys=True)+'\n'})
for rel,text in outputs.items():
 path=ROOT/rel
 if '--check' in sys.argv:assert path.read_text()==text,f'Regenerate {rel}'
 else:path.write_text(text)
print(f'{len(entries)} glossary entries, DE/EN parity verified')

