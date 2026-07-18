package com.demar.vpn.domain.content

import androidx.annotation.StringRes
import com.demar.vpn.R

enum class LegalDocument(
  @param:StringRes val titleRes: Int,
  @param:StringRes val subtitleRes: Int,
  val url: String,
) {
  Terms(
    titleRes = R.string.settings_terms,
    subtitleRes = R.string.settings_terms_sub,
    url = "https://raw.githubusercontent.com/demartltd/tos-pp/refs/heads/main/ToS.md",
  ),
  Privacy(
    titleRes = R.string.settings_privacy,
    subtitleRes = R.string.settings_privacy_sub,
    url = "https://raw.githubusercontent.com/demartltd/tos-pp/refs/heads/main/pp.md",
  ),
}
