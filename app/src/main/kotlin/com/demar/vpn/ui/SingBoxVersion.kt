package com.demar.vpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.demar.vpn.vpn.SingBoxBundledAssets

@Composable
fun rememberSingBoxVersionLabel(): String {
  val assets = LocalContext.current.assets
  return remember(assets) {
    runCatching { SingBoxBundledAssets.readVersionLabel(assets) }.getOrDefault("—")
  }
}
