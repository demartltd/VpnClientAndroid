package com.demar.vpn.data.vpn

import com.demar.vpn.domain.vpn.SingBoxTags
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import org.json.JSONObject

data class ThroughputRates(val downloadBps: Double, val uploadBps: Double) {
  companion object {
    val ZERO = ThroughputRates(0.0, 0.0)
  }
}

object ClashTrafficMonitor {
  private val trafficUrl = URL("http://localhost:${SingBoxTags.CLASH_API_PORT}/traffic")

  /** Parses one NDJSON line from Clash `GET /traffic`. Returns up/down Bps. */
  fun tryParse(line: String): Pair<Long, Long>? = try {
    val o = JSONObject(line)
    o.getLong("up") to o.getLong("down")
  } catch (_: Exception) {
    null
  }

  suspend fun run(onSample: (up: Long, down: Long) -> Unit) {
    while (currentCoroutineContext().isActive) {
      try {
        streamOnce(onSample)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        delay(1_000)
      }
    }
  }

  private suspend fun streamOnce(onSample: (up: Long, down: Long) -> Unit) {
    val conn = (trafficUrl.openConnection() as HttpURLConnection).apply {
      connectTimeout = 2_000
      readTimeout = 0
      requestMethod = "GET"
      useCaches = false
    }
    val close = currentCoroutineContext().job.invokeOnCompletion { runCatching { conn.disconnect() } }
    try {
      conn.connect()
      if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
      BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
        while (currentCoroutineContext().isActive) {
          currentCoroutineContext().ensureActive()
          val line = reader.readLine() ?: break
          val sample = tryParse(line) ?: continue
          onSample(sample.first, sample.second)
        }
      }
    } finally {
      close.dispose()
      runCatching { conn.disconnect() }
    }
  }
}
