// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.ambivalenton.openv.data.preferences.UserPreferencesRepository
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.DeviceLocationProvider
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.ColorPickerViewModel
import dev.ambivalenton.openv.ui.OverviewViewModel
import dev.ambivalenton.openv.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class OpenVApp : Application() {
    lateinit var koin: Koin
        private set

    val locations
        get() = koin.get<LocationRepository>()

    val deviceLocation
        get() = koin.get<DeviceLocationProvider>()

    private val policyScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        // A scoped Koin container avoids mutable process-global service locators in tests.
        koin =
            koinApplication {
                    androidContext(this@OpenVApp)
                    modules(
                        module {
                            single { LocationRepository(get<android.content.Context>()) }
                            single { EditorialHistoryRepository(get<android.content.Context>()) }
                            single { dev.ambivalenton.openv.launcher.LauncherIconManager(get<android.content.Context>()) }
                            single { PersonalDesignRepository(get(),get()) }
                            single { LocalDataRecovery(storeFile(get<android.content.Context>()),
                                java.io.File(get<android.content.Context>().filesDir,"recovery"),StoreRecovery.gate,get()) }
                            single { AppBackupService(get<android.content.Context>(),get(),get(),get(),get()) }
                            single { JourneySearchCache(java.io.File(cacheDir,"journey-searches-v1.json")) }
                            single { DeviceLocationProvider(get()) }
                            single { UserPreferencesRepository(get()) }
                            single {
                                OkHttpClient.Builder()
                                    .connectTimeout(12, TimeUnit.SECONDS)
                                    .readTimeout(22, TimeUnit.SECONDS)
                                    .callTimeout(28, TimeUnit.SECONDS)
                                    .cache(Cache(java.io.File(cacheDir, "http"), 20L * 1024 * 1024))
                                    .build()
                            }
                            single { NetworkClient(get()) { deviceOnline(get<android.content.Context>()) } }
                            single<WalkingDirectionsSource> { WalkingDirectionsRepository(get()) }
                            single<WalkingMapTileSource> { WalkingMapTiles(get(),get()) }
                            single { WeatherRepository(get()) }
                            single { GeocodingRepository(get()) }
                            single { RegularRouteMemory(get<android.content.Context>()) }
                            single { RegionalTimetable(get<android.content.Context>()) }
                            single { StaticStopCatalog(get<android.content.Context>()) }
                            single { TransitProviderFactory(get()) }
                            single { OverviewRepository(get<TransitProviderFactory>()::get) }
                            single { PinnedJourneyController(get(), get<TransitProviderFactory>()::get,
                                get<DeviceLocationProvider>(), get<WeatherRepository>()) }
                            single {
                                val tracker = get<PinnedJourneyController>()
                                DepartureBoardRepository(
                                    get<LocationRepository>(),
                                    get<TransitProviderFactory>()::get,
                                    { tracker.focusIsOpen },
                                    get<RegionalTimetable>(),
                                )
                            }
                            single { WidgetUpdater(get()) }
                            single { dev.ambivalenton.openv.tracking.PinnedBubbleCoordinator(this@OpenVApp,get(),get()) }
                            viewModel { ColorPickerViewModel(get()) }
                            viewModel {
                                OverviewViewModel(get(), get(), get<DeviceLocationProvider>(), weather = get<WeatherRepository>())
                            }
                            viewModel {
                                AppViewModel(get(), get(), get(), get(), get(), get(), get(),get(),get(),get(),get(),get())
                            }
                        }
                    )
                }
                .koin
        // On demand: widgets whose stand is older than 15 minutes refresh when the phone is unlocked.
        androidx.core.content.ContextCompat.registerReceiver(this, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                dev.ambivalenton.openv.widget.WidgetRefreshScheduler.refreshIfStale(context)
            }
        }, android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        // Observes the shared journey state only; it starts no tracking of its own.
        koin.get<dev.ambivalenton.openv.tracking.PinnedBubbleCoordinator>().start()
        // The former walking-pace learning is gone; its measurements file is removed once.
        policyScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            listOf("local-learning-v1.json","local-learning-v1.json.bak","local-learning-v1.json.new").forEach {name->
                runCatching {java.io.File(filesDir,name).delete()}
            }
        }
        // "Planermodus" switches every position request off, also for a running companion.
        policyScope.launch {
            locations.state.map {it.settings.plannerMode}.distinctUntilChanged().catch {}.collect {off->deviceLocation.setLocationOff(off)}
        }
        policyScope.launch {
            locations.state.map {it.settings.powerSaving}.distinctUntilChanged().catch {}.collectLatest {enabled->
                deviceLocation.setPowerSaving(enabled)
                koin.get<PinnedJourneyController>().setPowerSaving(enabled)
            }
        }
    }
}

/**
 * H05: the device's own view of internet access, so a radio gap is reported as "Keine
 * Internetverbindung" and not as a server problem. Null when Android cannot tell.
 */
internal fun deviceOnline(context: android.content.Context): Boolean? = runCatching {
    val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return@runCatching null
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrNull()
