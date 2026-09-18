# Ядро VPN: Sing-box (Hysteria2) — Demar Proxy Android

Целевая версия core: **sing-box 1.14.1**. Фактическая — в `app/src/main/assets/singbox/bundled_version.txt` после `libbox-build-android.py`.

Документация: https://sing-box.sagernet.org/

- [Hysteria2 outbound](https://sing-box.sagernet.org/configuration/outbound/hysteria2/)
- [DNS rule action](https://sing-box.sagernet.org/configuration/dns/rule_action/) (since 1.11.0)
- [Dial Fields](https://sing-box.sagernet.org/configuration/shared/dial/) — `domain_resolver`
- [HTTP Client](https://sing-box.sagernet.org/configuration/shared/http-client/) — remote rule-set download (since 1.14.0)

Платформа: **Android only** (нативный Kotlin).

---

## Pipeline

```
ServerProfile → SingBoxConnectPlan → [resolveUplinkIpv4s] → SingBoxConfigBuilder → config.json
```

`resolveUplinkIpv4s` — перед connect на Android. Расширяемость: новый profile + `SingBoxConnectPlan.foo()` + factory в `ServerProfile`.

### Добавление протокола

1. Profile struct (`FooProfile`) в `domain/vpn/`
2. `SingBoxConnectPlan.foo(profile)` — outbound + tun overrides
3. `ServerProtocol` + factory / `connectPlan`
4. Test / API seed (не hardcode в runtime)
5. При новом outbound — обновить `TAGS` в `scripts/libbox-build-android.py` (если нужен build tag)

---

## Hysteria2

| App field | sing-box key |
|-----------|--------------|
| `server` | `server` |
| `port` | `server_port` |
| `password` | `password` |
| `tlsServerName` | `tls.server_name` |
| `alpn` | `tls.alpn` (default `["h3"]`) |
| `obfsPassword` | `obfs.type=salamander` + `obfs.password` (если задан) |
| `upMbps` / `downMbps` | `up_mbps` / `down_mbps` | no (empty → BBR) |
| `displayName` | UI only |

### Outbound defaults (`SingBoxConnectPlan.hysteria2`)

| Поле | Значение |
|------|----------|
| `tls` | enabled + `server_name` + `alpn` |
| `domain_resolver` | `dns-direct`, `ipv4_only` |
| `up_mbps` / `down_mbps` | omitted → BBR |

Uplink preflight: DNS-only (QUIC/UDP).

---

## DNS (sing-box 1.11+ rule actions)

| Tag | Тип | Назначение |
|-----|-----|------------|
| `dns-direct` | `type: local` | bootstrap, domain resolver |
| `dns-remote` | HTTPS `1.1.1.1` | DoH через proxy |

Rules: PTR → reject; bootstrap domains → `dns-direct`. Глобально: `final: dns-remote`, `strategy: ipv4_only`, `optimistic: true` (1.14+).

`experimental.cache_file`: всегда `enabled` + `store_dns` — DNS-кэш переживает reconnect.

Hysteria2: Chrome QUIC parrot **включён по умолчанию** в 1.14 (поле в JSON не нужно). Серверы с Ed25519-сертификатом сломают handshake → тогда `disable_chrome_parrot` (сейчас не используем).

---

## Route и TUN

- TUN: `gvisor`, `strict_route`, `172.19.0.1/30`, split `0.0.0.0/1` + `128.0.0.0/1`
- Route: sniff → hijack-dns → QUIC reject → private direct → **API rule-sets** → IPv6 reject → proxy
- `override_android_vpn: true`
- `default_domain_resolver`: `dns-direct`, `ipv4_only`

### API geo-bypass (из `clientConfigJson`)

Servermaster кладёт remote `route.rule_set` (RU/IR/CN) в `clientConfigJson`. Клиент парсит tags/URLs в `ApiBypassRuleSet` и собирает Android-конфиг:

- geosite → DNS `dns-direct` + route `direct`
- geoip → route `direct`
- **не** эмитить TUN `route_exclude_address_set` (VpnService / DeadSystemException)
- download: top-level `http_clients` (`rs-dl` → `detour: proxy`) + `route.default_http_client` (не `download_detour`)
- cold start: `RuleSetPrefetch` скачивает `.srs` до TUN → remote `initial_path` (sing-box 1.14); фон — через `rs-dl`

### TunSettings (локально)

Модель: `domain/vpn/tun/TunSettings` — `bypassPackages` + `disabledGroupIds` + `rulesEnabled`.

- `com.demar.vpn` всегда в TUN `exclude_package` + `VpnService.addDisallowedApplication` (control-plane мимо TUN)
- Эффективный exclude = ручные пакеты ∪ пакеты **включённых** preset-групп (если `rulesEnabled`; на Free master всегда ON)
- Preset-группы: `GET https://fastapi.demar.cc/tun` → `android[]` (`TunGroupsApi`); кэш prefs `tun_groups_json`
- Free: все группы forced ON (Switch locked); кастомные apps недоступны; Plus/Vip — master Switch + тоггл группы + кастом
- UI: App settings → TUN → (paid) master rules → ручные apps → системные группы
- Persist: SharedPreferences `bypass_packages`, `tun_disabled_groups`, `tun_rules_enabled`

---

## Uplink bypass

| Uplink `server` | Механизм |
|-----------------|----------|
| Literal IPv4 | `route_exclude_address` в TUN |
| Hostname | `resolveUplinkIpv4s` → `withExtraDirectIps` перед build |

---

## Diag inbound

Mixed `127.0.0.1:10808` (`SingBoxTags.diagInbound`):

- Post-connect: `VpnTunnelProbe` — HTTP сразу после diag TCP (`generate_204` → CF trace, timeout 1.5s, adaptive backoff)
- Egress: `NetworkDiagnostics` (roadmap)
- Native snapshot: `getEgressRouteSnapshot` (`osVpnActive`, `demarConnected`, `diagProxyReady`)

Clash API `127.0.0.1:19090` (`experimental.clash_api`) — Home throughput via `GET /traffic`.

---

## Сборка Libbox

```bash
python3 scripts/libbox-build-android.py
./gradlew :app:assembleRelease
```

Очистка: `python3 scripts/singbox-cleaner.py -y`

### Build tags

```
with_gvisor,with_quic,with_clash_api,badlinkname,tfogo_checklinkname0
```

- `with_quic` — Hysteria2 / QUIC

AAR: `app/libs/libbox.aar` (arm64). Geo-базы в APK не bundle'ятся.

---

## Kotlin layout

| Путь | Роль |
|------|------|
| `domain/vpn/*` | `Hysteria2Profile`, `SingBoxConnectPlan`, `ApiBypassRuleSet` |
| `data/vpn/SingBoxConfigBuilder.kt` | TUN + DNS + route JSON |
| `data/vpn/VpnController.kt` | Connect pipeline, UI bridge |
| `data/network/UplinkIpv4.kt` | uplink resolve |
| `vpn/SingBoxCore.kt` | Libbox in-process |
| `vpn/DemarVpnService.kt` | `VpnService` + FGS |
| `vpn/DemarLibboxPlatform.kt` | `openTun`, `DefaultInterfaceMonitor` |
| `vpn/VpnEventStream.kt` | State events для Compose UI |

Синхронизировать `TAGS` / pipeline с [demar_vpn/readme-core.md](../demar_vpn/readme-core.md).
