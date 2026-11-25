#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate the full-text index of the settings search (Build 121).

Every settings page is drawn by a root composable (see SettingsScreen.kt). This tool follows the calls of
those composables through the app's own UI code and collects every visible text (string literals passed to
tr()). The search then finds a setting by any word that is really written on its page – "Fuß" finds the
footprints of the bar chart as well as the walking pace. Texts that appear on many pages (buttons such as
"Abbrechen") are left out. Nothing is downloaded; run it after changing settings texts.
"""
from pathlib import Path
import json, re, sys
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/dev/ambivalenton/openv'
TARGET = SOURCE / 'ui/screens/SettingsSearchTexts.kt'

# The composables that draw each page (SettingsScreen.kt); extra literals of a page drawn inline there.
PAGES = {
    'MODES': ['AppModesSettings', 'BackupSettings'],
    'WALKING': ['TimelineWalkingSettings'],
    'SEARCH_PRIORITY': ['SearchPrioritySettings'],
    'APP_LOGO': ['AppLogoSettings'],
    'APP_THEME': ['AppAppearanceSettings'],
    'BOARD': ['BoardDesignSettings', 'BoardLocationControls'],
    'SENTENCES': ['OverviewDesignSettings'],
    'TYPOGRAPHY': ['ReadabilitySettings'],
    'TIMELINE': ['TimelineDesignSettings'],
    'NETWORK_PLAN': ['NetworkPlanDesignSettings'],
    'FAVORITES': ['FavoriteSettingsContent'],
    'SEARCH': ['ConnectionSearchSettings'],
    'SERVICES': ['ProviderNotice'],
    'INFO': ['InfoHelpContent'],
}
# How deep calls are followed from a page's root, and on how many pages a text may appear at most.
DEPTH = 6
MAX_PAGES = 3


def scan(text):
    """The source with comments and string contents blanked (same length) and every plain literal by position."""
    out = list(text)
    literals = {}
    i, n = 0, len(text)

    def blank(a, b):
        for k in range(a, b):
            if out[k] != '\n':
                out[k] = ' '

    def string_end(i, raw):
        """Index after the closing quote of a string starting at i (at the opening quote)."""
        j = i + (3 if raw else 1)
        while j < n:
            if raw and text.startswith('"""', j):
                while j + 3 < n and text[j + 3] == '"':
                    j += 1
                return j + 3
            c = text[j]
            if not raw and c == '\\':
                j += 2
                continue
            if not raw and c == '"':
                return j + 1
            if c == '$' and j + 1 < n and text[j + 1] == '{':
                depth = 0
                while j < n:
                    if text[j] == '{':
                        depth += 1
                    elif text[j] == '}':
                        depth -= 1
                        if depth == 0:
                            break
                    elif text[j] == '"':
                        j = string_end(j, text.startswith('"""', j)) - 1
                    j += 1
            j += 1
        return n

    while i < n:
        if text.startswith('//', i):
            j = text.find('\n', i)
            j = n if j < 0 else j
            blank(i, j); i = j
        elif text.startswith('/*', i):
            depth, j = 0, i
            while j < n:
                if text.startswith('/*', j):
                    depth += 1; j += 2
                elif text.startswith('*/', j):
                    depth -= 1; j += 2
                    if depth == 0:
                        break
                else:
                    j += 1
            blank(i, j); i = j
        elif text[i] == '"':
            raw = text.startswith('"""', i)
            j = string_end(i, raw)
            body = text[i + (3 if raw else 1): j - (3 if raw else 1)]
            if not raw and '${' not in body and not re.search(r'(?<!\\)\$[A-Za-z_]', body):
                literals[i] = body
            blank(i + 1, j - 1); i = j
        elif text[i] == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == '\\' else 1
            blank(i + 1, j); i = j + 1
        else:
            i += 1
    return ''.join(out), literals


def unescape(s):
    return re.sub(r'\\(u[0-9a-fA-F]{4}|.)', lambda m: chr(int(m.group(1)[1:], 16)) if m.group(1)[0] == 'u' and len(m.group(1)) == 5
                  else {'n': '\n', 't': '\t', 'r': '\r'}.get(m.group(1), m.group(1)), s)


def match_close(masked, i, open_c, close_c):
    depth = 0
    for j in range(i, len(masked)):
        if masked[j] == open_c:
            depth += 1
        elif masked[j] == close_c:
            depth -= 1
            if depth == 0:
                return j
    return len(masked) - 1


DECLARATION = re.compile(r'\n[ \t]*(?:@[A-Za-z_][\w.]*(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|protected|override|inline|suspend|'
                         r'data|enum|sealed|abstract|open|operator|infix|tailrec)\s+)*(?:fun|val|var|class|object|interface|typealias)\b')
FUN = re.compile(r'\bfun\s+(?:<[^>{}]*>\s*)?(?:[A-Za-z_][\w.]*(?:<[^>]*>)?\??\.)?([A-Za-z_]\w*)\s*\(')
CALL = re.compile(r'(?<![\w.:])(?:[a-z_][\w]*\.)*([A-Z]\w*)\s*(?:\(|\{)|(?<=\.)([A-Z]\w*)\s*(?:\(|\{)')


def functions():
    """name -> list of (texts, called names) for every function in the app's sources."""
    found = defaultdict(list)
    for path in sorted(SOURCE.rglob('*.kt')):
        if path == TARGET:
            continue
        text = path.read_text()
        masked, literals = scan(text)
        for m in FUN.finditer(masked):
            name = m.group(1)
            close = match_close(masked, m.end() - 1, '(', ')')
            j = close + 1
            # The return type never holds '{' or a lone '='; the first of them starts the body.
            while j < len(masked) and masked[j] not in '{=':
                j += 1
            if j >= len(masked):
                continue
            if masked[j] == '{':
                end = match_close(masked, j, '{', '}')
            else:
                nxt = DECLARATION.search(masked, j)
                end = nxt.start() if nxt else len(masked)
            body = masked[j:end + 1]
            texts = []
            for t in re.finditer(r'\btr\(\s*"', masked[m.start():end + 1]):
                start = m.start() + t.end() - 1
                if start in literals:
                    texts.append(unescape(literals[start]))
            calls = {c.group(1) or c.group(2) for c in CALL.finditer(body)}
            found[name].append((texts, calls))
    return found


def page_texts():
    defined = functions()
    reach = {}
    for page, roots in PAGES.items():
        seen, texts, frontier = set(), [], [r for r in roots if r in defined]
        missing = [r for r in roots if r not in defined]
        assert not missing, f'{page}: root composable not found: {missing}'
        for _ in range(DEPTH + 1):
            nxt = []
            for name in frontier:
                if name in seen:
                    continue
                seen.add(name)
                for found_texts, calls in defined[name]:
                    texts += found_texts
                    nxt += [c for c in sorted(calls) if c in defined and c not in seen]
            frontier = nxt
        reach[page] = texts
    count = defaultdict(set)
    for page, texts in reach.items():
        for t in texts:
            count[t].add(page)
    result = {}
    for page, texts in reach.items():
        kept, seen = [], set()
        for t in texts:
            words = re.sub(r'%\d*\$?[sdf%]', ' ', t).strip()
            if t in seen or len(count[t]) > MAX_PAGES or len(re.sub(r'[^\w]', '', words)) < 3:
                continue
            seen.add(t); kept.append(t)
        result[page] = kept
    return result


def literal(s):
    return json.dumps(s, ensure_ascii=False).replace('$', '\\$')


texts = page_texts()
body = ',\n'.join(f'    SettingsPage.{page} to listOf(\n' + ',\n'.join(f'        {literal(t)}' for t in items) + ',\n    )'
                  for page, items in texts.items() if items)
content = f'''// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Generated by tools/generate_settings_search.py – do not edit; change the settings pages and run the tool.
package dev.ambivalenton.openv.ui.screens

/** Every visible text of each settings page (German source texts; the search also matches their translation). */
internal val settingsPageTexts: Map<SettingsPage, List<String>> = mapOf(
{body},
)
'''
if '--check' in sys.argv:
    assert TARGET.is_file() and TARGET.read_text() == content, 'Run tools/generate_settings_search.py'
else:
    TARGET.write_text(content)
print(f'{sum(len(v) for v in texts.values())} texts on {sum(1 for v in texts.values() if v)} settings pages')
