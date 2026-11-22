# Licences & data sources

Öpen-V combines free software with public data. Find attribution, terms and complete software licence texts here, even offline. External sources open only after a tap.

## App code and design

Own app code: **GNU GPL version 3 or later (GPL-3.0-or-later)**. Copyright 2026 Ambivalenton, to the extent rights exist. Earlier Apache-2.0 grants remain valid. Third-party attribution is preserved; no exclusive rights are claimed in purely AI-generated material.

The project’s own vector/canvas designs, icon variants and texts use this licence. Operating systems, art periods and cities inspire some designs. This implies no manufacturer affiliation or inclusion of third-party system logos, original wallpapers or proprietary fonts.

> **The app licence is separate from server access.** The GPL does not automatically permit unrestricted use of external transit, map or weather services. Their terms and capacity limits also apply.

## Timetables, stops and place search

### Current services

Connections and departures come from [VRR OpenService](https://www.opendata-oepnv.de/ht/de/api), Verkehrsverbund Rhein-Ruhr, without warranty. API terms and the GPL app licence are separate.

### Nationwide stop search

Local search directory: **Central Stop Directory, © DELFI e.V., export 2 November 2026**. Additional names and identifier mappings: **VRR scheduled timetable data (GTFS), © Verkehrsverbund Rhein-Ruhr AöR**, feed version 20261013, valid 1 September 2026 to 28 February 2027.

Sources: [DELFI on OpenData ÖPNV](https://www.opendata-oepnv.de/ht/de/organisation/delfi/startseite) · [VRR datasets](https://www.opendata-oepnv.de/ht/de/organisation/verkehrsverbuende/vrr/openvrr/datensaetze).

Download metadata points to [Creative Commons Attribution (CC-BY)](https://opendefinition.org/licenses/cc-by/) without explicitly naming a numerical version. That original reference is preserved; its overview links to [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). An overview link is not treated as a retroactively specified licence version.

Modifications: selecting root-level stops, normalising and indexing search terms, and mapping VRR identifiers through documented parent/DHID fields. Entries with a last service day before the export date are omitted from suggestions; this alone does not prove permanent closure. Identifiers are not truncated to guess parents. The expired separate line CSV is not included.

The directory does not update automatically. It is **not an offline timetable or real-time access**. Online search runs alongside it. No endorsement or approval by the data providers is claimed.

### Additional place suggestions

[Photon by komoot](https://github.com/komoot/photon) provides place/address suggestions through an HTTP API using OpenStreetMap data. Photon server code is not embedded in the app. OSM data: © OpenStreetMap contributors, ODbL.

## Maps, walking guidance and weather

| Source | Use and conditions |
| --- | --- |
| OpenStreetMap | © OpenStreetMap contributors, ODbL. [Copyright and licence](https://www.openstreetmap.org/copyright). |
| Map tiles | Visible areas from the selected server, by default tile.openstreetmap.org. [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/). Bounded caches, no offline prefetch. |
| Valhalla | Detailed walking instructions over HTTP, by default valhalla1.openstreetmap.de using OSM data. [Project](https://valhalla.github.io/valhalla/) and [public demo / fair use](https://valhalla.github.io/valhalla/start/introduction/). No embedded server code. |
| Open-Meteo | Weather data under CC BY 4.0, locally rephrased into arrival hints. [Licence](https://open-meteo.com/en/licence), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) and [service terms](https://open-meteo.com/en/terms). The free hosted API is restricted to non-commercial use and limited request volume. |

External navigation opens your selected map service after a tap. Its terms apply independently. Map and weather data may be incomplete; attribution is not a guarantee of accuracy.

## Fonts, icons and editorial content

The 62 bundled, unchanged fonts from [google/fonts](https://github.com/google/fonts) use the **SIL Open Font License 1.1**. Open complete licence and attribution notices from each font selection using Show licence. Source revision and checksums are documented in localization/fonts.json and docs/FONTS.md. Font licences apply alongside the app’s GPL.

Material Icons and the included Material Color Utilities algorithms: Copyright Google LLC / The Android Open Source Project, **Apache-2.0**. Original headers are retained; package/import paths were adjusted. [Material Icons](https://github.com/google/material-design-icons) · [Material Color Utilities](https://github.com/material-foundation/material-color-utilities).

Facts are original short summaries. Sources and stable content IDs are recorded in the project’s source catalogue; available sources can also be opened from text history. No generative AI service creates text during use.

## Libraries in this revision

The following resolved runtime inventory names components and their licences. Unmodified MapLibre notices may describe optional SDK parts; this list identifies the components actually resolved for the app.

