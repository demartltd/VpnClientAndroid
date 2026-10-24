package com.demar.vpn

import com.demar.vpn.data.servers.ServersStore
import com.demar.vpn.domain.vpn.ServerProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServersStoreMergeTest {
  @Test
  fun mergeNewEnums_enablesProtocolAddedAfterPrefsSaved() {
    val saved = setOf(
      ServerProtocol.Hysteria2,
      ServerProtocol.Trojan,
      ServerProtocol.VlessReality,
    )
    val previouslyKnown = saved.map { it.name }.toSet()
    val merged = ServersStore.mergeNewEnums(
      saved,
      previouslyKnown,
      ServerProtocol.entries.toSet(),
    ) { it.name }
    assertTrue(ServerProtocol.OpenFlux in merged)
    assertEquals(ServerProtocol.entries.toSet(), merged)
  }

  @Test
  fun mergeNewEnums_keepsUserDisabledAfterKnown() {
    val saved = setOf(ServerProtocol.Hysteria2, ServerProtocol.Trojan)
    val previouslyKnown = ServerProtocol.entries.map { it.name }.toSet()
    val merged = ServersStore.mergeNewEnums(
      saved,
      previouslyKnown,
      ServerProtocol.entries.toSet(),
    ) { it.name }
    assertEquals(saved, merged)
  }
}
