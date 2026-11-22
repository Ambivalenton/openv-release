// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ThemeCatalogTest {
    @Test fun sixCollectionsEachContainTenNewThemesAndAppearImmediatelyBeforeVivaldi() {
        val groups=groupedAppThemes()
        val index=groups.indexOfFirst {it.first==AppThemeGroup.SEASONS}
        assertEquals(catalogThemeGroups,groups.subList(index-catalogThemeGroups.size,index).map {it.first})
        // Gaming and pets are larger collections (at least 20 each).
        val large=setOf(AppThemeGroup.GAMING,AppThemeGroup.PETS)
        for(group in catalogThemeGroups){
            val themes=groups.single {it.first==group}.second
            if(group in large) assertTrue(group.name,themes.size>=20) else assertEquals(group.name,10,themes.size)
            assertTrue(themes.all {it.texture.name.startsWith("CATALOG_")})
            if(group in large) assertTrue(themes.map {it.texture}.distinct().size>=4) else assertEquals(5,themes.map {it.texture}.distinct().size)
            assertTrue(themes.any {it.dark});assertTrue(themes.any {!it.dark})
        }
        // At least three cat and three dog designs among the pets.
        val pets=groups.single {it.first==AppThemeGroup.PETS}.second
        assertTrue(pets.count {it.name.startsWith("PET_CAT_")}>=3);assertTrue(pets.count {it.name.startsWith("PET_DOG_")}>=3)
        assertEquals(AppThemeGroup.CITY,appThemeGroup(AppTheme.SPACE_TERMINAL))
    }
    @Test fun everyThemeIsListedExactlyOnceAndOldQuietModeAndVivaldiOrderSurvive() {
        val listed=groupedAppThemes().flatMap {it.second}
        assertEquals(AppTheme.entries.size,listed.size)
        assertEquals(AppTheme.entries.toSet(),listed.toSet())
        assertEquals(quietAppThemes.toSet(),groupedAppThemes(true).flatMap {it.second}.toSet())
        val seasons=groupedAppThemes().single {it.first==AppThemeGroup.SEASONS}.second
        assertEquals(listOf(AppTheme.VIVALDI_PRIMAVERA,AppTheme.VIVALDI_PRIMAVERA_II,
            AppTheme.VIVALDI_PRIMAVERA_III),seasons.take(3))
        assertEquals(12,seasons.size)
    }
    @Test fun allNewThemesAndExistingSystemIdsRoundTripWithoutRenamingSavedPreferences() {
        val themes=AppTheme.entries.filter {appThemeGroup(it) in catalogThemeGroups||it.name.startsWith("OS_")}
        for(theme in themes){
            val encoded=Json.encodeToString(theme)
            assertEquals("\"${theme.name}\"",encoded)
            assertEquals(theme,Json.decodeFromString<AppTheme>(encoded))
            val colors=theme.defaultColors()
            assertEquals(colors,Json.decodeFromString<AppThemeColors>(Json.encodeToString(colors)))
        }
    }
    @Test fun eachNewThemeHasADistinctPaletteAndAnExplicitEditorPalette() {
        val themes=AppTheme.entries.filter {appThemeGroup(it) in catalogThemeGroups}
        assertEquals(104,themes.map {it.defaultColors()}.distinct().size)
        for(theme in themes){
            assertEquals(10,appColorFamily(theme).colors.size)
            assertTrue(theme.radiusScale in 0f..2f)
            listOf(theme.background,theme.surface,theme.accent,theme.secondary).forEach {
                assertEquals(255L,it ushr 24)
            }
        }
    }
}
