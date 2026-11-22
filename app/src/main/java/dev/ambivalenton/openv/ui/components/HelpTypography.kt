// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.HelpBlock

/** Named links and readable emphasis, without a WebView or remote images. */
@Composable
internal fun HelpText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val color = MaterialTheme.colorScheme.primary
    val annotated = remember(text, color) {
        buildAnnotatedString {
            val tokens = Regex("\\[([^]\\n]+)]\\((https?://[^\\s)]+|mailto:[^\\s)]+)\\)|https?://[^\\s<>]+[^\\s<>.,;)]|\\*\\*([^*]+)\\*\\*")
            var offset = 0
            tokens.findAll(text).forEach { match ->
                append(text.substring(offset, match.range.first))
                when {
                    match.value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[3]) }
                    else -> {
                        val url = if (match.groupValues[2].isNotEmpty()) match.groupValues[2] else match.value
                        val label = match.groupValues[1].ifEmpty { url }
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color)))) { append(label) }
                    }
                }
                offset = match.range.last + 1
            }
            append(text.substring(offset))
        }
    }
    SelectionContainer { Text(annotated, modifier, style = style) }
}

@Composable
internal fun HelpBlocks(blocks: List<HelpBlock>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is HelpBlock.Paragraph -> HelpText(block.text)
                is HelpBlock.Subheading -> HelpText(block.text, Modifier.semantics { heading() }, MaterialTheme.typography.titleSmall)
                is HelpBlock.Callout -> Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    HelpText(block.text, Modifier.fillMaxWidth().padding(16.dp))
                }
                is HelpBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.rows.forEach { row -> Row {
                        Text("•", Modifier.padding(end = 12.dp))
                        HelpText(row, Modifier.weight(1f))
                    } }
                }
                is HelpBlock.Table -> HelpTable(block.rows)
                is HelpBlock.Literal -> SelectionContainer {
                    Text(block.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
                HelpBlock.Divider -> HorizontalDivider()
            }
        }
    }
}

/** At large font sizes rows become labelled cards, with no clipped cells. */
@Composable
private fun HelpTable(rows: List<List<String>>) {
    val stacked = LocalDensity.current.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360
    val headers = rows.firstOrNull().orEmpty()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!stacked) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                headers.forEach { HelpText(it, Modifier.weight(1f), MaterialTheme.typography.labelLarge) }
            }
            rows.drop(1).forEachIndexed { index, row ->
                if (index > 0 || !stacked) HorizontalDivider()
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { column, cell ->
                        HelpText(headers.getOrNull(column).orEmpty(), style = MaterialTheme.typography.labelMedium)
                        HelpText(cell)
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { HelpText(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}
