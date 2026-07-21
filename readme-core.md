# Ядро VPN: Sing-box (ShadowTLS + Hysteria2) — Demar VPN Android

Целевая версия core: **sing-box 1.13.14**. Фактическая — в `app/src/main/assets/singbox/bundled_version.txt` после `libbox-build-android.py`.

Документация: https://sing-box.sagernet.org/

- [Shadowsocks outbound](https://sing-box.sagernet.org/configuration/outbound/shadowsocks/)
- [ShadowTLS outbound](https://sing-box.sagernet.org/configuration/outbound/shadowtls/)
- [Hysteria2 outbound](https://sing-box.sagernet.org/configuration/outbound/hysteria2/)
- [DNS rule action](https://sing-box.sagernet.org/configuration/dns/rule_action/) (since 1.11.0)
- [Dial Fields](https://sing-box.sagernet.org/configuration/shared/dial/) — `domain_resolver`

Платформа: **Android only** (нативный Kotlin).

---

## Pipeline

```
ServerProfile → SingBoxConnectPlan → [resolveUplinkIpv4s] → SingBoxConfigBuilder → config.json
```

`resolveUplinkIpv4s` — перед connect на Android. Расширяемость: новый profile + `SingBoxConnectPlan.foo()` + `ServerProfileKind.foo`.

### Добавление протокола

1. Profile struct (`FooProfile`) в `domain/vpn/`
2. `SingBoxConnectPlan.foo(profile)` — outbound + tun overrides (+ `dialOutbounds` при цепочке)
3. `ServerProfileKind.foo` + switch в `connectPlan`
4. Test / API seed (не hardcode в runtime)
5. При новом outbound — обновить `TAGS` в `scripts/libbox-build-android.py` (если нужен build tag)

---

## ShadowTLS (Shadowsocks over ShadowTLS v3)

Цепочка: `shadowsocks` (`tag=proxy`, `detour=shadowtls-out`, multiplex) → `shadowtls` → `direct`.

| App field | sing-box key |
|-----------|--------------|
| `server` / `port` | ShadowTLS `server` / `server_port` |
| `password` | ShadowTLS `password` (v2/v3) |
| `tlsServerName` | ShadowTLS `tls.server_name` |
| `version` | ShadowTLS `version` (default `3`) |
| `ssMethod` | Shadowsocks `method` |
| `ssPassword` | Shadowsocks `password` |
| `displayName` | UI only |

### Outbound defaults (`SingBoxConnectPlan.shadowTls`)

| Поле | Значение |
|------|----------|
| SS `multiplex` | `enabled: true` |
| SS `detour` | `shadowtls-out` |
| ShadowTLS `tls` | enabled + `server_name` |
| ShadowTLS `domain_resolver` | `dns-direct`, `ipv4_only` |

Uplink preflight: TCP (как dial ShadowTLS).

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
| `displayName` | UI only |

### Outbound defaults (`SingBoxConnectPlan.hysteria2`)

| Поле | Значение |
|------|----------|
| `tls` | enabled + `server_name` + `alpn` |
| `domain_resolver` | `dns-direct`, `ipv4_only` |
| `up_mbps` / `down_mbps` | omitted → BBR |

Uplink preflight: DNS-only (QUIC/UDP). ShadowTLS остаётся на TCP.

---

## DNS (sing-box 1.11+ rule actions)

| Tag | Тип | Назначение |
|-----|-----|------------|
| `dns-direct` | `type: local` | bootstrap, domain resolver |
| `dns-remote` | HTTPS `1.1.1.1` | DoH через proxy |

Rules: PTR → reject; bootstrap domains → `dns-direct`. Глобально: `final: dns-remote`, `strategy: ipv4_only`.

---

## Route и TUN

- TUN: `gvisor`, `strict_route`, `172.19.0.1/30`, split `0.0.0.0/1` + `128.0.0.0/1`
- Route: sniff → hijack-dns → QUIC reject → private direct → IPv6 reject → proxy
- `override_android_vpn: true`
- `default_domain_resolver`: `dns-direct`, `ipv4_only`

---

## Uplink bypass

| Uplink `server` | Механизм |
|-----------------|----------|
| Literal IPv4 | `route_exclude_address` в TUN |
| Hostname | `resolveUplinkIpv4s` → `withExtraDirectIps` перед build |

---

## Diag inbound

Mixed `127.0.0.1:10808` (`SingBoxTags.diagInbound`):

- Post-connect: `VpnTunnelProbe`
- Egress: `NetworkDiagnostics` (roadmap)
- Native snapshot: `getEgressRouteSnapshot` (`osVpnActive`, `demarConnected`, `diagProxyReady`)

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
- Shadowsocks / ShadowTLS — без отдельного tag (в ядре по умолчанию)

AAR: `app/libs/libbox.aar` (arm64). Geo-базы в APK не bundle'ятся.

---

## Kotlin layout

| Путь | Роль |
|------|------|
| `domain/vpn/*` | Profiles, `SingBoxConnectPlan` |
| `data/vpn/SingBoxConfigBuilder.kt` | TUN + DNS + route JSON |
| `data/vpn/VpnController.kt` | Connect pipeline, UI bridge |
| `data/network/UplinkIpv4.kt` | uplink resolve |
| `vpn/SingBoxCore.kt` | Libbox in-process |
| `vpn/DemarVpnService.kt` | `VpnService` + FGS |
| `vpn/DemarLibboxPlatform.kt` | `openTun`, `DefaultInterfaceMonitor` |
| `vpn/VpnEventStream.kt` | State events для Compose UI |

Синхронизировать `TAGS` / pipeline с [demar_vpn/readme-core.md](../demar_vpn/readme-core.md).
