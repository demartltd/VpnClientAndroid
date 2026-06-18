package com.demar.vpn.vpn

import com.demar.vpn.data.logging.AppLog

internal object VpnLogSink {
  fun install() {
    VpnEventStream.addListener { e ->
      if (e["type"] == "log") (e["line"] as? String)?.let(AppLog::append)
    }
  }
}
