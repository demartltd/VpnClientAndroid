package com.demar.vpn.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SnackKind
import kotlinx.coroutines.launch

private data class SnackDemo(val label: String, val icon: ImageVector, val kind: SnackKind, val message: String)

private val Demos = listOf(
  SnackDemo("Info", Icons.Filled.Info, SnackKind.Info, "Sample info message"),
  SnackDemo("Success", Icons.Filled.CheckCircle, SnackKind.Success, "Operation completed"),
  SnackDemo("Warning", Icons.Filled.Warning, SnackKind.Warning, "Warning"),
  SnackDemo("Error", Icons.Filled.Error, SnackKind.Error, "Something went wrong"),
)

@Composable
fun ServiceMenuSnackChips(snackbarHost: SnackbarHostState, iconColor: androidx.compose.ui.graphics.Color) {
  val cs = MaterialTheme.colorScheme
  val scope = rememberCoroutineScope()
  val outline = cs.outline.copy(alpha = 0.22f)
  val chipBg = cs.surfaceContainerHighest.copy(alpha = 0.35f)

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (i in Demos.indices step 2) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (j in i until minOf(i + 2, Demos.size)) {
          val demo = Demos[j]
          Row(
            modifier = Modifier
              .weight(1f)
              .background(chipBg, RoundedCornerShape(10.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = {
                  scope.launch {
                    when (demo.kind) {
                      SnackKind.Info -> DemarSnackbar.showInfo(snackbarHost, demo.message)
                      SnackKind.Success -> DemarSnackbar.showSuccess(snackbarHost, demo.message)
                      SnackKind.Error -> DemarSnackbar.showError(snackbarHost, demo.message)
                      SnackKind.Warning -> DemarSnackbar.showWarning(snackbarHost, demo.message)
                    }
                  }
                },
              )
              .border(1.dp, outline, RoundedCornerShape(10.dp))
              .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(demo.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Text(
              demo.label,
              modifier = Modifier.padding(start = 8.dp),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
          }
        }
      }
    }
  }
}
