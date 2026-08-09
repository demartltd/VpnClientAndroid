package com.demar.vpn.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demar.vpn.data.update.AppUpdateState

@Composable
fun UpdateFab(
  state: AppUpdateState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val downloading = state as? AppUpdateState.Downloading
  val visible = state is AppUpdateState.Available ||
    state is AppUpdateState.Downloading ||
    state is AppUpdateState.Ready
  if (!visible) return

  Surface(
    onClick = onClick,
    modifier = modifier.size(64.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.tertiaryContainer,
    shadowElevation = 6.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (downloading != null) {
        Text(
          "${downloading.percent}%",
          color = MaterialTheme.colorScheme.onTertiaryContainer,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp,
        )
      } else {
        Icon(
          Icons.Filled.SystemUpdate,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }
    }
  }
}
