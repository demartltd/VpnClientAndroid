package com.demar.vpn.core.servers

import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ShadowTlsProfile

val testServers = listOf(
  ServerProfile.shadowTlsFromTag(
    ShadowTlsProfile(
      displayName = "FI-DEV-99",
      server = "fi-f-2.xyzdeer.space",
      port = 8443,
      password = "REDACTED",
      tlsServerName = "www.microsoft.com",
      ssMethod = "2022-blake3-aes-128-gcm",
      ssPassword = "qgt6dEYUp+5a7oaBuXoXpA==",
    ),
    city = "Helsinki",
  ),
  ServerProfile.hysteria2FromTag(
    Hysteria2Profile(
      displayName = "FI-FREE-89",
      server = "fi-f-1.xyzdeer.space",
      port = 8443,
      password = "REDACTED",
      tlsServerName = "fi-f-1.xyzdeer.space",
      alpn = listOf("h3"),
    ),
    city = "Helsinki",
  ),
)
