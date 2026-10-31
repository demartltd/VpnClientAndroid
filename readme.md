**Demar Proxy Android** — native VPN client in Kotlin + Jetpack Compose

Platform: **Android only**.

**Android minSdk: API 33** (Android 13). arm64-only APK — `app/build.gradle.kts`.

Core: **sing-box 1.14.1** (**Hysteria2** + **Trojan** + **VLESS Reality** + **OpenFlux** SOCKS sidecar). Docs: https://sing-box.sagernet.org/

**UI:** Material 3 Expressive, **dark theme only**, seed `#3A6EA5` — `MaterialKolor` `TonalSpot`.

Layout: `filestree.md` · core: `readme-core.md` · signing: `readme-signing.md`

---

## First build

Prepare locally (not in git): `libbox.aar`, OpenFlux arm64 sidecar, rule-sets, launcher icons. Signing — [`readme-signing.md`](readme-signing.md).

```bash
./gradlew :app:assembleDebug
# release:
./gradlew :app:assembleRelease
```

Version: `gradle.properties`.

---

## UI

| Screen | |
|-------|--|
| Home (VPN toggle) | Connect/disconnect via `VpnController` (HY2 / Trojan / VLESS / OpenFlux); throughput KB/s |
| Servers | Refresh / filters (tier + protocol); HY2/TRJ/VLESS badges; OpenFlux → White List/SOCKS; list from VPN API |
| Settings + social links | FA6 brand icons; Documents → ToS/PP; Support → CS tickets (support API) |
| Account + login sheet | OTP + Telegram → auth API; plan/traffic/notify → VPN API; RollyPay checkout (`/vpn/pay/*`) |
| App settings | journal, language, beta/update, TUN (ad block + app/site bypass, bundled geosite/geoip) |
| Service menu (3× tap version) | about + diagnostics |

---

## Debug

```bash
./gradlew :app:installDebug && adb shell am start -n com.demar.vpn/.MainActivity
```

### Auth

Email OTP + TOTP (when enabled) and Telegram OIDC via Custom Tabs → `demarvpn://auth/callback`. Session in SharedPreferences (`AuthSessionStore`); on launch — `session/check`; on revoke — local sign-out. Sign-out: `POST /auth/mobile/session/logout` + stop VPN + clear `vpn_control_state` (Hy2 secrets). New email: confirm «Create new?» → `purpose: register`. API: `POST /auth/mobile/*` + `X-Demar-Client` / UA `DemarVPN/…`. Store review: `test@test.com` → local session without OTP; server list from API (no passwords); connect blocked with snackbar.

Session survives reinstall via **Android Auto Backup** (whitelist `demar_auth_session`; system Backup required). Test:

```bash
adb shell bmgr backupnow com.demar.vpn
# uninstall + reinstall same package/signing
adb shell bmgr restore com.demar.vpn
```

### Servers

After sign-in `POST /vpn/servers` → subscription list. Unsupported protocols are skipped. Without a session — sign-in CTA. Pull-to-refresh reloads list + reachability.

### Journal (AppLog)

UI journal (**Settings → App settings**) and on-device file share one `AppLog` source (up to 512 lines, tail-trim). The app mirrors the journal to external storage on every write — available on **debug and release** without `run-as`.

```bash
adb pull /sdcard/Android/data/com.demar.vpn/files/demar/demar_app.log
```
