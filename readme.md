**Demar Proxy Android** — нативный VPN-клиент на Kotlin + Jetpack Compose

Платформа: **Android only**.

**Android minSdk: API 33** (Android 13). arm64-only APK — `app/build.gradle.kts`.

Ядро: **sing-box 1.13.14** (**Hysteria2**). Документация: https://sing-box.sagernet.org/

**UI:** Material 3 Expressive, **тёмная тема only**, seed `#3A6EA5` — `MaterialKolor` `TonalSpot`.

Структура: `filestree.md` · ядро: `readme-core.md` · signing: `readme-signing.md`

---

## Первая сборка

```bash
python3 scripts/libbox-build-android.py   # один раз (нужен go)
python3 scripts/generate_icons.py         # launcher icons из scripts/logo_clear.png
# key.properties — см. readme-signing.md
pwsh scripts/build_android.ps1
```

Установка release на USB: `pwsh scripts/install_release.ps1`

Версия: `gradle.properties`. Bump: `pwsh scripts/increment_version_auto.ps1`

---

## UI

| Экран | Статус |
|-------|--------|
| Home (VPN toggle) | Hysteria2 connect/disconnect через `VpnController` |
| Servers | SegmentBar: Hysteria2 + All/Free/Plus/Vip (+ Dev/Tester если есть в списке); список с `fastapi.demar.cc` по подписке |
| Settings + social links | FA6 brand icons; Documents sheet → ToS/PP; Support sheet → CS tickets (`csapi.demar.cc`, channel `android`, `sourceId=2`, альбом до 10 фото WebP) |
| Account + login sheet | Header (icon + name + ID); Subscription card; SettingsActionButtons → notify/traffic bottom sheets; family stub («Скоро»); Email OTP + Telegram OAuth → `api.demar.cc`; VPN profile/notify/traffic → `fastapi.demar.cc`; Buy/Renew/Increase → RollyPay checkout (`/vpn/pay/*`, Custom Tabs, `demarvpn://pay/ok\|fail`) |
| App settings (журнал + язык + beta) | `AppLog` — до 512 строк; язык → системный `ACTION_APP_LOCALE_SETTINGS` (EN/RU, default = язык телефона); бета-канал; update sheet на каждый active + APK download в FGS (`UpdateDownloadService`) с progress notification |
| Service menu (3× tap version) | about + latest version API + diagnostics |

VPN connect (Hysteria2) — **реализовано**. Auth (email OTP + Telegram) — **реализовано** (`data/auth/*`, `https://api.demar.cc`). Account VPN profile / notify / monthly traffic — **реализовано** (`data/account/*`, `POST https://fastapi.demar.cc/vpn/{account,notify,traffic}`). Subscription pay (RollyPay web checkout) — **реализовано** (`PayApi` GET `/vpn/pay/plans`, POST `/vpn/pay/create`; promo apply `/vpn/promo/apply`; return `demarvpn://pay/ok|fail` + session refresh). Servers list по подписке — **реализовано** (`POST https://fastapi.demar.cc/vpn/servers`). Share, egress diagnostics — **следующий этап**. Throughput (Home KB/s) — **реализовано**.

---

## Отладка

```bash
./gradlew :app:installDebug && adb shell am start -n com.demar.vpn/.MainActivity
./scripts/adb/debug.sh
./scripts/adb/auth_login_test.sh
./scripts/adb/servers_fetch_test.sh
./scripts/adb/vpn_cycle_test.sh -n 5
./scripts/adb/server_switch_test.sh -n 10
./scripts/adb/tun_apps_test.sh
./scripts/adb/self_traffic_test.sh
./scripts/adb/test_all_devices.sh -n 5
```

### Auth

Email OTP + TOTP (если включён) и Telegram OIDC через Custom Tabs → `demarvpn://auth/callback`. Сессия в SharedPreferences (`AuthSessionStore`); при старте — `session/check`, при отзыве — local sign-out. Выход: `POST /auth/mobile/session/logout` + stop VPN + очистка `vpn_control_state` (секреты Hy2). Новый email: confirm «Создать новый?» → `purpose: register`. API: `POST /auth/mobile/*` + `X-Demar-Client` / UA `DemarVPN/…`. Store review: `test@test.com` → локальная сессия без OTP; список серверов с API (без паролей); connect блокируется snackbar’ом.

Сессия переживает переустановку через **Android Auto Backup** (whitelist `demar_auth_session`; нужен системный Backup). Тест:

```bash
adb shell bmgr backupnow com.demar.vpn
# uninstall + reinstall same package/signing
adb shell bmgr restore com.demar.vpn
```

### Servers

После входа `POST https://fastapi.demar.cc/vpn/servers` → список по подписке. Неподдерживаемые протоколы пропускаются в клиенте. Без сессии — CTA войти. Pull-to-refresh обновляет список + reachability.

### Журнал (AppLog)

UI-журнал («Настройки → Настройки приложения») и ADB-выгрузка — один источник `AppLog` (до 512 строк, tail-trim). Приложение зеркалирует журнал во внешнее хранилище при каждой записи — доступно на **debug и release** без `run-as`.

```bash
# Весь журнал в терминал
./scripts/adb/journal.sh

# Сохранить в файл
./scripts/adb/journal.sh -o journal.txt
```

Путь на устройстве (ручной `adb pull`):

```
/sdcard/Android/data/com.demar.vpn/files/demar/demar_app.log
```


---

## Статус (2026-06)

| Область | Статус |
|---------|--------|
| Compose UI (4 tabs + push + login) | реализовано |
| Тема / snackbar / соц. иконки / i18n EN+RU | per-app language через `localeConfig` |
| Libbox VPN layer | `vpn/*` |
| Domain / data / Hysteria2 pipeline | `domain/vpn/*`, `data/vpn/*` |
| Home connect/disconnect | `VpnController` + `MainActivity` VPN permission |
| Auto-update (APK + SHA-256) | `data/update/*`, Home FAB + UpdateBottomSheet; beta channel via App settings → `/android/beta` |
