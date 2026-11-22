// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.launcher.LauncherIconManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persist first, then notify the launcher. A platform process restart cannot lose the choice. */
class PersonalDesignRepository(private val locations:LocationRepository,private val icons:LauncherIconManager){
    private val lock=Mutex()
    suspend fun changeLogo(logo:AppLogo):Result<AppLogo> = withContext(Dispatchers.IO){lock.withLock{
        val previous=icons.current().currentReplacement()
        try{
            locations.setLauncherLogo(logo)
            val result=icons.select(logo.currentReplacement())
            if(result.isFailure)locations.setLauncherLogo(previous)
            result
        }catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
    }}
    suspend fun apply(id:String):Result<Unit> = withContext(Dispatchers.IO){lock.withLock{
        val before=locations.state.first();val oldLogo=icons.current().currentReplacement()
        try{
            val after=locations.applyPersonalTheme(id)
            val result=icons.select(after.launcherLogo?:oldLogo)
            if(result.isFailure){
                locations.restoreDesign(before.settings.personalDesign(oldLogo));return@withLock Result.failure(requireNotNull(result.exceptionOrNull()))
            }
            Result.success(Unit)
        }catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
    }}
    suspend fun repairLauncher()=withContext(Dispatchers.IO){lock.withLock{
        val state=locations.state.first()
        val target=(state.launcherLogo?:icons.current()).currentReplacement()
        icons.select(target).onSuccess {if(state.launcherLogo!=it)locations.setLauncherLogo(it)}
    }}
}
