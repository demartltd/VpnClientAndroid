package com.demar.vpn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ActiveTabContent(
  active: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  if (!active) {
    Box(modifier.fillMaxSize())
    return
  }
  content()
}

fun toolbarBottomInset(extra: Dp = 16.dp): Dp = extra + AppFloatingToolbarExtent

fun Modifier.mainTabPadding(
  top: Dp = 64.dp,
  bottomExtra: Dp = 16.dp,
): Modifier = padding(start = 16.dp, end = 16.dp, top = top, bottom = bottomExtra + AppFloatingToolbarExtent)
