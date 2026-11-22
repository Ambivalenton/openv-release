#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build the licensed, read-only Android FTS4 stop catalogue from local source downloads.

No network, credentials or timetable routing. Re-run with fresh downloads before publication.
"""
from pathlib import Path
import argparse, collections, csv, datetime as dt, gzip, hashlib, io, json, re, sqlite3, unicodedata, zipfile

ROOT = Path(__file__).resolve().parents[1]
APPLICATION_ID = 0x4F565354

def normalized(value):
    value = ''.join(c for c in unicodedata.normalize('NFKD', value) if not unicodedata.combining(c)).lower().replace('ß', 'ss')
    return ' '.join(''.join(c if c.isalnum() else ' ' for c in value).split())

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def rows(archive, name, delimiter=','):
    with archive.open(name) as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, encoding='utf-8-sig', newline=''), delimiter=delimiter)

def build(zhv_path, gtfs_path, line_path, output):
    output.mkdir(parents=True, exist_ok=True)
    db_path = output / 'stops.sqlite'
    db_path.unlink(missing_ok=True)
    db = sqlite3.connect(db_path)
    db.executescript(f'''
        PRAGMA page_size=4096;
        PRAGMA journal_mode=DELETE;
        PRAGMA application_id={APPLICATION_ID};
        PRAGMA user_version=1;
        CREATE TABLE stops(dhid TEXT PRIMARY KEY, name TEXT NOT NULL, city TEXT NOT NULL,
            lat REAL NOT NULL, lon REAL NOT NULL, name_key TEXT NOT NULL, city_key TEXT NOT NULL,
            retired INTEGER NOT NULL);
        CREATE TABLE aliases(alias TEXT PRIMARY KEY, dhid TEXT NOT NULL);
        CREATE VIRTUAL TABLE stop_search USING fts4(terms, tokenize=unicode61, prefix='2,3,4');
    ''')
    counts = collections.Counter()
    hierarchy = {}
    root_ids = set()
    with zipfile.ZipFile(zhv_path) as archive:
        filename = next(n for n in archive.namelist() if n.lower().endswith('.csv'))
        match = re.search(r'(\d{4}-\d{2}-\d{2})', filename)
        if not match:
            raise ValueError('zHV export date missing from CSV filename')
        export_date = dt.date.fromisoformat(match[1])
        for row in rows(archive, filename, ';'):
            counts['zhv_' + row['Type']] += 1
            dhid, parent = row['DHID'].strip(), row['Parent'].strip()
            # Explicit Parent is authoritative. Never truncate an ID to guess its hierarchy.
            hierarchy[dhid] = parent
            if row['Type'] != 'S':
                continue
            name, city = row['Name'].strip(), row['Municipality'].strip()
            try:
                lat, lon = (float(row[k].replace(',', '.')) for k in ('Latitude', 'Longitude'))
                assert -90 <= lat <= 90 and -180 <= lon <= 180 and name and city not in ('', '-')
            except (ValueError, AssertionError):
                counts['invalid_roots'] += 1
                continue
            last = row['LastOperationDate'][:10]
            # LastOperationDate means last service day, NOT a modification timestamp.
            retired = bool(last and dt.date.fromisoformat(last) < export_date)
            counts['historical_roots' if retired else 'searchable_roots'] += 1
            root_ids.add(dhid)
            db.execute('INSERT INTO stops VALUES(?,?,?,?,?,?,?,?)',
                (dhid, name, city, lat, lon, normalized(name), normalized(city), int(retired)))
    def root_of(value):
        visited = set()
        while value and value not in visited:
            if value in root_ids:
                return value
            visited.add(value)
            value = hierarchy.get(value)
        return None
    gtfs_names = collections.defaultdict(set)
    with zipfile.ZipFile(gtfs_path) as archive:
        feed = list(rows(archive, 'feed_info.txt'))[0]
        stops = {r['stop_id']: r for r in rows(archive, 'stops.txt')}
        aliases = {}
        for identifier, row in stops.items():
            resolved = root_of(identifier)
            if not resolved:
                resolved = root_of(row.get('NVBW_HST_DHID', ''))
            parent, seen = row.get('parent_station'), set()
            while not resolved and parent and parent not in seen:
                seen.add(parent)
                resolved = root_of(parent)
                parent_row = stops.get(parent, {})
                if not resolved:
                    resolved = root_of(parent_row.get('NVBW_HST_DHID', ''))
                parent = parent_row.get('parent_station')
            if resolved:
                aliases[identifier] = resolved
                gtfs_names[resolved].add(normalized(row['stop_name']))
            else:
                counts['unmapped_gtfs_stops'] += 1
        db.executemany('INSERT INTO aliases VALUES(?,?)', sorted(aliases.items()))
        counts['gtfs_stop_aliases'] = len(aliases)
        counts['gtfs_stops'] = len(stops)
        counts['gtfs_routes'] = sum(1 for _ in rows(archive, 'routes.txt'))
        counts['gtfs_shape_points'] = sum(1 for _ in rows(archive, 'shapes.txt'))
    for rowid, dhid, name, city in db.execute('SELECT rowid,dhid,name_key,city_key FROM stops WHERE retired=0').fetchall():
        terms = ' '.join(dict.fromkeys((name + ' ' + city + ' ' + ' '.join(sorted(gtfs_names[dhid]))).split()))
        db.execute('INSERT INTO stop_search(docid,terms) VALUES(?,?)', (rowid, terms))
    db.execute("INSERT INTO stop_search(stop_search) VALUES('optimize')")
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert db.execute('SELECT count(*) FROM stop_search').fetchone()[0] == counts['searchable_roots']
    db.execute('VACUUM')
    db.close()
    # A .gz asset is transparently expanded/renamed by Android's asset merger.
    # Preserve compressed bytes under a neutral extension for the runtime GZIPInputStream.
    gz_path = output / 'stops.catalog'
    with db_path.open('rb') as src, gz_path.open('wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0, compresslevel=9) as target:
        while chunk := src.read(1024 * 1024):
            target.write(chunk)
    line_rows = []
    with line_path.open(encoding='cp1252', newline='') as stream:
        line_rows = list(csv.DictReader(stream, delimiter=';'))
    today = export_date.strftime('%Y%m%d')
    counts['line_rows'] = len(line_rows)
    counts['line_current_at_export'] = sum(r['VALID_FROM'] <= today <= r['VALID_TO'] for r in line_rows)
    info = {
        'schema_version': 1, 'application_id': APPLICATION_ID, 'export_date': export_date.isoformat(),
        'asset_file': 'stops.catalog',
        'database_sha256': digest(db_path), 'database_bytes': db_path.stat().st_size,
        'asset_sha256': digest(gz_path), 'asset_bytes': gz_path.stat().st_size,
        'counts': dict(counts), 'gtfs_feed': feed,
        'line_valid_from': sorted({r['VALID_FROM'] for r in line_rows}),
        'line_valid_to': sorted({r['VALID_TO'] for r in line_rows}),
        'sources': [
            {'file': zhv_path.name, 'sha256': digest(zhv_path), 'publisher': 'DELFI e.V.',
             'license': 'CC-BY (as linked by publisher)', 'license_url': 'https://opendefinition.org/licenses/cc-by/',
             'url': 'https://www.opendata-oepnv.de/ht/de/organisation/delfi/startseite'},
            {'file': gtfs_path.name, 'sha256': digest(gtfs_path), 'publisher': 'Verkehrsverbund Rhein-Ruhr AöR',
             'license': 'CC-BY (as linked by publisher)', 'license_url': 'https://opendefinition.org/licenses/cc-by/',
             'url': 'https://www.opendata-oepnv.de/ht/de/organisation/verkehrsverbuende/vrr/openvrr/datensaetze'},
            {'file': line_path.name, 'sha256': digest(line_path), 'use': 'audited only; expired, not imported'},
        ],
        'modifications': 'Root stops indexed and historical entries excluded from search at export date; names normalized for search; VRR stop aliases joined through explicit zHV Parent, GTFS parent_station and DHID extension. No timetable or real-time data imported.',
        'limits': ['Not a national journey planner', 'No real-time information', 'Snapshot, no automatic download',
                   'LastOperationDate is last service day, not last update', 'GTFS shapes file has no points'],
    }
    (output / 'catalog.json').write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    db_path.unlink()
    return info

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--zhv', type=Path, required=True)
    p.add_argument('--gtfs', type=Path, required=True)
    p.add_argument('--lines', type=Path, required=True)
    p.add_argument('--output', type=Path, default=ROOT/'app/src/main/assets/stops')
    args = p.parse_args()
    print(json.dumps(build(args.zhv, args.gtfs, args.lines, args.output), ensure_ascii=False, indent=2))
