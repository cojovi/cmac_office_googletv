# CMAC Field Operations Command — Google TV / Android TV

Native Android TV rewrite of the `CMAC_Office_dash` browser dashboard. Same three
boards, same data, same HUD look — built to live on a wall unattended instead of
in a browser tab someone has to babysit.

<pre>
CMAC_Office_dash  (Node + Express)          cmac_office_googletv  (this repo)
├─ Bolt API + pagination                    ├─ native client of the same API
├─ Mapbox geocoding (token server-side)     ├─ Compose UI, fixed 1920x1080
├─ Slack WebSocket -> SSE relay             ├─ D-pad control, keep-screen-on
└─ /api/* + /api/events  ────────────────>  └─ boots itself after a power cut
</pre>

**The backend does not change.** This app is a drop-in replacement for the
browser, so the Bolt credentials, the geocoding and the Slack relay all stay on
the server exactly as they are today. Keep running `CMAC_Office_dash` — the TV
just points at it instead of Chrome doing so.

---

## 1. Configure

Create/edit `local.properties` in the repo root (gitignored, never committed):

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk

# Base URL of the running CMAC_Office_dash Express server
CMAC_SERVER_URL=http://192.168.1.50:3000

# Mapbox *public* (pk.) token — the same one already in the server's .env.
# Optional: leave blank and the map panel shows the same "MAP UNAVAILABLE"
# placeholder the web dashboard shows without a token.
CMAC_MAPBOX_TOKEN=pk.your_public_token
```

Any of these can also be passed per-build: `-PCMAC_SERVER_URL=...`.

Build with nothing configured and the app says so on screen rather than showing
an empty dashboard.

> The Mapbox token is a *public* token. The web app already ships it to every
> browser that loads the page (`window.MAPBOX_TOKEN`), so putting it in the APK
> is the same exposure, not new exposure. The Bolt credentials never leave the
> server.

## 2. Build

```bash
./gradlew assembleDebug          # dev build
./gradlew assembleRelease        # minified, ~1.7 MB
./gradlew testDebugUnitTest      # map projection / framing tests
```

Requires JDK 17+ (JDK 21 used here) and Android SDK 36. Gradle 9.5 and
AGP 9.3.1 — note AGP 9 has **built-in Kotlin support**, so there is deliberately
no `org.jetbrains.kotlin.android` plugin.

## 3. Install on a TV

Enable developer mode on the TV (Settings → System → About → click *Build* 7x),
then turn on **USB debugging** and **Network debugging**.

```bash
adb connect 192.168.1.222:5555        # the TV's IP
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.cmac.opscommand/.MainActivity
```

It registers under `LEANBACK_LAUNCHER`, so after the first install it appears on
the Android TV / Google TV home row and launches like any other app. It also
relaunches itself on `BOOT_COMPLETED`.

## 4. Remote control

The web dashboard's page dots and LOCK button were mouse targets, which is
useless on a TV. Everything is on the remote:

| Key | Action |
| --- | --- |
| **LEFT / RIGHT** | previous / next board |
| **OK / ENTER / PLAY-PAUSE** | hold or resume the 15 s auto-rotation (the old LOCK) |
| **UP / DOWN** | page the assignment roster by hand |
| **1 / 2 / 3** | jump straight to Overview / Assignments / Comms |

---

## Parity with the web dashboard

| Web behaviour | Here |
| --- | --- |
| 3 boards, 15 s rotation, LOCK to pause | same, on the remote |
| Fixed 1920x1080 layout | same, authored in a 1920x1080 design space and mapped to the panel by density, so it is pixel-faithful on 1080p and stays sharp on 4K |
| Stat cards with 1.4 s ease-out count-up | same |
| `#` / address / community / office / types / crew / status table | same, plus paging |
| Slack sidebar (8) and full comms feed (12) | same |
| Messages < 3 h highlighted green | same, plus an explicit `<3H` badge |
| SSE: live mentions + 5-minute count pushes | same |
| Mapbox dark-v11 map with job pins | same imagery via the Static Images API |
| Orbitron + Rajdhani, scanlines, corner brackets, grid | same, bundled (no webfont fetch) |
| YouTube mini-player | **not carried over** — see below |

## What is deliberately different

**Assignments are paged instead of clipped.** The web table rendered all 381
rows into an `overflow: hidden` box, so everything past roughly the first 19 was
unreachable on a TV. Here the roster pages itself every 5 s, keeps its place
between visits, and the header states which slice is showing (`VIEW 3/16`).

**Pins are coloured by work-order type.** The web legend advertised five colours
but `updateMapMarkers()` painted every marker the same red, so the legend never
matched the map. Now it does.

**The map is a static image, not a live GL context.** The web app started a full
Mapbox GL renderer and then disabled scroll, drag, rotate, pitch, keyboard and
double-click zoom — nobody touches a TV above a register. One cached PNG plus
pins drawn in a single canvas pass replaces a continuously-rendering WebGL
surface and ~380 animated DOM nodes. Trade-off: no extruded 3D buildings or
camera fly-in.

**The roster refreshes — on a deliberately slow clock.** The web app fetched jobs
once at page load, so a TV left up all day showed that morning's assignments
until someone reloaded the browser. Here counts and Slack mentions re-poll every
5 minutes (cheap: `/api/jobs/counts` only needs Bolt's `/schedules`), while the
job roster re-polls every **30 minutes** because it is drastically more expensive
— see *Bolt rate limits* below. Failures back off, and a reconnecting SSE stream
triggers an immediate refresh, so a server blip clears in seconds rather than
minutes.

**It survives being left alone.** Screen never sleeps, the last good payload is
cached to disk so a reboot paints real data before the network is up, and the
header distinguishes `DATA LIVE` / `DATA SYNCING` / `DATA STALE` from
`SLACK ONLINE` / `SLACK OFFLINE` so a glance tells you what is actually broken.

**No Material.** Every surface is custom-drawn to match the HUD design, so
`material3` and `tv-material` would be dead weight. Release APK is ~1.7 MB.

**YouTube mini-player omitted.** The web build hardcodes a playlist `<iframe>`.
Embedding YouTube in a WebView on Android TV is unreliable (autoplay gesture
requirements, embed restrictions) and is not something to hang a 24/7 display on.
If you want it, the honest options are a separate app on the TV or a licensed
player — say the word and it can be added behind a config flag.

---

## Known upstream issue: Bolt rate limits the roster build

`/api/jobs/today` is far more expensive than its URL suggests. On a cache miss
`buildJobsToday()` issues **one Bolt detail request and one Mapbox geocode per
job**, five at a time. Measured against production (359 jobs) that call ran past
**five minutes** and then failed outright with HTTP 429 — Bolt's rate limit —
after which every subsequent request kept 429ing for a long window.

This is pre-existing and not specific to this app: the web dashboard calls the
same endpoint on every page load and will hit the same wall on a cold cache.

Two consequences were designed around here:

- **Client timeout.** The browser's `fetch()` has no deadline, so the web app
  simply waited out the five minutes. A conventional 30–90 s HTTP timeout would
  abandon every cold start and park the board on "DATA STALE" while the server
  was working normally, so the roster call gets a 10-minute ceiling while the
  cheap endpoints keep a 60-second one.
- **Poll cadence.** The server caches for 5 minutes. Polling the roster on that
  same 5-minute beat would miss the cache *every time* and re-trigger the whole
  fan-out — thousands of Bolt calls an hour, keeping the account permanently
  rate-limited. Hence the split cadence: counts every 5 minutes, roster every 30.

Worth fixing **on the server**, in rough order of value:

1. **Cache job details.** Addresses and coordinates for a given job number do not
   change during a day. Persisting `fetchJobDetail` + geocode results (even to a
   JSON file) turns the daily cost from ~359 requests *per cache miss* into ~359
   *per day*, and makes a cold start near-instant.
2. **Raise the roster cache TTL** above 5 minutes so ordinary refreshes stop
   re-triggering the fan-out.
3. **Lower concurrency or add jitter** in `mapConcurrent(summaries, 5, ...)`; the
   current 429 retry ladder (1.5/3/4.5/6 s, 4 attempts) amplifies the burst
   rather than smoothing it.

## Known upstream issue: geocoding puts jobs in the wrong state

Not introduced here — it is in the server's geocoder, and it is why
`map_error.png` and `wrong_address.png` in the web repo show pins across Portland,
Miami and Toronto for a Dallas/Austin company. `wrong_address.png` shows the
popup for "729 Vineyard Way … Dallas" with its pin in **Florida**.

Cause, in `server.js` → `buildJobsToday()`:

```js
const city  = details.city || details.city_name || '';
const state = details.state || details.state_code || details.state_name || '';
const zip   = details.zip  || details.zip_code   || ...;
const geocodeQuery = [street, city, state, zip].filter(Boolean).join(', ');
```

When Bolt's `/open/v1/jobs/{n}` response does not carry those keys under the
guessed names, the query collapses to the street line alone and Mapbox returns
the first match anywhere in the US.

Two fixes worth applying **on the server**:

1. **Bias the geocoder to the service area.** Smallest, highest-value change —
   add a bounding box to the request in `geocodeAddress()`:
   ```js
   params: { access_token: MAPBOX_TOKEN, limit: 1, country: 'US',
             bbox: '-106.65,25.84,-93.51,36.50' }   // Texas
   ```
2. **Stop losing the city.** `office` *is* populated (`Dallas`, `Austin`,
   `Doors DFW`) — fall back to it, and log the real field names once to fix the
   guesses: the `console.log('[bolt job fields]', ...)` already in
   `fetchJobDetail()` prints them on the first request.

Until that is fixed, this app frames the map to the 3rd–97th percentile of pins
rather than their outright extremes, so a handful of strays cannot zoom the
camera out to the whole continent. **No pin is hidden** — every job is still
drawn, and the panel reports `N OF M PINS OUTSIDE FRAME` so the problem stays
visible instead of being quietly swallowed. Framing was chosen over discarding
suspicious jobs because the legitimate Houston and San Antonio clusters sit
~3.3° from Dallas, further than any fixed "plausible radius" that still catches
an out-of-state stray.

## Layout

```
app/src/main/java/com/cmac/opscommand/
├─ MainActivity.kt          fullscreen, keep-screen-on, D-pad mapping
├─ CmacApp.kt               Coil image loader + disk cache for map tiles
├─ BootReceiver.kt          relaunch after power cut
├─ data/
│  ├─ Models.kt             API wire models (lenient: defaults everywhere)
│  ├─ CmacApi.kt            OkHttp + kotlinx.serialization over /api/*
│  ├─ EventStream.kt        /api/events as a Flow (okhttp-sse)
│  ├─ DashboardRepository.kt polling, backoff, SSE, disk cache
│  ├─ Geo.kt                Mercator projection + robust bounds framing
│  └─ TimeUtil.kt           tolerant timestamp parsing (ISO / epoch / Slack ts)
└─ ui/
   ├─ DashboardScreen.kt    header, board host, page dots
   ├─ DashboardViewModel.kt rotation, hold, roster paging, clock
   ├─ Components.kt         FixedStage, corner-bracket cards, scanlines
   ├─ Widgets.kt            stat cards, badges, tags, Slack rows
   ├─ MapPanel.kt           static map + single-pass pin layer
   ├─ OverviewPage.kt / JobsPage.kt / CommsPage.kt
   └─ theme/Hud.kt          colour + type tokens ported from styles.css
```

`Geo.kt` is covered by unit tests (`./gradlew testDebugUnitTest`) because the pin
positions come from our own projection maths rather than a map SDK.
