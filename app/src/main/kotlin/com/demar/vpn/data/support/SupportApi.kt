package com.demar.vpn.data.support

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.demar.vpn.BuildConfig
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.device.DeviceIdentityCollector
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.update.AndroidReleaseApi
import com.demar.vpn.data.update.UpdateChannelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SupportApi {
  const val BASE_URL = "https://csapi.demar.cc"
  const val VpnSourceId = "2"
  const val Channel = "android"
  const val MaxMediaBytes = 25L * 1024 * 1024
  const val MaxMediaFiles = 10
  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 25_000
  private const val MediaTimeoutMs = 180_000L
  /** Match Apple SupportAttach — smaller payloads survive CS Telegram relay. */
  private const val MaxPhotoEdge = 1024
  private const val MaxPhotoBytes = 400 * 1024
  private const val MediaAttempts = 2

  suspend fun listTickets(userId: String, loginAt: String): List<SupportTicketSummary> =
    withContext(Dispatchers.IO) {
      val json = get("/support/me", userId, loginAt)
      parseSummaries(json.optJSONArray("tickets"))
    }

  fun encodeSummaries(list: List<SupportTicketSummary>): String {
    val arr = JSONArray()
    for (t in list) {
      arr.put(
        JSONObject()
          .put("id", t.id)
          .put("status", t.status)
          .putOpt("openedAt", t.openedAt)
          .putOpt("closedAt", t.closedAt)
          .putOpt("channel", t.channel)
          .putOpt("sourceId", t.sourceId)
          .putOpt("themeId", t.themeId)
          .putOpt("preview", t.preview)
          .putOpt("lastMessageRole", t.lastMessageRole)
          .put("hasUnread", t.hasUnread),
      )
    }
    return arr.toString()
  }

  fun decodeSummaries(raw: String): List<SupportTicketSummary>? =
    runCatching { parseSummaries(JSONArray(raw)) }.getOrNull()

  fun encodeTicket(ticket: SupportTicket): String =
    JSONObject()
      .put("id", ticket.id)
      .put("status", ticket.status)
      .putOpt("channel", ticket.channel)
      .putOpt("sourceId", ticket.sourceId)
      .putOpt("themeId", ticket.themeId)
      .putOpt("openedAt", ticket.openedAt)
      .putOpt("closedAt", ticket.closedAt)
      .put(
        "messages",
        JSONArray().also { arr ->
          for (m in ticket.messages) arr.put(encodeMessage(m))
        },
      )
      .toString()

  fun decodeTicket(raw: String): SupportTicket? =
    runCatching { parseTicket(JSONObject(raw)) }.getOrNull()

  private fun encodeMessage(m: SupportMessage): JSONObject {
    val o = JSONObject()
      .put("id", m.id)
      .put("role", m.role)
      .put("edited", m.edited)
      .put("deleted", m.deleted)
      .putOpt("createdAt", m.createdAt)
    m.resolved?.let { r ->
      val resolved = JSONObject().putOpt("text", r.text)
      val media = JSONArray()
      for (med in r.media) {
        media.put(
          JSONObject()
            .putOpt("type", med.type)
            .putOpt("url", med.url)
            .putOpt("fileName", med.fileName)
            .putOpt("mimeType", med.mimeType)
            .putOpt("fileSize", med.fileSize)
            .putOpt("width", med.width)
            .putOpt("height", med.height),
        )
      }
      resolved.put("media", media)
      o.put("resolved", resolved)
    }
    return o
  }

  suspend fun listThemes(userId: String, loginAt: String, sourceId: String = VpnSourceId): List<SupportTheme> =
    withContext(Dispatchers.IO) {
      val json = get("/support/themes", userId, loginAt, mapOf("sourceId" to sourceId))
      parseThemes(json.optJSONArray("themes"))
    }

  suspend fun getTicket(userId: String, loginAt: String, ticketId: String): SupportTicket =
    withContext(Dispatchers.IO) {
      parseTicketPayload(get("/support/tickets/${enc(ticketId)}", userId, loginAt))
    }

  suspend fun markTicketRead(userId: String, loginAt: String, ticketId: String): SupportTicket =
    withContext(Dispatchers.IO) {
      parseTicketPayload(
        post("/support/tickets/${enc(ticketId)}/read", sessionBody(userId, loginAt), hasMedia = false),
      )
    }

  suspend fun postTicket(
    userId: String,
    loginAt: String,
    text: String? = null,
    media: List<SupportUploadMedia> = emptyList(),
    log: String? = null,
    logMedia: List<SupportUploadMedia> = emptyList(),
    themeId: String? = null,
    sourceId: String? = VpnSourceId,
    channel: String = Channel,
    ticketId: String? = null,
    forceNew: Boolean = false,
  ): SupportTicket =
    withContext(Dispatchers.IO) {
      withTimeout(if (media.isNotEmpty() || logMedia.isNotEmpty()) MediaTimeoutMs else 60_000L) {
        val body = sessionBody(userId, loginAt).put("channel", channel)
        if (forceNew) body.put("forceNew", true)
        ticketId?.takeIf { it.isNotBlank() }?.let { body.put("ticketId", it) }
        text?.takeIf { it.isNotBlank() }?.let { body.put("text", it) }
        putLog(body, log, logMedia)
        themeId?.takeIf { it.isNotBlank() }?.let { body.put("themeId", it) }
        sourceId?.takeIf { it.isNotBlank() }?.let { body.put("sourceId", it) }
        if (media.isNotEmpty()) body.put("media", mediaToJson(media))
        parseTicketPayload(post("/support/tickets", body, hasMedia = media.isNotEmpty() || logMedia.isNotEmpty()))
      }
    }

  /**
   * Create ticket with silent diagnostics: device text + full journal.txt attach.
   */
  suspend fun postTicketCreate(
    userId: String,
    loginAt: String,
    text: String? = null,
    log: String? = null,
    logMedia: List<SupportUploadMedia> = emptyList(),
    themeId: String? = null,
    forceNew: Boolean = false,
  ): SupportTicket =
    postTicketWithRetry(
      userId, loginAt, text, media = emptyList(), log, logMedia,
      themeId, ticketId = null, forceNew = forceNew, maxAttempts = 2,
    )

  private suspend fun postTicketWithRetry(
    userId: String,
    loginAt: String,
    text: String?,
    media: List<SupportUploadMedia>,
    log: String?,
    logMedia: List<SupportUploadMedia>,
    themeId: String?,
    ticketId: String?,
    forceNew: Boolean,
    maxAttempts: Int = 3,
  ): SupportTicket {
    var delayMs = 400L
    var last: Throwable = AuthException("ticket post failed", 500)
    repeat(maxAttempts) { attempt ->
      try {
        return postTicket(
          userId = userId,
          loginAt = loginAt,
          text = text,
          media = media,
          log = log,
          logMedia = logMedia,
          themeId = themeId,
          ticketId = ticketId,
          forceNew = forceNew,
        )
      } catch (t: CancellationException) {
        throw t
      } catch (t: Throwable) {
        last = t
        val msg = t.message.orEmpty()
        AppLog.append("support ticket post try${attempt + 1}: $msg")
        val retryable =
          msg.contains("fetch failed", ignoreCase = true) ||
            msg.contains("архив", ignoreCase = true) ||
            msg.contains("archive", ignoreCase = true) ||
            (t is AuthException && t.statusCode == 502)
        if (attempt == maxAttempts - 1 || !retryable) throw t
        delay(delayMs)
        delayMs *= 2
      }
    }
    throw last
  }

  /** Album: one POST with media[1…10] → one ticket message. */
  suspend fun postMediaAlbum(
    userId: String,
    loginAt: String,
    media: List<SupportUploadMedia>,
    ticketId: String,
  ): SupportTicket {
    require(media.isNotEmpty()) { "media required" }
    AppLog.append("support media album: ${media.size} files")
    return postTicketWithRetry(
      userId = userId,
      loginAt = loginAt,
      text = null,
      media = media,
      log = null,
      logMedia = emptyList(),
      themeId = null,
      ticketId = ticketId,
      forceNew = false,
      maxAttempts = MediaAttempts,
    )
  }

  suspend fun patchMessage(
    userId: String,
    loginAt: String,
    ticketId: String,
    messageId: String,
    text: String? = null,
    deleted: Boolean = false,
  ): SupportTicket =
    withContext(Dispatchers.IO) {
      val body = sessionBody(userId, loginAt).put("channel", Channel)
      when {
        deleted -> body.put("deleted", true)
        text != null -> body.put("text", text)
        else -> throw AuthException("text or deleted required", 400)
      }
      parseTicketPayload(
        patch("/support/tickets/${enc(ticketId)}/messages/${enc(messageId)}", body),
      )
    }

  suspend fun readUriAsUpload(context: Context, uri: Uri): SupportUploadMedia =
    withContext(Dispatchers.IO) {
      val cr = context.contentResolver
      var name: String? = null
      var size = -1L
      cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { c ->
          if (c.moveToFirst()) {
            name = c.getString(0)
            if (!c.isNull(1)) size = c.getLong(1)
          }
        }
      if (size > MaxMediaBytes) throw SupportMediaTooLargeException()
      val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
        ?: throw AuthException("cannot_read_file", 400)
      if (bytes.isEmpty()) throw AuthException("empty media", 400)
      if (bytes.size > MaxMediaBytes) throw SupportMediaTooLargeException()
      val webp = compressImageToWebp(bytes) ?: throw AuthException("unsupported image", 400)
      SupportUploadMedia(
        dataBase64 = Base64.encodeToString(webp, Base64.NO_WRAP),
        fileName = (name?.substringBeforeLast('.') ?: "photo") + ".webp",
        mimeType = "image/webp",
      )
    }

  private fun compressImageToWebp(raw: ByteArray): ByteArray? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    val longest = maxOf(w, h)
    while (longest / sample > MaxPhotoEdge * 2) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply {
      inSampleSize = sample
      inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val decoded = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null
    val scaled = run {
      val lw = decoded.width
      val lh = decoded.height
      val edge = maxOf(lw, lh)
      if (edge <= MaxPhotoEdge) decoded
      else {
        val scale = MaxPhotoEdge.toFloat() / edge
        val nw = (lw * scale).toInt().coerceAtLeast(1)
        val nh = (lh * scale).toInt().coerceAtLeast(1)
        android.graphics.Bitmap.createScaledBitmap(decoded, nw, nh, true).also {
          if (it !== decoded) decoded.recycle()
        }
      }
    }
    var quality = 82
    var out = ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
    while (out.size() > MaxPhotoBytes && quality > 35) {
      quality -= 10
      out = ByteArrayOutputStream()
      scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
    }
    if (scaled !== decoded) scaled.recycle() else decoded.recycle()
    return out.toByteArray().takeIf { it.isNotEmpty() }
  }

  data class SilentDiagnostics(
    val logText: String,
    val logMedia: List<SupportUploadMedia>,
  )

  /** Device summary as log text; full journal as `journal.txt` attach. */
  suspend fun buildSilentDiagnostics(context: Context): SilentDiagnostics =
    withContext(Dispatchers.IO) {
      AppLog.importPendingNativeDiag(context)
      val device = DeviceIdentityCollector.load(context)
      val latest = runCatching {
        withTimeout(3_000L) { AndroidReleaseApi.fetch(UpdateChannelStore.enabled.value) }
      }.getOrNull()
      val deviceText = buildString {
        appendLine("${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Latest: ${latest?.version ?: "—"}")
        appendLine("UA: ${AuthApi.UserAgent}")
        appendLine("--- device ---")
        for ((k, v) in device) appendLine("$k=$v")
      }.trim().take(8_000)
      val journal = AppLog.readPersistedUtf8().ifBlank { "(empty)" }
      val bytes = journal.toByteArray(Charsets.UTF_8)
      val file = SupportUploadMedia(
        dataBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        fileName = "journal.txt",
        mimeType = "text/plain",
      )
      SilentDiagnostics(logText = deviceText, logMedia = listOf(file))
    }

  private fun mediaToJson(media: List<SupportUploadMedia>): JSONArray {
    val arr = JSONArray()
    for (m in media) {
      val item = JSONObject().put("data", m.dataBase64)
      m.fileName?.let { item.put("fileName", it) }
      m.mimeType?.let { item.put("mimeType", it) }
      arr.put(item)
    }
    return arr
  }

  private fun putLog(body: JSONObject, log: String?, logMedia: List<SupportUploadMedia>) {
    val text = log?.takeIf { it.isNotBlank() }
    if (logMedia.isEmpty()) {
      text?.let { body.put("log", it) }
      return
    }
    val obj = JSONObject()
    text?.let { obj.put("text", it) }
    obj.put("media", mediaToJson(logMedia))
    body.put("log", obj)
  }

  suspend fun loadMediaBytes(
    context: Context,
    userId: String,
    loginAt: String,
    mediaPath: String,
    ticketId: String? = null,
  ): ByteArray = withContext(Dispatchers.IO) {
    var path = mediaPath.removePrefix(BASE_URL).let { if (it.startsWith("/")) it else "/$it" }
    if (path.startsWith("http")) {
      val u = URL(path)
      path = u.path + (u.query?.let { "?$it" } ?: "")
    }
    if (path.startsWith("/api/telegram-media?")) {
      path = "/support/media?" + path.removePrefix("/api/telegram-media?")
    }
    val cacheKey = "$path|$ticketId|$userId"
    SupportMediaCache.load(context, cacheKey)?.let { return@withContext it }
    val sep = if (path.contains('?')) '&' else '?'
    val ticketQ = if (!ticketId.isNullOrBlank()) "&ticketId=${enc(ticketId)}" else ""
    val full = "$BASE_URL$path${sep}userId=${enc(userId)}&loginAt=${enc(loginAt)}$ticketQ"
    val conn = (URL(full).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = ConnectTimeoutMs
      readTimeout = MediaTimeoutMs.toInt()
      useCaches = true
      setRequestProperty("Accept", "*/*")
      setRequestProperty("X-Demar-Client", AuthApi.CLIENT_ID)
      setRequestProperty("User-Agent", AuthApi.UserAgent)
    }
    try {
      val code = conn.responseCode
      if (code !in 200..299) {
        val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw AuthException(err.ifBlank { "HTTP $code" }, code)
      }
      val bytes = conn.inputStream.use { it.readBytes() }
      SupportMediaCache.save(context, cacheKey, bytes)
      bytes
    } finally {
      conn.disconnect()
    }
  }

  private fun sessionBody(userId: String, loginAt: String) =
    JSONObject().put("userId", userId).put("loginAt", loginAt)

  private fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name())

  private fun get(
    path: String,
    userId: String,
    loginAt: String,
    extra: Map<String, String> = emptyMap(),
  ): JSONObject {
    val q = buildString {
      append("userId=${enc(userId)}&loginAt=${enc(loginAt)}")
      for ((k, v) in extra) append("&${enc(k)}=${enc(v)}")
    }
    return request("GET", "$path?$q", body = null, hasMedia = false)
  }

  private fun post(path: String, body: JSONObject, hasMedia: Boolean): JSONObject =
    request("POST", path, body, hasMedia)

  private fun patch(path: String, body: JSONObject): JSONObject =
    request("PATCH", path, body, hasMedia = false)

  private fun request(method: String, path: String, body: JSONObject?, hasMedia: Boolean): JSONObject {
    val payload = body?.toString()?.toByteArray(Charsets.UTF_8)
    val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = ConnectTimeoutMs
      readTimeout = if (hasMedia) MediaTimeoutMs.toInt() else ReadTimeoutMs
      useCaches = false
      setRequestProperty("Accept", "application/json")
      setRequestProperty("X-Demar-Client", AuthApi.CLIENT_ID)
      setRequestProperty("User-Agent", AuthApi.UserAgent)
      if (payload != null) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setFixedLengthStreamingMode(payload.size)
      }
    }
    try {
      if (payload != null) {
        conn.outputStream.use { out ->
          var off = 0
          while (off < payload.size) {
            val n = minOf(64 * 1024, payload.size - off)
            out.write(payload, off, n)
            off += n
          }
          out.flush()
        }
      }
      val code = conn.responseCode
      val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        .orEmpty()
      val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
      if (code !in 200..299) {
        val err = json.optString("error").ifBlank { "HTTP $code" }
        if (code == 413 || err.contains("too large", ignoreCase = true)) {
          throw SupportMediaTooLargeException()
        }
        if (err == "ticket_channel_mismatch") {
          throw AuthException(err, 409)
        }
        throw AuthException(err, code)
      }
      return json
    } finally {
      conn.disconnect()
    }
  }

  private fun parseTicketPayload(json: JSONObject): SupportTicket {
    json.optJSONObject("ticket")?.let { return parseTicket(it) }
    if (json.anyId("id", "_id") != null || json.has("messages")) return parseTicket(json)
    throw AuthException("missing ticket", 500)
  }

  private fun parseSummaries(arr: JSONArray?): List<SupportTicketSummary> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.anyId("id", "_id") ?: continue
        add(
          SupportTicketSummary(
            id = id,
            status = o.optString("status"),
            openedAt = o.optString("openedAt").nullIfBlank(),
            closedAt = o.optString("closedAt").nullIfBlank(),
            channel = o.optString("channel").nullIfBlank(),
            sourceId = o.optString("sourceId").nullIfBlank(),
            themeId = o.optString("themeId").nullIfBlank(),
            preview = o.optString("preview").nullIfBlank(),
            lastMessageRole = o.optString("lastMessageRole").nullIfBlank(),
            hasUnread = o.optBoolean("hasUnread", false),
          ),
        )
      }
    }
  }

  private fun parseThemes(arr: JSONArray?): List<SupportTheme> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.anyId("id", "_id") ?: continue
        add(SupportTheme(id, o.optString("name").ifBlank { id }))
      }
    }
  }

  private fun parseTicket(o: JSONObject): SupportTicket {
    val msgs = o.optJSONArray("messages")
    val messages = buildList {
      if (msgs != null) {
        for (i in 0 until msgs.length()) {
          val m = msgs.optJSONObject(i) ?: continue
          add(parseMessage(m))
        }
      }
    }
    return SupportTicket(
      id = o.anyId("id", "_id").orEmpty(),
      status = o.optString("status"),
      channel = o.optString("channel").nullIfBlank(),
      sourceId = o.optString("sourceId").nullIfBlank(),
      themeId = o.optString("themeId").nullIfBlank(),
      openedAt = o.optString("openedAt").nullIfBlank(),
      closedAt = o.optString("closedAt").nullIfBlank(),
      messages = messages,
    )
  }

  private fun parseMessage(o: JSONObject): SupportMessage {
    val resolved = o.optJSONObject("resolved")?.let { r ->
      val mediaArr = r.optJSONArray("media")
      val media = buildList {
        if (mediaArr != null) {
          for (i in 0 until mediaArr.length()) {
            val m = mediaArr.optJSONObject(i) ?: continue
            add(
              SupportMedia(
                type = m.optString("type").nullIfBlank(),
                url = m.optString("url").nullIfBlank(),
                fileName = m.optString("fileName").nullIfBlank(),
                mimeType = m.optString("mimeType").nullIfBlank(),
                fileSize = m.optLong("fileSize").takeIf { it > 0 },
                width = m.optInt("width").takeIf { it > 0 },
                height = m.optInt("height").takeIf { it > 0 },
              ),
            )
          }
        }
      }
      SupportResolved(text = r.optString("text").nullIfBlank(), media = media)
    }
    return SupportMessage(
      id = o.anyId("id", "_id").orEmpty(),
      role = o.optString("role"),
      edited = o.optBoolean("edited"),
      deleted = o.optBoolean("deleted"),
      createdAt = o.optString("createdAt").nullIfBlank(),
      resolved = resolved,
    )
  }

  /** API may send ids as string or number. */
  private fun JSONObject.anyId(vararg keys: String): String? {
    for (key in keys) {
      if (!has(key) || isNull(key)) continue
      val s = when (val v = opt(key)) {
        is String -> v
        is Int, is Long -> v.toString()
        is Number -> v.toLong().toString()
        else -> optString(key)
      }.trim()
      if (s.isNotEmpty() && s != "null") return s
    }
    return null
  }

  private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() && it != "null" }
}
