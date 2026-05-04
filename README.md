# PureWeb Browser

A lightweight and fast Android web browser built with native WebView.

## Features

- Fast and responsive browsing experience
- JavaScript toggle support
- Desktop mode for full websites
- Clear data functionality
- Material Design UI
- Navigation controls (Back, Forward, Refresh)
- URL bar with search functionality
- Progress indicator

## Requirements

- Android Studio Hedgehog or later
- Android SDK 34 (Compile SDK)
- Java 17

## Building

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

## Project Structure

```
PureWeb-Browser/
├── app/
│   └── src/main/
│       ├── java/com/pureweb/browser/
│       │   ├── MainActivity.java
│       │   └── SettingsActivity.java
│       ├── res/
│       │   ├── drawable/
│       │   ├── layout/
│       │   ├── menu/
│       │   ├── mipmap-anydpi-v26/
│       │   └── values/
│       └── AndroidManifest.xml
├── .github/workflows/
│   └── android-ci.yml
├── build.gradle
├── settings.gradle
└── gradle/wrapper/
```

## License

MIT License
