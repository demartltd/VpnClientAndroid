package com.demar.vpn

import com.demar.vpn.data.network.RawTextFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawTextFetcherTest {
  @Test
  fun get_success_returnsBody() = runBlocking {
    val fetcher = RawTextFetcher(transport = { 200 to "# Hello".toByteArray() })
    assertEquals("# Hello", fetcher.get("https://example.com/doc.md"))
  }

  @Test
  fun get_httpError_throws() = runBlocking {
    val fetcher = RawTextFetcher(transport = { 404 to ByteArray(0) })
    val err = runCatching { fetcher.get("https://example.com/missing.md") }.exceptionOrNull()
    assertTrue(err?.message?.contains("HTTP 404") == true)
  }

  @Test
  fun get_rejectsNonHttps() = runBlocking {
    val fetcher = RawTextFetcher(transport = { 200 to "x".toByteArray() })
    val err = runCatching { fetcher.get("http://example.com/doc.md") }.exceptionOrNull()
    assertTrue(err is IllegalArgumentException)
  }

  @Test
  fun get_oversized_throws() = runBlocking {
    val fetcher = RawTextFetcher(
      maxBytes = 8,
      transport = { 200 to "0123456789".toByteArray() },
    )
    val err = runCatching { fetcher.get("https://example.com/big.md") }.exceptionOrNull()
    assertTrue(err?.message?.contains("too large") == true)
  }
}
