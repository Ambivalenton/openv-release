#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Remove unused export fields; verify every runtime row, alias and FTS document unchanged."""
from pathlib import Path
import argparse, gzip, hashlib, json, sqlite3, tempfile

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def logical(db):
    result={}
    for table, sql in {
        'stops': 'SELECT rowid,dhid,name,city,lat,lon,name_key,city_key,retired FROM stops ORDER BY rowid',
        'aliases': 'SELECT alias,dhid FROM aliases ORDER BY alias',
        'search': 'SELECT docid,terms FROM stop_search ORDER BY docid',
    }.items():
        digest=hashlib.sha256();count=0
        for row in db.execute(sql):
            digest.update(json.dumps(row,ensure_ascii=False,separators=(',',':')).encode()+b'\n');count+=1
        result[table]={'rows':count,'sha256':digest.hexdigest()}
    return result

def slim(directory, report):
    asset=directory/'stops.catalog';manifest=directory/'catalog.json'
    info=json.loads(manifest.read_text());before_bytes=asset.stat().st_size
    with tempfile.TemporaryDirectory() as temporary:
        database=Path(temporary)/'stops.sqlite'
        database.write_bytes(gzip.decompress(asset.read_bytes()))
        assert sha(database)==info['database_sha256']
        with sqlite3.connect(database) as db:
            before=logical(db)
            columns={r[1] for r in db.execute('PRAGMA table_info(stops)')}
            removed=[c for c in ['last_service','replacement'] if c in columns]
            for column in removed:
                db.execute('ALTER TABLE stops DROP COLUMN '+column)
            db.commit();db.execute('VACUUM')
            assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
            assert db.execute('PRAGMA application_id').fetchone()[0]==info['application_id']
            assert db.execute('PRAGMA user_version').fetchone()[0]==info['schema_version']
            after=logical(db)
            assert before==after,'Runtime data or FTS row mapping changed'
        database_bytes=database.stat().st_size
        data_hash=sha(database)
        with asset.open('wb') as raw, gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0,compresslevel=9) as target:
            with database.open('rb') as source:
                while chunk:=source.read(1024*1024):target.write(chunk)
    info.update(database_sha256=data_hash,database_bytes=database_bytes,asset_sha256=sha(asset),asset_bytes=asset.stat().st_size)
    note=' Unused last_service and replacement export columns removed; retired flags and all runtime search data retained.'
    if removed:info['modifications']+=note
    manifest.write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
    evidence={'removed_columns':removed,'runtime_logical_data':after,'runtime_data_identical':before==after,
        'asset_before_bytes':before_bytes,'asset_after_bytes':asset.stat().st_size,
        'asset_saved_bytes':before_bytes-asset.stat().st_size,'database_after_bytes':database_bytes,
        'asset_sha256':info['asset_sha256'],'database_sha256':data_hash}
    report.parent.mkdir(parents=True,exist_ok=True);report.write_text(json.dumps(evidence,indent=2)+'\n')
    print(json.dumps(evidence,indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True);parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args();slim(args.directory,args.report)
