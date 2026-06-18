package com.demar.vpn.core.servers

import com.demar.vpn.domain.vpn.NaiveProfile
import com.demar.vpn.domain.vpn.ServerProfile

val testNaiveProfile = NaiveProfile(
  displayName = "backronym.ru",
  server = "193.35.17.5",
  port = 443,
  username = "u_e06786d0488ce48f",
  password = "QmKxb6pt1LfINYKak8CUdO3qFw4dxvuP",
  tlsServerName = "backronym.ru",
)

val testServers = listOf(ServerProfile.naive(testNaiveProfile))
