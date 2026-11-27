# Privacy policy for Öpen-V

Updated: 22 November 2026.

**At a glance:** no account, advertising or analytics SDKs. Favourites and settings remain on your device. Functional services receive necessary request information and technical connection data.

## 1 · Controller and scope

**Erik Tomczyk**, developer name **Ambivalenton**, is responsible for this app. Contact: [oepenvapp@gmail.com](mailto:oepenvapp@gmail.com).

This privacy policy applies to the Öpen-V Android app (dev.ambivalenton.openv). Public policy: [ambivalenton.github.io/openv-release/privacy](https://ambivalenton.github.io/openv-release/privacy/). The developer does not operate an app analytics or location-collection server.

## 2 · Local data

Favourites with locations, styles and time rules; network plan; settings and personal themes; onboarding and tutorial progress; up to 50 grouped search routes; saved journey plans; board stops and latest departures; text history; and the latest permitted position with time and accuracy remain in private app storage until deletion, replacement or expiry. There is no continuous raw GPS history. The bundled DELFI/VRR stop catalogue is searched locally without sending your input to DELFI. Separate online suggestions are independent of this lookup. Android cloud backup and automatic device transfer are disabled by the app.

## 3 · Location and journey guidance

Location is optional and uses Android LocationManager, without Google Play Services or a Google account. GPS, network or combined positioning may be involved depending on your device. A manual origin and fixed stops work without location permission.

Pinning a journey starts a visible foreground service with a notification. With permission, location updates are generally requested every two to ten seconds depending on the situation. Transit data is normally checked approximately once per minute, less often in power-saving mode or after failures. Power-saving mode replaces continuous location registration with occasional short checks. Android can delay or stop execution.

Available acceleration sensors supplement local walking patterns. No health/fitness database or activity-recognition permission is used. Raw sensor readings and short pulse histories stay in memory and are discarded when guidance ends. Steps alone do not prove that you left a vehicle. Necessary origin, destination, intermediate stops and coordinates can be sent to VRR for refreshes or reachable alternatives. Local observations may change estimates and recommendations.

Minimising does not end guidance. Removing the pin, selecting End journey in its notification, or a plausible destination arrival ends guidance, location/sensor registration and its transit requests. Notifications are generated locally without a push server; Android 13 and later require separate notification permission.

Installed departure widgets may refresh while the app is closed, roughly every fifteen minutes and only while the screen is on; unlocking refreshes an older stand. With the screen off, Öpen-V makes no widget requests. Öpen-V requests no additional background-location permission for widgets. A fixed stop or last permitted position is used. A new position can be obtained while the app or its explicit refresh screen is visible.

The optional floating bubble needs permission to display over other apps. It shows the existing journey without a second location/network loop. Its side, height and journey-specific dismissal are stored locally. It is hidden on the lock screen. Closing it does not end guidance.

## 4 · External recipients

Direct requests expose the IP address, time, requested resource and technical headers to the recipient. Öpen-V has no proxy hiding your IP. Configuring your own address, tile or walking service changes the corresponding recipient.

| Function | Recipient and information |
| --- | --- |
| Transit | [VRR OpenService](https://www.opendata-oepnv.de/ht/de/api) receives search terms, stop IDs, origin/destination or coordinates, intermediate stops, requested times and transport/routing options. Once a destination is chosen in the search, the app already requests the next and the previous connection in the background, so results appear at once when you tap Search (not in power saving mode). Ordinary overview queries are tied to visible use; explicitly pinned journeys and departure widgets may continue as described above. [VRR privacy](https://www.vrr.de/de/datenschutz/). |
| Address suggestions | [Photon](https://photon.komoot.io/) receives typed text after input pauses and may receive a location bias. Limited spelling/house-number retries or an EFA address fallback are possible. [Project](https://github.com/komoot/photon), [komoot privacy](https://www.komoot.com/privacy). |
| Maps | Visible maps load the required tiles, by default from [OpenStreetMap](https://tile.openstreetmap.org/). Tile coordinates reveal the viewed area. This includes the destination map of the search once a destination is chosen (not in power saving mode). No bulk offline download is provided. Hidden walking maps pause requests. [OSMF privacy](https://osmfoundation.org/wiki/Privacy_Policy), [tile policy](https://operations.osmfoundation.org/policies/tiles/). |
| Detailed walking routes | Opening a pinned timeline can send the two walking endpoints, speed and language to [Valhalla](https://valhalla1.openstreetmap.de/). Favourite labels and raw movement histories are not sent. This is separate from opening an external map app. Disable detailed directions in walking/navigation settings to stop new requests. [Service](https://valhalla.github.io/valhalla/start/introduction/). |
| Optional arrival weather | Initially off. When enabled, [Open-Meteo](https://api.open-meteo.com/) receives destination coordinates rounded to three decimal places, not favourite labels or search history. Rounding is not anonymisation. The provider describes possible technical logs containing coordinates, deleted after 90 days. [Terms and privacy](https://open-meteo.com/en/terms). |

## 5 · Sessions and caches

The active journey is stored privately with sections, times, confirmed progress, dismissed alternatives and shown notices. Up to 24 observed stop/departure events can inform local predictions. At most 16 recent telemetry readings from two minutes are held only in RAM. Up to eight closed/replaced journeys with the last reliable position can remain recoverable for six hours while usable; expired entries are removed on the next load/save. Clearing search history also clears recoverable journeys and the connection cache.

| Cache | Limit |
| --- | --- |
| Connection views | Eight views, 30 minutes and 8 MiB in private cache; clearing search history removes these. |
| Repeated direct-journey searches | Up to 64 local entries with a hashed search key, hashed timetable pattern, boarding-stop ID, count and last check. Inactive after 48 hours, pruned on the next write after 14 days, and removed when the connection cache is cleared. |
| HTTP responses | 20 MiB, following server caching rules. Android can remove the cache. |
| Walking-map tiles | 32 MiB on disk and 16 MiB in RAM; server expiry, otherwise seven days. MapLibre has an additional map cache. |
| Detailed walking routes | 32 entries for up to 24 hours in RAM; requests use HTTP no-store. |
| Weather | 48 destinations for 30 minutes in RAM; short error pause. |

Short-lived transit/search data and shared in-flight requests also remain in RAM. Caching is not permanent archival or a guarantee of current predictions.

## 6 · Backups, compact view and external navigation

Export/import opens Android's file picker after your action. An unencrypted JSON backup contains settings, themes, favourites with locations, network plan, search history and text history. Saved journeys contribute only user search intentions to history. Full transit responses, live forecasts, GPS and departure caches are excluded. Older backups remain readable; their full saved transit responses are discarded while search intentions are retained. Backups can reveal personal places. Selecting cloud storage subjects the file to that provider's terms. Import validates before replacement; delete exported copies separately.

External navigation opens OpenStreetMap or your chosen map app after tapping. Depending on the mode, destination and an already known origin can be passed to OpenStreetMap with a routing service, or the destination to Google Maps. No extra location is requested for this handoff. The recipient's terms apply.

**Compact view** shows a connection or the live journey as a full-screen card with data source and time. Öpen-V creates no image file and passes nothing to other apps. A screenshot only exists if you take one yourself with Android; your device settings apply to it. The live view can show your current position, delays, arrival forecasts and times.

## 7 · Legal bases and international processing

Article 6(1)(b) GDPR applies to expressly requested core functions and necessary storage insofar as an app-use contract exists. Optional location/weather functions rely on informed, revocable consent under Article 6(1)(a). An Android permission alone does not replace appropriate explanation. Technically necessary device access is assessed under section 25(2) TDDDG; optional access may require consent.

External operators act under their own notices. Processing outside the EU/EEA is possible, particularly with user-configured services. Their notices describe countries and safeguards. Exclusive EU processing or an unverified processor relationship is not promised. Local deletion cannot enforce deletion on their servers.

## 8 · Rights and deletion

Subject to legal conditions, you have rights to access, correction, deletion, restriction, portability, withdrawal of consent and objection where legitimate interests apply. You may complain to a supervisory authority, including [LDI NRW](https://www.ldi.nrw.de/).

- Remove favourites and board stops individually; clear search history in data settings.
- Revoke location, notification or overlay permissions in Android.
- Clear the app's storage or uninstall it to delete private stored data and caches.
- Delete your exported copies separately. Contact external operators about their technical logs.

There is no user account and no account-deletion flow. Manual searches require neither location nor weather permission. Guidance is non-binding and makes no automated decision with legal or comparable significant effects under Article 22 GDPR.

## 9 · Security and updates

HTTPS is used and cleartext traffic is disallowed. Editorial text, help, illustrations and translations work locally, without a generative AI service. No software guarantees absolute security. This policy changes when functions or recipients change.

## 10 · This website

GitHub, Inc. hosts this page through GitHub Pages and receives technical connection data such as IP, time and requested resource. Öpen-V embeds no analytics scripts, advertisements, external fonts or its own tracking cookies. Hosting fulfils information duties and secure operation under Article 6(1)(c) and (f) GDPR. [GitHub General Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
