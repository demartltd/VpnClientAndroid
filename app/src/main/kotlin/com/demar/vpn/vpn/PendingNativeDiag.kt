package com.demar.vpn.vpn

import android.content.Context
import java.io.File

internal object PendingNativeDiag {
  private fun file(ctx: Context): File {
    val dir = File(ctx.filesDir, "demar").apply { mkdirs() }
    return File(dir, "pending_native_diag.log")
  }

  fun append(ctx: Context, line: String) {
    try {
      val t = line.trim()
      if (t.isEmpty()) return
      file(ctx).appendText("$t\n")
    } catch (_: Throwable) {
    }
  }

  fun drainAndClear(ctx: Context): List<String> {
    val f = file(ctx)
    if (!f.exists()) return emptyList()
    return try {
      val lines = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
      f.delete()
      lines
    } catch (_: Throwable) {
      emptyList()
    }
  }
}
