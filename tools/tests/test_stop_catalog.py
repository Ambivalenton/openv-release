# SPDX-License-Identifier: GPL-3.0-or-later
"""Small import regression: explicit foreign-looking child IDs, history and invalid coordinates."""
from pathlib import Path
import csv, gzip, io, sqlite3, sys, tempfile, unittest, zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_stop_catalog import build

class StopCatalogImportTest(unittest.TestCase):
    def test_explicit_hierarchy_wins_over_id_syntax_and_history_is_not_a_modification_date(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);zhv=root/'zHV.zip';gtfs=root/'gtfs.zip';lines=root/'line.csv'
            headers=['SeqNo','Type','DHID','Parent','Name','Latitude','Longitude','Municipality','LastOperationDate','SEV']
            data=[
                ['1','S','de:test:1','de:test:1','Markt','51,4','7,2','Teststadt','2026-12-12T00:00:00','false'],
                ['2','S','de:test:2','de:test:2','Anderer Markt','51,5','7,3','Teststadt','2026-12-12T00:00:00','false'],
                ['3','S','de:test:old','de:test:old','Alt','51,4','7,2','Teststadt','2026-01-01T00:00:00','false'],
                ['4','S','de:test:bad','de:test:bad','Fehler','NaN','7,2','Teststadt','','false'],
                # Its syntax resembles root 2, but it explicitly belongs to root 1.
                ['5','Q','de:test:2::1','de:test:1','Steig 1','51,4','7,2','-','2026-12-12T00:00:00','false'],
            ]
            text=io.StringIO(newline='');writer=csv.writer(text,delimiter=';');writer.writerow(headers);writer.writerows(data)
            with zipfile.ZipFile(zhv,'w') as archive:archive.writestr('zHV_aktuell_csv.2026-11-02.csv',text.getvalue().encode('utf-8-sig'))
            with zipfile.ZipFile(gtfs,'w') as archive:
                archive.writestr('feed_info.txt','feed_publisher_name,feed_version\nVRR,20261013\n')
                archive.writestr('stops.txt','stop_id,stop_name,parent_station,NVBW_HST_DHID\nde:test:2::1,Alter Markt,,\n')
                archive.writestr('routes.txt','route_id\nr1\n')
                archive.writestr('shapes.txt','shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n')
            lines.write_text('VALID_FROM;VALID_TO\n20250101;20250904\n',encoding='cp1252')
            info=build(zhv,gtfs,lines,root/'output')
            self.assertEqual(2,info['counts']['searchable_roots']);self.assertEqual(1,info['counts']['historical_roots'])
            self.assertEqual(1,info['counts']['invalid_roots']);self.assertEqual(0,info['counts']['line_current_at_export'])
            unpacked=root/'test.sqlite'
            unpacked.write_bytes(gzip.decompress((root/'output/stops.catalog').read_bytes()))
            with sqlite3.connect(unpacked) as db:
                self.assertEqual(('de:test:1',),db.execute('SELECT dhid FROM aliases WHERE alias=?',('de:test:2::1',)).fetchone())
                self.assertEqual([('de:test:1',)],db.execute('SELECT s.dhid FROM stop_search f JOIN stops s ON s.rowid=f.docid WHERE stop_search MATCH ?',('alter*',)).fetchall())
                self.assertEqual((0,),db.execute('SELECT retired FROM stops WHERE dhid=?',('de:test:1',)).fetchone())
                self.assertEqual((1,),db.execute('SELECT retired FROM stops WHERE dhid=?',('de:test:old',)).fetchone())

if __name__=='__main__':unittest.main()
