package com.demar.vpn

import com.demar.vpn.domain.content.LegalDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemarNavigationTest {
  @Test
  fun parseIncomingRoute_readsAuthCallback() {
    val route = parseIncomingRoute("demarvpn://auth/callback?token=abc") as IncomingRoute.Auth
    assertEquals(AuthHandoff.ClientToken("abc"), route.handoff)
  }

  @Test
  fun parseIncomingRoute_readsExchangeCode() {
    val route = parseIncomingRoute("demarvpn://auth/callback?code=xyz") as IncomingRoute.Auth
    assertEquals(AuthHandoff.ExchangeCode("xyz"), route.handoff)
  }

  @Test
  fun parseIncomingRoute_readsSettingsSupportTicket() {
    val route = parseIncomingRoute("demarvpn://settings/support/ticket/42") as IncomingRoute.Settings
    assertEquals(SettingsRoute.Support(SupportRoute.Chat("42")), route.route)
  }

  @Test
  fun parseIncomingRoute_readsOpenPrefixDocument() {
    val route = parseIncomingRoute("demarvpn://open/settings/documents/Terms") as IncomingRoute.Settings
    assertEquals(SettingsRoute.Document(LegalDocument.Terms), route.route)
  }

  @Test
  fun parseIncomingRoute_readsSettingsTraffic() {
    val route = parseIncomingRoute("demarvpn://settings/traffic") as IncomingRoute.Settings
    assertEquals(SettingsRoute.Traffic, route.route)
  }

  @Test
  fun parseIncomingRoute_readsAccountBuy() {
    val route = parseIncomingRoute("demarvpn://account/buy") as IncomingRoute.Account
    assertEquals(AccountSheet.Buy, route.sheet)
  }

  @Test
  fun parseIncomingRoute_readsAccountPlans() {
    val route = parseIncomingRoute("demarvpn://account/plans") as IncomingRoute.Account
    assertEquals(AccountSheet.Plans, route.sheet)
  }

  @Test
  fun parseIncomingRoute_readsAccountTraffic() {
    val route = parseIncomingRoute("demarvpn://account/traffic") as IncomingRoute.Account
    assertEquals(AccountSheet.Traffic, route.sheet)
  }

  @Test
  fun parseIncomingRoute_readsPayOk() {
    val route = parseIncomingRoute("demarvpn://pay/ok") as IncomingRoute.PayReturn
    assertEquals(true, route.success)
  }

  @Test
  fun parseIncomingRoute_readsPayFail() {
    val route = parseIncomingRoute("demarvpn://pay/fail") as IncomingRoute.PayReturn
    assertEquals(false, route.success)
  }

  @Test
  fun parseIncomingRoute_rejectsUnknownLinks() {
    assertNull(parseIncomingRoute("demarvpn://settings/unknown"))
    assertNull(parseIncomingRoute("https://account.example.invalid/auth/app/complete"))
    assertNull(parseIncomingRoute("demar://auth/callback?code=xyz"))
    assertNull(parseIncomingRoute("demarvpn://pay/unknown"))
    assertNull(parseIncomingRoute("demarvpn://account/unknown"))
  }
}
