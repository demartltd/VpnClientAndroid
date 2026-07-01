package com.demar.vpn.vpn

import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import android.util.Base64
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class DemarLibboxPlatform(
  private val service: VpnService,
  private val onTunEstablished: (Int) -> Unit,
) : PlatformInterface {
  private val main = Handler(Looper.getMainLooper())

  var tunFd: Int = -1
    private set

  private var tunPfd: ParcelFileDescriptor? = null

  fun closeTun() {
    tunPfd?.close()
    tunPfd = null
    tunFd = -1
  }

  override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

  override fun autoDetectInterfaceControl(fd: Int) {
    service.protect(fd)
  }

  override fun openTun(options: TunOptions): Int {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      return openTunOnMain(options)
    }
    val latch = CountDownLatch(1)
    val out = intArrayOf(-1)
    val err = arrayOf<Throwable?>(null)
    main.post {
      try {
        out[0] = openTunOnMain(options)
      } catch (t: Throwable) {
        err[0] = t
      } finally {
        latch.countDown()
      }
    }
    check(latch.await(30, TimeUnit.SECONDS)) { "android: openTun timed out" }
    err[0]?.let { throw it }
    return out[0]
  }

  private fun openTunOnMain(options: TunOptions): Int {
    val builder =
      service.Builder()
        .setSession("Demar VPN")
        .setMtu(options.mtu)
        .addDisallowedApplication(service.packageName)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      builder.setMetered(false)
    }

    val ipv4 = mutableListOf<Pair<String, Int>>()
    val inet4 = options.inet4Address
    while (inet4.hasNext()) {
      val addr = inet4.next()
      ipv4.add(addr.address() to addr.prefix())
      builder.addAddress(addr.address(), addr.prefix())
    }

    val inet6 = options.inet6Address
    while (inet6.hasNext()) {
      val addr = inet6.next()
      builder.addAddress(addr.address(), addr.prefix())
    }

    if (options.autoRoute) {
      builder.addDnsServer(options.dnsServerAddress.value)

      val inet4Route = options.inet4RouteAddress
      if (inet4Route.hasNext()) {
        while (inet4Route.hasNext()) {
          builder.addRoute(inet4Route.next().toIpPrefix())
        }
      } else if (ipv4.isNotEmpty()) {
        builder.addRoute("0.0.0.0", 0)
      }

      val inet6Route = options.inet6RouteAddress
      if (inet6Route.hasNext()) {
        while (inet6Route.hasNext()) {
          builder.addRoute(inet6Route.next().toIpPrefix())
        }
      }

      val exclude4 = options.inet4RouteExcludeAddress
      while (exclude4.hasNext()) {
        builder.excludeRoute(exclude4.next().toIpPrefix())
      }

      val exclude6 = options.inet6RouteExcludeAddress
      while (exclude6.hasNext()) {
        builder.excludeRoute(exclude6.next().toIpPrefix())
      }
    }

    closeTun()
    val pfd = builder.establish() ?: throw IllegalStateException("android: VPN establish() returned null")
    tunPfd = pfd
    tunFd = pfd.fd
    onTunEstablished(tunFd)
    return pfd.fd
  }

  override fun useProcFS(): Boolean = false

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun findConnectionOwner(
    ipProtocol: Int,
    sourceAddress: String,
    sourcePort: Int,
    destinationAddress: String,
    destinationPort: Int,
  ): ConnectionOwner {
    val cm = service.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager
    val uid =
      cm.getConnectionOwnerUid(
        ipProtocol,
        InetSocketAddress(sourceAddress, sourcePort),
        InetSocketAddress(destinationAddress, destinationPort),
      )
    if (uid == Process.INVALID_UID) {
      throw Exception("android: connection owner not found")
    }
    val packages = service.packageManager.getPackagesForUid(uid)
    val owner = ConnectionOwner()
    owner.userId = uid
    owner.userName = packages?.firstOrNull().orEmpty()
    owner.setAndroidPackageNames(
      StringArray(packages?.toList()?.iterator() ?: emptyList<String>().iterator()),
    )
    return owner
  }

  override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
    DefaultInterfaceMonitor.attach(listener)
  }

  override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
    DefaultInterfaceMonitor.detach(listener)
  }

  override fun getInterfaces(): NetworkInterfaceIterator {
    val cm = service.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networks = cm.allNetworks
    val systemIfaces = NetworkInterface.getNetworkInterfaces().toList()
    val out = mutableListOf<LibboxNetworkInterface>()
    for (network in networks) {
      val link = cm.getLinkProperties(network) ?: continue
      val caps = cm.getNetworkCapabilities(network) ?: continue
      val name = link.interfaceName ?: continue
      val sys = systemIfaces.find { it.name == name } ?: continue
      val item = LibboxNetworkInterface()
      item.name = name
      item.index = sys.index
      item.mtu = runCatching { sys.mtu }.getOrDefault(1500)
      item.type =
        when {
          caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
          caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
          caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
          else -> Libbox.InterfaceTypeOther
        }
      item.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
      item.dnsServer = StringArray(link.dnsServers.mapNotNull { it.hostAddress }.iterator())
      item.addresses =
        StringArray(sys.interfaceAddresses.map { it.toPrefix() }.iterator())
      var flags = 0
      if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
      }
      if (sys.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
      if (sys.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
      if (sys.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
      item.flags = flags
      out.add(item)
    }
    return IteratorWrapper(out.iterator())
  }

  override fun underNetworkExtension(): Boolean = false

  override fun includeAllNetworks(): Boolean = false

  override fun clearDNSCache() {}

  override fun readWIFIState(): WIFIState? = null

  override fun localDNSTransport(): LocalDNSTransport? = LocalResolver

  override fun systemCertificates(): StringIterator {
    val certificates = mutableListOf<String>()
    runCatching {
      val keyStore = KeyStore.getInstance("AndroidCAStore")
      keyStore.load(null, null)
      val aliases = keyStore.aliases()
      while (aliases.hasMoreElements()) {
        val cert = keyStore.getCertificate(aliases.nextElement())
        certificates.add(
          "-----BEGIN CERTIFICATE-----\n" +
            Base64.encodeToString(cert.encoded, Base64.NO_WRAP) +
            "\n-----END CERTIFICATE-----",
        )
      }
    }
    return StringArray(certificates.iterator())
  }

  override fun sendNotification(notification: Notification) {}

  class StringArray(private val iterator: Iterator<String>) : StringIterator {
    override fun len(): Int = 0
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): String = iterator.next()
  }

  private class IteratorWrapper(
    private val iterator: Iterator<LibboxNetworkInterface>,
  ) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): LibboxNetworkInterface = iterator.next()
  }

  private fun InterfaceAddress.toPrefix(): String =
    if (address is Inet6Address) {
      "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
      "${address.hostAddress}/$networkPrefixLength"
    }

  private fun io.nekohasekai.libbox.RoutePrefix.toIpPrefix(): IpPrefix {
    return IpPrefix(java.net.InetAddress.getByName(address()), prefix())
  }
}

internal object DefaultInterfaceMonitor {
  private const val LOSS_DEBOUNCE_MS = 3000L

  private val main = Handler(Looper.getMainLooper())
  private var connectivityManager: ConnectivityManager? = null
  private var callback: ConnectivityManager.NetworkCallback? = null
  private var listener: InterfaceUpdateListener? = null
  @Volatile private var activeOwner: Long = 0L
  @Volatile var defaultNetwork: Network? = null
    private set
  private var registered = false
  private var lossRunnable: Runnable? = null
  private var lastReported: InterfaceSnapshot? = null
  private var lossPending = false

  private data class InterfaceSnapshot(
    val name: String,
    val index: Int,
    val isExpensive: Boolean,
    val isConstrained: Boolean,
    val networkReady: Boolean,
  ) {
    val isLost: Boolean get() = index < 0
    val isReady: Boolean get() = !isLost && index > 0 && networkReady
  }

  fun ensureStarted(owner: Long, service: VpnService) {
    if (registered && activeOwner == owner) return
    val latch = CountDownLatch(1)
    main.post {
      try {
        if (registered && activeOwner != owner) {
          runCatching {
            val cb = callback
            val cm = connectivityManager
            if (cb != null && cm != null) cm.unregisterNetworkCallback(cb)
          }
          callback = null
          connectivityManager = null
          registered = false
        }
        if (!registered) {
          val cm = service.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager
          connectivityManager = cm
          val cb =
            object : ConnectivityManager.NetworkCallback() {
              override fun onAvailable(network: Network) = report()

              override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = report()

              override fun onLost(network: Network) = report()
            }
          callback = cb
          cm.registerDefaultNetworkCallback(cb, main)
          registered = true
          activeOwner = owner
          report()
        }
      } finally {
        latch.countDown()
      }
    }
    latch.await(5, TimeUnit.SECONDS)
  }

  fun awaitReady(timeoutMs: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val cm = connectivityManager ?: return false
      val snap = readSnapshot(cm)
      if (snap.isReady && defaultNetwork != null) return true
      Thread.sleep(50)
    }
    return defaultNetwork != null
  }

  fun stop(owner: Long) {
    if (activeOwner != owner) return
    val latch = CountDownLatch(1)
    main.post {
      try {
        cancelLossDebounce()
        listener = null
        lastReported = null
        lossPending = false
        defaultNetwork = null
        val cb = callback
        val cm = connectivityManager
        callback = null
        connectivityManager = null
        registered = false
        activeOwner = 0L
        if (cb != null && cm != null) {
          runCatching { cm.unregisterNetworkCallback(cb) }
        }
      } finally {
        latch.countDown()
      }
    }
    latch.await(5, TimeUnit.SECONDS)
  }

  fun attach(listener: InterfaceUpdateListener) {
    main.post {
      this.listener = listener
      report()
    }
  }

  fun detach(listener: InterfaceUpdateListener) {
    main.post {
      if (this.listener === listener) {
        this.listener = null
      }
    }
  }

  private fun cancelLossDebounce() {
    lossRunnable?.let { main.removeCallbacks(it) }
    lossRunnable = null
  }

  private fun report() {
    val target = listener ?: return
    val cm = connectivityManager ?: return
    val snapshot = readSnapshot(cm)
    if (snapshot.isLost) {
      if (lastReported?.isLost == true || lossPending) return
      lossPending = true
      cancelLossDebounce()
      val runnable =
        Runnable {
          lossRunnable = null
          if (!lossPending) return@Runnable
          lossPending = false
          defaultNetwork = null
          val lost = InterfaceSnapshot("", -1, false, false, false)
          lastReported = lost
          target.updateDefaultInterface(lost.name, lost.index, lost.isExpensive, lost.isConstrained)
        }
      lossRunnable = runnable
      main.postDelayed(runnable, LOSS_DEBOUNCE_MS)
      return
    }
    cancelLossDebounce()
    lossPending = false
    if (snapshot == lastReported) return
    lastReported = snapshot
    target.updateDefaultInterface(
      snapshot.name,
      snapshot.index,
      snapshot.isExpensive,
      snapshot.isConstrained,
    )
  }

  private fun readSnapshot(cm: ConnectivityManager): InterfaceSnapshot {
    val active = cm.activeNetwork ?: return InterfaceSnapshot("", -1, false, false, false)
    val link = cm.getLinkProperties(active)
    val caps = cm.getNetworkCapabilities(active)
    val name = link?.interfaceName.orEmpty()
    val index =
      runCatching {
        if (name.isEmpty()) -1 else NetworkInterface.getByName(name)?.index ?: -1
      }.getOrDefault(-1)
    val isExpensive =
      caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
    val networkReady =
      caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        (
          caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
          )
    if (networkReady && caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true) {
      defaultNetwork = active
    }
    return InterfaceSnapshot(name, index, isExpensive, false, networkReady)
  }
}
