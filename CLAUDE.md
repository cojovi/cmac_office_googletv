# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

Native Android TV / Google TV client for the `CMAC_Office_dash` dashboard
(a sibling repo: `../CMAC_Office_dash`). It replaces the *browser*, not the
backend — the Express server keeps doing Bolt pagination, Mapbox geocoding and
the Slack WebSocket→SSE relay. If a data question comes up, the answer is almost
always "the server already does that; consume it."

Read `README.md` first — it covers configuration, the remote-control map, parity
with the web app, and the known upstream geocoding bug.

## Toolchain

- Gradle **9.5.0**, AGP **9.3.1**, Kotlin **2.4.0**, JDK **17+** (21 in use), SDK 36.
- AGP 9 has **built-in Kotlin support**: do *not* add
  `org.jetbrains.kotlin.android`, it hard-errors. `plugin.compose` and
  `plugin.serialization` still apply normally.
- `minSdk = 26` (variable fonts; every Google TV device in the field is above it).
- Build config comes from `local.properties` / `-P` flags: `CMAC_SERVER_URL`,
  `CMAC_MAPBOX_TOKEN`. Never hardcode either into source.

## Conventions that matter here

**The layout is authored in a fixed 1920x1080 design space.** `FixedStage`
(`ui/Components.kt`) overrides `LocalDensity` so `1.dp == 1 design px` and forces
`fontScale = 1f`. That means **every dp/sp number in the UI is the same number as
the original CSS** — when porting a style from `../CMAC_Office_dash/public/styles.css`,
copy the pixel value verbatim. Do not "convert" it, and do not scale with
`graphicsLayer` (it rasterises and softens text).

**No Material.** `material3`/`tv-material` are intentionally absent; every
surface is custom-drawn to match the HUD. Use `HudText` (wraps `BasicText`) and
the `hud()` / `data()` text-style builders in `ui/theme/Hud.kt`. Adding Material
for one convenience component is a regression — the release APK is ~1.7 MB.

**Draw many things in one pass.** The map renders ~380 pins in a single `Canvas`
with one shared animation clock, not 380 composables. Keep it that way; this runs
on TV-class hardware.

**Wire models are lenient.** Everything in `data/Models.kt` has a default and the
`Json` config sets `ignoreUnknownKeys`/`coerceInputValues`. A dashboard must
degrade, never blank out, if a field moves.

**`Geo.kt` is unit-tested.** Pin positions come from our own Mercator maths
rather than a map SDK, so changes there need `./gradlew testDebugUnitTest` to stay
green. `fitBoundsRobust` deliberately frames a percentile band; see the README
section on geocoding before "simplifying" it to a plain min/max fit.

## Verifying changes

There is no committed mock server, but the API surface is small and easy to stub
(`/api/jobs/counts`, `/api/jobs/today`, `/api/slack/mentions`, `/api/events` SSE).
When verifying on an emulator, the host is reachable at `http://10.0.2.2:<port>`.

```bash
emulator -avd Google_TV_1080p_API36
./gradlew assembleDebug -PCMAC_SERVER_URL=http://10.0.2.2:3000 -PCMAC_MAPBOX_TOKEN=pk.test
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.cmac.opscommand/.MainActivity
```

Useful checks that do not need screenshots — Compose publishes text to the
accessibility tree, so board state is greppable:

```bash
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | tr ',' '\n' | grep -oE "DATA LIVE|DATA STALE|SLACK OFFLINE|VIEW [0-9]+/[0-9]+"
```

Things worth re-verifying after touching the repository or map code, because each
has already been a real bug once:

- Auto-rotation still cycles all three boards (sample over ~45 s).
- Killing the server surfaces `DATA STALE` within seconds, and restarting it
  recovers **without** relaunching the app.
- A cold start with the server down still paints cached numbers from disk.
- The release build runs: `assembleRelease` + install. R8 plus
  kotlinx.serialization is where breakage hides, and `proguard-rules.pro` is what
  prevents it.

## Non-obvious gotchas

- Orbitron has no `⊗ ○ ◄ ►` glyphs. Missing-glyph boxes on a wall display look
  broken — draw indicators instead of typing symbols.
- The static map is requested at `pitch=0,bearing=0`. `Mercator.offsetPx` is only
  valid for a flat projection; adding pitch breaks every pin position.
- Mapbox Static Images caps each side at 1280 px, hence the `k` scale factor in
  `MapPanel`. Keep the request aspect equal to the viewport or pins skew.
- `local.properties` must use forward slashes for `sdk.dir` on Windows;
  backslashes get eaten as Java properties escapes.
