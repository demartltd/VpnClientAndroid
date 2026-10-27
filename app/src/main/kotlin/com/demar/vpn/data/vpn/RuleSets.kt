package com.demar.vpn.data.vpn

import android.content.Context
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import java.io.File

/** Bundled local `.srs` (assets → filesDir). No runtime HTTP. */
object RuleSets {
  const val ADS_TAG = "geosite-category-ads"
  private const val DIR = "rule-sets"

  fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

  fun pathFor(context: Context, tag: String): String = File(dir(context), "$tag.srs").absolutePath

  /** Copy asset if needed. Returns absolute path or null. */
  fun ensure(context: Context, tag: String): String? {
    val out = File(dir(context), "$tag.srs")
    if (out.isFile && out.length() > 0L) return out.absolutePath
    return runCatching {
      context.assets.open("$DIR/$tag.srs").use { input ->
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null
        out.writeBytes(bytes)
        out.absolutePath
      }
    }.getOrNull()
  }

  /** Drop missing GEO tags; resolve ads. Logs gaps. */
  fun prepare(context: Context, plan: SingBoxConnectPlan, wantAds: Boolean): Prepared {
    val ready = plan.bypassRuleSets.filter { ensure(context, it.tag) != null }
    val missing = plan.bypassRuleSets.map { it.tag } - ready.map { it.tag }.toSet()
    if (missing.isNotEmpty()) AppLog.append("[vpn] rule-set missing: ${missing.joinToString(",")}")
    val adsPath = if (wantAds) ensure(context, ADS_TAG) else null
    if (wantAds && adsPath == null) AppLog.append("[vpn] ads asset missing")
    return Prepared(
      plan = if (missing.isEmpty()) plan else plan.copy(bypassRuleSets = ready),
      adsPath = adsPath,
      dir = dir(context).absolutePath,
    )
  }

  data class Prepared(val plan: SingBoxConnectPlan, val adsPath: String?, val dir: String)
}
