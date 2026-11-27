#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the packaged data and measure local SQL only, with no claims about phone/API latency."""
from pathlib import Path
import gzip, hashlib, json, shutil, sqlite3, statistics, tempfile, time
from build_stop_catalog import normalized
ROOT=Path(__file__).resolve().parents[1]
assets=ROOT/'app/src/main/assets/stops'
info=json.loads((assets/'catalog.json').read_text())
assert hashlib.sha256((assets/'stops.catalog').read_bytes()).hexdigest()==info['asset_sha256']
with tempfile.TemporaryDirectory() as directory:
    path=Path(directory)/'stops.sqlite'
    started=time.perf_counter()
    with gzip.open(assets/'stops.catalog','rb') as source,path.open('wb') as target:shutil.copyfileobj(source,target)
    extract_ms=(time.perf_counter()-started)*1000
    assert path.stat().st_size==info['database_bytes']
    assert hashlib.sha256(path.read_bytes()).hexdigest()==info['database_sha256']
    db=sqlite3.connect(f'file:{path}?mode=ro',uri=True)
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    assert db.execute('PRAGMA application_id').fetchone()[0]==info['application_id']
    assert db.execute('PRAGMA user_version').fetchone()[0]==1
    assert db.execute('SELECT count(*) FROM stops').fetchone()[0]==info['counts']['zhv_S']
    assert db.execute('SELECT count(*) FROM stop_search').fetchone()[0]==info['counts']['searchable_roots']
    assert db.execute('SELECT count(*) FROM aliases').fetchone()[0]==info['counts']['gtfs_stop_aliases']
    assert db.execute('SELECT count(*) FROM aliases a LEFT JOIN stops s ON a.dhid=s.dhid WHERE s.dhid IS NULL').fetchone()[0]==0
    assert db.execute('SELECT count(*) FROM stop_search f JOIN stops s ON s.rowid=f.docid WHERE s.retired!=0').fetchone()[0]==0
    assert info['counts']['line_current_at_export']==0
    assert info['counts']['gtfs_shape_points']==0
    query='''SELECT s.dhid,s.name,s.city,s.lat,s.lon FROM stop_search JOIN stops s ON s.rowid=stop_search.docid
        WHERE stop_search MATCH ? AND s.retired=0 ORDER BY CASE WHEN s.name_key=? THEN 0
        WHEN s.name_key LIKE ? THEN 1 ELSE 2 END,CASE WHEN s.city_key=? THEN 0 ELSE 1 END,
        length(s.name),s.dhid LIMIT 64'''
    inputs=['Bochum Rathaus','Berlin Alexanderplatz','Hamburg Hauptbahnhof','München Marienplatz',
        'Dortmund Hbf','Köln Dom','Dresden Postplatz','Boch','Berlin','Haupt','Am','zzzzzzzzz']
    samples=[];hits={}
    for cycle in range(21):
        for text in inputs:
            key=normalized(text);match=' '.join(w+'*' for w in key.split())
            started=time.perf_counter();rows=db.execute(query,(match,key,key+'%',key)).fetchall()
            elapsed=(time.perf_counter()-started)*1000
            if cycle:samples.append(elapsed)
            else:hits[text]={'count':len(rows),'first_names':[r[1] for r in rows[:3]],'first_query_ms':round(elapsed,3)}
            if text not in ['zzzzzzzzz']:assert rows,text
    samples.sort()
    result={'result':'PASS','database_sha256':info['database_sha256'],'asset_bytes':info['asset_bytes'],
        'database_bytes':info['database_bytes'],'executor_extract_ms':round(extract_ms,2),
        'executor_sql_warm_samples':len(samples),'executor_sql_p50_ms':round(statistics.median(samples),3),
        'executor_sql_p95_ms':round(samples[int(len(samples)*.95)-1],3),'executor_sql_max_ms':round(max(samples),3),
        'scope':'Linux executor SQLite; warm SQL, not Android UI or network; no old/new phone comparison',
        'queries':hits}
    audit = ROOT / 'audit/build51'
    audit.mkdir(parents=True, exist_ok=True)
    (audit/'catalog-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))
