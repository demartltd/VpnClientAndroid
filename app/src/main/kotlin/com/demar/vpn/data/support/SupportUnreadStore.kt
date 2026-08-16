package com.demar.vpn.data.support

import com.demar.vpn.data.auth.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SupportUnreadStore {
  private val _hasUnread = MutableStateFlow(false)
  val hasUnread: StateFlow<Boolean> = _hasUnread.asStateFlow()

  fun clear() {
    _hasUnread.value = false
  }

  fun apply(tickets: List<SupportTicketSummary>) {
    _hasUnread.value = tickets.any { it.hasUnread }
  }

  suspend fun refresh(session: AuthSession) {
    val tickets = SupportApi.listTickets(session.userId, session.loginAt)
    apply(tickets)
  }
}
