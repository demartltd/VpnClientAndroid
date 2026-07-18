package com.demar.vpn.data.content

import com.demar.vpn.data.network.RawTextFetcher
import com.demar.vpn.domain.content.LegalDocument

object LegalDocumentRepository {
  private val fetcher = RawTextFetcher()

  suspend fun load(document: LegalDocument): String = fetcher.get(document.url)
}
