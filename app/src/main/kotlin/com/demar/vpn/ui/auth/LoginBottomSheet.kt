package com.demar.vpn.ui.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginBottomSheet(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { StaticSheetDragHandle() },
  ) {
    LoginPanel(
      onSuccess = onSignedIn,
      modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
    )
  }
}
