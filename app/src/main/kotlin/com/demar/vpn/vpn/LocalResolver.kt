package com.demar.vpn.vpn

import android.net.DnsResolver
import android.os.CancellationSignal
import android.system.ErrnoException
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal object LocalResolver : LocalDNSTransport {
  private const val RCODE_NXDOMAIN = 3
  private const val QUERY_TIMEOUT_SEC = 5L
  private val executor = Executors.newCachedThreadPool()

  override fun raw(): Boolean = true

  override fun exchange(ctx: ExchangeContext, message: ByteArray) {
    val network = DefaultInterfaceMonitor.defaultNetwork ?: error("missing default network")
    val signal = CancellationSignal()
    ctx.onCancel(signal::cancel)
    val latch = CountDownLatch(1)
    val error = AtomicReference<Throwable?>(null)
    DnsResolver.getInstance().rawQuery(
      network,
      message,
      DnsResolver.FLAG_NO_RETRY,
      executor,
      signal,
      object : DnsResolver.Callback<ByteArray> {
        override fun onAnswer(answer: ByteArray, rcode: Int) {
          if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
          latch.countDown()
        }

        override fun onError(err: DnsResolver.DnsException) {
          val cause = err.cause
          if (cause is ErrnoException) {
            ctx.errnoCode(cause.errno)
            latch.countDown()
          } else {
            error.set(err)
            latch.countDown()
          }
        }
      },
    )
    if (!latch.await(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)) error("local dns exchange timeout")
    error.get()?.let { throw it }
  }

  override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
    val net = DefaultInterfaceMonitor.defaultNetwork ?: error("missing default network")
    val signal = CancellationSignal()
    ctx.onCancel(signal::cancel)
    val latch = CountDownLatch(1)
    val error = AtomicReference<Throwable?>(null)
    val callback =
      object : DnsResolver.Callback<Collection<InetAddress>> {
        override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
          if (rcode == 0) {
            ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n"))
          } else {
            ctx.errorCode(rcode)
          }
          latch.countDown()
        }

        override fun onError(err: DnsResolver.DnsException) {
          val cause = err.cause
          if (cause is ErrnoException) {
            ctx.errnoCode(cause.errno)
            latch.countDown()
          } else {
            error.set(err)
            latch.countDown()
          }
        }
      }
    val type =
      when {
        network.endsWith("4") -> DnsResolver.TYPE_A
        network.endsWith("6") -> DnsResolver.TYPE_AAAA
        else -> null
      }
    if (type != null) {
      DnsResolver.getInstance().query(net, domain, type, DnsResolver.FLAG_NO_RETRY, executor, signal, callback)
    } else {
      DnsResolver.getInstance().query(net, domain, DnsResolver.FLAG_NO_RETRY, executor, signal, callback)
    }
    if (!latch.await(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)) error("local dns lookup timeout")
    error.get()?.let { throw it }
  }
}
