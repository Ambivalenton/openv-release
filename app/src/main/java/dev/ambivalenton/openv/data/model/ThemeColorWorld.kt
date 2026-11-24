// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.color.utilities.Hct
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Build 113 · the colour world of one design. Until now every design offered one of fourteen shared
 * families (up to thirty designs showed the same ten colours) and the same four grounds. Now each
 * design's own hues are the source: its accent and second colour in brighter, deeper and quieter
 * forms, their neighbours, opposites and a triad partner – always at the tone that works on the
 * design's own ground, so every offer is usable as it is. Achromatic designs get a grey scale.
 *
 * Pure and deterministic: the same design always offers the same colours (tested for all designs).
 * Names come from the colour itself when shown ([DesignSwatch.nameKey] stays empty).
 */
object ThemeColorWorld {
    private fun argb(h: Double, c: Double, t: Double): Long =
        Hct.from(((h % 360.0) + 360.0) % 360.0, max(c, 0.0), t.coerceIn(0.0, 100.0)).toInt().toLong() and 0xffffffffL

    private fun hct(color: Long) = Hct.fromInt(color.toInt() or 0xff000000.toInt())

    private fun contrast(a: Long, b: Long) = ColorUtils.calculateContrast(a.toInt() or 0xff000000.toInt(), b.toInt() or 0xff000000.toInt())

    /** The same hue and chroma, its tone moved (darker on light grounds, lighter on dark ones) until it reaches [minimum] on [ground]. */
    private fun readable(color: Long, ground: Long, dark: Boolean, minimum: Double): Long {
        if (contrast(color, ground) >= minimum) return color
        val h = hct(color)
        var t = h.tone
        repeat(60) {
            t += if (dark) 1.5 else -1.5
            val next = argb(h.hue, h.chroma, t)
            if (contrast(next, ground) >= minimum || t <= 0.0 || t >= 100.0) return next
        }
        return if (dark) 0xffffffffL else 0xff000000L
    }

    /** Perceptual distance in HCT: tone, chroma and the arc of the hue difference at the mean chroma. */
    internal fun distance(a: Long, b: Long): Double {
        val x = hct(a); val y = hct(b)
        val dh = abs(((x.hue - y.hue + 540.0) % 360.0) - 180.0) / 180.0 * PI * (x.chroma + y.chroma) / 2.0
        val dt = x.tone - y.tone; val dc = x.chroma - y.chroma
        return sqrt(dt * dt + dc * dc + dh * dh)
    }

    /** The first [count] candidates that differ enough from each other ([gap] in HCT units). */
    private fun pick(candidates: Sequence<Long>, count: Int, gap: Double, keep: List<Long> = emptyList()): List<Long> {
        val out = keep.toMutableList()
        for (c in candidates) {
            if (out.size >= count) break
            if (out.none { it == c || distance(it, c) < gap }) out += c
        }
        // Small worlds (very quiet designs): fill with smaller steps rather than fail.
        if (out.size < count) for (c in candidates) {
            if (out.size >= count) break
            if (out.none { it == c || distance(it, c) < gap / 3 }) out += c
        }
        return out
    }

    private fun achromatic(theme: AppTheme) = hct(theme.accent).chroma < 10.0 && hct(theme.secondary).chroma < 10.0

    /** Ten accents in the design's own hues, readable on its ground. */
    fun accents(theme: AppTheme): List<Long> {
        val a = hct(theme.accent); val b = hct(theme.secondary)
        val dark = theme.dark
        val main = if (dark) 76.0 else 44.0
        val alt = if (dark) 86.0 else 32.0
        val third = if (dark) 64.0 else 56.0
        if (achromatic(theme)) {
            val tones = if (dark) listOf(98.0, 92.0, 86.0, 80.0, 74.0, 68.0, 62.0, 56.0, 50.0, 95.0) else listOf(4.0, 10.0, 16.0, 22.0, 28.0, 34.0, 40.0, 46.0, 52.0, 13.0)
            val hue = if (a.chroma >= b.chroma) a.hue else b.hue
            return pick((tones.asSequence().map { argb(hue, 2.0, it) } + tones.asSequence().map { argb(hue, 8.0, it - 3) })
                .map { readable(it, theme.background, dark, 3.0) }, 10, 4.0)
        }
        val ca = (a.chroma).coerceIn(30.0, 90.0); val cb = (b.chroma).coerceIn(24.0, 90.0)
        val candidates = sequence {
            yield(argb(a.hue, ca, main)); yield(argb(a.hue, ca, alt)); yield(argb(a.hue, ca * .5, main))
            yield(argb(b.hue, cb, main)); yield(argb(b.hue, cb, alt))
            yield(argb(a.hue + 30, ca, main)); yield(argb(a.hue - 30, ca, main))
            yield(argb(a.hue + 180, ca * .85, main)); yield(argb(b.hue + 180, cb * .85, third))
            yield(argb(a.hue + 120, ca * .8, main)); yield(argb(a.hue - 120, ca * .8, third))
            yield(argb(a.hue, ca, third)); yield(argb(b.hue, cb, third)); yield(argb(b.hue, cb * .5, alt))
            for (shift in listOf(60.0, -60.0, 90.0, -90.0, 150.0, -150.0)) { yield(argb(a.hue + shift, ca * .8, main)); yield(argb(a.hue + shift, ca * .7, alt)) }
        }
        return pick(candidates.map { readable(it, theme.background, dark, 3.0) }, 10, 12.0)
    }

    /** Five grounds at the tone of the design's own background (or surface): its own, tinted by its hues, neutral, opposite. */
    fun grounds(theme: AppTheme, role: DesignColorRole, default: Long): List<Long> {
        val base = hct(if (role == DesignColorRole.SURFACE) theme.surface else theme.background)
        val a = hct(theme.accent); val b = hct(theme.secondary)
        val tint = if (theme.dark) 7.0 else 9.0
        val t = base.tone
        val candidates = sequence {
            yield(argb(a.hue, tint, t)); yield(argb(b.hue, tint, t)); yield(argb(a.hue, 1.5, t))
            yield(argb(a.hue + 180, tint * .8, t)); yield(argb(b.hue + 180, tint * .8, t))
            yield(argb(a.hue, tint * 1.6, t)); yield(argb(b.hue, tint * 1.6, t))
            for (dt in listOf(3.0, -3.0, 6.0, -6.0)) { yield(argb(a.hue, tint, t + dt)); yield(argb(a.hue, 1.5, t + dt)) }
        }
        return pick(candidates, 5, 3.0, keep = listOf(default or 0xff000000L))
    }

    /** Ten text inks: near-black (or near-white on dark designs), quietly tinted with the design's hues. */
    fun inks(theme: AppTheme): List<Long> {
        val a = hct(theme.accent); val b = hct(theme.secondary)
        val tones = if (theme.dark) listOf(97.0, 94.0, 90.0) else listOf(7.0, 12.0, 18.0)
        val candidates = sequence<Long> {
            for (t in tones) { yield(argb(a.hue, 0.0, t)); yield(argb(a.hue, 6.0, t)); yield(argb(b.hue, 6.0, t)) }
            for (t in tones) { yield(argb(a.hue, 14.0, t)); yield(argb(b.hue, 14.0, t)); yield(argb(a.hue + 180, 8.0, t)) }
            for (t in tones) yield(argb(a.hue, 22.0, t))
        }.map { readable(it, theme.background, theme.dark, 7.0) }
        return pick(candidates, 10, 2.5)
    }

    /** The design's set for one role of the palette editor. */
    fun set(theme: AppTheme, role: DesignColorRole, default: Long): DesignColorSet {
        val colors = when (role) {
            DesignColorRole.BACKGROUND, DesignColorRole.SURFACE -> grounds(theme, role, default)
            DesignColorRole.TEXT -> inks(theme)
            DesignColorRole.ACCENT -> accents(theme)
        }
        return DesignColorSet(theme.title, colors.map { DesignSwatch("", it) }, role)
    }

    /** Twenty favourite colours for the active design: its ten accents and a lighter or deeper form of each. */
    fun favorites(theme: AppTheme): List<Long> {
        val first = accents(theme)
        val shift = if (theme.dark) -14.0 else 14.0
        val second = first.asSequence().map { val h = hct(it); argb(h.hue, h.chroma, h.tone + shift) } +
            first.asSequence().map { val h = hct(it); argb(h.hue, h.chroma * .6, h.tone - shift / 2) }
        return pick(second, 20, 4.0, keep = first)
    }

    /** The tone of a ground, for tests and previews. */
    internal fun tone(color: Long) = hct(color).tone
    internal fun chroma(color: Long) = hct(color).chroma
    @Suppress("unused") private fun clamp(v: Double) = min(100.0, max(0.0, v))
}
