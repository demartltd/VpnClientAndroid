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

| Экран | Статус |
|-------|--------|
| Home (VPN toggle) | Connect/disconnect через `VpnController` (HY2 / Trojan / VLESS / OpenFlux) |
| Servers | Refresh / фильтры 1:1 (sheet: tier + protocol multi-select); бейджи HY2/TRJ/VLESS; OpenFlux → White List/SOCKS; список с `vpn.example.invalid` по подписке |
| Settings + social links | FA6 brand icons; Documents sheet → ToS/PP; Support sheet → CS tickets (`csauth.example.invalid`, channel `android`, `sourceId=2`, альбом до 10 фото WebP) |
| Account + login sheet | Header (icon + name + ID); Subscription card; SettingsActionButtons → notify/traffic bottom sheets; family stub («Скоро»); Email OTP + Telegram OAuth → `auth.example.invalid`; VPN profile/notify/traffic → `vpn.example.invalid`; Buy/Renew/Increase → RollyPay checkout (`/vpn/pay/*`, Custom Tabs, `demarvpn://pay/ok\|fail`) |
| App settings (журнал + язык + beta + TUN) | `AppLog` — до 512 строк; язык → системный `ACTION_APP_LOCALE_SETTINGS` (EN/RU/TR/UK/UZ/zh-Hans/zh-Hant/KO/JA/ES/DE/FR/HI, default = язык телефона); бета-канал; update sheet на каждый active + APK download в FGS; **TUN**: Ad block + app/site bypass; geo bypass = bundled geosite/geoip `.srs` (no runtime download) |
| Service menu (3× tap version) | about + latest version API + diagnostics |

VPN connect (Hysteria2 + Trojan + VLESS Reality + OpenFlux SOCKS sidecar) — **реализовано**. Auth (email OTP + Telegram) — **реализовано** (`data/auth/*`, `https://auth.example.invalid`). Account VPN profile / notify / monthly traffic — **реализовано** (`data/account/*`, `POST https://vpn.example.invalid/vpn/{account,notify,traffic}`). Subscription pay (RollyPay web checkout) — **реализовано** (`PayApi` GET `/vpn/pay/plans`, POST `/vpn/pay/create`; promo apply `/vpn/promo/apply`; return `demarvpn://pay/ok|fail` + session refresh). Servers list по подписке — **реализовано** (`POST https://vpn.example.invalid/vpn/servers`). Share, egress diagnostics — **следующий этап**. Throughput (Home KB/s) — **реализовано**.

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


---

## Статус (2026-06)

| Область | Статус |
|---------|--------|
| Compose UI (4 tabs + push + login) | реализовано |
| Тема / snackbar / соц. иконки / i18n EN+RU+TR+UK+UZ+zh-Hans+zh-Hant+KO+JA+ES+DE+FR+HI | per-app language через `localeConfig`; checkout default currency: RU→RUB, иначе EUR |
| Libbox VPN layer | `vpn/*` |
| Domain / data / VPN pipeline | `domain/vpn/*`, `data/vpn/*` (HY2 + Trojan + VLESS + OpenFlux) |
| Home connect/disconnect | `VpnController` + `MainActivity` VPN permission |
| Auto-update (APK + SHA-256) | `data/update/*`, Home FAB + UpdateBottomSheet; beta channel via App settings → `/android/beta` |
