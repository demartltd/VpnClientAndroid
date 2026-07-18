package com.demar.vpn.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.demar.vpn.data.content.LegalDocumentRepository
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.content.LegalDocument
import com.demar.vpn.ui.scaffold.DemarBackScaffold
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import kotlinx.coroutines.delay

private sealed interface DocUiState {
  data object Loading : DocUiState
  data class Ready(val markdown: String) : DocUiState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentScreen(document: LegalDocument, onBack: () -> Unit) {
  var state by remember(document) { mutableStateOf<DocUiState>(DocUiState.Loading) }

  LaunchedEffect(document) {
    while (true) {
      state = DocUiState.Loading
      val result = runCatching { LegalDocumentRepository.load(document) }
      if (result.isSuccess) {
        state = DocUiState.Ready(result.getOrThrow())
        break
      }
      AppLog.append("DocumentScreen load ${document.name} failed: ${result.exceptionOrNull()}")
      delay(2_000)
    }
  }

  DemarBackScaffold(title = stringResource(document.titleRes), onBack = onBack) { padding ->
    when (val ui = state) {
      DocUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator()
      }
      is DocUiState.Ready -> {
        val typography = MaterialTheme.typography
        Markdown(
          content = ui.markdown,
          typography = markdownTypography(
            h1 = typography.headlineSmall,
            h2 = typography.titleLarge,
            h3 = typography.titleMedium,
            h4 = typography.titleSmall,
            h5 = typography.bodyLarge,
            h6 = typography.bodyLarge,
            text = typography.bodyMedium,
            paragraph = typography.bodyMedium,
            ordered = typography.bodyMedium,
            bullet = typography.bodyMedium,
            list = typography.bodyMedium,
            table = typography.bodySmall,
          ),
          dimens = markdownDimens(tableCellWidth = 220.dp, tableCellPadding = 8.dp),
          components = markdownComponents(
            table = {
              MarkdownTable(
                content = it.content,
                node = it.node,
                style = it.typography.table,
                headerBlock = { content, header, tableWidth, style ->
                  MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                  )
                },
                rowBlock = { content, header, tableWidth, style ->
                  MarkdownTableRow(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                  )
                },
              )
            },
          ),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    }
  }
}
