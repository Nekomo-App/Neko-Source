# Notes for coding agents / contributors

## Build & verify

This is a Gradle Android project (Kotlin). There is no Android SDK / JDK available in the
sandboxed environment these notes were written in, so changes made here could **not** be
compiled or run — review diffs carefully and build locally before releasing:

```
./gradlew assembleDebug        # or assemblePhoneDebug / assembleTvDebug depending on flavor
./gradlew lint
```

Requires a JDK (17 recommended) and the Android SDK (compileSdk/targetSdk per `app/build.gradle`).

## Error logging

`com.lagradost.shiro.utils.ErrorLogger` persists a rolling crash/error log to app-private
storage (`filesDir/logs/error_log.txt`), unlike `logError()`'s old debug-only Logcat output.
It's wired up via:
- `Thread.setDefaultUncaughtExceptionHandler` in `AcraApplication.attachBaseContext`
- every call to `logError()` (in `utils/mvvm/ArchComponentExt.kt`)
- the `CoroutineExceptionHandler` on `Coroutines.main`

Users can view/copy/save/share/clear it from Settings -> History -> "Error log"
(`ui/settings/ErrorLogActivity`), and file it as a pre-filled GitHub issue against
`Nekomo-App/neko-source`.

## Networking / DNS over HTTPS

The app's own network calls (`ShiroApi`, `LiveApi`, `MALApi`, `AniListApi`, extractors, ...)
all go through the `khttp.get/post/put/delete/head/patch` facade in
`app/src/main/java/khttp/KHttp.kt`. That facade is now backed by OkHttp (previously raw
`HttpURLConnection`) specifically so a single `okhttp3.Dns` can be swapped in for DNS-over-HTTPS.

- `com.lagradost.shiro.utils.DohProvider` holds the provider list (Cloudflare, Google,
  AdGuard, AdGuard Family, Quad9) and builds an `okhttp3.dnsoverhttps.DnsOverHttps` resolver
  for the selection, or `Dns.SYSTEM` when disabled.
- Settings -> General -> "DNS over HTTPS" (`dns_provider` key, `settings_general.xml`) lets
  the user pick a provider; `khttp`'s cached `OkHttpClient` is rebuilt automatically the next
  request after the preference changes (see `baseClient()` in `KHttp.kt`), no app restart
  needed.
- This does **not** affect ExoPlayer's own `DefaultHttpDataSource` used for video/stream
  playback sockets - matches CloudStream's scope, which only applies DoH to its own API/HTML
  scraping requests, not to the video CDN connections themselves.
- Added Gradle deps: `com.squareup.okhttp3:okhttp:4.12.0` and
  `com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0`.

## Settings audit

Cross-referenced every preference key defined in `res/xml/settings_*.xml` against where it's
actually read in code. Re-enabled three that had a finished feature behind them but were
left commented out in both the XML and the Kotlin read site (`PlayerFragment.kt`): "Skip OP
button", "Playback speed button", "Player resize button" (all default `true` to preserve
today's always-on behavior, and blacklisted from TV settings since `PlayerFragmentTv` doesn't
use them - it has its own always-available D-pad actions instead).

**Left disabled on purpose:** "Use alternative Vidstream" (`alternative_vidstream`,
`utils/extractors/Vidstream.kt`). Its `mainUrl`/`getExtractorUrl()` toggle only ever pointed at
the old fastani/gogo-stream/streamani.net mirrors, which the class's own `getUrl()` already
treats as dead ("Legacy fastani/vidstream ids are dead with the old backend") - the live code
path goes through `LiveApi.resolveStreams` instead and never consults `mainUrl`. Re-enabling
this toggle would just add a switch that does nothing.

## Known follow-up work

- **`Fragment.fv()` (`utils/Synthetics.kt`)** throws `IllegalStateException` via
  `requireView()` whenever a fragment's view is already destroyed (e.g. callbacks firing
  after back-navigation). This is the single biggest source of crashes in the app. Fixing
  it properly means changing `fv()` to return `T?` (`view?.findViewById(id) as? T`) and
  auditing every call site that currently assumes a non-null result (`fv(id).foo()` instead
  of `fv(id)?.foo()`) — this could not be done blind without a compiler in this environment,
  so it was intentionally left out of this pass. Do this with a working Gradle build so
  compile errors surface immediately.
- **Extension/source compatibility (Aniyomi/Mihon/CloudStream)**: this app currently has no
  dynamic extension system — `ShiroApi`/`LiveApi`/`Mkissa` are hardcoded API clients baked
  into the app. Real compatibility with CloudStream's `.cs3` plugin ecosystem requires a new
  subsystem: a repository index format, a downloader, a `DexClassLoader`-based plugin loader
  (compiled against the actual `cloudstream3` API artifact so `Plugin`/`MainAPI` are binary
  compatible), a Settings UI to manage repos/extensions, and wiring loaded `MainAPI`
  providers into search/browse/playback alongside (or instead of) the existing hardcoded
  APIs. This is a substantial, multi-session project and has not been started yet.
