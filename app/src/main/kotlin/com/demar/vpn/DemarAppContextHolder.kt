package com.demar.vpn

import android.content.Context
import com.demar.vpn.vpn.PendingNativeDiag
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import java.util.Locale

internal object DemarAppContextHolder {
  @Volatile private var app: Context? = null
  @Volatile private var libboxReady = false
  private var workingDir: File? = null

  fun init(context: Context) {
    val ctx = context.applicationContext
    if (app == null) {
      app = ctx
    }
    ensureLibboxSetup(ctx)
  }

  @Synchronized
  fun ensureLibboxSetup(context: Context) {
    if (libboxReady) return
    val base = context.filesDir.apply { mkdirs() }
    val work =
      (context.getExternalFilesDir(null) ?: context.filesDir)
        .resolve("libbox")
        .apply { mkdirs() }
    val tmp = File(context.cacheDir, "libbox_tmp").apply { mkdirs() }
    val options = SetupOptions()
    options.basePath = base.absolutePath
    options.workingPath = work.absolutePath
    options.tempPath = tmp.absolutePath
    options.fixAndroidStack = true
    options.logMaxLines = 1000
    options.debug = false
    Libbox.setLocale(Locale.getDefault().toLanguageTag().replace("-", "_"))
    Libbox.setup(options)
    Libbox.redirectStderr(File(work, "stderr.log").absolutePath)
    workingDir = work
    libboxReady = true
  }

  fun stderrLogFile(): File? = workingDir?.resolve("stderr.log")

  fun importPendingStderrIfNeeded(context: Context) {
    val work = workingDir ?: return
    for (name in listOf("stderr.log.old", "stderr.log")) {
      val file = work.resolve(name)
      if (!file.isFile) continue
      runCatching {
        val tail = file.readLines().takeLast(30).joinToString("\n").trim()
        if (tail.isNotEmpty()) {
          PendingNativeDiag.append(context, "[libbox $name]\n$tail")
        }
      }
    }
  }

  fun getOrNull(): Context? = app
}
