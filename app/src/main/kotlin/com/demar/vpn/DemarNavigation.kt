package com.demar.vpn

import android.net.Uri
import com.demar.vpn.domain.content.LegalDocument
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DemarUrlScheme {
  const val Name = "demarvpn"
}

sealed interface AuthHandoff {
  data class ExchangeCode(val code: String) : AuthHandoff
  data class ClientToken(val token: String) : AuthHandoff
}

sealed interface SupportRoute {
  data class Chat(val ticketId: String) : SupportRoute
  data class Draft(val themeId: String) : SupportRoute
}

sealed interface SettingsRoute {
  data object AppSettings : SettingsRoute
  data object ServiceMenu : SettingsRoute
  data object Traffic : SettingsRoute
  data class Support(val route: SupportRoute? = null) : SettingsRoute
  data class Document(val document: LegalDocument) : SettingsRoute
}

enum class AccountSheet {
  Traffic,
  Buy,
  Plans,
}

sealed interface IncomingRoute {
  data class Auth(val handoff: AuthHandoff) : IncomingRoute
  data class Settings(val route: SettingsRoute) : IncomingRoute
  data class Account(val sheet: AccountSheet) : IncomingRoute
  data class PayReturn(val success: Boolean) : IncomingRoute
}

fun parseIncomingRoute(uri: Uri?): IncomingRoute? {
  if (uri == null) return null
  return parseIncomingRoute(uri.toString())
}

internal fun parseIncomingRoute(raw: String): IncomingRoute? {
  val link = ParsedLink.parse(raw) ?: return null
  parseAuthHandoff(link)?.let { return IncomingRoute.Auth(it) }
  parsePayReturn(link)?.let { return it }
  parseAccountSheet(link)?.let { return IncomingRoute.Account(it) }
  parseSettingsRoute(link)?.let { return IncomingRoute.Settings(it) }
  return null
}

fun parseAuthHandoff(uri: Uri): AuthHandoff? {
  return parseAuthHandoff(ParsedLink.parse(uri.toString()) ?: return null)
}

private fun parseAuthHandoff(link: ParsedLink): AuthHandoff? {
  if (!link.isDemarScheme()) return null
  if (link.host != "auth") return null
  val path = link.path ?: return null
  if (path != "/callback" && path != "callback") return null
  link.query["token"]?.trim()?.takeIf(String::isNotEmpty)?.let {
    return AuthHandoff.ClientToken(it)
  }
  link.query["code"]?.trim()?.takeIf(String::isNotEmpty)?.let {
    return AuthHandoff.ExchangeCode(it)
  }
  return null
}

fun parseSettingsRoute(uri: Uri): SettingsRoute? {
  return parseSettingsRoute(ParsedLink.parse(uri.toString()) ?: return null)
}

private fun parsePayReturn(link: ParsedLink): IncomingRoute.PayReturn? {
  if (!link.isDemarScheme()) return null
  val parts = demarPathParts(link)
  if (parts.size != 2 || parts[0] != "pay") return null
  return when (parts[1]) {
    "ok" -> IncomingRoute.PayReturn(success = true)
    "fail" -> IncomingRoute.PayReturn(success = false)
    else -> null
  }
}

private fun parseAccountSheet(link: ParsedLink): AccountSheet? {
  if (!link.isDemarScheme()) return null
  val parts = demarPathParts(link)
  if (parts.size != 2 || parts[0] != "account") return null
  return when (parts[1]) {
    "buy" -> AccountSheet.Buy
    "plans" -> AccountSheet.Plans
    "traffic" -> AccountSheet.Traffic
    else -> null
  }
}

private fun demarPathParts(link: ParsedLink): List<String> =
  buildList {
    link.host?.trim()?.takeIf(String::isNotEmpty)?.takeUnless { it == "auth" }?.let(::add)
    addAll(link.pathSegments.filter(String::isNotBlank))
  }.dropWhile { it == "open" }

private fun parseSettingsRoute(link: ParsedLink): SettingsRoute? {
  if (!link.isDemarScheme()) return null
  val parts = demarPathParts(link)
  if (parts.isEmpty()) return null
  return when {
    parts == listOf("settings", "app") -> SettingsRoute.AppSettings
    parts == listOf("settings", "service") -> SettingsRoute.ServiceMenu
    parts == listOf("settings", "traffic") -> SettingsRoute.Traffic
    parts == listOf("settings", "support") -> SettingsRoute.Support()
    parts.size == 4 && parts[0] == "settings" && parts[1] == "support" && parts[2] == "ticket" ->
      parts[3].takeIf(String::isNotEmpty)?.let { SettingsRoute.Support(SupportRoute.Chat(it)) }
    parts.size == 4 && parts[0] == "settings" && parts[1] == "support" && parts[2] == "new" ->
      parts[3].takeIf(String::isNotEmpty)?.let { SettingsRoute.Support(SupportRoute.Draft(it)) }
    parts.size == 3 && parts[0] == "settings" && parts[1] == "documents" ->
      parts[2].takeIf(String::isNotEmpty)
        ?.let(::legalDocumentOrNull)
        ?.let(SettingsRoute::Document)
    else -> null
  }
}

fun SettingsRoute.requiresAuth(): Boolean = this is SettingsRoute.Support

fun SettingsRoute.toNavRoute(): String = when (this) {
  SettingsRoute.AppSettings -> AppNavRoute.AppSettings.pattern
  SettingsRoute.ServiceMenu -> AppNavRoute.ServiceMenu.pattern
  SettingsRoute.Traffic -> error("SettingsRoute.Traffic opens Account sheet, not a nav destination")
  is SettingsRoute.Support -> when (val nested = route) {
    null -> AppNavRoute.support()
    is SupportRoute.Chat -> AppNavRoute.support(ticketId = nested.ticketId)
    is SupportRoute.Draft -> AppNavRoute.support(themeId = nested.themeId)
  }
  is SettingsRoute.Document -> AppNavRoute.document(document)
}

object AppNavRoute {
  val Main = NavPattern("main")
  val AppSettings = NavPattern("app_settings")
  val TunSettings = NavPattern("tun_settings")
  val ServiceMenu = NavPattern("service_menu")
  val Support = NavPattern("support?ticketId={ticketId}&themeId={themeId}")
  val Document = NavPattern("document/{kind}")

  fun support(ticketId: String? = null, themeId: String? = null): String = buildString {
    append("support")
    val params = listOfNotNull(
      ticketId?.let { "ticketId=${Uri.encode(it)}" },
      themeId?.let { "themeId=${Uri.encode(it)}" },
    )
    if (params.isNotEmpty()) {
      append('?')
      append(params.joinToString("&"))
    }
  }

  fun document(kind: LegalDocument): String = "document/${kind.name}"
}

data class NavPattern(val pattern: String)

private fun legalDocumentOrNull(raw: String): LegalDocument? =
  runCatching { enumValueOf<LegalDocument>(raw) }.getOrNull()

private data class ParsedLink(
  val scheme: String?,
  val host: String?,
  val path: String?,
  val pathSegments: List<String>,
  val query: Map<String, String>,
) {
  fun isDemarScheme(): Boolean = scheme == DemarUrlScheme.Name

  companion object {
    fun parse(raw: String): ParsedLink? {
      val uri = runCatching { URI(raw) }.getOrNull() ?: return null
      return ParsedLink(
        scheme = uri.scheme,
        host = uri.host,
        path = uri.path,
        pathSegments = uri.path.orEmpty().split('/').filter(String::isNotBlank),
        query = parseQuery(uri.rawQuery),
      )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
      if (rawQuery.isNullOrBlank()) return emptyMap()
      return rawQuery
        .split('&')
        .mapNotNull { item ->
          val key = item.substringBefore('=').trim()
          if (key.isEmpty()) return@mapNotNull null
          val value = item.substringAfter('=', "").trim()
          key to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
        .toMap()
    }
  }
}
