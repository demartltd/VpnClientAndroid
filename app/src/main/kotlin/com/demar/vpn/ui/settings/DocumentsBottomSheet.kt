package com.demar.vpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.domain.content.LegalDocument
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.components.SettingsGroup
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsBottomSheet(
  onDismiss: () -> Unit,
  onOpenDocument: (LegalDocument) -> Unit,
) {
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
      SettingsGroup {
        SettingsActionButton(
          Icons.Filled.Description,
          stringResource(LegalDocument.Terms.titleRes),
          stringResource(LegalDocument.Terms.subtitleRes),
          onClick = { onOpenDocument(LegalDocument.Terms) },
        )
        SettingsActionButton(
          Icons.Filled.PrivacyTip,
          stringResource(LegalDocument.Privacy.titleRes),
          stringResource(LegalDocument.Privacy.subtitleRes),
          onClick = { onOpenDocument(LegalDocument.Privacy) },
        )
      }
    }
  }
}
