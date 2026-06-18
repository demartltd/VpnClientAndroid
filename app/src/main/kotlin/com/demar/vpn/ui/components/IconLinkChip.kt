package com.demar.vpn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun IconLinkChip(
  @DrawableRes iconRes: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cs = MaterialTheme.colorScheme
  Icon(
    painter = painterResource(iconRes),
    contentDescription = null,
    tint = cs.primary,
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(cs.primary.copy(alpha = 0.1f))
      .clickable(onClick = onClick)
      .padding(8.dp)
      .size(26.dp),
  )
}
