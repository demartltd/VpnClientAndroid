# Структура проекта

Android-only. Kotlin + Compose Material 3 Expressive (dark only).

## `app/src/main/kotlin/com/demar/vpn/`

```
app/src/main/kotlin/com/demar/vpn/
├── MainActivity.kt              # VPN permission + VpnController
├── DemarApp.kt
├── DemarAppContextHolder.kt
├── core/servers/TestServer.kt
├── domain/vpn/                  # NaiveProfile, SingBoxConnectPlan, …
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
│   ├── servers/ServersScreen.kt
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
├── libbox-build-android.py
├── generate_icons.py
├── singbox-cleaner.py
├── build_android.ps1
├── install_release.ps1
├── increment_version*.ps1
├── backup-android-signing.ps1
├── open_output_folders.ps1
├── push-github.ps1
├── adb_debug.sh
└── adb_journal.sh
```

## Res

```
app/src/main/res/
├── mipmap-*/ic_launcher.png
├── drawable-*/ic_launcher_*.png
├── drawable/ic_brand_*.xml
├── drawable/ic_qs_vpn_placeholder.xml
└── values/strings.xml
```

Синхронизация Libbox TAGS с `demar_vpn/scripts`.
