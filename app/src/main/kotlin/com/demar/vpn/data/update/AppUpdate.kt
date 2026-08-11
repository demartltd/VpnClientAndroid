package com.demar.vpn.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.demar.vpn.BuildConfig
import com.demar.vpn.R
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.DemarHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

sealed class AppUpdateState {
  data object Idle : AppUpdateState()
  data class Available(val release: AndroidRelease) : AppUpdateState()
  data class Downloading(val release: AndroidRelease, val percent: Int) : AppUpdateState()
  data class Ready(val release: AndroidRelease, val file: File) : AppUpdateState()
}

object AppUpdate {
  private val mutex = Mutex()
  private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
  val state: StateFlow<AppUpdateState> = _state.asStateFlow()

  private val _errors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
  val errors: SharedFlow<Int> = _errors.asSharedFlow()

  fun isNewer(remote: String, local: String = BuildConfig.VERSION_NAME): Boolean {
    val r = parseVersion(remote) ?: return false
    val l = parseVersion(local) ?: return true
    for (i in 0 until maxOf(r.size, l.size)) {
      val a = r.getOrElse(i) { 0 }
      val b = l.getOrElse(i) { 0 }
      if (a != b) return a > b
    }
    return false
  }

  fun parseVersion(raw: String): List<Int>? {
    val s = raw.trim().trimStart('v', 'V')
    if (s.isEmpty()) return null
    val parts = s.split('.')
    if (parts.any { it.toIntOrNull() == null }) return null
    return parts.map { it.toInt() }
  }

  fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .joinToString("") { "%02x".format(it) }

  fun clearPending() {
    if (_state.value !is AppUpdateState.Downloading) {
      _state.value = AppUpdateState.Idle
    }
  }

  suspend fun checkAndDownload(context: Context) = mutex.withLock {
    if (_state.value is AppUpdateState.Downloading) return@withLock

    val app = context.applicationContext
    val release = AndroidReleaseApi.fetch(UpdateChannelStore.enabled.value) ?: return@withLock
    if (!isNewer(release.version)) {
      clearCache(app, keepAsset = null)
      _state.value = AppUpdateState.Idle
      return@withLock
    }

    val cached = cachedApk(app, release)
    if (cached != null) {
      _state.value = AppUpdateState.Ready(release, cached)
      return@withLock
    }

    _state.value = AppUpdateState.Available(release)
    download(app, release)
  }

  fun install(activity: Activity): Boolean {
    val ready = _state.value as? AppUpdateState.Ready ?: return false
    if (!ready.file.isFile) {
      _state.value = AppUpdateState.Idle
      return false
    }
    if (!activity.packageManager.canRequestPackageInstalls()) {
      activity.startActivity(
        Intent(
          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          "package:${activity.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
      return false
    }
    val uri = FileProvider.getUriForFile(
      activity,
      "${activity.packageName}.fileprovider",
      ready.file,
    )
    activity.startActivity(
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      },
    )
    return true
  }

  private fun updateDir(context: Context) = File(context.filesDir, "update").apply { mkdirs() }

  private fun stampFile(dir: File, asset: String) = File(dir, "$asset.sha256")

  /** Valid completed APK for this release, or null. */
  private fun cachedApk(context: Context, release: AndroidRelease): File? {
    val dir = updateDir(context)
    val target = File(dir, release.asset)
    val stamp = stampFile(dir, release.asset)
    if (!target.isFile || !stamp.isFile) return null
    val stamped = runCatching { stamp.readText().trim().lowercase() }.getOrNull() ?: return null
    if (stamped != release.sha256.lowercase()) return null
    return target
  }

  private fun writeStamp(dir: File, asset: String, sha256: String) {
    stampFile(dir, asset).writeText(sha256.lowercase())
  }

  private fun clearCache(context: Context, keepAsset: String?) {
    val dir = updateDir(context)
    dir.listFiles()?.forEach { f ->
      if (keepAsset == null || !f.name.startsWith(keepAsset)) f.delete()
    }
  }

  private suspend fun download(context: Context, release: AndroidRelease) = withContext(Dispatchers.IO) {
    val dir = updateDir(context)
    val target = File(dir, release.asset)
    val partial = File(dir, "${release.asset}.partial")
    try {
      // Drop stale completed file for a different digest.
      if (target.isFile && cachedApk(context, release) == null) {
        target.delete()
        stampFile(dir, release.asset).delete()
      }

      var offset = partial.takeIf { it.isFile }?.length() ?: 0L
      _state.value = AppUpdateState.Downloading(
        release,
        if (offset > 0) 1 else 0, // non-zero hint until Content-Range known
      )

      val headers = buildMap {
        put("Accept", "application/vnd.android.package-archive,*/*")
        if (offset > 0) put("Range", "bytes=$offset-")
      }
      DemarHttp.call(
        release.downloadUrl,
        headers = headers,
        connectTimeoutMs = 30_000,
        readTimeoutMs = 120_000,
        attempts = 1,
      ).use { resp ->
        val code = resp.code
        when (code) {
          206 -> Unit // resume
          in 200..299 -> {
            offset = 0L
            if (partial.exists()) partial.delete()
          }
          else -> error("HTTP $code")
        }

        val contentLen = resp.body?.contentLength() ?: -1L
        val total = when {
          code == 206 -> contentRangeTotal(resp.header("Content-Range"))
            ?: (offset + contentLen).takeIf { contentLen > 0 }
          else -> contentLen.takeIf { it > 0 }
        } ?: -1L

        FileOutputStream(partial, offset > 0).use { out ->
          resp.body!!.byteStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read = offset
            while (true) {
              val n = input.read(buf)
              if (n < 0) break
              out.write(buf, 0, n)
              read += n
              val pct = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 99) else 0
              _state.value = AppUpdateState.Downloading(release, pct)
            }
          }
        }
      }

      if (sha256File(partial) != release.sha256.lowercase()) {
        partial.delete()
        target.delete()
        stampFile(dir, release.asset).delete()
        AppLog.append("update: sha256 mismatch")
        _errors.tryEmit(R.string.update_hash_mismatch)
        _state.value = AppUpdateState.Idle
        return@withContext
      }
      if (target.exists()) target.delete()
      if (!partial.renameTo(target)) {
        partial.copyTo(target, overwrite = true)
        partial.delete()
      }
      writeStamp(dir, release.asset, release.sha256)
      _state.value = AppUpdateState.Ready(release, target)
      AppLog.append("update: ready ${release.version} (cached)")
    } catch (e: Exception) {
      // Keep .partial for resume on next launch.
      AppLog.append("update: download failed — ${e.message}")
      _errors.tryEmit(R.string.update_failed)
      _state.value = AppUpdateState.Idle
    }
  }

  private fun contentRangeTotal(header: String?): Long? {
    // bytes 100-199/12345
    val slash = header?.substringAfter('/', "") ?: return null
    return slash.toLongOrNull()?.takeIf { it > 0 }
  }

  private fun sha256File(file: File): String {
    val dig = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(64 * 1024)
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        dig.update(buf, 0, n)
      }
    }
    return dig.digest().joinToString("") { "%02x".format(it) }
  }
}
