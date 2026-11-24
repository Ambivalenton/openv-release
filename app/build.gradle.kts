// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.ByteBuffer
import java.nio.ByteOrder

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.ambivalenton.openv"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.ambivalenton.openv"
        minSdk = 26
        targetSdk = 36
        versionCode = 117
        versionName = "1.1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Timetable service of this build: "vrr" (VRR OpenService, default) or "delfi" (DELFI-Navi).
        // openv.timetable.protocol names the interface of the granted access: "efa" (EFA JSON, the
        // adapter in use today) or "trias" (VDV 431). See docs/timetable-services.md. The endpoint of a
        // service that needs individual access comes from the environment (CI secret), never from here.
        val timetable = providers.gradleProperty("openv.timetable").orElse("vrr").get()
        val timetableProtocol = providers.gradleProperty("openv.timetable.protocol").orElse("efa").get()
        val timetableEndpoint = providers.environmentVariable("OPENV_TIMETABLE_ENDPOINT").orElse("").get()
        require(timetable in setOf("vrr", "delfi")) { "openv.timetable must be vrr or delfi, not '$timetable'" }
        require(timetableProtocol in setOf("efa", "trias")) {
            "openv.timetable.protocol must be efa or trias, not '$timetableProtocol'"
        }
        require(timetable != "vrr" || timetableProtocol == "efa") { "VRR OpenService is reached through EFA." }
        require(timetable != "delfi" || timetableEndpoint.startsWith("https://")) {
            "openv.timetable=delfi needs OPENV_TIMETABLE_ENDPOINT (https) from the granted access."
        }
        // Remove this guard together with adding the TRIAS adapter (docs/timetable-services.md).
        require(timetableProtocol != "trias") {
            "The TRIAS adapter is not part of this source yet; see docs/timetable-services.md."
        }
        buildConfigField("String", "TIMETABLE_SERVICE", "\"$timetable\"")
        buildConfigField("String", "TIMETABLE_PROTOCOL", "\"$timetableProtocol\"")
        // Only a build for a service with individual access carries its endpoint.
        buildConfigField("String", "TIMETABLE_ENDPOINT", "\"${if (timetable == "delfi") timetableEndpoint else ""}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Release signing: the Play upload key, restored by CI from repository secrets. Without it the
    // release build stays unsigned. Keys never live in this repository.
    val uploadKey = providers.environmentVariable("OPENV_KEYSTORE")
    if (uploadKey.isPresent) {
        signingConfigs.create("upload") {
            storeFile = file(uploadKey.get())
            storePassword = providers.environmentVariable("OPENV_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("OPENV_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("OPENV_KEY_PASSWORD").get()
        }
    }
    // Preview signing: a stable test key (an Android debug keystore) restored by CI, so every preview
    // installs as an update. Without it, previews use the local debug key of the machine that builds them.
    val previewKey = providers.environmentVariable("OPENV_REVIEW_KEYSTORE").orNull
    if (previewKey != null) {
        signingConfigs.create("preview") {
            storeFile = file(previewKey)
            storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (uploadKey.isPresent) signingConfig = signingConfigs.getByName("upload")
        }
        // "Öpen-V · Vorschau": an optimized test build that installs next to the store app.
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".next"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "Öpen-V · Vorschau")
            signingConfig = if (previewKey != null) signingConfigs.getByName("preview") else signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    // Per-device APKs avoid shipping every native MapLibre ABI to one phone.
    // App Bundles continue to carry all supported ABIs for Play delivery.
    splits {
        abi {
            // Build APK splits separately from AAB: AGP resource shrinking forbids both in one run.
            isEnable = providers.gradleProperty("apkSplits").orNull == "true"
            reset()
            val requestedAbis=providers.gradleProperty("apkAbis").orElse("arm64-v8a,armeabi-v7a,x86,x86_64").get().split(",")
            require(requestedAbis.isNotEmpty() && requestedAbis.all {it in setOf("arm64-v8a","armeabi-v7a","x86","x86_64")})
            include(*requestedAbis.toTypedArray())
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Expressive APIs are public in this pinned prerelease; 1.4.0 hides them.
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.datastore:datastore:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Reference implementation is used only by the color parity regression test.
    testImplementation("com.google.android.material:material:1.13.0")
    implementation("io.insert-koin:koin-android:4.1.1")
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

// Robolectric's Android runtime (SDK 35, the level all tests use) is resolved by Gradle – cached and
// retried like every other dependency – instead of being downloaded by Robolectric itself in each
// test run, which failed whenever Maven Central throttled. Robolectric then runs offline on this copy.
// The version belongs to Robolectric 4.16.1; update it together with Robolectric (DefaultSdkProvider).
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
dependencies { robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-13954326-i7") }
val robolectricRuntimeDir = layout.buildDirectory.dir("robolectric-runtime")
val syncRobolectricRuntime = tasks.register<Sync>("syncRobolectricRuntime") {
    from(robolectricRuntime)
    into(robolectricRuntimeDir)
}

tasks.withType<Test>().configureEach {
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key
        ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    systemProperty("http.nonProxyHosts", "localhost|127.*|[::1]")
    dependsOn(syncRobolectricRuntime)
    inputs.files(syncRobolectricRuntime).withPropertyName("robolectricRuntime").withPathSensitivity(PathSensitivity.NAME_ONLY)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricRuntimeDir.get().asFile.absolutePath)
}

// Verify actual class definitions in R8's executable output. Mapping files may omit unchanged
// identities or be transformed for Compose; neither is a reliable substitute for the DEX tables.
for (variant in listOf("Preview", "Release")) {
    val lower=variant.replaceFirstChar { it.lowercase() }
    val verify=tasks.register("verify${variant}WidgetIdentities") {
        dependsOn("minify${variant}WithR8")
        val dexDirectory=layout.buildDirectory.dir("intermediates/dex/$lower/minify${variant}WithR8")
        inputs.dir(dexDirectory)
        doLast {
            val names=setOf("Ldev/ambivalenton/openv/widget/AppQuickWidget;", "Ldev/ambivalenton/openv/widget/DepartureBoardWidget;")
            val found=mutableMapOf<String,Int>()
            val dexFiles=dexDirectory.get().asFile.listFiles {file -> file.name.matches(Regex("classes[0-9]*\\.dex")) }?.toList().orEmpty()
            check(dexFiles.isNotEmpty()) {"No optimized DEX output exists for $lower"}
            for(file in dexFiles) {
                val bytes=file.readBytes()
                check(bytes.size>=112 && bytes.copyOfRange(0,4).contentEquals(byteArrayOf(100,101,120,10))) {"Invalid DEX header: $file"}
                val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                fun word(offset:Int):Int {check(offset>=0 && offset<=bytes.size-4);return buffer.getInt(offset)}
                check(word(40)==0x12345678) {"Unsupported DEX endianness"}
                val stringCount=word(56);val strings=word(60);val typeCount=word(64);val types=word(68)
                val classCount=word(96);val classes=word(100)
                check(stringCount>=0 && strings.toLong()+stringCount.toLong()*4<=bytes.size)
                check(typeCount>=0 && types.toLong()+typeCount.toLong()*4<=bytes.size)
                check(classCount>=0 && classes.toLong()+classCount.toLong()*32<=bytes.size)
                fun descriptor(type:Int):String {
                    check(type in 0 until typeCount)
                    val string=word(types+type*4);check(string in 0 until stringCount)
                    var offset=word(strings+string*4);var lengthBytes=0
                    do {check(offset in bytes.indices && ++lengthBytes<=5);val value=bytes[offset++].toInt() and 255} while(value and 128!=0)
                    val start=offset
                    while(offset<bytes.size && bytes[offset].toInt()!=0) offset++
                    check(offset<bytes.size) {"Unterminated DEX descriptor"}
                    return bytes.copyOfRange(start,offset).toString(Charsets.UTF_8)
                }
                repeat(classCount) {index ->
                    val offset=classes+index*32;val name=descriptor(word(offset))
                    if(name in names) {
                        check(word(offset+4) and 0x400==0 && word(offset+24)>0) {"Widget is abstract or has no class data: $name"}
                        found[name]=(found[name] ?: 0)+1
                    }
                }
            }
            check(names.all {found[it]==1}) {"Optimized widget identities are missing or duplicated: $found"}
            logger.lifecycle("Verified two separate, stable Glance widget identities in actual $lower DEX definitions.")
        }
    }
    tasks.matching {it.name=="assemble$variant" || it.name=="bundle$variant"}.configureEach {dependsOn(verify)}
}

