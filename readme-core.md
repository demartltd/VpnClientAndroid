# Ядро VPN: Sing-box (Hysteria2 + Trojan + VLESS Reality) — Demar Proxy Android

Целевая версия core: **sing-box 1.14.1**. Фактическая — в `app/src/main/assets/singbox/bundled_version.txt` после `libbox-build-android.py`.

Документация: https://sing-box.sagernet.org/

- [Hysteria2 outbound](https://sing-box.sagernet.org/configuration/outbound/hysteria2/)
- [Trojan outbound](https://sing-box.sagernet.org/configuration/outbound/trojan/)
- [VLESS outbound](https://sing-box.sagernet.org/configuration/outbound/vless/)
- [TLS / Reality](https://sing-box.sagernet.org/configuration/shared/tls/)
- [DNS rule action](https://sing-box.sagernet.org/configuration/dns/rule_action/) (since 1.11.0)
- [Dial Fields](https://sing-box.sagernet.org/configuration/shared/dial/) — `domain_resolver`
- [HTTP Client](https://sing-box.sagernet.org/configuration/shared/http-client/) — remote rule-set download (since 1.14.0)

Платформа: **Android only** (нативный Kotlin).

---

## Pipeline

```
ServerProfile → SingBoxConnectPlan → [resolveUplinkIpv4s] → SingBoxConfigBuilder → config.json
```

`resolveUplinkIpv4s` — перед connect на Android. Расширяемость: новый `ProxyProfile` + `toConnectPlan` + `ServerProtocol` + parse ветка.

### Добавление протокола

1. `FooProfile : ProxyProfile` — `protocol`, `preferResolvedDial`, `toConnectPlan` → `SingBoxConnectPlan.foo()`
2. `ServerProtocol` entry (`apiKey` / label)
3. Parse/preview ветка в `ServersApi` (+ `ServerProfile.fromTag`)
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

## Trojan

API `protocol`: `trojan`. Outbound type: `trojan`. TCP + TLS (ACME на ноде) + multiplex.

| App field | sing-box key |
|-----------|--------------|
| `server` | `server` |
| `port` | `server_port` |
| `password` | `password` |
| `tlsServerName` | `tls.server_name` |

### Outbound defaults (`SingBoxConnectPlan.trojan`)

| Поле | Значение |
|------|----------|
| `tls` | enabled + `server_name` |
| `multiplex` | `{ enabled: true }` |
| `domain_resolver` | `dns-direct`, `ipv4_only` |
| dial | `preferResolvedDial` → `withResolvedUplink` после resolve |

Bootstrap DNS/route: hostname сервера + TLS SNI (обычно совпадают).

---

## VLESS Reality

Стек: **VLESS + TCP + xtls-rprx-vision + REALITY** (не XHTTP — в stock sing-box 1.14 нет).

API `protocol`: `vless_reality`. Outbound type: `vless`.

| App field | sing-box key |
|-----------|--------------|
| `server` | `server` (VPS domain) |
| `port` | `server_port` |
| `uuid` | `uuid` |
| `flow` | `flow` (default `xtls-rprx-vision`) |
| `tlsServerName` | `tls.server_name` (handshake host, often `www.cloudflare.com`; avoid `www.microsoft.com` — Reality EOF) |
| `publicKey` | `tls.reality.public_key` |
| `shortId` | `tls.reality.short_id` |

### Outbound defaults (`SingBoxConnectPlan.vlessReality`)

| Поле | Значение |
|------|----------|
| `tls.reality` | enabled + public_key + short_id |
| `tls.utls` | enabled + fingerprint `chrome` (обязателен для Reality в sing-box) |
| `domain_resolver` | `dns-direct`, `ipv4_only` |
| `network` / `multiplex` | **не эмитятся** (omit = tcp+udp; Vision ∌ mux) |
| dial | `preferResolvedDial` → `withResolvedUplink` после resolve |

Uplink host = VPS `server`, не SNI. Bootstrap DNS/route: только uplink hostname (без handshake SNI).

**Нода:** inbound `tls.server_name` обязан совпадать с handshake host — без него `reality verification failed`. После правки шаблона — перенастроить ноду из панели.

---

## DNS (sing-box 1.11+ rule actions)

| Tag | Тип | Назначение |
|-----|-----|------------|
| `dns-direct` | `type: local` | bootstrap, domain resolver |
| `dns-remote` | HTTPS `1.1.1.1` | DoH через proxy |

Rules: PTR → reject; bootstrap → `dns-direct`; **Ad block** reject (`geosite-category-ads`) **до** site-bypass / geosite (иначе `yandex.ru` пропускает `an.yandex.ru`); site bypass → `dns-direct`; geosite → `dns-direct`. `final: dns-remote`, `ipv4_only`, `optimistic`.

`experimental.cache_file`: всегда `enabled` + `store_dns` — DNS-кэш переживает reconnect.

Hysteria2: Chrome QUIC parrot **включён по умолчанию** в 1.14 (поле в JSON не нужно). Серверы с Ed25519-сертификатом сломают handshake → тогда `disable_chrome_parrot` (сейчас не используем).

---

## Route и TUN

- TUN: `gvisor`, `strict_route`, `172.19.0.1/30`, MTU ≤1400, split `0.0.0.0/1` + `128.0.0.0/1`
- Route: sniff → hijack-dns → QUIC reject → private direct → **API rule-sets** → IPv6 reject → proxy
- Bootstrap / bypass hosts: `domain_suffix` → DIRECT (DNS + route)
- OpenFlux: SOCKS sidecar + carrier suffix anti-loop (`boards.yandex.ru`…); restart on default-network change / dead SOCKS
- `override_android_vpn: true`
- `default_domain_resolver`: `dns-direct`, `ipv4_only`

### API geo-bypass (из `clientConfigJson`)

Servermaster кладёт `route.rule_set` (RU/IR/CN) в `clientConfigJson`. Клиент парсит tags в `ApiBypassRuleSet` (URL ignored). Connect: `RuleSets.prepare` → только существующие bundled `.srs`; все `type: local`:

- geosite → DNS `dns-direct` + route `direct`
- geoip → route `direct`
- ads → DNS+route `reject` (отдельный тоггл, не в `plan.bypassRuleSets`)
- **не** эмитить TUN `route_exclude_address_set` (VpnService / DeadSystemException)
- без runtime HTTP: нет `http_clients` / `rs-dl` / `initial_path`

### TunSettings (локально)

Модель: `domain/vpn/tun/TunSettings` — `bypassPackages` + group overrides + `rulesEnabled` + `adBlockEnabled`.

- `com.demar.vpn` всегда в TUN `exclude_package` + `VpnService.addDisallowedApplication` (control-plane мимо TUN)
- Эффективный exclude = ручные пакеты ∪ пакеты **включённых** preset-групп (если `rulesEnabled`; на Free master всегда ON)
- Группы с `isWhiteList=true`: в обычном режиме — в exclude (мимо TUN); на OpenFlux (WL) — **не** в exclude (через VPN). Классический bypass (`isWhiteList=false`) — всегда мимо TUN
- Preset-группы: `GET https://fastapi.demar.cc/tun` → `android[]` (`TunGroupsApi`); кэш prefs `tun_groups_json`
- Free: все группы forced ON (Switch locked); кастомные apps недоступны; Plus/Vip — master Switch + тоггл группы + кастом
- UI: App settings → TUN → Ad block → (paid) master rules → ручные apps → системные группы
- Persist: SharedPreferences `bypass_packages`, `tun_*_overrides`, `tun_rules_enabled`, `tun_ad_block_enabled`

### Ad block (DNS + route)

Отдельный тоггл в TUN settings. Free: всегда off. Plus/VIP: default **on**, иначе сохранённый выбор. При on: bundled ads rule-set (`type: local`, tag `geosite-category-ads`) + DNS и route `action: reject`. Build: `scripts/sync_rulesets.ps1` → ads + geosite/geoip RU/IR/CN → `app/src/main/assets/rule-sets/` (gitignore; вызывается из `build_android.ps1` / `install_release.ps1` / `release_to_github.ps1`). Gradle `verifyRuleSets` на `preBuild`. Runtime — `RuleSets.prepare` (copy assets → `filesDir`), без сети. Path-реклама на first-party хосте (напр. `yandex.ru/ads/...`) DNS не режет. Ads **не** в `plan.bypassRuleSets`. Применение на следующем Connect.

---

## Uplink bypass

| Uplink `server` | Механизм |
|-----------------|----------|
| Literal IPv4 | `route_exclude_address` в TUN |
| Hostname | `resolveUplinkIpv4s` → `withExtraDirectIps` перед build |

---

## List ping (Servers)

Параллельный uplink RTT до connect (`UplinkProbe` → `InternetProbe`, ≤2 s × ≤3, concurrency 8):

- Trojan / VLESS: TCP + TLS
- HY2: TCP + QUIC Version Negotiation (UDP); при UDP-filter — TCP fallback в list metric
- OpenFlux: `—` (local SOCKS)
- Триггеры: fetch servers / pull-to-refresh / первый вход на tab; уход с tab **не** отменяет batch

Не путать с post-connect tunnel probe и с connect-time `resolveUplinkIpv4s` (DNS bypass).

## Diag inbound

Mixed `127.0.0.1:10808` (`SingBoxTags.diagInbound`):

- Post-connect: `VpnTunnelProbe` — HTTP через diag mixed → **proxy**; успех = **OR** (первый ответ): Google `generate_204` → CF domain → literal `1.1.1.1` (timeout 1.5s). Probe-хосты только в DNS bootstrap, не в route DIRECT. Правило `diag → proxy` стоит до domain DIRECT.
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
with_gvisor,with_quic,with_utls,with_clash_api,badlinkname,tfogo_checklinkname0
```

- `with_quic` — Hysteria2 / QUIC
- `with_utls` — Reality (обязателен для VLESS Reality)

AAR: `app/libs/libbox.aar` (arm64). Geo-базы в APK не bundle'ятся.

---

## Kotlin layout

| Путь | Роль |
|------|------|
| `domain/vpn/*` | `ProxyProfile`, `Hysteria2Profile`, `TrojanProfile`, `VlessRealityProfile`, `SingBoxConnectPlan`, `ApiBypassRuleSet` |
| `data/vpn/SingBoxConfigBuilder.kt` | TUN + DNS + route JSON (`type: local` `.srs`) |
| `data/vpn/RuleSets.kt` | Bundled `.srs` copy assets → filesDir, `prepare` |
| `data/vpn/VpnController.kt` | Connect pipeline, UI bridge |
| `data/network/UplinkIpv4.kt` | uplink resolve |
| `vpn/SingBoxCore.kt` | Libbox in-process |
| `vpn/DemarVpnService.kt` | `VpnService` + FGS |
| `vpn/DemarLibboxPlatform.kt` | `openTun`, `DefaultInterfaceMonitor` |
| `vpn/VpnEventStream.kt` | State events для Compose UI |

Синхронизировать `TAGS` / pipeline с [demar_vpn/readme-core.md](../demar_vpn/readme-core.md).
