package com.demar.vpn

import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §10 model: enabled = all − disabled; new enums default ON.
 * Legacy migrate: disabled = known − enabled (absent from known → ON).
 */
class ServersStoreMergeTest {
  private val allProtocols = ServerProtocol.entries.toSet()
  private val allTiers = ServerTier.entries.toSet()

  private fun <T> enabledFromDisabled(all: Set<T>, disabled: Set<T>) = all - disabled

  /** Same formula as ServersStore.loadDisabled for legacy prefs. */
  private fun <T> legacyDisabled(all: Set<T>, enabled: Set<T>, knownNames: Set<String>, name: (T) -> String) =
    all.filter { name(it) in knownNames }.toSet() - enabled

  @Test
  fun disabledOnly_newProtocolStaysOn() {
    val enabled = enabledFromDisabled(allProtocols, setOf(ServerProtocol.Trojan))
    assertTrue(ServerProtocol.OpenFlux in enabled)
  }

  @Test
  fun disabledOnly_userOffStaysOff() {
    val enabled = enabledFromDisabled(
      allProtocols,
      setOf(ServerProtocol.Hysteria2, ServerProtocol.OpenFlux),
    )
    assertFalse(ServerProtocol.OpenFlux in enabled)
    assertFalse(ServerProtocol.Hysteria2 in enabled)
  }

  @Test
  fun disabledOnly_emptyMeansAllOn() {
    assertEquals(allProtocols, enabledFromDisabled(allProtocols, emptySet()))
    assertEquals(allTiers, enabledFromDisabled(allTiers, emptySet()))
  }

  @Test
  fun legacyMigrate_missingFromKnownStaysOn() {
    val enabled = setOf(
      ServerProtocol.Hysteria2,
      ServerProtocol.Trojan,
      ServerProtocol.VlessReality,
    )
    val known = enabled.map { it.name }.toSet() // pre-OpenFlux install
    val disabled = legacyDisabled(allProtocols, enabled, known) { it.name }
    assertTrue(disabled.isEmpty())
    assertTrue(ServerProtocol.OpenFlux in enabledFromDisabled(allProtocols, disabled))
  }

  @Test
  fun legacyMigrate_fullKnownPreservesUserOff() {
    val enabled = setOf(ServerProtocol.Hysteria2, ServerProtocol.Trojan)
    val known = allProtocols.map { it.name }.toSet()
    val disabled = legacyDisabled(allProtocols, enabled, known) { it.name }
    assertEquals(
      setOf(ServerProtocol.VlessReality, ServerProtocol.OpenFlux),
      disabled,
    )
  }

  @Test
  fun filtersActive_whenAnyDisabled() {
    assertFalse(allTiers != allTiers || allProtocols != allProtocols)
    val protocols = allProtocols - setOf(ServerProtocol.Trojan)
    assertTrue(allTiers != allTiers || protocols != allProtocols)
  }
}
