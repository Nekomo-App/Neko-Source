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

Windows/OneDrive note: the repo lives in OneDrive and a first build of a new variant can fail with
`AAPT: failed writing to ... R.txt: The data is invalid (13)`. It is transient (OneDrive touching
`app/build`); just re-run the same Gradle command, ideally one variant at a time.

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
- **Aniyomi/Mihon extension compatibility**: not implemented (CloudStream is, see below).

## CloudStream extensions (.cs3)

- `app/libs/cs3api.jar` is a trimmed `com/lagradost/**` copy of CloudStream's `pre-release`
  `classes.jar` (what real plugins compile against). It needs Kotlin >= 2.4 (root `build.gradle`)
  and the runtime deps listed under "CloudStream extension" in `app/build.gradle`.
  It is GPL-derived; review licensing before redistributing.
- `utils/cs3/CsPluginManager.kt`: repos, install (sha256 verify, atomic replace), `PathClassLoader`
  loading from `filesDir/Extensions`, enable/disable, update. Initialised in `AcraApplication`.
- `utils/cs3/CsBridge.kt`: maps providers into app models. Slugs/tokens are `cs3|<api>|<hex url>`;
  hooks are in `ShiroApi.searchNew`, `LiveApi.getAnimePage` and `LiveApi.resolveStreams`.
- `ExtractorLink` gained `isDash` and `headers`; `PlayerFragment` applies them.
- UI: Settings -> Extensions (`ui/settings/ExtensionsActivity`).
- Slugs/tokens are now `cs3-<hex api>-<hex url>` (hex + `-` only: safe in file names, prefs keys and
  `|`-split tokens). Search/load/loadLinks calls have timeouts in `CsBridge`.
- Extensions screen shows a one-time "only install extensions you trust" warning.

## Sign-in gate / welcome screen

- `ui/welcome/WelcomeGate` is a full-screen non-cancelable Dialog shown over `MainActivity` and
  `TvActivity` (called right after `setContentView`) until `AuthManager.isSignedIn` (token AND fetched
  profile for AniList, MAL or Kitsu) and the community rules have been accepted.
  OAuth redirects still return through `handleIntent`; the gate polls and advances by itself.
  On TV the in-app WebView sits under the dialog window, so the dialog hides while it is open.
- Kitsu (`utils/auth/KitsuApi`): OAuth password grant with Kitsu's public client credentials.
- Placeholder URLs (Discord, Telegram, website, support, ToS, Privacy) live in `res/values/links.xml`
  and MUST be replaced before release.
- Settings -> Community Rules (`CommunityRulesActivity`) reuses the rules text and `SocialLinks`.
- Known gap: `PlayerActivity` is exported for `content://` video intents and is not gated.

- Verified: phone + TV debug builds and phone release (R8) compile; `lintPhoneDebug` has 28 errors, none
  in the new code (rest pre-existing: NewApi, Range, receiver flags).
- Earlier note: phone + TV debug builds compile. NOT verified: on-device plugin loading, playback,
  and the release (R8) build - no emulator/device was available.
