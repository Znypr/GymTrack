import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

val releaseApplicationId = "app.znypr.gymtrack"
val debugApplicationIdSuffix = ".debug"
val releaseCheckApplicationIdSuffix = ".releasecheck"
val releaseVersionCode = 2026070701
val releaseVersionName = "0.1.0"
val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
// CI fallback: when no local properties file exists, the upload key can come from
// environment variables (GitHub encrypted secrets). See docs/RELEASE.md.
val releaseSigningEnvironment = mapOf(
    "storeFile" to "GYMTRACK_UPLOAD_STORE_FILE",
    "storePassword" to "GYMTRACK_UPLOAD_STORE_PASSWORD",
    "keyAlias" to "GYMTRACK_UPLOAD_KEY_ALIAS",
    "keyPassword" to "GYMTRACK_UPLOAD_KEY_PASSWORD",
)
fun releaseSigningProperty(name: String): String? =
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningProperties.getProperty(name)
    } else {
        releaseSigningEnvironment[name]?.let { System.getenv(it) }
    }
fun hasCompleteReleaseSigningConfig(): Boolean = releaseSigningKeys.all { key ->
    !releaseSigningProperty(key).isNullOrBlank()
}
// Opt-in escape hatch for CI before the upload key exists: -Pgymtrack.allowUnsignedRelease=true
// builds an UNSIGNED release AAB/APK instead of failing. Play rejects unsigned uploads.
val allowUnsignedRelease = providers.gradleProperty("gymtrack.allowUnsignedRelease")
    .map { it.toBoolean() }
    .getOrElse(false)

android {
    namespace = "com.example.gymtrack"
    compileSdk = 36

    defaultConfig {
        applicationId = releaseApplicationId
        minSdk = 26
        targetSdk = 36
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
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Local-only twin of release: same R8/shrink rules, signed with the debug key and
        // installed beside the real app (app.znypr.gymtrack.releasecheck) so the minified
        // build can be smoke-tested on a device without the upload key. Never uploaded.
        create("releaseCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = releaseCheckApplicationIdSuffix
            versionNameSuffix = "-releasecheck"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
    sourceSets {
        // Deterministic workout-history generator and invariants, shared by the JVM
        // long-horizon simulation (test) and the on-device simulation (androidTest).
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
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
        check(release.isMinifyEnabled && release.isShrinkResources) {
            "Release builds must keep R8 minification and resource shrinking on (#337)."
        }
        val releaseCheck = android.buildTypes.getByName("releaseCheck")
        check(releaseCheck.applicationIdSuffix == releaseCheckApplicationIdSuffix) {
            "The debug-signed releaseCheck build must install as $releaseApplicationId$releaseCheckApplicationIdSuffix, never as the real app."
        }
        check(releaseCheck.isMinifyEnabled == release.isMinifyEnabled && releaseCheck.isShrinkResources == release.isShrinkResources) {
            "releaseCheck must use the same R8 settings as release, or it proves nothing."
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

// Fails a release APK/AAB package step early and clearly when no upload key is configured,
// instead of silently producing an unsigned artifact. Compile, lint and unit-test tasks
// of the release variant are not affected.
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    group = "verification"
    description = "Fails unless the release upload key is configured (or unsigned output was explicitly allowed)."
    val signed = hasCompleteReleaseSigningConfig()
    val allowUnsigned = allowUnsignedRelease
    val propertiesFileExists = releaseSigningPropertiesFile.exists()
    doLast {
        if (signed) return@doLast
        if (allowUnsigned) {
            logger.warn(
                "WARNING: building an UNSIGNED release artifact (gymtrack.allowUnsignedRelease=true). " +
                    "Google Play will not accept it. See docs/RELEASE.md to set up the upload key."
            )
            return@doLast
        }
        val where = if (propertiesFileExists) {
            "release-signing.properties exists but is missing one of: ${releaseSigningKeys.joinToString()}."
        } else {
            "No release-signing.properties and no GYMTRACK_UPLOAD_* environment variables."
        }
        throw GradleException(
            "Release signing is not configured. $where " +
                "Create the upload key and release-signing.properties as described in docs/RELEASE.md, " +
                "use the debug-signed 'releaseCheck' build type for local testing, " +
                "or pass -Pgymtrack.allowUnsignedRelease=true for an unsigned CI artifact."
        )
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
    dependsOn(requireReleaseSigning)
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.6.5")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
