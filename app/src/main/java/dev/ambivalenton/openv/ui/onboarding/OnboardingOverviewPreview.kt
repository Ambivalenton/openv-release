// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.QuoteFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Fiction stays on this screen and is never submitted to a journey provider. */
internal fun mordorOverviewExample():Pair<Favorite,Trip> {
    val home=Location(tr("Auenland"));val start=Location(tr("Hobbingen, Dorfplatz"))
    val bree=Location(tr("Bree, Westtor"));val change=Location(tr("Bree, Bahnhof"));val mordor=Location("Mordor")
    val time=java.time.LocalDate.of(2026,11,3).atTime(10,15).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun leg(id:String,a:Location,b:Location,s:Int,e:Int,type:LineType,line:String,platform:String?=null)=
        TripSegment(id,a,b,time+s*60_000,time+e*60_000,type,line,departurePlatform=platform)
    val trip=Trip("onboarding-middle-earth",listOf(
        leg("sh-walk",home,start,0,5,LineType.WALK,""),
        leg("sh-bus",start,bree,5,25,LineType.BUS,tr("Auenland-Express"),"2"),
        leg("br-walk",bree,change,25,30,LineType.WALK,""),
        leg("md-train",change,mordor,38,75,LineType.REGIONAL, tr("Schattenlinie"),"9"),
    ),tr("Fiktives Beispiel"),time)
    return Favorite(id="onboarding-mordor",label="Mordor",location=mordor) to trip
}

@Composable internal fun OnboardingOverviewPreview(favorite:Favorite?,settings:AppSettings,permissions:SetupPermissions,
    example:suspend (Favorite,AppSettings)->Trip?,onTextLayout:(TextLayoutResult)->Unit={},
    modifier:Modifier=Modifier,fillHeight:Boolean=false) {
    val fictional=remember {mordorOverviewExample()}
    var real by remember {mutableStateOf<Trip?>(null)}
    var loading by remember {mutableStateOf(false)}
    val latestExample by rememberUpdatedState(example)
    val manual=settings.originMode==OriginMode.MANUAL && settings.manualOrigin?.let {it.routable && !it.isDevicePosition}==true
    val eligible=favorite!=null && (manual || permissions.location!=SetupLocationAccess.NONE && permissions.locationEnabled)
    // Wording and visual changes reuse one response instead of firing another GPS/network request.
    LaunchedEffect(favorite?.id,favorite?.location,eligible,settings.originMode,settings.manualOrigin,TimetableProfile.identity(settings),settings.products) {
        real=null
        if(eligible && favorite!=null) {
            loading=true
            try {real=withTimeoutOrNull(15_000) {latestExample(favorite,settings)}?.takeIf {it.segments.isNotEmpty()}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){real=null}
            finally {loading=false}
        } else loading=false
    }
    val trip=real ?: fictional.second
    val destination=if(real!=null && favorite!=null) favorite else fictional.first
    val parts=OverviewSentenceGenerator.sentence(destination,trip,settings.overview,0)
    val ink=MaterialTheme.colorScheme.onSurface;val accent=MaterialTheme.colorScheme.primary
    val text=buildAnnotatedString {parts.forEach {part->
        withStyle(SpanStyle(color=if(part.emphasis==OverviewEmphasis.NORMAL)ink else accent,
            fontWeight=if(part.emphasis==OverviewEmphasis.NORMAL) FontWeight.Normal else FontWeight.SemiBold)) {append(part.text)}
    }}
    Column(modifier.fillMaxWidth().testTag("onboarding-overview-preview"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(when {
            real!=null->tr("Vorschau · Verbindung zu deinem Favoriten")
            loading->tr("Deine Verbindung wird geladen · vorerst ein fiktives Beispiel aus Mittelerde")
            eligible->tr("Live-Beispiel derzeit nicht verfügbar · fiktive Reise nach Mordor")
            else->tr("Fiktives Beispiel · Reise nach Mordor")
        },style=MaterialTheme.typography.labelMedium)
        QuoteFrame(QuoteDecoration.MATERIAL_CARD,Modifier.fillMaxWidth().then(if(fillHeight)Modifier.weight(1f) else Modifier)) {color->
            // As large as the card allows (up to 22 sp), wrapping only between words.
            dev.ambivalenton.openv.ui.components.FittedText(text,Modifier.fillMaxWidth().then(if(fillHeight)Modifier.fillMaxHeight() else Modifier.height(240.dp)).testTag("onboarding-overview-text"),
                color=color,fontSize=22.sp,style=MaterialTheme.typography.bodyLarge,onTextLayout=onTextLayout,maxLines=Int.MAX_VALUE)
        }
    }
}
