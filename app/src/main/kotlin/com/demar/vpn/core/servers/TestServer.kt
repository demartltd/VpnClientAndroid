package com.demar.vpn.core.servers

import com.demar.vpn.domain.vpn.NaiveProfile
import com.demar.vpn.domain.vpn.ServerProfile

val testServers = listOf(
  ServerProfile.naive(
    NaiveProfile(
      displayName = "backronym.ru",
      server = "193.35.17.5",
      port = 443,
      username = "u_e06786d0488ce48f",
      password = "QmKxb6pt1LfINYKak8CUdO3qFw4dxvuP",
      tlsServerName = "backronym.ru",
    ),
  ),
  ServerProfile.naive(
    NaiveProfile(
      displayName = "de-f-1.backronym.ru",
      server = "de-f-1.backronym.ru",
      port = 443,
      username = "u_5f0d9beeab98bf6c",
      password = "AjcvRkaQALvRlyEqCUZznp7bX66-6vbF",
      tlsServerName = "de-f-1.backronym.ru",
    ),
  ),
)
