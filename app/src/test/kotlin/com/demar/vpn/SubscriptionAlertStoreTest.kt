package com.demar.vpn

import com.demar.vpn.data.account.SubscriptionAlertStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SubscriptionAlertStoreTest {
  @Test
  fun shouldShowExpiry_earlyAndFinalWindows() {
    val now = Instant.parse("2026-10-08T12:00:00Z")
    // 48h — early window (25h, 72h]
    assertTrue(SubscriptionAlertStore.shouldShowExpiry("2026-10-10T12:00:00Z", now))
    // 72h — edge of early window
    assertTrue(SubscriptionAlertStore.shouldShowExpiry("2026-10-11T12:00:00Z", now))
    // 73h — too early
    assertFalse(SubscriptionAlertStore.shouldShowExpiry("2026-10-11T13:00:00Z", now))
    // 25h — silent gap (18h, 25h]
    assertFalse(SubscriptionAlertStore.shouldShowExpiry("2026-10-09T13:00:00Z", now))
    // 20h — silent gap
    assertFalse(SubscriptionAlertStore.shouldShowExpiry("2026-10-09T08:00:00Z", now))
    // 18h — final window
    assertTrue(SubscriptionAlertStore.shouldShowExpiry("2026-10-09T06:00:00Z", now))
    // 12h — final window
    assertTrue(SubscriptionAlertStore.shouldShowExpiry("2026-10-09T00:00:00Z", now))
    // already expired
    assertFalse(SubscriptionAlertStore.shouldShowExpiry("2026-10-08T11:00:00Z", now))
  }

  @Test
  fun shouldShowTraffic_under3GiB() {
    val threeGiB = 3L shl 30
    assertTrue(SubscriptionAlertStore.shouldShowTraffic(threeGiB - 1))
    assertFalse(SubscriptionAlertStore.shouldShowTraffic(threeGiB))
    assertFalse(SubscriptionAlertStore.shouldShowTraffic(4L shl 30))
  }

  @Test
  fun remainingTime_hoursOrMinutes() {
    val hours = SubscriptionAlertStore.remainingTime(5L * 60 * 60 * 1000)
    assertTrue(hours.hours)
    assertEquals(5, hours.amount)

    val minutes = SubscriptionAlertStore.remainingTime(45L * 60 * 1000)
    assertFalse(minutes.hours)
    assertEquals(45, minutes.amount)

    val edge = SubscriptionAlertStore.remainingTime(59L * 60 * 1000)
    assertFalse(edge.hours)
    assertEquals(59, edge.amount)
  }
}
