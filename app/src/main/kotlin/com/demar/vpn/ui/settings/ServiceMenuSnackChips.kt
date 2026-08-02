package com.demar.vpn.ui.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SnackKind
import kotlinx.coroutines.launch

@Composable
fun ServiceMenuSnackChips(snackbarHost: SnackbarHostState) {
  val cs = MaterialTheme.colorScheme
  val scope = rememberCoroutineScope()
  val outline = cs.outline.copy(alpha = 0.22f)
  val chipBg = cs.surfaceContainerHighest.copy(alpha = 0.35f)
  val iconColor = cs.primary
  val demos = listOf(
    Triple(stringResource(R.string.snack_demo_info), SnackKind.Info, stringResource(R.string.snack_demo_info_msg)),
    Triple(stringResource(R.string.snack_demo_success), SnackKind.Success, stringResource(R.string.snack_demo_success_msg)),
    Triple(stringResource(R.string.snack_demo_warning), SnackKind.Warning, stringResource(R.string.snack_demo_warning_msg)),
    Triple(stringResource(R.string.snack_demo_error), SnackKind.Error, stringResource(R.string.snack_demo_error_msg)),
  )

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (i in demos.indices step 2) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (j in i until minOf(i + 2, demos.size)) {
          val (label, kind, message) = demos[j]
          Row(
            modifier = Modifier
              .weight(1f)
              .background(chipBg, RoundedCornerShape(10.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = { scope.launch { DemarSnackbar.show(snackbarHost, kind, message) } },
              )
              .border(1.dp, outline, RoundedCornerShape(10.dp))
              .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(DemarSnackbar.icon(kind), contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Text(
              label,
              modifier = Modifier.padding(start = 8.dp),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
          }
        }
      }
    }
  }
}
