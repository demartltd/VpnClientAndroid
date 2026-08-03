package com.demar.vpn.data.logging

import android.content.Context
import com.demar.vpn.vpn.PendingNativeDiag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque

fun journalStampUtc(time: Instant = Instant.now()): String {
  val d = time.atZone(ZoneOffset.UTC)
  fun t2(x: Int) = x.toString().padStart(2, '0')
  return "${d.year}-${t2(d.monthValue)}-${t2(d.dayOfMonth)}-${t2(d.hour)}-${t2(d.minute)}-${t2(d.second)}"
}

fun isVpnTrafficAccessNoise(line: String): Boolean {
  val s = line.trim()
  if (!s.contains(" accepted ") || !s.contains(" from ") || !s.contains("->") || !s.contains("]")) return false
  val lower = s.lowercase()
  return lower.contains("tcp:") || lower.contains("udp:")
}

object AppLogLimits {
  const val maxLines = 512
}

object AppLog {
  private val lock = Any()
  private val memory = ArrayDeque<String>(AppLogLimits.maxLines)
  private val _lines = MutableStateFlow<List<String>>(emptyList())
  val lines: StateFlow<List<String>> = _lines.asStateFlow()
  private var file: File? = null
  private var exportMirror: File? = null

  fun init(context: Context) {
    val app = context.applicationContext
    synchronized(lock) {
      if (file == null) {
        bindUnlocked(File(app.filesDir, "demar").apply { mkdirs() }.resolve("demar_app.log"))
      }
      exportMirror = app.getExternalFilesDir("demar")?.apply { mkdirs() }?.resolve("demar_app.log")
      writeMirrorUnlocked()
    }
    importPendingNativeDiag(app)
  }

  fun exportMirrorPath(context: Context): String? =
    context.getExternalFilesDir("demar")?.resolve("demar_app.log")?.absolutePath

  fun syncExportMirror(context: Context) = synchronized(lock) {
    exportMirror = exportMirror ?: context.applicationContext.getExternalFilesDir("demar")
      ?.apply { mkdirs() }
      ?.resolve("demar_app.log")
    writeMirrorUnlocked()
  }

  fun append(line: String) {
    val t = line.trim()
    if (t.isEmpty() || isVpnTrafficAccessNoise(t)) return
    synchronized(lock) { pushRowsUnlocked(listOf("${journalStampUtc()} $t")) }
  }

  fun appendMany(raw: List<String>, prependStamp: Boolean = true) {
    val rows = raw.mapNotNull { line ->
      val t = line.trim()
      when {
        t.isEmpty() || isVpnTrafficAccessNoise(t) -> null
        prependStamp -> "${journalStampUtc()} $t"
        else -> t
      }
    }
    if (rows.isEmpty()) return
    synchronized(lock) { pushRowsUnlocked(rows) }
  }

  fun clear() = synchronized(lock) {
    val f = file ?: return
    runCatching {
      if (f.exists()) f.delete()
      f.createNewFile()
    }
    memory.clear()
    publishUnlocked()
    writeMirrorUnlocked()
  }

  fun readPersistedUtf8(): String = synchronized(lock) { joinedUnlocked() }

  fun importPendingNativeDiag(context: Context) {
    val drained = PendingNativeDiag.drainAndClear(context)
    if (drained.isNotEmpty()) appendMany(drained)
  }

  internal fun bindForTest(dir: File) = synchronized(lock) {
    bindUnlocked(File(dir, "demar_app.log"))
  }

  internal fun bindMirrorForTest(dir: File) = synchronized(lock) {
    exportMirror = File(dir, "export_mirror.log")
  }

  internal fun syncExportMirrorForTest() = synchronized(lock) { writeMirrorUnlocked() }

  internal fun resetForTest() = synchronized(lock) {
    file = null
    exportMirror = null
    memory.clear()
    publishUnlocked()
  }

  private fun bindUnlocked(f: File) {
    file = f.also { it.parentFile?.mkdirs() }
    memory.clear()
    if (f.isFile) {
      val loaded = runCatching {
        f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
      }.getOrDefault(emptyList())
      val keep = if (loaded.size > AppLogLimits.maxLines) loaded.takeLast(AppLogLimits.maxLines) else loaded
      memory.addAll(keep)
      if (loaded.size > AppLogLimits.maxLines) persistUnlocked()
    }
    publishUnlocked()
  }

  private fun pushRowsUnlocked(rows: List<String>) {
    if (file == null) return
    for (row in rows) {
      if (memory.size >= AppLogLimits.maxLines) memory.removeFirst()
      memory.addLast(row)
    }
    persistUnlocked()
    publishUnlocked()
    writeMirrorUnlocked()
  }

  private fun persistUnlocked() {
    val f = file ?: return
    runCatching {
      f.parentFile?.mkdirs()
      f.writeText(joinedUnlocked())
    }
  }

  private fun writeMirrorUnlocked() {
    val mirror = exportMirror ?: return
    runCatching {
      mirror.parentFile?.mkdirs()
      mirror.writeText(joinedUnlocked())
    }
  }

  private fun joinedUnlocked(): String =
    if (memory.isEmpty()) "" else memory.joinToString("\n", postfix = "\n")

  private fun publishUnlocked() {
    _lines.value = memory.toList()
  }
}
