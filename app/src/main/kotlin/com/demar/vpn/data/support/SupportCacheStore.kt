package com.demar.vpn.data.support

import android.content.Context
import com.demar.vpn.data.auth.AuthSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object SupportUnreadStore {
  private val _hasUnread = MutableStateFlow(false)
  val hasUnread: StateFlow<Boolean> = _hasUnread.asStateFlow()

  fun clear() {
    _hasUnread.value = false
  }

  fun apply(tickets: List<SupportTicketSummary>) {
    _hasUnread.value = tickets.any { it.hasUnread }
  }

  suspend fun refresh(context: Context, session: AuthSession) {
    runCatching {
      val tickets = SupportApi.listTickets(session.userId, session.loginAt)
      apply(tickets)
      SupportCacheStore.saveList(context, session.userId, tickets)
      warmTickets(context, session, tickets)
    }
  }

  suspend fun warmTickets(
    context: Context,
    session: AuthSession,
    summaries: List<SupportTicketSummary>,
  ) {
    val missing = summaries.filter {
      SupportCacheStore.loadTicket(context, session.userId, it.id) == null
    }
    if (missing.isEmpty()) return
    coroutineScope {
      for (summary in missing) {
        launch {
          runCatching {
            val fresh = SupportApi.getTicket(session.userId, session.loginAt, summary.id)
            SupportCacheStore.saveTicket(context, session.userId, fresh)
          }
        }
      }
    }
  }
}

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

  fun clearAll(context: Context) {
    File(context.applicationContext.filesDir, DIR).deleteRecursively()
  }

  private fun read(context: Context, userId: String, name: String): String? =
    runCatching { file(context, userId, name).takeIf { it.isFile }?.readText() }.getOrNull()
      ?.takeIf { it.isNotBlank() }

  private fun write(context: Context, userId: String, name: String, body: String) {
    runCatching {
      val f = file(context, userId, name)
      f.parentFile?.mkdirs()
      val tmp = File(f.parentFile, "${f.name}.tmp")
      tmp.writeText(body)
      if (!tmp.renameTo(f)) {
        f.writeText(body)
        tmp.delete()
      }
    }
  }

  private fun file(context: Context, userId: String, name: String): File {
    val safeUser = userId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return File(context.applicationContext.filesDir, "$DIR/$safeUser/$name")
  }
}

object SupportMediaCache {
  private const val DIR = "support_media"
  private const val MaxFiles = 80
  private const val MaxBytes = 64L * 1024 * 1024

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
      val tmp = File(f.parentFile, "${f.name}.tmp")
      tmp.writeBytes(bytes)
      if (!tmp.renameTo(f)) {
        f.writeBytes(bytes)
        tmp.delete()
      }
      trim(context)
    }
  }

  private fun file(context: Context, key: String): File {
    val name = key.hashCode().toUInt().toString(16) + ".bin"
    return File(context.applicationContext.cacheDir, "$DIR/$name")
  }

  private fun trim(context: Context) {
    val dir = File(context.applicationContext.cacheDir, DIR)
    if (!dir.isDirectory) return
    val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
    var total = files.sumOf { it.length() }
    var i = 0
    while ((files.size - i > MaxFiles || total > MaxBytes) && i < files.size) {
      total -= files[i].length()
      files[i].delete()
      i++
    }
  }
}
