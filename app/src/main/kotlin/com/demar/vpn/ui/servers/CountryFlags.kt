package com.demar.vpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demar.vpn.R

/** Twemoji COLR subset (flags only) — flat flags, closer to iOS than default Android emoji. */
val TwemojiCountryFlagFont = FontFamily(Font(R.font.twemoji_country_flags))

fun countryCodeToFlagEmoji(countryCode: String?): String {
  val code = countryCode?.trim()?.uppercase().orEmpty()
  if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
  val first = Character.toChars(0x1F1E6 + (code[0] - 'A'))
  val second = Character.toChars(0x1F1E6 + (code[1] - 'A'))
  return String(first) + String(second)
}

@Composable
fun CountryFlag(countryCode: String?, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val flagEmoji = countryCodeToFlagEmoji(countryCode)
  Box(
    modifier
      .size(36.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(cs.primary.copy(alpha = 0.1f)),
    contentAlignment = Alignment.Center,
  ) {
    if (flagEmoji.isNotEmpty()) {
      Text(
        text = flagEmoji,
        fontFamily = TwemojiCountryFlagFont,
        fontSize = 22.sp,
        style = MaterialTheme.typography.bodyMedium.copy(
          platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
      )
    } else {
      Text(
        countryCode?.uppercase().orEmpty().ifEmpty { "?" },
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = cs.primary,
      )
    }
  }
}
