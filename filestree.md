# Структура проекта

Android-only. Kotlin + Compose Material 3 Expressive (dark only).

## `app/src/main/kotlin/com/demar/vpn/`

```
app/src/main/kotlin/com/demar/vpn/
├── MainActivity.kt              # VPN permission + VpnController
├── DemarApp.kt
├── DemarAppContextHolder.kt
├── core/servers/TestServer.kt
├── domain/vpn/                  # NaiveProfile, Hysteria2Profile, SingBoxConnectPlan, …
├── data/
│   ├── vpn/                     # SingBoxConfigBuilder, VpnController
│   ├── network/UplinkIpv4.kt
│   ├── logging/AppLog.kt
│   └── device/
├── ui/
│   ├── AppRoot.kt
│   ├── SingBoxVersion.kt
│   ├── mock/UiMocks.kt
│   ├── theme/
│   ├── scaffold/AppScaffold.kt
│   ├── components/
│   ├── home/HomeScreen.kt
│   ├── servers/ServersScreen.kt  # Protocol + tier chips above list
│   ├── settings/
│   ├── account/
│   └── auth/
└── vpn/                         # Libbox (из demar_vpn)
```

## Tests

```
app/src/test/kotlin/com/demar/vpn/VpnPipelineTest.kt
```

## Scripts

```
scripts/
├── adb/                         # ADB install + UI automation tests
│   ├── common.sh
│   ├── ui_parse.py
│   ├── debug.sh
│   ├── journal.sh
│   ├── vpn_cycle_test.sh
│   ├── server_switch_test.sh
│   └── test_all_devices.sh
├── libbox-build-android.py
├── generate_icons.py
├── singbox-cleaner.py
├── build_android.ps1
├── install_release.ps1
├── increment_version*.ps1
├── backup-android-signing.ps1
├── open_output_folders.ps1
├── push-github.ps1
```

## Res

```
app/src/main/res/
├── mipmap-*/ic_launcher.png
├── drawable-*/ic_launcher_*.png
├── drawable/ic_brand_*.xml
└── values/strings.xml
```

Синхронизация Libbox TAGS с `demar_vpn/scripts`.
