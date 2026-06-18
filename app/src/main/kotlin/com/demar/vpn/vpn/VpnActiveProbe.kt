package com.demar.vpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object VpnActiveProbe {
  fun isActive(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
  }
}
