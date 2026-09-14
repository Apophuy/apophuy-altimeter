# Altimeter

English | [Русский](README.ru.md)

A native, offline-first altimeter and compass for Android 8.0 and later. Google Play Services are not required.

## Screenshots

<p align="center">
  <img src="docs/screenshots/instrument.png" alt="Instrument screen with compass, altitude, and GNSS status" width="30%">
  <img src="docs/screenshots/weather.png" alt="Weather screen with current conditions and forecast" width="30%">
  <img src="docs/screenshots/settings-dark.png" alt="Settings screen in the dark theme" width="30%">
</p>

## Features

- a stable, filtered compass that clearly switches between magnetic and true north, reports sensor accuracy, and includes a calibration guide;
- coordinates, place name, GNSS fix status, and the number of satellites used;
- a delayed warning when GNSS accuracy remains unstable or a reliable satellite signal is unavailable;
- hybrid altitude above mean sea level using geoid-corrected GNSS data and the barometer;
- separate Copernicus GLO-90 terrain elevation;
- pressure from the phone sensor or weather data;
- a dedicated weather tab with current conditions, pressure, and 24-hour and 7-day forecasts;
- a scroll-free instrument screen with coordinates, place, compass, altitude, and satellite status;
- a one-time missing-hardware notice, with permanent hardware details in Settings;
- manually started background altitude recording with configurable elevation step (5 metres by default), sampling interval, and maximum coordinate age;
- local track history, elevation chart, ascent/descent statistics, and GPX/CSV export;
- saved return points with compass target, distance, and bearing;
- decimal degrees, DMS, UTM, and MGRS coordinate formats without a network API;
- altitude threshold notifications with sound and vibration while recording;
- 12 interface languages, including English, Russian, Simplified Chinese, Hindi, Spanish, and Arabic;
- light and dark themes, following the system or selected manually;
- an optional keep-screen-on mode for the Instrument and Points tabs;
- an opt-in GNSS diagnostic log that users can share through the Android share sheet, including Telegram.

The app has no maps, accounts, ads, analytics, paid APIs, or cloud synchronization. Tracks are stored only in the app's local database, and Android backup is disabled.

## Build

JDK 17 or later and Android SDK 36 are required.

```bash
make check
```

The build script automatically detects the SDK through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` in a local `local.properties`, or the standard Android SDK directories. Individual tasks are available through `make unit`, `make lint`, and `make assemble`.

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

To install it on a connected device with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Android Emulator tests

The project defines a Gradle Managed Device named `pixel2Api29` (Android 10). Gradle creates and starts an isolated emulator, runs the instrumentation tests, and shuts it down automatically:

```bash
make emulator-check
```

Gradle may download the required system image on the first run. If a regular emulator is already running or an Android device is connected over USB, use `make device-check`. CI runs both the standard checks and the emulator instrumentation tests.

## Codex tooling

The repository includes agent instructions in `AGENTS.md`, an Android/Kotlin skill in `.agents/skills/android-kotlin/`, and project-local MCP configuration in `.codex/config.toml`. Context7 provides current AndroidX, Compose, Kotlin, and Gradle documentation. The `codebase_memory` MCP keeps a separate semantic project index in the ignored `.codebase-memory/` directory; its visualization is available locally. These project-local tools do not send application data anywhere.

## GNSS diagnostics

To investigate a GNSS issue, enable diagnostic logging in Settings, reproduce the problem outdoors for two to five minutes, and explicitly share the log through the Android share sheet.

Logging is disabled by default. It records GNSS events, device and firmware details, and signal metadata, but does not store precise coordinates or raw NMEA sentences and never uploads anything automatically.

## Accuracy and offline behavior

The compass, GNSS coordinates, core altitude, sensor pressure, and track recording work without a network connection. Place names depend on the system geocoder. Weather and terrain elevation remain available from the latest cache during network failures and are marked as stale. Forecast times use the location's time zone.

Altimeter is intended for everyday and hiking use. It is not suitable for aviation, surveying, rescue, or other safety-critical work.

## Data sources and attribution

- [Open-Meteo](https://open-meteo.com/) provides current weather and forecasts.
- [Copernicus DEM GLO-90](https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM) terrain elevation is accessed through the [Open-Meteo Elevation API](https://open-meteo.com/en/docs/elevation-api).
- Android `AltitudeConverterCompat` performs the offline conversion from ellipsoidal GNSS altitude to mean sea level.
- [NGA MGRS Java 2.1.3](https://github.com/ngageoint/mgrs-java) is used under the MIT license for offline UTM/MGRS conversion in the UTM latitude range from 80° S to 84° N. Outside that range, the app shows no coordinate instead of presenting an invalid UPS result.

## License

Altimeter is licensed under the [GNU General Public License version 3 only](LICENSE).
