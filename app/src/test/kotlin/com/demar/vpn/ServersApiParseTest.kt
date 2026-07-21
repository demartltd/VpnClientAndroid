package com.demar.vpn

import com.demar.vpn.data.servers.ServersApi
import com.demar.vpn.domain.vpn.ServerProfileKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServersApiParseTest {
  @Test
  fun parseShadowTls_fromClientConfigJson() {
    val config = """
      {
        "outbounds": [
          {
            "type": "shadowsocks",
            "tag": "FI-DEV-1",
            "city": "Helsinki",
            "method": "2022-blake3-aes-128-gcm",
            "password": "ss-pass",
            "detour": "shadowtls-out",
            "multiplex": { "enabled": true }
          },
          {
            "type": "shadowtls",
            "tag": "shadowtls-out",
            "server": "fi.example.com",
            "server_port": 8443,
            "version": 3,
            "password": "st-pass",
            "tls": { "enabled": true, "server_name": "www.microsoft.com" }
          },
          { "type": "direct", "tag": "direct" }
        ],
        "route": { "final": "FI-DEV-1" }
      }
    """.trimIndent()
    val item = JSONObject()
      .put("id", "abc123")
      .put("name", "FI-DEV-1")
      .put("protocol", "shadowtls")
      .put("city", "Helsinki")
      .put("clientConfigJson", config)
    val profile = ServersApi.parseServerItem(item)
    assertNotNull(profile)
    assertEquals("abc123", profile!!.id)
    assertEquals("FI-DEV-1", profile.displayName)
    assertTrue(profile.kind is ServerProfileKind.ShadowTls)
    val st = (profile.kind as ServerProfileKind.ShadowTls).profile
    assertEquals("fi.example.com", st.server)
    assertEquals(8443, st.port)
    assertEquals("st-pass", st.password)
    assertEquals("ss-pass", st.ssPassword)
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
    assertTrue(profile.kind is ServerProfileKind.Hysteria2)
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
}
