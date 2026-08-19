package com.demar.vpn.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.demar.vpn.R
import com.demar.vpn.data.logging.AppLog

private val SocialLinks = listOf(
  R.drawable.ic_brand_x to "https://x.com/demargames",
  R.drawable.ic_brand_bluesky to "https://bsky.app/profile/demargames.bsky.social",
  R.drawable.ic_brand_instagram to "https://www.instagram.com/demargames/",
  R.drawable.ic_brand_tiktok to "https://www.tiktok.com/@demargames",
  R.drawable.ic_brand_telegram to "https://t.me/demargames",
)

fun openSocialLink(context: android.content.Context, url: String) {
  try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
  } catch (e: Exception) {
    AppLog.append("link open failed: $url — $e")
  }
}

/** OAuth / login URLs — Custom Tabs so demarvpn:// return lands back in the app task. */
fun openAuthUrl(context: android.content.Context, url: String) {
  try {
    androidx.browser.customtabs.CustomTabsIntent.Builder()
      .setShowTitle(true)
      .build()
      .launchUrl(context, url.toUri())
  } catch (e: Exception) {
    AppLog.append("auth tab failed, fallback VIEW: $e")
    openSocialLink(context, url)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsLinksStrip(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SocialLinks.forEach { (icon, url) ->
      IconLinkChip(iconRes = icon, onClick = { openSocialLink(context, url) })
    }
  }
}
