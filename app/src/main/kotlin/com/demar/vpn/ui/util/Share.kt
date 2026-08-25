package com.demar.vpn.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.demar.vpn.data.logging.journalStampUtc
import java.io.File

fun shareJournal(context: Context, body: String): Boolean {
  val text = body.trim()
  if (text.isEmpty()) return false
  val name = "demar_vpn_journal_${journalStampUtc()}.txt"
  val file = File(context.cacheDir, name).apply { writeText(text) }
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(Intent.createChooser(intent, null))
  return true
}
