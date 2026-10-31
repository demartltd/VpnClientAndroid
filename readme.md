**Demar Proxy Android** — нативный VPN-клиент на Kotlin + Jetpack Compose

Платформа: **Android only**.

**Android minSdk: API 33** (Android 13). arm64-only APK — `app/build.gradle.kts`.

Ядро: **sing-box 1.14.1** (**Hysteria2** + **Trojan** + **VLESS Reality** + **OpenFlux** SOCKS sidecar). Документация: https://sing-box.sagernet.org/

**UI:** Material 3 Expressive, **тёмная тема only**, seed `#3A6EA5` — `MaterialKolor` `TonalSpot`.

Структура: `filestree.md` · ядро: `readme-core.md` · signing: `readme-signing.md`

---

## Первая сборка

Локально (не в git): `libbox.aar`, OpenFlux sidecar arm64, rule-sets, launcher icons. Signing — [`readme-signing.md`](readme-signing.md).

```bash
./gradlew :app:assembleDebug
# release:
./gradlew :app:assembleRelease
```

Версия: `gradle.properties`.

---

## UI

| Экран | |
|-------|--|
| Home (VPN toggle) | Connect/disconnect через `VpnController` (HY2 / Trojan / VLESS / OpenFlux); throughput KB/s |
| Servers | Refresh / фильтры (tier + protocol); бейджи HY2/TRJ/VLESS; OpenFlux → White List/SOCKS; список с `vpn.example.invalid` |
| Settings + social links | FA6 brand icons; Documents → ToS/PP; Support → CS tickets (`csauth.example.invalid`) |
| Account + login sheet | OTP + Telegram → `auth.example.invalid`; plan/traffic/notify → `vpn.example.invalid`; checkout RollyPay (`/vpn/pay/*`) |
| App settings | журнал, язык, beta/update, TUN (ad block + app/site bypass, bundled geosite/geoip) |
| Service menu (3× tap version) | about + diagnostics |

---

## Отладка

```bash
./gradlew :app:installDebug && adb shell am start -n com.demar.vpn/.MainActivity
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

После входа `POST https://vpn.example.invalid/vpn/servers` → список по подписке. Неподдерживаемые протоколы пропускаются в клиенте. Без сессии — CTA войти. Pull-to-refresh обновляет список + reachability.

### Журнал (AppLog)

UI-журнал («Настройки → Настройки приложения») и файл на устройстве — один источник `AppLog` (до 512 строк, tail-trim). Приложение зеркалирует журнал во внешнее хранилище при каждой записи — доступно на **debug и release** без `run-as`.

```bash
adb pull /sdcard/Android/data/com.demar.vpn/files/demar/demar_app.log
```
