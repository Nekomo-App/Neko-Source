# Notes for coding agents / contributors

## Build & verify

This is a Gradle Android project (Kotlin). Build and test locally (JDK 17 + Android SDK; min Android 7.0 / API 24):

```
./gradlew assembleDebug        # or assemblePhoneDebug / assembleTvDebug depending on flavor
./gradlew lint
```

Requires a JDK (17 recommended) and the Android SDK (compileSdk/targetSdk per `app/build.gradle`).

Windows/OneDrive note: the repo lives in OneDrive and a first build of a new variant can fail with
`AAPT: failed writing to ... R.txt: The data is invalid (13)`. It is transient (OneDrive touching
`app/build`); just re-run the same Gradle command, ideally one variant at a time.

## Variants / release builds

- Build types: `debug` (`.debug`), `beta` (`.beta`, minified, signed with the release key), `release` (stable).
  Flavors: `phone`, `tv`. `./gradlew assemble<Flavor><Type>`; copy outputs to `apks/Nekomo-<flavor>-<type>.apk`.
- `coreLibraryDesugaringEnabled true` stays on for newer java.time/collection APIs (minSdk is 24, see "Browsing / installing sources").
- Release/beta builds OOM'd in lint-vital with a 2 GB heap; heap is now 4 GB. If it recurs, add
  `-x lintVitalAnalyze<Variant> -x lintVitalReport<Variant> -x lintVital<Variant>`.
- TV manifest declares leanback `required=false` so the TV APK can be installed on phones for testing
  (`TvActivity` has both LAUNCHER and LEANBACK_LAUNCHER categories).
- Use `NavController.safeNavigate` (`utils/Synthetics.kt`) instead of `navigate` to avoid double-tap crashes;
  use `ContextCompat.registerReceiver` with an explicit export flag (Android 14 target).

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

The app's own network calls (`NekomoApi`, `LiveApi`, `MALApi`, `AniListApi`, extractors, ...)
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

- **`Fragment.fv()` (`utils/Synthetics.kt`) — mitigated:** it is now `inline reified` and returns a
  detached throwaway view of the requested type when the fragment view is destroyed, instead of
  throwing. Call sites are unchanged. Remaining risk: code that casts `layoutParams` or reads
  state from that dummy view can still fail. Original note: it throws `IllegalStateException` via
  `requireView()` whenever a fragment's view is already destroyed (e.g. callbacks firing
  after back-navigation). This is the single biggest source of crashes in the app. Fixing
  it properly means changing `fv()` to return `T?` (`view?.findViewById(id) as? T`) and
  auditing every call site that currently assumes a non-null result (`fv(id).foo()` instead
  of `fv(id)?.foo()`) — this could not be done blind without a compiler in this environment,
  so it was intentionally left out of this pass. Do this with a working Gradle build so
  compile errors surface immediately.
- **Aniyomi/Mihon/Zangetsu**: implemented (see below). LN Reader plugins are not implemented.

## CloudStream extensions (.cs3)

- `app/libs/cs3api.jar` is a trimmed `com/lagradost/**` copy of CloudStream's `pre-release`
  `classes.jar` (what real plugins compile against). It needs Kotlin >= 2.4 (root `build.gradle`)
  and the runtime deps listed under "CloudStream extension" in `app/build.gradle`.
  It is GPL-derived; review licensing before redistributing.
- `utils/cs3/CsPluginManager.kt`: repos, install (sha256 verify, atomic replace), `PathClassLoader`
  loading from `filesDir/Extensions`, enable/disable, update. Initialised in `AcraApplication`.
- `utils/cs3/CsBridge.kt`: maps providers into app models. Slugs/tokens are `cs3|<api>|<hex url>`;
  hooks are in `NekomoApi.searchNew`, `LiveApi.getAnimePage` and `LiveApi.resolveStreams`.
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
- Official URLs (Discord, Telegram, website, support, ToS, Privacy, DMCA) live in `res/values/links.xml` and `settings_about.xml`.
- Settings -> Community Rules (`CommunityRulesActivity`) reuses the rules text and `SocialLinks`.
- `PlayerActivity` (exported for `content://` video intents) is gated: it bounces to the launcher unless signed in
  and rules accepted.

- Verified: phone + TV debug builds and phone release (R8) compile; `lintPhoneDebug` has 28 errors, none
  in the new code (rest pre-existing: NewApi, Range, receiver flags).
- Earlier note: phone + TV debug builds compile. NOT verified: on-device plugin loading, playback,
  and the release (R8) build - no emulator/device was available.

## Device testing notes (verified on a Galaxy S22 Ultra, Android 16)

- Extensions: `CsBridge.search` tries `search(query, page)` first, then the legacy `search(query)`
  (each provider implements only one; the other throws NotImplementedError). Verified end to end with
  CloudStream's official repo (`recloudstream/extensions`): install InternetArchiveProvider -> search ->
  details -> play; the player logs `ExtractorLink(name=Internet Archive ...)` from archive.org.
- `CsPluginManager.init` and `ErrorLogger.init` must run with a real context: `applicationContext` is null in
  `attachBaseContext`. `ErrorLogger` silently dropped every entry before this was fixed.
- R8: Jackson + kotlin-reflect failed on `kotlin.collections.EmptyMap` (home feed never loaded in release/beta,
  with no logged error). `LiveApi.gql` now uses plain JDK maps and proguard keeps the empty collections,
  view constructors (`Synthetics.fv` placeholders) and `com.lagradost.shiro.**`.
- `desugar_jdk_libs` must be >= 2.1.5 (1.2.2 rewrote `List.addFirst` to a method that does not exist -> crash on
  Android 16 at launch).
- To read logs from a minified build, temporarily set `debuggable true` on the beta type and use
  `adb exec-out run-as app.nekomo.beta cat files/logs/error_log.txt`.

## Sources / playback (verified on device, release build)

- Anime page has a cloud-icon "Choose source" button (`ResultFragment.showSourceChooser`): lists the built-in
  catalogue plus every loaded extension provider, searches the title there and opens the match.
- In-player "Source" menu picks a stream link; it now uses `setValue` (was `postValue`, which raced with the
  reload) and `onPlayerError` automatically falls through to the next link.
- The old built-in streaming backend (AllAnime/mkissa/fastani, WebView scraper, host extractors) was **removed**; AniList
  is metadata only and streams come exclusively from user-installed sources (`LiveApi.resolveStreams` routes
  `cs3-` / `zg-` / `ay-` tokens). `Vidstream.kt` only dispatches those tokens.
- Not implemented: LN Reader plugins (need a cheerio-style JS DOM plus a novel reader).

## Zangetsu sources (JS providers)

- `utils/zg/ZangetsuEngine` runs Zangetsu provider scripts on Rhino (interpreted, ES6, one big-stack thread;
  host API = promise `fetch`, timers, `htmlText`, `absUrl`, base64/hex helpers, sha256/AES-CTR). `ZangetsuManager`
  stores repos (manifest `index.json` URLs) and installed scripts in `filesDir/Zangetsu`; `ZangetsuBridge` maps
  `search` / `getDetail` / `getVideoSources` into app models. Slugs are `zg-<hex id>-<hex url>`.
- Nothing is bundled: the user adds a repo and installs sources in Settings -> Extensions -> Zangetsu sources.
- Verified on device (debug + minified release): add `Spyou/zangetsu-providers` -> install HiAnime -> results show
  in search, anime page loads, and an episode streamed (HLS) in debug. HiAnime itself can answer
  "no playable server" for some episodes; the player now says so instead of spinning forever.
- Aniyomi/Mihon were ported from Zangetsu's reference bridges (GPLv3); LN Reader plugins are still not implemented.

## No bundled sources (policy) + Aniyomi / Mihon / filters

- A fresh install ships NO streaming source, repo, extractor or extension. AniList (graphql.anilist.co) is used for
  metadata only (trending/search/details); an AniList title has no episodes until the user picks one of their own
  sources via the cloud "Choose source" button (the page opens the chooser automatically). The old AllAnime/mkissa
  backend, its WebView scraper and all host extractors were removed; there is no "browse public repositories" list.
- Sources the user adds themselves: CloudStream (`cs3-`), Zangetsu (`zg-`), Aniyomi anime extensions (`ay-`), and
  Mihon manga extensions (Settings -> Manga). Settings -> Extensions has one section per kind; every add/install asks
  for confirmation, and each installed item has a "Report" button (opens the repo's issue tracker).
- `eu/kanade/tachiyomi/**`, `tachiyomi/**` are the vendored Aniyomi/Mihon extension API (Apache-2.0, copied from
  Zangetsu's repo, GPLv3 host). `utils/aniyomi/AniyomiRuntime` stands up Injekt; `AniyomiManager`/`MihonManager`
  download APKs from an `index.min.json` repo, validate and load them with `DexClassLoader`.
- Settings -> General: "Show movies and TV shows" (`non_anime_enabled`, default off) and "Show 18+ sources and
  extensions" (`nsfw_enabled`, default off); see `utils/ContentPrefs`.
- Verified on device: Aniyomi (yuzono/anime-repo, Anikoto) -> install -> search -> details -> episode list; stream playback
  could not be confirmed because the test phone's DNS-filter VPN blocks that extension's backends. Mihon
  (`keiyoushi/extensions` `index.pb`, MANGA Plus) -> install -> popular -> chapters -> reader works on debug and on the
  minified release build. Keiyoushi's/Yuzono's `index.min.json` now return only an "update your app" stub; use `index.pb`.
- Not device-tested: the "Show movies and TV shows" toggle revealing CloudStream plugins, the TV builds, Zangetsu after the
  OkHttp 5 upgrade. LN Reader plugins are not implemented (need a cheerio-compatible JS DOM).

## Update channel (in-app updater)

- `utils/InAppUpdater` reads GitHub releases from every repo in the `update_repos` setting (default
  `Nekomo-App/Nekomo,Nekomo-App/neko-source`), compares versions numerically, and offers the newest one.
- Assets are matched to the installed build: `Nekomo-<phone|tv>-<release|beta|debug>.apk` (legacy `x.y.z.apk` /
  `x.y.z-TV.apk` still match for stable). Beta/Debug apps (different package + signature) only ever see their own
  APK and always follow pre-releases; the stable app follows pre-releases only with "beta_mode".
- Settings -> Updates: startup check (`auto_update`, throttled to 1/h), pre-release switch, repositories, manual
  check, version/channel line. Downloads are restricted to https github.com / *.githubusercontent.com.
- Publish with `scripts/publish-release.sh <version> [stable|beta] [owner/repo]` (needs `gh`). No releases exist
  yet in either Nekomo-App repo, so the updater currently reports "up to date".
- `ShiroApi` was renamed `NekomoApi`; the old Shiro token/donor/shiro-db/fastani endpoints were deleted.

## Licensing

- `LICENSE` = original Shiro MIT notice (kept). `COPYING` = GPL-3.0 text: the shipped APK includes GPL-3.0 parts
  (cs3api.jar, NiceHttp, NewPipeExtractor, Zangetsu-derived), so the combined app is GPL-3.0-or-later.
- `THIRD_PARTY_LICENSES.md` + `app/src/main/assets/licenses.txt` are generated: run
  `./gradlew :app:dependencies --configuration phoneReleaseRuntimeClasspath > deps.txt` then
  `python scripts/gen_licenses.py deps.txt` after changing dependencies (script fails loudly on unknown licenses).
- fuzzywuzzy (GPL-2.0-only, incompatible with GPL-3) was removed; `utils/FuzzyMatch` replaces it.
- play-services-cast/gms use Google's proprietary SDK license; keep that in mind for F-Droid style distribution.

## Theme

- Default Cyanea theme (`assets/themes/cyanea_themes.json`[0] "Nekomo") and `values/colors.xml` use the website/icon
  palette (accent #e5395f, deep #8d2249, bright #ff5c7a, background #0c0508). It is re-applied once per install
  (`nekomo_theme_v1`). The old red is still available as "Classic red".

## Browsing / installing sources

- No in-app directory/search/one-click install of sources: users paste a repository URL themselves (CloudStream
  shortcodes via cutt.ly/py.md and the "Install all" button were removed). A repo's own list is shown only after the
  user adds that repo. Mihon repos accept `index.min.json` or the new gzip'd protobuf `index.pb`.
- minSdk is 24: current Mihon extensions (extension-lib 1.6) need real interface default methods, which D8 strips at
  minSdk < 24 (AbstractMethodError). OkHttp is 5.3.2 (extensions use okhttp3.brotli.Brotli / okhttp3.zstd.Zstd).
  proguard-rules.pro keeps kotlin/okhttp/okio/jsoup/rx/injekt/eu.kanade for the dynamically loaded extension APKs.

## Offline (local) library

- Not signed in? `utils/LocalLibrary` keeps a per-device library (Watching / Plan to watch / Completed / On hold / Dropped).
  Add titles from the title page's right-hand sync panel ("Save to offline library"); view/edit/export/import in
  `ui/library/LocalLibraryActivity` (Library tab -> "Open offline library"). Export is a JSON share; import merges by
  newest update; "Sync to AniList" pushes entries whose slug is `al<id>` once signed in.
- Favorites (hearts), continue-watching, downloads and the existing Back up / Restore in Settings -> History also work
  without an account.
