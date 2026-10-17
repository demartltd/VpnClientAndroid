# Структура проекта

Android-only. Kotlin + Compose Material 3 Expressive (dark only).

## `app/src/main/kotlin/com/demar/vpn/`

```
app/src/main/kotlin/com/demar/vpn/
├── MainActivity.kt              # VPN permission + VpnController
├── DemarApp.kt
├── DemarAppContextHolder.kt
├── domain/
│   ├── vpn/                     # ProxyProfile, Hysteria2/Trojan/VlessReality, SingBoxConnectPlan, ApiBypassRuleSet
│   │   └── tun/                 # TunSettings, TunGroup, effectivePackages
│   └── content/                 # LegalDocument (ToS/PP URLs)
├── data/
│   ├── vpn/                     # SingBoxConfigBuilder, VpnController, RuleSetPrefetch, TunSettingsStore, TunGroupsApi
│   ├── network/                 # UplinkIpv4, RawTextFetcher, probes
│   ├── auth/                    # AuthApi, AuthSessionStore, AuthRepository
│   ├── account/                 # VpnAccountApi (/vpn/account, notify, traffic, promo); PayApi (/vpn/pay/plans, create)
│   ├── support/                 # SupportApi + cache/unread; csauth.example.invalid (channel android, sourceId=2; album ≤10 WebP)
│   ├── servers/                 # ServersApi (POST vpn.example.invalid/vpn/servers → profiles)
│   ├── update/                  # AndroidReleaseApi + AppUpdate + UpdateDownloadService (FGS dataSync) + UpdateChannelStore
│   ├── content/                 # LegalDocumentRepository (GitHub Markdown)
│   ├── logging/AppLog.kt
│   ├── locale/AppLocale.kt      # openAppLocaleSettings + currentLanguageLabel + defaultPayCurrency (ru→RUB else EUR)
│   └── device/                  # InstalledAppsQuery (TUN exclude picker)
├── ui/
│   ├── AppRoot.kt
│   ├── SingBoxVersion.kt
│   ├── theme/
│   ├── scaffold/AppScaffold.kt  # tabs + pay flow sheets (checkout / compare / result)
│   ├── components/              # SettingsActionButton, DemarSnackbar, …
│   ├── home/HomeScreen.kt       # + UpdateFab overlay in AppScaffold
│   ├── update/                  # UpdateBottomSheet, UpdateFab
│   ├── servers/                 # ServersScreen, CountryFlags, ServerBadges, ServerDetailsSheet
│   ├── settings/                # App settings, TunSettings, Documents, Service menu
│   │   └── support/             # SupportScreen, ChatPane, Composer, Bubble, Attach, UiUtils
│   ├── account/                 # AccountScreen + checkout/compare/pay result + TrafficPieChart
│   └── auth/                    # LoginBottomSheet, LoginPanel, OtpInput
└── vpn/                         # Libbox (из demar_vpn)
```

## Tests

```
app/src/test/kotlin/com/demar/vpn/VpnPipelineTest.kt
app/src/test/kotlin/com/demar/vpn/TunGroupsParseTest.kt
app/src/test/kotlin/com/demar/vpn/ServersApiParseTest.kt
app/src/test/kotlin/com/demar/vpn/VpnAccountApiTest.kt
app/src/test/kotlin/com/demar/vpn/PayApiParseTest.kt
app/src/test/kotlin/com/demar/vpn/AppUpdateTest.kt
```

## Scripts

```
scripts/
├── sync_ads_ruleset.ps1
├── adb/                         # ADB install + UI automation tests
│   ├── common.sh
│   ├── ui_parse.py
│   ├── debug.sh
│   ├── journal.sh
│   ├── auth_login_test.sh
│   ├── servers_fetch_test.sh
│   ├── vpn_cycle_test.sh
│   ├── server_switch_test.sh
│   ├── tun_apps_test.sh
│   ├── self_traffic_test.sh
│   └── test_all_devices.sh
├── libbox-build-android.py
├── generate_icons.py
├── singbox-cleaner.py
├── build_android.ps1
├── install_release.ps1
├── increment_version*.ps1
├── backup-android-signing.ps1
├── open_output_folders.ps1
└── logo_clear.png
```

## Assets / native

```
app/src/main/assets/singbox/bundled_version.txt
app/src/main/assets/rule-sets/geosite-category-ads.srs  # DustinWin ads.srs via scripts/sync_ads_ruleset.ps1 (gitignored)
app/libs/libbox.aar
```

См. также `readme.md`, `readme-core.md`, `readme-signing.md`.
