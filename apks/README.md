# Nekomo builds

This folder is the home of every Nekomo build. The website and the in-app updater read it, so keep the names stable.

| File | What it is |
| --- | --- |
| `Nekomo-phone-release.apk` / `Nekomo-tv-release.apk` | Stable (`app.nekomo`, `app.nekomo.tv`) |
| `Nekomo-phone-beta.apk` / `Nekomo-tv-beta.apk` | Beta (`app.nekomo.beta`, `app.nekomo.tv.beta`) |
| `Nekomo-phone-debug.apk` / `Nekomo-tv-debug.apk` | Debug / alpha (`app.nekomo.debug`, `app.nekomo.tv.debug`) |
| `latest.json` | Version, size, SHA-256 and build time of each file (written by Gradle) |
| `archive/` | Older or legacy builds kept for reference. Nothing in here is served by the website. |

Build and collect everything with `./gradlew assembleAllApks` (or run `./gradlew collectApks` after building single
variants). Never rename the files: the website redirects (`/Nekomo-phone-release.apk` ...) and `scripts/publish-release.sh`
depend on them.
