// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.rendering

import dev.ambivalenton.openv.localization.tr

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.boardCandidates
import dev.ambivalenton.openv.data.repository.activeBoardSelection
import dev.ambivalenton.openv.data.repository.boardConfigurationKey
import dev.ambivalenton.openv.data.repository.boardLocationKey
import dev.ambivalenton.openv.ui.theme.readableForeground
import kotlin.math.*

data class BoardPresentation(
    val title: String,
    val rows: List<Departure>,
    val design: BoardDesign,
    val colors: BoardColors,
    val caption: String,
    val emptyMessage: String,
    val isDesignPreview: Boolean = false,
    val typeface:Typeface?=null,
    val appStyle:AppTheme?=null,val appSurface:Long?=null,val appSecondary:Long?=null,
    val appCornerScale:Float=1f,val appTextScale:Float=1f,val appDecoration:Boolean=true,
    /** Roles of a ThemeLanguage design when the board follows the app theme. */
    val appRoles:BoardLanguageRoles?=null,
    /** The app theme's own typeface for the adaptive board (a chosen app font still wins). */
    val themeTypeface:Typeface?=null,
    /** "App-Theme" board: the app's own surfaces and inks, so the board blends into the screen. */
    val appPalette:BoardAppPalette?=null,
) {
    fun description(limit: Int = rows.size): String =
        title +
            ". " +
            rows.take(if (rows.isEmpty()) 0 else limit.coerceAtLeast(1)).joinToString(". ") {
                tr("%1\$s %2\$s, %3\$s, %4\$s", it.lineType.label, it.lineName, it.destination, clockTime(it.time)) +
                    (it.platform?.let { p -> ", "+platformName(p,it.lineType) } ?: "") +
                    (if(it.journey!=null)" · "+tr("Zeitstrahl öffnen")else "") +
                    if (it.delay.cancelled) tr(", fällt aus")
                    else
                        it.delay.minutes
                            ?.takeIf { m -> m > 0 }
                            ?.let { m -> tr(", %1\$s Minuten verspätet", m) }
                            .orEmpty()
            } +
            tr(". %1\$s", caption) +
            if (rows.isEmpty()) tr(". %1\$s", emptyMessage) else ""
}

/** Opaque ARGB colours of the app scheme (translucent glass surfaces composited onto the background). */
data class BoardAppPalette(val background:Int,val container:Int,val surface:Int,val primary:Int,val primaryContainer:Int,
    val onPrimaryContainer:Int,val secondary:Int,val onSurface:Int,val onSurfaceVariant:Int,val outlineVariant:Int,val error:Int)

private fun ColorScheme.boardAppPalette():BoardAppPalette {
    val base=background.toArgb() or 0xff000000.toInt()
    fun solid(c:androidx.compose.ui.graphics.Color)=androidx.core.graphics.ColorUtils.compositeColors(c.toArgb(),base) or 0xff000000.toInt()
    return BoardAppPalette(base,solid(surfaceContainerHighest),solid(surfaceContainer),solid(primary),solid(primaryContainer),
        solid(onPrimaryContainer),solid(secondary),solid(onSurface),solid(onSurfaceVariant),solid(outlineVariant),solid(error))
}

/** Shared projection: appearance never changes the identity of the cached station data. */
fun boardPresentation(
    state: StoredState,
    scheme: ColorScheme,
    inApp: Boolean = false,
    now: Long = System.currentTimeMillis(),
    typeface:Typeface?=null,
    /** Bundled typeface of the app theme; [typeface] (the user's font) wins. */
    themeTypeface:Typeface?=null,
): BoardPresentation {
    val options = state.settings.board
    val following=normalizedBoardDesign(options.design).followsAppTheme
    val design=if(inApp&&options.forceMaterialInApp&&!following)BoardDesign.MATERIAL else normalizedBoardDesign(options.design)
    val integrated=design==BoardDesign.APP_INTEGRATED
    val palette=if(integrated)scheme.boardAppPalette() else null
    val appStyle=if(following)state.settings.appTheme?:when {
        state.settings.themeMode==ThemeMode.AMOLED->AppTheme.AMOLED_BLACK
        scheme.background.luminance()<.35f->AppTheme.CLASSIC_DARK
        else->AppTheme.CLASSIC_LIGHT
    }else null
    val cached =
        state.departureBoard.takeIf { it.configurationKey == boardConfigurationKey(state) }
            ?: DepartureBoardState()
    val material =
        BoardColors(
            scheme.onSurface.toArgb().toLong() and 0xffffffffL,
            scheme.surfaceContainer.toArgb().toLong() and 0xffffffffL,
            scheme.primary.toArgb().toLong() and 0xffffffffL,
        )
    // "App-Theme" (APP_INTEGRATED) with a ThemeLanguage design is printed in the app's own
    // material; "Display" (APP_THEME) shows either a curated screen or the language's real technology.
    val languageBoard=integrated && appStyle!!.themeLanguage!=ThemeLanguage.STANDARD
    val displayTechnology=if(design==BoardDesign.APP_THEME) displayTechnologyFor(appStyle!!) else null
    val raw =
        if(languageBoard)material.copy(background=scheme.background.toArgb().toLong() and 0xffffffffL)
        else if(palette!=null)BoardColors(palette.onSurface.toLong() and 0xffffffffL,palette.container.toLong() and 0xffffffffL,palette.primary.toLong() and 0xffffffffL)
        else if(following && displayTechnology!=null)themedDisplayColors(appStyle!!,material.copy(background=scheme.background.toArgb().toLong() and 0xffffffffL),
            scheme.secondary.toArgb().toLong() and 0xffffffffL)
        else if(following)displayProfile(appStyle!!,state.settings.appThemeColors[appStyle] ?: appStyle.defaultColors()).let {
            BoardColors(it.ink.toLong() and 0xffffffffL,it.background.toLong() and 0xffffffffL,it.accent.toLong() and 0xffffffffL)}
        else if (inApp && options.forceMaterialInApp) material
        else
            options.customColors[design]
                ?: boardColorPresets(design).firstOrNull()?.colors
                ?: material
    val stopAccent = cached.stop?.let { state.stopColors[boardLocationKey(it)] }
    val colors =
        raw.copy(
            foreground = readableForeground(raw.foreground, raw.background),
            accent = readableForeground(stopAccent ?: raw.accent, raw.background),
        )
    return BoardPresentation(
        cached.stop?.name ?: tr("Öpen-V"),
        cached.departures
            .filter {
                it.time >= now - 60_000 &&
                    it.lineType in options.products &&
                    (state.settings.includeLongDistance || LineType.ICE in it.journeyQuery?.products.orEmpty() ||
                        !dev.ambivalenton.openv.data.model.isLongDistance(it.lineType, it.lineName))
            }
            .distinctBy { it.id }
            .sortedBy { it.time }
            .take(30),
        design,
        colors,
        when {
            // H05: the cause in a few words, then the stand of the rows that are still shown.
            cached.error != null -> {
                val stand = if (cached.scheduled) tr("Sollfahrplan")
                    else if (cached.fetchedAt > 0) tr("Stand %1\$s", clockTime(cached.fetchedAt)) else tr("erneut laden")
                when (cached.errorKind) {
                    "OFFLINE" -> tr("Offline · %1\$s", stand)
                    "TIMEOUT" -> tr("Keine Antwort · %1\$s", stand)
                    "RATE_LIMITED" -> tr("Kurz pausiert · %1\$s", stand)
                    "UNAVAILABLE", "INVALID_RESPONSE" -> tr("Dienst antwortet nicht · %1\$s", stand)
                    "MISSING_START" -> tr("Start fehlt · %1\$s", stand)
                    null -> tr("Offline · %1\$s", stand)
                    else -> tr("Nicht aktualisiert · %1\$s", stand)
                }
            }
            cached.fetchedAt == 0L -> tr("Standort & Abfahrten laden")
            activeBoardSelection(state) == null && state.settings.originMode != OriginMode.MANUAL && now - cached.presenceAt !in 0..15 * 60_000 ->
                tr("Standort aktualisieren · Stand %1\$s", clockTime(cached.fetchedAt))
            now - cached.fetchedAt !in 0..120_000 ->
                tr("Stand %1\$s · aktualisieren", clockTime(cached.fetchedAt))
            else -> tr("Stand %1\$s", clockTime(cached.fetchedAt))
        },
        when {
            options.products.isEmpty() -> tr("Verkehrsmittel in den Einstellungen auswählen")
            boardCandidates(state).isEmpty() -> tr("Haltestellen hinzufügen")
            cached.error != null -> boardFailureText(cached.errorKind) ?: cached.error
            cached.fetchedAt > 0 -> tr("Keine Abfahrten gemeldet")
            else -> tr("Abfahrten werden geladen")
        },
        typeface=typeface,appStyle=appStyle,
        appSurface=if(following)scheme.surfaceContainer.toArgb().toLong() and 0xffffffffL else null,
        appSecondary=if(following)scheme.secondary.toArgb().toLong() and 0xffffffffL else null,
        appCornerScale=state.settings.readability.normalized().cornerScale,appTextScale=state.settings.readability.normalized().textScale,
        appDecoration=!state.settings.reduceMotion && state.settings.readability.contrast!=AppContrast.HIGH,
        appRoles=if(languageBoard) scheme.boardLanguageRoles() else null,
        themeTypeface=themeTypeface.takeIf {following},
        appPalette=palette,
    )
}

data class BoardLayout(
    val padding: Float,
    val rowHeight: Float,
    val rows: Int,
    val header: Float,
    val footer: Float,
    val narrow: Boolean,
    val singleLine: Boolean = false,
    val journeyLinkHeight:Float = 0f,
)

fun boardLayout(width: Float, height: Float, fontScale: Float,journeyRows:Boolean=false): BoardLayout {
    if (width >= 220 && height <= 220 && !journeyRows) {
        val pad = 6f
        val heading = if (height >= 100) 22f else 18f
        val foot = if (height >= 110) 12f else 0f
        val available = (height - 2 * pad - heading - foot).coerceAtLeast(0f)
        val count = maxOf(4, floor(available / 22f).toInt()).coerceAtMost(30)
        return BoardLayout(pad, available / count, count, heading, foot, false, true)
    }
    val scale = fontScale.coerceIn(1f, 2f)
    val compact = height < 180
    val padding = if (width < 120) 6f else if (compact) 8f else 12f
    val narrow = width < 230 * scale
    val linkHeight=if(journeyRows)14f*scale else 0f
    val rowHeight = (if (narrow) if (compact) 44f else 52f else if (compact) 30f else 34f) * scale+linkHeight
    val header = (if (compact) 24f else 29f) * scale
    val footer = if (height >= 100) (if (compact) 16f else 22f) * scale else 0f
    val rows = floor((height - padding * 2 - header - footer) / rowHeight).toInt().coerceIn(0, 30)
    return BoardLayout(padding, rowHeight, rows, header, footer, narrow,journeyLinkHeight=linkHeight)
}

/** Shared measurement for the bitmap and its Compose/Glance row hit areas. */
fun BoardPresentation.layout(width:Float,height:Float,fontScale:Float)=boardLayout(width,height,
    if(appStyle!=null)fontScale*appTextScale else fontScale,rows.any {it.journey!=null})

/** Identical Canvas glyph/layout renderer for Compose and Glance, with a bounded bitmap size. */
object BoardRenderer {
    fun render(
        model: BoardPresentation,
        width: Float,
        height: Float,
        fontScale: Float = 1f,
        headerAction: Boolean = false,
        clipCorners: Boolean = true,
        /** Pixels per dp of the screen showing the board. In the app the board is drawn at
         * the real screen resolution (it was about 1.2 px/dp and scaled up, so fine dots and type
         * blurred); widgets keep the small bounded budget (0). */
        density: Float = 0f,
    ): Bitmap {
        val w = width.coerceIn(40f, 1000f)
        val h = height.coerceIn(30f, 1600f)
        val sampling = if (density > 0f) minOf(density.coerceAtMost(3.5f), sqrt(4_500_000f / (w * h)))
            else minOf(1.75f, sqrt(240_000f / (w * h)))
        val image =
            createBitmap(
                (w * sampling).roundToInt().coerceAtLeast(1),
                (h * sampling).roundToInt().coerceAtLeast(1),
            )
        val canvas = Canvas(image)
        canvas.scale(image.width / w, image.height / h)
        val adaptiveScale=if(model.appStyle!=null)fontScale*model.appTextScale else fontScale
        val layout = if(model.isDesignPreview) BoardLayout(8f,h-16f,1,0f,0f,false,true) else model.layout(w,h,fontScale)
        val p = layout.padding
        val bg = model.colors.background.toInt()
        val ink = model.colors.foreground.toInt()
        val accent = model.colors.accent.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = model.design in setOf(BoardDesign.DOT_MATRIX, BoardDesign.FLIP_DOT, BoardDesign.MODERN_MATRIX)
        val mono =
            model.design in listOf(BoardDesign.DOS, BoardDesign.TELETEXT, BoardDesign.SEVEN_SEGMENT,
                BoardDesign.SPLIT_FLAP, BoardDesign.E_PAPER, BoardDesign.DEPARTURE_TICKET, BoardDesign.RETRO_CRT, BoardDesign.CYBERPUNK)
        val typeface =
            model.typeface ?: if (mono) Typeface.MONOSPACE else Typeface.create("sans-serif", Typeface.NORMAL)
        val path =
            Path().apply {
                addRoundRect(
                    RectF(0f, 0f, w, h),
                    if (mono) 3f else 20f,
                    if (mono) 3f else 20f,
                    Path.Direction.CW,
                )
            }
        if(clipCorners && model.appStyle==null && !model.design.followsAppTheme) canvas.clipPath(path)
        canvas.drawColor(bg)
        // Without a resolved theme (direct renderer use) both following designs still keep their own look.
        val followed=if(model.design.followsAppTheme && model.appStyle==null)model.copy(appStyle=AppTheme.CLASSIC_DARK) else model
        val followedTheme=followed.appStyle
        // "App-Theme": the ten design-language collections print the board in their own material.
        if(model.design==BoardDesign.APP_INTEGRATED && followedTheme!=null && followedTheme.themeLanguage!=ThemeLanguage.STANDARD &&
            drawLanguageBoardDesign(canvas,followed,w,h,layout,adaptiveScale,headerAction,followedTheme)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        if(model.design==BoardDesign.APP_INTEGRATED && drawAppThemeBoard(canvas,followed,w,h,layout,adaptiveScale,headerAction)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        // "Display": a curated screen, unless the theme's design language brings a real display technology (era renderer below).
        if(model.design==BoardDesign.APP_THEME && (followedTheme==null || displayTechnologyFor(followedTheme)==null) &&
            drawThemeDisplay(canvas,followed,w,h,layout,adaptiveScale,headerAction)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        if(drawTeletextBoard(canvas,model,w,h,layout,fontScale,headerAction)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        if(drawEraBoardDesign(canvas,model,w,h,layout,adaptiveScale,headerAction)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        if(drawCreativeBoardDesign(canvas,model,w,h,layout,fontScale,headerAction)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        if(drawAdditionalBoardDesign(canvas,model,w,h,layout,fontScale,headerAction)){drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale);return image}
        // LED and flip-dot boards draw their real cells (lit and unlit) per text field, so the
        // former sprinkle of background dots is gone; the panel only gets the depth of its housing.
        val dots = when (model.design) {
            BoardDesign.DOT_MATRIX, BoardDesign.MODERN_MATRIX -> BoardDisplayTechnology.LED_MATRIX
            BoardDesign.FLIP_DOT -> BoardDisplayTechnology.FLIP_DOT
            else -> null
        }
        if (dots != null) {
            paint.shader = LinearGradient(0f, 0f, 0f, h, ColorUtils.blendARGB(bg, Color.WHITE, .03f), ColorUtils.blendARGB(bg, Color.BLACK, .3f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        }
        /** Cells of [text] whose cap height [cap] starts at [top] (LED or flip-dot module). */
        fun matrixText(text: String, x: Float, top: Float, cap: Float, color: Int, width: Float) {
            val technology = dots ?: return
            val pitch = cap / MatrixFont.CAP
            drawDotText(canvas, technology, text, x, top + pitch * MatrixFont.ROWS / 2f, width, cap / 1.18f, pitch * MatrixFont.ROWS, color, typeface, false)
        }
        if(model.design==BoardDesign.CYBERPUNK) {
            paint.color=ink;paint.alpha=18;paint.strokeWidth=.7f
            for(x in 0 until w.toInt() step 20) canvas.drawLine(x.toFloat(),0f,x.toFloat(),h,paint)
            for(y in 0 until h.toInt() step 20) canvas.drawLine(0f,y.toFloat(),w,y.toFloat(),paint)
            paint.alpha=255
        }
        if (model.design == BoardDesign.NIGHT_SKY) {
            // Fixed artwork: no battery-draining animation or random changes on widget refresh.
            paint.color = accent
            repeat(42) { star ->
                paint.alpha = 12 + star % 4 * 7
                canvas.drawCircle(((star * 97 + 23) % w.toInt()).toFloat(),
                    ((star * 53 + 7) % h.toInt()).toFloat(), if (star % 6 == 0) 1.4f else .7f, paint)
            }
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.alpha = 40
            canvas.drawCircle(w - 40, -12f, 62f, paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
        }
        if (model.design == BoardDesign.DEPARTURE_TICKET) {
            paint.color = accent
            paint.alpha = 80
            var hole = 9f
            while (hole < h) {
                canvas.drawCircle(3f, hole, 1.4f, paint)
                canvas.drawCircle(w - 3, hole, 1.4f, paint)
                hole += 11f
            }
            paint.alpha = 255
        }
        if(model.design==BoardDesign.RETRO_CRT) {
            paint.color=ink;paint.alpha=18
            for(yy in 1 until h.toInt() step 3) canvas.drawRect(0f,yy.toFloat(),w,yy+1f,paint)
            paint.alpha=255
        }
        val scale =
            if (layout.singleLine) minOf(1.1f, layout.rowHeight / 20f)
            else fontScale.coerceIn(1f, 2f)
        fun label(
            text: String,
            x: Float,
            baseline: Float,
            size: Float,
            color: Int = ink,
            max: Float = w - p - x,
            bold: Boolean = false,
            fit: Boolean = false,
            centered: Boolean = false,
        ) {
            val tp =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    this.color = color
                    this.typeface =
                        Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
                }
            // On LED and flip-dot boards every text is made of cells, on split-flap boards of flaps.
            if (dots != null) {
                val cap = size * .72f
                drawDotText(canvas, dots, text, x, baseline - cap / 2f + cap / MatrixFont.CAP, max.coerceAtLeast(1f), cap / 1.18f,
                    cap / MatrixFont.CAP * MatrixFont.ROWS, color, typeface, centered)
                return
            }
            if (model.design == BoardDesign.SPLIT_FLAP && baseline > p + layout.header && baseline < h - p - layout.footer) {
                drawFlapText(canvas, text, x, baseline - size * .36f, max.coerceAtLeast(1f), size, size * 1.32f, color, bg, typeface, bold)
                return
            }
            if(fit && tp.measureText(text)>max.coerceAtLeast(1f)) tp.textSize*=max.coerceAtLeast(1f)/tp.measureText(text)
            val fitted =
                TextUtils.ellipsize(text, tp, max.coerceAtLeast(1f), TextUtils.TruncateAt.END)
                    .toString()
            if(model.design==BoardDesign.SPLIT_FLAP && baseline>p+layout.header && baseline<h-p-layout.footer) {
                val advance=tp.measureText("M").coerceAtLeast(1f)
                val tiles=ceil(tp.measureText(fitted)/advance).toInt()
                val tile=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=ink;alpha=18}
                repeat(tiles) {i->canvas.drawRoundRect(x+i*advance+.5f,baseline-size*.90f,x+(i+1)*advance-.5f,baseline+size*.18f,1.2f,1.2f,tile)}
                tile.color=bg;tile.alpha=65;tile.strokeWidth=.6f
                canvas.drawLine(x,baseline-size*.32f,x+tp.measureText(fitted),baseline-size*.32f,tile)
                canvas.drawText(fitted,x,baseline,tp)
            } else {tp.textAlign=if(centered)Paint.Align.CENTER else Paint.Align.LEFT;canvas.drawText(fitted,if(centered)x+max/2 else x,baseline,tp)}
        }
        val title =
            when (model.design) {
                BoardDesign.DOS -> "C:\\> ${model.title}"
                BoardDesign.TELETEXT -> model.title.uppercase()
                else -> model.title
            }
        if(!model.isDesignPreview) label(
            title,
            p,
            p + 17 * scale,
            15 * scale,
            accent,
            max = w - 2 * p - if (headerAction) 30 else 0,
            bold = true,
        )
        var y = p + layout.header
        val visible = model.rows.take(layout.rows)
        if (visible.isEmpty()) {
            val message =
                if (layout.rows == 0 && model.rows.isNotEmpty())
                    "${model.rows.first().lineName} ${clockTime(model.rows.first().time)}"
                else model.emptyMessage
            label(message, p, (y + 15 * scale).coerceAtMost(h - p), 12 * scale)
        }
        visible.forEachIndexed { index, row ->
            val time = if (row.delay.cancelled) tr("Fällt aus") else clockTime(row.time)
            val late = if(row.delay.cancelled) "" else row.delay.minutes?.takeIf { it > 0 }?.let { "+$it" }.orEmpty()
            when (model.design) {
                BoardDesign.SPLIT_FLAP -> {
                    paint.color = ink
                    paint.alpha = 12
                    canvas.drawRoundRect(p - 3, y + 1, w - p + 3, y + layout.rowHeight - 2, 3f, 3f, paint)
                    paint.alpha = 100
                    paint.color = bg
                    paint.strokeWidth = 1.3f
                    canvas.drawLine(p - 3, y + layout.rowHeight / 2, w - p + 3, y + layout.rowHeight / 2, paint)
                    paint.alpha = 255
                }
                BoardDesign.FLIP_DOT -> {
                    paint.color = ink
                    paint.alpha = 45
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = .6f
                    canvas.drawRect(p - 3, y, w - p + 3, y + layout.rowHeight - 2, paint)
                    paint.style = Paint.Style.FILL
                    paint.alpha = 255
                }
                BoardDesign.METRO_MAP -> {
                    paint.color = accent
                    paint.strokeWidth = 2f
                    canvas.drawLine(p - 5, y, p - 5, y + layout.rowHeight, paint)
                    canvas.drawCircle(p - 5, y + layout.rowHeight / 2, 3f, paint)
                    paint.color = bg
                    canvas.drawCircle(p - 5, y + layout.rowHeight / 2, 1.5f, paint)
                }
                BoardDesign.CYBERPUNK -> {
                    paint.color=bg;paint.alpha=215
                    canvas.drawRoundRect(p-3,y+1,w-p+3,y+layout.rowHeight-2,3f,3f,paint);paint.alpha=255
                }
                BoardDesign.NIGHT_SKY -> {
                    paint.color = accent
                    paint.alpha = if (index % 2 == 0) 9 else 17
                    canvas.drawRoundRect(p - 3, y + 1, w - p + 3, y + layout.rowHeight - 2, 8f, 8f, paint)
                    paint.alpha = 255
                }
                BoardDesign.E_PAPER, BoardDesign.DEPARTURE_TICKET -> {
                    paint.color = ink
                    paint.alpha = 100
                    paint.strokeWidth = .65f
                    paint.pathEffect = if (model.design == BoardDesign.DEPARTURE_TICKET) DashPathEffect(floatArrayOf(3f, 3f), 0f) else null
                    canvas.drawLine(p, y + layout.rowHeight - 1, w - p, y + layout.rowHeight - 1, paint)
                    paint.pathEffect = null
                    paint.alpha = 255
                }
                else -> Unit
            }
            if(model.isDesignPreview) {
                val height=minOf(18f*fontScale,(w-2*p)/(row.destination.length.coerceAtLeast(1)*.86f)).coerceAtLeast(7f)
                val cy=y+layout.rowHeight/2
                if(matrix || model.design==BoardDesign.TELETEXT)
                    matrixText(row.destination, p, cy-height/2, height, ink, w-2*p)
                else label(row.destination,p,cy+height*.34f,height,ink,w-2*p,true)
                return@forEachIndexed
            }
            if (layout.singleLine) {
                val baseline = y + layout.rowHeight / 2 + 4 * scale
                val lineWidth = 45f * scale
                val timeWidth = if(late.isNotEmpty() || row.delay.cancelled) minOf(86f*scale,w*.32f) else 48f*scale
                if (model.design == BoardDesign.MATERIAL) {
                    paint.color = accent
                    paint.alpha = 24
                    canvas.drawRoundRect(
                        p,
                        y + 2,
                        p + lineWidth - 5,
                        y + layout.rowHeight - 2,
                        6f,
                        6f,
                        paint,
                    )
                    paint.alpha = 255
                }
                label(row.lineName, if(model.design==BoardDesign.MATERIAL)p else p+3, baseline, 11 * scale, accent, if(model.design==BoardDesign.MATERIAL)lineWidth-5 else lineWidth-8, true, centered=model.design==BoardDesign.MATERIAL)
                val dx = p + lineWidth
                val dw = w - p - dx - timeWidth - 5
                if (
                    matrix || model.design == BoardDesign.TELETEXT
                ) {
                    matrixText(row.destination, dx, baseline - 10 * scale, 10f * scale, ink, dw)
                } else label(row.destination, dx, baseline, 11 * scale, ink, dw)
                if (model.design == BoardDesign.SEVEN_SEGMENT && !row.delay.cancelled) {
                    drawSegments(
                        canvas,
                        time,
                        w - p - timeWidth,
                        baseline - 11 * scale,
                        12 * scale,
                        ink,
                    )
                    if(late.isNotEmpty()) label(late,w-p-timeWidth+38*scale,baseline,9*scale,accent,timeWidth-38*scale,fit=true)
                } else
                    label(
                        if(!row.delay.cancelled && late.isNotEmpty()) "$time $late" else time,
                        w - p - timeWidth,
                        baseline,
                        11 * scale,
                        if (row.delay.cancelled || late.isNotEmpty()) accent else ink,
                        timeWidth,
                        true,
                        fit = true,
                    )
                y += layout.rowHeight
                return@forEachIndexed
            }
            val lineW = (if (layout.narrow) 45f else 50f) * scale
            val timeW = if(row.delay.cancelled) minOf(80f*scale,w*.34f) else 52f*scale
            if (model.design == BoardDesign.DB || model.design == BoardDesign.TELETEXT) {
                paint.color = if (index % 2 == 0) ink else accent
                paint.alpha = 12
                canvas.drawRect(p - 3, y, w - p + 3, y + layout.rowHeight - 2, paint)
                paint.alpha = 255
            }
            if (model.design == BoardDesign.MATERIAL) {
                paint.color = accent
                paint.alpha = 25
                canvas.drawRoundRect(p, y + 2, p + lineW, y + 27 * scale, 9f, 9f, paint)
                paint.alpha = 255
            }
            val lineX = p
            if (matrix)
                matrixText(row.lineName, lineX, y + 7 * scale, 12f * scale, accent, lineW - 6)
            else label(row.lineName, lineX, y + 19 * scale, 12 * scale, accent, if(model.design==BoardDesign.MATERIAL)lineW else lineW-5, true, centered=model.design==BoardDesign.MATERIAL)
            val destX = if (layout.narrow) p else p + lineW + 8
            val destY = if (layout.narrow) y + 42 * scale else y + 19 * scale
            val destW = if (layout.narrow) w - 2 * p else w - p - destX - timeW - 6
            if (matrix || model.design == BoardDesign.TELETEXT)
                matrixText(row.destination, destX, destY - 12 * scale, 12f * scale, ink, destW)
            else label(row.destination, destX, destY, 12 * scale, ink, destW)
            val timeX = w - p - timeW
            if (model.design == BoardDesign.SEVEN_SEGMENT && !row.delay.cancelled)
                drawSegments(canvas, time, timeX, y + 4 * scale, 16 * scale, ink)
            else if (matrix && !row.delay.cancelled)
                matrixText(time, timeX, y + 7 * scale, 12f * scale, ink, timeW)
            else
                label(
                    time,
                    timeX,
                    y + 19 * scale,
                    12 * scale,
                    if (row.delay.cancelled) accent else ink,
                    timeW,
                    true,
                    fit = true,
                )
            if (!layout.narrow)
                label(
                    listOfNotNull(
                            platformName(row.platform,row.lineType),
                            late.takeIf { it.isNotEmpty() }?.let { tr("%1\$s min", it) },
                        )
                        .joinToString(" · "),
                    destX,
                    y + 29 * scale,
                    9 * scale,
                    accent,
                    destW,
                )
            else if (late.isNotEmpty())
                label(
                    late,
                    p + lineW + 3,
                    y + 19 * scale,
                    10 * scale,
                    accent,
                    (timeX - p - lineW - 7).coerceAtLeast(1f),
                )
            if (model.design == BoardDesign.DB) {
                paint.color = bg
                paint.strokeWidth = 1f
                canvas.drawLine(p, y + layout.rowHeight - 2, w - p, y + layout.rowHeight - 2, paint)
            }
            y += layout.rowHeight
        }
        if (layout.footer > 0)
            label(
                model.caption,
                p,
                h - p - 2,
                9 * scale,
                accent,
            )
        drawBoardJourneyLinks(canvas,model,w,layout,adaptiveScale)
        return image
    }

    private fun drawSegments(
        canvas: Canvas, time: String, x: Float, y: Float, height: Float, color: Int,
    ) {
        val masks=intArrayOf(0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color}
        val digitW=height*.52f;val t=height*.095f;val gap=height*.14f
        var xx=x
        fun horizontal(left:Float,top:Float,right:Float)=Path().apply {
            moveTo(left,top+t/2);lineTo(left+t/2,top);lineTo(right-t/2,top)
            lineTo(right,top+t/2);lineTo(right-t/2,top+t);lineTo(left+t/2,top+t);close()
        }
        fun vertical(left:Float,top:Float,bottom:Float)=Path().apply {
            moveTo(left+t/2,top);lineTo(left+t,top+t/2);lineTo(left+t,bottom-t/2)
            lineTo(left+t/2,bottom);lineTo(left,bottom-t/2);lineTo(left,top+t/2);close()
        }
        time.forEach {c->
            if(c==':') {
                paint.alpha=255
                canvas.drawCircle(xx+height*.10f,y+height*.34f,t*.42f,paint)
                canvas.drawCircle(xx+height*.10f,y+height*.70f,t*.42f,paint)
                xx+=height*.20f
            } else if(c.isDigit()) {
                val upperTop=y+t*1.25f;val upperBottom=y+height/2-t*.75f
                val lowerTop=y+height/2+t*.75f;val lowerBottom=y+height-t*1.25f
                val segments=listOf(
                    horizontal(xx+t*.3f,y,xx+digitW-t*.3f),
                    vertical(xx+digitW-t,upperTop,upperBottom),
                    vertical(xx+digitW-t,lowerTop,lowerBottom),
                    horizontal(xx+t*.3f,y+height-t,xx+digitW-t*.3f),
                    vertical(xx,lowerTop,lowerBottom),vertical(xx,upperTop,upperBottom),
                    horizontal(xx+t*.3f,y+height/2-t/2,xx+digitW-t*.3f),
                )
                segments.forEachIndexed {bit,path->paint.alpha=if(masks[c-'0'] and (1 shl bit)!=0) 255 else 16;canvas.drawPath(path,paint)}
                xx+=digitW+gap
            }
        }
    }
}

/** H05: the message line of a board whose refresh failed, by cause (null: keep the provider text). */
internal fun boardFailureText(kind: String?): String? = when (kind) {
    "OFFLINE" -> tr("Keine Internetverbindung. Die Tafel zeigt den letzten Stand.")
    "TIMEOUT" -> tr("Der Fahrplandienst antwortet nicht rechtzeitig. Bitte erneut laden.")
    "RATE_LIMITED" -> tr("Der Fahrplandienst begrenzt gerade die Anfragen. Gleich erneut laden.")
    "UNAVAILABLE", "INVALID_RESPONSE" -> tr("Der Fahrplandienst antwortet gerade nicht. Bitte erneut laden.")
    "MISSING_START" -> tr("Wähle eine feste Haltestelle oder einen Start.")
    else -> null
}
