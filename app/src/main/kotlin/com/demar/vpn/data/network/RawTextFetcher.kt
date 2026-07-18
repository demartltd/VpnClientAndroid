package com.demar.vpn.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RawTextFetcher(
  private val connectTimeoutMs: Int = 10_000,
  private val readTimeoutMs: Int = 15_000,
  private val maxBytes: Int = 512 * 1024,
  private val transport: (String) -> Pair<Int, ByteArray> = { url -> httpGet(url, connectTimeoutMs, readTimeoutMs) },
) {
  suspend fun get(url: String): String =
    withContext(Dispatchers.IO) {
      require(url.startsWith("https://")) { "Only HTTPS URLs are allowed" }
      val (code, bytes) = transport(url)
      if (code !in 200..299) throw IOException("HTTP $code")
      if (bytes.size > maxBytes) throw IOException("Response too large")
      bytes.toString(Charsets.UTF_8)
    }
}

private fun httpGet(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): Pair<Int, ByteArray> {
  val conn = URL(url).openConnection() as HttpURLConnection
  return try {
    conn.requestMethod = "GET"
    conn.connectTimeout = connectTimeoutMs
    conn.readTimeout = readTimeoutMs
    conn.instanceFollowRedirects = true
    conn.useCaches = false
    conn.setRequestProperty("Accept", "text/plain, text/markdown, */*")
    conn.setRequestProperty("Cache-Control", "no-cache, no-store")
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    code to (stream?.readBytes() ?: ByteArray(0))
  } finally {
    conn.disconnect()
  }
}
