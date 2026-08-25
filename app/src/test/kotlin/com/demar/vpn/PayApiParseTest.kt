package com.demar.vpn

import com.demar.vpn.data.account.PayApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayApiParseTest {
  @Test
  fun parseCatalog() {
    val catalog = PayApi.parseCatalog(
      JSONObject(
        """
        {
          "plans": [
            {
              "tier": "plus",
              "periods": [
                {"period": "month", "days": 30, "prices": {"RUB": "199.00", "EUR": "4.00"}},
                {"period": "year", "days": 365, "prices": {"RUB": "1599.00", "EUR": "32.00"}}
              ]
            },
            {
              "tier": "vip",
              "periods": [
                {"period": "month", "days": 30, "prices": {"RUB": "399.00", "EUR": "8.00"}}
              ]
            }
          ]
        }
        """.trimIndent(),
      ),
    )
    assertEquals(2, catalog.tiers.size)
    assertEquals("199.00", catalog.price("plus", "month", "RUB"))
    assertEquals("8.00", catalog.price("vip", "month", "EUR"))
    assertNull(catalog.price("free", "month", "RUB"))
  }

  @Test
  fun parseCheckout() {
    val checkout = PayApi.parseCheckout(
      JSONObject(
        """
        {
          "orderId": "ord_1",
          "payUrl": "https://rollypay.io/pay/abc",
          "amount": "199.00",
          "currency": "RUB",
          "discountPercent": 10
        }
        """.trimIndent(),
      ),
    )!!
    assertEquals("ord_1", checkout.orderId)
    assertEquals("https://rollypay.io/pay/abc", checkout.payUrl)
    assertEquals("199.00", checkout.amount)
    assertEquals("RUB", checkout.currency)
    assertEquals(10, checkout.discountPercent)
  }

  @Test
  fun parseCheckoutRejectsMissingPayUrl() {
    assertNull(PayApi.parseCheckout(JSONObject("""{"orderId":"ord_1","amount":"1.00"}""")))
  }

  @Test
  fun formatPrice() {
    assertEquals("199 ₽", PayApi.formatPrice("199.00", "RUB"))
    assertEquals("€4", PayApi.formatPrice("4.00", "EUR"))
    assertEquals("€1.5", PayApi.formatPrice("1.50", "EUR"))
  }
}
