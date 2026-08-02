package com.demar.vpn

import com.demar.vpn.data.servers.ServersApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ServersApiParseTest {
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
            "tls": { "enabled": true, "server_name": "hy2.example.com", "alpn": ["h3"] }
          },
          { "type": "direct", "tag": "direct" }
        ],
        "route": { "final": "FI-FREE-2" }
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
  }
}
