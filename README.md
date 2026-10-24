# Auralis TV

A cinematic, Apple TV-inspired launcher concept for Android TV and Google TV devices, built with Kotlin and Jetpack Compose.

Auralis is an independent Android launcher, not an Apple tvOS port. It uses Android's installed app list and does not access streaming-service accounts or content histories.

## Features

- Dark, cinematic home screen with Auralis branding and a live clock/date.
- Real icons and names for installed apps that can be launched.
- A **Recently opened** row stored locally on the device.
- Animated focus scaling and clear focus outlines for TV remote/D-pad navigation.
- System Settings shortcut.
- Android TV Leanback launcher entry and Home intent filter.
- Layout spacing that adapts to shorter TV viewports.
- GitHub Actions build that creates and uploads a debug APK.

## Build and download the APK

1. Open the [Actions tab](https://github.com/PauzeDevs/Auralis-TV/actions).
2. Select **Android APK** and open the latest successful run.
3. Download the **Auralis-TV-debug** artifact.
4. Extract it to get `app-debug.apk`.

The workflow runs on pushes to `main`, pull requests, and manual dispatches.

## Install on Google TV

Sideload `app-debug.apk` using a trusted transfer method or Android Debug Bridge (ADB). The TV may ask you to allow installation from the app used to open the APK.

Auralis requests package visibility so it can find installed apps. The Recently opened list is stored locally using app package names. Auralis does not request Google passwords, read account credentials, or sign in to streaming services.

## Choosing Auralis as the Home app

The app advertises Android's Home intent. Whether the TV allows it to be selected as the default launcher depends on the manufacturer firmware and Google TV version. First test Auralis as a regular installed app. Do not disable the stock launcher unless you have verified a reliable recovery path.

## Development

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- JDK: 17
- Minimum Android version: 8.0 (API 26)
- Compile SDK: 35

## License

AGPL-3.0. See [LICENSE](LICENSE).
