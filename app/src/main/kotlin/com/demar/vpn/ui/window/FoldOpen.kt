package com.demar.vpn.ui.window

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val LocalFoldWide = compositionLocalOf { false }

@Composable
fun rememberIsFoldOpen(): Boolean {
  val activity = LocalActivity.current ?: return false
  val foldOpen by remember(activity) {
    WindowInfoTracker.getOrCreate(activity)
      .windowLayoutInfo(activity)
      .map { info -> info.displayFeatures.any { it is FoldingFeature } }
      .distinctUntilChanged()
  }.collectAsStateWithLifecycle(false)
  return foldOpen
}
