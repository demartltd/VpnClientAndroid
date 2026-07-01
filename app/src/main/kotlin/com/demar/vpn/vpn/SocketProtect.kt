package com.demar.vpn.vpn

import java.io.FileDescriptor
import java.net.Socket

internal object SocketProtect {
  fun protect(socket: Socket, protectFd: ((Int) -> Boolean)?) {
    if (protectFd == null) return
    fd(socket)?.let(protectFd)
  }

  private fun fd(socket: Socket): Int? =
    runCatching {
      val implField = Socket::class.java.getDeclaredField("impl")
      implField.isAccessible = true
      val socketImpl = implField.get(socket) ?: return@runCatching null
      val fdField = socketImpl.javaClass.getDeclaredField("fd")
      fdField.isAccessible = true
      val descriptor = fdField.get(socketImpl) as? FileDescriptor ?: return@runCatching null
      val intField = FileDescriptor::class.java.getDeclaredField("descriptor")
      intField.isAccessible = true
      intField.getInt(descriptor)
    }.getOrNull()
}
