# Head unit reference: Doro S10A / QCM6125

Captured 2026-11-14 over wireless adb while the unit was on. The car was powered off the same day, before the next Android Auto setting was tried. This note is for later debugging and for apps that integrate with this unit, including Mix Auto (`com.kyuusanq3.mixauto`).

Values below were read back from the unit or from package dumps taken that session. A field that was not captured is marked **unknown**. Display density, screen size, `ro.product.manufacturer`, `ro.product.device`, and `ro.product.board` were queried in passing and were not recorded, so they are omitted.

Wireless adb serial: `adb-C5CA62F9FB26D989-R59hpA._adb-tls-connect._tcp`

The firmware stack on the unit is HCT / Microntek (`android.microntek.*`, `MicrontekServer`). Brand property is Doro. Hardware is Qualcomm (`qcom`), model `QCM6125`, product `S10A`.

## Build

| Property | Value |
|---|---|
| `ro.build.version.release` | 13 |
| SDK | 33 (REL) |
| `ro.build.type` | `userdebug` |
| `ro.build.tags` | `release-keys` |
| `ro.build.user` | `hct` |
| `ro.product.name` | `S10A` |
| `ro.product.model` | `QCM6125` |
| `ro.product.brand` | `Doro` |
| `ro.hardware` | `qcom` |
| `ro.build.fingerprint` | `Doro/S10A/QCM6125:13/S10A_123/eng.hct.20250122.094729:userdebug/release-keys` |
| System fingerprint | `qti/qssi/qssi:13/S10A_123/hct01220947:userdebug/release-keys` |
| `ro.build.display.id` | `qssi-userdebug 13 S10A_123 eng.hct.20250122.094729 release-keys` |
| Build id in the fingerprint | `S10A_123` |
| Incremental in the product fingerprint | `eng.hct.20250122.094729` |
| Incremental in the system fingerprint | `hct01220947` |
| Security patch | `2022-08-05` |

The product fingerprint and the system fingerprint differ. The system image identifies as `qti/qssi/qssi`. The product fingerprint identifies as `Doro/S10A/QCM6125`.

`ro.build.tags` is `release-keys`. The platform certificate is the public AOSP test key (next section). The tag and the certificate do not describe the same key.

## Verified boot and shell

| Property | Value |
|---|---|
| Verified boot | `orange` |
| `ro.boot.vbmeta.device_state` | `unlocked` |
| `ro.boot.flash.locked` | `0` |
| `ro.debuggable` | `1` |
| `ro.secure` | `1` |
| adb shell | uid **2000**, SELinux context `u:r:shell:s0` |

No `su` or Magisk binary was present in the usual paths (`/system/bin/su`, `/system/xbin/su`, `/sbin/su`, `/debug_ramdisk/su`, `/system/bin/magisk`, `/sbin/.magisk`). `command -v` found neither `su` nor `magisk`. This session did not root the unit and did not change the image.

## Platform signing

Read from the public signer of `/system/framework/framework-res.apk` (package `android`). The APK was not kept. No private key, gearhead certificate, or keystore was pulled.

| Field | Value |
|---|---|
| Scheme | v3, one signer, no past signers |
| SHA-256 | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| SHA-1 | `27196e386b875e76adf700e7ea84e4c6eee33dfa` |
| Subject | `EMAILADDRESS=android@android.com`, `CN=Android` |
| What it is | Public AOSP platform test certificate |
| Package Manager signature id | `b4addb29` |

Signature id `b4addb29` is the id shared by apps that run as `android.uid.system`.

`com.miniaa.launcher` 0.3.0 is the on-device proof of what that certificate allows. It was installed through `com.android.packageinstaller` (path `/data/app`), signed with this certificate, and declared `android:sharedUserId="android.uid.system"`. It runs as uid 1000. It is not a priv-app. Its package flags are not `SYSTEM` and not `PRIVILEGED`.

## Packages

Signing class is "platform" only where the report tied the package to signature id `b4addb29` / the AOSP test cert. "Own cert" means a different signer was observed. Blank cells that were not captured are **unknown**.

| Package | Version | versionCode | uid | Path | Signing / privilege |
|---|---|---|---|---|---|
| `com.kyuusanq3.mixauto` | 0.1.13 | 59 | 10202 | `/data/app` | Normal app. Installer `com.android.packageinstaller`. No `sharedUserId`. Different signer from the platform. Flags only `HAS_CODE`, `ALLOW_CLEAR_USER_DATA`, `ALLOW_BACKUP`. Does not request `REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION`. |
| `com.google.android.projection.gearhead` (current) | 10.9.634674-release | 109634674 | 10198 | `/data/app/~~4xNYcJvCb_TuWLjZqUg2FA==/com.google.android.projection.gearhead-JwsibpI_cZMETaLiqPzDcQ==` | Own shared user `com.google.android.projection.gearhead`. Different signer. Not `SYSTEM`, not `PRIVILEGED`. Installed for user 0 via adb on 2026-11-14 after the previous build was uninstalled. |
| `com.google.android.projection.gearhead` (previous, uninstalled) | 18.0.664264 | 180664264 | unknown | unknown | Play Store build that was on the unit at the start of the session. Crash handler also logged minimum required ver `174663050`. |
| `com.zjinnova.zlink` | 5.4.89 | unknown | 10164 | `/system_ext/bundled_persist-app/...` | `SYSTEM`, not privileged. Own certificate. JAR v1. Not `android.uid.system`. Factory phone-link app. The OS lists it next to the CAN bus service and SystemUI as a package it will try to rescue if it crashes. |
| `com.miniaa.launcher` | 0.3.0 | unknown | 1000 | `/data/app` | Platform cert, signature id `b4addb29`, `sharedUser=android.uid.system`. Installer `com.android.packageinstaller`. Not `SYSTEM`, not `PRIVILEGED`. |
| `gb.xxy.hr` (Headunit Reloaded V9.0) | label V9.0; versionName unknown | unknown | 10181 | `/data/app/~~dvjUPxjJgcEvk23IWloWdw==/gb.xxy.hr-rbeh10Frp4fL1cIKxkEY0w==` | Signing class unknown. Activity `gb.xxy.hr.activities.MainActivity`. Log tag `HU-TransportService_Headunit Reloaded V9.0`. |
| `fr.sd.taada` | 2.8.1 | unknown | 10186 (process name `u0a186`) | unknown | Signing class unknown. Was running during the session. Later killed as an empty process (`adj 995`) at 11-14 08:25:10. |
| `net.easyconn` | unknown | unknown | unknown | unknown | Installed and idle during the session. EasyConnection. No version, uid, path, or signer was recorded. |
| `com.borconi.emil.wifilauncherforhur` | unknown | unknown | unknown | unknown | Installed and idle. Headunit Reloaded Wi-Fi launcher. It queries `gearhead`. No version, uid, path, or signer was recorded. |

`fr.sd.taada`, `gb.xxy.hr`, and `com.borconi.emil.wifilauncherforhur` all appear in gearhead's package-visibility queries, so those packages are installed and can see gearhead.

### Gearhead 10.9 package detail

User 0, after settings had been opened once: `installed=true`, `hidden=false`, `suspended=false`, `stopped=false`, `notLaunched=false`, `enabled=0` (default; the package itself is enabled). At the moment of install, before that open: `stopped=true`, `notLaunched=true`. There is no component enable/disable override.

| Field | Value |
|---|---|
| minSdk / targetSdk | 26 / 34 |
| `primaryCpuAbi` | `arm64-v8a` (`secondaryCpuAbi` null) |
| APK file that was installed | `com.google.android.projection.gearhead_10.9.634674-release-109634674_minAPI26(arm64-v8a)(nodpi)_apkmirror.com.apk` |
| `apkSigningVersion` | 3 |
| splits | `base` |
| `extractNativeLibs` | false |
| `pkgFlags` | empty |
| flags | `HAS_CODE`, `ALLOW_CLEAR_USER_DATA`, `ALLOW_BACKUP` |
| privateFlags | `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE`, `ALLOW_AUDIO_PLAYBACK_CAPTURE`, `HAS_DOMAIN_URLS`, `PARTIALLY_DIRECT_BOOT_AWARE`, `PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING` |
| Signing KeySet | 76 |
| signatures | version 3, current `d2a5217f`; past `159c4de5` flags 17 and `d2a5217f` flags 17 |
| dataDir | `/data/user/0/com.google.android.projection.gearhead` |
| firstInstallTime / lastUpdateTime | 2026-11-14 08:37:46 |
| package timestamp | 2026-11-14 08:37:44 |
| `packageSource` | 0 |
| queriesPackages | `com.google.android.apps.maps` |
| supplementary gids | 3001, 3002, 3003 |
| dexopt | arm64 `status=verify`, `reason=install` |
| optional libraries | `org.apache.http.legacy`, `androidx.window.extensions`, `androidx.window.sidecar` |
| legacy jar | `/system/framework/org.apache.http.legacy.jar` |

`REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION` and `TOGGLE_AUTOMOTIVE_PROJECTION` are in the requested-permission list only. They are absent from install permissions. Gearhead does not have either one granted.

Install permissions that are granted include the ordinary ones (`INTERNET`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, Bluetooth, Wi-Fi state, `QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, gearhead's own signature permissions, `androidx.car.app.ACCESS_SURFACE`, `androidx.car.app.NAVIGATION_TEMPLATES`, `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, GSF `READ_GSERVICES`). Runtime permissions recorded as granted, with `SYSTEM_FIXED|GRANTED_BY_DEFAULT`, include calendar, call log, contacts, phone state, SMS, fine and coarse location, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `NEARBY_WIFI_DEVICES`, `CALL_PHONE`, and `com.google.android.gms.permission.CAR_SPEED`.

### How to open Android Auto 10.9

There is no app-list icon. The only launcher entry is the activity-alias `com.google.android.gearhead.vanagon.VnLaunchPadActivity`. The manifest sets `android:enabled="false"` on it (`exported=true`, target `VnLaunchPadInternalActivity`). `query-activities` for `MAIN` / `LAUNCHER` returned no activities. `aapt dump badging` lists no launchable activity.

`SetupActivity` (`.frx.SetupActivity`) is enabled and exported, category `android.intent.category.CAR_PROJECTION`, so it does not appear in the app list.

Settings activity, enabled and exported:

`com.google.android.projection.gearhead/com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity`

It is also registered for action `com.google.android.projection.gearhead.SETTINGS`. One start of that activity succeeded and the window was focused and fullscreen.

`com.miniaa.launcher` and `com.android.launcher3` are both installed. Neither APK contains the gearhead package name. Their private hide-lists were not readable as shell (`Permission denied`). The icon never reaches a launcher query because the alias is disabled. Which package holds `android.app.role.HOME` was not recorded.

### uid 1000 (`android.uid.system`)

uid 1000 is the shared user `android.uid.system`. Explicitly checked as the same platform cert and `sharedUser=android.uid.system`:

| Package | Path | Flags |
|---|---|---|
| `android` | `/system/framework` | `SYSTEM` + `PRIVILEGED` |
| `android.microntek.service` | `/system/priv-app/HCTManagerService` | `SYSTEM` + `PRIVILEGED` |
| `android.microntek.canbus` | `/system/priv-app/HCTCanBus` | `SYSTEM` + `PRIVILEGED` |
| `android.microntek.mtcser` | `/system/priv-app/HCTBlueToothManager` | `SYSTEM` + `PRIVILEGED` |
| `com.miniaa.launcher` | `/data/app` | uid 1000, platform cert, not `SYSTEM`, not `PRIVILEGED` |

Other packages listed as uid 1000 (individual apksigner dumps were not taken; they share this uid):

`com.microntek.travel`, `com.microntek.autotest`, `com.hct.factory`, `com.android.settings`, `com.android.providers.settings`, `com.android.server.telecom`, `com.android.inputdevices`, `com.android.keychain`, `com.android.location.fused`, `com.android.localtransport`, `com.android.wallpaperbackup`, `com.android.provision`, `com.android.dynsystem`, `com.android.music`, `com.qti.diagservices`, `vendor.qti.qesdk.sysservice`, `com.qualcomm.qti.xrvd.service`, `com.qualcomm.qti.services.secureui`, `com.qti.snapdragon.qdcm_ff`, `com.fibo.logviewer`, `com.abupdate.fota_demo_iot`.

`com.miniaa.launcher` process was observed as uid 1000 for `com.miniaa.launcher.service.MediaListenerService`. ActivityManager logged `com.miniaa.launcher is exempt from freezer`. A top-activity start of Mix Auto was recorded as `com.kyuusanq3.mixauto/com.kyuusanq3.mixauto.LauncherModeAlias` in process `u0a202` (uid 10202).

### Other uids seen the same morning

Versions were not read for these. Included because the uid was in a process or log record.

| Package | uid |
|---|---|
| `com.android.vending` | 10091 |
| `com.google.android.gms` | 10092 |
| `com.android.systemui` | 10141 (`u0a141`) |
| `com.android.networkstack.process` | 1073 |
| phone / radio sender of `SERVICE_STATE` | 1001 |
| `system` (system_server, pid 1444 through the session) | 1000 |

Also named in logs or in gearhead's query list, with version and uid **unknown**: `com.google.android.apps.maps`, `com.google.android.youtube`, `com.google.android.apps.youtube.music`, `com.waze`, `com.anod.car.home.free`, `app.organicmaps`, `com.google.android.play.games`, `com.google.android.apps.accessibility.voiceaccess`, `colorwidgets.ios.widget.topwidgets`, `mureung.obdproject`, `com.android.cellbroadcastreceiver.module`, `com.android.launcher3`. During the Android Auto 18 crash, `MicrontekServer` logged `GPSPKNAME` `com.keigun.drivenowshortcut2`.

## Priv-apps and allowlists

`/vendor/priv-app` does not exist. Privileged apps live under `/system/priv-app`, `/system_ext/priv-app`, and `/product/priv-app`.

HCT priv-app directory names that were listed:

| Directory | Package, when recorded | Partition, when recorded |
|---|---|---|
| `HCTManagerService` | `android.microntek.service` | `/system/priv-app` |
| `HCTCanBus` | `android.microntek.canbus` | `/system/priv-app` |
| `HCTBlueToothManager` | `android.microntek.mtcser` | `/system/priv-app` |
| `HCTUsbMS` | unknown | among the priv-app listings; per-directory partition not recorded |
| `HiCarViewLite` | unknown | among the priv-app listings; per-directory partition not recorded |
| `HCTProvision13` | unknown | among the priv-app listings; per-directory partition not recorded |
| `HCTSettingsMsm13` | unknown | among the priv-app listings; per-directory partition not recorded |
| `HCTSystemUIMsm13` | unknown | among the priv-app listings; per-directory partition not recorded |

Gearhead is not in any priv-app directory. Product priv-app includes `GmsCore`, `Phonesky`, and `Velvet`. `GoogleServicesFramework` is under `system_ext`.

Privapp allowlist files found:

- `/system/etc/permissions/privapp-permissions-platform.xml`
- `/system/etc/permissions/privapp-permissions-google-system.xml`
- `/system/etc/permissions/privapp-permissions-qti.xml`
- `/system/etc/permissions/privapp_allowlist_com.android.adservices.api.xml`
- `/system/etc/permissions/privapp_allowlist_com.android.providers.media.module.xml`
- `/system_ext/etc/permissions/privapp-permissions-google-system-ext.xml`
- `/system_ext/etc/permissions/privapp-permissions-qti-system-ext.xml`
- `/system_ext/etc/permissions/privapp-permissions-aptxals.xml`
- `/system_ext/etc/permissions/privapp-permissions-com.qualcomm.location.xml`
- `/system_ext/etc/permissions/privapp-permissions-com.quicinc.voice.activation.xml`
- additional QTI files matching `telephony_*`, `embms-noship_*`, and `wfd-system-ext-*` (individual filenames not recorded)
- `/product/etc/permissions/privapp-permissions-google-product.xml`
- `/product/etc/permissions/privapp-permissions-soundrecorder.xml`
- `/product/etc/permissions/telephony_product_privapp-permissions-qti.xml`

`/vendor/etc/permissions` has feature XMLs only, no privapp allowlist. No `vendor/etc/sysconfig`.

Gearhead is named only in `/product/etc/permissions/privapp-permissions-google-product.xml`, in a `privapp-permissions` entry around line 346. That entry does not apply to the running gearhead, because gearhead is not a priv-app. No file under `/system`, `/system_ext`, `/vendor`, or `/product` permissions or sysconfig contains `AUTOMOTIVE_PROJECTION`.

## Automotive projection permission

| Permission | Protection on this build | Source package |
|---|---|---|
| `android.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION` | `internal\|role` | `android` |
| `android.permission.TOGGLE_AUTOMOTIVE_PROJECTION` | `internal\|role` | `android` |
| `android.permission.REQUEST_COMPANION_PROFILE_COMPUTER` | `signature\|privileged` | (comparison point on this build) |

These two projection permissions are not `signature`, not `privileged`, and not `signature|privileged`.

Role `android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION` exists. `cmd role get-role-holders` returned no holders.

Platform signing does not grant the role permission. A priv-app allowlist does not grant `internal|role`. `pm grant` cannot give it out. Shizuku cannot give it out. Gearhead 10.9, installed as a normal app, still does not have it.

## Android Auto and Headunit Reloaded

Two self-mode attempts were captured on 2026-11-14. No `Force stopping` line for gearhead, ZLink, EasyConnection, `fr.sd.taada`, `gb.xxy.hr`, or the Wi-Fi launcher was in the buffer. The force-stops that were present were Play Store updates (Chrome, Play Store, Waze, and others).

### Android Auto 18.0.664264, self mode

Headunit Reloaded did connect to the developer head unit server.

| Time (logcat 11-14) | What was logged |
|---|---|
| 08:17 | gearhead `:car` process was already up, with `:shared` and `:provider` |
| 08:23:26.717 | `GH.DHUService`: network server running on port **5277** |
| 08:23:58.019 | `GH.DHUService`: `Head unit connected` |
| 08:23:58.697 | `:car` / `CarService` died, about 800 ms later |

Crash:

```text
FATAL EXCEPTION: CarService
java.lang.IllegalStateException: Failed to register vehicle with CDM
Caused by: SecurityException: Application must hold
android.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION
to associate with a device with android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION profile.
```

The projection process then died with `Failed to start connection` (`DeadObjectException`), because the car process was already gone. Headunit Reloaded's transport service threw, logged `HUR-CarPlay: manager: off`, and its process exited with signal 9 while it was the foreground app. That is crash cleanup. There is no `Force stopping gb.xxy.hr from pid …` line. ZLink never logged a kill. `MicrontekServer` only recorded which app was in front (`gearhead`, then `gb.xxy.hr`).

`GH.CrashHandler` logged version code `180664264` and `installed ver=180664264 minimum required ver=174663050`.

Android Auto 11.0.635014 is known to already contain this same CDM check. 10.x was chosen as the newest series from before that check. 11.0.635014 was not installed in this session. Anything from 11 through 18 that still registers the car with Companion Device Manager hits the same missing permission on this unit, because the role has no holder. A downgrade does not fill the role. If a 10.x log still contains `Failed to register vehicle with CDM`, the next series to try is the newest 9.x. Play Store will put 18 back unless auto-update is turned off. Whether auto-update was actually turned off was not recorded.

### Android Auto 10.9.634674-release, self mode

The developer head unit server listens on port **5277**. Headunit Reloaded listens on port **5288**. Nothing connected the two.

On 10.9, HUR does not dial 5277. Its own log, tag `HU-TransportService_Headunit Reloaded V9.0`, at 08:52:52.817 and again at 08:53:13.654:

- `Phone mode: AA 10.9.634674-release (<17.4) - using intent -> 127.0.0.1:5288`
- `AAStarter: Sending broadcast to AA with start intent`
- `Phone mode (<17.4): started WIRELESS_SOCKET_CONNECTION activity -> 127.0.0.1:5288`

HUR treats this gearhead as older than 17.4 and uses the phone-mode path: broadcast `wirelessstartup.START` so gearhead should connect to `127.0.0.1:5288`.

Both broadcasts were delivered. Gearhead's `WirelessStartupReceiver` returned in about 15 ms. It did not open a socket, did not start `WirelessStartupActivity`, and did not hit the CDM crash.

The head unit server on 5277 does not help this version. The next step, when the car is back, is to enable automatic wireless projection startup in Android Auto's developer settings (Version tapped ten times to open developer settings), then retry self mode.

## Development implications for Mix Auto

Mix Auto today is a normal app: uid 10202, installer `com.android.packageinstaller`, its own signer, no `sharedUserId`. It can keep doing what a normal app can do on this unit (launcher UI via `LauncherModeAlias`, map, media session, ordinary runtime permissions the user grants). It cannot hold signature permissions, it cannot run as uid 1000, and it cannot hold `REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION`.

### Platform test key plus `sharedUserId`

`com.miniaa.launcher` 0.3.0 already shows the result on this unit. An APK installed by the package installer, signed with the public AOSP platform test certificate above, and declaring `android:sharedUserId="android.uid.system"`, runs as uid 1000 without being a priv-app.

That reaches **signature** permissions and **signature|privileged** permissions. On this build, `REQUEST_COMPANION_PROFILE_COMPUTER` is `signature|privileged`, so that class of permission is in reach of a platform-signed uid-1000 app. It does not set the `PRIVILEGED` flag and does not grant privileged-only permissions. It does not grant `internal|role`. No root is required for this path: the certificate on the image is the public test key, and miniaa is already installed that way. There is no `su`.

This note deliberately stops there. It does not include a private key, a signing cookbook, or key material.

### Privileged permissions still need a priv-app

Privileged-only permissions are granted by a priv-app install plus a privapp-permissions allowlist. That is an image change or root. The usual `su` and Magisk paths were empty. Signature permissions still depend on the platform certificate, which is the public test cert, so that part does not require root.

The bootloader is unlocked (`flash.locked=0`, vbmeta `unlocked`, verified boot `orange`) and the build is `userdebug` with `ro.debuggable=1`. `ro.secure` is still 1. Those flags describe the image. They are not a root shell.

### The automotive projection role cannot be granted this way

`REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION` and `TOGGLE_AUTOMOTIVE_PROJECTION` are `internal|role`. The role `android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION` exists and has no holder. Platform signing leaves the role permission ungranted. A priv-app allowlist leaves `internal|role` ungranted. No allowlist file on the unit contains `AUTOMOTIVE_PROJECTION`. Gearhead, including the product allowlist entry at line 346, does not hold the permission, because that entry applies only to a priv-app and the installed gearhead is not one.

A Mix Auto build cannot become the automotive-projection companion by signing with the test key, by `sharedUserId`, or by a privapp allowlist. The CDM crash in Android Auto 11 through 18 is that missing role. Android Auto 10.9 gets past the crash and then waits on the wireless-startup developer setting instead.

## Resume later

The car was powered off on 2026-11-14 before automatic wireless projection startup was tried in Android Auto developer settings. Android Auto 10.9.634674-release is what was left installed. The head unit server was listening on 5277, Headunit Reloaded was listening on 5288, and the `wirelessstartup.START` broadcasts at 08:52:52 and 08:53:13 were delivered without a socket. Pick up there: open `DefaultSettingsActivity`, enable automatic wireless projection startup, then retry Headunit Reloaded self mode. Do not expect port 5277 to be the 10.9 path.
