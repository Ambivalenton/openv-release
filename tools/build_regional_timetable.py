#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the VRR GTFS snapshot into a read-only index of direct scheduled rides.

Calls with identical stop, timing and boarding patterns share one pattern. The
service dates are encoded separately so a timetable exception costs no trip copy.
"""
import argparse
import csv
import datetime as dt
import gzip
import hashlib
import io
import itertools
import json
import shutil
import sqlite3
import tempfile
import zipfile
from pathlib import Path

DAYS = "monday tuesday wednesday thursday friday saturday sunday".split()
TYPES = {"0": "TRAM", "1": "SUBWAY", "3": "BUS", "106": "REGIONAL", "109": "SUBURBAN",
         "405": "OTHER", "4": "FERRY"}
DATE = lambda s: dt.datetime.strptime(s, "%Y%m%d").date()


def rows(archive, name):
    with archive.open(name + ".txt") as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig"))


def seconds(value):
    h, m, s = map(int, value.split(":"))
    return h * 3600 + m * 60 + s


def compile_feed(source, output):
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source) as archive, tempfile.TemporaryDirectory() as tmp:
        # This compiler targets the VRR fixed-time feed.
        if "frequencies.txt" in archive.namelist():
            raise ValueError("Frequency-based trips require a different timetable representation")
        feed, = rows(archive, "feed_info")
        first, last = map(DATE, (feed["feed_start_date"], feed["feed_end_date"]))
        dates = [first + dt.timedelta(days=i) for i in range((last - first).days + 1)]
        active = {}
        for row in rows(archive, "calendar"):
            start, end = map(DATE, (row["start_date"], row["end_date"]))
            active[row["service_id"]] = bytearray(start <= d <= end and row[DAYS[d.weekday()]] == "1" for d in dates)
        for row in rows(archive, "calendar_dates"):
            i = (DATE(row["date"]) - first).days
            if 0 <= i < len(dates):
                active.setdefault(row["service_id"], bytearray(len(dates)))[i] = row["exception_type"] == "1"
        routes = {r["route_id"]: (r["route_short_name"], TYPES.get(r["route_type"]))
                  for r in rows(archive, "routes")}
        trips = {r["trip_id"]: (r["route_id"], r["service_id"], r["trip_headsign"])
                 for r in rows(archive, "trips")}
        stops = {r["stop_id"]: r for r in rows(archive, "stops")}
        dbfile = Path(tmp) / "schedule.db"
        db = sqlite3.connect(dbfile)
        db.executescript("""
            CREATE TABLE stop(id INTEGER PRIMARY KEY, dhid TEXT, name TEXT, platform TEXT);
            CREATE INDEX station ON stop(dhid);
            CREATE TABLE pattern(id INTEGER PRIMARY KEY, line TEXT, product TEXT, direction TEXT);
            CREATE TABLE call(pattern INTEGER, seq INTEGER, stop INTEGER, arr INTEGER, dep INTEGER,
                              pickup INTEGER, dropoff INTEGER, PRIMARY KEY(pattern,seq)) WITHOUT ROWID;
            CREATE TABLE run(id INTEGER PRIMARY KEY, pattern INTEGER, start INTEGER, service INTEGER);
            CREATE TABLE service(id INTEGER PRIMARY KEY, days TEXT);
            CREATE TABLE feed(first TEXT,last TEXT,version TEXT,max_seconds INTEGER);
        """)
        stop_ids = {}
        for i, (key, r) in enumerate(stops.items(), 1):
            dhid = r.get("NVBW_HST_DHID") or r.get("parent_station") or key
            # Explicit German station IDs have three components, platform IDs extend them.
            root = ":".join(dhid.split(":")[:3])
            stop_ids[key] = i
            db.execute("INSERT INTO stop VALUES(?,?,?,?)", (i, root, r["stop_name"], r["platform_code"]))
        services = {}
        for i, (key, days) in enumerate(active.items(), 1):
            services[key] = i
            db.execute("INSERT INTO service VALUES(?,?)", (i, "".join(map(str, days))))
        patterns, seen = {}, set()
        max_seconds = 0
        for key, group in itertools.groupby(rows(archive, "stop_times"), key=lambda r: r["trip_id"]):
            if key in seen:
                raise ValueError("stop_times.txt must be grouped by trip_id")
            seen.add(key)
            route, service, head = trips[key]
            line, product = routes[route]
            if not line or not product or service not in services:
                continue
            calls = sorted(group, key=lambda r: int(r["stop_sequence"]))
            if len(calls) < 2 or any(not r["arrival_time"] or not r["departure_time"] for r in calls):
                continue
            start = seconds(calls[0]["arrival_time"])
            sequence = tuple((stop_ids[r["stop_id"]], seconds(r["arrival_time"])-start,
                              seconds(r["departure_time"])-start, int(r["pickup_type"] or 0)==0,
                              int(r["drop_off_type"] or 0)==0) for r in calls)
            direction = head or stops[calls[-1]["stop_id"]]["stop_name"]
            signature = (line, product, direction, sequence)
            pattern = patterns.get(signature)
            if pattern is None:
                pattern = len(patterns) + 1
                patterns[signature] = pattern
                db.execute("INSERT INTO pattern VALUES(?,?,?,?)", (pattern, line, product, direction))
                db.executemany("INSERT INTO call VALUES(?,?,?,?,?,?,?)",
                               ((pattern, i, *call) for i, call in enumerate(sequence)))
            db.execute("INSERT INTO run(pattern,start,service) VALUES(?,?,?)", (pattern, start, services[service]))
            max_seconds = max(max_seconds, start + max(c[2] for c in sequence))
        db.execute("INSERT INTO feed VALUES(?,?,?,?)", (first.isoformat(), last.isoformat(), feed["feed_version"], max_seconds))
        db.executescript("CREATE INDEX boarding ON call(stop,pattern); CREATE INDEX operating ON run(pattern,start); ANALYZE;")
        db.commit()
        db.close()
        asset = output / "schedule.catalog"
        with asset.open("wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz, dbfile.open("rb") as stream:
            shutil.copyfileobj(stream, gz)
        info = dict(feed=feed, source_sha256=hashlib.file_digest(source.open("rb"), "sha256").hexdigest(),
                    database_sha256=hashlib.file_digest(dbfile.open("rb"), "sha256").hexdigest(),
                    database_bytes=dbfile.stat().st_size, patterns=len(patterns))
        (output / "schedule.json").write_text(json.dumps(info, indent=2) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    compile_feed(args.source, args.output)
