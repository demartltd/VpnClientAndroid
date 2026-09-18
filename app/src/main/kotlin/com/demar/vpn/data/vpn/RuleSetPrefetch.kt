package com.demar.vpn.data.vpn

import android.content.Context
import com.demar.vpn.data.network.httpsGet
import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import java.io.File

/** Prefetch remote `.srs` before TUN so sing-box can use `initial_path` on cold start. */
object RuleSetPrefetch {
  private const val DIR = "rule-sets"
  private const val MAX_BYTES = 8 * 1024 * 1024

  fun ensure(context: Context, sets: List<ApiBypassRuleSet>): List<ApiBypassRuleSet> {
    if (sets.isEmpty()) return sets
    val dir = File(context.filesDir, DIR).apply { mkdirs() }
    return sets.map { rs ->
      val file = dir.resolve("${rs.tag}.srs")
      if (!file.isFile || file.length() == 0L) {
        file.writeBytes(httpsGet(rs.url, readTimeoutMs = 30_000, maxBytes = MAX_BYTES))
      }
      rs.copy(initialPath = file.absolutePath)
    }
  }
}
