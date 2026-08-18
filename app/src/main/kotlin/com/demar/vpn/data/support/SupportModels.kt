package com.demar.vpn.data.support

data class SupportTicketSummary(
  val id: String,
  val status: String,
  val openedAt: String?,
  val closedAt: String?,
  val channel: String?,
  val sourceId: String?,
  val themeId: String?,
  val preview: String?,
  val lastMessageRole: String?,
  val hasUnread: Boolean = false,
) {
  val isOpen: Boolean get() = status == "open"
}

data class SupportTheme(
  val id: String,
  val name: String,
)

data class SupportMedia(
  val type: String?,
  val url: String?,
  val fileName: String?,
  val mimeType: String?,
  val fileSize: Long?,
  val width: Int? = null,
  val height: Int? = null,
) {
  val isPhoto: Boolean
    get() {
      if (type == "photo") return true
      if (mimeType?.startsWith("image/") == true) return true
      val name = fileName?.lowercase().orEmpty()
      return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".heic")
    }
}

data class SupportResolved(
  val text: String?,
  val media: List<SupportMedia>,
)

data class SupportMessage(
  val id: String,
  val role: String,
  val edited: Boolean,
  val deleted: Boolean,
  val createdAt: String?,
  val resolved: SupportResolved?,
) {
  val isUser: Boolean get() = role == "user"
  val text: String? get() = resolved?.text?.takeIf { it.isNotBlank() }
  val media: List<SupportMedia> get() = resolved?.media.orEmpty()
}

data class SupportTicket(
  val id: String,
  val status: String,
  val channel: String?,
  val sourceId: String?,
  val themeId: String?,
  val openedAt: String?,
  val closedAt: String?,
  val messages: List<SupportMessage>,
) {
  val isOpen: Boolean get() = status == "open"
}

data class SupportUploadMedia(
  val dataBase64: String,
  val fileName: String?,
  val mimeType: String?,
)

class SupportMediaTooLargeException : Exception("media_too_large")
