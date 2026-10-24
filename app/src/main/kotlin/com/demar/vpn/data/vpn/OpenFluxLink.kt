package com.demar.vpn.data.vpn

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** Decoded openflux://v1 share link (Yandex Boards carrier). */
data class OpenFluxLinkConfig(
  val secret: String,
  val boardsUrl: String,
)

object OpenFluxLink {
  private const val PREFIX = "openflux://v1/"
  private const val MAX_PAYLOAD = 16 * 1024
  private const val TRANSPORT = "boards"

  fun decode(link: String): OpenFluxLinkConfig {
    val trimmed = link.trim()
    require(trimmed.startsWith(PREFIX)) { "not an openflux://v1 link" }
    val body = normalizeBody(trimmed.removePrefix(PREFIX))
    val packed = Base64.getUrlDecoder().decode(padBase64(body))
    val raw = inflateRaw(packed)
    require(raw.size <= MAX_PAYLOAD) { "link payload too large" }
    val json = JSONObject(String(raw, Charsets.UTF_8))
    val secret = json.optString("secret").trim()
    require(secret.length >= 16) { "short secret" }
    val transports = json.optJSONArray("transports")
      ?: throw IllegalArgumentException("no transports")
    var url = ""
    for (i in 0 until transports.length()) {
      val t = transports.optJSONObject(i) ?: continue
      if (t.optString("type") == TRANSPORT) {
        url = t.optString("url").trim()
        if (url.isNotEmpty()) break
      }
    }
    if (url.isEmpty()) url = json.optString("context").trim()
    require(url.isNotEmpty()) { "boards url missing" }
    return OpenFluxLinkConfig(secret = secret, boardsUrl = url)
  }

  private fun normalizeBody(b: String): String =
    b.filterNot { it in " \t\r\n\u00a0\u200b" }
      .replace('+', '-')
      .replace('/', '_')
      .trimEnd('=')

  private fun padBase64(s: String): String {
    val rem = s.length % 4
    return if (rem == 0) s else s + "=".repeat(4 - rem)
  }

  private fun inflateRaw(packed: ByteArray): ByteArray {
    val inflater = Inflater(true)
    return InflaterInputStream(ByteArrayInputStream(packed), inflater).use { it.readBytes() }
  }
}
