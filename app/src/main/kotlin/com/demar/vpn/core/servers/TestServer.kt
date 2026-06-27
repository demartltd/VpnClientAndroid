package com.demar.vpn.core.servers

import com.demar.vpn.domain.vpn.NaiveProfile
import com.demar.vpn.domain.vpn.ServerProfile

val testServers = listOf(
  ServerProfile.naive(
    NaiveProfile(
      displayName = "FIN-DEV-1",
      server = "193.35.17.5",
      port = 443,
      username = "u_e06786d0488ce48f",
      password = "REDACTED",
      tlsServerName = "backronym.ru",
    ),
  ),
  ServerProfile.naive(
    NaiveProfile(
      displayName = "DE-FREE-1",
      server = "de-f-1.backronym.ru",
      port = 443,
      username = "u_5f0d9beeab98bf6c",
      password = "REDACTED",
      tlsServerName = "de-f-1.backronym.ru",
    ),
  ),
)
