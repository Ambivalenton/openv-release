// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import java.text.Normalizer

/**
 * A hand-set dot-matrix typeface for LED, flip-dot, plasma and segment-LED boards, drawn
 * like the fonts of real destination displays: 7 rows of cap height plus 2 descender rows,
 * proportional letters, mixed case, German umlauts and ß, and digits of one width so times line up.
 * Before, an ordinary font was scaled down to 9 pixels and became illegible.
 *
 * Every glyph is given as rows from the top ('#' lit, '.' unlit); rows below the 7th are descenders.
 */
internal object MatrixFont {
    const val ROWS = 9
    const val CAP = 7
    /** Free columns between two letters and the width of a word space. */
    const val TRACKING = 1
    const val SPACE = 3

    class Glyph(val width: Int, private val bits: BooleanArray) {
        fun lit(row: Int, col: Int) = bits[row * width + col]
    }

    private val source: Map<Char, String> = mapOf(
        // Capitals
        'A' to ".###./#...#/#...#/#####/#...#/#...#/#...#",
        'B' to "####./#...#/#...#/####./#...#/#...#/####.",
        'C' to ".###./#...#/#..../#..../#..../#...#/.###.",
        'D' to "####./#...#/#...#/#...#/#...#/#...#/####.",
        'E' to "#####/#..../#..../####./#..../#..../#####",
        'F' to "#####/#..../#..../####./#..../#..../#....",
        'G' to ".###./#...#/#..../#.###/#...#/#...#/.####",
        'H' to "#...#/#...#/#...#/#####/#...#/#...#/#...#",
        'I' to "###/.#./.#./.#./.#./.#./###",
        'J' to "..###/...#./...#./...#./...#./#..#./.##..",
        'K' to "#...#/#..#./#.#../##.../#.#../#..#./#...#",
        'L' to "#..../#..../#..../#..../#..../#..../#####",
        'M' to "#...#/##.##/#.#.#/#.#.#/#...#/#...#/#...#",
        'N' to "#...#/#...#/##..#/#.#.#/#..##/#...#/#...#",
        'O' to ".###./#...#/#...#/#...#/#...#/#...#/.###.",
        'P' to "####./#...#/#...#/####./#..../#..../#....",
        'Q' to ".###./#...#/#...#/#...#/#.#.#/#..#./.##.#",
        'R' to "####./#...#/#...#/####./#.#../#..#./#...#",
        'S' to ".####/#..../#..../.###./....#/....#/####.",
        'T' to "#####/..#../..#../..#../..#../..#../..#..",
        'U' to "#...#/#...#/#...#/#...#/#...#/#...#/.###.",
        'V' to "#...#/#...#/#...#/#...#/#...#/.#.#./..#..",
        'W' to "#...#/#...#/#...#/#.#.#/#.#.#/#.#.#/.#.#.",
        'X' to "#...#/#...#/.#.#./..#../.#.#./#...#/#...#",
        'Y' to "#...#/#...#/.#.#./..#../..#../..#../..#..",
        'Z' to "#####/....#/...#./..#../.#.../#..../#####",
        // Umlaut capitals keep their dots inside the cap height with a slightly shorter body.
        'Ä' to ".#.#./...../.###./#...#/#####/#...#/#...#",
        'Ö' to ".#.#./...../.###./#...#/#...#/#...#/.###.",
        'Ü' to ".#.#./...../#...#/#...#/#...#/#...#/.###.",
        // Small letters: x-height of 5 rows, ascenders to the top, descenders two rows deep.
        'a' to "...../...../.###./....#/.####/#...#/.####",
        'b' to "#..../#..../#.##./##..#/#...#/#...#/####.",
        'c' to "...../...../.###./#..../#..../#...#/.###.",
        'd' to "....#/....#/.##.#/#..##/#...#/#...#/.####",
        'e' to "...../...../.###./#...#/#####/#..../.###.",
        'f' to "..##/.#../.#../###./.#../.#../.#..",
        'g' to "...../...../.####/#...#/#...#/#...#/.####/....#/.###.",
        'h' to "#..../#..../#.##./##..#/#...#/#...#/#...#",
        'i' to "#/./#/#/#/#/#",
        'j' to "..#/.../..#/..#/..#/..#/..#/#.#/.#.",
        'k' to "#.../#.../#..#/#.#./##../#.#./#..#",
        'l' to "#/#/#/#/#/#/#",
        'm' to "...../...../##.#./#.#.#/#.#.#/#...#/#...#",
        'n' to "...../...../#.##./##..#/#...#/#...#/#...#",
        'o' to "...../...../.###./#...#/#...#/#...#/.###.",
        'p' to "...../...../####./#...#/#...#/#...#/####./#..../#....",
        'q' to "...../...../.####/#...#/#...#/#...#/.####/....#/....#",
        'r' to "...../...../#.##./##..#/#..../#..../#....",
        's' to "...../...../.####/#..../.###./....#/####.",
        't' to ".#../.#../####/.#../.#../.#../..##",
        'u' to "...../...../#...#/#...#/#...#/#..##/.##.#",
        'v' to "...../...../#...#/#...#/#...#/.#.#./..#..",
        'w' to "...../...../#...#/#...#/#.#.#/#.#.#/.#.#.",
        'x' to "...../...../#...#/.#.#./..#../.#.#./#...#",
        'y' to "...../...../#...#/#...#/#...#/#...#/.####/....#/.###.",
        'z' to "...../...../#####/...#./..#../.#.../#####",
        'ä' to ".#.#./...../.###./....#/.####/#...#/.####",
        'ö' to ".#.#./...../.###./#...#/#...#/#...#/.###.",
        'ü' to ".#.#./...../#...#/#...#/#...#/#..##/.##.#",
        'ß' to ".##../#..#./#..#./#.#../#..#./#...#/#.##.",
        // Digits: all five columns wide, so departure times stand in fixed columns.
        '0' to ".###./#...#/#..##/#.#.#/##..#/#...#/.###.",
        '1' to "..#../.##../..#../..#../..#../..#../.###.",
        '2' to ".###./#...#/....#/...#./..#../.#.../#####",
        '3' to "#####/...#./..#../...#./....#/#...#/.###.",
        '4' to "...#./..##./.#.#./#..#./#####/...#./...#.",
        '5' to "#####/#..../####./....#/....#/#...#/.###.",
        '6' to "..##./.#.../#..../####./#...#/#...#/.###.",
        '7' to "#####/....#/...#./..#../.#.../.#.../.#...",
        '8' to ".###./#...#/#...#/.###./#...#/#...#/.###.",
        '9' to ".###./#...#/#...#/.####/....#/...#./.##..",
        // Punctuation
        '.' to "././././././#",
        ',' to "../../../../../.#/.#/#./..",
        ':' to "././#/./#/./.",
        ';' to "../../.#/../.#/.#/#.",
        '-' to "..../..../..../####/..../..../....",
        '–' to "...../...../...../#####/...../...../.....",
        '+' to "...../..#../..#../#####/..#../..#../.....",
        '/' to "....#/....#/...#./..#../.#.../#..../#....",
        '(' to "..#/.#./#../#../#../.#./..#",
        ')' to "#../.#./..#/..#/..#/.#./#..",
        '\'' to "#/#/././././.",
        '"' to "#.#/#.#/.../.../.../.../...",
        '!' to "#/#/#/#/#/./#",
        '?' to ".###./#...#/....#/...#./..#../...../..#..",
        '&' to ".##../#..#./#.#../.#.../#.#.#/#..#./.##.#",
        '…' to "...../...../...../...../...../...../#.#.#",
        '·' to "./././#/./././".dropLast(1),
        '°' to ".#./#.#/.#./.../.../.../...",
        '%' to "##..#/##.#./...#./..#../.#.../.#.##/#..##",
        '*' to "...../#.#.#/.###./#####/.###./#.#.#/.....",
        '>' to "#..../.#.../..#../...#./..#../.#.../#....",
        '<' to "....#/...#./..#../.#.../..#../...#./....#",
        '=' to "...../...../#####/...../#####/...../.....",
        '_' to "...../...../...../...../...../...../#####",
    )

    private val glyphs: Map<Char, Glyph> = source.mapValues { (char, value) ->
        val rows = value.split('/')
        val width = rows.maxOf { it.length }
        require(rows.size == CAP || rows.size == ROWS) { "glyph $char has ${rows.size} rows" }
        require(rows.all { it.length == width }) { "glyph $char has rows of different widths" }
        Glyph(width, BooleanArray(ROWS * width) { i -> rows.getOrNull(i / width)?.getOrNull(i % width) == '#' })
    }

    private val space = Glyph(SPACE, BooleanArray(ROWS * SPACE))

    /** The glyph for [c]; accents fall back to their base letter, anything else to a question mark. */
    fun glyph(c: Char): Glyph {
        if (c.isWhitespace()) return space
        glyphs[c]?.let { return it }
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull()
        return base?.let { glyphs[it] } ?: when (c) {
            '—', '‒', '−' -> glyphs.getValue('–')
            '’', '‘', '´', '`' -> glyphs.getValue('\'')
            '„', '“', '”' -> glyphs.getValue('"')
            else -> glyphs.getValue('?')
        }
    }

    /** Columns [text] needs, letters separated by [TRACKING] free columns. */
    fun width(text: String): Int =
        if (text.isEmpty()) 0 else text.sumOf { glyph(it).width } + TRACKING * (text.length - 1)

    /** [text] shortened with an ellipsis so it fits into [columns]; unchanged when it already fits. */
    fun fit(text: String, columns: Int): String {
        val trimmed = text.trim()
        if (width(trimmed) <= columns) return trimmed
        val ellipsis = glyph('…').width
        var shown = trimmed
        while (shown.isNotEmpty() && width(shown.trimEnd()) + TRACKING + ellipsis > columns) shown = shown.dropLast(1)
        return if (shown.isBlank()) "" else shown.trimEnd() + "…"
    }

    /** Lit cells of [text]: [ROWS] rows, row-major, exactly [width] columns. */
    fun mask(text: String): Pair<Int, BooleanArray> {
        val cols = width(text).coerceAtLeast(1)
        val lit = BooleanArray(ROWS * cols)
        var x = 0
        text.forEach { c ->
            val g = glyph(c)
            for (row in 0 until ROWS) for (col in 0 until g.width) if (g.lit(row, col)) lit[row * cols + x + col] = true
            x += g.width + TRACKING
        }
        return cols to lit
    }

    /** Every character with an own glyph (for tests and previews). */
    val characters: Set<Char> get() = glyphs.keys
}
