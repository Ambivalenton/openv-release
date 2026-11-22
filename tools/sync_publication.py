#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate the in-app legal texts and the privacy web page from the authoritative Markdown.

Sources: PRIVACY.md (German), PRIVACY_EN.md (English), docs/legal/publisher.de.md and
docs/legal/publisher.en.md. Outputs: app/src/main/assets/legal/** and docs/privacy/index.html
(served by GitHub Pages). Run with --check in CI."""
from pathlib import Path
from html import escape
import re,sys
ROOT=Path(__file__).resolve().parents[1]
sources={'privacy_de':'PRIVACY.md','privacy_en':'PRIVACY_EN.md',
         'publisher_de':'docs/legal/publisher.de.md','publisher_en':'docs/legal/publisher.en.md'}
texts={key:(ROOT/path).read_text() for key,path in sources.items()}
outputs={
    'app/src/main/assets/legal/privacy.txt':texts['privacy_de'],
    'app/src/main/assets/legal/publisher.txt':texts['publisher_de'],
    'app/src/main/assets/legal/en/privacy.txt':texts['privacy_en'],
    'app/src/main/assets/legal/en/publisher.txt':texts['publisher_en'],
}

def inline(text):
    safe=escape(text)
    safe=re.sub(r'\[([^\]]+)\]\(((?:https://|mailto:)[^\s)]+)\)',r'<a href="\2">\1</a>',safe)
    return re.sub(r'\*\*([^*]+)\*\*',r'<strong>\1</strong>',safe)

def html_document(text):
    result=[]
    for block in re.split(r'\n\s*\n',text.strip()):
        lines=block.splitlines()
        if block.startswith('# '):result.append('<h1>'+inline(block[2:])+'</h1>')
        elif block.startswith('### '):result.append('<h3>'+inline(block[4:])+'</h3>')
        elif block.startswith('## '):result.append('<h2>'+inline(block[3:])+'</h2>')
        elif block.startswith('> '):result.append('<aside>'+inline('\n'.join(line.removeprefix('> ') for line in lines))+'</aside>')
        elif all(line.startswith('- ') for line in lines):result.append('<ul>'+''.join('<li>'+inline(line[2:])+'</li>' for line in lines)+'</ul>')
        elif all(line.startswith('|') for line in lines):
            rows=[[cell.strip() for cell in line.strip('|').split('|')] for line in lines]
            rows=[row for row in rows if not all(re.fullmatch(r':?-{3,}:?',cell) for cell in row)]
            result.append('<div class="table"><table>'+''.join('<tr>'+''.join(f'<{tag}>'+inline(cell)+f'</{tag}>' for cell in row)+'</tr>' for index,row in enumerate(rows) for tag in ['th' if index==0 else 'td'])+'</table></div>')
        else:result.append('<p>'+inline(block).replace('\n','<br>')+'</p>')
    return '\n'.join(result)

parts=[]
for language,key in [('de','privacy_de'),('en','privacy_en')]:
    parts.append(f'<section id="{language}" lang="{language}">'+html_document(texts[key])+'</section>')
outputs['docs/privacy/index.html']='''<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Öpen-V · Datenschutz / Privacy</title>
<style>body{max-width:56rem;margin:2rem auto;padding:0 1.2rem;font:17px/1.7 system-ui;color:#183431;background:#f8faf7}h1{font-size:2rem;line-height:1.2}h2{margin-top:2.6rem;padding-top:1rem;border-top:1px solid #c7d5ce;line-height:1.35}h3{line-height:1.4}a{color:#17634d;text-underline-offset:.15em}nav{display:flex;gap:1.2rem}aside{background:#e4eee6;border-left:4px solid #397256;padding:1rem 1.3rem;border-radius:.5rem}section{margin-bottom:5rem}table{width:100%;border-collapse:collapse}th,td{text-align:left;vertical-align:top;padding:.8rem;border-bottom:1px solid #c7d5ce}th{background:#e4eee6}.table{overflow-x:auto}li{margin:.6rem 0}@media(prefers-color-scheme:dark){body{color:#dfebe3;background:#14221d}aside,th{background:#253c30}a{color:#a4dabb}}@media(max-width:35rem){th,td{padding:.5rem;font-size:.9em}}</style></head>
<body><nav aria-label="Sprache / Language"><a href="#de">Deutsch</a><a href="#en">English</a></nav>
'''+''.join(parts)+'</body></html>\n'
for rel,content in outputs.items():
    path=ROOT/rel
    if '--check' in sys.argv:assert path.read_text()==content,f'Regenerate {rel}'
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
print('Bilingual app and publication copies are consistent.')

