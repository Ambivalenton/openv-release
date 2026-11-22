// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2.10 / Sun and moon in one drawing. [night] 0 is the sun, 1 the moon. On the way the rays
 * turn a little and draw into the disc, a soft glow fades, the shadow glides in from the upper right
 * and carves the crescent (lit side to the lower left, never a tilted "mouth"), and two small
 * stars light up. The colour moves from a warm sun gold to [color].
 */
@Composable fun SunMoonGlyph(night: Float, color: Color, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .4f
    val sun = if (dark) Color(0xFFFFC53D) else Color(0xFFD98300)
    val dusk = if (dark) Color(0xFFFF8FA8) else Color(0xFFC94F7C)
    Canvas(modifier) { drawSunMoon(night, sun, dusk, color) }
}

private fun smooth(from: Float, to: Float, value: Float): Float {
    val x = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The drawing of [SunMoonGlyph]. The colour runs like a sunset through [dusk] instead of a grey
 * middle; the rays are drawn in before the shadow arrives, and only the glyph sits in its own layer,
 * so the shadow never cuts a ring into the glow.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunMoon(night: Float, sun: Color, dusk: Color, moon: Color) {
    val t = night.coerceIn(0f, 1f)
    val ink = if (t < .5f) androidx.compose.ui.graphics.lerp(sun, dusk, smooth(0f, .5f, t))
        else androidx.compose.ui.graphics.lerp(dusk, moon, smooth(.5f, 1f, t))
    val c = center; val r = size.minDimension * .25f
    // Glow of the sun, outside the layer.
    val glow = 1f - smooth(0f, .6f, t)
    if (glow > .01f) drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(sun.copy(alpha = .30f * glow), Color.Transparent),
        c, r * 2.1f), r * 2.1f, c)
    drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint()) }
    // Rays: they turn a little and draw into the disc before the shadow comes.
    val rays = 1f - smooth(0f, .62f, t)
    if (rays > .01f) rotate(36f * (1f - rays), c) {
        for (i in 0 until 8) {
            val a = i * PI / 4
            val inner = r * (1.38f - .3f * (1f - rays)); val outer = inner + r * .52f * rays
            drawLine(ink.copy(alpha = rays), c + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                c + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()), size.minDimension * .072f,
                androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
    // Disc, a little larger as the moon.
    val grow = smooth(.1f, .9f, t)
    val disc = r * (1f + .34f * grow)
    drawCircle(ink, disc, c)
    // The shadow comes from the upper right: far outside at day, carving the crescent at night.
    val cut = smooth(.25f, 1f, t)
    if (cut > .001f) {
        val reach = disc * (2.9f - 2.2f * cut)
        drawCircle(Color.Black, disc * .86f, c + Offset(reach * .76f, -reach * .64f), blendMode = BlendMode.DstOut)
    }
    drawIntoCanvas { it.restore() }
    // Two small stars light up beside the moon.
    val star = smooth(.62f, 1f, t)
    if (star > .01f) {
        fun spark(x: Float, y: Float, k: Float) {
            val p = c + Offset(x * r, y * r); val l = r * .22f * k * star
            drawLine(ink.copy(alpha = star), p - Offset(l, 0f), p + Offset(l, 0f), size.minDimension * .03f, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(ink.copy(alpha = star), p - Offset(0f, l), p + Offset(0f, l), size.minDimension * .03f, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        spark(1.25f, -.95f, 1f); spark(1.62f, -.18f, .7f)
    }
}

/**
 * 2.10: "Tag & Nacht" in the header of "Design & Darstellung". The symbol shows where the next tap
 * leads (sun: to the day design, moon: to the night design) and morphs fluidly; the press is a soft
 * dip of its own, so nothing jumps while the design changes.
 */
@Composable fun DayNightButton(settings: AppSettings, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val toNight = settings.nextDayNightIsNight(systemDark)
    val calm = LocalAppSettings.current.reduceMotion
    val progress by animateFloatAsState(if (toNight) 1f else 0f,
        if (calm) snap() else spring(dampingRatio = .82f, stiffness = 140f), label = "day-night")
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val label = if (toNight) tr("Zum Nacht-Theme wechseln") else tr("Zum Tag-Theme wechseln")
    IconButton({
        if (!calm) scope.launch { press.animateTo(.84f, tween(90, easing = FastOutSlowInEasing)); press.animateTo(1f, spring(dampingRatio = .45f, stiffness = 300f)) }
        onToggle()
    }, modifier.size(48.dp).testTag("day-night-toggle").semantics {
        contentDescription = label
        stateDescription = if (toNight) tr("Tag-Theme aktiv") else tr("Nacht-Theme aktiv")
    }) {
        SunMoonGlyph(progress, MaterialTheme.colorScheme.primary, Modifier.size(30.dp).graphicsLayer {
            scaleX = press.value; scaleY = press.value
            // A gentle half turn accompanies the change.
            rotationZ = -24f * kotlin.math.sin(progress * PI).toFloat()
        })
    }
}

/**
 * 2.10: the frame in "Modi & Verhalten" below "Minimalismus": by hand only, by the set times, or by
 * sunrise and sunset at the current location. The times are greyed out while they do not apply.
 */
@Composable fun DayNightFrame(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, place: Location?) {
    val day = settings.dayNight
    ModeFrame("mode-day-night", { m -> SunMoonGlyph(if (day.night) 1f else 0f, MaterialTheme.colorScheme.primary, m) }) {
        FittedText(tr("Tag & Nacht"), style = MaterialTheme.typography.titleMedium)
        Text(tr("Wechselt zwischen deinem Tag- und deinem Nacht-Design. Von Hand geht es jederzeit über Sonne oder Mond oben rechts in „Design & Darstellung“."),
            style = MaterialTheme.typography.bodySmall)
        fun schedule(value: DayNightSchedule) = update { it.copy(dayNight = it.dayNight.copy(schedule = value, lastAutoNight = null)) }
        ToggleRow(tr("Nur von Hand"), tr("Kein automatischer Wechsel."), day.schedule == DayNightSchedule.MANUAL) { on ->
            schedule(if (on) DayNightSchedule.MANUAL else DayNightSchedule.TIMES) }
        ToggleRow(tr("Nach Sonnenauf- und -untergang"), tr("Richtet sich nach deinem Standort, ohne Netzabfrage. Ohne bekannten Standort gelten die Zeiten unten."),
            day.schedule == DayNightSchedule.SUN) { on -> schedule(if (on) DayNightSchedule.SUN else DayNightSchedule.TIMES) }
        val timesApply = day.schedule == DayNightSchedule.TIMES || (day.schedule == DayNightSchedule.SUN && place == null)
        Column(Modifier.fillMaxWidth().testTag("day-night-times").alpha(if (timesApply) 1f else .42f)
            .then(if (timesApply) Modifier else Modifier.semantics { disabled() }.pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
            }), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ScheduleTimeField(tr("Tag ab"), day.dayStartMinute) { m -> update { it.copy(dayNight = it.dayNight.copy(dayStartMinute = m, lastAutoNight = null)) } }
            ScheduleTimeField(tr("Nacht ab"), day.nightStartMinute) { m -> update { it.copy(dayNight = it.dayNight.copy(nightStartMinute = m, lastAutoNight = null)) } }
        }
        if (day.schedule == DayNightSchedule.SUN && place?.latitude != null && place.longitude != null) {
            val zone = ZoneId.systemDefault()
            sunTimes(ZonedDateTime.now(zone).toLocalDate(), place.latitude, place.longitude)?.let { sun ->
                Text(tr("Heute: Sonnenaufgang %1\$s · Sonnenuntergang %2\$s", clockTime(sun.sunrise.toEpochMilli()), clockTime(sun.sunset.toEpochMilli())),
                    Modifier.testTag("day-night-sun"), style = MaterialTheme.typography.labelMedium)
            } ?: Text(tr("Heute geht die Sonne hier nicht auf oder unter; es gelten die Zeiten oben."), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 2.8: fires once after [millis] of one unbroken press without moving. Lifting, moving or a second
 * finger cancel it; after it fired, the release is consumed, so the tile does not open as well.
 */
fun Modifier.secretHold(millis: Long = 3_000, onHold: () -> Unit): Modifier = pointerInput(onHold, millis) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val cancelled = withTimeoutOrNull(millis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || event.changes.count { it.pressed } > 1 ||
                    (change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (cancelled == null) {
            onHold()
            do { val event = awaitPointerEvent(PointerEventPass.Initial); event.changes.forEach { it.consume() } }
            while (event.changes.any { it.pressed })
        }
    }
}

/**
 * 2.8: the small hidden window: two independent options, both off at first and kept locally.
 * The random theme takes effect at the next cold start; switching it off shows the chosen theme.
 */
@Composable fun SecretDesignDialog(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit,
    onRandomOff: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("secret-design-dialog"),
        icon = { SunMoonGlyph(.35f, MaterialTheme.colorScheme.tertiary, Modifier.size(36.dp)) },
        title = { Text(tr("Geheime Design-Ecke")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleRow(tr("Zufälliges Theme beim App-Start"),
                    tr("Bei jedem Kaltstart zeigt Öpen-V ein anderes Theme. Deine Auswahl bleibt gespeichert und ist nach dem Ausschalten wieder da."),
                    settings.randomThemeOnStart) { on -> update { it.copy(randomThemeOnStart = on) }; if (!on) onRandomOff() }
                ToggleRow(tr("Regenbogen-Modus"),
                    tr("Jeder Wechsel zwischen Übersicht, Suchen und Abfahrten bringt neue Akzentfarben. Nur vorübergehend und nie in einem gespeicherten Theme."),
                    settings.rainbowMode) { on -> update { it.copy(rainbowMode = on) } }
            }
        },
        confirmButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp).testTag("secret-design-close")) { Text(tr("Fertig")) } })
}
