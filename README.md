<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=Inter&weight=600&size=34&pause=900&color=B7F36B&center=true&vCenter=true&width=700&lines=Auralis+TV;A+calmer+home+for+your+TV;Made+for+the+big+screen" alt="Animated Auralis TV title" />

<p><strong>A cinematic, Apple TV-inspired launcher for Android TV and Google TV.</strong></p>

<p>
  <a href="https://github.com/PauzeDevs/Auralis-TV/actions"><img src="https://img.shields.io/github/actions/workflow/status/PauzeDevs/Auralis-TV/android.yml?branch=main&style=for-the-badge&label=Build" alt="Build status" /></a>
  <img src="https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV-101820?style=for-the-badge" alt="Android TV and Google TV" />
  <img src="https://img.shields.io/badge/Min%20Android-8.0%20(API%2026)-B7F36B?style=for-the-badge&labelColor=101820" alt="Minimum Android 8.0" />
  <img src="https://img.shields.io/badge/License-AGPL--3.0-101820?style=for-the-badge" alt="AGPL-3.0 license" />
</p>

</div>

---

## ✨ What is Auralis?

Auralis TV is an independent Android TV launcher concept built with **Kotlin and Jetpack Compose**. It brings a minimal, cinematic interface to your television—with app shortcuts, a locally stored recent-apps row, and remote-friendly focus navigation.

> Auralis is inspired by the visual language of tvOS. It is not an Apple tvOS port and is not affiliated with Apple.

## 🎬 Features

- **Cinematic home screen** — dark surfaces, restrained accent color, and a live clock/date.
- **Your installed apps** — real app labels and icons, launched directly.
- **Recently opened** — a local list of up to 12 apps.
- **Remote-first navigation** — D-pad focus outlines, scaling, and activation.
- **System Settings shortcut** — quickly open Android settings.
- **Launcher integration** — Android TV Leanback entry and Home intent.
- **Adaptive layout** — spacing adjusts for shorter TV viewports.
- **CI-built APK** — GitHub Actions builds and uploads a debug artifact.

## 📺 Device compatibility

Auralis is designed for the **Android TV / Google TV platform**, rather than one TV manufacturer. The launcher may run on Android TV-based models from brands including:

| Brand / platform | Compatibility target |
|---|---|
| TCL Google TV / Android TV | Primary development device |
| Sony BRAVIA Android TV / Google TV | Intended platform support |
| Hisense Android TV / Google TV models | Intended platform support |
| Philips Android TV / Google TV models | Intended platform support |
| Xiaomi / Redmi Android TV models | Intended platform support |
| Other Android TV / Google TV devices | Depends on Android version and manufacturer firmware |

**Important:** Brand names alone do not guarantee support. Models can use different operating systems, Android versions, and firmware restrictions. The ability to set Auralis as the default Home app is controlled by the device manufacturer and system software.

- **Amazon Fire TV:** Android-based Fire OS devices may be compatible, but require testing.
- **Samsung Tizen and LG webOS:** Not supported by this Android APK. Those platforms require separate native applications.

## 📦 Download the APK

1. Open the [GitHub Actions page](https://github.com/PauzeDevs/Auralis-TV/actions).
2. Choose the latest successful **Android APK** workflow run.
3. Download the **Auralis-TV-debug** artifact.
4. Extract the archive to find `app-debug.apk`.

The workflow runs on pushes to `main`, pull requests, and manual dispatches. Only use an artifact from a successful build.

## 🛠 Install on Android TV / Google TV

1. Download `app-debug.apk` from the workflow artifact.
2. Transfer it to the TV using a trusted method, or install through Android Debug Bridge (ADB).
3. If prompted, allow the file manager or installer to install unknown apps.
4. Open Auralis TV and test it as a regular app first.

### Set Auralis as the Home app

Auralis declares Android's Home intent. Whether your television lets you select it as the default launcher depends on manufacturer firmware and Android TV / Google TV version.

**Do not disable or remove the stock launcher** unless you have verified a reliable way to recover your device.

## 🔐 Privacy notes

- Auralis does not request streaming-service credentials or sign in to streaming accounts.
- Recently opened apps are stored locally as package names.
- App discovery uses Android package visibility.
- Auralis does not read account passwords or streaming-service watch history.

## 🧰 Development

| Component | Version / target |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| JDK | 17 |
| Minimum Android | 8.0 (API 26) |
| Compile SDK | 35 |

## 🗺 Roadmap

- [ ] Test on additional Android TV and Google TV manufacturers
- [ ] Improve launcher selection and recovery guidance by device
- [ ] Validate remote navigation across different screen sizes
- [ ] Explore a separate companion experience for other TV platforms

## 📄 License

Auralis TV is licensed under **AGPL-3.0**. See [LICENSE](LICENSE).

---

<div align="center">
  <sub>Built for the big screen · Auralis TV</sub>
</div>
