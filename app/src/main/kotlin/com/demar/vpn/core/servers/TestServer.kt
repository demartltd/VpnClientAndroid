package com.demar.vpn.core.servers

import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.NaiveProfile
import com.demar.vpn.domain.vpn.ServerProfile

val testServers = listOf(
  ServerProfile.naiveFromTag(
    NaiveProfile(
      displayName = "FI-DEV-99",
      server = "backronym.ru",
      port = 8443,
      username = "u_e06786d0488ce48f",
      password = "QmKxb6pt1LfINYKak8CUdO3qFw4dxvuP",
      tlsServerName = "backronym.ru",
    ),
    city = "Helsinki",
  ),
  ServerProfile.naiveFromTag(
    NaiveProfile(
      displayName = "SE-FREE-71",
      server = "swe-f-1.backronym.ru",
      port = 8443,
      username = "u_7e870100ee3ef828",
      password = "cPtKkgK5ALTDhAgogOElOuBQS-auN1h8",
      tlsServerName = "swe-f-1.backronym.ru",
    ),
    city = "Stockholm",
  ),
  ServerProfile.naiveFromTag(
    NaiveProfile(
      displayName = "GR-FREE-22",
      server = "ne-f-1.backronym.ru",
      port = 8443,
      username = "u_0dd19c46df7fc81a",
      password = "ukENr-LbKZ_Tjrn6V5yoTgnTJZtK6pXF",
      tlsServerName = "ne-f-1.backronym.ru",
    ),
    city = "Athens",
  ),
  ServerProfile.naiveFromTag(
    NaiveProfile(
      displayName = "DE-FREE-1",
      server = "de-f-1.backronym.ru",
      port = 8443,
      username = "u_5f0d9beeab98bf6c",
      password = "AjcvRkaQALvRlyEqCUZznp7bX66-6vbF",
      tlsServerName = "de-f-1.backronym.ru",
    ),
    city = "Berlin",
  ),
  ServerProfile.hysteria2FromTag(
    Hysteria2Profile(
      displayName = "FI-FREE-89",
      server = "fi-f-1.xyzdeer.space",
      port = 8443,
      password = "AX9shERdOhYTWhsUZ21_8dY3ZCBWyTTN",
      tlsServerName = "fi-f-1.xyzdeer.space",
      alpn = listOf("h3"),
    ),
    city = "Helsinki",
  ),
)
