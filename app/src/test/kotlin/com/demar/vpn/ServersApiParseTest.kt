package com.demar.vpn

import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.servers.ServersApi
import com.demar.vpn.data.vpn.SingBoxConfigBuilder
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.TrojanProfile
import com.demar.vpn.domain.vpn.VlessRealityProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServersApiParseTest {
  @Test
  fun validateParsedCount_rejectsEmptyParse() {
    assertThrows(AuthException::class.java) {
      ServersApi.validateParsedCount(3, 0)
    }
    ServersApi.validateParsedCount(0, 0)
    ServersApi.validateParsedCount(2, 2)
  }

  @Test
  fun parseUnsupportedProtocol_returnsNull() {
    val config = """
      {
        "outbounds": [
          {
            "type": "shadowsocks",
            "tag": "FI-DEV-1",
            "method": "2022-blake3-aes-128-gcm",
            "password": "ss-pass",
            "detour": "shadowtls-out"
          },
          {
            "type": "shadowtls",
            "tag": "shadowtls-out",
            "server": "fi.example.com",
            "server_port": 8443,
            "version": 3,
            "password": "st-pass",
            "tls": { "enabled": true, "server_name": "www.microsoft.com" }
          }
        ]
      }
    """.trimIndent()
    val item = JSONObject()
      .put("id", "abc123")
      .put("name", "FI-DEV-1")
      .put("protocol", "shadowtls")
      .put("clientConfigJson", config)
    assertNull(ServersApi.parseServerItem(item))
  }

  @Test
  fun parseHysteria2_fromClientConfigJson() {
    val config = """
      {
        "outbounds": [
          {
            "type": "hysteria2",
            "tag": "FI-FREE-2",
            "city": "Helsinki",
            "server": "hy2.example.com",
            "server_port": 443,
            "password": "hy2-pass",
            "up_mbps": 10,
            "down_mbps": 10,
            "tls": { "enabled": true, "server_name": "hy2.example.com", "alpn": ["h3"] }
          },
          { "type": "direct", "tag": "direct" }
        ],
        "route": {
          "final": "FI-FREE-2",
          "rule_set": [
            {
              "tag": "geoip-ru",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs"
            },
            {
              "tag": "geosite-category-ru",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs"
            },
            {
              "tag": "geoip-ir",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ir.srs"
            },
            {
              "tag": "geosite-ir",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/Chocolate4U/Iran-sing-box-rules/rule-set/geosite-ir.srs"
            },
            {
              "tag": "geoip-cn",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs"
            },
            {
              "tag": "geosite-cn",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs"
            }
          ]
        },
        "inbounds": [
          {
            "type": "tun",
            "route_exclude_address_set": ["geoip-ru", "geoip-ir", "geoip-cn"]
          }
        ]
      }
    """.trimIndent()
    val item = JSONObject()
      .put("id", "def456")
      .put("name", "FI-FREE-2")
      .put("protocol", "hysteria2")
      .put("clientConfigJson", config)
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    assertEquals("def456", profile!!.id)
    assertEquals(ServerProtocol.Hysteria2, profile.protocol)
    assertEquals("hy2.example.com", profile.profile.server)
    assertEquals(443, profile.profile.port)
    val hy2 = profile.profile as Hysteria2Profile
    assertEquals("hy2-pass", hy2.password)
    assertEquals(10, hy2.upMbps)
    assertEquals(10, hy2.downMbps)
    assertEquals(6, profile.bypassRuleSets.size)
    assertTrue(profile.bypassRuleSets.any { it.tag == "geosite-ir" && it.url.contains("Chocolate4U") })
    assertTrue(profile.bypassRuleSets.any { it.tag == "geosite-cn" })
    val built = SingBoxConfigBuilder.buildTunConfigJson(profile.connectPlan)
    assertFalse(built.contains("route_exclude_address_set"))
    assertTrue(built.contains("geosite-ir"))
    assertTrue(built.contains("\"up_mbps\": 10"))
    assertTrue(built.contains("\"down_mbps\": 10"))
  }

  @Test
  fun parseVlessReality_fromClientConfigJson() {
    val config = """
      {
        "outbounds": [
          {
            "type": "vless",
            "tag": "DE-FREE-9",
            "city": "Frankfurt",
            "server": "vless.example.com",
            "server_port": 443,
            "uuid": "bf000d23-0752-40b4-affe-68f7707a9661",
            "flow": "xtls-rprx-vision",
            "tls": {
              "enabled": true,
              "server_name": "www.microsoft.com",
              "reality": {
                "enabled": true,
                "public_key": "jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0",
                "short_id": "0123456789abcdef"
              }
            },
            "multiplex": { "enabled": true }
          },
          { "type": "direct", "tag": "direct" }
        ],
        "route": {
          "final": "DE-FREE-9",
          "rule_set": [
            {
              "tag": "geoip-ru",
              "type": "remote",
              "format": "binary",
              "url": "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs"
            }
          ]
        }
      }
    """.trimIndent()
    val item = JSONObject()
      .put("id", "vless1")
      .put("name", "DE-FREE-9")
      .put("protocol", "vless_reality")
      .put("clientConfigJson", config)
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    assertEquals(ServerProtocol.VlessReality, profile!!.protocol)
    assertEquals("VLESS", profile.protocolLabel)
    val vl = profile.profile as VlessRealityProfile
    assertEquals("vless.example.com", vl.server)
    assertEquals(443, vl.port)
    assertEquals("bf000d23-0752-40b4-affe-68f7707a9661", vl.uuid)
    assertEquals("www.microsoft.com", vl.tlsServerName)
    assertEquals("jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0", vl.publicKey)
    assertEquals("0123456789abcdef", vl.shortId)
    assertEquals(1, profile.bypassRuleSets.size)
    val built = SingBoxConfigBuilder.buildTunConfigJson(profile.connectPlan)
    val outbound = JSONObject(built).getJSONArray("outbounds").getJSONObject(0)
    assertEquals("vless", outbound.getString("type"))
    assertEquals("xtls-rprx-vision", outbound.getString("flow"))
    assertTrue(outbound.has("tls"))
    assertFalse(outbound.has("multiplex"))
    assertFalse(outbound.has("network"))
    assertTrue(built.contains("www.microsoft.com"))
    assertTrue(profile.profile.preferResolvedDial)
    assertTrue(profile.connectPlan.tun.bootstrapDomains.contains("vless.example.com"))
    assertFalse(profile.connectPlan.tun.bootstrapDomains.contains("www.microsoft.com"))
  }

  @Test
  fun parseTrojan_fromClientConfigJson() {
    val config = """
      {
        "outbounds": [
          {
            "type": "trojan",
            "tag": "SE-VIP-71",
            "city": "Stockholm",
            "server": "se-f-1.xyzdeer.space",
            "server_port": 8443,
            "password": "trojan-secret",
            "tls": { "enabled": true, "server_name": "se-f-1.xyzdeer.space" },
            "multiplex": { "enabled": true }
          },
          { "type": "direct", "tag": "direct" }
        ]
      }
    """.trimIndent()
    val item = JSONObject()
      .put("id", "trj1")
      .put("name", "SE-VIP-71")
      .put("protocol", "trojan")
      .put("clientConfigJson", config)
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    assertEquals(ServerProtocol.Trojan, profile!!.protocol)
    assertEquals("TRJ", profile.protocolLabel)
    val tr = profile.profile as TrojanProfile
    assertEquals("se-f-1.xyzdeer.space", tr.server)
    assertEquals(8443, tr.port)
    assertEquals("trojan-secret", tr.password)
    assertEquals("se-f-1.xyzdeer.space", tr.tlsServerName)
    assertTrue(profile.profile.preferResolvedDial)
    val outbound = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(profile.connectPlan))
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals("trojan", outbound.getString("type"))
    assertEquals("trojan-secret", outbound.getString("password"))
    assertTrue(outbound.getJSONObject("multiplex").getBoolean("enabled"))
    assertEquals("se-f-1.xyzdeer.space", outbound.getJSONObject("tls").getString("server_name"))
  }

  @Test
  fun parseUnsupported_returnsNull() {
    val item = JSONObject()
      .put("id", "x")
      .put("name", "DE-FREE-1")
      .put("protocol", "shadowsocks")
      .put("clientConfigJson", "{}")
    assertNull(ServersApi.parseServerItem(item))
  }

  @Test
  fun parseServersResponse_normalizesNullableEntitlements() {
    val result = ServersApi.parseServersResponse(
      JSONObject(
        """
        {
          "vpnSubscription": "",
          "vpnExpiresAt": "null",
          "servers": []
        }
        """.trimIndent(),
      ),
    )
    assertNull(result.vpnSubscription)
    assertNull(result.vpnExpiresAt)
  }

  @Test
  fun parseHysteria2_missingPassword_keepsEmpty() {
    val config = """
      {
        "outbounds": [
          {
            "type": "hysteria2",
            "tag": "FI-FREE-1",
            "city": "Helsinki",
            "server": "hy2.example.com",
            "server_port": 443,
            "tls": { "enabled": true, "server_name": "hy2.example.com", "alpn": ["h3"] }
          }
        ]
      }
    """.trimIndent()
    val item = JSONObject()
      .put("id", "rev1")
      .put("name", "FI-FREE-1")
      .put("protocol", "hysteria2")
      .put("clientConfigJson", config)
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    val hy2 = profile!!.profile as Hysteria2Profile
    assertEquals("", hy2.password)
    assertNull(hy2.upMbps)
    assertNull(hy2.downMbps)
  }

  @Test
  fun parseHysteria2_previewWithoutConfig_buildsStub() {
    val item = JSONObject()
      .put("id", "6a3eeae01afeb82f6b1cddb7")
      .put("name", "DE-FREE-93")
      .put("status", "free")
      .put("protocol", "hysteria2")
      .put("city", "Frankfurt am Main")
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    assertEquals("6a3eeae01afeb82f6b1cddb7", profile!!.id)
    assertEquals("DE-FREE-93", profile.displayName)
    assertEquals("Frankfurt am Main", profile.city)
    val hy2 = profile.profile as Hysteria2Profile
    assertEquals("", hy2.password)
    assertEquals("", profile.profile.server)
    assertNull(hy2.upMbps)
    assertNull(hy2.downMbps)
  }

  @Test
  fun parseVlessReality_previewWithoutConfig_buildsStub() {
    val item = JSONObject()
      .put("id", "vless-preview")
      .put("name", "DE-FREE-10")
      .put("protocol", "vless_reality")
      .put("city", "Frankfurt")
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    assertEquals(ServerProtocol.VlessReality, profile!!.protocol)
    val vl = profile.profile as VlessRealityProfile
    assertEquals("", vl.uuid)
    assertEquals("", vl.publicKey)
    assertFalse(vl.hasCredentials)
  }
}
