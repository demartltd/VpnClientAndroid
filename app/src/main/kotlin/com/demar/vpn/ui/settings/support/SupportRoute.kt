package com.demar.vpn.ui.settings.support

internal sealed interface SupportRoute {
  data object List : SupportRoute
  /** Existing ticket chat. */
  data class Chat(val ticketId: String) : SupportRoute
  /** New ticket draft: created on first send. */
  data class Draft(val themeId: String) : SupportRoute
}
