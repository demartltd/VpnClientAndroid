package com.demar.vpn

import com.demar.vpn.domain.vpn.VpnEntitlements
import com.demar.vpn.domain.vpn.tun.TemporaryOffPolicy
import com.demar.vpn.domain.vpn.tun.TunRegions
import com.demar.vpn.domain.vpn.tun.TunSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryOffPolicyTest {
  @Test
  fun defaultStoredOn_staysOn() {
    assertTrue(TemporaryOffPolicy.effectiveEnabled(storedEnabled = true, disabledAtMs = 0, nowMs = 1_000))
  }

  @Test
  fun offWithinWindow_staysOff() {
    val t0 = 1_000_000L
    assertFalse(
      TemporaryOffPolicy.effectiveEnabled(
        storedEnabled = false,
        disabledAtMs = t0,
        nowMs = t0 + TemporaryOffPolicy.AUTO_REENABLE_MS - 1,
      ),
    )
  }

  @Test
  fun offAfterWindow_becomesOn() {
    val t0 = 1_000_000L
    assertTrue(
      TemporaryOffPolicy.effectiveEnabled(
        storedEnabled = false,
        disabledAtMs = t0,
        nowMs = t0 + TemporaryOffPolicy.AUTO_REENABLE_MS,
      ),
    )
  }

  @Test
  fun remainingDisableMs() {
    val t0 = 1_000_000L
    assertEquals(
      TemporaryOffPolicy.AUTO_REENABLE_MS - 5_000,
      TemporaryOffPolicy.remainingDisableMs(t0, t0 + 5_000),
    )
    assertEquals(0L, TemporaryOffPolicy.remainingDisableMs(t0, t0 + TemporaryOffPolicy.AUTO_REENABLE_MS + 1))
  }

  @Test
  fun autoReenableIs10Minutes() {
    assertEquals(10L * 60L * 1000L, TemporaryOffPolicy.AUTO_REENABLE_MS)
  }
}

class VpnEntitlementsTest {
  @Test
  fun canToggleRegion_entitledPlans() {
    for (sub in listOf("plus", "VIP", "Dev", "tester", "TEST", " Plus ")) {
      assertTrue(sub, VpnEntitlements.canToggleRegion(TunRegions.RU, sub))
      assertTrue(sub, VpnEntitlements.canToggleRegion(TunRegions.CN, sub))
      assertTrue(sub, VpnEntitlements.canToggleRegion(TunRegions.IR, sub))
    }
  }

  @Test
  fun canToggleRegion_freeAndNull() {
    assertFalse(VpnEntitlements.canToggleRegion(TunRegions.RU, null))
    assertFalse(VpnEntitlements.canToggleRegion(TunRegions.CN, "free"))
    assertFalse(VpnEntitlements.canToggleRegion(TunRegions.IR, ""))
  }

  @Test
  fun effective_freeAlwaysHasAllCatalogRegions() {
    val off = TunSettings(bypassRegions = emptySet())
    val eff = VpnEntitlements.effective(off, "free")
    assertTrue(eff.bypassRegions.containsAll(TunRegions.ids))
    assertTrue(VpnEntitlements.effective(off, null).bypassRegions.containsAll(TunRegions.ids))
  }

  @Test
  fun effective_entitledFollowsStored() {
    val off = TunSettings(bypassRegions = emptySet())
    val on = TunSettings(bypassRegions = setOf(TunRegions.CN))
    assertTrue(VpnEntitlements.effective(off, "plus").bypassRegions.isEmpty())
    assertEquals(setOf(TunRegions.CN), VpnEntitlements.effective(on, "vip").bypassRegions)
  }
}
