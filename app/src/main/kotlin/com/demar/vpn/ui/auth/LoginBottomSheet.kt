package com.demar.vpn.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginBottomSheet(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = null,
  ) {
    Column(
      Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      StaticSheetDragHandle()
      Text(
        stringResource(R.string.login_sheet_title),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.height(16.dp))
      LoginPanel(onSuccess = onSignedIn)
    }
  }
}
