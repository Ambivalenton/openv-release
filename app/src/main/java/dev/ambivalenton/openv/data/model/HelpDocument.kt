// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Small offline publishing format. Licence blocks stay verbatim. */
data class HelpSection(val id: Int, val title: String?, val blocks: List<HelpBlock>)
sealed interface HelpBlock {
    data class Paragraph(val text: String) : HelpBlock
    data class Subheading(val text: String) : HelpBlock
    data class Callout(val text: String) : HelpBlock
    data class Bullets(val rows: List<String>) : HelpBlock
    data class Table(val rows: List<List<String>>) : HelpBlock
    data class Literal(val text: String) : HelpBlock
    data object Divider : HelpBlock
}

fun parseHelpDocument(source: String): List<HelpSection> {
    val lines = source.replace("\r\n", "\n").lines()
    val sections = mutableListOf<HelpSection>()
    val blocks = mutableListOf<HelpBlock>()
    var title: String? = null
    var i = 0
    fun flush() {
        if (title != null || blocks.isNotEmpty()) sections += HelpSection(sections.size, title, blocks.toList())
        blocks.clear()
    }
    fun special(line: String) = line.isBlank() || line.startsWith("# ") || line.startsWith("## ") ||
        line.startsWith("### ") || line.startsWith("> ") || line.startsWith("- ") ||
        line.startsWith("|") || line.startsWith("```") || line == "---"
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() || line.startsWith("# ") -> i++
            line.startsWith("## ") -> { flush(); title = line.removePrefix("## ").trim(); i++ }
            line.startsWith("### ") -> { blocks += HelpBlock.Subheading(line.removePrefix("### ")); i++ }
            line == "---" -> { blocks += HelpBlock.Divider; i++ }
            line.startsWith("```") -> {
                val fence = line.takeWhile { it == '`' }
                i++
                val raw = mutableListOf<String>()
                while (i < lines.size && lines[i].trim() != fence) raw += lines[i++]
                if (i < lines.size) i++
                blocks += HelpBlock.Literal(raw.joinToString("\n"))
            }
            line.startsWith("> ") -> {
                val rows = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("> ")) rows += lines[i++].removePrefix("> ")
                blocks += HelpBlock.Callout(rows.joinToString("\n"))
            }
            line.startsWith("- ") -> {
                val rows = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("- ")) rows += lines[i++].removePrefix("- ")
                blocks += HelpBlock.Bullets(rows)
            }
            line.startsWith("|") -> {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].startsWith("|")) {
                    val cells = lines[i++].trim().trim('|').split('|').map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-{3,}:?")) }) rows += cells
                }
                if (rows.isNotEmpty()) blocks += HelpBlock.Table(rows)
            }
            else -> {
                val rows = mutableListOf(lines[i++])
                while (i < lines.size && !special(lines[i])) rows += lines[i++]
                blocks += HelpBlock.Paragraph(rows.joinToString("\n"))
            }
        }
    }
    flush()
    return sections
}
