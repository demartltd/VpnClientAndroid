package com.demar.vpn.data.support

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SupportCacheStore {
  private const val DIR = "support_cache"

  fun loadList(context: Context, userId: String): List<SupportTicketSummary>? =
    read(context, userId, "list.json")?.let { SupportApi.decodeSummaries(it) }

  fun saveList(context: Context, userId: String, tickets: List<SupportTicketSummary>) {
    write(context, userId, "list.json", SupportApi.encodeSummaries(tickets))
  }

  fun loadTicket(context: Context, userId: String, ticketId: String): SupportTicket? =
    read(context, userId, "ticket_$ticketId.json")?.let { SupportApi.decodeTicket(it) }

  fun saveTicket(context: Context, userId: String, ticket: SupportTicket) {
    write(context, userId, "ticket_${ticket.id}.json", SupportApi.encodeTicket(ticket))
  }

  fun removeTicket(context: Context, userId: String, ticketId: String) {
    file(context, userId, "ticket_$ticketId.json").delete()
  }

  private fun read(context: Context, userId: String, name: String): String? =
    runCatching { file(context, userId, name).takeIf { it.isFile }?.readText() }.getOrNull()
      ?.takeIf { it.isNotBlank() }

  private fun write(context: Context, userId: String, name: String, body: String) {
    runCatching {
      val f = file(context, userId, name)
      f.parentFile?.mkdirs()
      f.writeText(body)
    }
  }

  private fun file(context: Context, userId: String, name: String): File {
    val safeUser = userId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return File(context.applicationContext.filesDir, "$DIR/$safeUser/$name")
  }
}

object SupportMediaCache {
  private const val DIR = "support_media"
  private const val DIMS = "dims.json"
  private const val MaxFiles = 80
  private const val MaxBytes = 64L * 1024 * 1024

  data class Dims(val width: Int, val height: Int)

  private val bitmaps = object : android.util.LruCache<String, android.graphics.Bitmap>(
    ((Runtime.getRuntime().maxMemory() / 1024) / 10).toInt().coerceIn(8_192, 48_000),
  ) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int =
      value.byteCount / 1024
  }

  fun memoryGet(key: String): android.graphics.Bitmap? = synchronized(bitmaps) { bitmaps.get(key) }

  fun memoryPut(key: String, bitmap: android.graphics.Bitmap) {
    synchronized(bitmaps) { bitmaps.put(key, bitmap) }
  }

  fun load(context: Context, key: String): ByteArray? =
    runCatching {
      val f = file(context, key)
      if (!f.isFile) return null
      f.setLastModified(System.currentTimeMillis())
      f.readBytes()
    }.getOrNull()

  fun save(context: Context, key: String, bytes: ByteArray) {
    runCatching {
      val f = file(context, key)
      f.parentFile?.mkdirs()
      f.writeBytes(bytes)
      trim(context)
    }
  }

  fun loadDims(context: Context, url: String): Dims? {
    val o = dimsJson(context).optJSONObject(url) ?: return null
    val w = o.optInt("w")
    val h = o.optInt("h")
    return if (w > 0 && h > 0) Dims(w, h) else null
  }

  fun saveDims(context: Context, url: String, width: Int, height: Int) {
    if (width <= 0 || height <= 0 || url.isBlank()) return
    runCatching {
      val root = dimsJson(context)
      root.put(url, JSONObject().put("w", width).put("h", height))
      val f = dimsFile(context)
      f.parentFile?.mkdirs()
      f.writeText(root.toString())
    }
  }

  private fun dimsJson(context: Context): JSONObject =
    runCatching {
      val raw = dimsFile(context).takeIf { it.isFile }?.readText().orEmpty()
      if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }.getOrDefault(JSONObject())

  private fun dimsFile(context: Context) =
    File(context.applicationContext.cacheDir, "$DIR/$DIMS")

  private fun file(context: Context, key: String): File {
    val name = key.hashCode().toUInt().toString(16) + ".bin"
    return File(context.applicationContext.cacheDir, "$DIR/$name")
  }

  private fun trim(context: Context) {
    val dir = File(context.applicationContext.cacheDir, DIR)
    if (!dir.isDirectory) return
    val files = dir.listFiles()?.filter { it.name != DIMS }?.sortedBy { it.lastModified() } ?: return
    var total = files.sumOf { it.length() }
    var i = 0
    while ((files.size - i > MaxFiles || total > MaxBytes) && i < files.size) {
      total -= files[i].length()
      files[i].delete()
      i++
    }
  }
}
