**Demar VPN Android** — нативный VPN-клиент на Kotlin + Jetpack Compose

Платформа: **Android only** (Windows/Linux/macOS не в scope).

**Android minSdk: API 33** (Android 13). arm64-only APK — `app/build.gradle.kts`.

Ядро: **sing-box 1.13.14** (**ShadowTLS** + **Hysteria2**). Документация: https://sing-box.sagernet.org/

**UI:** Material 3 Expressive, **тёмная тема only**, seed `#3A6EA5` — `MaterialKolor` `TonalSpot` (parity с Flutter `ColorScheme.fromSeed`).

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

## UI (parity с demar_vpn Flutter)

| Экран | Статус |
|-------|--------|
| Home (VPN toggle) | ShadowTLS connect/disconnect через `VpnController` |
| Servers | Protocol chips + All/Dev/Free/Plus/Vip; список с `api.demar.cc` по подписке; ShadowTLS + Hy2 |
| Settings + social links | FA6 brand icons; Documents sheet → ToS/PP с GitHub Markdown |
| Account + login sheet | Email OTP + Telegram OAuth → `api.demar.cc` |
| App settings (журнал) | `AppLog` — до 512 строк, tail-trim |
| Service menu (3× tap version) | mock diagnostics |

VPN connect (ShadowTLS + Hysteria2) — **реализовано**. Auth (email OTP + Telegram) — **реализовано** (`data/auth/*`, `https://api.demar.cc`). Servers list по подписке — **реализовано** (`POST /auth/mobile/servers`). Share, egress diagnostics — **следующий этап**. Throughput (Home KB/s) — **реализовано**.

---

## Отладка

```bash
./gradlew :app:installDebug && adb shell am start -n com.demar.vpn/.MainActivity
./scripts/adb/debug.sh
./scripts/adb/auth_login_test.sh
./scripts/adb/servers_fetch_test.sh
./scripts/adb/vpn_cycle_test.sh -n 5
./scripts/adb/server_switch_test.sh -n 10
./scripts/adb/test_all_devices.sh -n 5
```

### Auth

Email OTP + TOTP (если включён) и Telegram OIDC через Custom Tabs → `demar://auth/callback`. Сессия в SharedPreferences (`AuthSessionStore`); при старте — `session/check`, при отзыве — local sign-out. Новый email: confirm «Создать новый?» → `purpose: register`. API: `POST /auth/mobile/*` + `X-Demar-Client` / UA `DemarVPN/…`.

Сессия переживает переустановку через **Android Auto Backup** (whitelist `demar_auth_session`; нужен системный Backup). Тест:

```bash
adb shell bmgr backupnow com.demar.vpn
# uninstall + reinstall same package/signing
adb shell bmgr restore com.demar.vpn
```

### Servers

После входа `POST /auth/mobile/servers` → список по подписке. Неподдерживаемые протоколы (naive/trojan) пропускаются в клиенте. Без сессии — CTA войти. Pull-to-refresh обновляет список + reachability.

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
| Тема / snackbar / соц. иконки | parity с Flutter (`DemarSnackbarHost`, copy-on-tap) |
| Libbox VPN layer | `vpn/*` |
| Domain / data / ShadowTLS+Hy2 pipeline | `domain/vpn/*`, `data/vpn/*` |
| Home connect/disconnect | `VpnController` + `MainActivity` VPN permission |
