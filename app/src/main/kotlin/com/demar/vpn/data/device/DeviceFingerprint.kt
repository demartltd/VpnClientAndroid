package com.demar.vpn.data.device

import java.security.MessageDigest

private const val OmitFingerprint = "fingerprint_sha256"

fun deviceFingerprintHex(fields: Map<String, String>): String {
  val parts = fields.entries
    .filter { (k, v) -> k != OmitFingerprint && v.isNotEmpty() && v != "—" }
    .map { "${it.key}=${it.value}" }
    .sorted()
  val bytes = parts.joinToString("|").toByteArray(Charsets.UTF_8)
  val full = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  return if (full.length <= 32) full else full.take(32)
}
