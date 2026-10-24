<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=220&color=0:050505,45:07152D,75:007BFF,100:00D9FF&text=AURALIS%20TV&fontSize=56&fontColor=FFFFFF&fontAlignY=38&desc=ANDROID%20TV%20%E2%80%A2%20GOOGLE%20TV&descAlignY=62&descSize=16" width="100%" />

<br/>

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=18&duration=2800&pause=900&color=00C8FF&center=true&vCenter=true&width=780&lines=A+cinematic+TV+home+experience;Built+with+Kotlin+%26+Jetpack+Compose;Made+for+the+big+screen" alt="Auralis TV typing animation" />

<br/><br/>

<a href="https://github.com/PauzeDevs/Auralis-TV"><img src="https://img.shields.io/badge/EXPLORE_AURALIS_TV-007BFF?style=for-the-badge&logo=github&logoColor=white" alt="Explore Auralis TV" /></a>

<br/><br/>

<table align="center" role="presentation" cellspacing="0" cellpadding="0" border="0">
<tr>
<td><a href="https://github.com/PauzeDevs/Auralis-TV/actions"><img src="https://img.shields.io/github/actions/workflow/status/PauzeDevs/Auralis-TV/android.yml?branch=Auralis&style=for-the-badge&label=BUILD&color=007BFF&labelColor=050505" alt="Build" /></a></td><td><a href="https://github.com/PauzeDevs/Auralis-TV/releases"><img src="https://img.shields.io/badge/RELEASES-VIEW-00C8FF?style=for-the-badge&labelColor=050505" alt="Releases" /></a></td><td><a href="https://github.com/PauzeDevs/Auralis-TV/blob/Auralis/LICENSE"><img src="https://img.shields.io/github/license/PauzeDevs/Auralis-TV?style=for-the-badge&color=00C8FF&labelColor=050505" alt="License" /></a></td>
</tr>
</table>

<br/><br/>

<table align="center" role="presentation" cellspacing="0" cellpadding="0" border="0">
<tr>
<td><a href="https://github.com/PauzeDevs/Auralis-TV/actions"><img src="https://img.shields.io/badge/CHECK_BUILDS-007BFF?style=for-the-badge&logo=githubactions&logoColor=white" alt="Check builds" /></a></td><td><a href="https://github.com/PauzeDevs/Auralis-TV/issues"><img src="https://img.shields.io/badge/REPORT_ISSUE-07152D?style=for-the-badge&logo=github&logoColor=00C8FF" alt="Report issue" /></a></td><td><a href="https://github.com/PauzeDevs/Auralis-TV/discussions"><img src="https://img.shields.io/badge/DISCUSS-07152D?style=for-the-badge&logo=github&logoColor=00C8FF" alt="Discussions" /></a></td>
</tr>
</table>

</div>

---

## 01 / ABOUT

**A cleaner home for the big screen.**

Auralis TV is a minimal, remote-friendly launcher for Android TV and Google TV, built with **Kotlin** and **Jetpack Compose**.

Inspired by the cinematic feel of tvOS. Not affiliated with Apple.

## 02 / HIGHLIGHTS

- Minimal, cinematic home screen
- Remote-friendly navigation
- Dedicated Apps and For You drawers
- Detects common installed streaming apps and can search titles through their link destinations
- Local per-profile watchlist and recently launched app history
- Cinematic film details with artwork, metadata, trailer/details, and service search actions
- Voice search through the device's Android speech recognizer, with matching titles, installed apps, and service-search options
- Quick Control Center and ambient clock/artwork screensaver
- Default Home-app selection shortcut where the device exposes it
- Quick access to installed apps

## 03 / DOWNLOAD

Get the **Auralis-TV-debug** artifact from a successful [GitHub Actions build](https://github.com/PauzeDevs/Auralis-TV/actions).

## 04 / DEFAULT LAUNCHER

The activity declares Android's Home intent, and the home screen includes a **Set Home** action. On devices that expose the Home role, Auralis requests it; otherwise it opens Home-app settings where available. Some TV vendors restrict third-party default launchers, so the choice must be offered by the device.

## 05 / COMPATIBILITY

Designed for Android TV and Google TV. App discovery uses launchable activities, including TV and standard launcher entries. Known streaming services are detected from their installed package names. Search covers installed apps and the current film-discovery catalogue, and can pass a query to an installed streaming service. The For You drawer shows general film-discovery picks; service searches do not guarantee catalog availability or stream content. Watchlists and recently launched apps are stored locally per Auralis profile. Watchlist entries retain their catalogue metadata locally so they remain visible when the discovery list changes. Android TV does not provide Auralis with a universal playback-progress feed, so recent apps are launcher history only and do not imply cross-app Continue Watching synchronization. Voice search requires a compatible speech-recognition app on the device.

## 06 / BUILT WITH

<div align="center">
<img src="https://skillicons.dev/icons?i=kotlin,androidstudio,gradle" alt="Kotlin, Android Studio, Gradle" />
</div>

## 07 / FEATURED FILMS

Auralis TV can show a small movie-discovery shelf using the public Cinemeta catalogue. The latest successful catalogue response is cached for offline fallback; artwork still depends on network access or the image cache and may not be available offline. Selecting a film opens its available YouTube trailer or its IMDb details page. Auralis TV does **not** stream or provide access to films.

Catalogue: [Cinemeta](https://cinemeta.strem.io/stremio/v1). Artwork URLs are supplied by its metadata response. Availability and catalogue fields are controlled by the external service.

## 08 / LICENSE

[AGPL-3.0](LICENSE)

---

<div align="center">

**MADE FOR THE BIG SCREEN.**

`DESIGN → BUILD → REFINE → SHIP`

<sub>Auralis TV · A PauzeDevs project</sub>

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:00D9FF,50:007BFF,100:050505&height=110&section=footer" width="100%" />

</div>
