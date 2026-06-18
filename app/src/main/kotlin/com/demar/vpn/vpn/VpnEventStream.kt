package com.demar.vpn.vpn

import android.os.Handler
import android.os.Looper
import com.demar.vpn.DemarAppContextHolder
import java.util.concurrent.CopyOnWriteArrayList

/** Main-thread VPN state/log events for Compose UI. */
internal object VpnEventStream {
  private val main = Handler(Looper.getMainLooper())
  private val listeners = CopyOnWriteArrayList<(Map<String, Any?>) -> Unit>()

  @Volatile
  private var lastStateEvent: Map<String, Any?>? = null

  fun addListener(listener: (Map<String, Any?>) -> Unit): () -> Unit {
    listeners.add(listener)
    main.post {
      lastStateEvent?.let { listener(HashMap(it)) }
    }
    return { listeners.remove(listener) }
  }

  fun emit(event: Map<String, Any?>) {
    main.post {
      if ((event["type"] as? String) == "state") {
        lastStateEvent = HashMap(event)
      }
      if (listeners.isNotEmpty()) {
        listeners.forEach { it(event) }
      } else if ((event["type"] as? String) == "log") {
        val line = (event["line"] as? String)?.trim().orEmpty()
        if (line.isNotEmpty()) {
          DemarAppContextHolder.getOrNull()?.let { PendingNativeDiag.append(it, line) }
        }
      }
    }
  }

  fun vpnStateSnapshot(context: android.content.Context): Map<String, Any?>? {
    lastStateSnapshot()?.let { return it }
    val stored = VpnControlStateStore.loadState(context)
    if (!stored.isActive) {
      return stateEvent(VpnControlStateStore.State.Disconnected)
    }
    if (VpnActiveProbe.isActive(context)) {
      return stateEvent(stored)
    }
    return stateEvent(VpnControlStateStore.State.Disconnected)
  }

  private fun stateEvent(state: VpnControlStateStore.State, message: String = ""): Map<String, Any?> =
    mapOf("type" to "state", "state" to state.raw, "message" to message)

  fun lastStateSnapshot(): Map<String, Any?>? {
    val snap = lastStateEvent ?: return null
    return HashMap(snap)
  }
}
