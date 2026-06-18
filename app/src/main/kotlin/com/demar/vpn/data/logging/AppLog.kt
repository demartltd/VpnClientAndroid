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
  return "${d.year}-${t2(d.monthValue)}-${t2(d.dayOfMonth)}-${t2(d.hour)}-${t2(d.minute)}"
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
  private val memory = ArrayDeque<String>(AppLogLimits.maxLines)
  private val _lines = MutableStateFlow<List<String>>(emptyList())
  val lines: StateFlow<List<String>> = _lines.asStateFlow()
  private var file: File? = null

  fun init(context: Context) {
    if (file != null) return
    bind(File(context.filesDir, "demar").apply { mkdirs() }.resolve("demar_app.log"))
    importPendingNativeDiag(context)
  }

  fun append(line: String) {
    val t = line.trim()
    if (t.isEmpty() || isVpnTrafficAccessNoise(t)) return
    pushRows(listOf("${journalStampUtc()} $t"))
  }

  fun appendMany(raw: List<String>, prependStamp: Boolean = true) {
    if (raw.isEmpty()) return
    val stamp = if (prependStamp) journalStampUtc() else ""
    val rows = raw.mapNotNull { line ->
      val t = line.trim()
      when {
        t.isEmpty() || isVpnTrafficAccessNoise(t) -> null
        prependStamp -> "$stamp $t"
        else -> t
      }
    }
    if (rows.isNotEmpty()) pushRows(rows)
  }

  fun clear() {
    val f = file ?: return
    runCatching {
      if (f.exists()) f.delete()
      f.createNewFile()
    }
    memory.clear()
    publish()
  }

  fun readPersistedUtf8(): String {
    val f = file ?: return ""
    return if (f.isFile) runCatching { f.readText() }.getOrDefault("") else ""
  }

  fun importPendingNativeDiag(context: Context) {
    val drained = PendingNativeDiag.drainAndClear(context)
    if (drained.isNotEmpty()) appendMany(drained)
  }

  internal fun bindForTest(dir: File) = bind(File(dir, "demar_app.log"))

  internal fun resetForTest() {
    file = null
    memory.clear()
    publish()
  }

  private fun bind(f: File) {
    file = f.also { it.parentFile?.mkdirs() }
    syncMemoryFromDisk()
    trimDisk()
    publish()
  }

  private fun pushRows(rows: List<String>) {
    val f = file ?: return
    runCatching {
      f.appendText(rows.joinToString("\n", postfix = "\n"))
      rows.forEach { memory.addLast(it) }
      trimMemory()
      trimDisk()
      publish()
    }
  }

  private fun fileLines(f: File) =
    runCatching { f.readLines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())

  private fun List<String>.capped() =
    if (size > AppLogLimits.maxLines) takeLast(AppLogLimits.maxLines) else this

  private fun syncMemoryFromDisk() {
    memory.clear()
    val f = file ?: return
    if (f.isFile) memory.addAll(fileLines(f).capped())
  }

  private fun trimDisk() {
    val f = file ?: return
    if (!f.isFile) return
    runCatching {
      val all = fileLines(f)
      if (all.size <= AppLogLimits.maxLines) return
      f.writeText(all.capped().joinToString("\n", postfix = "\n"))
      syncMemoryFromDisk()
    }
  }

  private fun trimMemory() {
    while (memory.size > AppLogLimits.maxLines) memory.removeFirst()
  }

  private fun publish() {
    _lines.value = memory.toList()
  }
}
