package com.demar.vpn.ui.settings.support

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.demar.vpn.R
import com.demar.vpn.data.support.SupportApi
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.components.SettingsGroup
import com.demar.vpn.ui.components.StaticSheetDragHandle

internal class SupportAttachHandlers(
  val openGallery: () -> Unit,
  val openFiles: () -> Unit,
)

@Composable
internal fun rememberSupportAttachHandlers(
  onPicked: (List<Uri>) -> Unit,
  onPermissionDenied: () -> Unit,
): SupportAttachHandlers {
  val context = LocalContext.current
  val picked by rememberUpdatedState(onPicked)
  val gallery = rememberLauncherForActivityResult(
    ActivityResultContracts.GetMultipleContents(),
  ) { uris -> picked(uris.take(SupportApi.MaxMediaFiles)) }
  val files = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris -> picked(uris.take(SupportApi.MaxMediaFiles)) }
  val permission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    gallery.launch("image/*")
  }
  return remember(gallery, files, permission) {
    SupportAttachHandlers(
      openGallery = {
        val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        if (images == PackageManager.PERMISSION_GRANTED) {
          gallery.launch("image/*")
        } else {
          permission.launch(
            arrayOf(
              Manifest.permission.READ_MEDIA_IMAGES,
              Manifest.permission.READ_MEDIA_VIDEO,
            ),
          )
        }
      },
      openFiles = { files.launch(arrayOf("*/*")) },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupportAttachSheet(
  onDismiss: () -> Unit,
  onGallery: () -> Unit,
  onFile: () -> Unit,
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
          Icons.Filled.PhotoLibrary,
          stringResource(R.string.support_attach_gallery),
          stringResource(R.string.support_attach_gallery_sub),
          onClick = onGallery,
        )
        SettingsActionButton(
          Icons.AutoMirrored.Filled.InsertDriveFile,
          stringResource(R.string.support_attach_file),
          stringResource(R.string.support_attach_file_sub),
          onClick = onFile,
        )
      }
    }
  }
}
