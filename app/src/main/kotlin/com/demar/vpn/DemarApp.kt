package com.demar.vpn

import android.app.Application
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.vpn.VpnLogSink
import com.demar.vpn.DemarAppContextHolder as ContextHolder

class DemarApp : Application() {
  override fun onCreate() {
    super.onCreate()
    ContextHolder.init(this)
    ContextHolder.importPendingStderrIfNeeded(this)
    AppLog.init(this)
    AuthRepository.init(this)
    VpnLogSink.install()
  }
}
