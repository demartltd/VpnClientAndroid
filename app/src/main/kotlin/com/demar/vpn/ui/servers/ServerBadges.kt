package com.demar.vpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.protocolBadges
import com.demar.vpn.ui.theme.PlusBadgeSeed
import com.demar.vpn.ui.theme.VipBadgeSeed
import com.demar.vpn.ui.theme.WhiteListBadgeBg
import com.demar.vpn.ui.theme.WhiteListBadgeFg
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

/** Shared height for home server badges (heart / tier / protocol). */
private val HomeBadgeHeight: Dp = 24.dp

private val CompactLineHeight = LineHeightStyle(
  alignment = LineHeightStyle.Alignment.Center,
  trim = LineHeightStyle.Trim.Both,
)

data class ServerBadgePalette(
  val freeBg: Color,
  val freeFg: Color,
  val plusBg: Color,
  val plusFg: Color,
  val vipBg: Color,
  val vipFg: Color,
  val whiteListBg: Color = WhiteListBadgeBg,
  val whiteListFg: Color = WhiteListBadgeFg,
) {
  fun colors(tier: ServerTier? = null, whiteList: Boolean = false): Pair<Color, Color> = when {
    whiteList -> whiteListBg to whiteListFg
    tier == ServerTier.Plus -> plusBg to plusFg
    tier == ServerTier.Vip -> vipBg to vipFg
    else -> freeBg to freeFg
  }
}

@Composable
fun rememberServerBadgePalette(): ServerBadgePalette {
  val app = MaterialTheme.colorScheme
  val plus = rememberDynamicColorScheme(
    seedColor = PlusBadgeSeed,
    isDark = true,
    style = PaletteStyle.TonalSpot,
  )
  val vip = rememberDynamicColorScheme(
    seedColor = VipBadgeSeed,
    isDark = true,
    style = PaletteStyle.TonalSpot,
  )
  return remember(app.onSurface, app.onSurfaceVariant, plus, vip) {
    ServerBadgePalette(
      freeBg = app.onSurface.copy(alpha = 0.10f),
      freeFg = app.onSurfaceVariant,
      plusBg = plus.primaryContainer,
      plusFg = plus.onPrimaryContainer,
      vipBg = vip.primaryContainer,
      vipFg = vip.onPrimaryContainer,
    )
  }
}

fun TextStyle.compact(size: TextUnit? = null): TextStyle =
  copy(
    fontSize = size ?: fontSize,
    lineHeight = size ?: lineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = CompactLineHeight,
  )

@Composable
fun ServerBadge(
  label: String,
  palette: ServerBadgePalette,
  tier: ServerTier? = null,
  whiteList: Boolean = false,
  large: Boolean = false,
) {
  val (bg, fg) = palette.colors(tier, whiteList)
  Box(
    Modifier
      .then(if (large) Modifier.height(HomeBadgeHeight) else Modifier)
      .clip(RoundedCornerShape(if (large) 6.dp else 4.dp))
      .background(bg)
      .padding(horizontal = if (large) 8.dp else 4.dp, vertical = if (large) 0.dp else 1.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelSmall.compact(if (large) 12.sp else 10.sp),
      fontWeight = FontWeight.SemiBold,
      color = fg,
    )
  }
}

/** Protocol chips; OpenFlux keeps White List + SOCKS in one row. */
@Composable
fun ProtocolServerBadges(
  profile: ServerProfile,
  palette: ServerBadgePalette,
  full: Boolean = false,
  large: Boolean = false,
  gap: Dp = 4.dp,
) {
  val specs = profile.protocolBadges(full)
  Row(
    horizontalArrangement = Arrangement.spacedBy(gap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    specs.forEach { spec ->
      ServerBadge(spec.label, palette, whiteList = spec.whiteList, large = large)
    }
  }
}

/** Square favorite chip for Home server card. */
@Composable
fun FavoriteServerBadge() {
  val cs = MaterialTheme.colorScheme
  Box(
    Modifier
      .size(HomeBadgeHeight)
      .clip(RoundedCornerShape(6.dp))
      .background(cs.error.copy(alpha = 0.16f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Filled.Favorite,
      contentDescription = null,
      modifier = Modifier.size(12.dp),
      tint = cs.error,
    )
  }
}
