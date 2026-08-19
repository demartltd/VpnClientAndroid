package com.demar.vpn

import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.servers.ServersApi
import com.demar.vpn.data.vpn.SingBoxConfigBuilder
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
    assertEquals("hy2.example.com", profile.profile.server)
    assertEquals(443, profile.profile.port)
    assertEquals("hy2-pass", profile.profile.password)
    assertEquals(10, profile.profile.upMbps)
    assertEquals(10, profile.profile.downMbps)
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
  fun parseUnsupported_returnsNull() {
    val item = JSONObject()
      .put("id", "x")
      .put("name", "DE-FREE-1")
      .put("protocol", "trojan")
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
    assertEquals("", profile!!.profile.password)
    assertNull(profile.profile.upMbps)
    assertNull(profile.profile.downMbps)
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
    assertEquals("", profile.profile.password)
    assertEquals("", profile.profile.server)
    assertNull(profile.profile.upMbps)
    assertNull(profile.profile.downMbps)
  }
}
