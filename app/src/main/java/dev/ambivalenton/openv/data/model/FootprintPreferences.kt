// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import kotlinx.serialization.Serializable
import dev.ambivalenton.openv.localization.tr

@Serializable enum class FootprintStyle(val titleKey:String) {
    CAT("Katzenpfoten"),DOG("Hundepfoten"),DUCK("Ente"),SHOE("Schuhabdruck"),BAREFOOT("Barfuß"),
    HORSE("Pferdehuf"),BIRD("Vogelspur"),RABBIT("Kaninchenpfote"),FOX("Fuchspfote"),BEAR("Bärenpfote"),
    LIZARD("Echsenfuß"),TURTLE("Schildkrötenfuß"),ELEPHANT("Elefantenfuß"),DINOSAUR("Dinosaurierspur"),ROBOT("Roboterfuß"),
    DEER("Hirschhuf"),GOAT("Ziegenhuf"),WOLF("Wolfspfote"),OTTER("Otterpfote"),RACCOON("Waschbärpfote"),
    HEDGEHOG("Igelpfote"),FROG("Froschfuß"),BADGER("Dachspfote"),HIKING_BOOT("Wanderschuh"),HIGH_HEEL("Absatzschuh"),
    SNEAKER("Sneakerprofil"),CRUTCHES("Mit Krücken"),PENGUIN("Pinguin"),OSTRICH("Strauß"),CHICKEN("Hühnerspur"),
    SQUIRREL("Eichhörnchen"),MOUSE("Mäusepfote"),LION("Löwenpfote"),BOAR("Wildschweinhuf"),CROCODILE("Krokodil"),
    PIXEL_HERO("Pixel-Abenteurer"),SPACE_BOOT("Weltraumstiefel"),YETI("Yeti"),BIKE_SCOOTER("Fahrrad / E-Scooter"),WHEELCHAIR("Rollstuhl");
    val title get()=tr(titleKey)
    val continuous get()=this==BIKE_SCOOTER || this==WHEELCHAIR
    val group get()=when(this) {
        SHOE,BAREFOOT,HIKING_BOOT,HIGH_HEEL,SNEAKER,CRUTCHES->FootprintGroup.HUMAN
        DUCK,BIRD,DINOSAUR,PENGUIN,OSTRICH,CHICKEN->FootprintGroup.BIPED
        ROBOT,PIXEL_HERO,SPACE_BOOT,YETI,BIKE_SCOOTER,WHEELCHAIR->FootprintGroup.OTHER
        else->FootprintGroup.QUADRUPED
    }
}
enum class FootprintGroup(val titleKey:String) {HUMAN("Menschen"),BIPED("Zweibeiner"),QUADRUPED("Vierbeiner"),OTHER("Weitere Spuren")}
@Serializable enum class FootprintPathMode { RANDOM, STRAIGHT }
@Serializable data class FootprintPreferences(val speed:Float=1f,val path:FootprintPathMode=FootprintPathMode.RANDOM,
    val style:FootprintStyle=FootprintStyle.SHOE,val barScale:Float=1f) {
    fun normalized()=copy(speed=if(speed.isFinite()) speed.coerceIn(0f,2f) else 1f,
        barScale=if(barScale.isFinite())barScale.coerceIn(.7f,2f)else 1f)
    val enabled get()=speed>0f
}

/** The unchanged 100% size is the centre, despite deliberately asymmetric endpoints. */
fun barFootprintScale(control:Float):Float {
    val p=if(control.isFinite())control.coerceIn(0f,1f)else .5f
    return if(p<=.5f).7f+.6f*p else 1f+2f*(p-.5f)
}
fun barFootprintControl(scale:Float):Float {
    val s=if(scale.isFinite())scale.coerceIn(.7f,2f)else 1f
    return if(s<=1f)(s-.7f)/.6f else .5f+(s-1f)/2f
}
