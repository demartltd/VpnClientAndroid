package com.demar.vpn.domain.vpn

object SingBoxTags {
  const val TUN_INBOUND = "demar-tun"
  const val DIAG_INBOUND = "diag-in"
  const val DIAG_LISTEN_HOST = "127.0.0.1"
  const val DIAG_LISTEN_PORT = 10808
  const val CLASH_API_HOST = "127.0.0.1"
  const val CLASH_API_PORT = 19090
  const val PROXY = "proxy"
  const val DIRECT = "direct"
  const val DNS_DIRECT = "dns-direct"
  const val DNS_REMOTE = "dns-remote"
  /** Shared HTTP client for remote rule-set downloads (sing-box 1.14+). */
  const val HTTP_RS_DL = "rs-dl"
}
