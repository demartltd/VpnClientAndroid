package com.demar.vpn.ui.servers

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
