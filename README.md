# Auralis TV

A personal Android TV launcher concept for Google TV devices, built with Kotlin and Jetpack Compose.

> This is an early development build. It is not an Apple tvOS port, and default-launcher behavior depends on the TV manufacturer's firmware.

## Current features

- Dark, cinematic home screen with Auralis branding
- Finds installed apps with launch intents and displays them as remote-friendly tiles
- Launches apps using Android's package manager
- Opens Android Settings
- Leanback launcher entry and Home intent filter
- GitHub Actions workflow that builds and uploads a debug APK

## Build APK

1. Open the **Actions** tab in this repository.
2. Select **Android APK**.
3. Choose **Run workflow** (or push a commit to `main`).
4. Open the completed workflow run.
5. Download the **Auralis-TV-debug** artifact. It contains `app-debug.apk`.

## Install on Google TV

Sideload the APK using a trusted file transfer method or Android Debug Bridge (ADB). On the TV, you may need to allow installation from the app used to open the APK.

Auralis TV requests package visibility to find installed apps. It does not request your Google password, read account credentials, or sign in to streaming services.

## Default Home app

The app includes an Android Home intent filter. Whether Google TV permits selecting it as the default Home app is firmware-dependent. Test the app as a regular installed launcher first. Avoid disabling the stock launcher unless you have confirmed a reliable way to restore it.

## Development

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- JDK: 17
- Minimum Android version: 8.0 (API 26)
- Target SDK: 28 (initial sideload-focused prototype)

## License

AGPL-3.0. See [LICENSE](LICENSE).
