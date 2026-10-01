<div align="center">

<img src="app/src/main/res/drawable/nekomo_logo.png" alt="Nekomo logo" width="160" height="160" />

# Nekomo Source Code

### The open-source codebase for the Nekomo application

[![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-3DDC84?style=flat-square&logo=android&logoColor=white)](#-building)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.com/invite/E4Ezmgg7Ka)
[![Stars](https://img.shields.io/github/stars/Nekomo-App/neko-source?style=flat-square&logo=github&color=f5c542)](https://github.com/Nekomo-App/neko-source/stargazers)

<p>
  <a href="https://github.com/Nekomo-App/Nekomo/releases">Download</a>
  ·
  <a href="https://github.com/Nekomo-App/Nekomo">Main Repository</a>
  ·
  <a href="https://github.com/Nekomo-App/Nekomo/issues">Report an Issue</a>
  ·
  <a href="https://discord.com/invite/E4Ezmgg7Ka">Discord</a>
  ·
  <a href="https://nekomoapp.neocities.org/">Website</a>
  ·
  <a href="https://github.com/Nekomo-App">GitHub</a>
</p>

</div>

> [!IMPORTANT]
> Please read the [DMCA & Legal Disclaimer](#-dmca--legal-disclaimer) before using, modifying, distributing, or contributing to Nekomo.

---

## ✨ About

This repository contains the source code for **Nekomo**, an anime and manga tracking client for Android and Android TV. The application itself does **not** host any anime or manga content.

- 🎌 AniList, MyAnimeList and Kitsu sign-in and sync
- 🧩 CloudStream (`.cs3`) extension support
- 📱🖥️ Phone and Android TV flavors from one codebase
- 🔒 Optional DNS-over-HTTPS for the app's own API traffic
- 🪵 Persistent in-app error log with one-tap GitHub issue reporting

---

## 🛠️ Building

Requires a **JDK 17** and the **Android SDK** (versions per `app/build.gradle`).

```bash
./gradlew assemblePhoneDebug   # phone build
./gradlew assembleTvDebug      # Android TV build
./gradlew lint
```

> [!TIP]
> On Windows/OneDrive, a first build can fail with `AAPT: failed writing to ... R.txt`. It's transient — just re-run the same command, one variant at a time.

Contributor and architecture notes live in [`AGENTS.md`](AGENTS.md).

---

## 🗂️ Project Layout

| Path | Description |
| --- | --- |
| `app/src/main/java/com/lagradost/shiro/ui` | Screens: player, settings, welcome gate, library |
| `app/src/main/java/com/lagradost/shiro/utils` | API clients, DoH, error logger, extractors |
| `app/src/main/java/com/lagradost/shiro/utils/cs3` | CloudStream plugin manager and bridge |
| `app/src/main/java/khttp` | OkHttp-backed HTTP facade |
| `app/libs/cs3api.jar` | CloudStream API used by extensions |

---

## 🌐 Socials

| | Link |
| --- | --- |
| 💬 Discord | [discord.com/invite/E4Ezmgg7Ka](https://discord.com/invite/E4Ezmgg7Ka) |
| 🐙 GitHub org | [Nekomo-App](https://github.com/Nekomo-App) |
| 📦 App releases | [Nekomo-App/Nekomo](https://github.com/Nekomo-App/Nekomo/releases) |
| 🧑‍💻 Source code | [Nekomo-App/neko-source](https://github.com/Nekomo-App/neko-source) |
| 🐛 Issues | [Report a bug](https://github.com/Nekomo-App/Nekomo/issues) |
| 🌍 Website | [nekomoapp.neocities.org](https://nekomoapp.neocities.org/) *(occasionally unavailable)* |

<a href="https://discord.com/invite/E4Ezmgg7Ka">
  <img src="https://invidget.switchblade.xyz/E4Ezmgg7Ka" alt="Join the Nekomo Discord server">
</a>

---

## 💬 Community & Contributing

Contributions are welcome — code, bug reports, docs, and testing. Use GitHub for code and issues, and the [Discord](https://discord.com/invite/E4Ezmgg7Ka) for discussion.

---

## ⚖️ DMCA & Legal Disclaimer

<details>
<summary><strong>Click to expand — read before using, modifying, distributing, or contributing</strong></summary>

### 📚 Content and Metadata

Nekomo provides anime and manga tracking functionality. It does **not** host, upload, store, or directly distribute anime or manga content.

Information displayed within the application — titles, artwork, descriptions, episode information, and other metadata — may be obtained from public third-party APIs such as [AniList](https://anilist.co/), [MyAnimeList](https://myanimelist.net/), and [Kitsu](https://kitsu.io/).

Nekomo is not affiliated with, endorsed by, sponsored by, or officially connected to these services unless explicitly stated otherwise.

### 🔗 Third-Party Plugins and Sources

Any anime or manga links accessible through Nekomo may be provided by third-party plugins or external services. These plugins, links, and sources:

- Are not owned or operated by Nekomo
- Have no official affiliation with the Nekomo development team
- May change, become unavailable, or be removed without notice
- Are outside the control of Nekomo and its contributors

### 👤 User Responsibility

Users are solely responsible for how they use the application and any external services accessed through it. By using Nekomo, you agree to:

- Comply with all applicable laws and regulations
- Respect copyright and intellectual property rights
- Follow the terms of service of third-party providers
- Avoid using Nekomo to stream or download copyrighted content unlawfully

The Nekomo developers, maintainers, contributors, and staff are not responsible for misuse of the application or content accessed through third-party services.

### 📩 Copyright Concerns

If you believe content accessible through a third-party source infringes your rights, please contact the relevant source website, provider, or hosting service directly. Nekomo cannot remove, modify, or manage content that it does not host, own, operate, or control.

### ✅ Agreement

By downloading, installing, modifying, distributing, contributing to, or using Nekomo, you acknowledge and agree to the terms of this disclaimer.

</details>

---

<div align="center">

<img src="app/src/main/res/drawable/nekomo_logo.png" alt="Nekomo logo" width="80" height="80" />

**猫も — Nekomo — Cat too.**

*Track responsibly. Respect copyright. Follow applicable laws.*

</div>
