// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import dev.ambivalenton.openv.data.model.AppLogo
import dev.ambivalenton.openv.data.model.currentReplacement

internal interface LauncherIconBackend {
    val atomic:Boolean
    fun state(logo:AppLogo):Int
    fun set(logo:AppLogo,state:Int)
    fun batch(states:Map<AppLogo,Int>)
}
private class AndroidLauncherIconBackend(context:Context):LauncherIconBackend {
    private val pm=context.applicationContext.packageManager
    private val packageName=context.packageName
    private fun component(logo:AppLogo)=ComponentName(packageName,logo.alias)
    override val atomic get()=Build.VERSION.SDK_INT>=33
    override fun state(logo:AppLogo)=pm.getComponentEnabledSetting(component(logo))
    override fun set(logo:AppLogo,state:Int)=pm.setComponentEnabledSetting(component(logo),state,PackageManager.DONT_KILL_APP)
    @RequiresApi(33) override fun batch(states:Map<AppLogo,Int>) {
        pm.setComponentEnabledSettings(states.map {(logo,state)->PackageManager.ComponentEnabledSetting(component(logo),state,PackageManager.DONT_KILL_APP)})
    }
}

/** Switch launcher aliases only. MainActivity stays enabled for widgets, links and running guidance. */
class LauncherIconManager internal constructor(private val backend:LauncherIconBackend,private val read:()->String?,private val save:(String)->Unit) {
    constructor(context:Context):this(AndroidLauncherIconBackend(context),
        {context.applicationContext.getSharedPreferences("launcher-icon",Context.MODE_PRIVATE).getString("selected",null)},
        {value->context.applicationContext.getSharedPreferences("launcher-icon",Context.MODE_PRIVATE).edit().putString("selected",value).apply()})
    private fun enabled(logo:AppLogo,state:Int)=state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
        (state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && logo==AppLogo.ORIGINAL)
    internal fun active()=AppLogo.entries.filter {enabled(it,backend.state(it))}
    @Synchronized fun current():AppLogo {
        val active=active()
        return active.firstOrNull {it.name==read()} ?: active.firstOrNull() ?: AppLogo.ORIGINAL
    }
    private fun applyStates(states:Map<AppLogo,Int>) {
        val changed=states.filter{(logo,state)->backend.state(logo)!=state}
        if(changed.isEmpty())return
        if(backend.atomic)backend.batch(changed)
        else {
            // Enable the destination first: older Android versions must never see an empty launcher.
            changed.filter {(logo,state)->enabled(logo,state)}.forEach {(logo,state)->backend.set(logo,state)}
            changed.filterNot {(logo,state)->enabled(logo,state)}.forEach {(logo,state)->backend.set(logo,state)}
        }
    }
    @Synchronized fun select(logo:AppLogo):Result<AppLogo> {
        val snapshot=AppLogo.entries.associateWith(backend::state)
        if(active()==listOf(logo))return Result.success(logo)
        return try {
            applyStates(AppLogo.entries.associateWith {if(it==logo)PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED})
            check(active()==listOf(logo)){"Launcher selection was not applied"}
            save(logo.name);Result.success(logo)
        }catch(error:Exception) {
            runCatching {applyStates(snapshot)}
            // Retain at least one entry if a launcher service also rejects the rollback.
            if(runCatching {active().isEmpty()}.getOrDefault(false))runCatching {backend.set(AppLogo.ORIGINAL,PackageManager.COMPONENT_ENABLED_STATE_ENABLED)}
            Result.failure(error)
        }
    }
    /** Recover an interrupted older-Android switch without resetting a valid user's choice. */
    @Synchronized fun repair():Result<AppLogo> {
        val target=current().currentReplacement()
        return if(active()==listOf(target))Result.success(target)else select(target)
    }
}
