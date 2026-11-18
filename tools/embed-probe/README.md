# embed-probe

Tiny full-screen app used to prove the headunit Map Source virtual-display path on an
**API 33 AOSP emulator** (no Play Store / no Google Maps).

## What it does

- Solid cyan screen with the word `PROBE`
- Shows the last touch coordinates
- Package: `com.kyuusanq3.embedprobe`

## Build and install

```powershell
# From repo root — requires ANDROID_HOME / local.properties sdk.dir
./gradlew.bat :embed-probe:assembleDebug
adb install -r tools/embed-probe/build/outputs/apk/debug/embed-probe-debug.apk
```

If the standalone module is not wired into `settings.gradle.kts` yet, open
`tools/embed-probe` as its own project in Android Studio, or add:

```kotlin
include(":embed-probe")
project(":embed-probe").projectDir = file("tools/embed-probe")
```

## Point Mix Auto at the probe

In `DeveloperSettings.EMBEDDED_MAP_PACKAGE_OVERRIDE` set:

```kotlin
const val EMBEDDED_MAP_PACKAGE_OVERRIDE: String = "com.kyuusanq3.embedprobe"
```

Rebuild the **headunit** flavor (`assembleHeadunitDebug`) signed with the AOSP platform
test key (`headunit-signing.properties`), install on the AOSP AVD, open **Map Source**,
toggle **Use Google Maps instead of Mix Auto Maps**. The map pane should show `PROBE`.

## Emulator checklist

1. AOSP API 33 AVD (Google APIs / Play Store images will reject the platform-signed APK).
2. `adb shell dumpsys package com.kyuusanq3.mixauto` → `userId=1000`, `sharedUser=android.uid.system`.
3. Permissions `ADD_TRUSTED_DISPLAY`, `INJECT_EVENTS`, … in install permissions.
4. `adb shell ps -AZ | findstr mixauto` → domain `system_app`.
5. Toggle Map Source on → probe fills the pane; divider drag / rotate keeps it inside the pane.
6. `adb shell dumpsys activity activities` → probe task `displayId` is the virtual display, not 0.
7. Toggle off → MapLibre returns; `.\avd-gps.ps1` still feeds Mix Auto.

Google Play AVD: standard `assembleStandardDebug` installs; Map Source is hidden.
`assembleHeadunitDebug` fails install with `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`
or `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — expected.
