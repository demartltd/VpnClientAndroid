package com.demar.vpn.data.locale

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.demar.vpn.R

fun openAppLocaleSettings(context: Context) {
  context.startActivity(
    Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
    },
  )
}

/** Checkout default: RUB for Russian UI, EUR for every other language. */
fun defaultPayCurrency(context: Context): String =
  if (context.resources.configuration.locales[0]?.language == "ru") "RUB" else "EUR"

fun currentLanguageLabel(context: Context): String {
  val appLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
  if (appLocales.isEmpty) return context.getString(R.string.settings_language_system)
  val locale = appLocales[0] ?: return context.getString(R.string.settings_language_system)
  return locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
}
