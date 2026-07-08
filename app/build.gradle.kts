import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

val releaseApplicationId = "app.znypr.gymtrack"
val debugApplicationIdSuffix = ".debug"
val releaseVersionCode = 2026070701
val releaseVersionName = "0.1.0"
val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
fun releaseSigningProperty(name: String): String? = releaseSigningProperties.getProperty(name)
fun hasCompleteReleaseSigningConfig(): Boolean = releaseSigningKeys.all { key ->
    !releaseSigningProperty(key).isNullOrBlank()
}

android {
    namespace = "com.example.gymtrack"
    compileSdk = 35

    defaultConfig {
        applicationId = releaseApplicationId
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PERMANENT_APPLICATION_ID", "\"$releaseApplicationId\"")
    }

    signingConfigs {
        if (hasCompleteReleaseSigningConfig()) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperty("storeFile")!!)
                storePassword = releaseSigningProperty("storePassword")!!
                keyAlias = releaseSigningProperty("keyAlias")!!
                keyPassword = releaseSigningProperty("keyPassword")!!
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = debugApplicationIdSuffix
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.register("validateReleaseConfig") {
    group = "verification"
    description = "Validates release identity, versioning, signing, and debug/release separation."

    doLast {
        check(releaseApplicationId == "app.znypr.gymtrack") {
            "Release application ID must stay permanent unless an explicit migration issue approves a change."
        }
        check(android.defaultConfig.applicationId == releaseApplicationId) {
            "defaultConfig.applicationId must be $releaseApplicationId."
        }
        check(!releaseApplicationId.startsWith("com.example.")) {
            "Release application ID must not use the template com.example namespace."
        }
        check(android.defaultConfig.versionCode == releaseVersionCode) {
            "Version code must use the documented releaseVersionCode constant."
        }
        check(releaseVersionCode >= 2026070701) {
            "Version code must not go below the first permanent-ID release baseline."
        }
        check(!android.defaultConfig.versionName.isNullOrBlank()) {
            "Version name must be set for release builds."
        }

        val debug = android.buildTypes.getByName("debug")
        check(debug.applicationIdSuffix == debugApplicationIdSuffix) {
            "Debug builds must use $debugApplicationIdSuffix so debug and release data cannot be confused."
        }

        val release = android.buildTypes.getByName("release")
        check(!release.isDebuggable) { "Release builds must not be debuggable." }
        check(release.signingConfig?.name != "debug") {
            "Release builds must never use the debug signing configuration."
        }
        if (releaseSigningPropertiesFile.exists()) {
            check(hasCompleteReleaseSigningConfig()) {
                "release-signing.properties exists but is missing one of: ${releaseSigningKeys.joinToString()}."
            }
            check(release.signingConfig?.name == "release") {
                "Complete release signing properties must produce the release signing config."
            }
        }
    }
}

tasks.register("validateReleaseHandoff") {
    group = "verification"
    description = "Requires complete local release signing before building the final handoff APK."
    dependsOn("validateReleaseConfig")

    doLast {
        check(releaseSigningPropertiesFile.exists()) {
            "Create release-signing.properties from release-signing.properties.example before a release handoff."
        }
        check(hasCompleteReleaseSigningConfig()) {
            "release-signing.properties must define: ${releaseSigningKeys.joinToString()}."
        }

        val release = android.buildTypes.getByName("release")
        check(release.signingConfig?.name == "release") {
            "Release handoff builds must use the local release signing config."
        }
        check(!release.isDebuggable) { "Release handoff builds must not be debuggable." }
        check(android.defaultConfig.applicationId == releaseApplicationId) {
            "Release handoff must install as $releaseApplicationId."
        }

        val debug = android.buildTypes.getByName("debug")
        check(debug.applicationIdSuffix == debugApplicationIdSuffix) {
            "Debug must keep $debugApplicationIdSuffix so app.znypr.gymtrack.debug remains separate."
        }
    }
}

tasks.named("check") {
    dependsOn("validateReleaseConfig")
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.5")
    implementation(libs.androidx.navigation.compose)
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // YCharts (Fixes 'co.yml.charts' errors)
    implementation("co.yml:ycharts:2.1.0")

    // Extended Icons (Fixes 'Icons.Default.FitnessCenter')
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
}
