package com.demar.vpn.vpn

import android.content.res.AssetManager

object SingBoxBundledAssets {
  private const val ASSET_VERSION = "singbox/bundled_version.txt"

  fun readVersionLabel(assets: AssetManager): String {
    return try {
      assets.open(ASSET_VERSION).bufferedReader().use { r ->
        r.readLine()?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
      }
    } catch (_: Exception) {
      "—"
    }
  }
}
