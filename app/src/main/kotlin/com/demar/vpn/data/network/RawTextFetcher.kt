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
  private val transport: (String) -> Pair<Int, ByteArray> = { url ->
    httpsGetRaw(url, connectTimeoutMs, readTimeoutMs)
  },
) {
  suspend fun get(url: String): String =
    withContext(Dispatchers.IO) {
      httpsGet(url, connectTimeoutMs, readTimeoutMs, maxBytes, transport).toString(Charsets.UTF_8)
    }
}

/** HTTPS GET with size cap. Shared by text fetch and rule-set prefetch. */
fun httpsGet(
  url: String,
  connectTimeoutMs: Int = 10_000,
  readTimeoutMs: Int = 15_000,
  maxBytes: Int = 512 * 1024,
  transport: (String) -> Pair<Int, ByteArray> = { u -> httpsGetRaw(u, connectTimeoutMs, readTimeoutMs) },
): ByteArray {
  require(url.startsWith("https://")) { "Only HTTPS URLs are allowed" }
  val (code, bytes) = transport(url)
  if (code !in 200..299) throw IOException("HTTP $code")
  if (bytes.size > maxBytes) throw IOException("Response too large")
  return bytes
}

internal fun httpsGetRaw(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): Pair<Int, ByteArray> {
  val conn = URL(url).openConnection() as HttpURLConnection
  return try {
    conn.requestMethod = "GET"
    conn.connectTimeout = connectTimeoutMs
    conn.readTimeout = readTimeoutMs
    conn.instanceFollowRedirects = true
    conn.useCaches = false
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    code to (stream?.readBytes() ?: ByteArray(0))
  } finally {
    conn.disconnect()
  }
}
