package com.demar.vpn.ui.settings.support

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.demar.vpn.data.support.SupportApi

internal class SupportAttachHandlers(
  val openGallery: () -> Unit,
)

@Composable
internal fun rememberSupportAttachHandlers(
  onPicked: (List<Uri>) -> Unit,
): SupportAttachHandlers {
  val picked by rememberUpdatedState(onPicked)
  val gallery = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(SupportApi.MaxMediaFiles),
  ) { uris -> picked(uris) }
  return remember(gallery) {
    SupportAttachHandlers(
      openGallery = {
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
    )
  }
}

